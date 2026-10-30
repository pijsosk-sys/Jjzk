package com.wipe.datacleaner;

import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.DocumentsContract;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Deletes a folder under Android/data by trying every route in turn and recording
 * exactly why each one refused. The recorded reasons are what the log menu shows.
 */
public final class Deleter {

    public static final String DATA_ROOT =
            Environment.getExternalStorageDirectory().getAbsolutePath() + "/Android/data";

    private static final int MAX_FAILED_PATHS_LOGGED = 15;

    private Deleter() { }

    // ---------------------------------------------------------------- model

    public static class Attempt {
        public final String method;
        public final boolean ok;
        public final String reason;
        public final String detail;

        Attempt(String method, boolean ok, String reason, String detail) {
            this.method = method;
            this.ok = ok;
            this.reason = reason;
            this.detail = detail == null ? "" : detail;
        }
    }

    public static class Result {
        public final Target target;
        public final List<Attempt> attempts = new ArrayList<>();
        public boolean ok;
        public String methodUsed;
        public int filesDeleted;
        public int entriesFailed;
        public long bytesFreed;

        Result(Target target) { this.target = target; }

        public String summary() {
            if (ok) {
                return "deleted via " + methodUsed + " - " + filesDeleted + " entries, "
                        + Fmt.bytes(bytesFreed) + " freed";
            }
            Attempt last = attempts.isEmpty() ? null : attempts.get(attempts.size() - 1);
            return "NOT deleted - " + (last == null ? "no route available" : last.reason);
        }

        /** The full "why it failed" dump shown when a log row is tapped. */
        public String detailDump() {
            StringBuilder sb = new StringBuilder();
            sb.append("Folder : ").append(target.packageName).append('\n');
            sb.append("Path   : ").append(target.path).append('\n');
            if (target.appLabel != null) sb.append("App    : ").append(target.appLabel).append('\n');
            sb.append("Android: ").append(Build.VERSION.RELEASE)
              .append(" (API ").append(Build.VERSION.SDK_INT).append(")\n");
            sb.append("Device : ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append('\n');
            sb.append('\n');
            int n = 1;
            for (Attempt a : attempts) {
                sb.append(n++).append(") ").append(a.method).append(" -> ")
                  .append(a.ok ? "SUCCESS" : "FAILED").append('\n');
                sb.append("   reason: ").append(a.reason).append('\n');
                if (!a.detail.isEmpty()) {
                    for (String line : a.detail.split("\n")) sb.append("   ").append(line).append('\n');
                }
                sb.append('\n');
            }
            return sb.toString().trim();
        }
    }

    // ---------------------------------------------------------------- driver

    /** Runs File -> SAF -> root until one works. Every attempt lands in the result. */
    public static Result delete(Context ctx, Target target, boolean allowRoot) {
        Result res = new Result(target);

        Attempt file = tryFileApi(target, res);
        res.attempts.add(file);
        if (file.ok && verifyGone(ctx, target)) {
            res.ok = true;
            res.methodUsed = file.method;
            return res;
        }
        if (file.ok) {
            res.attempts.add(new Attempt("Verification", false,
                    "folder still present after a reported success",
                    "The delete call reported success but the folder is still listed.\n"
                            + "This happens when a FUSE/sdcardfs layer only pretends to delete, or\n"
                            + "when the owning app recreated the folder immediately."));
        }

        Attempt saf = trySaf(ctx, target, res);
        res.attempts.add(saf);
        if (saf.ok && verifyGone(ctx, target)) {
            res.ok = true;
            res.methodUsed = saf.method;
            return res;
        }

        Attempt root = tryRoot(target, allowRoot);
        res.attempts.add(root);
        if (root.ok && verifyGone(ctx, target)) {
            res.ok = true;
            res.methodUsed = root.method;
            return res;
        }

        return res;
    }

    private static boolean verifyGone(Context ctx, Target t) {
        if (new File(t.path).exists()) return false;
        Uri doc = SafAccess.documentUriFor(ctx, t.path);
        return doc == null || !SafAccess.documentExists(ctx, doc);
    }

    // ---------------------------------------------------------------- strategies

    private static Attempt tryFileApi(Target t, Result res) {
        final String method = "File API (java.io.File)";
        File dir = new File(t.path);

        if (!dir.exists()) {
            return new Attempt(method, false,
                    "folder is not visible through the File API",
                    "File.exists() returned false for " + t.path + "\n" + scopedStorageNote());
        }
        if (dir.isDirectory() && dir.list() == null) {
            return new Attempt(method, false,
                    "directory cannot be listed (EACCES)",
                    "File.list() returned null, so the contents cannot be walked.\n"
                            + scopedStorageNote());
        }

        Walk walk = new Walk();
        boolean gone = deleteRecursive(dir, walk);
        res.filesDeleted += walk.deleted;
        res.entriesFailed += walk.failed;
        res.bytesFreed += walk.bytes;

        if (gone) {
            return new Attempt(method, true,
                    "removed " + walk.deleted + " entries (" + Fmt.bytes(walk.bytes) + ")", "");
        }

        StringBuilder d = new StringBuilder();
        d.append("Removed ").append(walk.deleted).append(" entries, ")
         .append(walk.failed).append(" refused.\n");
        if (!walk.failures.isEmpty()) {
            d.append("First refusals:\n");
            for (String f : walk.failures) d.append("  - ").append(f).append('\n');
            if (walk.failed > walk.failures.size()) {
                d.append("  ... and ").append(walk.failed - walk.failures.size()).append(" more\n");
            }
        }
        d.append(scopedStorageNote());
        return new Attempt(method, false,
                walk.failed + " entries refused deletion", d.toString());
    }

