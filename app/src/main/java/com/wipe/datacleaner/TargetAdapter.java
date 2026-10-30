package com.wipe.datacleaner;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.TextView;

import java.util.List;

public class TargetAdapter extends BaseAdapter {

    private final Context ctx;
    private final List<Target> items;
    private final Runnable onSelectionChanged;

    public TargetAdapter(Context ctx, List<Target> items, Runnable onSelectionChanged) {
        this.ctx = ctx;
        this.items = items;
        this.onSelectionChanged = onSelectionChanged;
    }

    @Override public int getCount() { return items.size(); }
    @Override public Object getItem(int position) { return items.get(position); }
    @Override public long getItemId(int position) { return position; }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View v = convertView;
        if (v == null) {
            v = LayoutInflater.from(ctx).inflate(R.layout.row_target, parent, false);
        }

        final Target t = items.get(position);
        TextView name = v.findViewById(R.id.tvName);
        TextView sub = v.findViewById(R.id.tvSub);
        TextView meta = v.findViewById(R.id.tvMeta);
        CheckBox cb = v.findViewById(R.id.cbSelect);

        name.setText(t.packageName);
        sub.setText(t.appLabel != null ? t.appLabel : "(app not installed)");
        meta.setText(t.sizeText() + "  ·  " + t.sourceName());

        cb.setOnCheckedChangeListener(null);
        cb.setChecked(t.selected);
        cb.setOnCheckedChangeListener((button, checked) -> {
            t.selected = checked;
            if (onSelectionChanged != null) onSelectionChanged.run();
        });
        return v;
    }
}
