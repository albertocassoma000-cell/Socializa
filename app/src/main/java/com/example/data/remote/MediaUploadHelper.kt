package com.example.data.remote

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import okio.source
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import kotlin.math.max
import kotlin.math.roundToInt

data class UploadedMediaResult(
    val storagePath: String,
    val publicUrl: String,
    val mimeType: String,
    val width: Int? = null,
    val height: Int? = null
)

object MediaUploadHelper {
    private const val MAX_IMAGE_DIMENSION = 1440
    private const val JPEG_QUALITY = 82

    suspend fun uploadImageUri(
        context: Context,
        api: SupabaseApiService,
        firebaseToken: String,
        uid: String,
        folder: String,
        uri: Uri
    ): Result<UploadedMediaResult> = withContext(Dispatchers.IO) {
        runCatching {
            val (compressedBytes, w, h) = compressImage(context, uri)
            val objectPath = "$uid/$folder/${UUID.randomUUID()}.jpg"
            val body = compressedBytes.toRequestBody("image/jpeg".toMediaTypeOrNull())
            val res = api.uploadMedia(
                apiKey = SupabaseConfig.supabaseAnonKey,
                firebaseBearer = "Bearer $firebaseToken",
                contentType = "image/jpeg",
                objectPath = objectPath,
                fileBody = body
            )
            if (!res.isSuccessful) {
                throw IllegalStateException("Falha no envio da imagem (HTTP ${res.code()}).")
            }
            UploadedMediaResult(
                storagePath = objectPath,
                publicUrl = buildPublicMediaUrl(objectPath),
                mimeType = "image/jpeg",
                width = w,
                height = h
            )
        }
    }

    suspend fun uploadBinaryUri(
        context: Context,
        api: SupabaseApiService,
        firebaseToken: String,
        uid: String,
        folder: String,
        uri: Uri,
        fallbackMime: String,
        fallbackExt: String
    ): Result<UploadedMediaResult> = withContext(Dispatchers.IO) {
        runCatching {
            val mime = context.contentResolver.getType(uri) ?: fallbackMime
            val ext = extensionForMime(mime, fallbackExt)
            val objectPath = "$uid/$folder/${UUID.randomUUID()}.$ext"
            val body = streamingUriRequestBody(context, uri, mime)
            val res = api.uploadMedia(
                apiKey = SupabaseConfig.supabaseAnonKey,
                firebaseBearer = "Bearer $firebaseToken",
                contentType = mime,
                objectPath = objectPath,
                fileBody = body
            )
            if (!res.isSuccessful) {
                throw IllegalStateException("Falha no envio do ficheiro (HTTP ${res.code()}).")
            }
            UploadedMediaResult(
                storagePath = objectPath,
                publicUrl = buildPublicMediaUrl(objectPath),
                mimeType = mime
            )
        }
    }

    suspend fun uploadLocalFile(
        api: SupabaseApiService,
        firebaseToken: String,
        uid: String,
        folder: String,
        file: File,
        mimeType: String
    ): Result<UploadedMediaResult> = withContext(Dispatchers.IO) {
        runCatching {
            val bytes = file.readBytes()
            val ext = file.extension.ifBlank { "m4a" }
            val objectPath = "$uid/$folder/${UUID.randomUUID()}.$ext"
            val res = api.uploadMedia(
                apiKey = SupabaseConfig.supabaseAnonKey,
                firebaseBearer = "Bearer $firebaseToken",
                contentType = mimeType,
                objectPath = objectPath,
                fileBody = bytes.toRequestBody(mimeType.toMediaTypeOrNull())
            )
            if (!res.isSuccessful) {
                throw IllegalStateException("Falha no envio do áudio (HTTP ${res.code()}).")
            }
            UploadedMediaResult(
                storagePath = objectPath,
                publicUrl = buildPublicMediaUrl(objectPath),
                mimeType = mimeType
            )
        }
    }

    fun buildPublicMediaUrl(objectPath: String): String {
        val base = SupabaseConfig.supabaseUrl
        return "$base/storage/v1/object/public/media/$objectPath"
    }

    fun resolveDisplayName(context: Context, uri: Uri): String {
        return runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull() ?: uri.lastPathSegment ?: "ficheiro"
    }

    private fun compressImage(context: Context, uri: Uri): Triple<ByteArray, Int, Int> {
        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, boundsOpts)
        } ?: throw IllegalStateException("Não foi possível ler a imagem selecionada.")

        var sampleSize = 1
        val maxSide = max(boundsOpts.outWidth, boundsOpts.outHeight)
        while (maxSide / sampleSize > MAX_IMAGE_DIMENSION * 2) {
            sampleSize *= 2
        }

        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val decoded = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, decodeOpts)
        } ?: throw IllegalStateException("Imagem inválida ou corrompida.")

        val scaled = scaleDownIfNeeded(decoded)
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
        val w = scaled.width
        val h = scaled.height
        if (scaled !== decoded) decoded.recycle()
        scaled.recycle()
        return Triple(out.toByteArray(), w, h)
    }

    private fun scaleDownIfNeeded(bitmap: Bitmap): Bitmap {
        val maxDim = max(bitmap.width, bitmap.height)
        if (maxDim <= MAX_IMAGE_DIMENSION) return bitmap
        val ratio = MAX_IMAGE_DIMENSION.toFloat() / maxDim.toFloat()
        val targetW = (bitmap.width * ratio).roundToInt().coerceAtLeast(1)
        val targetH = (bitmap.height * ratio).roundToInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
    }

    private fun streamingUriRequestBody(context: Context, uri: Uri, mimeType: String): RequestBody {
        return object : RequestBody() {
            override fun contentType() = mimeType.toMediaTypeOrNull()

            override fun writeTo(sink: BufferedSink) {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    sink.writeAll(stream.source())
                } ?: throw IllegalStateException("Não foi possível abrir o ficheiro selecionado.")
            }
        }
    }

    private fun extensionForMime(mime: String, fallback: String): String {
        return when {
            mime.contains("mp4") -> "mp4"
            mime.contains("quicktime") -> "mov"
            mime.contains("png") -> "png"
            mime.contains("webp") -> "webp"
            mime.contains("jpeg") || mime.contains("jpg") -> "jpg"
            mime.contains("mpeg") -> "mp3"
            mime.contains("aac") -> "aac"
            mime.contains("pdf") -> "pdf"
            else -> fallback
        }
    }
}
