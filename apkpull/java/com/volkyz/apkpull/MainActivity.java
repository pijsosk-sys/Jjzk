package com.volkyz.apkpull;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Pulls an installed app's full split set (base + every config split) into one .xapk and hands it
 * to the share sheet. No root: an installed apk under /data/app is world readable, the split list
 * comes from ApplicationInfo.
 */
public class MainActivity extends Activity {

    private static final int BG = 0xFF101216, CARD = 0xFF1A1D24, FG = 0xFFE6E8EC, DIM = 0xFF8A90A0;
    private static final int ACCENT = 0xFF5B8CFF;

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler ui = new Handler(Looper.getMainLooper());

    private final List<Entry> all = new ArrayList<>();
    private final List<Entry> shown = new ArrayList<>();
    private ArrayAdapter<Entry> adapter;
    private TextView status;

    static File outputDir(Context context) {
        File dir = new File(context.getExternalFilesDir(null), "pulled");
        if (!dir.isDirectory()) dir.mkdirs();
        return dir;
    }

    private static final class Entry {
        final String label, pkg, version;
        final String base;
        final String[] splits;
        final boolean system;
        final long bytes;

        Entry(String label, String pkg, String version, String base, String[] splits,
              boolean system, long bytes) {
            this.label = label; this.pkg = pkg; this.version = version;
            this.base = base; this.splits = splits; this.system = system; this.bytes = bytes;
        }

        @Override public String toString() {
            return label + "\n" + pkg + "  ·  v" + version + "  ·  "
                    + (splits.length + 1) + " apk" + (splits.length == 0 ? "" : "s")
                    + "  ·  " + String.format(Locale.US, "%.1f MB", bytes / 1e6);
        }
    }

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        int pad = dp(12);
        root.setPadding(pad, pad, pad, 0);

        TextView title = new TextView(this);
        title.setText("APK PULL");
        title.setTextColor(ACCENT);
        title.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        root.addView(title);

        TextView hint = new TextView(this);
        hint.setText("tap an app → base + all splits are zipped into one .xapk → share it");
        hint.setTextColor(DIM);
        hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        hint.setPadding(0, dp(2), 0, dp(10));
        root.addView(hint);

        EditText filter = new EditText(this);
        filter.setHint("filter (try: lunar, mojang)");
        filter.setSingleLine(true);
        filter.setTextColor(FG);
        filter.setHintTextColor(DIM);
        filter.setBackgroundColor(CARD);
        filter.setPadding(dp(10), dp(10), dp(10), dp(10));
        root.addView(filter);

        status = new TextView(this);
        status.setTextColor(DIM);
        status.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        status.setPadding(0, dp(8), 0, dp(8));
        status.setText("reading installed packages…");
        root.addView(status);

