package com.github.thiagokokada.meowprinter.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.canhub.cropper.CropImageView
import com.github.thiagokokada.meowprinter.R
import com.github.thiagokokada.meowprinter.databinding.ActivityImageCropBinding
import com.google.android.material.color.MaterialColors
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.iconics.typeface.library.fontawesome.FontAwesome

class ImageCropActivity : AppCompatActivity() {
    private enum class CropAction(
        val menuId: Int,
        val icon: FontAwesome.Icon,
        val titleRes: Int,
    ) {
        RotateLeft(1, FontAwesome.Icon.faw_undo, R.string.image_crop_rotate_left),
        RotateRight(2, FontAwesome.Icon.faw_redo, R.string.image_crop_rotate_right),
        Save(3, FontAwesome.Icon.faw_check, R.string.image_crop_save),
    }

    private lateinit var binding: ActivityImageCropBinding
    private lateinit var sourceUri: Uri
    private lateinit var outputUri: Uri

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityImageCropBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.applyTopSystemBarPadding()
        binding.cropImageView.applySideAndBottomSystemBarsPadding()

        sourceUri = intent.getParcelableExtra(EXTRA_SOURCE_URI, Uri::class.java)
            ?: return finishWithError(getString(R.string.image_crop_source_missing))
        outputUri = intent.getParcelableExtra(EXTRA_OUTPUT_URI, Uri::class.java)
            ?: return finishWithError(getString(R.string.image_crop_output_missing))

        binding.toolbar.setNavigationOnClickListener {
            setResult(RESULT_CANCELED)
            finish()
        }
        bindToolbarActions()

        binding.cropImageView.guidelines = CropImageView.Guidelines.ON_TOUCH
        binding.cropImageView.setFixedAspectRatio(false)
        binding.cropImageView.setOnCropImageCompleteListener { _, result ->
            binding.toolbar.menu.setGroupEnabled(0, true)
            if (!result.isSuccessful) {
                finishWithError(result.error?.message ?: getString(R.string.image_crop_failed))
                return@setOnCropImageCompleteListener
            }
            val editedUri = result.uriContent ?: outputUri
            setResult(RESULT_OK, Intent().setData(editedUri))
            finish()
        }
        binding.cropImageView.setImageUriAsync(sourceUri)
    }

    private fun saveCroppedImage() {
        binding.toolbar.menu.setGroupEnabled(0, false)
        binding.cropImageView.croppedImageAsync(
            saveCompressFormat = android.graphics.Bitmap.CompressFormat.JPEG,
            saveCompressQuality = 90,
            customOutputUri = outputUri,
        )
    }

    private fun bindToolbarActions() {
        val iconColor = MaterialColors.getColor(
            this,
            com.google.android.material.R.attr.colorOnSurface,
            0
        )
        val iconSizePx = dp(18)
        val menu = binding.toolbar.menu
        CropAction.entries.forEach { action ->
            menu.add(Menu.NONE, action.menuId, Menu.NONE, action.titleRes).apply {
                icon = IconicsDrawable(this@ImageCropActivity, action.icon).apply {
                    sizeXPx = iconSizePx
                    sizeYPx = iconSizePx
                    setTint(iconColor)
                }
                setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
            }
        }
        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                CropAction.RotateLeft.menuId -> {
                    binding.cropImageView.rotateImage(-90)
                    true
                }
                CropAction.RotateRight.menuId -> {
                    binding.cropImageView.rotateImage(90)
                    true
                }
                CropAction.Save.menuId -> {
                    saveCroppedImage()
                    true
                }
                else -> false
            }
        }
    }

    private fun finishWithError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        setResult(RESULT_CANCELED)
        finish()
    }

    companion object {
        private const val EXTRA_SOURCE_URI = "source_uri"
        private const val EXTRA_OUTPUT_URI = "output_uri"

        fun intent(context: Context, sourceUri: Uri, outputUri: Uri): Intent {
            return Intent(context, ImageCropActivity::class.java).apply {
                putExtra(EXTRA_SOURCE_URI, sourceUri)
                putExtra(EXTRA_OUTPUT_URI, outputUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }
}
