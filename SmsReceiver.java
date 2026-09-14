package com.example.myapplication;

import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Telephony;
import android.telephony.SmsMessage;

public class SmsReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        if (Telephony.Sms.Intents.SMS_DELIVER_ACTION.equals(action) || 
            Telephony.Sms.Intents.SMS_RECEIVED_ACTION.equals(action)) {
            try {
                Bundle bundle = intent.getExtras();
                if (bundle != null) {
                    Object[] pdus = (Object[]) bundle.get("pdus");
                    String format = bundle.getString("format");
                    if (pdus != null) {
                        for (Object pdu : pdus) {
                            SmsMessage sms;
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                                sms = SmsMessage.createFromPdu((byte[]) pdu, format);
                            } else {
                                sms = SmsMessage.createFromPdu((byte[]) pdu);
                            }
                            if (sms != null) {
                                String sender = sms.getDisplayOriginatingAddress();
                                String body = sms.getMessageBody();
                                long time = sms.getTimestampMillis();

                                // Write incoming SMS to Inbox when acting as Default SMS App
                                ContentValues values = new ContentValues();
                                values.put("address", sender);
                                values.put("body", body);
                                values.put("read", 0);
                                values.put("date", time > 0 ? time : System.currentTimeMillis());
                                context.getContentResolver().insert(Uri.parse("content://sms/inbox"), values);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
