package com.example.smsprinter;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.hardware.usb.*;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import java.io.ByteArrayOutputStream;

final class Printer {
    static UsbDevice selected(Context context) {
        android.content.SharedPreferences p = context.getSharedPreferences("settings", 0);
        int vendor = p.getInt("vendor", -1), product = p.getInt("product", -1);
        UsbManager manager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
        for (UsbDevice d : manager.getDeviceList().values()) if (d.getVendorId() == vendor && d.getProductId() == product) return d;
        return null;
    }
    static boolean print(Context context, String text) {
        UsbManager manager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
        UsbDevice device = selected(context);
        if (device == null || !manager.hasPermission(device)) return false;
        UsbInterface iface = null; UsbEndpoint out = null;
        for (int i = 0; i < device.getInterfaceCount() && out == null; i++) {
            UsbInterface candidate = device.getInterface(i);
            for (int j = 0; j < candidate.getEndpointCount(); j++) {
                UsbEndpoint ep = candidate.getEndpoint(j);
                if (ep.getDirection() == UsbConstants.USB_DIR_OUT && ep.getType() == UsbConstants.USB_ENDPOINT_XFER_BULK) { iface = candidate; out = ep; break; }
            }
        }
        if (out == null) return false;
        UsbDeviceConnection connection = manager.openDevice(device);
        if (connection == null) return false;
        try {
            if (!connection.claimInterface(iface, true)) return false;
            byte[] data = raster(text);
            for (int offset = 0; offset < data.length;) {
                int length = Math.min(4096, data.length - offset);
                int sent = connection.bulkTransfer(out, data, offset, length, 15000);
                if (sent <= 0) return false;
                offset += sent;
            }
            return true;
        } catch (Exception e) { return false; }
        finally { connection.close(); }
    }
    private static byte[] raster(String text) {
        final int width = 576;
        TextPaint paint = new TextPaint(Paint.ANTI_ALIAS_FLAG); paint.setColor(Color.BLACK); paint.setTextSize(27);
        android.text.SpannableString styled = new android.text.SpannableString(text);
        int firstLine = text.indexOf('\n');
        if (firstLine < 0) firstLine = text.length();
        if (firstLine > 0) {
            styled.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD), 0, firstLine, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            styled.setSpan(new android.text.style.RelativeSizeSpan(1.35f), 0, firstLine, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        StaticLayout layout = StaticLayout.Builder.obtain(styled, 0, styled.length(), paint, width - 64).setAlignment(Layout.Alignment.ALIGN_NORMAL).setIncludePad(true).build();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        bytes.write(0x1b); bytes.write(0x40);
        int totalHeight = layout.getHeight() + 64;
        for (int top = 0; top < totalHeight; top += 256) {
            int height = Math.min(256, totalHeight - top);
            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap); canvas.drawColor(Color.WHITE);
            canvas.translate(0, -top);
            Paint border = new Paint(); border.setColor(Color.BLACK); border.setStyle(Paint.Style.STROKE); border.setStrokeWidth(text.startsWith("!!! PRIORITY !!!") ? 7 : 3);
            canvas.drawRect(8, 8, width - 9, totalHeight - 9, border);
            canvas.translate(32, 32); layout.draw(canvas);
            bytes.write(0x1d); bytes.write(0x76); bytes.write(0x30); bytes.write(0);
            bytes.write(width / 8); bytes.write(0); bytes.write(height & 255); bytes.write(height >> 8);
            int[] row = new int[width];
            for (int y = 0; y < height; y++) {
                bitmap.getPixels(row, 0, width, 0, y, width, 1);
                for (int x = 0; x < width; x += 8) {
                    int value = 0;
                    for (int bit = 0; bit < 8; bit++) if ((row[x + bit] & 255) < 160) value |= 0x80 >> bit;
                    bytes.write(value);
                }
            }
            bitmap.recycle();
        }
        bytes.write(10); bytes.write(10); bytes.write(10);
        return bytes.toByteArray();
    }
}
