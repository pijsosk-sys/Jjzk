package com.redcore.booster.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

val Context.dataStore: DataStore<Preferences> by preferencesDataStore("redcore")

class SettingsRepository(context: Context, private val store: DataStore<Preferences> = context.dataStore) {
    /** A damaged settings file must not crash the app: fall back to defaults and keep going. */
    private val data: Flow<Preferences> = store.data.catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }

    object K {
        val mode = stringPreferencesKey("mode")
        val auto = booleanPreferencesKey("auto_detect")
        val hud = booleanPreferencesKey("hud")
        val restore = booleanPreferencesKey("restore")
        val fahr = booleanPreferencesKey("fahrenheit")
        val scanned = booleanPreferencesKey("scanned")
        val bx = intPreferencesKey("bubble_x")
        val by = intPreferencesKey("bubble_y")
        val scale = floatPreferencesKey("overlay_scale")
        val onboarded = booleanPreferencesKey("onboarded")
        val library = stringPreferencesKey("library")
        val removed = stringSetPreferencesKey("removed")
        val session = stringPreferencesKey("session_restore")
        val edge = booleanPreferencesKey("edge_guard")
        val edgeRange = intPreferencesKey("edge_range")
        val trig = booleanPreferencesKey("triggers")
        val l1x = floatPreferencesKey("l1x"); val l1y = floatPreferencesKey("l1y")
        val r1x = floatPreferencesKey("r1x"); val r1y = floatPreferencesKey("r1y")
        val trigHold = booleanPreferencesKey("trig_hold")
        val trigVib = booleanPreferencesKey("trig_vib")
        val cool = booleanPreferencesKey("cool_guard")
        val coolTarget = intPreferencesKey("cool_target")
        val swipeTop = booleanPreferencesKey("swipe_top")
        val swipeSide = booleanPreferencesKey("swipe_side")
        val bubble = booleanPreferencesKey("show_bubble")
        val hint = booleanPreferencesKey("handle_hint")
        val notif = stringPreferencesKey("notif_mode")
        val calls = stringPreferencesKey("call_mode")
        val haptics = booleanPreferencesKey("haptics")
        val hapticStrength = floatPreferencesKey("haptic_strength")
        val eq = stringPreferencesKey("eq")
        val cross = booleanPreferencesKey("crosshair")
        val crossStyle = stringPreferencesKey("cross_style")
        val crossColor = intPreferencesKey("cross_color")
        val crossSize = floatPreferencesKey("cross_size")
        val breakMin = intPreferencesKey("break_min")
        val recQ = intPreferencesKey("rec_quality")
        val fps = booleanPreferencesKey("show_fps")
        val ping = booleanPreferencesKey("show_ping")
        val sessions = stringPreferencesKey("session_log")
        val smooth = booleanPreferencesKey("smooth120")
    }

    val settings: Flow<AppSettings> = data.map { p ->
        AppSettings(
            globalMode = p[K.mode]?.let { runCatching { GameMode.valueOf(it) }.getOrNull() } ?: GameMode.BALANCED,
            autoDetect = p[K.auto] ?: false,
            hudEnabled = p[K.hud] ?: true,
            restoreOnExit = p[K.restore] ?: true,
            fahrenheit = p[K.fahr] ?: false,
            libraryScanned = p[K.scanned] ?: false,
            bubbleX = p[K.bx] ?: 0,
            bubbleY = p[K.by] ?: 260,
            overlayScale = p[K.scale] ?: 1f,
            onboarded = p[K.onboarded] ?: false,
            edgeGuard = p[K.edge] ?: false,
            edgeRange = p[K.edgeRange] ?: 1,
            triggers = p[K.trig] ?: false,
            l1x = p[K.l1x] ?: 0.18f, l1y = p[K.l1y] ?: 0.55f,
            r1x = p[K.r1x] ?: 0.82f, r1y = p[K.r1y] ?: 0.55f,
            triggerHold = p[K.trigHold] ?: false,
            triggerVibrate = p[K.trigVib] ?: true,
            coolGuard = p[K.cool] ?: false,
            coolTarget = p[K.coolTarget] ?: 41,
            swipeTop = p[K.swipeTop] ?: true,
            swipeSide = p[K.swipeSide] ?: true,
            showBubble = p[K.bubble] ?: false,
            handleHint = p[K.hint] ?: true,
            notifMode = p[K.notif]?.let { runCatching { NotifMode.valueOf(it) }.getOrNull() } ?: NotifMode.BARRAGE,
            callMode = p[K.calls]?.let { runCatching { CallMode.valueOf(it) }.getOrNull() } ?: CallMode.ALLOW,
            haptics = p[K.haptics] ?: false,
            hapticStrength = p[K.hapticStrength] ?: 0.7f,
            eq = p[K.eq]?.let { runCatching { EqPreset.valueOf(it) }.getOrNull() } ?: EqPreset.OFF,
            crosshair = p[K.cross] ?: false,
            crossStyle = p[K.crossStyle]?.let { runCatching { CrossStyle.valueOf(it) }.getOrNull() } ?: CrossStyle.CROSS,
            crossColor = p[K.crossColor] ?: 0,
            crossSize = p[K.crossSize] ?: 0.5f,
            breakMinutes = p[K.breakMin] ?: 60,
            recQuality = p[K.recQ] ?: 1,
            showFps = p[K.fps] ?: true,
            showPing = p[K.ping] ?: true,
            smooth120 = p[K.smooth] ?: false,
        )
    }

    val library: Flow<List<GameProfile>> = data.map { GameProfile.listFromJson(it[K.library]) }
    val removed: Flow<Set<String>> = data.map { it[K.removed] ?: emptySet() }

    suspend fun current() = settings.first()

    /** Last known settings, for code that must not block (kept fresh by RedcoreApp). */
    @Volatile var latest: AppSettings = AppSettings()
        internal set

    /** Generic setter used by the many small toggles. */
    suspend fun <T> put(key: Preferences.Key<T>, value: T) = store.edit { it[key] = value }

    /** Recent game sessions (pkg, start, end) — used for the per-game "Moments" captures gallery. */
    suspend fun logSession(pkg: String, start: Long, end: Long) = store.edit {
        val list = (it[K.sessions] ?: "").split(';').filter { e -> e.isNotBlank() }.takeLast(79)
        it[K.sessions] = (list + "$pkg,$start,$end").joinToString(";")
    }

    suspend fun sessions(): List<Triple<String, Long, Long>> = (data.first()[K.sessions] ?: "")
        .split(';').mapNotNull { e -> e.split(',').takeIf { it.size == 3 }?.let { Triple(it[0], it[1].toLongOrNull() ?: 0, it[2].toLongOrNull() ?: 0) } }

    suspend fun setMode(m: GameMode) = store.edit { it[K.mode] = m.name }
    suspend fun setAutoDetect(v: Boolean) = store.edit { it[K.auto] = v }
    suspend fun setHud(v: Boolean) = store.edit { it[K.hud] = v }
    suspend fun setRestore(v: Boolean) = store.edit { it[K.restore] = v }
    suspend fun setFahrenheit(v: Boolean) = store.edit { it[K.fahr] = v }
    suspend fun setOverlayScale(v: Float) = store.edit { it[K.scale] = v }
    suspend fun setOnboarded() = store.edit { it[K.onboarded] = true }
    suspend fun setEdgeGuard(v: Boolean) = store.edit { it[K.edge] = v }
    suspend fun setEdgeRange(v: Int) = store.edit { it[K.edgeRange] = v }
    suspend fun setTriggers(v: Boolean) = store.edit { it[K.trig] = v }
    suspend fun setTriggerPoints(l1x: Float, l1y: Float, r1x: Float, r1y: Float) = store.edit {
        it[K.l1x] = l1x; it[K.l1y] = l1y; it[K.r1x] = r1x; it[K.r1y] = r1y
    }
    suspend fun setBubble(x: Int, y: Int) = store.edit { it[K.bx] = x; it[K.by] = y }

    suspend fun updateLibrary(transform: (List<GameProfile>) -> List<GameProfile>) = store.edit {
        it[K.library] = GameProfile.listToJson(transform(GameProfile.listFromJson(it[K.library])))
    }

    suspend fun markRemoved(pkg: String, removed: Boolean) = markRemoved(listOf(pkg), removed)

    /** One DataStore write for many packages. */
    suspend fun markRemoved(pkgs: Collection<String>, removed: Boolean) = store.edit {
        val s = (it[K.removed] ?: emptySet()).toMutableSet()
        if (removed) s += pkgs else s -= pkgs.toSet()
        it[K.removed] = s
    }

    suspend fun setScanned() = store.edit { it[K.scanned] = true }

    /**
     * 1.3.x kept a full snapshot here that could be overwritten with boosted values after a crash. 2.0 uses a
     * write-ahead journal instead (see BoostController), so the old snapshot is discarded on upgrade.
     */
    suspend fun clearLegacyRestorePoint() = store.edit { it.remove(K.session) }
}
