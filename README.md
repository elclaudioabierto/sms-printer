# Notification Printer

Android prototype that prints notification text from apps to a USB ESC/POS thermal printer. It can print notifications from SMS, Signal, WhatsApp and other apps when Android delivers readable notification text. It does not read messages directly. Android or the source app may hide, group, redact or omit notification content; some notifications may not be delivered. Test each app and phone.

## Setup

1. Install the APK from the GitHub release on a phone running Android 8 or later with USB host support. The package retains the original `com.example.smsprinter` ID so it can update an existing installation.
2. Open Notification Printer, tap **Continue setup → Allow notifications**, and enable Notification Printer in Android settings. Android may require **Settings → Apps → Notification Printer → ⋮ → Allow restricted settings** for an app downloaded outside an app store before enabling notification access. The exact steps depend on the phone.
3. Under **Choose apps**, turn on **Print selected apps only** if desired. Apps appear after they post a notification; use **Refresh app list** and check the apps you want. Turn on selected-only mode before triggering notifications if you want to prevent them from printing during setup. With selected-only mode off, every app can print.
4. Optionally enter comma-separated words or phrases under **Settings → Filters → Keywords**. After the app filter, a notification prints if its title or text contains any one keyword, case-insensitively. An empty list matches all notifications. Filter changes apply to new notifications, not items already queued.
5. Under **Settings → Filters → Secret code**, optionally require notification text to begin with a chosen code, such as `#print`. This is a convenience filter, not a password or authentication. It depends on the source app exposing notification text.
6. Under **Settings → Print style & preview**, set a header, priority words, and whether to include tiny art. Priority matches get a prominent heading and heavier border, but still must pass the app, keyword, and secret-code filters.
7. Connect the powered printer via USB OTG, open **Settings → Setup & printer**, choose the printer and grant USB access. Print a test page.
8. Send test notifications from each app, including with the screen off. Ensure notifications and their content are enabled in the source app and phone settings.
9. Leave phone and printer powered and connected. After a USB reconnect or reboot, Android may require USB permission again. Check paper and test for an extended period before relying on it.

Notification access can reveal sensitive information, including message content and verification codes. Notifications from all apps may be printed by default, including status notifications; keep paper secure. This app stores notifications locally until sent to the printer and does not upload them. It suppresses repeated notification updates with identical title and text while they remain active; updated text may print again. Android can restart the listener, in which case repeat suppression resets.

The app retries queued notifications periodically when Android permits background jobs. It removes a notification after data is sent to the printer, which does not prove it physically printed. A partial USB failure can cause a duplicate on retry. Battery optimization, USB host and charging support, ESC/POS variants, and device-specific behavior can affect reliability. USB permission may need to be granted again after reconnecting.

Build locally with `./build.sh` using Android SDK 35, Build Tools 35.0.0, and JDK 17. An Android debug signing key is generated in `~/.android/debug.keystore` if absent.
