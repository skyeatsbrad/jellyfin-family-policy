package org.familypolicy.android;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import java.util.List;

final class DarkSpinnerAdapter<T> extends ArrayAdapter<T> {
    DarkSpinnerAdapter(Context context, T[] values) {
        super(context, android.R.layout.simple_spinner_item, values);
    }

    DarkSpinnerAdapter(Context context, List<T> values) {
        super(context, android.R.layout.simple_spinner_item, values);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        return bind(position, convertView, false);
    }

    @Override
    public View getDropDownView(int position, View convertView, ViewGroup parent) {
        return bind(position, convertView, true);
    }

    private View bind(int position, View convertView, boolean dropdown) {
        TextView text = convertView instanceof TextView ? (TextView) convertView : new TextView(getContext());
        T value = getItem(position);
        text.setText(value == null ? "" : value.toString());
        text.setTextColor(JellyfinTheme.TEXT_PRIMARY);
        text.setTextSize(15);
        text.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        text.setGravity(Gravity.CENTER_VERTICAL);
        text.setSingleLine(true);
        text.setEllipsize(TextUtils.TruncateAt.END);
        text.setMinHeight(JellyfinTheme.dp(getContext(), dropdown ? 50 : 48));
        text.setPadding(
            JellyfinTheme.dp(getContext(), 14),
            0,
            JellyfinTheme.dp(getContext(), dropdown ? 14 : 42),
            0
        );
        text.setBackgroundColor(dropdown ? JellyfinTheme.SURFACE_ELEVATED : Color.TRANSPARENT);
        return text;
    }
}
