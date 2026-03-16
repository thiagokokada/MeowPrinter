package com.github.thiagokokada.meowprinter.ui

import android.app.AlertDialog
import android.content.Context
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.AppCompatImageButton
import androidx.core.content.FileProvider
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import com.github.thiagokokada.meowprinter.R
import com.github.thiagokokada.meowprinter.data.AppSettings
import com.github.thiagokokada.meowprinter.data.DocumentImageStore
import com.github.thiagokokada.meowprinter.data.LogStore
import com.github.thiagokokada.meowprinter.databinding.FragmentTextBinding
import com.github.thiagokokada.meowprinter.document.BlockAlignment
import com.github.thiagokokada.meowprinter.document.CanvasDocument
import com.github.thiagokokada.meowprinter.document.CanvasDocumentCodec
import com.github.thiagokokada.meowprinter.document.CanvasDocumentEditor
import com.github.thiagokokada.meowprinter.document.CanvasDocumentRenderer
import com.github.thiagokokada.meowprinter.document.CanvasTextFont
import com.github.thiagokokada.meowprinter.document.CanvasTextSize
import com.github.thiagokokada.meowprinter.document.CanvasTextWeight
import com.github.thiagokokada.meowprinter.document.CalendarQrPayload
import com.github.thiagokokada.meowprinter.document.ContactQrPayload
import com.github.thiagokokada.meowprinter.document.DocumentBlock
import com.github.thiagokokada.meowprinter.document.EmailQrPayload
import com.github.thiagokokada.meowprinter.document.GeoQrPayload
import com.github.thiagokokada.meowprinter.document.ImageBlock
import com.github.thiagokokada.meowprinter.document.ImageBlockWidth
import com.github.thiagokokada.meowprinter.document.PhoneQrPayload
import com.github.thiagokokada.meowprinter.document.QrBlock
import com.github.thiagokokada.meowprinter.document.QrBlockSize
import com.github.thiagokokada.meowprinter.document.QrContentType
import com.github.thiagokokada.meowprinter.document.QrPayload
import com.github.thiagokokada.meowprinter.document.QrWifiSecurity
import com.github.thiagokokada.meowprinter.document.SmsQrPayload
import com.github.thiagokokada.meowprinter.document.TextBlock
import com.github.thiagokokada.meowprinter.document.TextQrPayload
import com.github.thiagokokada.meowprinter.document.UrlQrPayload
import com.github.thiagokokada.meowprinter.document.WifiQrPayload
import com.github.thiagokokada.meowprinter.image.DitheringMode
import com.github.thiagokokada.meowprinter.image.ImagePrintPreparer
import com.github.thiagokokada.meowprinter.image.ImageProcessingMode
import com.github.thiagokokada.meowprinter.image.ImageResizerMode
import com.github.thiagokokada.meowprinter.image.PreviewBitmapScaler
import com.github.thiagokokada.meowprinter.image.PreparedPrintImage
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.iconics.typeface.IIcon
import com.mikepenz.iconics.typeface.library.fontawesome.FontAwesome
import org.json.JSONObject
import java.io.File
import java.util.UUID

class TextFragment : Fragment(R.layout.fragment_text) {
    interface Host {
        fun printPreparedImage(preparedImage: PreparedPrintImage, sourceLabel: String)
        fun connectionSummary(): ConnectionSummary
        fun refreshPrinterConnection()
        fun isPrintInProgress(): Boolean
    }

