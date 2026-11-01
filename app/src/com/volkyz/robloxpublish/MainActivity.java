package com.volkyz.robloxpublish;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.text.method.ScrollingMovementMethod;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.widget.*;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Publishes a Roblox place from the phone. Studio under emulation cannot reach Roblox at all, and the
 * Creator Dashboard on a phone hides the ids in a truncated address bar, so this looks every id up from
 * a username and asks only for the one thing the website has to hand over: an API key.
 */
public class MainActivity extends Activity {
    private static final int PICK_FILE = 1;
    private static final int DARK_BG = 0xFF14121F, CARD = 0xFF1E1B2E, ACCENT = 0xFF7C5CFF;

    private EditText username, apiKey;
    private Spinner gameList;
    private TextView fileLabel, log;
    private Button findButton, pickButton, publishButton;
    private CheckBox liveBox;

    private final List<Api.Game> games = new ArrayList<>();
    private Uri placeUri;
    private String placeName = "";
    private volatile String rootPlaceId;
    private SharedPreferences prefs;
    private final Handler ui = new Handler(Looper.getMainLooper());

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences("publish", MODE_PRIVATE);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(DARK_BG);
        LinearLayout root = column(16);
        scroll.addView(root);

        root.addView(heading("Roblox Publish"));
        root.addView(note("Sends a place file straight to Roblox. No Studio, no website, no ID numbers."));

