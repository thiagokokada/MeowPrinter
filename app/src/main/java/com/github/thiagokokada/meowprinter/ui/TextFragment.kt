package com.github.thiagokokada.meowprinter.ui

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.AppCompatImageButton
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import com.github.thiagokokada.meowprinter.R
import com.github.thiagokokada.meowprinter.data.AppSettings
import com.github.thiagokokada.meowprinter.data.LogStore
import com.github.thiagokokada.meowprinter.databinding.FragmentTextBinding
import com.github.thiagokokada.meowprinter.document.BlockAlignment
import com.github.thiagokokada.meowprinter.document.CanvasDocument
import com.github.thiagokokada.meowprinter.document.CanvasDocumentCodec
import com.github.thiagokokada.meowprinter.document.CanvasDocumentEditor
import com.github.thiagokokada.meowprinter.document.CanvasDocumentRenderer
import com.github.thiagokokada.meowprinter.document.CanvasTextSize
import com.github.thiagokokada.meowprinter.document.DocumentBlock
import com.github.thiagokokada.meowprinter.document.ImageBlock
import com.github.thiagokokada.meowprinter.document.ImageBlockWidth
import com.github.thiagokokada.meowprinter.document.TextBlock
import com.github.thiagokokada.meowprinter.image.DitheringMode
import com.github.thiagokokada.meowprinter.image.ImagePrintPreparer
import com.github.thiagokokada.meowprinter.image.PreparedPrintImage
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import com.yalantis.ucrop.UCrop
import com.yalantis.ucrop.UCropActivity
import java.io.File
import java.util.UUID
import org.json.JSONObject

class TextFragment : Fragment(R.layout.fragment_text) {
    interface Host {
        fun printPreparedImage(preparedImage: PreparedPrintImage, sourceLabel: String)
        fun selectedTextDithering(): com.github.thiagokokada.meowprinter.image.DitheringMode
        fun connectionSummary(): ConnectionSummary
        fun refreshPrinterConnection()
        fun isPrintInProgress(): Boolean
    }

