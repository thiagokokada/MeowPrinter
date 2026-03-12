package com.github.thiagokokada.meowprinter.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.doOnLayout
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import com.github.thiagokokada.meowprinter.R
import com.github.thiagokokada.meowprinter.data.AppSettings
import com.github.thiagokokada.meowprinter.databinding.FragmentTextBinding
import com.github.thiagokokada.meowprinter.image.ImagePrintPreparer
import io.noties.markwon.Markwon

class TextFragment : Fragment() {
    interface Host {
        fun printPreparedImage(preparedImage: com.github.thiagokokada.meowprinter.image.PreparedPrintImage, sourceLabel: String)
        fun selectedMarkdownDithering(): com.github.thiagokokada.meowprinter.image.DitheringMode
    }

    private var binding: FragmentTextBinding? = null
    private var host: Host? = null
    private lateinit var appSettings: AppSettings
    private lateinit var markwon: Markwon
    private var suppressEditorCallback = false

    private val importMarkdownLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) {
            return@registerForActivityResult
        }

        runCatching {
            requireContext().contentResolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
                reader.readText()
            }.orEmpty()
        }.onSuccess { markdown ->
            suppressEditorCallback = true
            binding?.markdownInput?.setText(markdown)
            suppressEditorCallback = false
            persistMarkdown(markdown)
            renderMarkdown(markdown)
        }.onFailure { error ->
            Toast.makeText(requireContext(), R.string.markdown_import_failed, Toast.LENGTH_SHORT).show()
            appendLog("Markdown import failed: ${error.message ?: getString(R.string.unknown_error)}")
        }
    }

    private val exportMarkdownLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/markdown")
    ) { uri ->
        if (uri == null) {
            return@registerForActivityResult
        }

        val markdown = binding?.markdownInput?.text?.toString().orEmpty()
        runCatching {
            requireContext().contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { writer ->
                writer.write(markdown)
            }
        }.onSuccess {
            Toast.makeText(requireContext(), R.string.markdown_export_success, Toast.LENGTH_SHORT).show()
            appendLog("Markdown exported to $uri.")
        }.onFailure { error ->
            Toast.makeText(requireContext(), R.string.markdown_export_failed, Toast.LENGTH_SHORT).show()
            appendLog("Markdown export failed: ${error.message ?: getString(R.string.unknown_error)}")
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        host = context as? Host
        appSettings = AppSettings(context.applicationContext)
        markwon = Markwon.create(context.applicationContext)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val fragmentBinding = FragmentTextBinding.inflate(inflater, container, false)
        binding = fragmentBinding
        return fragmentBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding?.textContent?.applySideAndBottomSystemBarsPadding()

        val initialMarkdown = appSettings.markdownDraft
        suppressEditorCallback = true
        binding?.markdownInput?.setText(initialMarkdown)
        suppressEditorCallback = false

        binding?.markdownInput?.doAfterTextChanged { editable ->
            if (suppressEditorCallback) {
                return@doAfterTextChanged
            }
            val markdown = editable?.toString().orEmpty()
            persistMarkdown(markdown)
            renderMarkdown(markdown)
        }
        binding?.buttonImportMarkdown?.setOnClickListener {
            importMarkdownLauncher.launch(arrayOf("text/*"))
        }
        binding?.buttonExportMarkdown?.setOnClickListener {
            exportMarkdownLauncher.launch(getString(R.string.markdown_file_name))
        }
        binding?.buttonPrintMarkdown?.setOnClickListener {
            printMarkdown()
        }

        binding?.markdownPreviewCard?.doOnLayout {
            renderMarkdown(binding?.markdownInput?.text?.toString().orEmpty())
        }
        renderMarkdown(initialMarkdown)
    }

    override fun onResume() {
        super.onResume()
        renderMarkdown(binding?.markdownInput?.text?.toString().orEmpty())
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }

    override fun onDetach() {
        host = null
        super.onDetach()
    }

    private fun renderMarkdown(markdown: String) {
        val preview = binding?.markdownPreview ?: return
        markwon.setMarkdown(preview, markdown.ifBlank { " " })
    }

    private fun printMarkdown() {
        val markdown = binding?.markdownInput?.text?.toString().orEmpty()
        if (markdown.isBlank()) {
            Toast.makeText(requireContext(), R.string.markdown_empty, Toast.LENGTH_SHORT).show()
            return
        }

        val renderedBitmap = renderPreviewToBitmap(markdown)
        val preparedImage = ImagePrintPreparer.prepare(
            renderedBitmap,
            host?.selectedMarkdownDithering() ?: appSettings.selectedDitheringMode
        )
        host?.printPreparedImage(preparedImage, "Markdown")
    }

    private fun renderPreviewToBitmap(markdown: String): Bitmap {
        val preview = binding?.markdownPreview ?: error("Preview is unavailable.")
        val previewWidth = preview.width.takeIf { it > 0 }
            ?: resources.displayMetrics.widthPixels - preview.paddingLeft - preview.paddingRight

        val renderView = TextView(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(previewWidth, ViewGroup.LayoutParams.WRAP_CONTENT)
            setPadding(preview.paddingLeft, preview.paddingTop, preview.paddingRight, preview.paddingBottom)
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyLarge)
            setTextColor(preview.currentTextColor)
            setBackgroundColor(android.graphics.Color.WHITE)
        }
        markwon.setMarkdown(renderView, markdown)

        val widthSpec = View.MeasureSpec.makeMeasureSpec(previewWidth, View.MeasureSpec.EXACTLY)
        val heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        renderView.measure(widthSpec, heightSpec)
        renderView.layout(0, 0, renderView.measuredWidth, renderView.measuredHeight)

        return Bitmap.createBitmap(renderView.measuredWidth, renderView.measuredHeight, Bitmap.Config.ARGB_8888).also { bitmap ->
            val canvas = Canvas(bitmap)
            canvas.drawColor(android.graphics.Color.WHITE)
            renderView.draw(canvas)
        }
    }

    private fun persistMarkdown(markdown: String) {
        appSettings.markdownDraft = markdown
    }

    private fun appendLog(message: String) {
        com.github.thiagokokada.meowprinter.data.LogStore.append(message)
    }
}
