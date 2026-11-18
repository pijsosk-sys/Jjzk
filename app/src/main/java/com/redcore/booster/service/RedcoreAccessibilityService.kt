package com.redcore.booster.service

import android.accessibilityservice.AccessibilityService
import android.os.Build
import android.view.accessibility.AccessibilityEvent

/**
 * Exists only so the floating toolkit can trigger the system screenshot action.
 * It does not inspect window content (canRetrieveWindowContent=false) and ignores all events.
 */
class RedcoreAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() { instance = this }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit
    override fun onDestroy() { instance = null; super.onDestroy() }

    companion object {
        @Volatile var instance: RedcoreAccessibilityService? = null

        fun takeScreenshot(): Boolean {
            val s = instance ?: return false
            if (Build.VERSION.SDK_INT < 28) return false
            return s.performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)
        }
    }
}
