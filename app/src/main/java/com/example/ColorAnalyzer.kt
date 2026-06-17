package com.example

import android.graphics.Color
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import java.nio.ByteBuffer

enum class HitZone {
    HEAD, CHEST, LIMBS, NONE
}

data class RoiColorData(
    val r: Int,
    val g: Int,
    val b: Int,
    val h: Float,
    val s: Float,
    val v: Float
)

class ColorAnalyzer(
    private val onTargetLocked: (HitZone) -> Unit,
    private val onRoiColorUpdated: (RoiColorData) -> Unit,
    private val targetHeadHsv: () -> FloatArray?,
    private val targetChestHsv: () -> FloatArray?,
    private val targetLimbsHsv: () -> FloatArray?,
    private val matchingThreshold: Float = 0.40f // 40% of ROI pixels matching is a lock-on
) : ImageAnalysis.Analyzer {

    @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
    override fun analyze(image: ImageProxy) {
        val img = image.image ?: run {
            image.close()
            return
        }

        try {
            val width = image.width
            val height = image.height

            val yPlane = img.planes[0]
            val uPlane = img.planes[1]
            val vPlane = img.planes[2]

            val yBuffer: ByteBuffer = yPlane.buffer
            val uBuffer: ByteBuffer = uPlane.buffer
            val vBuffer: ByteBuffer = vPlane.buffer

            val yRowStride = yPlane.rowStride
            val uvRowStride = uPlane.rowStride
            val uvPixelStride = uPlane.pixelStride

            // Define small fixed ROI size: 16x16 pixels (perfect for center scanning)
            val roiSize = 16
            val startX = (width - roiSize) / 2
            val startY = (height - roiSize) / 2

            var sumR = 0L
            var sumG = 0L
            var sumB = 0L
            var validPixels = 0

            var headMatchCount = 0
            var chestMatchCount = 0
            var limbsMatchCount = 0

            val headTarget = targetHeadHsv()
            val chestTarget = targetChestHsv()
            val limbsTarget = targetLimbsHsv()

            val hsv = FloatArray(3)

            for (yOffset in 0 until roiSize) {
                val py = startY + yOffset
                if (py < 0 || py >= height) continue

                for (xOffset in 0 until roiSize) {
                    val px = startX + xOffset
                    if (px < 0 || px >= width) continue

                    val yIdx = py * yRowStride + px
                    val uvx = px / 2
                    val uvy = py / 2
                    val uvIdx = uvy * uvRowStride + uvx * uvPixelStride

                    if (yIdx >= yBuffer.remaining() || uvIdx >= uBuffer.remaining() || uvIdx >= vBuffer.remaining()) {
                        continue
                    }

                    val yVal = yBuffer.get(yIdx).toInt() and 0xFF
                    val uVal = uBuffer.get(uvIdx).toInt() and 0xFF
                    val vVal = vBuffer.get(uvIdx).toInt() and 0xFF

                    // Convert YUV to RGB using standard SDTV formula
                    val r = (yVal + 1.370705 * (vVal - 128)).toInt().coerceIn(0, 255)
                    val g = (yVal - 0.337633 * (uVal - 128) - 0.698001 * (vVal - 128)).toInt().coerceIn(0, 255)
                    val b = (yVal + 1.732446 * (uVal - 128)).toInt().coerceIn(0, 255)

                    sumR += r
                    sumG += g
                    sumB += b
                    validPixels++

                    Color.RGBToHSV(r, g, b, hsv)

                    if (headTarget != null && matchesHsv(hsv, headTarget)) {
                        headMatchCount++
                    } else if (chestTarget != null && matchesHsv(hsv, chestTarget)) {
                        chestMatchCount++
                    } else if (limbsTarget != null && matchesHsv(hsv, limbsTarget)) {
                        limbsMatchCount++
                    }
                }
            }

            if (validPixels > 0) {
                val avgR = (sumR / validPixels).toInt()
                val avgG = (sumG / validPixels).toInt()
                val avgB = (sumB / validPixels).toInt()
                val avgHsv = FloatArray(3)
                Color.RGBToHSV(avgR, avgG, avgB, avgHsv)

                onRoiColorUpdated(
                    RoiColorData(
                        r = avgR,
                        g = avgG,
                        b = avgB,
                        h = avgHsv[0],
                        s = avgHsv[1],
                        v = avgHsv[2]
                    )
                )

                val headMatchPct = headMatchCount.toFloat() / validPixels
                val chestMatchPct = chestMatchCount.toFloat() / validPixels
                val limbsMatchPct = limbsMatchCount.toFloat() / validPixels

                val lockedZone = when {
                    headMatchPct >= matchingThreshold -> HitZone.HEAD
                    chestMatchPct >= matchingThreshold -> HitZone.CHEST
                    limbsMatchPct >= matchingThreshold -> HitZone.LIMBS
                    else -> HitZone.NONE
                }

                onTargetLocked(lockedZone)
            } else {
                onTargetLocked(HitZone.NONE)
            }

        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            image.close()
        }
    }

    private fun matchesHsv(hsv: FloatArray, target: FloatArray): Boolean {
        val hDiff = Math.abs(hsv[0] - target[0])
        val hueDiff = Math.min(hDiff, 360f - hDiff)
        val satDiff = Math.abs(hsv[1] - target[1])
        val valDiff = Math.abs(hsv[2] - target[2])

        // Solid tolerances to allow for lighting, shadows, and camera variations.
        // Hue: within 25 degrees, Saturation: within 0.32, Value: within 0.38
        return hueDiff <= 25f && satDiff <= 0.32f && valDiff <= 0.38f && hsv[1] >= 0.15f && hsv[2] >= 0.15f
    }
}
