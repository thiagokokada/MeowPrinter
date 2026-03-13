package com.github.thiagokokada.meowprinter.ui

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.AppCompatImageButton
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import com.github.thiagokokada.meowprinter.document.CanvasFontFamily
import com.github.thiagokokada.meowprinter.document.CanvasTextSize
import com.github.thiagokokada.meowprinter.document.DocumentBlock
import com.github.thiagokokada.meowprinter.document.ImageBlock
import com.github.thiagokokada.meowprinter.document.TableBlock
import com.github.thiagokokada.meowprinter.document.TableBlockTextCodec
import com.github.thiagokokada.meowprinter.document.TextBlock
import com.github.thiagokokada.meowprinter.document.TextBlockStyle
import com.github.thiagokokada.meowprinter.image.DitheringMode
import com.github.thiagokokada.meowprinter.image.ImagePrintPreparer
import com.github.thiagokokada.meowprinter.image.PreparedPrintImage
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import com.yalantis.ucrop.UCrop
import com.yalantis.ucrop.UCropActivity
import java.io.File
import java.util.UUID

class TextFragment : Fragment() {
    interface Host {
        fun printPreparedImage(preparedImage: PreparedPrintImage, sourceLabel: String)
        fun selectedTextDithering(): com.github.thiagokokada.meowprinter.image.DitheringMode
    }

    private var binding: FragmentTextBinding? = null
    private var host: Host? = null
    private lateinit var appSettings: AppSettings
    private lateinit var documentRenderer: CanvasDocumentRenderer
    private var currentDocument = CanvasDocument.default()
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

    override fun onAttach(context: Context) {
        super.onAttach(context)
        host = context as? Host
        appSettings = AppSettings(context.applicationContext)
        documentRenderer = CanvasDocumentRenderer(context, context.contentResolver)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        currentDocument = savedInstanceState
            ?.getString(KEY_DOCUMENT_STATE)
            ?.let(CanvasDocumentCodec::decode)
            ?: appSettings.canvasDocumentDraft
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
        binding?.buttonAddTextBlock?.setOnClickListener { showTextBlockDialog() }
        binding?.buttonAddImageBlock?.setOnClickListener { startImageInsert() }
        binding?.buttonAddTableBlock?.setOnClickListener { showTableBlockDialog() }
        binding?.buttonPrintDocument?.setOnClickListener { printDocument() }
        renderDocument()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_DOCUMENT_STATE, CanvasDocumentCodec.encode(currentDocument))
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
        renderBlockCards()
        binding?.buttonPrintDocument?.isEnabled = currentDocument.blocks.isNotEmpty()
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
                    addView(actionRow(block, index))
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

    private fun actionRow(block: DocumentBlock, index: Int): View {
        val context = requireContext()
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(12), 0, 0)
            addView(iconActionButton(R.drawable.ic_edit_24, R.string.edit) {
                when (block) {
                    is TextBlock -> showTextBlockDialog(block)
                    is ImageBlock -> showImageBlockDialog(block)
                    is TableBlock -> showTableBlockDialog(block)
                }
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
            layoutParams = LinearLayout.LayoutParams(
                dp(44),
                dp(44)
            ).also { params ->
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
            is TableBlock -> getString(R.string.block_title_table)
        }
    }

    private fun showTextBlockDialog(existingBlock: TextBlock? = null) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_text_block, null)
        val contentInput = dialogView.findViewById<EditText>(R.id.input_text_block_content)
        val alignmentSpinner = dialogView.findViewById<Spinner>(R.id.spinner_text_block_alignment)
        val fontSpinner = dialogView.findViewById<Spinner>(R.id.spinner_text_block_font)
        val sizeSpinner = dialogView.findViewById<Spinner>(R.id.spinner_text_block_size)
        val boldCheckBox = dialogView.findViewById<CheckBox>(R.id.checkbox_text_block_bold)
        val italicCheckBox = dialogView.findViewById<CheckBox>(R.id.checkbox_text_block_italic)
        val underlineCheckBox = dialogView.findViewById<CheckBox>(R.id.checkbox_text_block_underline)
        val strikethroughCheckBox = dialogView.findViewById<CheckBox>(R.id.checkbox_text_block_strikethrough)

        val block = existingBlock ?: TextBlock(
            id = UUID.randomUUID().toString(),
            text = "",
            alignment = BlockAlignment.LEFT,
            style = TextBlockStyle()
        )

