# SMS Printer

A local-only Android prototype for printing incoming SMS on a USB ESC/POS thermal printer. Requires Android 8 or later and USB host support. SMS, not MMS, RCS, Signal or WhatsApp.

## Setup

1. Install `build/sms-printer-debug.apk` on the phone. This is a debug build, intended for private testing.
2. Connect the powered printer via USB OTG. Open SMS Printer, allow SMS access, choose the printer, and grant USB access.
3. Print a test page. Send an SMS from another phone and verify it prints with the screen off.
4. Leave phone and printer powered and connected. Check paper and run an extended test before relying on it. After a USB reconnect or reboot, Android may require permission again; open the app and reconnect if needed.

The app keeps pending messages in a private database and retries periodically while Android allows background jobs. It deletes a message after data is sent to the printer, which does not prove it physically printed. A partial USB failure can cause a duplicate on retry. Battery optimization, USB host/charging support, ESC/POS variants and device-specific behavior may affect reliability. Printed messages are visible on paper. Messages are not uploaded.

Build locally with `./build.sh` using Android SDK 35, Build Tools 35.0.0, and JDK 17. An Android debug signing key is generated in `~/.android/debug.keystore` if absent.
