package org.familypolicy.android;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.Spinner;
import android.widget.TextView;

final class JellyfinTheme {
    static final int BACKGROUND = Color.rgb(13, 14, 20);
    static final int SURFACE = Color.rgb(27, 29, 39);
    static final int SURFACE_ELEVATED = Color.rgb(35, 37, 49);
    static final int SURFACE_MUTED = Color.rgb(45, 47, 59);
    static final int BORDER = Color.rgb(61, 64, 80);
    static final int TEXT_PRIMARY = Color.rgb(246, 247, 251);
    static final int TEXT_SECONDARY = Color.rgb(174, 180, 199);
    static final int TEXT_MUTED = Color.rgb(126, 132, 151);
    static final int CYAN = Color.rgb(0, 164, 220);
    static final int BLUE = Color.rgb(67, 130, 238);
    static final int PURPLE = Color.rgb(170, 92, 195);
    static final int MAGENTA = Color.rgb(204, 80, 162);
    static final int SUCCESS = Color.rgb(70, 201, 126);
    static final int WARNING = Color.rgb(244, 174, 73);
    static final int ERROR = Color.rgb(242, 104, 116);

    private JellyfinTheme() {
    }

    static void applyWindow(Activity activity) {
        activity.getWindow().setStatusBarColor(BACKGROUND);
        activity.getWindow().setNavigationBarColor(BACKGROUND);
        activity.getWindow().getDecorView().setBackgroundColor(BACKGROUND);
    }

