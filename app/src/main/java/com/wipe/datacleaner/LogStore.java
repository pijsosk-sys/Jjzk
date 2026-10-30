package com.wipe.datacleaner;

import android.content.Context;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * In-memory log that survives between the two activities, mirrored to a file in the
 * app's private storage so a crash does not take the failure reasons with it.
 */
public final class LogStore {

    private static final int MAX_ENTRIES = 2000;
    private static LogStore instance;

    private final List<LogEntry> entries = new ArrayList<>();
    private File logFile;

    private LogStore() { }

    public static synchronized LogStore get() {
        if (instance == null) instance = new LogStore();
        return instance;
    }

    public synchronized void attach(Context ctx) {
        if (logFile == null) {
            logFile = new File(ctx.getFilesDir(), "wipe-log.txt");
        }
    }

    public synchronized void add(LogEntry e) {
        entries.add(e);
        if (entries.size() > MAX_ENTRIES) entries.remove(0);
        appendToFile(e);
    }

    public void info(String title, String summary) {
        add(new LogEntry(LogEntry.LEVEL_INFO, title, summary, null));
    }

    public void warn(String title, String summary, String detail) {
        add(new LogEntry(LogEntry.LEVEL_WARN, title, summary, detail));
    }

    public void ok(String title, String summary, String detail) {
        add(new LogEntry(LogEntry.LEVEL_OK, title, summary, detail));
    }

    public void fail(String title, String summary, String detail) {
        add(new LogEntry(LogEntry.LEVEL_FAIL, title, summary, detail));
    }

    /** Newest first, optionally only the failures. */
    public synchronized List<LogEntry> snapshot(boolean failuresOnly) {
        List<LogEntry> copy = new ArrayList<>();
        for (LogEntry e : entries) {
            if (!failuresOnly || e.level == LogEntry.LEVEL_FAIL || e.level == LogEntry.LEVEL_WARN) {
                copy.add(e);
            }
        }
        Collections.reverse(copy);
        return copy;
    }

    public synchronized int size() { return entries.size(); }

    public synchronized int failureCount() {
        int n = 0;
        for (LogEntry e : entries) if (e.level == LogEntry.LEVEL_FAIL) n++;
        return n;
    }

    public synchronized void clear() {
        entries.clear();
        if (logFile != null && logFile.exists() && !logFile.delete()) {
            // Nothing useful to do; the in-memory view is already empty.
            logFile.deleteOnExit();
        }
    }

    public synchronized String dumpAll() {
        StringBuilder sb = new StringBuilder();
        for (LogEntry e : entries) sb.append(e.toPlainText());
        return sb.toString();
    }

    /** Writes the whole log next to the app's files so the user can share it. */
    public synchronized File exportTo(File dir) {
        File out = new File(dir, "wipe-log-export.txt");
        BufferedWriter w = null;
        try {
            w = new BufferedWriter(new FileWriter(out, false));
            w.write(dumpAll());
            return out;
        } catch (Exception e) {
            return null;
        } finally {
            closeQuietly(w);
        }
    }

    private void appendToFile(LogEntry e) {
        if (logFile == null) return;
        BufferedWriter w = null;
        try {
            w = new BufferedWriter(new FileWriter(logFile, true));
            w.write(e.toPlainText());
        } catch (Exception ignored) {
            // Logging must never crash the delete run.
        } finally {
            closeQuietly(w);
        }
    }

    private static void closeQuietly(BufferedWriter w) {
        if (w != null) try { w.close(); } catch (Exception ignored) { }
    }
}
