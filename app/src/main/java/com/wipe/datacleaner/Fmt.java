package com.wipe.datacleaner;

import java.util.Locale;

public final class Fmt {
    private Fmt() { }

    public static String bytes(long b) {
        if (b < 0) return "?";
        if (b < 1024) return b + " B";
        double kb = b / 1024.0;
        if (kb < 1024) return String.format(Locale.US, "%.1f KB", kb);
        double mb = kb / 1024.0;
        if (mb < 1024) return String.format(Locale.US, "%.1f MB", mb);
        return String.format(Locale.US, "%.2f GB", mb / 1024.0);
    }
}