        ListView list = new ListView(this);
        list.setBackgroundColor(BG);
        list.setDivider(null);
        list.setDividerHeight(dp(6));
        root.addView(list, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        adapter = new ArrayAdapter<Entry>(this, 0, shown) {
            @Override public View getView(int position, View convert, ViewGroup parent) {
                TextView row = convert instanceof TextView ? (TextView) convert : new TextView(parent.getContext());
                Entry entry = shown.get(position);
                row.setText(entry.toString());
                row.setTextColor(entry.system ? DIM : FG);
                row.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
                row.setBackgroundColor(CARD);
                row.setPadding(dp(12), dp(10), dp(12), dp(10));
                return row;
            }
        };
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, position, id) -> confirm(shown.get(position)));

        filter.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { apply(s.toString()); }
            @Override public void afterTextChanged(Editable s) {}
        });

        setContentView(root);
        worker.execute(this::load);
    }

    private int dp(int value) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                getResources().getDisplayMetrics());
    }

    private void load() {
        PackageManager pm = getPackageManager();
        List<PackageInfo> packages = pm.getInstalledPackages(0);
        List<Entry> found = new ArrayList<>();
        for (PackageInfo info : packages) {
            ApplicationInfo app = info.applicationInfo;
            if (app == null || app.sourceDir == null) continue;
            String[] splits = app.splitSourceDirs == null ? new String[0] : app.splitSourceDirs;
            long bytes = new File(app.sourceDir).length();
            for (String split : splits) bytes += new File(split).length();
            found.add(new Entry(
                    String.valueOf(pm.getApplicationLabel(app)),
                    info.packageName,
                    info.versionName == null ? "?" : info.versionName,
                    app.sourceDir, splits,
                    (app.flags & ApplicationInfo.FLAG_SYSTEM) != 0,
                    bytes));
        }
        // user apps first, then by label; the thing you came for is near the top
        Collections.sort(found, new Comparator<Entry>() {
            @Override public int compare(Entry a, Entry b) {
                if (a.system != b.system) return a.system ? 1 : -1;
                return a.label.compareToIgnoreCase(b.label);
            }
        });
        ui.post(() -> {
            all.clear(); all.addAll(found);
            apply("");
            status.setText(found.size() + " packages  ·  output: " + outputDir(this).getAbsolutePath());
        });
    }

    private void apply(String query) {
        String needle = query.trim().toLowerCase(Locale.US);
        shown.clear();
        for (Entry entry : all) {
            if (needle.isEmpty()
                    || entry.label.toLowerCase(Locale.US).contains(needle)
                    || entry.pkg.toLowerCase(Locale.US).contains(needle)) {
                shown.add(entry);
            }
        }
        adapter.notifyDataSetChanged();
    }

    private void confirm(Entry entry) {
        StringBuilder body = new StringBuilder();
        body.append(entry.pkg).append("\nv").append(entry.version).append("\n\n");
        body.append("base.apk\n");
        for (String split : entry.splits) body.append(new File(split).getName()).append('\n');
        body.append("\n").append(String.format(Locale.US, "%.1f MB total", entry.bytes / 1e6));
        body.append("\n\nslim = manifest + dex + arm64 .so + assets, recompressed and cut into "
                + (PART_LIMIT / 1000000) + " MB parts.\nfull = every apk byte for byte.");

        new AlertDialog.Builder(this)
                .setTitle(entry.label)
                .setMessage(body.toString())
                .setPositiveButton("slim", (dialog, which) -> worker.execute(() -> pull(entry, true)))
                .setNeutralButton("full", (dialog, which) -> worker.execute(() -> pull(entry, false)))
                .setNegativeButton("cancel", null)
                .show();
    }

    /** Upload limits are per file, so output rolls over into parts at this size. */
    private static final long PART_LIMIT = 25_000_000L;

    private static final Pattern DEX = Pattern.compile("classes\\d*\\.dex");

    /**
     * What a static analysis actually reads. Dropping res/, resources.arsc and the density and
     * language splits is most of the download on a play-store app and costs nothing here.
     */
    private static boolean keepForAnalysis(String name) {
        if (name.equals("AndroidManifest.xml")) return true;
        if (DEX.matcher(name).matches()) return true;
        if (name.startsWith("lib/") && name.endsWith(".so")) {
            return name.startsWith("lib/arm64-v8a/") || name.startsWith("lib/armeabi-v7a/");
        }
        if (name.startsWith("assets/")) {
            String low = name.toLowerCase(Locale.US);
            return !(low.endsWith(".png") || low.endsWith(".jpg") || low.endsWith(".jpeg")
                    || low.endsWith(".webp") || low.endsWith(".gif") || low.endsWith(".ogg")
                    || low.endsWith(".mp3") || low.endsWith(".wav") || low.endsWith(".mp4")
                    || low.endsWith(".ttf") || low.endsWith(".otf"));
        }
        return name.endsWith(".json") && !name.contains("/");
    }

    private void pull(Entry entry, boolean slim) {
        String safeVersion = entry.version.replaceAll("[^A-Za-z0-9._-]", "_");
        String stem = entry.pkg + "-" + safeVersion + (slim ? "-slim" : "");

        List<File> sources = new ArrayList<>();
        sources.add(new File(entry.base));
        for (String split : entry.splits) sources.add(new File(split));

        List<File> parts = new ArrayList<>();
        ZipOutputStream zip = null;
        long written = 0;
        int part = 1;

        try {
            for (File source : sources) {
                String apkName = source == sources.get(0) ? "base.apk" : source.getName();
                ui.post(() -> status.setText("reading " + apkName + " …"));

                ZipFile apk = new ZipFile(source);
                java.util.Enumeration<? extends ZipEntry> entries = apk.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry source_entry = entries.nextElement();
                    if (source_entry.isDirectory()) continue;
                    if (slim && !keepForAnalysis(source_entry.getName())) continue;

                    if (zip == null) {
                        File file = new File(outputDir(this), stem + ".part" + part + ".zip");
                        zip = new ZipOutputStream(new java.io.FileOutputStream(file));
                        // slim recompresses (dex deflates ~3x); full is already-compressed apk bytes
                        zip.setLevel(slim ? Deflater.BEST_COMPRESSION : Deflater.NO_COMPRESSION);
                        parts.add(file);
                        written = 0;
                    }

                    zip.putNextEntry(new ZipEntry(apkName + "/" + source_entry.getName()));
                    InputStream in = apk.getInputStream(source_entry);
                    byte[] buffer = new byte[1 << 16];
                    int read;
                    while ((read = in.read(buffer)) > 0) {
                        zip.write(buffer, 0, read);
                        written += read;
                    }
                    in.close();
                    zip.closeEntry();

                    final int shown_part = part;
                    final long shown_written = written;
                    ui.post(() -> status.setText(String.format(Locale.US,
                            "part %d · %.1f MB · %s", shown_part, shown_written / 1e6,
                            source_entry.getName())));

                    // rolls over on entry boundaries; one entry bigger than the limit gets its own part
                    if (written >= PART_LIMIT) {
                        zip.close(); zip = null; part++;
                    }
                }
                apk.close();
            }

            if (zip == null) {
                File file = new File(outputDir(this), stem + ".part" + part + ".zip");
                zip = new ZipOutputStream(new java.io.FileOutputStream(file));
                parts.add(file);
            }
            zip.putNextEntry(new ZipEntry("info.json"));
            Writer writer = new OutputStreamWriter(zip, "UTF-8");
            writer.write("{\n  \"package\": \"" + entry.pkg + "\",\n"
                    + "  \"label\": \"" + entry.label.replace("\"", "\\\"") + "\",\n"
                    + "  \"versionName\": \"" + entry.version + "\",\n"
                    + "  \"splits\": " + entry.splits.length + ",\n"
                    + "  \"slim\": " + slim + ",\n"
                    + "  \"parts\": " + parts.size() + ",\n"
                    + "  \"abi\": \"" + android.os.Build.SUPPORTED_ABIS[0] + "\",\n"
                    + "  \"sdk\": " + android.os.Build.VERSION.SDK_INT + "\n}\n");
            writer.flush();
            zip.closeEntry();
            zip.close();
        } catch (IOException failure) {
            try { if (zip != null) zip.close(); } catch (IOException ignored) {}
            ui.post(() -> status.setText("failed: " + failure));
            return;
        }

        ui.post(() -> {
            StringBuilder summary = new StringBuilder("done · ");
            ArrayList<Uri> uris = new ArrayList<>();
            for (File file : parts) {
                uris.add(ApkProvider.uriFor(file));
                summary.append(String.format(Locale.US, "%s (%.1f MB)  ",
                        file.getName(), file.length() / 1e6));
            }
            status.setText(summary.toString());

            Intent send;
            if (uris.size() == 1) {
                send = new Intent(Intent.ACTION_SEND);
                send.putExtra(Intent.EXTRA_STREAM, uris.get(0));
            } else {
                send = new Intent(Intent.ACTION_SEND_MULTIPLE);
                send.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
            }
            send.setType("application/zip");
            send.putExtra(Intent.EXTRA_SUBJECT, stem);
            send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(send, "send " + parts.size() + " file(s)"));
        });
    }
}
