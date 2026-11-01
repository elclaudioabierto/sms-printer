package com.example.smsprinter;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.Telephony;
import android.telephony.SmsMessage;
import java.text.DateFormat;
import java.util.Date;

public final class SmsReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (!Telephony.Sms.Intents.SMS_RECEIVED_ACTION.equals(intent.getAction())) return;
        SmsMessage[] parts = Telephony.Sms.Intents.getMessagesFromIntent(intent);
        if (parts == null || parts.length == 0 || parts[0] == null) return;
        StringBuilder body = new StringBuilder();
        for (SmsMessage part : parts) if (part != null && part.getMessageBody() != null) body.append(part.getMessageBody());
        if (body.length() == 0) return;
        String sender = parts[0].getDisplayOriginatingAddress();
        String text = "SMS from: " + (sender == null ? "Unknown" : sender) + "\n" + DateFormat.getDateTimeInstance().format(new Date(parts[0].getTimestampMillis())) + "\n\n" + body + "\n\n------------------------------\n";
        PendingResult pending = goAsync();
        Context app = context.getApplicationContext();
        Work.executor.execute(() -> { try (Queue q = new Queue(app)) { q.add(text); q.drain(app); } finally { pending.finish(); } });
    }
}
