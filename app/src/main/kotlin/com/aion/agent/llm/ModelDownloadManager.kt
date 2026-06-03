package com.aion.agent.llm

import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import com.aion.agent.R
import com.aion.agent.data.SettingsRepository
import com.aion.agent.system.NotificationChannels
import com.aion.agent.util.AionLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import javax.inject.Inject
import javax.inject.Singleton

/** Progress of a GGUF model download. */
data class DownloadProgress(
    val modelName: String,
    val bytesDownloaded: Long,
    val totalBytes: Long,
    val speedBytesPerSec: Long = 0,
) {
    val fraction: Float get() =
        if (totalBytes > 0) bytesDownloaded.toFloat() / totalBytes else 0f
    val isComplete: Boolean get() = bytesDownloaded >= totalBytes && totalBytes > 0
}

/** Metadata about an available GGUF model for download. */
data class ModelInfo(
    val modelId: String,
    val displayName: String,
    val ggufFiles: List<GgufFileInfo>,
    val description: String = "",
)

/** One downloadable GGUF file variant. */
data class GgufFileInfo(
    val filename: String,
    val sizeBytes: Long,
    val quantization: String,
    val ramEstimateBytes: Long,
)

/**
 * Downloads GGUF models from HuggingFace Hub.
 *
 * Features:
 *  - Curated model list (Qwen 2.5 3B, Gemma 3 2B)
 *  - Download with progress notification
 *  - Resume interrupted downloads via HTTP Range header
 *  - Storage check before download starts
 *
 * Models are stored in [context.filesDir]/models/.
 */
