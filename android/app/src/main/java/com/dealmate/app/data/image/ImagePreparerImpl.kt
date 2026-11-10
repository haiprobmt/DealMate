package com.dealmate.app.data.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import com.dealmate.app.domain.model.AppError
import com.dealmate.app.domain.model.Outcome
import com.dealmate.app.domain.repository.ImagePreparer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject
import kotlin.math.max

/**
 * Downscales a picked/captured picture (longest side ≤ 1600 px) and re-encodes it as JPEG, lowering
 * the quality until it is below the upload limit. The result is written to the app's cache dir.
 */
class ImagePreparerImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : ImagePreparer {

    override suspend fun prepare(sourceUri: String): Outcome<String> = withContext(Dispatchers.IO) {
        runCatching {
            val uri = Uri.parse(sourceUri)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            require(bounds.outWidth > 0 && bounds.outHeight > 0) { "not an image" }

            val options = BitmapFactory.Options().apply {
                inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, MAX_SIDE)
            }
            val decoded = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
                ?: error("cannot decode")
            val scaled = scaleDown(decoded, MAX_SIDE)
            val bytes = compress(scaled, MAX_BYTES)
            val dir = File(context.cacheDir, IMAGE_DIR).apply { mkdirs() }
            dir.listFiles()?.filter { it.name.startsWith("upload_") }?.forEach { it.delete() }
            val out = File(dir, "upload_${System.currentTimeMillis()}.jpg")
            out.writeBytes(bytes)
            out.absolutePath
        }.fold(
            onSuccess = { Outcome.Success(it) },
            onFailure = { Outcome.Failure(AppError(AppError.Kind.VALIDATION, code = "image_unreadable")) },
        )
    }

    companion object {
        const val IMAGE_DIR = "images"
        const val MAX_SIDE = 1600
        /** Server limit is 5 MB; stay well below it. */
        const val MAX_BYTES = 4 * 1024 * 1024

        fun sampleSize(width: Int, height: Int, maxSide: Int): Int {
            var sample = 1
            while (max(width, height) / (sample * 2) >= maxSide) sample *= 2
            return sample
        }

        fun scaleDown(bitmap: Bitmap, maxSide: Int): Bitmap {
            val longest = max(bitmap.width, bitmap.height)
            if (longest <= maxSide) return bitmap
            val factor = maxSide.toFloat() / longest
            return Bitmap.createBitmap(
                bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply { postScale(factor, factor) }, true,
            )
        }

        fun compress(bitmap: Bitmap, maxBytes: Int): ByteArray {
            var quality = 88
            while (true) {
                val stream = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
                if (stream.size() <= maxBytes || quality <= 40) return stream.toByteArray()
                quality -= 12
            }
        }
    }
}
