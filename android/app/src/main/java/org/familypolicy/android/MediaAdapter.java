package org.familypolicy.android;

import android.content.Context;
import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

final class MediaAdapter extends BaseAdapter {
    interface StateAccess {
        String stateFor(String itemId);

        void onStateChanged(String itemId, String state);
    }

    private final Context context;
    private final StateAccess stateAccess;
    private final List<MediaEntry> items = new ArrayList<>();

    MediaAdapter(Context context, StateAccess stateAccess) {
        this.context = context;
        this.stateAccess = stateAccess;
    }

    void setItems(List<MediaEntry> next) {
        items.clear();
        items.addAll(next);
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return items.size();
    }

    @Override
    public MediaEntry getItem(int position) {
        return items.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        RowHolder holder;
        if (convertView == null) {
            holder = createRow();
            convertView = holder.root;
            convertView.setTag(holder);
        } else {
            holder = (RowHolder) convertView.getTag();
        }
        MediaEntry item = getItem(position);
        holder.title.setText(item.title);
        holder.detail.setText(item.detail);
        holder.group.setOnCheckedChangeListener(null);
        String state = stateAccess.stateFor(item.id);
        if (PolicyJson.ALLOW.equals(state)) {
            holder.allow.setChecked(true);
        } else if (PolicyJson.DENY.equals(state)) {
            holder.deny.setChecked(true);
        } else {
            holder.inherit.setChecked(true);
        }
        holder.group.setContentDescription("Access for " + item.title);
        holder.group.setOnCheckedChangeListener((group, checkedId) -> {
            String next = checkedId == holder.allow.getId()
                ? PolicyJson.ALLOW
                : checkedId == holder.deny.getId() ? PolicyJson.DENY : PolicyJson.INHERIT;
            stateAccess.onStateChanged(item.id, next);
        });
        return convertView;
    }

    private RowHolder createRow() {
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(10), dp(16), dp(10));
        root.setLayoutParams(new AbsListView.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        TextView title = new TextView(context);
        title.setTextSize(17);
        title.setTextColor(Color.rgb(25, 35, 45));
        root.addView(title, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        TextView detail = new TextView(context);
        detail.setTextSize(13);
        detail.setTextColor(Color.rgb(90, 100, 110));
        root.addView(detail, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        RadioGroup group = new RadioGroup(context);
        group.setOrientation(LinearLayout.HORIZONTAL);
        RadioButton inherit = radio("Inherit");
        RadioButton allow = radio("Allow");
        RadioButton deny = radio("Deny");
        group.addView(inherit);
        group.addView(allow);
        group.addView(deny);
        root.addView(group, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        return new RowHolder(root, title, detail, group, inherit, allow, deny);
    }

    private RadioButton radio(String label) {
        RadioButton button = new RadioButton(context);
        button.setId(View.generateViewId());
        button.setText(label);
        button.setTextSize(14);
        button.setLayoutParams(new RadioGroup.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        return button;
    }

    private int dp(int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private static final class RowHolder {
        final LinearLayout root;
        final TextView title;
        final TextView detail;
        final RadioGroup group;
        final RadioButton inherit;
        final RadioButton allow;
        final RadioButton deny;

        RowHolder(
            LinearLayout root,
            TextView title,
            TextView detail,
            RadioGroup group,
            RadioButton inherit,
            RadioButton allow,
            RadioButton deny
        ) {
            this.root = root;
            this.title = title;
            this.detail = detail;
            this.group = group;
            this.inherit = inherit;
            this.allow = allow;
            this.deny = deny;
        }
    }
}
