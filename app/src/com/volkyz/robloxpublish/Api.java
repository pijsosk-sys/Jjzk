package com.volkyz.robloxpublish;

import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/** Every Roblox call this app makes. Only the publish needs the key; the lookups are public. */
public class Api {
    public static class Game {
        public final String universeId, name;
        public Game(String u, String n) { universeId = u; name = n; }
        @Override public String toString() { return name; }
    }

    public static class Result {
        public final int code; public final String body;
        Result(int c, String b) { code = c; body = b; }
        public boolean ok() { return code == 200; }
    }

    private static String read(InputStream in) throws Exception {
        if (in == null) return "";
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
        return out.toString("UTF-8");
    }

    private static Result call(String url, String method, byte[] body, String contentType, String apiKey)
            throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setRequestMethod(method);
        c.setConnectTimeout(30000);
        c.setReadTimeout(300000);
        if (apiKey != null) c.setRequestProperty("x-api-key", apiKey);
        if (body != null) {
            c.setDoOutput(true);
            c.setFixedLengthStreamingMode(body.length);
            c.setRequestProperty("Content-Type", contentType);
            OutputStream os = c.getOutputStream();
            os.write(body);
            os.flush();
            os.close();
        }
        int code = c.getResponseCode();
        String text;
        try { text = read(code >= 400 ? c.getErrorStream() : c.getInputStream()); }
        finally { c.disconnect(); }
        Log.d("RobloxPublish", method + " " + url + " -> " + code);
        return new Result(code, text);
    }

    /** A username to the account's id, or null. */
    public static String userId(String username) throws Exception {
        JSONObject req = new JSONObject();
        req.put("usernames", new JSONArray().put(username));
        req.put("excludeBannedUsers", false);
        Result r = call("https://users.roblox.com/v1/usernames/users", "POST",
                req.toString().getBytes("UTF-8"), "application/json", null);
        if (!r.ok()) return null;
        JSONArray data = new JSONObject(r.body).optJSONArray("data");
        if (data == null || data.length() == 0) return null;
        return String.valueOf(data.getJSONObject(0).optLong("id"));
    }

    /** Everything that account owns. No key needed. */
    public static List<Game> games(String userId) throws Exception {
        Result r = call("https://games.roblox.com/v2/users/" + userId + "/games?limit=50", "GET",
                null, null, null);
        List<Game> out = new ArrayList<>();
        if (!r.ok()) return out;
        JSONArray data = new JSONObject(r.body).optJSONArray("data");
        for (int i = 0; data != null && i < data.length(); i++) {
            JSONObject g = data.getJSONObject(i);
            long id = g.optLong("id");
            if (id != 0) out.add(new Game(String.valueOf(id), g.optString("name", "(unnamed)")));
        }
        return out;
    }

    private static JSONObject universeInfo(String universeId) throws Exception {
        Result r = call("https://games.roblox.com/v1/games?universeIds=" + universeId, "GET", null, null, null);
        if (!r.ok()) return null;
        JSONArray data = new JSONObject(r.body).optJSONArray("data");
        return (data == null || data.length() == 0) ? null : data.getJSONObject(0);
    }

    /** The place players actually start in. Publishing anywhere else changes nothing they see. */
    public static String rootPlace(String universeId) throws Exception {
        JSONObject g = universeInfo(universeId);
        if (g == null) return null;
        long root = g.optLong("rootPlaceId");
        return root == 0 ? null : String.valueOf(root);
    }

    /** When Roblox last recorded a change. The only honest way to tell a publish landed. */
    public static String updatedAt(String universeId) throws Exception {
        JSONObject g = universeInfo(universeId);
        return g == null ? null : g.optString("updated", null);
    }

    public static Result publish(String apiKey, String universeId, String placeId, byte[] place,
                                 boolean xml, boolean live) throws Exception {
        String url = "https://apis.roblox.com/universes/v1/" + universeId + "/places/" + placeId
                + "/versions?versionType=" + (live ? "Published" : "Saved");
        return call(url, "POST", place, xml ? "application/xml" : "application/octet-stream", apiKey);
    }

    /** What to actually do about each failure, rather than the bare status. */
    public static String explain(Result r) {
        switch (r.code) {
            case 401: return "Roblox rejected the key. Check it was copied whole and has not expired.";
            case 403: return "The key is not allowed to publish here. On the key: add the API system "
                    + "universe-places, turn its toggle on, add this experience, and tick write. "
                    + "An IP restriction also causes this — a phone's address changes, so leave it off.";
            case 404: return "No such experience or place for this key.";
            case 400: return "Roblox did not accept the file. Is it a place saved by Studio?";
            case 429: return "Too many publishes just now. Wait a little and try again.";
            default:  return "Roblox answered " + r.code + ": "
                    + r.body.substring(0, Math.min(300, r.body.length()));
        }
    }
}
