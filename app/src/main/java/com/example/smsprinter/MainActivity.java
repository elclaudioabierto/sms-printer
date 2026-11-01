package com.example.smsprinter;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.hardware.usb.*;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import java.util.ArrayList;

public final class MainActivity extends Activity {
    private static final String USB_PERMISSION = "com.example.smsprinter.USB_PERMISSION";
    private static final int BLACK = Color.BLACK, WHITE = Color.WHITE;
    private UsbManager usb;
    private LinearLayout devices, appFilters;
    private TextView accessValue, printerValue, queueValue;
    private final BroadcastReceiver permissionReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (USB_PERMISSION.equals(intent.getAction())) {
                if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) { toast("PRINTER CONNECTED"); Work.drain(MainActivity.this); }
                else toast("USB PERMISSION NOT GRANTED");
                refresh();
            }
        }
    };
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BLACK);
        getWindow().setNavigationBarColor(BLACK);
        usb = (UsbManager) getSystemService(USB_SERVICE);
        IntentFilter filter = new IntentFilter(USB_PERMISSION);
        if (android.os.Build.VERSION.SDK_INT >= 33) registerReceiver(permissionReceiver, filter, Context.RECEIVER_NOT_EXPORTED); else registerReceiver(permissionReceiver, filter);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(WHITE);
        LinearLayout page = column();
        page.setPadding(dp(16), dp(16), dp(16), dp(32));
        scroll.addView(page);
        setContentView(scroll);

        LinearLayout header = column();
        header.setBackgroundColor(BLACK);
        header.setPadding(dp(18), dp(18), dp(18), dp(20));
        page.addView(header, matchWrap());
        TextView eyebrow = label("INCOMING / ON PAPER     ■", 11, WHITE, true);
        header.addView(eyebrow);
        TextView title = label("NOTIFICATION\nPRINTER", 32, WHITE, true);
        title.setTypeface(Typeface.create("sans-serif-black", Typeface.NORMAL));
        title.setLineSpacing(0, .90f);
        LinearLayout.LayoutParams titleParams = matchWrap(); titleParams.setMargins(0, dp(16), 0, dp(16));
        header.addView(title, titleParams);
        TextView sub = label("YOUR PHONE. YOUR PAPER.\nNO SCREEN REQUIRED.", 12, WHITE, false);
        sub.setTypeface(Typeface.MONOSPACE);
        header.addView(sub);

        section(page, "01", "SYSTEM STATUS");
        LinearLayout statusBox = column(); statusBox.setBackground(border(WHITE, BLACK, 2));
        accessValue = statusRow(statusBox, "NOTIFICATIONS");
        divider(statusBox);
        printerValue = statusRow(statusBox, "USB PRINTER");
        divider(statusBox);
        queueValue = statusRow(statusBox, "IN QUEUE");
        page.addView(statusBox, matchWrap());

        section(page, "02", "ACCESS");
        TextView accessHelp = body("Allow the app to read incoming notifications. Messages, verification codes and other private information can be printed.");
        page.addView(accessHelp, marginBottom(14));
        page.addView(action("ENABLE NOTIFICATIONS   ↗", true, () -> startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))), matchWrap());

        section(page, "03", "APP FILTER");
        TextView filterHelp = body("Choose which apps can print. Apps appear here after they send a notification.");
        page.addView(filterHelp, marginBottom(14));
        Switch selectedOnly = new Switch(this);
        selectedOnly.setText("PRINT SELECTED APPS ONLY");
        selectedOnly.setThumbTintList(android.content.res.ColorStateList.valueOf(BLACK));
        selectedOnly.setTrackTintList(android.content.res.ColorStateList.valueOf(0xffaaaaaa));
        selectedOnly.setTextColor(BLACK);
        selectedOnly.setTextSize(14);
        selectedOnly.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
        selectedOnly.setPadding(dp(12), dp(12), dp(12), dp(12));
        selectedOnly.setBackground(border(WHITE, BLACK, 2));
        selectedOnly.setChecked(getSharedPreferences("filters", 0).getBoolean("selected_only", false));
        selectedOnly.setOnCheckedChangeListener((button, checked) -> { getSharedPreferences("filters", 0).edit().putBoolean("selected_only", checked).apply(); refresh(); });
        page.addView(selectedOnly, matchWrap());
        appFilters = column();
        LinearLayout.LayoutParams appParams = matchWrap(); appParams.setMargins(0, dp(8), 0, 0);
        page.addView(appFilters, appParams);
        page.addView(action("REFRESH APP LIST   ↻", false, this::refresh), matchWrap());
        TextView filterNote = label("WHEN OFF, ALL APPS CAN PRINT.", 11, BLACK, false);
        filterNote.setTypeface(Typeface.MONOSPACE);
        page.addView(filterNote, marginBottom(4));

        section(page, "04", "KEYWORD FILTER");
        TextView keywordHelp = body("Print only notifications containing any of these words or phrases. Separate them with commas. Leave blank to print all text.");
        page.addView(keywordHelp, marginBottom(14));
        EditText keywordInput = new EditText(this);
        keywordInput.setTextColor(BLACK); keywordInput.setHintTextColor(0xff666666);
        keywordInput.setTextSize(15); keywordInput.setSingleLine(false); keywordInput.setMinLines(2);
        keywordInput.setPadding(dp(12), dp(12), dp(12), dp(12)); keywordInput.setBackground(border(WHITE, BLACK, 2));
        keywordInput.setHint("urgent, dinner, call me");
        keywordInput.setText(getSharedPreferences("filters", 0).getString("keywords", ""));
        keywordInput.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence value, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence value, int start, int before, int count) { getSharedPreferences("filters", 0).edit().putString("keywords", value.toString()).apply(); }
            @Override public void afterTextChanged(android.text.Editable value) { }
        });
        page.addView(keywordInput, matchWrap());

        section(page, "05", "PRINTER");
        TextView usbHelp = body("Connect a powered ESC/POS printer by USB. Select it below and approve the connection.");
        page.addView(usbHelp, marginBottom(14));
        page.addView(action("SCAN USB DEVICES   ↻", false, this::refresh), matchWrap());
        devices = column();
        LinearLayout.LayoutParams deviceParams = matchWrap(); deviceParams.setMargins(0, dp(8), 0, 0);
        page.addView(devices, deviceParams);

        section(page, "06", "OUTPUT");
        page.addView(action("PRINT TEST PAGE   →", true, () -> Work.executor.execute(() -> {
            boolean ok = Printer.print(getApplicationContext(), "NOTIFICATION PRINTER\n\nTEST PAGE\n\nIf you can read this, your printer is connected.\n");
            runOnUiThread(() -> { toast(ok ? "TEST SENT TO PRINTER" : "PRINT FAILED. CHECK USB CONNECTION."); refresh(); });
        })), matchWrap());
        LinearLayout.LayoutParams retryParams = matchWrap(); retryParams.setMargins(0, dp(8), 0, 0);
        page.addView(action("RETRY QUEUED ITEMS   →", false, () -> { Work.drain(this); toast("RETRY REQUESTED"); }), retryParams);
        TextView footer = label("KEEP PAPER LOADED.\nKEEP YOUR PRINTER CONNECTED.\n\nPRINTED NOTIFICATIONS MAY CONTAIN\nPRIVATE INFORMATION.", 11, BLACK, false);
        footer.setTypeface(Typeface.MONOSPACE); footer.setLineSpacing(dp(3), 1);
        LinearLayout.LayoutParams footerParams = matchWrap(); footerParams.setMargins(0, dp(28), 0, 0);
        page.addView(footer, footerParams);
        Work.schedule(this);
        refresh();
    }
    @Override protected void onResume() { super.onResume(); if (devices != null) refresh(); }
    @Override protected void onDestroy() { unregisterReceiver(permissionReceiver); super.onDestroy(); }
    private void refresh() {
        UsbDevice selected = Printer.selected(this);
        int count; try (Queue q = new Queue(this)) { count = q.count(); }
        String listeners = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        boolean enabled = false;
        if (listeners != null) for (String item : listeners.split(":")) { ComponentName name = ComponentName.unflattenFromString(item); if (name != null && name.equals(new ComponentName(this, NotificationReceiver.class))) enabled = true; }
        accessValue.setText(enabled ? "ON" : "OFF");
        printerValue.setText(selected == null ? "OFFLINE" : (usb.hasPermission(selected) ? "READY" : "APPROVE"));
        queueValue.setText(String.format(java.util.Locale.ROOT, "%02d", count));
        appFilters.removeAllViews();
        java.util.Map<String, ?> known = getSharedPreferences("sources", 0).getAll();
        java.util.ArrayList<String> packages = new java.util.ArrayList<>(known.keySet());
        java.util.Collections.sort(packages, (a, b) -> String.valueOf(known.get(a)).compareToIgnoreCase(String.valueOf(known.get(b))));
        if (packages.isEmpty()) {
            TextView empty = label("NO APPS SEEN YET. SEND A NOTIFICATION, THEN REOPEN THIS SCREEN.", 11, BLACK, false);
            empty.setTypeface(Typeface.MONOSPACE); empty.setPadding(dp(2), dp(8), 0, dp(8)); appFilters.addView(empty);
        }
        for (String pkg : packages) {
            CheckBox check = new CheckBox(this);
            check.setText(String.valueOf(known.get(pkg)) + "  /  " + pkg);
            check.setButtonTintList(android.content.res.ColorStateList.valueOf(BLACK));
            check.setTextColor(BLACK); check.setTextSize(13); check.setPadding(dp(8), dp(6), dp(8), dp(6));
            check.setChecked(getSharedPreferences("filters", 0).getBoolean("app:" + pkg, false));
            check.setOnCheckedChangeListener((button, checked) -> getSharedPreferences("filters", 0).edit().putBoolean("app:" + pkg, checked).apply());
            appFilters.addView(check, matchWrap());
        }
        devices.removeAllViews();
        ArrayList<UsbDevice> available = new ArrayList<>(usb.getDeviceList().values());
        if (available.isEmpty()) {
            TextView empty = label("NO USB DEVICES FOUND", 11, BLACK, false); empty.setTypeface(Typeface.MONOSPACE); empty.setPadding(dp(2), dp(8), 0, dp(4)); devices.addView(empty);
        }
        for (UsbDevice device : available) {
            String name = device.getProductName();
            String description = (name == null ? "USB DEVICE" : name.toUpperCase(java.util.Locale.ROOT)) + "  /  " + device.getVendorId() + ":" + device.getProductId();
            TextView button = action("SELECT  " + description + "   →", false, () -> {
                getSharedPreferences("settings", 0).edit().putInt("vendor", device.getVendorId()).putInt("product", device.getProductId()).apply();
                if (usb.hasPermission(device)) { Work.drain(this); refresh(); }
                else {
                    Intent intent = new Intent(USB_PERMISSION).setPackage(getPackageName());
                    PendingIntent pending = PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
                    usb.requestPermission(device, pending);
                }
            });
            LinearLayout.LayoutParams p = matchWrap(); p.setMargins(0, 0, 0, dp(8)); devices.addView(button, p);
        }
    }
    private LinearLayout column() { LinearLayout view = new LinearLayout(this); view.setOrientation(LinearLayout.VERTICAL); return view; }
    private TextView label(String text, int size, int color, boolean bold) {
        TextView view = new TextView(this); view.setText(text); view.setTextSize(size); view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
        view.setIncludeFontPadding(false); return view;
    }
    private TextView body(String text) { TextView view = label(text, 14, BLACK, false); view.setLineSpacing(dp(3), 1); return view; }
    private void section(LinearLayout parent, String number, String title) {
        LinearLayout row = new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams p = matchWrap(); p.setMargins(0, dp(28), 0, dp(10)); parent.addView(row, p);
        TextView index = label(number, 11, WHITE, true); index.setGravity(Gravity.CENTER); index.setBackgroundColor(BLACK);
        row.addView(index, new LinearLayout.LayoutParams(dp(30), dp(24)));
        TextView name = label(title, 15, BLACK, true); LinearLayout.LayoutParams np = matchWrap(); np.setMargins(dp(10), 0, 0, 0); row.addView(name, np);
    }
    private TextView statusRow(LinearLayout parent, String name) {
        LinearLayout row = new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL); row.setPadding(dp(12), dp(10), dp(12), dp(10)); parent.addView(row, matchWrap());
        TextView label = label(name, 12, BLACK, true); row.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
        TextView value = label("—", 12, BLACK, true); value.setGravity(Gravity.RIGHT); value.setTypeface(Typeface.MONOSPACE, Typeface.BOLD); row.addView(value); return value;
    }
    private void divider(LinearLayout parent) { View line = new View(this); line.setBackgroundColor(BLACK); parent.addView(line, new LinearLayout.LayoutParams(-1, dp(1))); }
    private TextView action(String text, boolean filled, Runnable callback) {
        TextView view = label(text, 14, filled ? WHITE : BLACK, true); view.setGravity(Gravity.CENTER_VERTICAL); view.setMinHeight(dp(52));
        view.setPadding(dp(14), dp(12), dp(14), dp(12)); view.setBackground(border(filled ? BLACK : WHITE, BLACK, 2));
        view.setClickable(true); view.setFocusable(true); view.setOnClickListener(v -> callback.run()); return view;
    }
    private GradientDrawable border(int fill, int stroke, int thickness) { GradientDrawable shape = new GradientDrawable(); shape.setColor(fill); shape.setStroke(dp(thickness), stroke); return shape; }
    private LinearLayout.LayoutParams matchWrap() { return new LinearLayout.LayoutParams(-1, -2); }
    private LinearLayout.LayoutParams marginBottom(int margin) { LinearLayout.LayoutParams p = matchWrap(); p.setMargins(0, 0, 0, dp(margin)); return p; }
    private int dp(float value) { return (int) (getResources().getDisplayMetrics().density * value + .5f); }
    private void toast(String text) { Toast.makeText(this, text, Toast.LENGTH_LONG).show(); }
}
