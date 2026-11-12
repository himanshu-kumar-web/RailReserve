package com.railreserve.ui;

import android.app.Activity;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.widget.Button;
import androidx.core.content.ContextCompat;
import com.railreserve.R;

final class AppNavigation {
    private AppNavigation() { }

    static void bind(Activity activity, String selectedSection) {
        bindItem(activity, R.id.navHome, "home", selectedSection);
        bindItem(activity, R.id.navTickets, AppSectionActivity.SECTION_TICKETS, selectedSection);
        bindItem(activity, R.id.navProfile, AppSectionActivity.SECTION_PROFILE, selectedSection);
        bindItem(activity, R.id.navAbout, AppSectionActivity.SECTION_ABOUT, selectedSection);
    }

    private static void bindItem(Activity activity, int id, String section, String selectedSection) {
        Button button = activity.findViewById(id);
        boolean selected = section.equals(selectedSection);
        button.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(activity,
                selected ? R.color.teal_soft : R.color.surface)));
        button.setTextColor(ContextCompat.getColor(activity,
                selected ? R.color.brand_dark : R.color.muted));
        button.setSelected(selected);
        button.setOnClickListener(view -> { if (!selected) navigate(activity, section); });
    }

    static void onResume(Activity activity) { activity.getWindow().getDecorView().setTag(R.id.navHome, null); }

    // Top-level sections switch without spatial motion; child screens retain normal navigation.
    static void navigate(Activity activity, String section) {
        if (Boolean.TRUE.equals(activity.getWindow().getDecorView().getTag(R.id.navHome))) return;
        if ("home".equals(section)) {
            if (activity instanceof AppSectionActivity) {
                activity.getWindow().getDecorView().setTag(R.id.navHome, true);
                Intent intent = new Intent(activity, LiveSearchActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NO_ANIMATION);
                activity.startActivity(intent);
                activity.finish();
                activity.overridePendingTransition(0, 0);
            }
            return;
        }
        if (activity instanceof AppSectionActivity) {
            ((AppSectionActivity) activity).showSection(section);
            return;
        }
        Intent intent = new Intent(activity, AppSectionActivity.class);
        activity.getWindow().getDecorView().setTag(R.id.navHome, true);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NO_ANIMATION);
        intent.putExtra(AppSectionActivity.EXTRA_SECTION, section);
        activity.startActivity(intent);
        activity.overridePendingTransition(0, 0);
    }
}
