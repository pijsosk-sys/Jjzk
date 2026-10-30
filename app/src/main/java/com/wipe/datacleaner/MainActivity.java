package com.wipe.datacleaner;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.DocumentsContract;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {

    private static final int REQ_SAF_TREE = 1001;
    private static final int REQ_ALL_FILES = 1002;
    private static final int REQ_LEGACY_PERM = 1003;

    private static final String PREFS = "wiper";
    private static final String KEY_ROOT = "use_root";

    private final Handler ui = new Handler(Looper.getMainLooper());

    private TextView tvStatus;
    private TextView tvCount;
    private ListView listView;
    private CheckBox cbRoot;
    private EditText etSearch;
    private Button btnDelete;
    private TargetAdapter adapter;
    private SharedPreferences prefs;

    /** Everything the scan found. */
    private final List<Target> allTargets = new ArrayList<>();
    /** What the search box currently lets through - this backs the adapter. */
    private final List<Target> visible = new ArrayList<>();
    private String query = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        LogStore.get().attach(this);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);

        tvStatus = findViewById(R.id.tvStatus);
        tvCount = findViewById(R.id.tvCount);
        listView = findViewById(R.id.listTargets);
        cbRoot = findViewById(R.id.cbRoot);
        btnDelete = findViewById(R.id.btnDelete);

        adapter = new TargetAdapter(this, visible, new Runnable() {
            @Override public void run() { updateCount(); }
        });
        listView.setAdapter(adapter);
        listView.setOnItemClickListener((parent, view, position, id) -> {
            visible.get(position).selected = !visible.get(position).selected;
            adapter.notifyDataSetChanged();
            updateCount();
        });
        listView.setOnItemLongClickListener((parent, view, position, id) -> {
            inspect(visible.get(position));
            return true;
        });

        etSearch = findViewById(R.id.etSearch);
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {
                query = s.toString().trim().toLowerCase(Locale.US);
                applyFilter();
            }
            @Override public void afterTextChanged(Editable s) { }
        });

        cbRoot.setChecked(prefs.getBoolean(KEY_ROOT, false));
        cbRoot.setOnCheckedChangeListener((v, checked) ->
                prefs.edit().putBoolean(KEY_ROOT, checked).apply());

        findViewById(R.id.btnPermission).setOnClickListener(v -> requestAllFilesAccess());
        findViewById(R.id.btnGrantFolder).setOnClickListener(v -> requestFolderAccess());
        findViewById(R.id.btnSelectAll).setOnClickListener(v -> setAllSelected(true));
        findViewById(R.id.btnSelectNone).setOnClickListener(v -> setAllSelected(false));
        findViewById(R.id.btnLogs).setOnClickListener(v -> openLog());
        btnDelete.setOnClickListener(v -> confirmDelete());

        LogStore.get().info("App started",
                Build.MANUFACTURER + " " + Build.MODEL + " - Android " + Build.VERSION.RELEASE
                        + " (API " + Build.VERSION.SDK_INT + ")");

        rescan();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateStatus();
    }

    // ------------------------------------------------------------ status

    private boolean hasAllFilesAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager();
        }
        return checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void updateStatus() {
        boolean allFiles = hasAllFilesAccess();
        Uri tree = SafAccess.treeCovering(this, SafAccess.DATA_DOC_ID);

        StringBuilder sb = new StringBuilder();
        sb.append("Android ").append(Build.VERSION.RELEASE)
          .append(" (API ").append(Build.VERSION.SDK_INT).append(")\n");
        sb.append(allFiles ? "✓" : "✗").append(" All files access\n");
        sb.append(tree != null ? "✓" : "✗").append(" Android/data folder access");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            sb.append("\n⚠ Android 11+ blocks Android/data for normal file access");
        }
        tvStatus.setText(sb.toString());
    }

    /** Re-runs the search box over the scan results. Selections are kept. */
    private void applyFilter() {
        visible.clear();
        for (Target t : allTargets) {
            if (matches(t)) visible.add(t);
        }
        adapter.notifyDataSetChanged();
        updateCount();
    }

    private boolean matches(Target t) {
        if (query.isEmpty()) return true;
        if (t.packageName.toLowerCase(Locale.US).contains(query)) return true;
        return t.appLabel != null && t.appLabel.toLowerCase(Locale.US).contains(query);
    }

    private void updateCount() {
        int sel = 0;
        long bytes = 0;
        for (Target t : allTargets) {
            if (t.selected) {
                sel++;
                if (t.sizeBytes > 0) bytes += t.sizeBytes;
            }
        }
        String shown = query.isEmpty()
                ? allTargets.size() + " folders"
                : visible.size() + " of " + allTargets.size() + " folders";
        tvCount.setText(shown + ", " + sel + " selected"
                + (bytes > 0 ? " (" + Fmt.bytes(bytes) + ")" : ""));
        btnDelete.setEnabled(sel > 0);
    }

    /** All / None act on what the search box is showing, not on hidden folders. */
    private void setAllSelected(boolean selected) {
        for (Target t : visible) t.selected = selected;
        adapter.notifyDataSetChanged();
        updateCount();
    }

    // ------------------------------------------------------------ permissions

    private void requestAllFilesAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) {
                toast("All files access is already granted");
                return;
            }
            try {
                Intent i = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                startActivityForResult(i, REQ_ALL_FILES);
            } catch (Exception e) {
                try {
                    startActivityForResult(
                            new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION),
                            REQ_ALL_FILES);
                } catch (Exception e2) {
                    LogStore.get().fail("Cannot open the all-files settings screen",
                            e2.getClass().getSimpleName(), String.valueOf(e2.getMessage()));
                    toast("This ROM has no all-files access screen - see the log");
                }
            }
        } else {
            requestPermissions(new String[]{
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQ_LEGACY_PERM);
        }
    }

    private void requestFolderAccess() {
        try {
            startActivityForResult(SafAccess.buildPickerIntent(), REQ_SAF_TREE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                toast("Pick Android/data - Android 13+ usually refuses it");
            }
        } catch (Exception e) {
            LogStore.get().fail("Cannot open the folder picker",
                    e.getClass().getSimpleName(), String.valueOf(e.getMessage()));
            toast("No folder picker on this device - see the log");
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode != REQ_LEGACY_PERM) return;
        for (int i = 0; i < permissions.length; i++) {
            if (results[i] != PackageManager.PERMISSION_GRANTED) {
                LogStore.get().fail("Storage permission denied", permissions[i],
                        "Without it the File API cannot touch shared storage at all.");
            }
        }
        updateStatus();
        rescan();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQ_ALL_FILES) {
            boolean granted = hasAllFilesAccess();
            if (granted) {
                LogStore.get().ok("All files access granted", "MANAGE_EXTERNAL_STORAGE is on", "");
            } else {
                LogStore.get().fail("All files access not granted",
                        "the permission screen was closed without granting",
                        "Note that even when granted this permission does not unlock "
                                + "Android/data on Android 11 and newer.");
            }
            updateStatus();
            rescan();
            return;
        }

        if (requestCode == REQ_SAF_TREE) {
            if (resultCode != RESULT_OK || data == null || data.getData() == null) {
                LogStore.get().fail("Folder access not granted",
                        "the picker was cancelled or returned nothing",
                        Deleter.safGrantNote());
                updateStatus();
                return;
            }
            Uri tree = data.getData();
            try {
                SafAccess.persist(this, tree);
            } catch (Exception e) {
                LogStore.get().fail("Could not keep the folder grant",
                        e.getClass().getSimpleName(), String.valueOf(e.getMessage()));
                updateStatus();
                return;
            }

            String treeId;
            try {
                treeId = DocumentsContract.getTreeDocumentId(tree);
            } catch (Exception e) {
                treeId = "?";
            }
            boolean coversData = SafAccess.DATA_DOC_ID.equals(treeId)
                    || treeId.startsWith(SafAccess.DATA_DOC_ID + "/");
            if (coversData) {
                LogStore.get().ok("Folder access granted", treeId, "Tree uri: " + tree);
            } else {
                LogStore.get().warn("Granted folder does not cover Android/data",
                        "granted: " + treeId,
                        "Deletes inside " + Deleter.DATA_ROOT + " will still be refused.\n"
                                + Deleter.safGrantNote());
            }
            updateStatus();
            rescan();
        }
    }

    // ------------------------------------------------------------ scan

    private void rescan() {
        final ProgressDialog pd = ProgressDialog.show(this, null, "Scanning Android/data...", true);
        final boolean allowRoot = cbRoot.isChecked();
        new Thread(() -> {
            final List<Target> found = Scanner.scan(MainActivity.this, allowRoot);
            ui.post(() -> {
                dismiss(pd);
                allTargets.clear();
                allTargets.addAll(found);
                applyFilter();
                updateStatus();
            });
        }).start();
    }

    // ------------------------------------------------------------ delete

    private void confirmDelete() {
        final List<Target> chosen = new ArrayList<>();
        for (Target t : allTargets) if (t.selected) chosen.add(t);
        if (chosen.isEmpty()) return;

        StringBuilder msg = new StringBuilder();
        msg.append("Delete ").append(chosen.size()).append(" folder")
           .append(chosen.size() == 1 ? "" : "s").append(" under\n")
           .append(Deleter.DATA_ROOT).append("?\n\n");
        int shown = 0;
        for (Target t : chosen) {
            if (shown++ == 8) {
                msg.append("... and ").append(chosen.size() - 8).append(" more\n");
                break;
            }
            msg.append("- ").append(t.packageName).append('\n');
        }
        msg.append("\nApp data such as logins, offline maps and downloaded media is lost. "
                + "This cannot be undone.");

        new AlertDialog.Builder(this)
                .setTitle("Confirm delete")
                .setMessage(msg.toString())
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        runDelete(chosen);
                    }
                })
                .show();
    }

    private void runDelete(final List<Target> chosen) {
        final ProgressDialog pd = new ProgressDialog(this);
        pd.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        pd.setMax(chosen.size());
        pd.setCancelable(false);
        pd.setTitle("Deleting");
        pd.show();

        final boolean allowRoot = cbRoot.isChecked();
        new Thread(() -> {
            int okCount = 0;
            int failCount = 0;
            long freed = 0;

            for (int i = 0; i < chosen.size(); i++) {
                final Target t = chosen.get(i);
                final int step = i + 1;
                ui.post(() -> {
                    pd.setProgress(step);
                    pd.setMessage(t.packageName);
                });

                Deleter.Result r = Deleter.delete(MainActivity.this, t, allowRoot);
                String title = (r.ok ? "Deleted " : "Failed to delete ") + t.packageName;
                if (r.ok) {
                    okCount++;
                    freed += r.bytesFreed;
                    LogStore.get().ok(title, r.summary(), r.detailDump());
                } else {
                    failCount++;
                    LogStore.get().fail(title, r.summary(), r.detailDump());
                }
            }

            final int fOk = okCount, fFail = failCount;
            final long fFreed = freed;
            ui.post(() -> {
                dismiss(pd);
                showSummary(fOk, fFail, fFreed);
                rescan();
            });
        }).start();
    }

    private void showSummary(int okCount, int failCount, long freed) {
        String msg = okCount + " deleted (" + Fmt.bytes(freed) + " freed)\n"
                + failCount + " failed";
        AlertDialog.Builder b = new AlertDialog.Builder(this)
                .setTitle("Finished")
                .setMessage(failCount > 0
                        ? msg + "\n\nOpen the log to see why each one failed."
                        : msg)
                .setPositiveButton("Close", null);
        if (failCount > 0) {
            b.setNeutralButton("Open log", (d, w) -> openLog());
        }
        b.show();
    }

    /** Non-destructive look at one folder; the report also lands in the log. */
    private void inspect(final Target t) {
        final ProgressDialog pd =
                ProgressDialog.show(this, null, "Inspecting " + t.packageName + "...", true);
        final boolean allowRoot = cbRoot.isChecked();
        new Thread(() -> {
            final String report = Probe.inspect(MainActivity.this, t, allowRoot);
            LogStore.get().info("Inspected " + t.packageName, t.path);
            LogStore.get().add(new LogEntry(LogEntry.LEVEL_INFO,
                    "Report for " + t.packageName, "read-only probe", report));
            ui.post(() -> {
                dismiss(pd);
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle(t.packageName)
                        .setMessage(report)
                        .setPositiveButton("Close", null)
                        .setNeutralButton("Open log", (d, w) -> openLog())
                        .show();
            });
        }).start();
    }

    private void openLog() {
        startActivity(new Intent(this, LogActivity.class));
    }

    // ------------------------------------------------------------ menu

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, 1, 0, "Rescan");
        menu.add(0, 2, 0, R.string.log_title);
        menu.add(0, 3, 0, "Why does it fail?");
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case 1: rescan(); return true;
            case 2: openLog(); return true;
            case 3: showExplainer(); return true;
            default: return super.onOptionsItemSelected(item);
        }
    }

    private void showExplainer() {
        String text =
                "Android/data is protected storage.\n\n"
                + "Android 10 and older: normal storage permission is enough.\n\n"
                + "Android 11-12: the File API is blocked for Android/data. \"All files "
                + "access\" does not lift that block. A folder grant on Android/data still "
                + "works on most builds.\n\n"
                + "Android 13+: the system picker also refuses to grant Android/data, so "
                + "only root (or a desktop adb/MTP session) can remove these folders.\n\n"
                + "This app tries the File API, then the folder grant, then root, and writes "
                + "the exact refusal of each one into the log.\n\n"
                + "Current path: " + Deleter.DATA_ROOT + "\n"
                + "Exists to this app: " + new File(Deleter.DATA_ROOT).exists();
        new AlertDialog.Builder(this)
                .setTitle("Why does it fail?")
                .setMessage(text)
                .setPositiveButton("OK", null)
                .show();
    }

    private void dismiss(ProgressDialog pd) {
        try { if (pd != null && pd.isShowing()) pd.dismiss(); } catch (Exception ignored) { }
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }
}
