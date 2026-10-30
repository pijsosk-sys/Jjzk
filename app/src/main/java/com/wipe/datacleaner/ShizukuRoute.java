package com.wipe.datacleaner;

import android.content.Context;
import android.content.pm.PackageManager;

import java.lang.reflect.Method;

import rikka.shizuku.Shizuku;

/**
 * Runs commands through Shizuku, which executes them as the shell user (uid 2000).
 *
 * That matters because shell is the one non-root identity that can still read and write
 * /storage/emulated/0/Android/data on Android 11 and newer - it is the same access an
 * "adb shell" session has. Without root this is the only on-device route left.
 */
public final class ShizukuRoute {

    public static final String SHIZUKU_PACKAGE = "moe.shizuku.privileged.api";
    public static final int PERMISSION_REQUEST_CODE = 7341;

    public enum State {
        NOT_INSTALLED("Shizuku is not installed"),
        NOT_RUNNING("Shizuku is installed but not running"),
        TOO_OLD("the installed Shizuku is too old (needs v11 or newer)"),
        NO_PERMISSION("Shizuku has not granted this app permission"),
        READY("ready");

        public final String text;
        State(String text) { this.text = text; }
    }

    private ShizukuRoute() { }

    public static boolean installed(Context ctx) {
        try {
            ctx.getPackageManager().getPackageInfo(SHIZUKU_PACKAGE, 0);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static State state(Context ctx) {
        boolean alive;
        try {
            alive = Shizuku.pingBinder();
        } catch (Throwable t) {
            alive = false;
        }
        if (!alive) return installed(ctx) ? State.NOT_RUNNING : State.NOT_INSTALLED;

        try {
            if (Shizuku.isPreV11()) return State.TOO_OLD;
        } catch (Throwable ignored) {
            // Older API surface; carry on and let the permission check decide.
        }
        try {
            return Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
                    ? State.READY : State.NO_PERMISSION;
        } catch (Throwable t) {
            return State.NO_PERMISSION;
        }
    }

    public static boolean ready(Context ctx) {
        return state(ctx) == State.READY;
    }

    public static void requestPermission() {
        Shizuku.requestPermission(PERMISSION_REQUEST_CODE);
    }

    public static int version() {
        try {
            return Shizuku.getVersion();
        } catch (Throwable t) {
            return -1;
        }
    }

    /** What the user has to do to unlock this route, in their own terms. */
    public static String howToFix(State s) {
        switch (s) {
            case NOT_INSTALLED:
                return "Install Shizuku from the Play Store or GitHub (RikkaApps/Shizuku).";
            case NOT_RUNNING:
                return "Open Shizuku and start the service. Without root that means pairing "
                        + "it once over Wireless debugging in Developer options; it has to be "
                        + "started again after every reboot.";
            case TOO_OLD:
                return "Update Shizuku to version 11 or newer.";
            case NO_PERMISSION:
                return "Press the Shizuku button here and allow this app in the Shizuku prompt.";
            default:
                return "";
        }
    }

    /**
     * Runs a shell command through Shizuku.
     *
     * Shizuku.newProcess is not part of the public API surface of the 13.x library, so it is
     * reached by reflection against the pinned jar in app/libs. If that ever stops working
     * the failure is reported like any other, rather than silently skipping the route.
     */
    public static Shell.Result exec(String command, int timeoutMs) {
        Shell.Result res = new Shell.Result();
        try {
            Method m = Shizuku.class.getDeclaredMethod(
                    "newProcess", String[].class, String[].class, String.class);
            m.setAccessible(true);
            Object proc = m.invoke(null,
                    (Object) new String[]{"sh", "-c", command}, null, null);
            if (!(proc instanceof Process)) {
                res.launchError = "Shizuku returned " + (proc == null ? "null" : proc.getClass());
                return res;
            }
            return Shell.consume((Process) proc, timeoutMs);
        } catch (Throwable t) {
            Throwable cause = t.getCause() != null ? t.getCause() : t;
            res.launchError = cause.getClass().getSimpleName() + ": " + cause.getMessage();
            return res;
        }
    }
}
