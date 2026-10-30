package com.volkyz.relay;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;

public class MainActivity extends Activity implements LogBus.Listener {

    private static final int BG = 0xFF0E1013, CARD = 0xFF181B21, FG = 0xFFE6E8EC,
            DIM = 0xFF868D9B, ACCENT = 0xFF66E0A3, WARN = 0xFFFF8A65;

    private final Handler ui = new Handler(Looper.getMainLooper());

    private EditText remoteField, portField;
    private CheckBox onlineBox;
    private TextView statusView, logView;
    private ScrollView logScroll;
    private LinearLayout moduleList;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        bridge.Bridge.init(getFilesDir().getAbsolutePath());
        bridge.Bridge.setLogger(LogBus.get());

        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }

        ScrollView page = new ScrollView(this);
        page.setBackgroundColor(BG);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(14);
        root.setPadding(pad, pad, pad, pad);
        page.addView(root);

        TextView title = new TextView(this);
        title.setText("RELAY");
        title.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        title.setTextColor(ACCENT);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        root.addView(title);

        statusView = label("", DIM, 12);
        root.addView(statusView);

        root.addView(label("server (host:port)", DIM, 12));
        remoteField = field("play.example.net:19132", InputType.TYPE_TEXT_VARIATION_URI);
        root.addView(remoteField);

        root.addView(label("local port", DIM, 12));
        portField = field("19132", InputType.TYPE_CLASS_NUMBER);
        root.addView(portField);

        onlineBox = new CheckBox(this);
        onlineBox.setText("sign in with Xbox account (needed for public servers)");
        onlineBox.setTextColor(FG);
        onlineBox.setChecked(true);
        root.addView(onlineBox);

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.addView(button("login", v -> login()), weighted());
        buttons.addView(button("start", v -> start()), weighted());
        buttons.addView(button("stop", v -> stop()), weighted());
        root.addView(buttons);

        root.addView(heading("modules"));
        moduleList = new LinearLayout(this);
        moduleList.setOrientation(LinearLayout.VERTICAL);
        root.addView(moduleList);

        root.addView(heading("log"));
        logScroll = new ScrollView(this);
        logScroll.setBackgroundColor(CARD);
        logView = label("", FG, 11);
        logView.setTypeface(Typeface.MONOSPACE);
        logView.setPadding(dp(8), dp(8), dp(8), dp(8));
        logScroll.addView(logView);
        root.addView(logScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(260)));

        setContentView(page);

        StringBuilder backlog = new StringBuilder();
        for (String line : LogBus.get().backlog()) backlog.append(line).append('\n');
        logView.setText(backlog.toString());

        renderModules();
        refreshStatus();
    }

    @Override protected void onResume() {
        super.onResume();
        LogBus.get().attach(this);
        refreshStatus();
    }

    @Override protected void onPause() {
        LogBus.get().detach(this);
        super.onPause();
    }

    @Override public void onLine(String line) {
        ui.post(() -> {
            logView.append(line + "\n");
            logScroll.post(() -> logScroll.fullScroll(View.FOCUS_DOWN));
            refreshStatus();
        });
    }

    // ------------------------------------------------------------------ actions

    private void login() {
        toast("opening device login — watch the log for the code");
        new Thread(() -> {
            try {
                bridge.Bridge.login();
            } catch (Exception failure) {
                LogBus.get().log("login failed: " + failure.getMessage());
            }
            ui.post(this::refreshStatus);
        }, "relay-login").start();
    }

    private void start() {
        String remote = remoteField.getText().toString().trim();
        if (!remote.contains(":")) remote = remote + ":19132";
        int port;
        try {
            port = Integer.parseInt(portField.getText().toString().trim());
        } catch (NumberFormatException bad) {
            toast("local port must be a number");
            return;
        }
        boolean online = onlineBox.isChecked();
        if (online && !bridge.Bridge.loggedIn()) {
            toast("sign in first, or untick the box for an offline server");
            return;
        }
        ProxyService.start(this, remote, port, online);
        ui.postDelayed(this::refreshStatus, 600);
    }

    private void stop() {
        ProxyService.stop(this);
        ui.postDelayed(this::refreshStatus, 400);
    }

    private void refreshStatus() {
        String state = bridge.Bridge.running()
                ? (bridge.Bridge.connected() ? "running · client connected" : "running · waiting for client")
                : "stopped";
        statusView.setTextColor(bridge.Bridge.running() ? ACCENT : DIM);
        statusView.setText("Minecraft " + bridge.Bridge.version()
                + " (protocol " + bridge.Bridge.protocol() + ")  ·  "
                + (bridge.Bridge.loggedIn() ? "signed in" : "not signed in")
                + "  ·  " + state);
    }

    // ------------------------------------------------------------------ modules

    private void renderModules() {
        moduleList.removeAllViews();
        try {
            JSONArray modules = new JSONArray(bridge.Bridge.modulesJSON());
            for (int i = 0; i < modules.length(); i++) {
                moduleList.addView(moduleRow(modules.getJSONObject(i)));
            }
        } catch (Exception bad) {
            moduleList.addView(label("module list unreadable: " + bad, WARN, 12));
        }
    }

    private View moduleRow(JSONObject module) throws Exception {
        String name = module.getString("name");
        String desc = module.getString("desc");
        String category = module.getString("category");
        boolean enabled = module.getBoolean("enabled");
        JSONArray options = module.optJSONArray("options");

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setBackgroundColor(CARD);
        row.setPadding(dp(10), dp(8), dp(10), dp(8));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(6);
        row.setLayoutParams(params);

        CheckBox toggle = new CheckBox(this);
        toggle.setText(name + "  ·  " + category);
        toggle.setTextColor(FG);
        toggle.setChecked(enabled);
        toggle.setOnCheckedChangeListener((view, checked) -> {
            try {
                bridge.Bridge.toggle(name, checked);
            } catch (Exception failure) {
                toast(failure.getMessage());
            }
        });
        row.addView(toggle);
        row.addView(label(desc, DIM, 11));

        if (options != null && options.length() > 0) {
            Button settings = button("settings (" + options.length() + ")",
                    v -> optionsDialog(name, options));
            row.addView(settings);
        }
        return row;
    }

    private void optionsDialog(String module, JSONArray options) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(8), dp(16), dp(8));

        EditText[] fields = new EditText[options.length()];
        String[] keys = new String[options.length()];

        try {
            for (int i = 0; i < options.length(); i++) {
                JSONObject option = options.getJSONObject(i);
                keys[i] = option.getString("key");
                String kind = option.getString("kind");
                box.addView(label(keys[i] + "  (" + kind + ")", DIM, 12));
                fields[i] = field(option.getString("value"),
                        kind.equals("float") || kind.equals("int")
                                ? InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL
                                : InputType.TYPE_CLASS_TEXT);
                box.addView(fields[i]);
            }
        } catch (Exception bad) {
            toast("options unreadable");
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle(module)
                .setView(box)
                .setPositiveButton("apply", (dialog, which) -> {
                    for (int i = 0; i < keys.length; i++) {
                        try {
                            bridge.Bridge.setOption(module, keys[i],
                                    fields[i].getText().toString().trim());
                        } catch (Exception failure) {
                            toast(failure.getMessage());
                        }
                    }
                    renderModules();
                })
                .setNegativeButton("cancel", null)
                .show();
    }

    // ------------------------------------------------------------------ views

    private int dp(int value) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                getResources().getDisplayMetrics());
    }

    private TextView label(String text, int colour, int sp) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(colour);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        return view;
    }

    private TextView heading(String text) {
        TextView view = label(text.toUpperCase(), ACCENT, 13);
        view.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        view.setPadding(0, dp(14), 0, dp(6));
        return view;
    }

    private EditText field(String value, int inputType) {
        EditText view = new EditText(this);
        view.setText(value);
        view.setSingleLine(true);
        view.setInputType(inputType);
        view.setTextColor(FG);
        view.setBackgroundColor(CARD);
        view.setPadding(dp(10), dp(10), dp(10), dp(10));
        return view;
    }

    private Button button(String text, View.OnClickListener onClick) {
        Button view = new Button(this);
        view.setText(text);
        view.setAllCaps(false);
        view.setOnClickListener(onClick);
        return view;
    }

    private LinearLayout.LayoutParams weighted() {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