        contentInput.setText(block.text)
        setupSpinner(alignmentSpinner, BlockAlignment.entries.map { it.displayName }, block.alignment.ordinal)
        setupSpinner(fontSpinner, CanvasFontFamily.entries.map { it.displayName }, block.style.fontFamily.ordinal)
        setupSpinner(sizeSpinner, CanvasTextSize.entries.map { it.displayName }, block.style.textSize.ordinal)
        boldCheckBox.isChecked = block.style.isBold
        italicCheckBox.isChecked = block.style.isItalic
        underlineCheckBox.isChecked = block.style.isUnderline
        strikethroughCheckBox.isChecked = block.style.isStrikethrough

        AlertDialog.Builder(requireContext())
            .setTitle(if (existingBlock == null) R.string.text_add_text else R.string.text_edit_text)
            .setView(dialogView)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.save) { _, _ ->
                val updatedBlock = block.copy(
                    text = contentInput.text?.toString().orEmpty(),
                    alignment = BlockAlignment.entries[alignmentSpinner.selectedItemPosition],
                    style = TextBlockStyle(
                        isBold = boldCheckBox.isChecked,
                        isItalic = italicCheckBox.isChecked,
                        isUnderline = underlineCheckBox.isChecked,
                        isStrikethrough = strikethroughCheckBox.isChecked,
                        fontFamily = CanvasFontFamily.entries[fontSpinner.selectedItemPosition],
                        textSize = CanvasTextSize.entries[sizeSpinner.selectedItemPosition]
                    )
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

    private fun showTableBlockDialog(existingBlock: TableBlock? = null) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_table_block, null)
        val rowsInput = dialogView.findViewById<EditText>(R.id.input_table_rows)
        val columnsInput = dialogView.findViewById<EditText>(R.id.input_table_columns)
        val alignmentSpinner = dialogView.findViewById<Spinner>(R.id.spinner_table_alignment)
        val headerCheckBox = dialogView.findViewById<CheckBox>(R.id.checkbox_table_header)
        val cellsInput = dialogView.findViewById<EditText>(R.id.input_table_cells)

        val block = existingBlock ?: TableBlock(
            id = UUID.randomUUID().toString(),
            alignment = BlockAlignment.LEFT,
            rows = 2,
            columns = 2,
            hasHeaderRow = true,
            cells = listOf(
                listOf("Header 1", "Header 2"),
                listOf("Value 1", "Value 2")
            )
        )

        rowsInput.setText(block.rows.toString())
        columnsInput.setText(block.columns.toString())
        setupSpinner(alignmentSpinner, BlockAlignment.entries.map { it.displayName }, block.alignment.ordinal)
        headerCheckBox.isChecked = block.hasHeaderRow
        cellsInput.setText(TableBlockTextCodec.encode(block.cells))

        AlertDialog.Builder(requireContext())
            .setTitle(if (existingBlock == null) R.string.text_add_table else R.string.text_edit_table)
            .setView(dialogView)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.save) { _, _ ->
                val rows = rowsInput.text?.toString()?.toIntOrNull()?.coerceIn(1, 12) ?: block.rows
                val columns = columnsInput.text?.toString()?.toIntOrNull()?.coerceIn(1, 6) ?: block.columns
                val cells = TableBlockTextCodec.decode(cellsInput.text?.toString().orEmpty(), rows, columns)
                val updatedBlock = block.copy(
                    alignment = BlockAlignment.entries[alignmentSpinner.selectedItemPosition],
                    rows = rows,
                    columns = columns,
                    hasHeaderRow = headerCheckBox.isChecked,
                    cells = cells
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
            setupSpinner(spinner, DitheringMode.entries.map { it.displayName }, block.ditheringMode.ordinal)
            container.addView(
                TextView(requireContext()).apply {
                    text = getString(R.string.image_block_dithering_label)
                    setPadding(0, dp(16), 0, dp(8))
                }
            )
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
                            ditheringMode = DitheringMode.entries[ditheringSpinner.selectedItemPosition]
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
            val existingAlignment = existingImageBlock?.alignment ?: BlockAlignment.CENTER
            val existingDitheringMode = existingImageBlock?.ditheringMode ?: DitheringMode.FLOYD_STEINBERG
            CanvasDocumentEditor.replaceBlock(
                currentDocument,
                newBlock.copy(
                    alignment = existingAlignment,
                    ditheringMode = existingDitheringMode
                )
            )
        }
        updateDocument(updatedDocument)
        appendLog("Prepared document image block from $editedUri.")
    }

    private fun printDocument() {
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
        private const val KEY_DOCUMENT_STATE = "document_state"
        private const val PRINT_RENDER_WIDTH_PX = 384
    }
}
