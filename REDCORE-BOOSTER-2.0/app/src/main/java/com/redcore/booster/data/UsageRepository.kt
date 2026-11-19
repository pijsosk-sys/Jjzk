package com.redcore.booster.data

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.os.Process
import com.redcore.booster.core.AppEvent
import com.redcore.booster.core.AppEventType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

data class PlayStat(val pkg: String, val foregroundMs: Long, val lastUsed: Long)

data class PlaySummary(
    val totalMs: Long,
    val perGame: List<PlayStat>,
    val dailyMs: List<Long>, // last 7 days, oldest first (for week view)
)

/** Play-time statistics and app transitions from Android's Usage Access. Requires the user to grant "Usage access". */
class UsageRepository(private val context: Context) {
    private val usm = context.getSystemService(UsageStatsManager::class.java)

    fun hasAccess(): Boolean {
        val ops = context.getSystemService(AppOpsManager::class.java) ?: return false
        val mode = if (Build.VERSION.SDK_INT >= 29) ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        else @Suppress("DEPRECATION") ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun startOfDay(daysAgo: Int): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        add(Calendar.DAY_OF_YEAR, -daysAgo)
    }.timeInMillis

    /**
     * Activity transitions between [fromMs] and [toMs]. Resumed/paused have the same codes on every API level
     * (MOVE_TO_FOREGROUND/BACKGROUND before Android 10); "stopped" exists from Android 10.
     */
    fun appEvents(fromMs: Long, toMs: Long): List<AppEvent> {
        val events = runCatching { usm?.queryEvents(fromMs, toMs) }.getOrNull() ?: return emptyList()
        val out = ArrayList<AppEvent>()
        val e = UsageEvents.Event()
        while (events.hasNextEvent()) {
            if (!events.getNextEvent(e)) break
            val type = when (e.eventType) {
                EVENT_RESUMED -> AppEventType.RESUMED
                EVENT_PAUSED -> AppEventType.PAUSED
                EVENT_STOPPED -> AppEventType.STOPPED
                else -> continue
            }
            out += AppEvent(e.timeStamp, e.packageName ?: continue, e.className, type)
        }
        return out
    }

    /** Foreground time per package from activity resume/pause events (handles several activities of one app). */
    private fun foregroundTime(start: Long, end: Long, pkgs: Set<String>): Map<String, PlayStat> {
        val events = appEvents(start, end)
        val openCount = HashMap<String, Int>()
        val openSince = HashMap<String, Long>()
        val total = HashMap<String, Long>()
        val last = HashMap<String, Long>()
        for (e in events) {
            if (e.pkg !in pkgs) continue
            when (e.type) {
                AppEventType.RESUMED -> {
                    val n = openCount[e.pkg] ?: 0
                    if (n == 0) openSince[e.pkg] = e.timeMs
                    openCount[e.pkg] = n + 1
                }
                AppEventType.PAUSED -> {
                    val n = openCount[e.pkg] ?: 0
                    if (n <= 1) {
                        // Paused without a resume in the window: it was already open when the window started.
                        val s = openSince.remove(e.pkg) ?: start
                        total[e.pkg] = (total[e.pkg] ?: 0) + (e.timeMs - s).coerceAtLeast(0)
                        last[e.pkg] = e.timeMs
                        openCount[e.pkg] = 0
                    } else openCount[e.pkg] = n - 1
                }
                else -> Unit
            }
        }
        openSince.forEach { (p, s) -> total[p] = (total[p] ?: 0) + (end - s).coerceAtLeast(0); last[p] = end }
        return pkgs.associateWith { PlayStat(it, total[it] ?: 0, last[it] ?: 0) }
    }

    suspend fun today(pkgs: Set<String>): PlaySummary = withContext(Dispatchers.IO) {
        if (!hasAccess() || pkgs.isEmpty()) return@withContext PlaySummary(0, emptyList(), emptyList())
        val now = System.currentTimeMillis()
        val m = foregroundTime(startOfDay(0), now, pkgs)
        val list = m.values.filter { it.foregroundMs > 0 }.sortedByDescending { it.foregroundMs }
        PlaySummary(list.sumOf { it.foregroundMs }, list, emptyList())
    }

    suspend fun week(pkgs: Set<String>): PlaySummary = withContext(Dispatchers.IO) {
        if (!hasAccess() || pkgs.isEmpty()) return@withContext PlaySummary(0, emptyList(), List(7) { 0L })
        val now = System.currentTimeMillis()
        val daily = (6 downTo 0).map { d ->
            val s = startOfDay(d)
            val e = if (d == 0) now else startOfDay(d - 1)
            foregroundTime(s, e, pkgs).values.sumOf { it.foregroundMs }
        }
        val m = foregroundTime(startOfDay(6), now, pkgs)
        val list = m.values.filter { it.foregroundMs > 0 }.sortedByDescending { it.foregroundMs }
        PlaySummary(list.sumOf { it.foregroundMs }, list, daily)
    }

    /** Most recently used games (by last time in foreground), newest first. */
    suspend fun recent(pkgs: Set<String>): List<PlayStat> = withContext(Dispatchers.IO) {
        if (!hasAccess() || pkgs.isEmpty()) return@withContext emptyList()
        val now = System.currentTimeMillis()
        val stats = runCatching { usm?.queryAndAggregateUsageStats(now - 30L * 24 * 3600 * 1000, now) }.getOrNull().orEmpty()
        pkgs.mapNotNull { p -> stats[p]?.let { PlayStat(p, it.totalTimeInForeground, it.lastTimeUsed) } }
            .filter { it.lastUsed > 0 }.sortedByDescending { it.lastUsed }
    }

    /** Apps used recently (last 3 h) that are probably still cached in memory. */
    suspend fun recentBackgroundApps(exclude: Set<String>, windowMs: Long = 3L * 3600 * 1000): List<PlayStat> = withContext(Dispatchers.IO) {
        if (!hasAccess()) return@withContext emptyList()
        val now = System.currentTimeMillis()
        runCatching { usm?.queryAndAggregateUsageStats(now - windowMs, now) }.getOrNull().orEmpty().values
            .filter { it.lastTimeUsed > now - windowMs && it.packageName !in exclude && it.packageName != context.packageName }
            .map { PlayStat(it.packageName, it.totalTimeInForeground, it.lastTimeUsed) }
            .sortedByDescending { it.lastUsed }
    }

    companion object {
        /** UsageEvents.Event.ACTIVITY_RESUMED (== MOVE_TO_FOREGROUND before API 29). */
        const val EVENT_RESUMED = 1
        /** UsageEvents.Event.ACTIVITY_PAUSED (== MOVE_TO_BACKGROUND before API 29). */
        const val EVENT_PAUSED = 2
        /** UsageEvents.Event.ACTIVITY_STOPPED (API 29+). */
        const val EVENT_STOPPED = 23
    }
}
