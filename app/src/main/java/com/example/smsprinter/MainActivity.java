package com.example.smsprinter;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.*;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.hardware.usb.*;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;

public final class MainActivity extends Activity {
    private static final String USB_PERMISSION = "com.example.smsprinter.USB_PERMISSION";
    private static final int BLACK = Color.BLACK, WHITE = Color.WHITE;
    private UsbManager usb;
    private LinearLayout page;
    private String screen = "home";
    private final BroadcastReceiver permissionReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (USB_PERMISSION.equals(intent.getAction())) {
                if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) { toast("Printer connected"); Work.drain(MainActivity.this); }
                else toast("USB permission not granted");
                show(screen);
            }
        }
    };
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BLACK); getWindow().setNavigationBarColor(BLACK);
        usb = (UsbManager) getSystemService(USB_SERVICE);
        IntentFilter filter = new IntentFilter(USB_PERMISSION);
        if (android.os.Build.VERSION.SDK_INT >= 33) registerReceiver(permissionReceiver, filter, Context.RECEIVER_NOT_EXPORTED); else registerReceiver(permissionReceiver, filter);
        Work.schedule(this);
        show("home");
    }
    @Override protected void onResume() { super.onResume(); if (page != null && (screen.equals("home") || screen.equals("setup") || screen.equals("apps"))) show(screen); }
    @Override protected void onDestroy() { unregisterReceiver(permissionReceiver); super.onDestroy(); }
    @Override public void onBackPressed() { if (screen.equals("home")) super.onBackPressed(); else if (screen.equals("filters") || screen.equals("style")) show("settings"); else show("home"); }

    private void show(String destination) {
        screen = destination;
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true); scroll.setBackgroundColor(WHITE);
        page = column(); page.setPadding(dp(16), dp(16), dp(16), dp(32)); scroll.addView(page);
        if (android.os.Build.VERSION.SDK_INT >= 35) {
            // Android 15 draws system bars over app content; give them a black backdrop.
            LinearLayout frame = column(); frame.setBackgroundColor(BLACK);
            frame.setOnApplyWindowInsetsListener((view, insets) -> {
                android.graphics.Insets bars = insets.getInsets(android.view.WindowInsets.Type.systemBars() | android.view.WindowInsets.Type.displayCutout());
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
                return insets;
            });
            frame.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1)); setContentView(frame);
        } else setContentView(scroll);
        switch (destination) {
            case "setup": setup(); break;
            case "apps": apps(); break;
            case "settings": settings(); break;
            case "filters": filters(); break;
            case "style": style(); break;
            default: home();
        }
    }
    private void home() {
        LinearLayout header = column(); header.setBackgroundColor(BLACK); header.setPadding(dp(18), dp(18), dp(18), dp(20)); page.addView(header, matchWrap());
        header.addView(label("INCOMING / ON PAPER    ■", 11, WHITE, true));
        TextView title = label("NOTIFICATION\nPRINTER", 32, WHITE, true); title.setTypeface(Typeface.create("sans-serif-black", Typeface.NORMAL)); title.setLineSpacing(0, .9f);
        add(header, title, 16, 12);
        TextView subtitle = label("YOUR PHONE. YOUR PAPER.", 12, WHITE, false); subtitle.setTypeface(Typeface.MONOSPACE); header.addView(subtitle);

        boolean access = notificationEnabled(); UsbDevice selected = Printer.selected(this);
        boolean ready = selected != null && usb.hasPermission(selected);
        int count; try (Queue q = new Queue(this)) { count = q.count(); }
        section("STATUS");
        LinearLayout card = column(); card.setBackground(border(WHITE, 2)); card.setPadding(dp(16), dp(16), dp(16), dp(16));
        String heading = !access ? "NOTIFICATION ACCESS NEEDED" : !ready ? "PRINTER NOT CONNECTED" : "READY TO PRINT";
        card.addView(label(heading, 19, BLACK, true));
        TextView detail = body(!access ? "Allow notification access to get started." : !ready ? "Connect and select your USB printer." : "Notifications can be sent to your printer."); add(card, detail, 8, 0);
        TextView queued = label(count == 1 ? "1 ITEM IN QUEUE" : count + " ITEMS IN QUEUE", 11, BLACK, true); add(card, queued, 12, 0);
        page.addView(card, matchWrap());
        if (!access || !ready) add(page, action(!access ? "CONTINUE SETUP   →" : "CONNECT PRINTER   →", true, () -> show("setup")), 10, 0);
        section("QUICK ACTIONS");
        add(page, action("CHOOSE APPS   →", true, () -> show("apps")), 0, 8);
        add(page, action("PRINT TEST PAGE   →", false, this::printTest), 0, 8);
        add(page, action("SETTINGS   →", false, () -> show("settings")), 0, 0);
        section("PRINTING RULES");
        TextView summary = body(ruleSummary()); page.addView(summary, matchWrap());
        add(page, action("EDIT FILTERS   →", false, () -> show("filters")), 12, 0);
        TextView note = label("PRINTED NOTIFICATIONS MAY CONTAIN PRIVATE INFORMATION.", 11, BLACK, false); note.setTypeface(Typeface.MONOSPACE); add(page, note, 28, 0);
    }
    private void setup() {
        screenHeader("SETUP", "Get ready in three steps.");
        section("01 / NOTIFICATIONS");
        page.addView(body(notificationEnabled() ? "Notification access is on." : "Allow notification access so the app can receive alerts."));
        add(page, action(notificationEnabled() ? "NOTIFICATIONS ENABLED   ✓" : "ALLOW NOTIFICATIONS   →", !notificationEnabled(), () -> startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))), 12, 0);
        section("02 / USB PRINTER");
        page.addView(body("Connect a powered printer by USB, then select it below."));
        add(page, action("REFRESH DEVICES   ↻", false, () -> show("setup")), 12, 8);
        ArrayList<UsbDevice> available = new ArrayList<>(usb.getDeviceList().values());
        if (available.isEmpty()) page.addView(body("No USB devices found."));
        for (UsbDevice device : available) {
            String name = device.getProductName();
            String text = "USE " + (name == null ? "USB DEVICE" : name.toUpperCase(Locale.ROOT)) + "  /  " + device.getVendorId() + ":" + device.getProductId();
            add(page, action(text, false, () -> selectDevice(device)), 0, 8);
        }
        section("03 / TEST");
        page.addView(body("Print a test page, then send a notification to check the full setup."));
        add(page, action("PRINT TEST PAGE   →", true, this::printTest), 12, 0);
    }
    private void apps() {
        screenHeader("CHOOSE APPS", "Decide which apps can print notifications.");
        boolean selected = prefs().getBoolean("selected_only", false);
        Switch toggle = new Switch(this); toggle.setText("PRINT SELECTED APPS ONLY"); toggle.setTextColor(BLACK); toggle.setTextSize(14); toggle.setTypeface(Typeface.DEFAULT_BOLD);
        toggle.setThumbTintList(ColorStateList.valueOf(BLACK)); toggle.setTrackTintList(ColorStateList.valueOf(0xffaaaaaa)); toggle.setPadding(dp(12), dp(12), dp(12), dp(12)); toggle.setBackground(border(WHITE, 2));
        toggle.setChecked(selected); toggle.setOnCheckedChangeListener((b, value) -> { prefs().edit().putBoolean("selected_only", value).apply(); show("apps"); });
        add(page, toggle, 20, 12);
        page.addView(body(selected ? "Only checked apps can print." : "All apps can print while this switch is off."));
        section("APPS SEEN RECENTLY");
        page.addView(body("Apps appear here after sending a notification. Turn on selected-only mode first to prevent unselected apps from printing."));
        Map<String, ?> known = getSharedPreferences("sources", 0).getAll(); ArrayList<String> packages = new ArrayList<>(known.keySet());
        Collections.sort(packages, (a, b) -> String.valueOf(known.get(a)).compareToIgnoreCase(String.valueOf(known.get(b))));
        if (packages.isEmpty()) { TextView empty = label("NO APPS SEEN YET", 12, BLACK, true); add(page, empty, 16, 4); }
        for (String pkg : packages) {
            CheckBox check = new CheckBox(this); check.setButtonTintList(ColorStateList.valueOf(BLACK)); check.setTextColor(BLACK); check.setTextSize(15);
            check.setText(String.valueOf(known.get(pkg))); check.setContentDescription(String.valueOf(known.get(pkg)) + ", " + pkg); check.setPadding(dp(4), dp(5), dp(4), dp(5));
            check.setChecked(prefs().getBoolean("app:" + pkg, false)); check.setOnCheckedChangeListener((button, checked) -> prefs().edit().putBoolean("app:" + pkg, checked).apply()); page.addView(check, matchWrap());
        }
        add(page, action("REFRESH APP LIST   ↻", false, () -> show("apps")), 12, 0);
    }
    private void settings() {
        screenHeader("SETTINGS", "Make your printer yours.");
        section("CUSTOMIZE");
        add(page, action("FILTERS   →", true, () -> show("filters")), 0, 8);
        add(page, action("PRINT STYLE & PREVIEW   →", false, () -> show("style")), 0, 8);
        add(page, action("SETUP & PRINTER   →", false, () -> show("setup")), 0, 8);
        add(page, action("RETRY QUEUED ITEMS   →", false, () -> { Work.drain(this); toast("Retry requested"); }), 0, 0);
    }
    private void filters() {
        screenHeader("FILTERS", "Choose which notifications reach the printer.");
        section("APP FILTER"); page.addView(body(ruleSummary()));
        add(page, action("CHOOSE APPS   →", false, () -> show("apps")), 12, 0);
        section("KEYWORDS"); page.addView(body("Print only if the notification contains any of these words or phrases. Separate them with commas; leave blank to allow all."));
        add(page, preferenceInput("keywords", "", "urgent, dinner, call me", true, null), 12, 0);
        section("SECRET CODE"); page.addView(body("Optional: notification text must start with this code. Leave blank to turn it off. This is a filter, not a password."));
        add(page, preferenceInput("secret_code", "", "e.g. #print", false, null), 12, 0);
        TextView note = body("All filters work together and apply to new notifications. They do not change items already in the queue."); add(page, note, 24, 0);
    }
    private void style() {
        screenHeader("PRINT STYLE", "Customize the little receipt.");
        section("PREVIEW");
        TextView preview = label("", 14, BLACK, false); preview.setTypeface(Typeface.MONOSPACE); preview.setPadding(dp(16), dp(16), dp(16), dp(16)); preview.setBackground(border(WHITE, 2)); page.addView(preview, matchWrap());
        Runnable updatePreview = () -> {
            String header = prefs().getString("print_header", "INCOMING TELEGRAM").trim();
            boolean art = prefs().getBoolean("tiny_art", true);
            preview.setText((header.isEmpty() ? "" : header + "\n") + "MESSAGES  /  TODAY\n\nSam\nSee you soon!" + (art ? "\n\n   *  .  *\n  .  [#]  .\n   *  .  *" : ""));
        };
        updatePreview.run();
        TextView hint = label("ILLUSTRATIVE PREVIEW / PRINT WIDTH MAY VARY", 10, BLACK, false); add(page, hint, 8, 0);
        section("CUSTOM HEADER"); page.addView(preferenceInput("print_header", "INCOMING TELEGRAM", "Your header", false, updatePreview), matchWrap());
        section("PRIORITY WORDS"); page.addView(body("Messages containing any of these words get a bold priority heading and heavier border. Filters still apply."));
        add(page, preferenceInput("priority_words", "urgent, emergency", "urgent, emergency", false, null), 12, 0);
        CheckBox art = new CheckBox(this); art.setButtonTintList(ColorStateList.valueOf(BLACK)); art.setTextColor(BLACK); art.setText("ADD TINY ART"); art.setTypeface(Typeface.DEFAULT_BOLD);
        art.setChecked(prefs().getBoolean("tiny_art", true)); art.setOnCheckedChangeListener((b, checked) -> { prefs().edit().putBoolean("tiny_art", checked).apply(); updatePreview.run(); }); add(page, art, 18, 0);
    }
    private String ruleSummary() {
        boolean selected = prefs().getBoolean("selected_only", false); StringBuilder text = new StringBuilder();
        if (!selected) text.append("Printing notifications from all apps");
        else {
            Map<String, ?> known = getSharedPreferences("sources", 0).getAll(); ArrayList<String> names = new ArrayList<>();
            for (String pkg : known.keySet()) if (prefs().getBoolean("app:" + pkg, false)) names.add(String.valueOf(known.get(pkg)));
            Collections.sort(names, String.CASE_INSENSITIVE_ORDER);
            if (names.isEmpty()) text.append("No apps selected — nothing will print");
            else text.append("Printing from ").append(android.text.TextUtils.join(", ", names));
        }
        String keywords = prefs().getString("keywords", "").trim(); if (!keywords.isEmpty()) text.append(" containing any of: ").append(keywords);
        String code = prefs().getString("secret_code", "").trim(); if (!code.isEmpty()) text.append("; text must start with ").append(code);
        return text.append(".").toString();
    }
    private boolean notificationEnabled() {
        String listeners = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        if (listeners != null) for (String item : listeners.split(":")) { ComponentName name = ComponentName.unflattenFromString(item); if (name != null && name.equals(new ComponentName(this, NotificationReceiver.class))) return true; }
        return false;
    }
    private void selectDevice(UsbDevice device) {
        getSharedPreferences("settings", 0).edit().putInt("vendor", device.getVendorId()).putInt("product", device.getProductId()).apply();
        if (usb.hasPermission(device)) { Work.drain(this); show("setup"); }
        else { Intent intent = new Intent(USB_PERMISSION).setPackage(getPackageName()); usb.requestPermission(device, PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE)); }
    }
    private void printTest() {
        Work.executor.execute(() -> { boolean ok = Printer.print(getApplicationContext(), "NOTIFICATION PRINTER\n\nTEST PAGE\n\nIf you can read this, your printer is connected.\n"); runOnUiThread(() -> toast(ok ? "Test sent to printer" : "Print failed. Check the USB connection.")); });
    }
    private SharedPreferences prefs() { return getSharedPreferences("filters", 0); }
    private EditText preferenceInput(String key, String defaultValue, String hint, boolean multiline, Runnable changed) {
        EditText input = new EditText(this); input.setTextColor(BLACK); input.setHintTextColor(0xff666666); input.setTextSize(15); input.setSingleLine(!multiline); if (multiline) input.setMinLines(2);
        input.setPadding(dp(12), dp(12), dp(12), dp(12)); input.setBackground(border(WHITE, 2)); input.setHint(hint); input.setText(prefs().getString(key, defaultValue));
        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence value, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence value, int start, int before, int count) { prefs().edit().putString(key, value.toString()).apply(); if (changed != null) changed.run(); }
            @Override public void afterTextChanged(Editable value) { }
        }); return input;
    }
    private void screenHeader(String title, String subtitle) {
        page.addView(action("←  BACK", false, () -> { if (screen.equals("filters") || screen.equals("style")) show("settings"); else show("home"); }), matchWrap());
        TextView heading = label(title, 30, BLACK, true); heading.setTypeface(Typeface.create("sans-serif-black", Typeface.NORMAL)); add(page, heading, 24, 8); page.addView(body(subtitle));
    }
    private LinearLayout column() { LinearLayout v = new LinearLayout(this); v.setOrientation(LinearLayout.VERTICAL); return v; }
    private TextView label(String text, int size, int color, boolean bold) { TextView v = new TextView(this); v.setText(text); v.setTextSize(size); v.setTextColor(color); if (bold) v.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD)); v.setIncludeFontPadding(false); return v; }
    private TextView body(String text) { TextView v = label(text, 14, BLACK, false); v.setLineSpacing(dp(3), 1); return v; }
    private void section(String title) { TextView t = label(title, 13, BLACK, true); LinearLayout.LayoutParams p = matchWrap(); p.setMargins(0, dp(26), 0, dp(10)); page.addView(t, p); }
    private TextView action(String text, boolean filled, Runnable callback) { TextView v = label(text, 14, filled ? WHITE : BLACK, true); v.setGravity(Gravity.CENTER_VERTICAL); v.setMinHeight(dp(52)); v.setPadding(dp(14), dp(12), dp(14), dp(12)); v.setBackground(border(filled ? BLACK : WHITE, 2)); v.setClickable(true); v.setFocusable(true); v.setOnClickListener(view -> callback.run()); return v; }
    private GradientDrawable border(int fill, int thickness) { GradientDrawable shape = new GradientDrawable(); shape.setColor(fill); shape.setStroke(dp(thickness), BLACK); return shape; }
    private void add(LinearLayout parent, View child, int top, int bottom) { LinearLayout.LayoutParams p = matchWrap(); p.setMargins(0, dp(top), 0, dp(bottom)); parent.addView(child, p); }
    private LinearLayout.LayoutParams matchWrap() { return new LinearLayout.LayoutParams(-1, -2); }
    private int dp(float value) { return (int) (getResources().getDisplayMetrics().density * value + .5f); }
    private void toast(String text) { Toast.makeText(this, text, Toast.LENGTH_LONG).show(); }
}
