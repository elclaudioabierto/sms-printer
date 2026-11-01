package com.example.smsprinter;

import android.app.Notification;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import java.text.DateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

public final class NotificationReceiver extends NotificationListenerService {
    private final Map<String, String> recent = new LinkedHashMap<String, String>(100, .75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, String> entry) { return size() > 200; }
    };
    @Override public void onNotificationPosted(StatusBarNotification posted) {
        if (posted == null || getPackageName().equals(posted.getPackageName())) return;
        Notification notification = posted.getNotification();
        if (notification == null) return;
        Bundle extras = notification.extras;
        CharSequence title = extras == null ? null : extras.getCharSequence(Notification.EXTRA_TITLE);
        CharSequence content = extras == null ? null : extras.getCharSequence(Notification.EXTRA_BIG_TEXT);
        if (content == null || content.length() == 0) content = extras == null ? null : extras.getCharSequence(Notification.EXTRA_TEXT);
        String app = posted.getPackageName();
        try { PackageManager pm = getPackageManager(); app = pm.getApplicationLabel(pm.getApplicationInfo(app, 0)).toString(); } catch (PackageManager.NameNotFoundException ignored) { }
        android.content.SharedPreferences sources = getSharedPreferences("sources", MODE_PRIVATE);
        sources.edit().putString(posted.getPackageName(), app).apply();
        android.content.SharedPreferences filters = getSharedPreferences("filters", MODE_PRIVATE);
        if (filters.getBoolean("selected_only", false) && !filters.getBoolean("app:" + posted.getPackageName(), false)) return;
        String titleText = title == null ? "" : title.toString();
        String contentText = content == null ? "" : content.toString();
        String keywords = filters.getString("keywords", "");
        String searchable = (titleText + "\n" + contentText).toLowerCase(java.util.Locale.ROOT);
        boolean hasKeyword = false, matchesKeyword = false;
        for (String keyword : keywords.split("[,\n]")) {
            String trimmed = keyword.trim().toLowerCase(java.util.Locale.ROOT);
            if (!trimmed.isEmpty()) { hasKeyword = true; if (searchable.contains(trimmed)) matchesKeyword = true; }
        }
        if (hasKeyword && !matchesKeyword) return;
        String fingerprint = titleText + "\n" + contentText;
        synchronized (recent) { if (fingerprint.equals(recent.put(posted.getKey(), fingerprint))) return; }
        StringBuilder text = new StringBuilder(app).append("\n").append(DateFormat.getDateTimeInstance().format(new Date(posted.getPostTime())));
        if (!titleText.isEmpty()) text.append("\n\n").append(titleText);
        if (!contentText.isEmpty() && !contentText.equals(titleText)) text.append("\n").append(contentText);
        if (titleText.isEmpty() && contentText.isEmpty()) text.append("\n\n(Notification has no readable text)");
        Context appContext = getApplicationContext();
        Work.executor.execute(() -> { try (Queue q = new Queue(appContext)) { q.add(text.toString()); q.drain(appContext); } });
    }
    @Override public void onNotificationRemoved(StatusBarNotification posted) { if (posted != null) synchronized (recent) { recent.remove(posted.getKey()); } }
}
