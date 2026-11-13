package com.railreserve.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Build;
import android.view.View;
import android.widget.Toast;
import com.railreserve.R;

/** Clipboard presentation shared by local ticket cards, lookup and confirmation. */
public final class TicketUi {
    private TicketUi() { }
    public static void bindCopy(View root, String pnr) {
        root.findViewById(R.id.copyPnrButton).setOnClickListener(view -> {
            ClipboardManager clipboard = (ClipboardManager) view.getContext().getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard == null) return;
            clipboard.setPrimaryClip(ClipData.newPlainText(view.getContext().getString(R.string.copy_demo_pnr),pnr));
            // Android 13+ already provides its own clipboard confirmation.
            if (Build.VERSION.SDK_INT < 33) Toast.makeText(view.getContext(),R.string.pnr_copied,Toast.LENGTH_SHORT).show();
        });
    }
}
