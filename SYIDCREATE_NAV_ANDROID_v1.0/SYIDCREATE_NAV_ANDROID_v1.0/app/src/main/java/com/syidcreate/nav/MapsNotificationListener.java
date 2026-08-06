package com.syidcreate.nav;

import android.app.Notification;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public final class MapsNotificationListener extends NotificationListenerService {
    private static final Set<String> MAP_PACKAGES = new HashSet<>(Arrays.asList(
            "com.google.android.apps.maps",
            "com.google.android.apps.mapslite"
    ));

    private String lastFingerprint = "";
    private long lastSentAt;

    @Override
    public void onListenerConnected() {
        BleForegroundService.start(this);
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null || !MAP_PACKAGES.contains(sbn.getPackageName())) return;
        Notification notification = sbn.getNotification();
        if (notification == null) return;

        Bundle extras = notification.extras;
        String title = text(extras, Notification.EXTRA_TITLE);
        String body = text(extras, Notification.EXTRA_TEXT);
        String bigText = text(extras, Notification.EXTRA_BIG_TEXT);
        String subText = text(extras, Notification.EXTRA_SUB_TEXT);
        if (bigText.isEmpty()) bigText = lines(extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES));

        String raw = title + "|" + body + "|" + bigText + "|" + subText;
        if (raw.replace("|", "").trim().isEmpty()) return;

        long now = System.currentTimeMillis();
        String fingerprint = Integer.toHexString(raw.hashCode());
        if (fingerprint.equals(lastFingerprint) && now - lastSentAt < 2500L) return;
        lastFingerprint = fingerprint;
        lastSentAt = now;

        NavParser.NavInstruction instruction = NavParser.parse(title, body, bigText, subText);
        SyidCreateApp.ble(this).sendNavigation(instruction);
        BleForegroundService.reportNavigation(this, instruction.toString());
    }

    private static String text(Bundle extras, String key) {
        CharSequence value = extras.getCharSequence(key);
        return value == null ? "" : value.toString().trim();
    }

    private static String lines(CharSequence[] values) {
        if (values == null) return "";
        StringBuilder builder = new StringBuilder();
        for (CharSequence value : values) {
            if (value == null) continue;
            if (builder.length() > 0) builder.append(' ');
            builder.append(value);
        }
        return builder.toString();
    }
}
