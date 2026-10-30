package com.wipe.datacleaner;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.UriPermission;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.DocumentsContract;

import java.util.ArrayList;
import java.util.List;

/**
 * Storage Access Framework helper.
 *
 * On Android 11+ the plain File API cannot touch /Android/data at all, so a tree
 * grant is the only non-root way in. On Android 13+ the system document picker
 * refuses to hand out a grant for Android/data itself, which is exactly the kind
 * of failure the log menu is there to spell out.
 */
public final class SafAccess {

    public static final String EXTERNAL_AUTHORITY = "com.android.externalstorage.documents";
    public static final String DATA_DOC_ID = "primary:Android/data";
    private static final String PRIMARY_ROOT = "/storage/emulated/0/";

    private SafAccess() { }

    /** Document id the external-storage provider uses for a primary-storage path. */
    public static String docIdForPath(String path) {
        if (path.startsWith(PRIMARY_ROOT)) {
            return "primary:" + path.substring(PRIMARY_ROOT.length());
        }
        if (path.startsWith("/sdcard/")) {
            return "primary:" + path.substring("/sdcard/".length());
        }
        return null;
    }

    /** The persisted tree grant that covers {@code docId}, or null if the user granted none. */
    public static Uri treeCovering(Context ctx, String docId) {
        if (docId == null) return null;
        ContentResolver cr = ctx.getContentResolver();
        Uri best = null;
        String bestId = null;
        for (UriPermission p : cr.getPersistedUriPermissions()) {
            if (!p.isReadPermission() || !p.isWritePermission()) continue;
            Uri uri = p.getUri();
            if (!EXTERNAL_AUTHORITY.equals(uri.getAuthority())) continue;
            String treeId;
            try {
                treeId = DocumentsContract.getTreeDocumentId(uri);
            } catch (Exception e) {
                continue;
            }
            if (treeId == null) continue;
            if (docId.equals(treeId) || docId.startsWith(treeId + "/")) {
                if (bestId == null || treeId.length() > bestId.length()) {
                    best = uri;
                    bestId = treeId;
                }
            }
        }
        return best;
    }

    /** Document uri for a concrete path, built from a covering tree grant. */
    public static Uri documentUriFor(Context ctx, String path) {
        String docId = docIdForPath(path);
        Uri tree = treeCovering(ctx, docId);
        if (tree == null) return null;
        try {
            return DocumentsContract.buildDocumentUriUsingTree(tree, docId);
        } catch (Exception e) {
            return null;
        }
    }

    public static boolean documentExists(Context ctx, Uri docUri) {
        if (docUri == null) return false;
        Cursor c = null;
        try {
            c = ctx.getContentResolver().query(docUri,
                    new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID}, null, null, null);
            return c != null && c.getCount() > 0;
        } catch (Exception e) {
            return false;
        } finally {
            if (c != null) c.close();
        }
    }

    public static class Child {
        public final String docId;
        public final String name;
        public final boolean isDir;

        Child(String docId, String name, boolean isDir) {
            this.docId = docId;
            this.name = name;
            this.isDir = isDir;
        }
    }

    /** Lists the children of a directory document inside a tree grant. */
    public static List<Child> listChildren(Context ctx, Uri tree, String parentDocId) {
        List<Child> out = new ArrayList<>();
        Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentDocId);
        Cursor c = null;
        try {
            c = ctx.getContentResolver().query(childrenUri, new String[]{
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE}, null, null, null);
            if (c == null) return out;
            while (c.moveToNext()) {
                String id = c.getString(0);
                String name = c.getString(1);
                String mime = c.getString(2);
                out.add(new Child(id, name, DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)));
            }
        } catch (Exception ignored) {
        } finally {
            if (c != null) c.close();
        }
        return out;
    }

    /** Picker intent, pre-pointed at Android/data where the platform allows it. */
    public static Intent buildPickerIntent() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                Uri initial = DocumentsContract.buildDocumentUri(EXTERNAL_AUTHORITY, DATA_DOC_ID);
                i.putExtra(DocumentsContract.EXTRA_INITIAL_URI, initial);
            } catch (Exception ignored) {
                // Pre-pointing is a convenience only; the picker still opens without it.
            }
        }
        return i;
    }

    public static void persist(Context ctx, Uri treeUri) {
        ctx.getContentResolver().takePersistableUriPermission(treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
    }
}
