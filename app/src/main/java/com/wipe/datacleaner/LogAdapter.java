package com.wipe.datacleaner;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.util.List;

public class LogAdapter extends BaseAdapter {

    private static final int C_OK = Color.parseColor("#4CAF50");
    private static final int C_FAIL = Color.parseColor("#FF5252");
    private static final int C_WARN = Color.parseColor("#FFB300");
    private static final int C_INFO = Color.parseColor("#64B5F6");

    private final Context ctx;
    private final List<LogEntry> items;

    public LogAdapter(Context ctx, List<LogEntry> items) {
        this.ctx = ctx;
        this.items = items;
    }

    @Override public int getCount() { return items.size(); }
    @Override public LogEntry getItem(int position) { return items.get(position); }
    @Override public long getItemId(int position) { return position; }

    static int colorFor(int level) {
        switch (level) {
            case LogEntry.LEVEL_OK:   return C_OK;
            case LogEntry.LEVEL_FAIL: return C_FAIL;
            case LogEntry.LEVEL_WARN: return C_WARN;
            default:                  return C_INFO;
        }
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View v = convertView;
        if (v == null) {
            v = LayoutInflater.from(ctx).inflate(R.layout.row_log, parent, false);
        }
        LogEntry e = items.get(position);

        TextView tag = v.findViewById(R.id.tvTag);
        TextView title = v.findViewById(R.id.tvLogTitle);
        TextView summary = v.findViewById(R.id.tvLogSummary);
        TextView time = v.findViewById(R.id.tvLogTime);

        tag.setText(e.levelTag());
        tag.setTextColor(colorFor(e.level));
        title.setText(e.title);
        summary.setText(e.summary);
        summary.setVisibility(e.summary.isEmpty() ? View.GONE : View.VISIBLE);
        time.setText(e.timeString());
        return v;
    }
}
