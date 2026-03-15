package com.github.thiagokokada.meowprinter.ui

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.canhub.cropper.CropImageView
import com.github.thiagokokada.meowprinter.R
import com.google.android.material.appbar.MaterialToolbar
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

@RunWith(AndroidJUnit4::class)
class ImageCropActivityTest {
    private var scenario: ActivityScenario<ImageCropActivity>? = null

    @After
    fun tearDown() {
        scenario?.close()
    }

    @Test
    fun launchShowsCropperAndToolbar() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val sourceUri = createBitmapFile(context.cacheDir, "crop-source-test.jpg")
        val outputUri = Uri.fromFile(File(context.cacheDir, "crop-output-test.jpg"))

        scenario = ActivityScenario.launch(
            ImageCropActivity.intent(context, sourceUri, outputUri)
        )

        scenario?.onActivity { activity ->
            val toolbar = activity.findViewById<MaterialToolbar>(R.id.toolbar)
            val cropView = activity.findViewById<CropImageView>(R.id.crop_image_view)

            assertEquals(View.VISIBLE, cropView.visibility)
            assertNotNull(toolbar.navigationIcon)
        }
    }

    private fun createBitmapFile(cacheDir: File, name: String): Uri {
        val file = File(cacheDir, name)
        val bitmap = Bitmap.createBitmap(32, 24, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.WHITE)
            setPixel(8, 8, Color.BLACK)
            setPixel(24, 16, Color.BLACK)
        }
        FileOutputStream(file).use { output ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output)
        }
        bitmap.recycle()
        return Uri.fromFile(file)
    }
}
