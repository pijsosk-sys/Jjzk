package com.wipe.datacleaner;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;

/** Minimal shell runner used for the optional root strategy. */
public final class Shell {

    public static class Result {
        public int exitCode = -1;
        public String stdout = "";
        public String stderr = "";
        public boolean timedOut;
        public String launchError;

        public boolean ok() { return exitCode == 0 && !timedOut && launchError == null; }

        public String describe() {
            StringBuilder sb = new StringBuilder();
            if (launchError != null) sb.append("could not start shell: ").append(launchError);
            else if (timedOut) sb.append("shell timed out (no response from su)");
            else sb.append("exit code ").append(exitCode);
            if (!stderr.trim().isEmpty()) sb.append("\nstderr: ").append(stderr.trim());
            if (!stdout.trim().isEmpty()) sb.append("\nstdout: ").append(stdout.trim());
            return sb.toString();
        }
    }

    private Shell() { }

    /** True when a su binary exists on PATH and actually grants a shell. */
    public static boolean hasRoot() {
        Result r = runSu("id", 8000);
        return r.ok() && r.stdout.contains("uid=0");
    }

    public static Result runSu(String command, int timeoutMs) {
        return run(new String[]{"su", "-c", command}, timeoutMs);
    }

    public static Result run(String[] argv, int timeoutMs) {
        Process proc;
        try {
            proc = new ProcessBuilder(argv).start();
        } catch (Exception e) {
            Result res = new Result();
            res.launchError = e.getClass().getSimpleName() + ": " + e.getMessage();
            return res;
        }
        return consume(proc, timeoutMs);
    }

    /** Drains a running process and waits for it, whoever started it. */
    public static Result consume(Process proc, int timeoutMs) {
        Result res = new Result();

        StreamReader out = new StreamReader(proc.getInputStream());
        StreamReader err = new StreamReader(proc.getErrorStream());
        out.start();
        err.start();

        // Close stdin so a shell waiting for input cannot hang us.
        try { new OutputStreamWriter(proc.getOutputStream()).close(); } catch (Exception ignored) { }

        long deadline = System.currentTimeMillis() + timeoutMs;
        while (true) {
            try {
                res.exitCode = proc.exitValue();
                break;
            } catch (IllegalThreadStateException stillRunning) {
                if (System.currentTimeMillis() > deadline) {
                    res.timedOut = true;
                    proc.destroy();
                    break;
                }
                try { Thread.sleep(40); } catch (InterruptedException ignored) { }
            }
        }

        try { out.join(1500); } catch (InterruptedException ignored) { }
        try { err.join(1500); } catch (InterruptedException ignored) { }
        res.stdout = out.text();
        res.stderr = err.text();
        return res;
    }

    private static class StreamReader extends Thread {
        private final InputStream in;
        private final StringBuilder sb = new StringBuilder();

        StreamReader(InputStream in) { this.in = in; setDaemon(true); }

        @Override public void run() {
            BufferedReader r = new BufferedReader(new InputStreamReader(in));
            try {
                String line;
                while ((line = r.readLine()) != null) sb.append(line).append('\n');
            } catch (Exception ignored) {
            } finally {
                try { r.close(); } catch (Exception ignored) { }
            }
        }

        synchronized String text() { return sb.toString(); }
    }
}
