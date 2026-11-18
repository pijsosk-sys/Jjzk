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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore("redcore")

class SettingsRepository(private val context: Context) {
    private object K {
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
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
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
        )
    }

    val library: Flow<List<GameProfile>> = context.dataStore.data.map { GameProfile.listFromJson(it[K.library]) }
    val removed: Flow<Set<String>> = context.dataStore.data.map { it[K.removed] ?: emptySet() }

    suspend fun current() = settings.first()

    suspend fun setMode(m: GameMode) = context.dataStore.edit { it[K.mode] = m.name }
    suspend fun setAutoDetect(v: Boolean) = context.dataStore.edit { it[K.auto] = v }
    suspend fun setHud(v: Boolean) = context.dataStore.edit { it[K.hud] = v }
    suspend fun setRestore(v: Boolean) = context.dataStore.edit { it[K.restore] = v }
    suspend fun setFahrenheit(v: Boolean) = context.dataStore.edit { it[K.fahr] = v }
    suspend fun setOverlayScale(v: Float) = context.dataStore.edit { it[K.scale] = v }
    suspend fun setOnboarded() = context.dataStore.edit { it[K.onboarded] = true }
    suspend fun setBubble(x: Int, y: Int) = context.dataStore.edit { it[K.bx] = x; it[K.by] = y }

    suspend fun updateLibrary(transform: (List<GameProfile>) -> List<GameProfile>) = context.dataStore.edit {
        it[K.library] = GameProfile.listToJson(transform(GameProfile.listFromJson(it[K.library])))
    }

    suspend fun markRemoved(pkg: String, removed: Boolean) = context.dataStore.edit {
        val s = (it[K.removed] ?: emptySet()).toMutableSet()
        if (removed) s += pkg else s -= pkg
        it[K.removed] = s
    }

    suspend fun setScanned() = context.dataStore.edit { it[K.scanned] = true }

    suspend fun saveSessionRestore(json: String?) = context.dataStore.edit { if (json == null) it.remove(K.session) else it[K.session] = json }
    suspend fun sessionRestore(): String? = context.dataStore.data.first()[K.session]
}
