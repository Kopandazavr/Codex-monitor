package dev.bennett.codexmeter;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

/** Runtime branding bridge while the legacy Java namespace/source identifiers remain in place. */
final class Branding {
    private static final String LEGACY_METER_NAME = "Codex Meter";
    private static final String LEGACY_WATCH_NAME = "Codex Watch";
    private static final String PRODUCT_NAME = "Codex Monitor";

    private Branding() {
    }

    static void apply(Activity activity) {
        if (activity == null) return;
        CharSequence title = activity.getTitle();
        if (title != null) {
            activity.setTitle(rebrand(title));
        }
        View root = activity.findViewById(android.R.id.content);
        replaceText(root);
    }

    private static void replaceText(View view) {
        if (view == null) return;
        if (view instanceof TextView) {
            TextView textView = (TextView) view;
            CharSequence current = textView.getText();
            CharSequence updated = rebrand(current);
            if (current != updated && !updated.equals(current)) {
                textView.setText(updated);
            }
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int index = 0; index < group.getChildCount(); index++) {
                replaceText(group.getChildAt(index));
            }
        }
    }

    static CharSequence rebrand(CharSequence value) {
        if (value == null) return "";
        String text = value.toString();
        if (!text.contains(LEGACY_METER_NAME) && !text.contains(LEGACY_WATCH_NAME)) return value;
        return text.replace(LEGACY_METER_NAME, PRODUCT_NAME)
                .replace(LEGACY_WATCH_NAME, PRODUCT_NAME);
    }
}
