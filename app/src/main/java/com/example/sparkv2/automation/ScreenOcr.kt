package com.example.sparkv2.automation

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.view.Display
import androidx.annotation.RequiresApi
import com.example.sparkv2.service.SparkAccessibilityService
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

/** A single OCR'd word and where it sits on screen, so we can tap it via a gesture. */
data class OcrWord(val text: String, val bounds: Rect)

/** Combined OCR text (for the parser) plus per-word boxes (for gesture taps). */
data class OcrResult(val text: String, val words: List<OcrWord>)

/**
 * Reads the screen with ML Kit when the accessibility tree is opaque (canvas-rendered content).
 * This is a *fallback only* — screenshots + recognition cost ~200-500ms and the system rate-limits
 * [AccessibilityService.takeScreenshot], so it must never run on the normal, text-rich path.
 *
 * Note: windows flagged FLAG_SECURE come back blank in screenshots, so OCR can't help there — only
 * non-secure canvas screens are recoverable.
 */
object ScreenOcr {

    /** takeScreenshot is API 30+; on older devices the OCR fallback is simply unavailable. */
    val isSupported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

    @Volatile private var lastRunAtMs = 0L
    @Volatile private var inFlight = false
    /** Set when the system rejects screenshot capture so we never crash the scan thread again. */
    @Volatile private var screenshotBlocked = false

    private val recognizer: TextRecognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    fun canTakeScreenshot(service: AccessibilityService): Boolean {
        if (!isSupported || screenshotBlocked) return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false
        val caps = service.serviceInfo?.capabilities ?: return false
        return caps and AccessibilityServiceInfo.CAPABILITY_CAN_TAKE_SCREENSHOT != 0
    }

    fun canRun(service: AccessibilityService, speed: SpeedConfig = SparkAutomationHub.speed(), nowMs: Long = System.currentTimeMillis()): Boolean =
        canTakeScreenshot(service) && !inFlight && nowMs - lastRunAtMs >= ScanTiming.ocrMinInterval(speed)

    /**
     * Captures the screen and OCRs it. [onResult] is invoked on the main thread with the result,
     * or null if capture/recognition failed or isn't supported. Self-throttles via [canRun].
     */
    fun capture(service: SparkAccessibilityService, onResult: (OcrResult?) -> Unit) {
        if (!canRun(service)) {
            onResult(null)
            return
        }
        inFlight = true
        lastRunAtMs = System.currentTimeMillis()
        takeScreenshotR(service, onResult)
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun takeScreenshotR(service: SparkAccessibilityService, onResult: (OcrResult?) -> Unit) {
        try {
            service.takeScreenshot(
                Display.DEFAULT_DISPLAY,
                service.mainExecutor,
                object : AccessibilityService.TakeScreenshotCallback {
                    override fun onSuccess(screenshot: AccessibilityService.ScreenshotResult) {
                        val hardware = try {
                            Bitmap.wrapHardwareBuffer(screenshot.hardwareBuffer, screenshot.colorSpace)
                        } catch (_: Throwable) {
                            null
                        } finally {
                            screenshot.hardwareBuffer.close()
                        }
                        val software = hardware?.copy(Bitmap.Config.ARGB_8888, false)
                        hardware?.recycle()
                        if (software == null) {
                            finish(onResult, null)
                            return
                        }
                        recognize(software, onResult)
                    }

                    override fun onFailure(errorCode: Int) {
                        finish(onResult, null)
                    }
                },
            )
        } catch (e: SecurityException) {
            screenshotBlocked = true
            finish(onResult, null)
        } catch (e: Throwable) {
            finish(onResult, null)
        }
    }

    private fun recognize(bitmap: Bitmap, onResult: (OcrResult?) -> Unit) {
        val image = InputImage.fromBitmap(bitmap, 0)
        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                val words = ArrayList<OcrWord>(64)
                for (block in visionText.textBlocks) {
                    for (line in block.lines) {
                        for (element in line.elements) {
                            element.boundingBox?.let { box ->
                                words.add(OcrWord(element.text, box))
                            }
                        }
                    }
                }
                bitmap.recycle()
                finish(onResult, OcrResult(text = visionText.text.replace('\n', ' '), words = words))
            }
            .addOnFailureListener {
                bitmap.recycle()
                finish(onResult, null)
            }
    }

    private fun finish(onResult: (OcrResult?) -> Unit, result: OcrResult?) {
        inFlight = false
        onResult(result)
    }
}
