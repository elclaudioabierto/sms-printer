package com.example.smsprinter;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.*;
import android.hardware.usb.*;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.*;
import java.util.ArrayList;

public final class MainActivity extends Activity {
    private static final String USB_PERMISSION = "com.example.smsprinter.USB_PERMISSION";
    private UsbManager usb;
    private LinearLayout list;
    private TextView status;
    private final BroadcastReceiver permissionReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (USB_PERMISSION.equals(intent.getAction())) {
                if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) { toast("Printer connected"); Work.drain(MainActivity.this); }
                else toast("USB permission was not granted");
                refresh();
            }
        }
    };
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        usb = (UsbManager) getSystemService(USB_SERVICE);
        IntentFilter filter = new IntentFilter(USB_PERMISSION);
        if (android.os.Build.VERSION.SDK_INT >= 33) registerReceiver(permissionReceiver, filter, Context.RECEIVER_NOT_EXPORTED); else registerReceiver(permissionReceiver, filter);
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(28, 32, 28, 24);
        ScrollView scroll = new ScrollView(this); scroll.addView(root); setContentView(scroll);
        TextView title = new TextView(this); title.setText("Notification Printer"); title.setTextSize(28); root.addView(title);
        TextView intro = new TextView(this); intro.setText("Connect your USB ESC/POS printer, choose it below, and allow notification access. Keep the printer connected and powered."); intro.setTextSize(16); intro.setPadding(0, 16, 0, 16); root.addView(intro);
        status = new TextView(this); status.setTextSize(16); root.addView(status);
        Button permission = new Button(this); permission.setText("Allow notification access"); permission.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))); root.addView(permission);
        Button refresh = new Button(this); refresh.setText("Find USB printers"); refresh.setOnClickListener(v -> refresh()); root.addView(refresh);
        list = new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL); root.addView(list);
        Button test = new Button(this); test.setText("Print test page"); test.setOnClickListener(v -> {
            Work.executor.execute(() -> { boolean ok = Printer.print(getApplicationContext(), "Notification Printer\n\nTest page\nIf you can read this, the printer is connected.\n"); runOnUiThread(() -> { toast(ok ? "Test sent to printer" : "Could not print. Check connection and USB permission."); refresh(); }); });
        }); root.addView(test);
        Button retry = new Button(this); retry.setText("Retry queued notifications"); retry.setOnClickListener(v -> { Work.drain(this); toast("Retry requested"); }); root.addView(retry);
        TextView note = new TextView(this); note.setText("Notifications can contain private information and are stored on this phone until sent to the printer. Check paper and test the setup before relying on it. USB permission may need to be granted again after reconnecting."); note.setPadding(0, 24, 0, 0); root.addView(note);
        Work.schedule(this);
        refresh();
    }
    @Override protected void onResume() { super.onResume(); if (list != null) refresh(); }
    @Override protected void onDestroy() { unregisterReceiver(permissionReceiver); super.onDestroy(); }
    private void refresh() {
        UsbDevice selected = Printer.selected(this);
        int count; try (Queue q = new Queue(this)) { count = q.count(); }
        String listeners = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        boolean enabled = false;
        if (listeners != null) for (String item : listeners.split(":")) { ComponentName name = ComponentName.unflattenFromString(item); if (name != null && name.equals(new ComponentName(this, NotificationReceiver.class))) enabled = true; }
        String printer = selected == null ? "not connected" : (usb.hasPermission(selected) ? "connected" : "permission needed");
        status.setText("Notification access: " + (enabled ? "allowed" : "not allowed") + "\nPrinter: " + printer + "\nQueued messages: " + count);
        list.removeAllViews();
        ArrayList<UsbDevice> devices = new ArrayList<>(usb.getDeviceList().values());
        if (devices.isEmpty()) { TextView empty = new TextView(this); empty.setText("No USB devices detected."); list.addView(empty); }
        for (UsbDevice device : devices) {
            Button button = new Button(this);
            String name = device.getProductName();
            button.setText("Use " + (name == null ? "USB device" : name) + " (" + device.getVendorId() + ":" + device.getProductId() + ")");
            button.setOnClickListener(v -> {
                getSharedPreferences("settings", 0).edit().putInt("vendor", device.getVendorId()).putInt("product", device.getProductId()).apply();
                if (usb.hasPermission(device)) { Work.drain(this); refresh(); }
                else {
                    Intent intent = new Intent(USB_PERMISSION).setPackage(getPackageName());
                    PendingIntent pending = PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
                    usb.requestPermission(device, pending);
                }
            });
            list.addView(button);
        }
    }
    private void toast(String text) { Toast.makeText(this, text, Toast.LENGTH_LONG).show(); }
}
