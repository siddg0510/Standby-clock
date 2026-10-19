package com.example.standbyclock

import android.graphics.Bitmap
import android.graphics.PointF
import android.media.FaceDetector

/**
 * Crops a photo to the screen's shape while keeping detected faces in frame.
 * Uses the platform face detector (no extra libraries). With no faces, it crops from the centre.
 */
object PhotoCrop {

    private const val MAX_FACES = 4

    fun cropToScreen(source: Bitmap, screenAspect: Float): Bitmap {
        val w = source.width
        val h = source.height
        var cropW = w.toFloat()
        var cropH = cropW / screenAspect
        if (cropH > h) {
            cropH = h.toFloat()
            cropW = cropH * screenAspect
        }
        val faces = detectFaces(source)
        val centerX = if (faces.isEmpty()) w / 2f else faces.map { it.x }.average().toFloat()
        // Faces sit in the upper part of most photos, so bias the crop upward slightly.
        val centerY = if (faces.isEmpty()) h / 2f else faces.map { it.y }.average().toFloat()
        val left = (centerX - cropW / 2).coerceIn(0f, w - cropW).toInt()
        val top = (centerY - cropH / 2).coerceIn(0f, h - cropH).toInt()
        return Bitmap.createBitmap(source, left, top, cropW.toInt(), cropH.toInt())
    }

    private fun detectFaces(source: Bitmap): List<PointF> {
        // The detector needs an even-sized RGB_565 image.
        val evenW = source.width - source.width % 2
        val evenH = source.height - source.height % 2
        if (evenW < 2 || evenH < 2) return emptyList()
        val even = Bitmap.createBitmap(source, 0, 0, evenW, evenH).copy(Bitmap.Config.RGB_565, false)
            ?: return emptyList()
        val detector = FaceDetector(even.width, even.height, MAX_FACES)
        val faces = arrayOfNulls<FaceDetector.Face>(MAX_FACES)
        val count = detector.findFaces(even, faces)
        return (0 until count).mapNotNull { index ->
            faces[index]?.let { face -> PointF().also { face.getMidPoint(it) } }
        }
    }
}
