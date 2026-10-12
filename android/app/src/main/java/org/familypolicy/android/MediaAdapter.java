package org.familypolicy.android;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.view.Gravity;
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
        holder.detail.setText(item.detail.replace(" - ", "  |  "));
        holder.badge.setText(badgeText(item.detail));
        holder.badge.setBackground(JellyfinTheme.mediaBadge(context, item.detail));
        holder.group.setOnCheckedChangeListener(null);
        String state = stateAccess.stateFor(item.id);
        holder.card.setBackground(JellyfinTheme.mediaCard(context, state));
        if (PolicyJson.ALLOW.equals(state)) {
            holder.group.check(holder.allow.getId());
        } else if (PolicyJson.DENY.equals(state)) {
            holder.group.check(holder.deny.getId());
        } else {
            holder.group.check(holder.inherit.getId());
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
        root.setPadding(dp(12), dp(6), dp(12), dp(6));
        root.setLayoutParams(new AbsListView.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(JellyfinTheme.card(context));
        card.setPadding(dp(14), dp(14), dp(14), dp(13));
        card.setElevation(dp(2));
        root.addView(card, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        card.addView(header, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        TextView badge = new TextView(context);
        badge.setGravity(Gravity.CENTER);
        badge.setTextColor(Color.WHITE);
        badge.setTextSize(10);
        badge.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        badge.setLetterSpacing(0.04f);
        LinearLayout.LayoutParams badgeParams = new LinearLayout.LayoutParams(dp(48), dp(48));
        badgeParams.rightMargin = dp(12);
        header.addView(badge, badgeParams);

        LinearLayout metadata = new LinearLayout(context);
        metadata.setOrientation(LinearLayout.VERTICAL);
        metadata.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(metadata, new LinearLayout.LayoutParams(
            0,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            1
        ));

        TextView title = new TextView(context);
        title.setTextSize(16);
        title.setTextColor(JellyfinTheme.TEXT_PRIMARY);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        title.setMaxLines(2);
        title.setEllipsize(TextUtils.TruncateAt.END);
        metadata.addView(title, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        TextView detail = new TextView(context);
        detail.setTextSize(13);
        detail.setTextColor(JellyfinTheme.TEXT_SECONDARY);
        detail.setPadding(0, dp(3), 0, 0);
        metadata.addView(detail, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        RadioGroup group = new RadioGroup(context);
        group.setOrientation(LinearLayout.HORIZONTAL);
        group.setGravity(Gravity.CENTER_VERTICAL);
        group.setBackground(JellyfinTheme.input(context));
        group.setPadding(dp(4), dp(4), dp(4), dp(4));
        RadioButton inherit = radio("Inherit", Color.rgb(61, 82, 121));
        RadioButton allow = radio("Allow", Color.rgb(31, 112, 75));
        RadioButton deny = radio("Deny", Color.rgb(119, 45, 58));
        group.addView(inherit);
        group.addView(allow);
        group.addView(deny);
        LinearLayout.LayoutParams groupParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        groupParams.topMargin = dp(12);
        card.addView(group, groupParams);
        return new RowHolder(root, card, badge, title, detail, group, inherit, allow, deny);
    }

    private RadioButton radio(String label, int checkedColor) {
        RadioButton button = new RadioButton(context);
        button.setId(View.generateViewId());
        button.setText(label);
        JellyfinTheme.styleSegment(context, button, checkedColor);
        button.setLayoutParams(new RadioGroup.LayoutParams(0, dp(38), 1));
        return button;
    }

    private String badgeText(String detail) {
        if (detail.startsWith("Movie")) {
            return "MOVIE";
        }
        if (detail.startsWith("Series")) {
            return "SERIES";
        }
        if (detail.startsWith("Episode")) {
            return "EP";
        }
        if (detail.startsWith("Audio")) {
            return "AUDIO";
        }
        if (detail.startsWith("Video")) {
            return "VIDEO";
        }
        return "MEDIA";
    }

    private int dp(int value) {
        return JellyfinTheme.dp(context, value);
    }

    private static final class RowHolder {
        final LinearLayout root;
        final LinearLayout card;
        final TextView badge;
        final TextView title;
        final TextView detail;
        final RadioGroup group;
        final RadioButton inherit;
        final RadioButton allow;
        final RadioButton deny;

        RowHolder(
            LinearLayout root,
            LinearLayout card,
            TextView badge,
            TextView title,
            TextView detail,
            RadioGroup group,
            RadioButton inherit,
            RadioButton allow,
            RadioButton deny
        ) {
            this.root = root;
            this.card = card;
            this.badge = badge;
            this.title = title;
            this.detail = detail;
            this.group = group;
            this.inherit = inherit;
            this.allow = allow;
            this.deny = deny;
        }
    }
}
