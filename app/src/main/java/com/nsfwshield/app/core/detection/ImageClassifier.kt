package com.nsfwshield.app.core.detection

import android.content.Context
import android.graphics.Bitmap
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import javax.inject.Inject
import javax.inject.Singleton

/**
 * On-device NSFW image classifier using TensorFlow Lite.
 *
 * Runs inference locally — no images are ever sent to external servers.
 * Returns confidence scores (0.0–1.0) per category.
 */
@Singleton
class ImageClassifier @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val MODEL_FILE = "nsfw_mobilenet.tflite"
        private const val INPUT_SIZE = 224
        private const val PIXEL_SIZE = 3
        private const val IMAGE_MEAN = 127.5f
        private const val IMAGE_STD = 127.5f
        private const val NUM_CLASSES = 5 // safe, explicit, suggestive, violence, neutral

        // Category labels matching model output order
        private val CATEGORY_LABELS = arrayOf(
            "safe", "explicit_sexual", "suggestive", "violence_gore", "neutral"
        )
    }

    private var interpreter: Interpreter? = null
    private var isModelLoaded = false

    /**
     * Initialize the TFLite interpreter. Call once at startup.
     */
    suspend fun initialize() = withContext(Dispatchers.IO) {
        try {
            val model = loadModelFile()
            val options = Interpreter.Options().apply {
                setNumThreads(4)
            }
            interpreter = Interpreter(model, options)
            isModelLoaded = true
        } catch (e: Exception) {
            // Model file not found — running in fallback mode
            isModelLoaded = false
        }
    }

    /**
     * Run NSFW detection on a bitmap image.
     * Returns a DetectionResult with confidence score and category.
     */
    suspend fun detectNSFW(image: Bitmap): DetectionResult = withContext(Dispatchers.Default) {
        if (!isModelLoaded || interpreter == null) {
            return@withContext DetectionResult(
                score = 0f,
                category = ContentCategory.SAFE,
                isBlocked = false,
                source = DetectionSource.IMAGE_CLASSIFIER,
                metadata = mapOf("error" to "model_not_loaded")
            )
        }

        val scaledBitmap = Bitmap.createScaledBitmap(image, INPUT_SIZE, INPUT_SIZE, true)
        val inputBuffer = preprocessImage(scaledBitmap)

        val outputArray = Array(1) { FloatArray(NUM_CLASSES) }
        interpreter?.run(inputBuffer, outputArray)

        val scores = outputArray[0]
        val maxIndex = scores.indices.maxByOrNull { scores[it] } ?: 0
        val maxScore = scores[maxIndex]
        val category = mapToCategory(CATEGORY_LABELS.getOrElse(maxIndex) { "safe" })

        DetectionResult(
            score = maxScore,
            category = category,
            isBlocked = false, // Blocking decision made by DecisionEngine
            source = DetectionSource.IMAGE_CLASSIFIER,
            metadata = buildScoreMap(scores)
        )
    }

    /**
     * Bulk classify multiple images (e.g., for media scanning).
     */
    suspend fun detectBatch(images: List<Bitmap>): List<DetectionResult> {
        return images.map { detectNSFW(it) }
    }

    /**
     * Check if the model is loaded and ready.
     */
    fun isReady(): Boolean = isModelLoaded

    /**
     * Release the interpreter resources.
     */
    fun close() {
        interpreter?.close()
        interpreter = null
        isModelLoaded = false
    }

    // ─── Private helpers ───

    private fun preprocessImage(bitmap: Bitmap): ByteBuffer {
        val buffer = ByteBuffer.allocateDirect(4 * INPUT_SIZE * INPUT_SIZE * PIXEL_SIZE)
        buffer.order(ByteOrder.nativeOrder())

        val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
        bitmap.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)

        for (pixel in pixels) {
            val r = ((pixel shr 16) and 0xFF)
            val g = ((pixel shr 8) and 0xFF)
            val b = (pixel and 0xFF)

            buffer.putFloat((r - IMAGE_MEAN) / IMAGE_STD)
            buffer.putFloat((g - IMAGE_MEAN) / IMAGE_STD)
            buffer.putFloat((b - IMAGE_MEAN) / IMAGE_STD)
        }

        return buffer
    }

    private fun loadModelFile(): MappedByteBuffer {
        val assetFileDescriptor = context.assets.openFd(MODEL_FILE)
        val inputStream = FileInputStream(assetFileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = assetFileDescriptor.startOffset
        val declaredLength = assetFileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    private fun mapToCategory(label: String): ContentCategory {
        return when (label) {
            "explicit_sexual" -> ContentCategory.EXPLICIT_SEXUAL
            "suggestive" -> ContentCategory.EXPLICIT_SEXUAL
            "violence_gore" -> ContentCategory.VIOLENCE_GORE
            "safe", "neutral" -> ContentCategory.SAFE
            else -> ContentCategory.SAFE
        }
    }

    private fun buildScoreMap(scores: FloatArray): Map<String, String> {
        return CATEGORY_LABELS.indices.associate { i ->
            CATEGORY_LABELS[i] to String.format("%.4f", scores.getOrElse(i) { 0f })
        }
    }
}