        root.addView(label("1.  Your Roblox username"));
        username = input("NimaGuku", false);
        username.setText(prefs.getString("username", ""));
        root.addView(username);
        findButton = button("Find my experiences");
        findButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { findGames(); }
        });
        root.addView(findButton);

        root.addView(label("2.  Which experience"));
        gameList = new Spinner(this);
        gameList.setBackgroundColor(CARD);
        gameList.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) { chooseGame(pos); }
            @Override public void onNothingSelected(AdapterView<?> p) { }
        });
        root.addView(gameList);

        root.addView(label("3.  The place file"));
        pickButton = button("Choose a .rbxl file");
        pickButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { pickFile(); }
        });
        root.addView(pickButton);
        fileLabel = note("Nothing chosen yet");
        root.addView(fileLabel);

        root.addView(label("4.  API key"));
        root.addView(note("create.roblox.com → Credentials → your key: add the system "
                + "universe-places, turn its toggle on, add your experience, tick write, no expiry."));
        Button keyPage = button("Open the key page");
        keyPage.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                startActivity(new Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://create.roblox.com/dashboard/credentials")));
            }
        });
        root.addView(keyPage);
        apiKey = input("Paste the key", true);
        apiKey.setText(prefs.getString("apiKey", ""));
        root.addView(apiKey);

        liveBox = new CheckBox(this);
        liveBox.setText("Publish live (off = saved version, only Studio sees it)");
        liveBox.setTextColor(Color.WHITE);
        liveBox.setChecked(true);
        root.addView(liveBox);

        publishButton = button("Publish");
        publishButton.setBackgroundColor(ACCENT);
        publishButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { publish(); }
        });
        root.addView(publishButton);

        log = new TextView(this);
        log.setTextColor(0xFFB9B4D0);
        log.setBackgroundColor(CARD);
        log.setPadding(20, 20, 20, 20);
        log.setMovementMethod(new ScrollingMovementMethod());
        log.setText("Ready.");
        root.addView(log);

        setContentView(scroll);
        String savedUniverse = prefs.getString("universeId", null);
        if (savedUniverse != null) say("Last used experience " + savedUniverse + ".");
    }

    // ---- layout helpers, so there are no XML files to keep in step -----------------------------
    private LinearLayout column(int pad) {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        int p = (int) (pad * getResources().getDisplayMetrics().density);
        l.setPadding(p, p, p, p);
        return l;
    }
    private TextView heading(String s) {
        TextView t = new TextView(this);
        t.setText(s); t.setTextSize(26); t.setTextColor(Color.WHITE);
        t.setPadding(0, 0, 0, 8); t.setGravity(Gravity.START);
        return t;
    }
    private TextView label(String s) {
        TextView t = new TextView(this);
        t.setText(s); t.setTextSize(16); t.setTextColor(0xFFCFC8FF);
        t.setPadding(0, 28, 0, 8);
        return t;
    }
    private TextView note(String s) {
        TextView t = new TextView(this);
        t.setText(s); t.setTextSize(13); t.setTextColor(0xFF9A93B5);
        t.setPadding(0, 0, 0, 6);
        return t;
    }
    private EditText input(String hint, boolean secret) {
        EditText e = new EditText(this);
        e.setHint(hint); e.setTextColor(Color.WHITE); e.setHintTextColor(0xFF6E6889);
        e.setBackgroundColor(CARD); e.setPadding(20, 24, 20, 24); e.setSingleLine(true);
        if (secret) e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        return e;
    }
    private Button button(String s) {
        Button b = new Button(this);
        b.setText(s); b.setAllCaps(false); b.setTextColor(Color.WHITE);
        b.setBackgroundColor(0xFF3A3357);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        lp.topMargin = 10;
        b.setLayoutParams(lp);
        return b;
    }

    private void say(final String line) {
        ui.post(new Runnable() { @Override public void run() {
            log.setText(log.getText() + "\n" + line);
        } });
    }
    private void busy(final boolean on) {
        ui.post(new Runnable() { @Override public void run() {
            findButton.setEnabled(!on); pickButton.setEnabled(!on); publishButton.setEnabled(!on);
        } });
    }

    // ---- the three steps ----------------------------------------------------------------------
    private void findGames() {
        final String name = username.getText().toString().trim();
        if (name.isEmpty()) { say("Type your Roblox username first."); return; }
        prefs.edit().putString("username", name).apply();
        busy(true);
        say("Looking up " + name + "…");
        new Thread(new Runnable() { @Override public void run() {
            try {
                String id = Api.userId(name);
                if (id == null) { say("No Roblox account called " + name + "."); return; }
                final List<Api.Game> found = Api.games(id);
                if (found.isEmpty()) { say("That account owns no experiences yet."); return; }
                ui.post(new Runnable() { @Override public void run() {
                    games.clear(); games.addAll(found);
                    ArrayAdapter<Api.Game> a = new ArrayAdapter<Api.Game>(MainActivity.this,
                            android.R.layout.simple_spinner_dropdown_item, games);
                    gameList.setAdapter(a);
                    say("Found " + found.size() + " experience" + (found.size() == 1 ? "" : "s") + ".");
                } });
            } catch (Exception e) {
                say("Lookup failed: " + e);
            } finally { busy(false); }
        } }).start();
    }

    private void chooseGame(final int pos) {
        if (pos < 0 || pos >= games.size()) return;
        final Api.Game g = games.get(pos);
        new Thread(new Runnable() { @Override public void run() {
            try {
                rootPlaceId = Api.rootPlace(g.universeId);
                prefs.edit().putString("universeId", g.universeId)
                        .putString("placeId", rootPlaceId == null ? "" : rootPlaceId).apply();
                say(g.name + ": experience " + g.universeId + ", starting place "
                        + (rootPlaceId == null ? "unknown" : rootPlaceId));
            } catch (Exception e) { say("Could not read that experience: " + e); }
        } }).start();
    }

    private void pickFile() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        startActivityForResult(Intent.createChooser(i, "Pick a .rbxl place file"), PICK_FILE);
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != PICK_FILE || result != RESULT_OK || data == null || data.getData() == null) return;
        placeUri = data.getData();
        placeName = placeUri.getLastPathSegment();
        if (placeName == null) placeName = "place";
        if (placeName.contains("/")) placeName = placeName.substring(placeName.lastIndexOf('/') + 1);
        fileLabel.setText(placeName);
        say("Chosen: " + placeName);
    }

    private byte[] readPlace() throws Exception {
        InputStream in = getContentResolver().openInputStream(placeUri);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[65536];
        for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
        in.close();
        return out.toByteArray();
    }

    private void publish() {
        final String key = apiKey.getText().toString().trim();
        final String universe = prefs.getString("universeId", null);
        final String place = rootPlaceId != null ? rootPlaceId : prefs.getString("placeId", null);
        final boolean live = liveBox.isChecked();
        if (key.isEmpty()) { say("Paste the API key first."); return; }
        if (universe == null || place == null || place.isEmpty()) { say("Pick an experience first."); return; }
        if (placeUri == null) { say("Choose a .rbxl file first."); return; }
        prefs.edit().putString("apiKey", key).apply();
        busy(true);
        new Thread(new Runnable() { @Override public void run() {
            try {
                byte[] bytes = readPlace();
                final boolean xml = placeName.toLowerCase().endsWith(".rbxlx");
                say("Uploading " + placeName + " (" + (bytes.length / 1024) + " KB) to place " + place + "…");
                String before = live ? Api.updatedAt(universe) : null;
                Api.Result r = Api.publish(key, universe, place, bytes, xml, live);
                if (!r.ok()) { say(Api.explain(r)); return; }
                say("Roblox accepted it.");
                if (!live) { say("Saved version: Studio sees it, players keep the live one."); return; }
                // A 200 only says Roblox took the file. Whether players get it is a different question.
                say("Checking the experience really changed…");
                for (int i = 0; i < 10; i++) {
                    Thread.sleep(6000);
                    String now = Api.updatedAt(universe);
                    if (now != null && !now.equals(before)) {
                        say("Live. The experience changed at " + now + ".");
                        return;
                    }
                }
                say("Roblox accepted the upload but the experience still reads " + before
                        + ". Check this is the starting place.");
            } catch (Exception e) {
                say("Failed: " + e);
            } finally { busy(false); }
        } }).start();
    }
}
