package com.volkyz.relay;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * One log sink for the whole process. The Go side writes here from its own threads; the activity
 * attaches and detaches as it comes and goes, and reads the backlog when it returns.
 */
public final class LogBus implements bridge.Logger {

    public interface Listener { void onLine(String line); }

    private static final LogBus INSTANCE = new LogBus();
    private static final int KEEP = 400;

    private final Deque<String> lines = new ArrayDeque<>();
    private Listener listener;

    public static LogBus get() { return INSTANCE; }

    @Override public synchronized void log(String line) {
        for (String part : line.split("\n")) {
            if (part.trim().isEmpty()) continue;
            lines.addLast(part);
            while (lines.size() > KEEP) lines.removeFirst();
            if (listener != null) listener.onLine(part);
        }
    }

    public synchronized void attach(Listener l) { listener = l; }

    public synchronized void detach(Listener l) { if (listener == l) listener = null; }

    public synchronized List<String> backlog() { return new ArrayList<>(lines); }
}
