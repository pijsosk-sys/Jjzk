package com.redcore.booster.data

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

data class PlayStat(val pkg: String, val foregroundMs: Long, val lastUsed: Long)

data class PlaySummary(
    val totalMs: Long,
    val perGame: List<PlayStat>,
    val dailyMs: List<Long>, // last 7 days, oldest first (for week view)
)

/** Play-time statistics from Android's Usage Access. Requires the user to grant "Usage access". */
class UsageRepository(private val context: Context) {
    private val usm = context.getSystemService(UsageStatsManager::class.java)

    fun hasAccess(): Boolean {
        val ops = context.getSystemService(AppOpsManager::class.java)
        @Suppress("DEPRECATION")
        val mode = ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun startOfDay(daysAgo: Int): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        add(Calendar.DAY_OF_YEAR, -daysAgo)
    }.timeInMillis

    /** Foreground time per package computed from activity resume/pause events (accurate for partial days). */
    private fun foregroundTime(start: Long, end: Long, pkgs: Set<String>): Map<String, PlayStat> {
        val events = usm?.queryEvents(start, end) ?: return emptyMap()
        val open = HashMap<String, Long>()
        val total = HashMap<String, Long>()
        val last = HashMap<String, Long>()
        val e = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            if (e.packageName !in pkgs) continue
            @Suppress("DEPRECATION")
            when (e.eventType) {
                UsageEvents.Event.MOVE_TO_FOREGROUND -> { if (open[e.packageName] == null) open[e.packageName] = e.timeStamp }
                UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                    val s = open.remove(e.packageName) ?: start
                    total[e.packageName] = (total[e.packageName] ?: 0) + (e.timeStamp - s)
                    last[e.packageName] = e.timeStamp
                }
            }
        }
        open.forEach { (p, s) -> total[p] = (total[p] ?: 0) + (end - s); last[p] = end }
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
        val stats = usm?.queryAndAggregateUsageStats(now - 30L * 24 * 3600 * 1000, now).orEmpty()
        pkgs.mapNotNull { p -> stats[p]?.let { PlayStat(p, it.totalTimeInForeground, it.lastTimeUsed) } }
            .filter { it.lastUsed > 0 }.sortedByDescending { it.lastUsed }
    }

    /** Apps used recently (last 3 h) that are probably still cached in memory. */
    suspend fun recentBackgroundApps(exclude: Set<String>, windowMs: Long = 3L * 3600 * 1000): List<PlayStat> = withContext(Dispatchers.IO) {
        if (!hasAccess()) return@withContext emptyList()
        val now = System.currentTimeMillis()
        usm?.queryAndAggregateUsageStats(now - windowMs, now).orEmpty().values
            .filter { it.lastTimeUsed > now - windowMs && it.packageName !in exclude && it.packageName != context.packageName }
            .map { PlayStat(it.packageName, it.totalTimeInForeground, it.lastTimeUsed) }
            .sortedByDescending { it.lastUsed }
    }

    /** Package currently in the foreground (based on the most recent resume event in the last [windowMs]). */
    fun foregroundPackage(windowMs: Long = 10_000): String? {
        val now = System.currentTimeMillis()
        val events = usm?.queryEvents(now - windowMs, now) ?: return null
        val e = UsageEvents.Event()
        var pkg: String? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            @Suppress("DEPRECATION")
            if (e.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) pkg = e.packageName
        }
        return pkg
    }
}