@Singleton
class ModelDownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val httpClient: OkHttpClient,
    private val settings: SettingsRepository,
    private val logger: AionLogger,
) {
    private val modelsDir: File get() =
        File(context.filesDir, MODELS_DIR).also { it.mkdirs() }

    private val notificationManager: NotificationManager
        get() = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    /** Return the curated list of downloadable models. */
    suspend fun availableModels(): List<ModelInfo> = withContext(Dispatchers.Default) {
        listOf(
            ModelInfo(
                modelId = "Qwen/Qwen2.5-3B-Instruct-GGUF",
                displayName = "Qwen 2.5 3B Instruct",
                description = "Best all-round 3B model. Intent classifier + simple tasks. " +
                    "Requires ~1.8GB RAM. Recommended for 8GB+ devices.",
                ggufFiles = listOf(
                    GgufFileInfo(
                        filename = "qwen2.5-3b-instruct-q4_k_m.gguf",
                        sizeBytes = 1_800_000_000L,
                        quantization = "Q4_K_M",
                        ramEstimateBytes = 1_800_000_000L,
                    ),
                    GgufFileInfo(
                        filename = "qwen2.5-3b-instruct-q8_0.gguf",
                        sizeBytes = 3_200_000_000L,
                        quantization = "Q8_0",
                        ramEstimateBytes = 3_200_000_000L,
                    ),
                ),
            ),
            ModelInfo(
                modelId = "google/gemma-3-2b-it-GGUF",
                displayName = "Gemma 3 2B",
                description = "Lighter 2B model. Good for 6GB devices. ~1.1GB RAM.",
                ggufFiles = listOf(
                    GgufFileInfo(
                        filename = "gemma-3-2b-it-q4_k_m.gguf",
                        sizeBytes = 1_100_000_000L,
                        quantization = "Q4_K_M",
                        ramEstimateBytes = 1_100_000_000L,
                    ),
                ),
            ),
        )
    }

    /** Get detailed info for a specific model by HuggingFace repo ID. */
    suspend fun getModelInfo(modelId: String): Result<ModelInfo> =
        runCatching {
            availableModels().firstOrNull { it.modelId == modelId }
                ?: throw IllegalArgumentException("Unknown model: $modelId")
        }

    /**
     * Download a GGUF file from HuggingFace. Returns a [Flow] of [DownloadProgress].
     *
     * @param modelId HuggingFace repo ID (e.g. "Qwen/Qwen2.5-3B-Instruct-GGUF").
     * @param filename The GGUF filename to download.
     * @param notificationId ID for progress notification (0 = no notification).
     */
    fun download(
        modelId: String,
        filename: String,
        notificationId: Int = DOWNLOAD_NOTIFICATION_ID,
    ): Flow<DownloadProgress> = flow {
        val targetFile = File(modelsDir, filename)
        val hfUrl = "https://huggingface.co/$modelId/resolve/main/$filename"

        // Check if partially downloaded (for resume)
        val existingBytes = if (targetFile.exists()) targetFile.length() else 0L

        val request = Request.Builder()
            .url(hfUrl)
            .apply {
                if (existingBytes > 0) {
                    header("Range", "bytes=$existingBytes-")
                }
            }
            .build()

        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            throw Exception("Download failed: HTTP ${response.code} for $filename")
        }

        val totalBytes = response.body?.contentLength()?.let { it + existingBytes }
            ?: existingBytes
        val outputStream = if (existingBytes > 0) {
            RandomAccessFile(targetFile, "rw").apply { seek(existingBytes) }
        } else {
            FileOutputStream(targetFile)
        }

        var downloaded = existingBytes
        var lastUpdateTime = System.nanoTime()
        var lastDownloaded = downloaded
        val buffer = ByteArray(8 * 1024)
        val bodyStream = response.body?.byteStream()
            ?: throw Exception("No response body from HuggingFace")

        bodyStream.use { input ->
            outputStream.use { out ->
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    when (out) {
                        is RandomAccessFile -> out.write(buffer, 0, bytesRead)
                        is FileOutputStream -> out.write(buffer, 0, bytesRead)
                    }
                    downloaded += bytesRead

                    val now = System.nanoTime()
                    val elapsed = (now - lastUpdateTime) / 1_000_000_000
                    if (elapsed >= 1) {
                        val speed = (downloaded - lastDownloaded) / (elapsed.coerceAtLeast(1))
                        emit(DownloadProgress(
                            modelName = filename,
                            bytesDownloaded = downloaded,
                            totalBytes = totalBytes,
                            speedBytesPerSec = speed,
                        ))
                        lastUpdateTime = now
                        lastDownloaded = downloaded
                    }
                }
            }
        }

        // Final progress
        emit(DownloadProgress(
            modelName = filename,
            bytesDownloaded = downloaded,
            totalBytes = downloaded,
            speedBytesPerSec = 0,
        ))

        // Persist the model path for future loading
        settings.setLastLoadedModelPath(targetFile.absolutePath)
    }

    /** Check if a model file already exists locally. */
    fun isDownloaded(filename: String): Boolean =
        File(modelsDir, filename).exists()

    /** Get absolute path to a downloaded model file, or null. */
    fun getModelPath(filename: String): String? =
        File(modelsDir, filename).takeIf { it.exists() }?.absolutePath

    /** Delete a downloaded model file. Returns true on success. */
    fun deleteModel(filename: String): Boolean =
        File(modelsDir, filename).delete()

    /** Total storage used by all downloaded models in bytes. */
    fun totalStorageBytes(): Long =
        modelsDir.listFiles()?.sumOf { it.length() } ?: 0L

    /**
     * Show or update a progress notification for an active download.
     */
    fun showProgressNotification(notificationId: Int, progress: DownloadProgress) {
        val notification = NotificationCompat.Builder(context, NotificationChannels.DOWNLOADS)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Downloading ${progress.modelName}")
            .setContentText(
                "${progress.bytesDownloaded / 1_000_000}MB / ${progress.totalBytes / 1_000_000}MB"
            )
            .setProgress(
                100,
                (progress.fraction * 100).toInt(),
                false,
            )
            .setOngoing(true)
            .build()
        notificationManager.notify(notificationId, notification)
    }

    /** Remove a progress notification. */
    fun hideProgressNotification(notificationId: Int) {
        notificationManager.cancel(notificationId)
    }

    companion object {
        private const val MODELS_DIR = "models"
        private const val DOWNLOAD_NOTIFICATION_ID = 4243
    }
}
