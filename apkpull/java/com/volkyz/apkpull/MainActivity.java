package com.volkyz.apkpull;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
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
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
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

        new AlertDialog.Builder(this)
                .setTitle(entry.label)
                .setMessage(body.toString())
                .setPositiveButton("pull", (dialog, which) -> worker.execute(() -> pull(entry)))
                .setNegativeButton("cancel", null)
                .show();
    }

    private void pull(Entry entry) {
        String safeVersion = entry.version.replaceAll("[^A-Za-z0-9._-]", "_");
        File out = new File(outputDir(this), entry.pkg + "-" + safeVersion + ".xapk");
        try {
            ZipOutputStream zip = new ZipOutputStream(new java.io.FileOutputStream(out));
            // apk entries are already deflated; recompressing them only burns phone battery
            zip.setLevel(Deflater.NO_COMPRESSION);

            copy(zip, new File(entry.base), "base.apk", entry, 0);
            for (int i = 0; i < entry.splits.length; i++) {
                File split = new File(entry.splits[i]);
                copy(zip, split, split.getName(), entry, i + 1);
            }

            zip.putNextEntry(new ZipEntry("info.json"));
            Writer writer = new OutputStreamWriter(zip, "UTF-8");
            writer.write("{\n  \"package\": \"" + entry.pkg + "\",\n"
                    + "  \"label\": \"" + entry.label.replace("\"", "\\\"") + "\",\n"
                    + "  \"versionName\": \"" + entry.version + "\",\n"
                    + "  \"splits\": " + entry.splits.length + ",\n"
                    + "  \"abi\": \"" + android.os.Build.SUPPORTED_ABIS[0] + "\",\n"
                    + "  \"sdk\": " + android.os.Build.VERSION.SDK_INT + "\n}\n");
            writer.flush();
            zip.closeEntry();
            zip.close();
        } catch (IOException failure) {
            ui.post(() -> status.setText("failed: " + failure));
            return;
        }

        ui.post(() -> {
            status.setText("done: " + out.getName() + String.format(Locale.US,
                    "  (%.1f MB)", out.length() / 1e6));
            Intent send = new Intent(Intent.ACTION_SEND);
            send.setType("application/zip");
            send.putExtra(Intent.EXTRA_STREAM, ApkProvider.uriFor(out));
            send.putExtra(Intent.EXTRA_SUBJECT, out.getName());
            send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(send, "send " + out.getName()));
        });
    }

    private void copy(ZipOutputStream zip, File file, String name, Entry entry, int index)
            throws IOException {
        ui.post(() -> status.setText("packing " + name + " …"));
        zip.putNextEntry(new ZipEntry(name));
        InputStream in = new FileInputStream(file);
        byte[] buffer = new byte[1 << 16];
        int read;
        while ((read = in.read(buffer)) > 0) zip.write(buffer, 0, read);
        in.close();
        zip.closeEntry();
    }
}
