package com.fc.collector

import android.graphics.Bitmap
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import org.json.JSONObject

/**
 * Regions are fractions of the full screen (x0,y0,x1,y1), 0..1.
 * Defaults target Fortnite Mobile landscape HUD at 1080x2400-class panels.
 * CALIBRATE per device: screenshot a match, measure the HUD element boxes,
 * convert pixel box to fractions, paste here. Wrong boxes -> empty/garbage reads.
 */
data class Roi(val name: String, val x0: Float, val y0: Float, val x1: Float, val y1: Float)

data class Hud(
    var health: Int? = null,
    var shield: Int? = null,
    var wood: Int? = null,
    var stone: Int? = null,
    var metal: Int? = null,
    var players: Int? = null,
    var ts: Long = System.currentTimeMillis()
) {
    fun toJson(): String = JSONObject().apply {
        put("ts", ts)
        put("health", health ?: JSONObject.NULL)
        put("shield", shield ?: JSONObject.NULL)
        put("wood", wood ?: JSONObject.NULL)
        put("stone", stone ?: JSONObject.NULL)
        put("metal", metal ?: JSONObject.NULL)
        put("players", players ?: JSONObject.NULL)
    }.toString()
}

class HudOcr {

    private val recognizer =
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    private val rois = listOf(
        Roi("health",  0.040f, 0.885f, 0.120f, 0.935f),
        Roi("shield",  0.040f, 0.840f, 0.120f, 0.888f),
        Roi("wood",    0.820f, 0.050f, 0.900f, 0.110f),
        Roi("stone",   0.900f, 0.050f, 0.980f, 0.110f),
        Roi("metal",   0.740f, 0.050f, 0.820f, 0.110f),
        Roi("players", 0.880f, 0.020f, 0.985f, 0.055f)
    )

    fun extract(frame: Bitmap): Hud {
        val hud = Hud()
        for (roi in rois) {
            val n = readNumber(frame, roi) ?: continue
            when (roi.name) {
                "health"  -> hud.health = n.coerceIn(0, 100)
                "shield"  -> hud.shield = n.coerceIn(0, 100)
                "wood"    -> hud.wood = n
                "stone"   -> hud.stone = n
                "metal"   -> hud.metal = n
                "players" -> hud.players = n
            }
        }
        return hud
    }

    private fun readNumber(frame: Bitmap, roi: Roi): Int? {
        val x = (roi.x0 * frame.width).toInt().coerceIn(0, frame.width - 1)
        val y = (roi.y0 * frame.height).toInt().coerceIn(0, frame.height - 1)
        val w = ((roi.x1 - roi.x0) * frame.width).toInt().coerceAtLeast(1)
        val h = ((roi.y1 - roi.y0) * frame.height).toInt().coerceAtLeast(1)
        if (x + w > frame.width || y + h > frame.height) return null

        val crop = Bitmap.createScaledBitmap(
            Bitmap.createBitmap(frame, x, y, w, h), w * 3, h * 3, true
        )
        try {
            val img = InputImage.fromBitmap(crop, 0)
            val text = Tasks.await(recognizer.process(img)).text
            val digits = DIGITS.find(text)?.value ?: return null
            return digits.toIntOrNull()
        } finally {
            crop.recycle()
        }
    }

    fun close() = recognizer.close()

    companion object {
        private val DIGITS = Regex("\\d{1,4}")
    }
}
