package com.wipe.datacleaner;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.net.Uri;
import android.os.Build;
import android.provider.DocumentsContract;

import java.io.File;
import java.util.List;

/**
 * Read-only investigation of a single folder: what each route can see, and what it
 * would refuse. Deletes nothing, so it is safe to run on anything.
 */
public final class Probe {

    private static final int MAX_CHILDREN_LISTED = 25;

    private Probe() { }

    public static String inspect(Context ctx, Target t, boolean allowRoot) {
        StringBuilder sb = new StringBuilder();

        sb.append("Folder : ").append(t.packageName).append('\n');
        sb.append("Path   : ").append(t.path).append('\n');
        sb.append("App    : ").append(appState(ctx, t)).append('\n');
        sb.append("Device : ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL)
          .append(" - Android ").append(Build.VERSION.RELEASE)
          .append(" (API ").append(Build.VERSION.SDK_INT).append(")\n");

        sb.append('\n').append(fileSection(t));
        sb.append('\n').append(safSection(ctx, t));
        sb.append('\n').append(shizukuSection(ctx, t));
        sb.append('\n').append(rootSection(t, allowRoot));

        return sb.toString().trim();
    }

    private static String appState(Context ctx, Target t) {
        try {
            ApplicationInfo ai = ctx.getPackageManager().getApplicationInfo(t.packageName, 0);
            String label = String.valueOf(ctx.getPackageManager().getApplicationLabel(ai));
            boolean system = (ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
            return label + (system ? " (system app)" : " (installed)");
        } catch (Exception e) {
            return "not installed - this folder is leftover data";
        }
    }

    // ---------------------------------------------------------------- File API

    private static String fileSection(Target t) {
        StringBuilder sb = new StringBuilder("FILE API\n");
        File f = new File(t.path);

        boolean exists = f.exists();
        sb.append("  exists      : ").append(exists).append('\n');
        if (!exists) {
            sb.append("  note        : ").append(wrap(Deleter.scopedStorageNote())).append('\n');
            sb.append("  verdict     : delete via this route would fail immediately\n");
            return sb.toString();
        }

        sb.append("  directory   : ").append(f.isDirectory()).append('\n');
        sb.append("  readable    : ").append(f.canRead()).append('\n');
        sb.append("  writable    : ").append(f.canWrite()).append('\n');
        File parent = f.getParentFile();
        if (parent != null) {
            sb.append("  parent wr.  : ").append(parent.canWrite()).append('\n');
        }

        String[] kids = f.list();
        if (kids == null) {
            sb.append("  children    : not listable (EACCES)\n");
            sb.append("  verdict     : delete would stop at the first unreadable directory\n");
            return sb.toString();
        }

        sb.append("  children    : ").append(kids.length).append('\n');
        long[] acc = new long[]{0, 0};
        walk(f, acc);
        sb.append("  size        : ").append(Fmt.bytes(acc[0]))
          .append(" in ").append(acc[1]).append(" files\n");

        int shown = Math.min(kids.length, MAX_CHILDREN_LISTED);
        for (int i = 0; i < shown; i++) {
            File kid = new File(f, kids[i]);
            sb.append("    - ").append(kids[i]).append(kid.isDirectory() ? "/" : "")
              .append(kid.isDirectory() ? "" : "  " + Fmt.bytes(kid.length())).append('\n');
        }
        if (kids.length > shown) {
            sb.append("    ... and ").append(kids.length - shown).append(" more\n");
        }
        sb.append("  verdict     : ")
          .append(f.canWrite() ? "delete via this route should work\n"
                               : "delete would be refused - not writable\n");
        return sb.toString();
    }

    private static void walk(File f, long[] acc) {
        if (acc[1] > 25000) return;
        if (f.isDirectory()) {
            File[] kids = f.listFiles();
            if (kids == null) return;
            for (File kid : kids) walk(kid, acc);
        } else {
            acc[0] += f.length();
            acc[1]++;
        }
    }

    // ---------------------------------------------------------------- SAF

    private static String safSection(Context ctx, Target t) {
        StringBuilder sb = new StringBuilder("STORAGE ACCESS FRAMEWORK\n");

        String docId = SafAccess.docIdForPath(t.path);
        sb.append("  document id : ").append(docId == null ? "cannot be derived" : docId).append('\n');
        if (docId == null) {
            sb.append("  verdict     : path is not on primary shared storage\n");
            return sb.toString();
        }

        Uri tree = SafAccess.treeCovering(ctx, docId);
        if (tree == null) {
            sb.append("  grant       : none covers this path\n");
            sb.append("  note        : ").append(wrap(Deleter.safGrantNote())).append('\n');
            sb.append("  verdict     : delete via this route would be refused\n");
            return sb.toString();
        }

        String treeId;
        try {
            treeId = DocumentsContract.getTreeDocumentId(tree);
        } catch (Exception e) {
            treeId = "?";
        }
        sb.append("  grant       : ").append(treeId).append('\n');

        Uri doc;
        try {
            doc = DocumentsContract.buildDocumentUriUsingTree(tree, docId);
        } catch (Exception e) {
            sb.append("  verdict     : document uri could not be built - ")
              .append(e.getClass().getSimpleName()).append('\n');
            return sb.toString();
        }

        boolean found = SafAccess.documentExists(ctx, doc);
        sb.append("  document    : ").append(found ? "found" : "not found under the grant").append('\n');
        if (!found) {
            sb.append("  verdict     : delete via this route would be refused\n");
            return sb.toString();
        }

        List<SafAccess.Child> kids = SafAccess.listChildren(ctx, tree, docId);
        sb.append("  children    : ").append(kids.size()).append('\n');
        int shown = Math.min(kids.size(), MAX_CHILDREN_LISTED);
        for (int i = 0; i < shown; i++) {
            SafAccess.Child c = kids.get(i);
            sb.append("    - ").append(c.name).append(c.isDir ? "/" : "").append('\n');
        }
        if (kids.size() > shown) {
            sb.append("    ... and ").append(kids.size() - shown).append(" more\n");
        }
        sb.append("  verdict     : delete via this route is worth trying\n");
        return sb.toString();
    }

    // ---------------------------------------------------------------- root

    private static String shizukuSection(Context ctx, Target t) {
        StringBuilder sb = new StringBuilder("SHIZUKU (shell user)\n");
        ShizukuRoute.State st = ShizukuRoute.state(ctx);
        sb.append("  state       : ").append(st.text).append('\n');
        if (st != ShizukuRoute.State.READY) {
            sb.append("  fix         : ").append(wrap(ShizukuRoute.howToFix(st))).append('\n');
            sb.append("  verdict     : this route is not usable yet\n");
            return sb.toString();
        }

        sb.append("  version     : ").append(ShizukuRoute.version()).append('\n');
        Shell.Result ls = ShizukuRoute.exec(
                "ls -la " + shQuote(t.path) + " 2>&1 | head -25", 20000);
        if (ls.launchError != null) {
            sb.append("  error       : ").append(wrap(ls.launchError)).append('\n');
            sb.append("  verdict     : Shizuku is running but the command could not start\n");
            return sb.toString();
        }
        sb.append("  ls -la      : ").append(ls.exitCode == 0 ? "ok" : "exit " + ls.exitCode)
          .append('\n');
        for (String line : ls.stdout.split("\n")) {
            if (!line.trim().isEmpty()) sb.append("    ").append(line).append('\n');
        }
        Shell.Result du = ShizukuRoute.exec("du -sh " + shQuote(t.path) + " 2>&1", 25000);
        if (du.stdout != null && !du.stdout.trim().isEmpty()) {
            sb.append("  du -sh      : ").append(du.stdout.trim()).append('\n');
        }
        sb.append("  verdict     : ")
          .append(ls.exitCode == 0 ? "Shizuku can remove this folder\n"
                                   : "the folder was not found by the shell user\n");
        return sb.toString();
    }

    private static String rootSection(Target t, boolean allowRoot) {
        StringBuilder sb = new StringBuilder("ROOT\n");
        if (!allowRoot) {
            sb.append("  enabled     : no\n");
            sb.append("  note        : tick \"Use root if available\" to probe this route\n");
            return sb.toString();
        }

        Shell.Result id = Shell.runSu("id", 10000);
        if (!id.ok()) {
            sb.append("  su          : not available\n");
            sb.append("  detail      : ").append(wrap(id.describe())).append('\n');
            sb.append("  verdict     : this device cannot use the root route\n");
            return sb.toString();
        }
        sb.append("  su          : available (").append(id.stdout.trim()).append(")\n");

        Shell.Result ls = Shell.runSu("ls -la " + shQuote(t.path) + " 2>&1 | head -25", 15000);
        sb.append("  ls -la      : ").append(ls.exitCode == 0 ? "ok" : "exit " + ls.exitCode).append('\n');
        for (String line : ls.stdout.split("\n")) {
            if (!line.trim().isEmpty()) sb.append("    ").append(line).append('\n');
        }

        Shell.Result du = Shell.runSu("du -sh " + shQuote(t.path) + " 2>&1", 20000);
        if (!du.stdout.trim().isEmpty()) {
            sb.append("  du -sh      : ").append(du.stdout.trim()).append('\n');
        }
        sb.append("  verdict     : root can remove this folder\n");
        return sb.toString();
    }

    private static String shQuote(String s) {
        return "'" + s.replace("'", "'\\''") + "'";
    }

    /** Keeps the long explanation notes readable inside the indented report. */
    private static String wrap(String s) {
        return s.replace("\n", "\n                ");
    }
}
