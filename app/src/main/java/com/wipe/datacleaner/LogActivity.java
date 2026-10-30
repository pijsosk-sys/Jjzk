package com.wipe.datacleaner;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.CheckBox;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** The log menu: every attempt, and the reason it failed, in full. */
public class LogActivity extends Activity {

    private final List<LogEntry> shown = new ArrayList<>();
    private LogAdapter adapter;
    private TextView tvHeader;
    private CheckBox cbFailuresOnly;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_log);
        LogStore.get().attach(this);

        tvHeader = findViewById(R.id.tvLogHeader);
        cbFailuresOnly = findViewById(R.id.cbFailuresOnly);
        ListView list = findViewById(R.id.listLog);

        adapter = new LogAdapter(this, shown);
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, position, id) -> showDetail(shown.get(position)));

        cbFailuresOnly.setOnCheckedChangeListener((v, checked) -> reload());

        findViewById(R.id.btnCopy).setOnClickListener(v -> copyAll());
        findViewById(R.id.btnExport).setOnClickListener(v -> export());
        findViewById(R.id.btnClear).setOnClickListener(v -> confirmClear());

        reload();
    }

    private void reload() {
        shown.clear();
        shown.addAll(LogStore.get().snapshot(cbFailuresOnly.isChecked()));
        adapter.notifyDataSetChanged();
        tvHeader.setText(LogStore.get().size() + " entries, "
                + LogStore.get().failureCount() + " failures"
                + (shown.isEmpty() ? "\nNothing logged yet." : "\nTap an entry for the full reason."));
    }

    private void showDetail(LogEntry e) {
        final String text = e.toPlainText();
        new AlertDialog.Builder(this)
                .setTitle(e.levelTag() + ": " + e.title)
                .setMessage(e.summary.isEmpty() ? e.detail : e.summary + "\n\n" + e.detail)
                .setPositiveButton("Close", null)
                .setNeutralButton("Copy", (d, w) -> {
                    copy(text);
                    toast("Entry copied");
                })
                .show();
    }

    private void copyAll() {
        String all = LogStore.get().dumpAll();
        if (all.isEmpty()) {
            toast("Log is empty");
            return;
        }
        copy(all);
        toast("Whole log copied");
    }

    private void copy(String text) {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("wipe-log", text));
    }

    private void export() {
        File dir = getExternalFilesDir(null);
        if (dir == null) dir = getFilesDir();
        File out = LogStore.get().exportTo(dir);
        if (out == null) {
            toast("Export failed");
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Log exported")
                .setMessage(out.getAbsolutePath())
                .setPositiveButton("OK", null)
                .show();
    }

    private void confirmClear() {
        new AlertDialog.Builder(this)
                .setTitle("Clear log")
                .setMessage("Remove all " + LogStore.get().size() + " entries?")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Clear", (d, w) -> {
                    LogStore.get().clear();
                    reload();
                })
                .show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, 1, 0, "Copy all");
        menu.add(0, 2, 0, "Clear");
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case 1: copyAll(); return true;
            case 2: confirmClear(); return true;
            default: return super.onOptionsItemSelected(item);
        }
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }
}