    private static Attempt trySaf(Context ctx, Target t, Result res) {
        final String method = "Storage Access Framework";

        String docId = SafAccess.docIdForPath(t.path);
        if (docId == null) {
            return new Attempt(method, false,
                    "path is not on primary shared storage",
                    "SAF document ids can only be derived for paths under "
                            + Environment.getExternalStorageDirectory().getAbsolutePath());
        }

        Uri tree = SafAccess.treeCovering(ctx, docId);
        if (tree == null) {
            return new Attempt(method, false,
                    "no folder access was granted for this path",
                    "No persisted tree permission covers " + docId + ".\n" + safGrantNote());
        }

        Uri doc;
        try {
            doc = DocumentsContract.buildDocumentUriUsingTree(tree, docId);
        } catch (Exception e) {
            return new Attempt(method, false,
                    "could not build a document uri",
                    e.getClass().getSimpleName() + ": " + e.getMessage());
        }

        if (!SafAccess.documentExists(ctx, doc)) {
            return new Attempt(method, false,
                    "folder not found under the granted tree",
                    "Granted tree : " + tree + "\nLooked for   : " + docId + "\n"
                            + "The grant may point at a different folder than the one being deleted.");
        }

        try {
            boolean deleted = DocumentsContract.deleteDocument(ctx.getContentResolver(), doc);
            if (deleted) {
                return new Attempt(method, true, "document provider deleted the folder",
                        "Granted tree: " + tree);
            }
            return new Attempt(method, false,
                    "document provider returned false",
                    "Granted tree: " + tree + "\nDocument    : " + docId + "\n"
                            + "The provider accepted the request but refused the delete. On many\n"
                            + "OEM builds the external-storage provider blocks writes inside\n"
                            + "Android/data even when a grant exists.");
        } catch (SecurityException e) {
            return new Attempt(method, false,
                    "SecurityException from the document provider",
                    "Granted tree: " + tree + "\n" + e.getMessage() + "\n" + safGrantNote());
        } catch (Exception e) {
            return new Attempt(method, false,
                    e.getClass().getSimpleName() + " from the document provider",
                    String.valueOf(e.getMessage()));
        }
    }

    private static Attempt tryRoot(Target t, boolean allowRoot) {
        final String method = "Root shell (su -c rm -rf)";
        if (!allowRoot) {
            return new Attempt(method, false,
                    "root strategy is switched off",
                    "Enable \"Use root if available\" on the main screen to try this route.");
        }

        Shell.Result r = Shell.runSu("rm -rf " + shQuote(t.path), 30000);
        if (r.launchError != null) {
            return new Attempt(method, false,
                    "no su binary (device is not rooted)",
                    r.describe());
        }
        if (r.timedOut) {
            return new Attempt(method, false,
                    "root request timed out",
                    "su did not answer within 30s - the superuser prompt was probably ignored.");
        }
        if (r.exitCode != 0) {
            return new Attempt(method, false,
                    "su exited with code " + r.exitCode,
                    r.describe());
        }
        return new Attempt(method, true, "rm -rf completed", r.describe());
    }

    // ---------------------------------------------------------------- walking

    private static class Walk {
        int deleted;
        int failed;
        long bytes;
        final List<String> failures = new ArrayList<>();

        void note(File f, String why) {
            failed++;
            if (failures.size() < MAX_FAILED_PATHS_LOGGED) {
                failures.add(f.getAbsolutePath() + "  (" + why + ")");
            }
        }
    }

    /** Post-order delete. Returns true only when the whole subtree is gone. */
    private static boolean deleteRecursive(File f, Walk walk) {
        if (f.isDirectory()) {
            File[] kids = f.listFiles();
            if (kids == null) {
                walk.note(f, "cannot list contents - permission denied");
                return false;
            }
            boolean allGone = true;
            for (File kid : kids) {
                if (!deleteRecursive(kid, walk)) allGone = false;
            }
            if (!allGone) {
                walk.note(f, "directory not empty - children above could not be removed");
                return false;
            }
        }

        long size = f.isFile() ? f.length() : 0;
        if (f.delete()) {
            walk.deleted++;
            walk.bytes += size;
            return true;
        }
        walk.note(f, diagnose(f));
        return false;
    }

    /** Best explanation we can get for a File.delete() that returned false. */
    private static String diagnose(File f) {
        if (!f.exists()) return "vanished before deletion";
        File parent = f.getParentFile();
        if (parent != null && !parent.canWrite()) return "parent directory is not writable";
        if (!f.canWrite()) return "no write permission on this entry";
        if (f.isDirectory()) {
            String[] left = f.list();
            if (left != null && left.length > 0) return "still contains " + left.length + " entries";
        }
        return "delete() refused by the storage layer";
    }

    // ---------------------------------------------------------------- notes

    public static String scopedStorageNote() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return "Android " + Build.VERSION.RELEASE + " blocks direct file access to "
                    + "Android/data for every app, and \"All files access\" does not lift that "
                    + "block. Grant folder access instead, or use root.";
        }
        return "Check that storage permission is granted.";
    }

    public static String safGrantNote() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return "On Android 13 and newer the system picker refuses to hand out a grant for "
                    + "Android/data or any folder inside it, so this route cannot be unlocked "
                    + "on a stock build. Root is the remaining option.";
        }
        return "Use \"Grant folder access\" and pick Android/data in the picker.";
    }

    private static String shQuote(String s) {
        return "'" + s.replace("'", "'\\''") + "'";
    }
}