    private var binding: FragmentTextBinding? = null
    private var host: Host? = null
    private lateinit var appSettings: AppSettings
    private lateinit var documentRenderer: CanvasDocumentRenderer
    private var currentDocument = CanvasDocument.default()
    private var currentSavedDocumentName: String? = null
    private var pendingImageTargetBlockId: String? = null

    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri == null) {
            appendLog("Document image picker canceled.")
            return@registerForActivityResult
        }
        launchImageEditor(uri)
    }

    private val imageEditorLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        when (result.resultCode) {
            android.app.Activity.RESULT_OK -> {
                val editedUri = result.data?.let(UCrop::getOutput)
                if (editedUri == null) {
                    Toast.makeText(requireContext(), R.string.text_image_edit_failed, Toast.LENGTH_SHORT).show()
                    appendLog("Text editor image flow finished without an output URI.")
                    return@registerForActivityResult
                }
                onEditedImageReady(editedUri)
            }

            android.app.Activity.RESULT_CANCELED -> {
                appendLog("Text editor image flow canceled.")
            }

            else -> {
                val error = result.data?.let(UCrop::getError)
                Toast.makeText(requireContext(), R.string.text_image_edit_failed, Toast.LENGTH_SHORT).show()
                appendLog("Text editor image flow failed: ${error?.message ?: getString(R.string.unknown_error)}")
            }
        }
    }

    private val saveDocumentLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument(DOCUMENT_MIME_TYPE)
    ) { uri ->
        if (uri == null) {
            appendLog("Compose document save canceled.")
            return@registerForActivityResult
        }
        saveDocumentToUri(uri)
    }

    private val loadDocumentLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) {
            appendLog("Compose document load canceled.")
            return@registerForActivityResult
        }
        loadDocumentFromUri(uri)
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        host = context as? Host
        appSettings = AppSettings(context.applicationContext)
        documentRenderer = CanvasDocumentRenderer(context, context.contentResolver)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding = FragmentTextBinding.bind(view)
        currentDocument = savedInstanceState
            ?.getString(KEY_DOCUMENT_STATE)
            ?.let(CanvasDocumentCodec::decode)
            ?: appSettings.canvasDocumentDraft
        currentSavedDocumentName = savedInstanceState?.getString(KEY_DOCUMENT_NAME)

        binding?.textContent?.applySideAndBottomSystemBarsPadding()
        binding?.buttonAddTextBlock?.setOnClickListener { showTextBlockDialog() }
        binding?.buttonAddImageBlock?.setOnClickListener { startImageInsert() }
        binding?.buttonNewDocument?.setOnClickListener { confirmStartNewDocument() }
        binding?.buttonSaveDocument?.setOnClickListener {
            saveDocumentLauncher.launch(suggestedDocumentFileName())
        }
        binding?.buttonLoadDocument?.setOnClickListener {
            loadDocumentLauncher.launch(arrayOf(DOCUMENT_MIME_TYPE))
        }
        binding?.buttonPrintDocument?.setOnClickListener { printDocument() }
        binding?.buttonComposeConnection?.setOnClickListener {
            host?.refreshPrinterConnection()
        }
        renderDocument()
    }

    override fun onResume() {
        super.onResume()
        renderConnectionSummary()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_DOCUMENT_STATE, CanvasDocumentCodec.encode(currentDocument))
        outState.putString(KEY_DOCUMENT_NAME, currentSavedDocumentName)
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }

    override fun onDetach() {
        host = null
        super.onDetach()
    }

    private fun renderDocument() {
        val summary = renderConnectionSummary()
        renderBlockCards()
        binding?.buttonPrintDocument?.isEnabled =
            currentDocument.blocks.isNotEmpty() &&
                summary?.isConnected == true &&
                host?.isPrintInProgress() != true
    }

    fun refreshConnectionSummary() {
        val summary = renderConnectionSummary()
        binding?.buttonPrintDocument?.isEnabled =
            currentDocument.blocks.isNotEmpty() &&
                summary?.isConnected == true &&
                host?.isPrintInProgress() != true
    }

    private fun renderConnectionSummary(): ConnectionSummary? {
        val summary = host?.connectionSummary() ?: return null
        binding?.composePrinterValue?.text = summary.printerName
        binding?.composeStatusValue?.text = summary.statusText
        binding?.buttonComposeConnection?.text = summary.actionLabel
        binding?.buttonComposeConnection?.isEnabled = summary.actionEnabled
        return summary
    }

    private fun renderBlockCards() {
        val container = binding?.textBlocksContainer ?: return
        container.removeAllViews()
        currentDocument.blocks.forEachIndexed { index, block ->
            container.addView(createBlockCard(block, index))
        }
        binding?.textBlocksPlaceholder?.isVisible = currentDocument.blocks.isEmpty()
    }

    private fun createBlockCard(block: DocumentBlock, index: Int): View {
        val context = requireContext()
        val previewWidth = (resources.displayMetrics.widthPixels - dp(96)).coerceAtLeast(dp(180))
        return MaterialCardView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).also { params ->
                if (index > 0) {
                    params.topMargin = dp(12)
                }
            }
            radius = dp(20).toFloat()
            setCardBackgroundColor(
                MaterialColors.getColor(
                    context,
                    com.google.android.material.R.attr.colorSurfaceContainerHighest,
                    0
                )
            )
            addView(
                LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(16), dp(16), dp(16), dp(16))
                    addView(titleView(blockTitle(block)))
                    addView(blockContentView(block, previewWidth))
                    addView(actionRow(block))
                }
            )
        }
    }

    private fun blockContentView(block: DocumentBlock, widthPx: Int): View {
        return documentRenderer.createPreviewBlockView(block, widthPx).apply {
            val params = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            params.topMargin = dp(12)
            layoutParams = params
        }
    }

    private fun actionRow(block: DocumentBlock): View {
        val context = requireContext()
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(12), 0, 0)
            addView(iconActionButton(R.drawable.ic_edit_24, R.string.edit) {
                when (block) {
                    is TextBlock -> showTextBlockDialog(block)
                    is ImageBlock -> showImageBlockDialog(block)
                }
            })
            addView(iconActionButton(R.drawable.ic_content_copy_24, R.string.duplicate) {
                updateDocument(CanvasDocumentEditor.duplicateBlock(currentDocument, block.id))
            })
            addView(iconActionButton(R.drawable.ic_arrow_upward_24, R.string.move_up) {
                updateDocument(CanvasDocumentEditor.moveBlock(currentDocument, block.id, -1))
            })
            addView(iconActionButton(R.drawable.ic_arrow_downward_24, R.string.move_down) {
                updateDocument(CanvasDocumentEditor.moveBlock(currentDocument, block.id, 1))
            })
            addView(iconActionButton(R.drawable.ic_delete_24, R.string.delete) {
                updateDocument(CanvasDocumentEditor.removeBlock(currentDocument, block.id))
            })
        }
    }

    private fun iconActionButton(iconRes: Int, descriptionRes: Int, onClick: () -> Unit): View {
        val context = requireContext()
        return AppCompatImageButton(context).apply {
            setImageResource(iconRes)
            contentDescription = getString(descriptionRes)
            background = null
            setColorFilter(
                MaterialColors.getColor(
                    context,
                    com.google.android.material.R.attr.colorOnSurfaceVariant,
                    0
                )
            )
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(dp(10), dp(10), dp(10), dp(10))
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(dp(44), dp(44)).also { params ->
                params.marginEnd = dp(8)
            }
        }
    }

    private fun titleView(text: String): TextView {
        return TextView(requireContext()).apply {
            this.text = text
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleMedium)
        }
    }

    private fun blockTitle(block: DocumentBlock): String {
        return when (block) {
            is TextBlock -> getString(R.string.block_title_text)
            is ImageBlock -> getString(R.string.block_title_image)
        }
    }

    private fun showTextBlockDialog(existingBlock: TextBlock? = null) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_text_block, null)
        val contentInput = dialogView.findViewById<EditText>(R.id.input_text_block_content)
        val alignmentSpinner = dialogView.findViewById<Spinner>(R.id.spinner_text_block_alignment)
        val sizeSpinner = dialogView.findViewById<Spinner>(R.id.spinner_text_block_size)

        val block = existingBlock ?: TextBlock(
            id = UUID.randomUUID().toString(),
            markdown = "",
            alignment = BlockAlignment.LEFT,
            textSize = CanvasTextSize.SP14
        )

        contentInput.setText(block.markdown)
        setupSpinner(alignmentSpinner, BlockAlignment.entries.map { it.displayName }, block.alignment.ordinal)
        setupSpinner(sizeSpinner, CanvasTextSize.entries.map { it.displayName }, block.textSize.ordinal)
        bindMarkdownHelperButtons(dialogView, contentInput)

        AlertDialog.Builder(requireContext())
            .setTitle(if (existingBlock == null) R.string.text_add_text else R.string.text_edit_text)
            .setView(dialogView)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.save) { _, _ ->
                val updatedBlock = block.copy(
                    markdown = contentInput.text?.toString().orEmpty(),
                    alignment = BlockAlignment.entries[alignmentSpinner.selectedItemPosition],
                    textSize = CanvasTextSize.entries[sizeSpinner.selectedItemPosition]
                )
                updateDocument(
                    if (existingBlock == null) {
                        CanvasDocumentEditor.appendBlock(currentDocument, updatedBlock)
                    } else {
                        CanvasDocumentEditor.replaceBlock(currentDocument, updatedBlock)
                    }
                )
            }
            .show()
    }

    private fun bindMarkdownHelperButtons(dialogView: View, contentInput: EditText) {
        dialogView.findViewById<View>(R.id.button_markdown_h1).setOnClickListener {
            applyMarkdownEdit(contentInput) { text, start, end ->
                MarkdownSnippetFormatter.heading1(text, start, end)
            }
        }
        dialogView.findViewById<View>(R.id.button_markdown_h2).setOnClickListener {
            applyMarkdownEdit(contentInput) { text, start, end ->
                MarkdownSnippetFormatter.heading2(text, start, end)
            }
        }
        dialogView.findViewById<View>(R.id.button_markdown_bold).setOnClickListener {
            applyMarkdownEdit(contentInput) { text, start, end ->
                MarkdownSnippetFormatter.bold(text, start, end)
            }
        }
        dialogView.findViewById<View>(R.id.button_markdown_italic).setOnClickListener {
            applyMarkdownEdit(contentInput) { text, start, end ->
                MarkdownSnippetFormatter.italic(text, start, end)
            }
        }
        dialogView.findViewById<View>(R.id.button_markdown_list).setOnClickListener {
            applyMarkdownEdit(contentInput) { text, start, end ->
                MarkdownSnippetFormatter.bulletList(text, start, end)
            }
        }
        dialogView.findViewById<View>(R.id.button_markdown_quote).setOnClickListener {
            applyMarkdownEdit(contentInput) { text, start, end ->
                MarkdownSnippetFormatter.blockquote(text, start, end)
            }
        }
        dialogView.findViewById<View>(R.id.button_markdown_table).setOnClickListener {
            applyMarkdownEdit(contentInput) { text, start, end ->
                MarkdownSnippetFormatter.table(text, start, end)
            }
        }
    }

    private fun applyMarkdownEdit(
        contentInput: EditText,
        formatter: (text: String, selectionStart: Int, selectionEnd: Int) -> MarkdownEditResult
    ) {
        val currentText = contentInput.text?.toString().orEmpty()
        val result = formatter(
            currentText,
            contentInput.selectionStart.coerceAtLeast(0),
            contentInput.selectionEnd.coerceAtLeast(0)
        )
        contentInput.setText(result.text)
        contentInput.requestFocus()
        contentInput.setSelection(result.selectionStart, result.selectionEnd)
    }

    private fun showImageBlockDialog(block: ImageBlock) {
        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(8), dp(8), 0)
        }
        container.addView(
            TextView(requireContext()).apply {
                text = getString(R.string.text_block_alignment_label)
                setPadding(0, 0, 0, dp(8))
            }
        )
        val alignmentSpinner = Spinner(requireContext()).also { spinner ->
            setupSpinner(spinner, BlockAlignment.entries.map { it.displayName }, block.alignment.ordinal)
            container.addView(spinner)
        }
        val ditheringSpinner = Spinner(requireContext()).also { spinner ->
            container.addView(
                TextView(requireContext()).apply {
                    text = getString(R.string.image_block_dithering_label)
                    setPadding(0, dp(16), 0, dp(8))
                }
            )
            setupSpinner(spinner, DitheringMode.entries.map { it.displayName }, block.ditheringMode.ordinal)
            container.addView(spinner)
        }
        val widthSpinner = Spinner(requireContext()).also { spinner ->
            container.addView(
                TextView(requireContext()).apply {
                    text = getString(R.string.image_block_width_label)
                    setPadding(0, dp(16), 0, dp(8))
                }
            )
            setupSpinner(spinner, ImageBlockWidth.entries.map { it.displayName }, block.width.ordinal)
            container.addView(spinner)
        }

        AlertDialog.Builder(requireContext())
            .setTitle(R.string.text_edit_image)
            .setView(container)
            .setNegativeButton(android.R.string.cancel, null)
            .setNeutralButton(R.string.text_replace_image) { _, _ ->
                pendingImageTargetBlockId = block.id
                startImageInsert()
            }
            .setPositiveButton(R.string.save) { _, _ ->
                updateDocument(
                    CanvasDocumentEditor.replaceBlock(
                        currentDocument,
                        block.copy(
                            alignment = BlockAlignment.entries[alignmentSpinner.selectedItemPosition],
                            ditheringMode = DitheringMode.entries[ditheringSpinner.selectedItemPosition],
                            width = ImageBlockWidth.entries[widthSpinner.selectedItemPosition]
                        )
                    )
                )
            }
            .show()
    }

    private fun startImageInsert() {
        imagePickerLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    private fun launchImageEditor(sourceUri: Uri) {
        val destinationUri = Uri.fromFile(
            File(requireContext().cacheDir, "document-image-${System.currentTimeMillis()}.png")
        )
        val options = UCrop.Options().apply {
            setCompressionFormat(Bitmap.CompressFormat.PNG)
            setCompressionQuality(100)
            setHideBottomControls(false)
            setFreeStyleCropEnabled(true)
            setToolbarColor(ContextCompat.getColor(requireContext(), R.color.meow_surface))
            setToolbarWidgetColor(ContextCompat.getColor(requireContext(), R.color.meow_on_surface))
            setActiveControlsWidgetColor(ContextCompat.getColor(requireContext(), R.color.meow_secondary))
            setRootViewBackgroundColor(ContextCompat.getColor(requireContext(), R.color.meow_background))
            setDimmedLayerColor(ContextCompat.getColor(requireContext(), R.color.meow_primary_container))
            setCropGridColor(ContextCompat.getColor(requireContext(), R.color.meow_outline))
            setCropFrameColor(ContextCompat.getColor(requireContext(), R.color.meow_secondary))
        }
        val intent = UCrop.of(sourceUri, destinationUri)
            .withOptions(options)
            .getIntent(requireContext())
            .apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                setClass(requireContext(), UCropActivity::class.java)
            }

        imageEditorLauncher.launch(intent)
    }

    private fun onEditedImageReady(editedUri: Uri) {
        val existingBlockId = pendingImageTargetBlockId
        pendingImageTargetBlockId = null
        val newBlock = ImageBlock(
            id = existingBlockId ?: UUID.randomUUID().toString(),
            imageUri = editedUri.toString(),
            alignment = BlockAlignment.CENTER
        )

        val updatedDocument = if (existingBlockId == null) {
            CanvasDocumentEditor.appendBlock(currentDocument, newBlock)
        } else {
            val existingImageBlock = currentDocument.blocks
                .filterIsInstance<ImageBlock>()
                .firstOrNull { it.id == existingBlockId }
            CanvasDocumentEditor.replaceBlock(
                currentDocument,
                newBlock.copy(
                    alignment = existingImageBlock?.alignment ?: BlockAlignment.CENTER,
                    ditheringMode = existingImageBlock?.ditheringMode ?: DitheringMode.FLOYD_STEINBERG,
                    width = existingImageBlock?.width ?: ImageBlockWidth.FULL
                )
            )
        }
        updateDocument(updatedDocument)
        appendLog("Prepared document image block from $editedUri.")
    }

    private fun printDocument() {
        if (host?.isPrintInProgress() == true) {
            return
        }
        if (currentDocument.blocks.isEmpty()) {
            Toast.makeText(requireContext(), R.string.text_document_empty, Toast.LENGTH_SHORT).show()
            return
        }

        runCatching {
            documentRenderer.renderBitmap(
                document = currentDocument,
                widthPx = PRINT_RENDER_WIDTH_PX,
                mode = CanvasDocumentRenderer.RenderMode.PRINT
            )
        }.onSuccess { bitmap ->
            val preparedImage = ImagePrintPreparer.prepare(
                bitmap,
                host?.selectedTextDithering() ?: appSettings.selectedDitheringMode
            )
            host?.printPreparedImage(preparedImage, getString(R.string.text_printing_label))
        }.onFailure { error ->
            Toast.makeText(requireContext(), R.string.text_print_failed, Toast.LENGTH_SHORT).show()
            appendLog("Document render failed before print: ${error.message ?: getString(R.string.unknown_error)}")
        }
    }

    private fun confirmStartNewDocument() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.text_new_document_title)
            .setMessage(R.string.text_new_document_message)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.text_new_document) { _, _ ->
                currentSavedDocumentName = null
                updateDocument(CanvasDocument.empty())
                appendLog("Started a new compose document.")
            }
            .show()
    }

    private fun saveDocumentToUri(uri: Uri) {
        runCatching {
            requireContext().contentResolver.openOutputStream(uri, "wt")?.bufferedWriter()?.use { writer ->
                writer.write(CanvasDocumentCodec.encode(currentDocument))
            } ?: error("Unable to open output stream.")
            readDisplayName(uri) ?: suggestedDocumentFileName()
        }.onSuccess { displayName ->
            currentSavedDocumentName = displayName
            Toast.makeText(
                requireContext(),
                getString(R.string.text_save_document_success, displayName),
                Toast.LENGTH_SHORT
            ).show()
            appendLog("Saved compose document to $uri.")
        }.onFailure { error ->
            Toast.makeText(requireContext(), R.string.text_save_document_failed, Toast.LENGTH_SHORT).show()
            appendLog("Failed to save compose document: ${error.message ?: getString(R.string.unknown_error)}")
        }
    }

    private fun loadDocumentFromUri(uri: Uri) {
        runCatching {
            val rawDocument = requireContext().contentResolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
                reader.readText()
            } ?: error("Unable to open input stream.")
            val root = JSONObject(rawDocument)
            if (!root.has("blocks")) {
                error("Invalid document format.")
            }
            val displayName = readDisplayName(uri) ?: uri.lastPathSegment.orEmpty()
            Triple(displayName, rawDocument, uri)
        }.onSuccess { (displayName, rawDocument, sourceUri) ->
            currentSavedDocumentName = displayName
            updateDocument(CanvasDocumentCodec.decode(rawDocument))
            Toast.makeText(
                requireContext(),
                getString(R.string.text_load_document_success, displayName),
                Toast.LENGTH_SHORT
            ).show()
            appendLog("Loaded compose document from $sourceUri.")
        }.onFailure { error ->
            Toast.makeText(requireContext(), R.string.text_load_document_failed, Toast.LENGTH_SHORT).show()
            appendLog("Failed to load compose document: ${error.message ?: getString(R.string.unknown_error)}")
        }
    }

    private fun suggestedDocumentFileName(): String {
        val baseName = currentSavedDocumentName
            ?.substringBeforeLast(".json")
            ?.trim()
            ?.takeIf(String::isNotBlank)
            ?: DEFAULT_DOCUMENT_FILE_NAME
        return "$baseName.json"
    }

    private fun readDisplayName(uri: Uri): String? {
        return requireContext().contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            val columnIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (columnIndex == -1 || !cursor.moveToFirst()) {
                null
            } else {
                cursor.getString(columnIndex)
            }
        }
    }

    private fun updateDocument(updatedDocument: CanvasDocument) {
        currentDocument = updatedDocument
        appSettings.canvasDocumentDraft = updatedDocument
        renderDocument()
    }

    private fun setupSpinner(spinner: Spinner, values: List<String>, selectedIndex: Int) {
        spinner.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            values
        ).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        spinner.setSelection(selectedIndex, false)
    }

    private fun appendLog(message: String) {
        LogStore.append(message)
    }

    private fun dp(value: Int): Int {
        return (value * requireContext().resources.displayMetrics.density).toInt()
    }

    companion object {
        private const val DEFAULT_DOCUMENT_FILE_NAME = "meow-document"
        private const val DOCUMENT_MIME_TYPE = "application/json"
        private const val KEY_DOCUMENT_STATE = "document_state"
        private const val KEY_DOCUMENT_NAME = "document_name"
        private const val PRINT_RENDER_WIDTH_PX = 384
    }
}

data class ConnectionSummary(
    val printerName: String,
    val statusText: String,
    val actionLabel: String,
    val actionEnabled: Boolean,
    val isConnected: Boolean
)
