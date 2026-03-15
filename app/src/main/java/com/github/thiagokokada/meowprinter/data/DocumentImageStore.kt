package com.github.thiagokokada.meowprinter.data

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID
import androidx.core.net.toUri

class DocumentImageStore(private val context: Context) {
    data class StoredImage(
        val mimeType: String,
        val bytes: ByteArray
    )

    private val contentResolver: ContentResolver = context.contentResolver
    private val authority = "${context.packageName}.fileprovider"
    private val imageDirectory = File(context.filesDir, IMAGE_DIRECTORY_NAME).apply {
        mkdirs()
    }

    fun persistImageFromUri(sourceUri: Uri): String {
        val mimeType = contentResolver.getType(sourceUri) ?: DEFAULT_MIME_TYPE
        val bytes = contentResolver.openInputStream(sourceUri)?.use { it.readBytes() }
            ?: error("Unable to read image data from $sourceUri")
        return persistImageBytes(bytes, mimeType)
    }

    fun persistEmbeddedImage(mimeType: String, bytes: ByteArray): String {
        return persistImageBytes(bytes, mimeType)
    }

    fun readImage(imageUri: String): StoredImage {
        val uri = imageUri.toUri()
        val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error("Unable to read document image from $imageUri")
        val mimeType = contentResolver.getType(uri)
            ?: mimeTypeFromPath(uri.lastPathSegment)
            ?: DEFAULT_MIME_TYPE
        return StoredImage(
            mimeType = mimeType,
            bytes = bytes
        )
    }

    fun deleteManagedImage(imageUri: String) {
        val uri = imageUri.toUri()
        if (uri.scheme != ContentResolver.SCHEME_CONTENT || uri.authority != authority) {
            return
        }
        val path = uri.path.orEmpty()
        if (!path.contains("/$IMAGE_DIRECTORY_NAME/")) {
            return
        }
        val file = File(context.filesDir, path.substringAfter("/$IMAGE_DIRECTORY_NAME/").let {
            "$IMAGE_DIRECTORY_NAME/$it"
        })
        if (file.exists()) {
            file.delete()
        }
    }

    private fun persistImageBytes(bytes: ByteArray, mimeType: String): String {
        val extension = extensionForMimeType(mimeType)
        val destinationFile = File(imageDirectory, "${UUID.randomUUID()}.$extension")
        destinationFile.outputStream().use { it.write(bytes) }
        return FileProvider.getUriForFile(context, authority, destinationFile).toString()
    }

    private fun extensionForMimeType(mimeType: String): String {
        return MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
            ?: when (mimeType.lowercase()) {
                "image/jpeg" -> "jpg"
                "image/webp" -> "webp"
                "image/png" -> "png"
                else -> DEFAULT_EXTENSION
            }
    }

    private fun mimeTypeFromPath(path: String?): String? {
        val extension = path
            ?.substringAfterLast('.', missingDelimiterValue = "")
            ?.lowercase()
            ?.takeIf(String::isNotBlank)
            ?: return null
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
    }

    companion object {
        private const val IMAGE_DIRECTORY_NAME = "document_images"
        private const val DEFAULT_EXTENSION = "jpg"
        private const val DEFAULT_MIME_TYPE = "image/jpeg"
    }
}
