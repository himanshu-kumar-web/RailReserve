package com.railreserve.ui;

import android.graphics.Typeface;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import com.railreserve.model.FareSnapshot;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Display formatting only. Original quotes, statuses and request state are never changed. */
public final class UiText {
    private static final Pattern CHECKED = Pattern.compile("\\| Checked: (\\S+)");
    private UiText() { }

    public static String checkedTime(String timestamp) {
        try {
            return DateTimeFormatter.ofPattern("d MMM, HH:mm:ss z", Locale.getDefault())
                    .withZone(ZoneId.systemDefault()).format(Instant.parse(timestamp));
        } catch (RuntimeException invalid) { return timestamp; }
    }

    static CharSequence enquiry(String original) {
        if (original == null) return "";
        Matcher matcher = CHECKED.matcher(original);
        StringBuffer compact = new StringBuffer();
        while (matcher.find()) matcher.appendReplacement(compact, Matcher.quoteReplacement("· Checked " + checkedTime(matcher.group(1))));
        matcher.appendTail(compact);
        SpannableString text = new SpannableString(compact.toString());
        int end = compact.indexOf("\n");
        if (end < 0) end = text.length();
        if (end > 0) {
            text.setSpan(new StyleSpan(Typeface.BOLD),0,end,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            text.setSpan(new RelativeSizeSpan(1.12f),0,end,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return text;
    }

    static String quote(FareSnapshot quote) {
        return "Captured estimate: " + PaymentReceiptText.amount(quote.getAmountPaise()) + " · " + quote.getClassCode() + "/" + quote.getQuota()
                + "\n" + quote.getSource() + " · Checked " + checkedTime(quote.getRetrievedAt())
                + (quote.getAvailability() == null ? "" : "\nAvailability snapshot: " + quote.getAvailability() + " (" + quote.getStatusType() + ")");
    }
}