    static Drawable hero(Context context) {
        GradientDrawable drawable = new GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            new int[]{Color.rgb(0, 116, 173), PURPLE}
        );
        drawable.setCornerRadius(dp(context, 24));
        return drawable;
    }

    static Drawable card(Context context) {
        return rounded(context, SURFACE, BORDER, 1, 18);
    }

    static Drawable elevatedCard(Context context) {
        return rounded(context, SURFACE_ELEVATED, BORDER, 1, 18);
    }

    static Drawable mediaCard(Context context, String state) {
        int stroke = BORDER;
        if (PolicyJson.ALLOW.equals(state)) {
            stroke = Color.rgb(45, 126, 86);
        } else if (PolicyJson.DENY.equals(state)) {
            stroke = Color.rgb(139, 56, 70);
        }
        return rounded(context, SURFACE, stroke, 1, 18);
    }

    static Drawable input(Context context) {
        return rounded(context, SURFACE_ELEVATED, BORDER, 1, 14);
    }

    static Drawable statusPill(Context context, boolean healthy) {
        int fill = healthy ? Color.rgb(25, 73, 54) : Color.rgb(78, 52, 26);
        int stroke = healthy ? SUCCESS : WARNING;
        return rounded(context, fill, stroke, 1, 100);
    }

    static Drawable mediaBadge(Context context, String detail) {
        int[] colors;
        if (detail.startsWith("Movie")) {
            colors = new int[]{CYAN, BLUE};
        } else if (detail.startsWith("Series")) {
            colors = new int[]{PURPLE, MAGENTA};
        } else if (detail.startsWith("Episode")) {
            colors = new int[]{BLUE, PURPLE};
        } else if (detail.startsWith("Audio")) {
            colors = new int[]{Color.rgb(42, 181, 145), CYAN};
        } else {
            colors = new int[]{Color.rgb(82, 91, 120), PURPLE};
        }
        GradientDrawable drawable = new GradientDrawable(GradientDrawable.Orientation.TL_BR, colors);
        drawable.setCornerRadius(dp(context, 12));
        return drawable;
    }

    static Drawable divider(Context context) {
        return rounded(context, BORDER, BORDER, 0, 1);
    }

    static void styleInput(Context context, EditText input) {
        input.setBackground(JellyfinTheme.input(context));
        input.setTextColor(TEXT_PRIMARY);
        input.setHintTextColor(TEXT_MUTED);
        input.setTextSize(15);
        input.setPadding(dp(context, 14), dp(context, 11), dp(context, 14), dp(context, 11));
        input.setMinHeight(dp(context, 50));
        input.setSelectAllOnFocus(false);
    }

    static void styleSpinner(Context context, Spinner spinner) {
        spinner.setBackgroundResource(R.drawable.fp_spinner_background);
        spinner.setPopupBackgroundDrawable(rounded(context, SURFACE_ELEVATED, BORDER, 1, 12));
        spinner.setPadding(dp(context, 2), 0, dp(context, 2), 0);
        spinner.setMinimumHeight(dp(context, 50));
    }

    static void stylePrimaryButton(Context context, Button button) {
        styleButton(context, button, primaryButton(context), TEXT_PRIMARY, 48);
    }

    static void styleSecondaryButton(Context context, Button button) {
        styleButton(context, button, secondaryButton(context), TEXT_PRIMARY, 44);
    }

    static void styleGhostButton(Context context, Button button) {
        styleButton(context, button, ghostButton(context), TEXT_SECONDARY, 42);
    }

    static void styleDangerButton(Context context, Button button) {
        styleButton(context, button, dangerButton(context), ERROR, 44);
    }

    static void styleSegment(Context context, RadioButton button, int checkedColor) {
        button.setButtonDrawable(null);
        button.setGravity(Gravity.CENTER);
        button.setTextSize(13);
        button.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        button.setTextColor(new ColorStateList(
            new int[][]{
                new int[]{android.R.attr.state_checked},
                new int[]{-android.R.attr.state_enabled},
                new int[]{}
            },
            new int[]{TEXT_PRIMARY, TEXT_MUTED, TEXT_SECONDARY}
        ));
        StateListDrawable background = new StateListDrawable();
        background.addState(
            new int[]{android.R.attr.state_checked},
            rounded(context, checkedColor, checkedColor, 0, 10)
        );
        background.addState(
            new int[]{android.R.attr.state_pressed},
            rounded(context, SURFACE_MUTED, BORDER, 1, 10)
        );
        background.addState(new int[]{}, rounded(context, Color.TRANSPARENT, Color.TRANSPARENT, 0, 10));
        button.setBackground(background);
        button.setMinHeight(dp(context, 38));
        button.setPadding(dp(context, 6), 0, dp(context, 6), 0);
    }

    static void styleHeading(TextView text) {
        text.setTextColor(TEXT_PRIMARY);
        text.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
    }

    static void styleSectionCaption(TextView text) {
        text.setTextColor(CYAN);
        text.setTextSize(12);
        text.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        text.setAllCaps(true);
        text.setLetterSpacing(0.08f);
    }

    static int statusColor(String message) {
        String lower = message == null ? "" : message.toLowerCase();
        if (lower.contains("failed")
            || lower.contains("error")
            || lower.contains("required")
            || lower.contains("not found")
            || lower.contains("no longer valid")
            || lower.contains("could not")
            || lower.contains("guard not healthy")
            || lower.contains("guard is not healthy")
            || lower.contains("conflict")) {
            return ERROR;
        }
        if (lower.contains("applied")
            || lower.contains("verified")
            || lower.contains("up to date")
            || lower.contains("healthy")) {
            return SUCCESS;
        }
        return TEXT_SECONDARY;
    }

    static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private static void styleButton(
        Context context,
        Button button,
        Drawable background,
        int enabledTextColor,
        int height
    ) {
        button.setAllCaps(false);
        button.setGravity(Gravity.CENTER);
        button.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        button.setTextSize(14);
        button.setTextColor(new ColorStateList(
            new int[][]{
                new int[]{-android.R.attr.state_enabled},
                new int[]{}
            },
            new int[]{TEXT_MUTED, enabledTextColor}
        ));
        button.setBackground(background);
        button.setMinHeight(dp(context, height));
        button.setMinWidth(0);
        button.setPadding(dp(context, 12), 0, dp(context, 12), 0);
        button.setStateListAnimator(null);
    }

    private static Drawable primaryButton(Context context) {
        GradientDrawable content = new GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            new int[]{CYAN, PURPLE}
        );
        content.setCornerRadius(dp(context, 14));
        return enabledState(context, ripple(context, content), rounded(context, SURFACE_MUTED, BORDER, 0, 14));
    }

    private static Drawable secondaryButton(Context context) {
        return enabledState(
            context,
            ripple(context, rounded(context, SURFACE_ELEVATED, BORDER, 1, 12)),
            rounded(context, SURFACE_MUTED, BORDER, 1, 12)
        );
    }

    private static Drawable ghostButton(Context context) {
        return enabledState(
            context,
            ripple(context, rounded(context, Color.TRANSPARENT, BORDER, 1, 12)),
            rounded(context, Color.TRANSPARENT, SURFACE_MUTED, 1, 12)
        );
    }

    private static Drawable dangerButton(Context context) {
        return enabledState(
            context,
            ripple(context, rounded(context, Color.rgb(70, 31, 42), ERROR, 1, 12)),
            rounded(context, SURFACE_MUTED, BORDER, 1, 12)
        );
    }

    private static Drawable enabledState(Context context, Drawable enabled, Drawable disabled) {
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{-android.R.attr.state_enabled}, disabled);
        states.addState(new int[]{}, enabled);
        return states;
    }

    private static Drawable ripple(Context context, Drawable content) {
        GradientDrawable mask = rounded(context, Color.WHITE, Color.TRANSPARENT, 0, 14);
        return new RippleDrawable(ColorStateList.valueOf(0x35FFFFFF), content, mask);
    }

    private static GradientDrawable rounded(
        Context context,
        int fill,
        int stroke,
        int strokeWidth,
        int radius
    ) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(context, radius));
        if (strokeWidth > 0) {
            drawable.setStroke(dp(context, strokeWidth), stroke);
        }
        return drawable;
    }
}
