package com.redcore.booster

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.redcore.booster.booster.SettingsBackend
import com.redcore.booster.core.RestoreJournal
import com.redcore.booster.core.Tweak
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File
import java.util.UUID

/** A fresh, isolated DataStore per test (the app's singleton delegate can't be shared across Robolectric tests). */
fun testDataStore(context: Context): DataStore<Preferences> = PreferenceDataStoreFactory.create(
    scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
    produceFile = { File(context.cacheDir, "test-${UUID.randomUUID()}.preferences_pb") },
)

/**
 * In-memory stand-in for Android's settings. [grants] controls which settings may be written, so tests can
 * simulate missing or revoked permissions. [coerce] lets a test model a system that clamps written values.
 */
class FakeSettingsBackend(initial: Map<Tweak, String?> = emptyMap()) : SettingsBackend {
    val values = HashMap<Tweak, String?>(initial)
    val grants = HashSet(Tweak.entries)
    val writes = mutableListOf<Pair<Tweak, String?>>()
    var coerce: (Tweak, String?) -> String? = { _, v -> v }

    override fun read(t: Tweak): String? = values[t]
    override fun write(t: Tweak, value: String?) {
        check(t in grants) { "Permission denied" }
        writes += t to value
        values[t] = coerce(t, value)
    }
    override fun canWrite(t: Tweak) = t in grants
    override fun requirement(t: Tweak) = "test permission for ${t.name}"

    fun revoke(vararg t: Tweak) { grants.removeAll(t.toSet()) }
    fun grant(vararg t: Tweak) { grants.addAll(t) }
    fun same(t: Tweak, v: String?) = RestoreJournal.same(t, values[t], v)
}
