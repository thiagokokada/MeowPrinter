package com.github.thiagokokada.meowprinter.ui

import android.net.Uri
import com.github.thiagokokada.meowprinter.document.QrPayload

sealed interface SharedImportAction

sealed interface SharedImageImportAction : SharedImportAction {
    data class AddToImagePrint(
        val uri: Uri,
    ) : SharedImageImportAction

    data class AddToCompose(
        val uri: Uri,
    ) : SharedImageImportAction
}

sealed interface SharedTextImportAction : SharedImportAction {
    data class AddAsTextBlock(
        val text: String,
    ) : SharedTextImportAction

    data class AddAsQrCode(
        val payload: QrPayload,
    ) : SharedTextImportAction
}
