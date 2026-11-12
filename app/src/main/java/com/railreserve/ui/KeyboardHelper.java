package com.railreserve.ui;

import android.app.Activity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;

final class KeyboardHelper {
    private KeyboardHelper() { }

    static void dismiss(Activity activity) {
        View focused = activity.getCurrentFocus();
        if (focused == null) return;
        InputMethodManager keyboard = activity.getSystemService(InputMethodManager.class);
        if (keyboard != null) keyboard.hideSoftInputFromWindow(focused.getWindowToken(), 0);
        focused.clearFocus();
    }
}
