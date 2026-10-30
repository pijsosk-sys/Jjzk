package com.wipe.datacleaner;

import android.net.Uri;

/** One folder under /storage/emulated/0/Android/data. */
public class Target {

    public static final int SOURCE_FILE = 0;
    public static final int SOURCE_SAF  = 1;
    public static final int SOURCE_ROOT = 2;
    public static final int SOURCE_SHIZUKU = 3;

    public final String packageName;
    public final String path;
    public String appLabel;
    public long sizeBytes = -1;
    public int fileCount = -1;
    public int source;
    public Uri documentUri;

    public boolean selected;
    /** True when the user typed this folder name instead of it coming from a scan. */
    public boolean manual;

    public Target(String packageName, String path, int source) {
        this.packageName = packageName;
        this.path = path;
        this.source = source;
    }

    public String sourceName() {
        switch (source) {
            case SOURCE_SAF:  return "SAF";
            case SOURCE_ROOT: return "root";
            case SOURCE_SHIZUKU: return "shizuku";
            default:          return "file";
        }
    }

    public String sizeText() {
        if (sizeBytes < 0) return "size unknown";
        return Fmt.bytes(sizeBytes) + (fileCount >= 0 ? " / " + fileCount + " files" : "");
    }
}
