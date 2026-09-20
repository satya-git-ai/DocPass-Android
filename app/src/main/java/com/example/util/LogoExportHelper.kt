package com.example.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.R
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

object LogoExportHelper {

    fun generateLogoBitmap(context: Context, size: Int = 512): Bitmap? {
        return try {
            val drawable = ContextCompat.getDrawable(context, R.drawable.ic_docpass_logo) ?: return null
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
            bitmap
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Downloads/saves the DocPass logo PNG to device storage (Pictures/DocPass).
     */
    fun downloadLogo(context: Context): Boolean {
        val bitmap = generateLogoBitmap(context, 1024) ?: return false
        val filename = "DocPass_Logo_${System.currentTimeMillis()}.png"

        return try {
            var outputStream: OutputStream? = null
            var savedUri: Uri? = null

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/DocPass")
                }
                val resolver = context.contentResolver
                savedUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                if (savedUri != null) {
                    outputStream = resolver.openOutputStream(savedUri)
                }
            } else {
                val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                val docPassDir = File(picturesDir, "DocPass")
                if (!docPassDir.exists()) docPassDir.mkdirs()
                val imageFile = File(docPassDir, filename)
                outputStream = FileOutputStream(imageFile)
            }

            outputStream?.use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }

            Toast.makeText(context, "Logo saved to Pictures/DocPass", Toast.LENGTH_LONG).show()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Failed to download logo: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            false
        }
    }

    /**
     * Shares or exports the DocPass logo PNG file.
     */
    fun shareLogo(context: Context) {
        val bitmap = generateLogoBitmap(context, 1024) ?: return
        try {
            val exportDir = File(context.cacheDir, "shared_exports")
            if (!exportDir.exists()) exportDir.mkdirs()
            val file = File(exportDir, "docpass_logo.png")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "DocPass Logo")
                putExtra(Intent.EXTRA_TEXT, "DocPass Vault Logo")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Share or Save DocPass Logo"))
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Unable to share logo: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }
}
