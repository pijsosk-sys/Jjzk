package com.horizonmobile;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.NativeActivity;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * The launcher: imports the game files prepared on a computer, imports worlds,
 * keeps the game's launch options and starts the game (NativeActivity running
 * libhorizon.so, in its own process).
 *
 * Folders, in the app's internal storage (the native side reads the same ones):
 *   horizon/                  the game folder: options.txt, saves/, logs/
 *   horizon/resources/        the prepared game files (horizon-resources.json at the top)
 *   horizon/launch-args.txt   extra game arguments, one a line
 */
public class LauncherActivity extends Activity {
    private static final int PICK_RESOURCES = 1;
    private static final int PICK_WORLD = 2;
    private static final String MANIFEST = "horizon-resources.json";

    private final Handler main = new Handler(Looper.getMainLooper());
    private TextView status;
    private Button play;

    private File home() {
        return new File(getFilesDir(), "horizon");
    }

    private File resources() {
        return new File(home(), "resources");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        home().mkdirs();
        setContentView(buildLayout());
        refresh();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private View buildLayout() {
        int pad = dp(20);
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText("Horizon");
        title.setTextSize(34);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(Color.WHITE);
        column.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("Minecraft: Java Edition 26.3, rewritten in Rust, on your phone. "
                + "Unofficial: not made by, approved by or associated with Mojang or Microsoft.");
        subtitle.setTextColor(0xFFB8B4E0);
        subtitle.setPadding(0, dp(4), 0, dp(16));
        column.addView(subtitle);

        status = new TextView(this);
        status.setTextColor(Color.WHITE);
        status.setPadding(0, 0, 0, dp(16));
        column.addView(status);

        play = button("Play", v -> startGame());
        play.setBackgroundColor(0xFFF0795A);
        column.addView(play);
        column.addView(button("Import game files (.zip)", v -> pick(PICK_RESOURCES)));
        column.addView(button("Import a world (.zip)", v -> pick(PICK_WORLD)));
        column.addView(button("Launch options", v -> editLaunchOptions()));
        column.addView(button("Show the last log", v -> showLog()));
        column.addView(button("How to get the game files", v -> showHelp()));
        column.addView(button("Remove the game files", v -> confirmRemove()));

        ScrollView scroll = new ScrollView(this);
        scroll.addView(column);
        return scroll;
    }

    private Button button(String text, View.OnClickListener onClick) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setOnClickListener(onClick);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(6);
        b.setLayoutParams(lp);
        return b;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private void refresh() {
        File manifest = new File(resources(), MANIFEST);
        boolean ready = manifest.isFile();
        play.setEnabled(ready);
        play.setAlpha(ready ? 1f : 0.5f);
        File saves = new File(home(), "saves");
        String[] worlds = saves.list();
        int count = worlds == null ? 0 : worlds.length;
        status.setText(ready
                ? "Game files: imported. Worlds: " + count + "."
                : "Game files: not imported yet. Prepare them on a computer with the Horizon "
                        + "launcher, zip the folder and import it here (How to get the game files).");
    }

    private void startGame() {
        startActivity(new Intent(this, NativeActivity.class));
    }

    private void pick(int request) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES,
                new String[] {"application/zip", "application/x-zip-compressed", "application/octet-stream"});
        startActivityForResult(intent, request);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        Uri uri = data.getData();
        if (requestCode == PICK_RESOURCES) {
            importZip(uri, true);
        } else if (requestCode == PICK_WORLD) {
            importZip(uri, false);
        }
    }

    /** Unzips game files (the folder holding horizon-resources.json) or a world (the folder holding level.dat). */
    private void importZip(Uri uri, boolean gameFiles) {
        String marker = gameFiles ? MANIFEST : "level.dat";
        ProgressBar bar = new ProgressBar(this);
        bar.setPadding(dp(24), dp(24), dp(24), dp(24));
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(gameFiles ? "Importing the game files" : "Importing the world")
                .setView(bar)
                .setCancelable(false)
                .show();
        new Thread(() -> {
            String error = null;
            String done = null;
            try {
                // Pass 1: where in the zip the folder starts.
                String prefix = null;
                try (ZipInputStream zip = open(uri)) {
                    for (ZipEntry e; (e = zip.getNextEntry()) != null; ) {
                        String name = e.getName().replace('\\', '/');
                        if (!e.isDirectory() && (name.equals(marker) || name.endsWith("/" + marker))) {
                            String p = name.substring(0, name.length() - marker.length());
                            if (prefix == null || p.length() < prefix.length()) {
                                prefix = p;
                            }
                        }
                    }
                }
                if (prefix == null) {
                    throw new IOException("This zip has no " + marker
                            + (gameFiles ? ": it is not Horizon's prepared game files." : ": it is not a world."));
                }
                File target;
                if (gameFiles) {
                    target = resources();
                } else {
                    String world = prefix.isEmpty() ? stripZip(displayName(uri)) : lastSegment(prefix);
                    target = uniqueFolder(new File(home(), "saves"), world);
                }
                File staging = new File(home(), ".import");
                deleteTree(staging);
                staging.mkdirs();
                // Pass 2: the folder's files.
                try (ZipInputStream zip = open(uri)) {
                    byte[] buf = new byte[1 << 16];
                    String root = staging.getCanonicalPath() + File.separator;
                    for (ZipEntry e; (e = zip.getNextEntry()) != null; ) {
                        String name = e.getName().replace('\\', '/');
                        if (!name.startsWith(prefix) || name.length() == prefix.length()) {
                            continue;
                        }
                        File out = new File(staging, name.substring(prefix.length()));
                        // No entry may leave the folder (zip slip).
                        if (!out.getCanonicalPath().startsWith(root)) {
                            throw new IOException("Unsafe path in the zip: " + name);
                        }
                        if (e.isDirectory()) {
                            out.mkdirs();
                            continue;
                        }
                        out.getParentFile().mkdirs();
                        try (OutputStream o = new FileOutputStream(out)) {
                            for (int n; (n = zip.read(buf)) > 0; ) {
                                o.write(buf, 0, n);
                            }
                        }
                    }
                }
                deleteTree(target);
                target.getParentFile().mkdirs();
                if (!staging.renameTo(target)) {
                    throw new IOException("Could not move the files into " + target);
                }
                done = gameFiles ? "The game files are ready. Tap Play." : "World imported as " + target.getName() + ".";
            } catch (IOException | RuntimeException e) {
                error = e.getMessage() == null ? e.toString() : e.getMessage();
            }
            String message = error != null ? "Import failed: " + error : done;
            main.post(() -> {
                dialog.dismiss();
                refresh();
                new AlertDialog.Builder(this).setMessage(message).setPositiveButton("OK", null).show();
            });
        }).start();
    }

    private ZipInputStream open(Uri uri) throws IOException {
        InputStream in = getContentResolver().openInputStream(uri);
        if (in == null) {
            throw new IOException("Could not open the file");
        }
        return new ZipInputStream(new BufferedInputStream(in, 1 << 16));
    }

    private String displayName(Uri uri) {
        try (Cursor c = getContentResolver().query(uri, new String[] {OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                return c.getString(0);
            }
        } catch (RuntimeException ignored) {
            // A provider without a display name: a generic one below.
        }
        return "World";
    }

    private static String stripZip(String name) {
        return name.toLowerCase().endsWith(".zip") ? name.substring(0, name.length() - 4) : name;
    }

    private static String lastSegment(String prefix) {
        String p = prefix.endsWith("/") ? prefix.substring(0, prefix.length() - 1) : prefix;
        int slash = p.lastIndexOf('/');
        return slash < 0 ? p : p.substring(slash + 1);
    }

    private static File uniqueFolder(File parent, String name) {
        String clean = name.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        if (clean.isEmpty()) {
            clean = "World";
        }
        File f = new File(parent, clean);
        for (int i = 2; f.exists(); i++) {
            f = new File(parent, clean + " (" + i + ")");
        }
        return f;
    }

    private static void deleteTree(File f) {
        File[] children = f.listFiles();
        if (children != null) {
            for (File c : children) {
                deleteTree(c);
            }
        }
        f.delete();
    }

    private void editLaunchOptions() {
        File file = new File(home(), "launch-args.txt");
        EditText text = new EditText(this);
        text.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        text.setMinLines(4);
        text.setGravity(Gravity.TOP);
        text.setHint("One argument a line, for example:\n--render-distance\n8");
        try {
            if (file.isFile()) {
                text.setText(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
            }
        } catch (IOException ignored) {
            // Starts empty.
        }
        new AlertDialog.Builder(this)
                .setTitle("Launch options")
                .setView(text)
                .setPositiveButton("Save", (d, w) -> {
                    try {
                        Files.write(file.toPath(), text.getText().toString().getBytes(StandardCharsets.UTF_8));
                    } catch (IOException e) {
                        toastDialog("Could not save: " + e.getMessage());
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showLog() {
        File log = new File(home(), "logs/latest.log");
        String text;
        try {
            byte[] bytes = Files.readAllBytes(log.toPath());
            int from = Math.max(0, bytes.length - 64 * 1024);
            text = new String(bytes, from, bytes.length - from, StandardCharsets.UTF_8);
        } catch (IOException e) {
            text = "No log yet: start the game once.";
        }
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextIsSelectable(true);
        view.setTypeface(Typeface.MONOSPACE);
        view.setTextSize(11);
        view.setPadding(dp(12), dp(12), dp(12), dp(12));
        ScrollView scroll = new ScrollView(this);
        scroll.addView(view);
        new AlertDialog.Builder(this).setTitle("latest.log").setView(scroll).setPositiveButton("Close", null).show();
    }

    private void showHelp() {
        toastDialog("Horizon needs your own copy of Minecraft: Java Edition 26.3. The game files are "
                + "prepared from it on a computer, because preparing runs Java:\n\n"
                + "1. On a computer, install the Horizon launcher from playhorizon.dev, sign in with the "
                + "Microsoft account that owns Java Edition and press Play once. It prepares the files.\n"
                + "2. Find the prepared folder, the one holding horizon-resources.json:\n"
                + "   horizon-launcher/versions/<version>/.local/horizon-client/26.3\n"
                + "   in your data folder (Windows: %APPDATA%, macOS: ~/Library/Application Support, "
                + "Linux: ~/.local/share). Zip that 26.3 folder.\n"
                + "3. Copy the zip to this phone and tap Import game files.\n\n"
                + "Worlds: zip a world folder (the one holding level.dat) and tap Import a world.\n\n"
                + "On the phone you play offline: single player, LAN and offline-mode servers. "
                + "Signing in to online-mode servers is not supported yet.");
    }

    private void confirmRemove() {
        new AlertDialog.Builder(this)
                .setMessage("Remove the imported game files? Your worlds and settings stay.")
                .setPositiveButton("Remove", (d, w) -> {
                    deleteTree(resources());
                    refresh();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void toastDialog(String message) {
        new AlertDialog.Builder(this).setMessage(message).setPositiveButton("OK", null).show();
    }
}
