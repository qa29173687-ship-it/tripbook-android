package tw.tripbook.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream

/** Stores 我要買 photos in app storage, shrunk to ~1600px JPEG to save space. */
object PhotoStore {
    private const val MAX = 1600

    fun dir(ctx: Context) = File(ctx.filesDir, "shopping").apply { mkdirs() }

    fun newFile(ctx: Context) = File(dir(ctx), "s_${System.currentTimeMillis()}.jpg")

    /** Copy a picked image into app storage (shrunk). Returns the saved path. */
    fun importUri(ctx: Context, uri: Uri): String? = runCatching {
        val raw = File(ctx.cacheDir, "pick_${System.currentTimeMillis()}")
        ctx.contentResolver.openInputStream(uri)?.use { i -> FileOutputStream(raw).use { i.copyTo(it) } } ?: return null
        val out = newFile(ctx)
        shrink(raw, out)
        raw.delete()
        out.absolutePath
    }.getOrNull()

    /** Shrink a camera capture in place. */
    fun shrinkInPlace(file: File): String? = runCatching {
        val tmp = File(file.parentFile, file.name + ".tmp")
        shrink(file, tmp)
        file.delete(); tmp.renameTo(file)
        file.absolutePath
    }.getOrNull()

    private fun shrink(src: File, dst: File) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(src.absolutePath, bounds)
        var sample = 1
        while (bounds.outWidth / sample > MAX * 2 || bounds.outHeight / sample > MAX * 2) sample *= 2
        var bmp = BitmapFactory.decodeFile(src.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: error("decode failed")
        val scale = MAX.toFloat() / maxOf(bmp.width, bmp.height)
        if (scale < 1f) bmp = Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true)
        val rot = runCatching {
            when (ExifInterface(src.absolutePath).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        }.getOrDefault(0f)
        if (rot != 0f) bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(rot) }, true)
        FileOutputStream(dst).use { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) }
    }

    /** Decode a small bitmap for display. */
    fun load(path: String, maxPx: Int): Bitmap? = runCatching {
        val b = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, b)
        var s = 1
        while (b.outWidth / (s * 2) >= maxPx && b.outHeight / (s * 2) >= maxPx) s *= 2
        BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = s })
    }.getOrNull()
}
