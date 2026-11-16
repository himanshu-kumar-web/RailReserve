package com.railreserve.ui;

import androidx.appcompat.app.AppCompatActivity;
import com.railreserve.R;

public final class BackNavigation {
    private BackNavigation() { }

    public static void bind(AppCompatActivity activity) {
        activity.findViewById(R.id.screenBackButton).setOnClickListener(
                view -> activity.getOnBackPressedDispatcher().onBackPressed());
    }
}
