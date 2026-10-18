package com.noroot.esp

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.util.Log
import android.view.accessibility.AccessibilityEvent

/**
 * AccessibilityService that dispatches short pan gestures on the right half
 * of the screen to steer the in-game camera toward a target screen point.
 *
 * Tunables: aimEnabled flips the whole thing, aimStrength scales each nudge
 * (bigger = snappier, more detectable), autoFire taps a fire button at
 * (fireX, fireY) when the target is within deadband pixels of crosshair.
 */
class AimService : AccessibilityService() {

    companion object {
        @Volatile var instance: AimService? = null
        @Volatile var aimEnabled: Boolean = false
        @Volatile var autoFire: Boolean = false
        @Volatile var aimStrength: Float = 0.35f   // 0..1
        @Volatile var deadbandPx: Int = 60
        @Volatile var fireX: Float = 0f
        @Volatile var fireY: Float = 0f
        @Volatile var panPivotX: Float = 0f   // where to anchor pan drags
        @Volatile var panPivotY: Float = 0f
        @Volatile var gestureCount: Long = 0

        /** Called from the detector worker when a target is picked. */
        fun aimAt(targetX: Float, targetY: Float, screenCx: Float, screenCy: Float) {
            val s = instance ?: return
            if (!aimEnabled) return
            s.doAim(targetX, targetY, screenCx, screenCy)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.i("AimService", "connected")
    }

    override fun onInterrupt() {}

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    @Volatile private var busy = false

    private fun doAim(tx: Float, ty: Float, cx: Float, cy: Float) {
        if (busy) return
        val dx = tx - cx
        val dy = ty - cy
        val dist = kotlin.math.hypot(dx, dy)
        if (dist < deadbandPx) {
            if (autoFire && fireX > 0 && fireY > 0) tap(fireX, fireY)
            return
        }
        busy = true
        val startX = if (panPivotX > 0) panPivotX else cx * 1.5f
        val startY = if (panPivotY > 0) panPivotY else cy
        val endX = startX + dx * aimStrength
        val endY = startY + dy * aimStrength
        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, 60)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(g: GestureDescription?) {
                gestureCount++; busy = false
                if (autoFire && fireX > 0 && fireY > 0 && dist < deadbandPx * 2) tap(fireX, fireY)
            }
            override fun onCancelled(g: GestureDescription?) { busy = false }
        }, null)
    }

    private fun tap(x: Float, y: Float) {
        val p = Path().apply { moveTo(x, y) }
        val s = GestureDescription.StrokeDescription(p, 0, 30)
        dispatchGesture(GestureDescription.Builder().addStroke(s).build(), null, null)
    }
}
