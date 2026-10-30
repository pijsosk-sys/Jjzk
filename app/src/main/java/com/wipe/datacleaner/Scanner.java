package com.wipe.datacleaner;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Builds the list of folders under Android/data, falling back as each route is blocked. */
public final class Scanner {

    private static final int SIZE_WALK_FILE_CAP = 25000;

    private Scanner() { }

    /** Outcome of a scan, including whether the list is real or only guesswork. */
    public static class ScanResult {
        public final List<Target> targets = new ArrayList<>();
        /** True when Android/data could not be listed and the list is only candidates. */
        public boolean candidatesOnly;
        public String notice;
    }

    public static ScanResult scan(Context ctx, boolean allowRoot) {
        LogStore log = LogStore.get();
        ScanResult result = new ScanResult();
        Map<String, Target> found = new LinkedHashMap<>();

        // 1. Plain file listing - works below Android 11, and on ROMs that relaxed the block.
        File root = new File(Deleter.DATA_ROOT);
        String[] names = root.list();
        if (names != null && names.length > 0) {
            for (String n : names) {
                found.put(n, new Target(n, Deleter.DATA_ROOT + "/" + n, Target.SOURCE_FILE));
            }
            log.info("Scan via File API", names.length + " folders listed in " + Deleter.DATA_ROOT);
        } else {
            log.warn("Scan via File API returned nothing",
                    names == null ? "directory could not be listed" : "directory listed as empty",
                    "Path: " + Deleter.DATA_ROOT + "\n" + Deleter.scopedStorageNote());
        }

        // 2. SAF listing, if the user managed to grant a tree over Android/data.
        if (found.isEmpty()) {
            Uri tree = SafAccess.treeCovering(ctx, SafAccess.DATA_DOC_ID);
            if (tree != null) {
                List<SafAccess.Child> kids =
                        SafAccess.listChildren(ctx, tree, SafAccess.DATA_DOC_ID);
                for (SafAccess.Child c : kids) {
                    if (!c.isDir) continue;
                    Target t = new Target(c.name, Deleter.DATA_ROOT + "/" + c.name, Target.SOURCE_SAF);
                    found.put(c.name, t);
                }
                log.info("Scan via SAF", kids.size() + " entries under the granted tree");
            } else {
                log.warn("Scan via SAF skipped", "no folder access granted",
                        Deleter.safGrantNote());
            }
        }

        // 3. Root listing.
        if (found.isEmpty() && allowRoot) {
            Shell.Result r = Shell.runSu("ls -1 " + Deleter.DATA_ROOT, 15000);
            if (r.ok()) {
                for (String line : r.stdout.split("\n")) {
                    String n = line.trim();
                    if (n.isEmpty()) continue;
                    found.put(n, new Target(n, Deleter.DATA_ROOT + "/" + n, Target.SOURCE_ROOT));
                }
                log.info("Scan via root", found.size() + " folders listed by su");
            } else {
                log.warn("Scan via root failed", "su could not list the folder", r.describe());
            }
        }

        // 4. Last resort: offer every installed app as a candidate so the user can still
        //    fire a delete at it and read the exact refusal in the log.
        if (found.isEmpty()) {
            for (String pkg : installedPackages(ctx)) {
                found.put(pkg, new Target(pkg, Deleter.DATA_ROOT + "/" + pkg, Target.SOURCE_FILE));
            }
            result.candidatesOnly = true;
            result.notice = "Android/data could not be listed on this device, so these "
                    + found.size() + " entries are installed apps shown as candidates.\n"
                    + "Folders left behind by uninstalled apps are NOT in this list - "
                    + "type the folder name in the search box to target one.";
            log.warn("Listing blocked - showing installed apps instead",
                    found.size() + " candidate folders from the package manager",
                    "The folders could not be listed by any route, so this list is built from\n"
                            + "installed packages. Folders of apps that are no longer installed\n"
                            + "cannot appear here at all - they have to be typed by hand.\n"
                            + "Some entries may have no data folder; deleting them says so.");
        }

        List<Target> out = new ArrayList<>(found.values());
        fillLabels(ctx, out);
        measure(out);
        Collections.sort(out, new Comparator<Target>() {
            @Override public int compare(Target a, Target b) {
                return a.packageName.compareToIgnoreCase(b.packageName);
            }
        });
        result.targets.addAll(out);
        return result;
    }

    /** Fills in label and size for a folder the user typed by hand. */
    public static void describe(Context ctx, Target t) {
        List<Target> one = new ArrayList<>();
        one.add(t);
        fillLabels(ctx, one);
        measure(one);
    }

    private static List<String> installedPackages(Context ctx) {
        List<String> pkgs = new ArrayList<>();
        try {
            for (ApplicationInfo ai : ctx.getPackageManager().getInstalledApplications(0)) {
                pkgs.add(ai.packageName);
            }
        } catch (Exception ignored) {
        }
        Collections.sort(pkgs);
        return pkgs;
    }

    private static void fillLabels(Context ctx, List<Target> targets) {
        PackageManager pm = ctx.getPackageManager();
        for (Target t : targets) {
            try {
                ApplicationInfo ai = pm.getApplicationInfo(t.packageName, 0);
                t.appLabel = String.valueOf(pm.getApplicationLabel(ai));
            } catch (Exception e) {
                t.appLabel = null;
            }
        }
    }

    private static void measure(List<Target> targets) {
        for (Target t : targets) {
            File f = new File(t.path);
            if (!f.exists()) continue;
            long[] acc = new long[]{0, 0};
            walk(f, acc);
            t.sizeBytes = acc[0];
            t.fileCount = (int) acc[1];
        }
    }

    private static void walk(File f, long[] acc) {
        if (acc[1] > SIZE_WALK_FILE_CAP) return;
        if (f.isDirectory()) {
            File[] kids = f.listFiles();
            if (kids == null) return;
            for (File kid : kids) walk(kid, acc);
        } else {
            acc[0] += f.length();
            acc[1]++;
        }
    }
}
