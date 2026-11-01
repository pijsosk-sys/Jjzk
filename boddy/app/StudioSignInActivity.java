package com.boddy.os;

import android.app.Activity;
import android.os.Bundle;

/**
 * Receives Roblox Studio's sign-in link (roblox-studio-auth:...) from the phone's own browser and hands it to
 * the Studio running in Boddy's desktop. Signing in on the phone's browser instead of a Firefox inside Boddy
 * keeps Firefox's memory out of Boddy, which Android closed when Studio and Firefox together ran the phone out
 * of memory. Shows nothing: it opens the desktop with the link as the command to run (DesktopActivity sends it
 * to BoddyService, which runs /opt/boddy/run-in-desktop.sh with it; that passes it to /opt/boddy/studio).
 */
public class StudioSignInActivity extends Activity {
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        String link = getIntent() == null ? null : getIntent().getDataString();
        // Only the sign-in link, and only of a sane size: the launcher checks it again before using it.
        if (link != null && link.startsWith("roblox-studio-auth:") && link.length() <= 16384) {
            DesktopActivity.Companion.open(this, link);
        }
        finish();
    }
}
