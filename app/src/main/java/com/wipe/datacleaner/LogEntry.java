package com.wipe.datacleaner;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** One line in the log menu. {@link #detail} holds the full "why it failed" dump. */
public class LogEntry {

    public static final int LEVEL_OK = 0;
    public static final int LEVEL_FAIL = 1;
    public static final int LEVEL_INFO = 2;
    public static final int LEVEL_WARN = 3;

    public final long time;
    public final int level;
    public final String title;
    public final String summary;
    public final String detail;

    public LogEntry(int level, String title, String summary, String detail) {
        this.time = System.currentTimeMillis();
        this.level = level;
        this.title = title;
        this.summary = summary == null ? "" : summary;
        this.detail = detail == null ? "" : detail;
    }

    public String timeString() {
        return new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date(time));
    }

    public String fullTimeString() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date(time));
    }

    public String levelTag() {
        switch (level) {
            case LEVEL_OK:   return "OK";
            case LEVEL_FAIL: return "FAIL";
            case LEVEL_WARN: return "WARN";
            default:         return "INFO";
        }
    }

    /** Plain-text form used for copy-to-clipboard and for the exported log file. */
    public String toPlainText() {
        StringBuilder sb = new StringBuilder();
        sb.append('[').append(fullTimeString()).append("] ")
          .append(levelTag()).append(" - ").append(title).append('\n');
        if (!summary.isEmpty()) sb.append("  ").append(summary).append('\n');
        if (!detail.isEmpty()) {
            for (String line : detail.split("\n")) sb.append("    ").append(line).append('\n');
        }
        return sb.toString();
    }
}