    private var binding: FragmentTextBinding? = null
    private var host: Host? = null
    private lateinit var appSettings: AppSettings
    private lateinit var documentImageStore: DocumentImageStore
    private lateinit var documentRenderer: CanvasDocumentRenderer
    private var previewDialog: AlertDialog? = null
    private var qrDialog: AlertDialog? = null
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
                val editedUri = result.data?.data
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
                Toast.makeText(requireContext(), R.string.text_image_edit_failed, Toast.LENGTH_SHORT).show()
                appendLog("Text editor image flow failed.")
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
        documentImageStore = DocumentImageStore(context.applicationContext)
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
        binding?.buttonAddQrBlock?.setOnClickListener { showQrBlockDialog() }
        binding?.buttonNewDocument?.setOnClickListener { confirmStartNewDocument() }
        binding?.buttonSaveDocument?.setOnClickListener {
            saveDocumentLauncher.launch(suggestedDocumentFileName())
        }
        binding?.buttonLoadDocument?.setOnClickListener {
            loadDocumentLauncher.launch(arrayOf(DOCUMENT_MIME_TYPE))
        }
        binding?.buttonPreviewDocument?.setOnClickListener { previewDocument() }
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
        previewDialog?.dismiss()
        previewDialog = null
        qrDialog?.dismiss()
        qrDialog = null
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

    fun appendSharedQrPayload(payload: QrPayload) {
        appendBlock(
            QrBlock(
                id = UUID.randomUUID().toString(),
                payload = payload,
                alignment = BlockAlignment.CENTER,
                size = QrBlockSize.MEDIUM
            )
        )
    }

    fun appendSharedTextBlock(markdown: String) {
        appendBlock(
            TextBlock(
                id = UUID.randomUUID().toString(),
                markdown = markdown,
                alignment = BlockAlignment.LEFT,
                textSize = CanvasTextSize.SP12,
                textFont = CanvasTextFont.SANS_SERIF
            )
        )
    }

    fun appendSharedImage(sourceUri: Uri) {
        pendingImageTargetBlockId = null
        launchImageEditor(sourceUri)
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
        val contentView = documentRenderer.createPreviewBlockView(block, widthPx)
        val topMargin = dp(12)
        val context = requireContext()
        return when (block) {
            is TextBlock -> MaterialCardView(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).also { params ->
                    params.topMargin = topMargin
                }
                radius = dp(16).toFloat()
                strokeWidth = dp(1)
                setStrokeColor(
                    MaterialColors.getColor(
                        context,
                        com.google.android.material.R.attr.colorOutlineVariant,
                        0
                    )
                )
                setCardBackgroundColor(
                    MaterialColors.getColor(
                        context,
                        com.google.android.material.R.attr.colorSurface,
                        0
                    )
                )
                addView(contentView.apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                    setPadding(dp(14), dp(14), dp(14), dp(14))
                })
            }

