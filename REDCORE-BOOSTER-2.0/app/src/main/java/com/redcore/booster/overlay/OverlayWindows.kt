package com.redcore.booster.overlay

import android.util.Log
import android.view.View
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Minimal window operations, so the registry can be tested without a real WindowManager. */
interface WindowHost {
    fun add(v: View, lp: WindowManager.LayoutParams)
    fun update(v: View, lp: WindowManager.LayoutParams)
    fun remove(v: View)
}

class WindowManagerHost(private val wm: WindowManager) : WindowHost {
    override fun add(v: View, lp: WindowManager.LayoutParams) = wm.addView(v, lp)
    override fun update(v: View, lp: WindowManager.LayoutParams) = wm.updateViewLayout(v, lp)
    override fun remove(v: View) = wm.removeViewImmediate(v)
}

/**
 * Tracks which overlay windows Android actually accepted. A window is only recorded after `addView`
 * succeeded, so nothing in REDCORE can believe a window exists when Android rejected it (missing or revoked
 * "Display over other apps", bad token, Android 12+ restrictions). Main thread only.
 */
class OverlayWindows(private val host: WindowHost, private val onFailure: (tag: String, error: Throwable) -> Unit = { _, _ -> }) {
    private val views = LinkedHashMap<String, Pair<View, WindowManager.LayoutParams>>()
    private val _active = MutableStateFlow<Set<String>>(emptySet())
    /** Tags of windows that are attached right now. */
    val active: StateFlow<Set<String>> = _active.asStateFlow()

    /** Adds [view] under [tag], replacing an older window with the same tag. Returns false if Android refused. */
    fun attach(tag: String, view: View, lp: WindowManager.LayoutParams): Boolean {
        views[tag]?.let { (old, _) -> if (old === view) return update(tag, lp) else detach(tag) }
        return try {
            host.add(view, lp)
            views[tag] = view to lp
            publish()
            true
        } catch (t: Throwable) {
            Log.w(TAG, "Overlay window '$tag' was rejected", t)
            (view as? ComposeView)?.disposeComposition()
            onFailure(tag, t)
            false
        }
    }

    fun update(tag: String, lp: WindowManager.LayoutParams? = null): Boolean {
        val (v, oldLp) = views[tag] ?: return false
        val p = lp ?: oldLp
        return try {
            host.update(v, p); views[tag] = v to p; true
        } catch (t: Throwable) {
            Log.w(TAG, "Overlay window '$tag' could not be updated; removing it", t)
            views.remove(tag); publish()
            (v as? ComposeView)?.disposeComposition()
            onFailure(tag, t)
            false
        }
    }

    /** Removes the window. Returns true if it was attached. */
    fun detach(tag: String): Boolean {
        val (v, _) = views.remove(tag) ?: return false
        publish()
        runCatching { host.remove(v) }.onFailure { Log.w(TAG, "Overlay window '$tag' was already gone", it) }
        (v as? ComposeView)?.disposeComposition()
        return true
    }

    fun detachAll() = views.keys.toList().forEach { detach(it) }

    fun isAttached(tag: String) = views.containsKey(tag)
    fun view(tag: String): View? = views[tag]?.first
    fun params(tag: String): WindowManager.LayoutParams? = views[tag]?.second

    private fun publish() { _active.value = views.keys.toSet() }

    companion object { private const val TAG = "OverlayWindows" }
}
