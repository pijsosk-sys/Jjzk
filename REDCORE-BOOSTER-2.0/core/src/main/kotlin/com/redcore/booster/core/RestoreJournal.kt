package com.redcore.booster.core

import kotlin.math.abs

/** Every system setting REDCORE may change during a game session. */
enum class Tweak(val label: String, val numeric: Boolean) {
    DND_FILTER("Do Not Disturb", true),
    DND_POLICY("Do Not Disturb rules", false),
    BRIGHTNESS_MODE("Auto-brightness", true),
    BRIGHTNESS("Brightness", true),
    AUTO_ROTATE("Auto-rotate", true),
    USER_ROTATION("Screen orientation", true),
    PEAK_REFRESH("Peak refresh rate", true),
    MIN_REFRESH("Minimum refresh rate", true),
    SAMSUNG_REFRESH_MODE("Motion smoothness", true),
    MASTER_SYNC("Auto-sync", false),
    ANIM_ANIMATOR("Animator duration scale", true),
    ANIM_TRANSITION("Transition animation scale", true),
    ANIM_WINDOW("Window animation scale", true);
}

/**
 * One changed setting. [original] is the value before REDCORE touched it (null = the setting did not
 * exist), [applied] the last value REDCORE itself wrote.
 */
data class JournalEntry(val tweak: Tweak, val original: String?, val applied: String?, val failures: Int = 0)

enum class RestoreAction {
    /** Current value is what REDCORE wrote: put the original back. */
    RESTORE,
    /** Someone else changed it after REDCORE did (the user, during the game): leave their choice. */
    KEEP_USER_CHANGE,
    /** It already holds the original value: nothing to do. */
    ALREADY_ORIGINAL,
}

data class RestoreStep(val tweak: Tweak, val action: RestoreAction, val target: String?, val current: String?)

/** Context of the session that owns the journal, used to explain a crash/reboot recovery. */
data class JournalMeta(val pkg: String?, val label: String, val startedAt: Long)

/**
 * Write-ahead journal of system changes. Immutable: every operation returns a new journal, which the
 * Android side persists *before* it writes the system setting, so a crash or reboot at any point can
 * be undone on the next start.
 */
data class RestoreJournal(val entries: Map<Tweak, JournalEntry> = emptyMap(), val meta: JournalMeta? = null) {
    val isEmpty get() = entries.isEmpty()

    /** Records the pre-REDCORE value. Only the first call per setting counts: later writes keep the true original. */
    fun noteOriginal(t: Tweak, current: String?): RestoreJournal =
        if (entries.containsKey(t)) this else copy(entries = entries + (t to JournalEntry(t, current, null)))

    fun noteApplied(t: Tweak, value: String?): RestoreJournal {
        val e = entries[t] ?: return this
        return copy(entries = entries + (t to e.copy(applied = value)))
    }

    fun withMeta(m: JournalMeta?): RestoreJournal = copy(meta = m)

    fun remove(t: Tweak): RestoreJournal = copy(entries = entries - t)

    fun noteFailure(t: Tweak): RestoreJournal {
        val e = entries[t] ?: return this
        return copy(entries = entries + (t to e.copy(failures = e.failures + 1)))
    }

    /**
     * Decides what to do with each entry, given the current system values.
     * With [keepUserChanges] (the default policy) a value the user changed during the game is left alone.
     */
    fun planRestore(current: Map<Tweak, String?>, keepUserChanges: Boolean = true): List<RestoreStep> =
        entries.values.sortedBy { it.tweak.ordinal }.map { e ->
            val cur = current[e.tweak]
            when {
                same(e.tweak, cur, e.original) -> RestoreStep(e.tweak, RestoreAction.ALREADY_ORIGINAL, e.original, cur)
                // applied == null means the write never succeeded: nothing of ours to undo, unless the value moved.
                e.applied == null && keepUserChanges -> RestoreStep(e.tweak, RestoreAction.KEEP_USER_CHANGE, e.original, cur)
                keepUserChanges && !same(e.tweak, cur, e.applied) -> RestoreStep(e.tweak, RestoreAction.KEEP_USER_CHANGE, e.original, cur)
                else -> RestoreStep(e.tweak, RestoreAction.RESTORE, e.original, cur)
            }
        }

    fun serialize(): String = buildString {
        meta?.let { append("#meta\t").append(enc(it.pkg)).append('\t').append(enc(it.label)).append('\t').append(it.startedAt).append('\n') }
        entries.values.sortedBy { it.tweak.ordinal }.forEach {
            append(it.tweak.name).append('\t').append(enc(it.original)).append('\t').append(enc(it.applied)).append('\t').append(it.failures).append('\n')
        }
    }

    companion object {
        fun parse(s: String?): RestoreJournal {
            if (s.isNullOrBlank()) return RestoreJournal()
            var meta: JournalMeta? = null
            val map = LinkedHashMap<Tweak, JournalEntry>()
            s.lineSequence().filter { it.isNotBlank() }.forEach { line ->
                val p = line.split('\t')
                if (p[0] == "#meta" && p.size >= 4) {
                    meta = JournalMeta(dec(p[1]), dec(p[2]) ?: "Game session", p[3].toLongOrNull() ?: 0L)
                } else if (p.size >= 4) {
                    val t = Tweak.entries.firstOrNull { it.name == p[0] } ?: return@forEach // unknown key from a newer version: skip
                    map[t] = JournalEntry(t, dec(p[1]), dec(p[2]), p[3].toIntOrNull() ?: 0)
                }
            }
            return RestoreJournal(map, meta)
        }

        /** Numeric settings compare by value ("120" == "120.0"); others by text. */
        fun same(t: Tweak, a: String?, b: String?): Boolean {
            if (a == null || b == null) return a == b
            if (t.numeric) {
                val x = a.trim().toDoubleOrNull(); val y = b.trim().toDoubleOrNull()
                if (x != null && y != null) return abs(x - y) < 0.01
            }
            return a.trim() == b.trim()
        }

        private fun enc(v: String?): String = if (v == null) "~" else "=" + v.replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n")
        private fun dec(v: String): String? {
            if (v == "~" || !v.startsWith("=")) return null
            val sb = StringBuilder(); var i = 1
            while (i < v.length) {
                val c = v[i]
                if (c == '\\' && i + 1 < v.length) {
                    when (v[i + 1]) { 't' -> sb.append('\t'); 'n' -> sb.append('\n'); else -> sb.append(v[i + 1]) }
                    i += 2
                } else { sb.append(c); i++ }
            }
            return sb.toString()
        }
    }
}