            is ImageBlock -> MaterialCardView(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).also { params ->
                    params.topMargin = topMargin
                }
                radius = dp(18).toFloat()
                setCardBackgroundColor(
                    MaterialColors.getColor(
                        context,
                        com.google.android.material.R.attr.colorSurface,
                        0
                    )
                )
                addView(contentView)
            }

            is QrBlock -> MaterialCardView(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).also { params ->
                    params.topMargin = topMargin
                }
                radius = dp(18).toFloat()
                setCardBackgroundColor(
                    MaterialColors.getColor(
                        context,
                        com.google.android.material.R.attr.colorSurface,
                        0
                    )
                )
                addView(contentView)
            }
        }
    }

    private fun actionRow(block: DocumentBlock): View {
        val context = requireContext()
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(12), 0, 0)
            addView(iconActionButton(FontAwesome.Icon.faw_pen, R.string.edit) {
                when (block) {
                    is TextBlock -> showTextBlockDialog(block)
                    is ImageBlock -> showImageBlockDialog(block)
                    is QrBlock -> showQrBlockDialog(block)
                }
            })
            addView(iconActionButton(FontAwesome.Icon.faw_clone, R.string.duplicate) {
                mutateDocument { CanvasDocumentEditor.duplicateBlock(it, block.id) }
            })
            addView(iconActionButton(FontAwesome.Icon.faw_arrow_up, R.string.move_up) {
                mutateDocument { CanvasDocumentEditor.moveBlock(it, block.id, -1) }
            })
            addView(iconActionButton(FontAwesome.Icon.faw_arrow_down, R.string.move_down) {
                mutateDocument { CanvasDocumentEditor.moveBlock(it, block.id, 1) }
            })
            addView(iconActionButton(FontAwesome.Icon.faw_trash_alt, R.string.delete) {
                mutateDocument { CanvasDocumentEditor.removeBlock(it, block.id) }
            })
        }
    }

    private fun iconActionButton(icon: IIcon, descriptionRes: Int, onClick: () -> Unit): View {
        val context = requireContext()
        val iconColor = MaterialColors.getColor(
            context,
            com.google.android.material.R.attr.colorOnSurfaceVariant,
            0
        )
        return AppCompatImageButton(context).apply {
            setImageDrawable(
                IconicsDrawable(context, icon).apply {
                    sizeXPx = dp(18)
                    sizeYPx = dp(18)
                    tint = ColorStateList.valueOf(iconColor)
                }
            )
            contentDescription = getString(descriptionRes)
            background = null
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
            is QrBlock -> getString(R.string.block_title_qr)
        }
    }

    private fun showTextBlockDialog(existingBlock: TextBlock? = null) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_text_block, null)
        val contentInput = dialogView.findViewById<EditText>(R.id.input_text_block_content)
        val alignmentSpinner = dialogView.findViewById<Spinner>(R.id.spinner_text_block_alignment)
        val sizeSpinner = dialogView.findViewById<Spinner>(R.id.spinner_text_block_size)
        val fontSpinner = dialogView.findViewById<Spinner>(R.id.spinner_text_block_font)
        val weightSpinner = dialogView.findViewById<Spinner>(R.id.spinner_text_block_weight)

        val block = existingBlock ?: TextBlock(
            id = UUID.randomUUID().toString(),
            markdown = "",
            alignment = BlockAlignment.LEFT,
            textSize = CanvasTextSize.SP12,
            textFont = CanvasTextFont.SANS_SERIF,
            textWeight = CanvasTextWeight.NORMAL
        )

        contentInput.setText(block.markdown)
        setupSpinner(alignmentSpinner, BlockAlignment.entries.map { it.displayName }, block.alignment.ordinal)
        setupSpinner(sizeSpinner, CanvasTextSize.entries.map { it.displayName }, block.textSize.ordinal)
        setupSpinner(fontSpinner, CanvasTextFont.entries.map { it.displayName }, block.textFont.ordinal)
        setupSpinner(weightSpinner, CanvasTextWeight.entries.map { it.displayName }, block.textWeight.ordinal)
        bindMarkdownHelperButtons(dialogView, contentInput)

        AlertDialog.Builder(requireContext())
            .setTitle(if (existingBlock == null) R.string.text_add_text else R.string.text_edit_text)
            .setView(dialogView)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.save) { _, _ ->
                val updatedBlock = block.copy(
                    markdown = contentInput.text?.toString().orEmpty(),
                    alignment = BlockAlignment.entries[alignmentSpinner.selectedItemPosition],
                    textSize = CanvasTextSize.entries[sizeSpinner.selectedItemPosition],
                    textFont = CanvasTextFont.entries[fontSpinner.selectedItemPosition],
                    textWeight = CanvasTextWeight.entries[weightSpinner.selectedItemPosition]
                )
                upsertBlock(updatedBlock, existingBlock == null)
            }
            .show()
    }

    private fun bindMarkdownHelperButtons(dialogView: View, contentInput: EditText) {
        bindMarkdownHelperIcons(dialogView)
        dialogView.findViewById<View>(R.id.button_markdown_h1).setOnClickListener {
            applyMarkdownEdit(contentInput) { text, start, end ->
                MarkdownSnippetFormatter.heading1(text, start, end)
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
        dialogView.findViewById<View>(R.id.button_markdown_numbered_list).setOnClickListener {
            applyMarkdownEdit(contentInput) { text, start, end ->
                MarkdownSnippetFormatter.numberedList(text, start, end)
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

    private fun bindMarkdownHelperIcons(dialogView: View) {
        val iconColor = MaterialColors.getColor(dialogView, com.google.android.material.R.attr.colorOnSurface)
        val iconSizePx = dp(20)
        val icons = listOf(
            R.id.button_markdown_h1 to FontAwesome.Icon.faw_heading,
            R.id.button_markdown_bold to FontAwesome.Icon.faw_bold,
            R.id.button_markdown_italic to FontAwesome.Icon.faw_italic,
            R.id.button_markdown_list to FontAwesome.Icon.faw_list_ul,
            R.id.button_markdown_numbered_list to FontAwesome.Icon.faw_list_ol,
            R.id.button_markdown_quote to FontAwesome.Icon.faw_quote_right,
            R.id.button_markdown_table to FontAwesome.Icon.faw_table
        )
        icons.forEach { (buttonId, icon) ->
            dialogView.findViewById<MaterialButton>(buttonId).icon = IconicsDrawable(requireContext(), icon).apply {
                sizeXPx = iconSizePx
                sizeYPx = iconSizePx
                tint = ColorStateList.valueOf(iconColor)
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
        val processingSpinner = Spinner(requireContext()).also { spinner ->
            container.addView(
                TextView(requireContext()).apply {
                    text = getString(R.string.image_processing_label)
                    setPadding(0, dp(16), 0, dp(8))
                }
            )
            setupSpinner(spinner, ImageProcessingMode.entries.map { it.displayName }, block.processingMode.ordinal)
            container.addView(spinner)
        }
        val resizerSpinner = Spinner(requireContext()).also { spinner ->
            container.addView(
                TextView(requireContext()).apply {
                    text = getString(R.string.image_resizer_label)
                    setPadding(0, dp(16), 0, dp(8))
                }
            )
            setupSpinner(spinner, ImageResizerMode.entries.map { it.displayName }, block.resizerMode.ordinal)
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
                replaceBlock(
                    block.copy(
                        alignment = BlockAlignment.entries[alignmentSpinner.selectedItemPosition],
                        ditheringMode = DitheringMode.entries[ditheringSpinner.selectedItemPosition],
                        processingMode = ImageProcessingMode.entries[processingSpinner.selectedItemPosition],
                        resizerMode = ImageResizerMode.entries[resizerSpinner.selectedItemPosition],
                        width = ImageBlockWidth.entries[widthSpinner.selectedItemPosition]
                    )
                )
            }
            .show()
    }

    private fun showQrBlockDialog(existingBlock: QrBlock? = null) {
        val context = requireContext()
        val block = existingBlock ?: QrBlock(
            id = UUID.randomUUID().toString(),
            payload = TextQrPayload(""),
            alignment = BlockAlignment.CENTER,
            size = QrBlockSize.MEDIUM
        )
        val contentContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(8), dp(8), 0)
        }
        val scrollView = ScrollView(context).apply {
            addView(contentContainer)
        }

        contentContainer.addView(dialogLabel(R.string.qr_type_label))
        val typeSpinner = Spinner(context).also { spinner ->
            setupSpinner(spinner, QrContentType.entries.map { it.displayName }, block.payload.type.ordinal)
            contentContainer.addView(spinner)
        }

        contentContainer.addView(dialogLabel(R.string.text_block_alignment_label))
        val alignmentSpinner = Spinner(context).also { spinner ->
            setupSpinner(spinner, BlockAlignment.entries.map { it.displayName }, block.alignment.ordinal)
            contentContainer.addView(spinner)
        }

        contentContainer.addView(dialogLabel(R.string.qr_size_label))
        val sizeSpinner = Spinner(context).also { spinner ->
            setupSpinner(spinner, QrBlockSize.entries.map { it.displayName }, block.size.ordinal)
            contentContainer.addView(spinner)
        }

        val payloadContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        contentContainer.addView(payloadContainer)

        val textInput = qrInput(
            initialValue = (block.payload as? TextQrPayload)?.text.orEmpty(),
            labelRes = R.string.qr_text_label,
            multiline = true
        )
        val urlInput = qrInput(initialValue = (block.payload as? UrlQrPayload)?.url.orEmpty(), labelRes = R.string.qr_url_label)
        val wifiPayload = block.payload as? WifiQrPayload
        val wifiSsidInput = qrInput(initialValue = wifiPayload?.ssid.orEmpty(), labelRes = R.string.qr_wifi_ssid_label)
        val wifiPasswordInput = qrInput(initialValue = wifiPayload?.password.orEmpty(), labelRes = R.string.qr_wifi_password_label)
        val wifiHiddenInput = CheckBox(context).apply {
            text = getString(R.string.qr_wifi_hidden_label)
            isChecked = wifiPayload?.hidden == true
        }
        val wifiSecuritySpinner = Spinner(context).also { spinner ->
            setupSpinner(
                spinner,
                QrWifiSecurity.entries.map { it.displayName },
                (wifiPayload?.security ?: QrWifiSecurity.WPA).ordinal
            )
        }
        val phoneInput = qrInput(initialValue = (block.payload as? PhoneQrPayload)?.number.orEmpty(), labelRes = R.string.qr_phone_label)
        val emailPayload = block.payload as? EmailQrPayload
        val emailToInput = qrInput(initialValue = emailPayload?.to.orEmpty(), labelRes = R.string.qr_email_to_label)
        val emailSubjectInput = qrInput(initialValue = emailPayload?.subject.orEmpty(), labelRes = R.string.qr_email_subject_label)
        val emailBodyInput = qrInput(initialValue = emailPayload?.body.orEmpty(), labelRes = R.string.qr_email_body_label, multiline = true)
        val smsPayload = block.payload as? SmsQrPayload
        val smsNumberInput = qrInput(initialValue = smsPayload?.number.orEmpty(), labelRes = R.string.qr_sms_number_label)
        val smsMessageInput = qrInput(initialValue = smsPayload?.message.orEmpty(), labelRes = R.string.qr_sms_message_label, multiline = true)
        val geoPayload = block.payload as? GeoQrPayload
        val geoLatitudeInput = qrInput(initialValue = geoPayload?.latitude.orEmpty(), labelRes = R.string.qr_geo_latitude_label, inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED)
        val geoLongitudeInput = qrInput(initialValue = geoPayload?.longitude.orEmpty(), labelRes = R.string.qr_geo_longitude_label, inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED)
        val geoQueryInput = qrInput(initialValue = geoPayload?.query.orEmpty(), labelRes = R.string.qr_geo_query_label)
        val contactPayload = block.payload as? ContactQrPayload
        val contactNameInput = qrInput(initialValue = contactPayload?.name.orEmpty(), labelRes = R.string.qr_contact_name_label)
        val contactPhoneInput = qrInput(initialValue = contactPayload?.phone.orEmpty(), labelRes = R.string.qr_contact_phone_label)
        val contactEmailInput = qrInput(initialValue = contactPayload?.email.orEmpty(), labelRes = R.string.qr_contact_email_label)
        val contactOrgInput = qrInput(initialValue = contactPayload?.organization.orEmpty(), labelRes = R.string.qr_contact_organization_label)
        val contactAddressInput = qrInput(initialValue = contactPayload?.address.orEmpty(), labelRes = R.string.qr_contact_address_label, multiline = true)
        val contactUrlInput = qrInput(initialValue = contactPayload?.url.orEmpty(), labelRes = R.string.qr_contact_url_label)
        val calendarPayload = block.payload as? CalendarQrPayload
        val calendarTitleInput = qrInput(initialValue = calendarPayload?.title.orEmpty(), labelRes = R.string.qr_calendar_title_label)
        val calendarStartInput = qrInput(initialValue = calendarPayload?.start.orEmpty(), labelRes = R.string.qr_calendar_start_label)
        val calendarEndInput = qrInput(initialValue = calendarPayload?.end.orEmpty(), labelRes = R.string.qr_calendar_end_label)
        val calendarLocationInput = qrInput(initialValue = calendarPayload?.location.orEmpty(), labelRes = R.string.qr_calendar_location_label)
        val calendarDescriptionInput = qrInput(initialValue = calendarPayload?.description.orEmpty(), labelRes = R.string.qr_calendar_description_label, multiline = true)

        fun section(vararg views: View): LinearLayout {
            return LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                views.forEach(::addView)
            }
        }

        val payloadSections = mapOf(
            QrContentType.TEXT to section(textInput),
            QrContentType.URL to section(urlInput),
            QrContentType.WIFI to section(
                wifiSsidInput,
                wifiPasswordInput,
                dialogLabel(R.string.qr_wifi_security_label),
                wifiSecuritySpinner,
                wifiHiddenInput
            ),
            QrContentType.PHONE to section(phoneInput),
            QrContentType.EMAIL to section(emailToInput, emailSubjectInput, emailBodyInput),
            QrContentType.SMS to section(smsNumberInput, smsMessageInput),
            QrContentType.GEO to section(geoLatitudeInput, geoLongitudeInput, geoQueryInput),
            QrContentType.CONTACT to section(
                contactNameInput,
                contactPhoneInput,
                contactEmailInput,
                contactOrgInput,
                contactAddressInput,
                contactUrlInput
            ),
            QrContentType.CALENDAR to section(
                calendarTitleInput,
                calendarStartInput,
                calendarEndInput,
                calendarLocationInput,
                calendarDescriptionInput
            )
        )
        payloadSections.values.forEach(payloadContainer::addView)

        fun selectedQrPayload(): QrPayload {
            return when (QrContentType.entries[typeSpinner.selectedItemPosition]) {
                QrContentType.TEXT -> TextQrPayload(textInput.textValue())
                QrContentType.URL -> UrlQrPayload(urlInput.textValue())
                QrContentType.WIFI -> WifiQrPayload(
                    ssid = wifiSsidInput.textValue(),
                    password = wifiPasswordInput.textValue(),
                    security = QrWifiSecurity.entries[wifiSecuritySpinner.selectedItemPosition],
                    hidden = wifiHiddenInput.isChecked
                )
                QrContentType.PHONE -> PhoneQrPayload(phoneInput.textValue())
                QrContentType.EMAIL -> EmailQrPayload(
                    to = emailToInput.textValue(),
                    subject = emailSubjectInput.textValue(),
                    body = emailBodyInput.textValue()
                )
                QrContentType.SMS -> SmsQrPayload(
                    number = smsNumberInput.textValue(),
                    message = smsMessageInput.textValue()
                )
                QrContentType.GEO -> GeoQrPayload(
                    latitude = geoLatitudeInput.textValue(),
                    longitude = geoLongitudeInput.textValue(),
                    query = geoQueryInput.textValue()
                )
                QrContentType.CONTACT -> ContactQrPayload(
                    name = contactNameInput.textValue(),
                    phone = contactPhoneInput.textValue(),
                    email = contactEmailInput.textValue(),
                    organization = contactOrgInput.textValue(),
                    address = contactAddressInput.textValue(),
                    url = contactUrlInput.textValue()
                )
                QrContentType.CALENDAR -> CalendarQrPayload(
                    title = calendarTitleInput.textValue(),
                    start = calendarStartInput.textValue(),
                    end = calendarEndInput.textValue(),
                    location = calendarLocationInput.textValue(),
                    description = calendarDescriptionInput.textValue()
                )
            }
        }

        fun updateVisiblePayloadSection() {
            val selectedType = QrContentType.entries[typeSpinner.selectedItemPosition]
            payloadSections.forEach { (type, view) ->
                view.isVisible = type == selectedType
            }
        }

        typeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                updateVisiblePayloadSection()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        updateVisiblePayloadSection()

        val dialog = AlertDialog.Builder(context)
            .setTitle(if (existingBlock == null) R.string.text_add_qr else R.string.text_edit_qr)
            .setView(scrollView)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.save, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val payload = selectedQrPayload()
                if (!payload.hasMeaningfulContent()) {
                    Toast.makeText(context, R.string.qr_content_required, Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val updatedBlock = block.copy(
                    payload = payload,
                    alignment = BlockAlignment.entries[alignmentSpinner.selectedItemPosition],
                    size = QrBlockSize.entries[sizeSpinner.selectedItemPosition]
                )
                upsertBlock(updatedBlock, existingBlock == null)
                dialog.dismiss()
            }
        }
        qrDialog?.dismiss()
        qrDialog = dialog
        dialog.show()
    }

    private fun startImageInsert() {
        imagePickerLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    private fun launchImageEditor(sourceUri: Uri) {
        val destinationFile = File(
            requireContext().cacheDir,
            "document-image-${System.currentTimeMillis()}.jpg"
        )
        val destinationUri = FileProvider.getUriForFile(
            requireContext(),
            "${requireContext().packageName}.fileprovider",
            destinationFile
        )
        imageEditorLauncher.launch(ImageCropActivity.intent(requireContext(), sourceUri, destinationUri))
    }

    private fun onEditedImageReady(editedUri: Uri) {
        val existingBlockId = pendingImageTargetBlockId
        pendingImageTargetBlockId = null
        val storedImageUri = runCatching {
            documentImageStore.persistImageFromUri(editedUri)
        }.getOrElse { error ->
            Toast.makeText(requireContext(), R.string.text_image_edit_failed, Toast.LENGTH_SHORT).show()
            appendLog("Failed to persist edited image: ${error.message ?: getString(R.string.unknown_error)}")
            return
        }
        val newBlock = ImageBlock(
            id = existingBlockId ?: UUID.randomUUID().toString(),
            imageUri = storedImageUri,
            alignment = BlockAlignment.CENTER,
            processingMode = appSettings.selectedImageProcessingMode,
            resizerMode = appSettings.selectedImageResizerMode
        )

        if (existingBlockId == null) {
            appendBlock(newBlock)
        } else {
            val existingImageBlock = currentDocument.blocks
                .filterIsInstance<ImageBlock>()
                .firstOrNull { it.id == existingBlockId }
            existingImageBlock
                ?.takeIf { block ->
                    currentDocument.blocks
                        .filterIsInstance<ImageBlock>()
                        .count { it.imageUri == block.imageUri } == 1
                }
                ?.let { documentImageStore.deleteManagedImage(it.imageUri) }
            replaceBlock(
                newBlock.copy(
                    alignment = existingImageBlock?.alignment ?: BlockAlignment.CENTER,
                    ditheringMode = existingImageBlock?.ditheringMode ?: DitheringMode.FLOYD_STEINBERG,
                    processingMode = existingImageBlock?.processingMode ?: appSettings.selectedImageProcessingMode,
                    resizerMode = existingImageBlock?.resizerMode ?: appSettings.selectedImageResizerMode,
                    width = existingImageBlock?.width ?: ImageBlockWidth.FULL
                )
            )
        }
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
            val preparedImage = ImagePrintPreparer.prepareRenderedDocument(bitmap)
            host?.printPreparedImage(preparedImage, getString(R.string.text_printing_label))
        }.onFailure { error ->
            Toast.makeText(requireContext(), R.string.text_print_failed, Toast.LENGTH_SHORT).show()
            appendLog("Document render failed before print: ${error.message ?: getString(R.string.unknown_error)}")
        }
    }

    private fun previewDocument() {
        if (currentDocument.blocks.isEmpty()) {
            Toast.makeText(requireContext(), R.string.text_document_empty, Toast.LENGTH_SHORT).show()
            return
        }

        runCatching {
            val renderedBitmap = documentRenderer.renderBitmap(
                document = currentDocument,
                widthPx = PRINT_RENDER_WIDTH_PX,
                mode = CanvasDocumentRenderer.RenderMode.PRINT
            )
            val preparedImage = ImagePrintPreparer.prepareRenderedDocument(renderedBitmap)
            val displayWidth = (resources.displayMetrics.widthPixels - dp(64)).coerceAtLeast(dp(180))
            PreviewBitmapScaler.scaleForDisplay(preparedImage.previewBitmap, displayWidth)
        }.onSuccess { previewBitmap ->
            val dialogView = layoutInflater.inflate(R.layout.dialog_compose_preview, null)
            dialogView.findViewById<ImageView>(R.id.image_compose_preview).setImageBitmap(previewBitmap)
            previewDialog?.dismiss()
            previewDialog = AlertDialog.Builder(requireContext())
                .setTitle(R.string.text_preview_document)
                .setView(dialogView)
                .setPositiveButton(android.R.string.ok, null)
                .show()
        }.onFailure { error ->
            Toast.makeText(requireContext(), R.string.text_preview_failed, Toast.LENGTH_SHORT).show()
            appendLog("Document preview failed: ${error.message ?: getString(R.string.unknown_error)}")
        }
    }

    internal fun isPreviewDialogShowingForTest(): Boolean {
        return previewDialog?.isShowing == true
    }

    internal fun qrDialogForTest(): AlertDialog? = qrDialog

    internal fun hasQrBlockForTest(): Boolean {
        return currentDocument.blocks.any { it is QrBlock }
    }

    internal fun hasTextBlockWithMarkdownForTest(markdown: String): Boolean {
        return currentDocument.blocks
            .filterIsInstance<TextBlock>()
            .any { it.markdown == markdown }
    }

    internal fun appendQrBlockForTest(payload: QrPayload) {
        appendBlock(
            QrBlock(
                id = UUID.randomUUID().toString(),
                payload = payload,
                alignment = BlockAlignment.CENTER,
                size = QrBlockSize.MEDIUM
            )
        )
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
                writer.write(CanvasDocumentCodec.encodeForExport(currentDocument, documentImageStore))
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
            updateDocument(CanvasDocumentCodec.decodeImported(rawDocument, documentImageStore))
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

    private fun mutateDocument(transform: (CanvasDocument) -> CanvasDocument) {
        updateDocument(transform(currentDocument))
    }

    private fun appendBlock(block: DocumentBlock) {
        mutateDocument { CanvasDocumentEditor.appendBlock(it, block) }
    }

    private fun replaceBlock(block: DocumentBlock) {
        mutateDocument { CanvasDocumentEditor.replaceBlock(it, block) }
    }

    private fun upsertBlock(block: DocumentBlock, append: Boolean) {
        if (append) {
            appendBlock(block)
        } else {
            replaceBlock(block)
        }
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

    private fun dialogLabel(textRes: Int): TextView {
        return TextView(requireContext()).apply {
            text = getString(textRes)
            setPadding(0, dp(16), 0, dp(8))
        }
    }

    private fun qrInput(
        initialValue: String,
        labelRes: Int,
        multiline: Boolean = false,
        inputType: Int = InputType.TYPE_CLASS_TEXT
    ): EditText {
        return EditText(requireContext()).apply {
            hint = getString(labelRes)
            tag = resources.getResourceEntryName(labelRes)
            setText(initialValue)
            this.inputType = if (multiline) {
                inputType or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            } else {
                inputType
            }
            if (multiline) {
                minLines = 3
                maxLines = 6
                isSingleLine = false
                setHorizontallyScrolling(false)
                gravity = android.view.Gravity.TOP or android.view.Gravity.START
            }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).also { params ->
                params.topMargin = dp(8)
            }
        }
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

private fun EditText.textValue(): String = text?.toString().orEmpty().trim()

private fun QrPayload.hasMeaningfulContent(): Boolean {
    return when (this) {
        is TextQrPayload -> text.isNotBlank()
        is UrlQrPayload -> url.isNotBlank()
        is WifiQrPayload -> ssid.isNotBlank()
        is PhoneQrPayload -> number.isNotBlank()
        is EmailQrPayload -> to.isNotBlank() || subject.isNotBlank() || body.isNotBlank()
        is SmsQrPayload -> number.isNotBlank() || message.isNotBlank()
        is GeoQrPayload -> latitude.isNotBlank() && longitude.isNotBlank()
        is ContactQrPayload -> listOf(name, phone, email, organization, address, url).any(String::isNotBlank)
        is CalendarQrPayload -> listOf(title, start, end, location, description).any(String::isNotBlank)
    }
}

data class ConnectionSummary(
    val printerName: String,
    val statusText: String,
    val actionLabel: String,
    val actionEnabled: Boolean,
    val isConnected: Boolean
)
