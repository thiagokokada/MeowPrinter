package com.github.thiagokokada.meowprinter.ui

import android.content.Intent
import android.net.Uri

sealed interface SharedImportRequest {
    data object None : SharedImportRequest

    data class Image(
        val uri: Uri,
    ) : SharedImportRequest

    data class Text(
        val value: String,
    ) : SharedImportRequest
}

data class SharedImportInput(
    val action: String?,
    val mimeType: String?,
    val extraStreamUri: String?,
    val clipDataUri: String?,
    val extraText: String?,
    val clipDataText: String?,
)

sealed interface SharedImportResolution {
    data object None : SharedImportResolution

    data class Image(
        val uri: String,
    ) : SharedImportResolution

    data class Text(
        val value: String,
    ) : SharedImportResolution
}

object SharedImportRequestParser {
    fun parse(intent: Intent, clipDataText: String? = null): SharedImportRequest {
        val input = SharedImportInput(
            action = intent.action,
            mimeType = intent.type,
            extraStreamUri = intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)?.toString(),
            clipDataUri = intent.clipData
                ?.takeIf { it.itemCount > 0 }
                ?.getItemAt(0)
                ?.uri
                ?.toString(),
            extraText = intent.getStringExtra(Intent.EXTRA_TEXT),
            clipDataText = clipDataText
        )
        return when (val resolution = resolve(input)) {
            is SharedImportResolution.Image -> SharedImportRequest.Image(Uri.parse(resolution.uri))
            SharedImportResolution.None -> SharedImportRequest.None
            is SharedImportResolution.Text -> SharedImportRequest.Text(resolution.value)
        }
    }

    fun resolve(input: SharedImportInput): SharedImportResolution {
        if (input.action != Intent.ACTION_SEND) {
            return SharedImportResolution.None
        }

        val isImageShare = input.mimeType?.startsWith("image/") == true
        if (isImageShare) {
            val imageUri = input.extraStreamUri ?: input.clipDataUri
            if (imageUri != null) {
                return SharedImportResolution.Image(imageUri)
            }
        }

        val isTextShare = input.mimeType?.startsWith("text/") == true
        if (!isTextShare) {
            return SharedImportResolution.None
        }

        val sharedText = (input.extraText ?: input.clipDataText)
            ?.takeIf(String::isNotBlank)
            ?: return SharedImportResolution.None
        return SharedImportResolution.Text(sharedText)
    }
}
