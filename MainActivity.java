package com.example.myapplication;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.provider.Telephony;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends Activity {

    public static MainActivity instance;
    public static String currentQueriedAccount = "";

    private WebView webView;
    private FrameLayout rootContainer;
    private View loadingOverlay;
    private BroadcastReceiver nameReceiver;
    private Handler checkerHandler = new Handler(Looper.getMainLooper());
    private Handler overlayTimeoutHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        instance = this;
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        // Android 13+ Notification ፈቃድ መጠየቅ
        if (Build.VERSION.SDK_INT >= 33) {
            try {
                if (checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 101);
                }
            } catch (Exception e) {}
        }

        // SMS ፈቃዶች መጠየቅ እና Default SMS App ጥያቄ ማቅረብ
        try {
            if (Build.VERSION.SDK_INT >= 23) {
                String[] smsPerms = new String[]{
                    "android.permission.READ_SMS",
                    "android.permission.WRITE_SMS",
                    "android.permission.RECEIVE_SMS",
                    "android.permission.SEND_SMS"
                };
                boolean needPerm = false;
                for (String p : smsPerms) {
                    if (checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED) {
                        needPerm = true;
                        break;
                    }
                }
                if (needPerm) {
                    requestPermissions(smsPerms, 102);
                }
            }
            checkAndRequestDefaultSmsApp();
        } catch (Exception e) {}

        // 1. መከላከያ፡ Screenኑ ጥቁር እንዳይሆን (Never flash or stay black)
        getWindow().setBackgroundDrawable(new ColorDrawable(Color.WHITE));
        if (getWindow().getDecorView() != null) {
            getWindow().getDecorView().setBackgroundColor(Color.WHITE);
        }

        rootContainer = new FrameLayout(this);
        rootContainer.setBackgroundColor(Color.WHITE);
        rootContainer.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        // WebView ማዋቀር
        webView = new WebView(this);
        webView.setBackgroundColor(Color.WHITE);
        webView.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));
        rootContainer.addView(webView);

        // Loading Overlay (CBE Birr ዘመናዊ ንፁህ ሎዲንግ - በፍፁም ጥቁር ስክሪን አይመጣም)
        setupLoadingOverlay();

        setContentView(rootContainer);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);

        webView.setOverScrollMode(WebView.OVER_SCROLL_NEVER);
        webView.addJavascriptInterface(new WebAppInterface(), "AndroidBridge");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                if (url.contains("receipt.html")) {
                    openInExternalChrome(url);
                    return true;
                }
                if (url.startsWith("tel:")) {
                    makePhoneCall(url);
                    return true;
                }
                if (url.startsWith("sms:") || url.startsWith("smsto:")) {
                    String number = url.replace("sms:", "").replace("smsto:", "");
                    if (number.contains("?")) number = number.substring(0, number.indexOf("?"));
                    view.loadUrl("file:///android_asset/messages_screen.html?recipient=" + Uri.encode(number));
                    return true;
                }
                view.loadUrl(url);
                return true;
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                if (url.contains("receipt.html")) {
                    openInExternalChrome(url);
                    return true;
                }
                if (url.startsWith("tel:")) {
                    makePhoneCall(url);
                    return true;
                }
                if (url.startsWith("sms:") || url.startsWith("smsto:")) {
                    String number = url.replace("sms:", "").replace("smsto:", "");
                    if (number.contains("?")) number = number.substring(0, number.indexOf("?"));
                    view.loadUrl("file:///android_asset/messages_screen.html?recipient=" + Uri.encode(number));
                    return true;
                }
                view.loadUrl(url);
                return true;
            }
        });

        // የባንኩ ድረ-ገጽ (ወይም በሎካል ፋይል መቀየር ይችላሉ)
        webView.loadUrl("https://cbe-birr-bedele.vercel.app/");

        // ከስልክ መደወያ ወይም ከሌላ አፕ የተላከ የ SMS intent ካለ መክፈት
        handleIncomingIntent(getIntent());

        // 2. ስሙን መቀበያ BroadcastReceiver (ከ ussd.java ጋር ቀጥታ የተገናኘ)
        nameReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                final String name = intent.getStringExtra("account_name");
                final String acc = intent.getStringExtra("account_number");
                injectNameToWeb(name, acc != null ? acc : currentQueriedAccount);
            }
        };
        registerReceiver(nameReceiver, new IntentFilter("CBE_NAME_RECEIVED"));

        // 3. ፈጣን ስም መድረሱን በየ 100ms ቼክ ማድረጊያ Handler
        checkerHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (ussd.lastFetchedName != null) {
                    String name = ussd.lastFetchedName;
                    String acc = ussd.lastFetchedAccount != null ? ussd.lastFetchedAccount : currentQueriedAccount;
                    ussd.lastFetchedName = null;
                    ussd.lastFetchedAccount = null;
                    injectNameToWeb(name, acc);
                }
                checkerHandler.postDelayed(this, 100);
            }
        }, 100);

        // 4. Accessibility ክፍት መሆኑን ማረጋገጫ
        checkAccessibilityPermission();
    }

    private void setupLoadingOverlay() {
        loadingOverlay = new FrameLayout(this);
        loadingOverlay.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));
        // ግማሽ ግልጽ ጀርባ (ጥቁር እንዳይሆን ነጭ-ግራጫ ቅኝት)
        loadingOverlay.setBackgroundColor(Color.parseColor("#99000000"));
        loadingOverlay.setVisibility(View.GONE);

        // ማዕከላዊ ነጭ ካርድ
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        int pad = (int) (24 * getResources().getDisplayMetrics().density);
        card.setPadding(pad, pad, pad, pad);

        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(Color.WHITE);
        cardBg.setCornerRadius(16 * getResources().getDisplayMetrics().density);
        card.setBackground(cardBg);

        FrameLayout.LayoutParams cardParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        cardParams.gravity = Gravity.CENTER;
        card.setLayoutParams(cardParams);

        ProgressBar spinner = new ProgressBar(this);
        card.addView(spinner);

        TextView label = new TextView(this);
        label.setText("የተቀባዩን ስም ከ CBE በማጣራት ላይ...\nVerifying Account Name...");
        label.setTextSize(14);
        label.setTextColor(Color.parseColor("#333333"));
        label.setGravity(Gravity.CENTER);
        label.setPadding(0, (int) (12 * getResources().getDisplayMetrics().density), 0, 0);
        card.addView(label);

        ((FrameLayout) loadingOverlay).addView(card);
        rootContainer.addView(loadingOverlay);
    }

    public void showLoadingOverlay() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (loadingOverlay != null) {
                    loadingOverlay.setVisibility(View.VISIBLE);
                    loadingOverlay.bringToFront();
                    // ስክሪኑ ተጣብቆ እንዳይቀር በ 4 ሰከንድ ራሱ ይጠፋል (Failsafe)
                    overlayTimeoutHandler.removeCallbacksAndMessages(null);
                    overlayTimeoutHandler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            hideLoadingOverlay();
                        }
                    }, 4000);
                }
            }
        });
    }

    public void hideLoadingOverlay() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (loadingOverlay != null) {
                    loadingOverlay.setVisibility(View.GONE);
                }
            }
        });
    }

    private void openInExternalChrome(String url) {
        try {
            Intent chromeIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            chromeIntent.setPackage("com.android.chrome");
            startActivity(chromeIntent);
        } catch (Exception e) {
            try {
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                startActivity(browserIntent);
            } catch (Exception ex) {}
        }
    }

    private void makePhoneCall(String url) {
        try {
            Intent callIntent = new Intent(Intent.ACTION_CALL);
            callIntent.setData(Uri.parse(url));
            startActivity(callIntent);
        } catch (Exception e) {
            try {
                Intent dialIntent = new Intent(Intent.ACTION_DIAL);
                dialIntent.setData(Uri.parse(url));
                startActivity(dialIntent);
            } catch (Exception ex) {}
        }
    }

    // ስሙንና አካውንቱን በቀጥታ ወደ ድረ-ገጹና ወደ Saved Account ማስተላለፊያ
    public void injectNameToWeb(final String name, final String accNum) {
        if (name == null || name.trim().isEmpty()) {
            hideLoadingOverlay();
            return;
        }
        hideLoadingOverlay();

        final String finalAcc = (accNum != null && !accNum.isEmpty()) ? accNum : currentQueriedAccount;

        webView.post(new Runnable() {
            @Override
            public void run() {
                String safeName = name.replace("'", "\\'").replace("\"", "\\\"").trim();
                String safeAcc = finalAcc.replace("'", "\\'").replace("\"", "\\\"").trim();

                // 1) JavaScript ተጠቅሞ ወደ Webview ስሙን ማስገባት
                // 2) ወደ CbeStorage.saveAccount(name, number) በቀጥታ መመዝገብ
                // 3) በ LocalStorage Saved Accounts ውስጥ 100% እንዲቀመጥ ማድረግ
                String js = "try {" +
                        "  var recName = '" + safeName + "';" +
                        "  var recAcc = '" + safeAcc + "';" +
                        "  if (window.setAccountDetails) { window.setAccountDetails(recName, recAcc); }" +
                        "  if (window.onUssdResult) { window.onUssdResult(recName, recAcc); }" +
                        "  if (window.CbeStorage && typeof window.CbeStorage.saveAccount === 'function') {" +
                        "    window.CbeStorage.saveAccount(recName, recAcc);" +
                        "  }" +
                        "  var display = document.getElementById('acc-name-display');" +
                        "  if (display) { display.innerHTML = '<span style=\"color:#000000;font-weight:700;font-size:14px;\">' + recName + '</span>'; }" +
                        "  var input = document.getElementById('input-recipient');" +
                        "  if (input && recAcc && !input.value) { input.value = recAcc; }" +
                        "} catch (e) { console.error('Inject error', e); }";

                webView.evaluateJavascript(js, null);
            }
        });
    }

    private void checkAccessibilityPermission() {
        int enabled = 0;
        try {
            enabled = Settings.Secure.getInt(getContentResolver(), Settings.Secure.ACCESSIBILITY_ENABLED);
        } catch (Exception e) {}

        boolean hasService = false;
        if (enabled == 1) {
            String services = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (services != null && services.contains(getPackageName())) {
                hasService = true;
            }
        }

        if (!hasService) {
            Toast.makeText(this, "እባክዎ Accessibility ውስጥ CBE Birr Plusን ያብሩ (ON ያድርጉ)", Toast.LENGTH_LONG).show();
            try {
                Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                startActivity(intent);
            } catch (Exception e) {}
        }
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    public class WebAppInterface {

        // የ USSD ጥሪ መደወያ - አካውንት ቁጥሩን ለይቶ በማስቀመጥ
        @JavascriptInterface
        public void makeCall(String ussdCode) {
            try {
                // ከአካውንት ኮዱ ውስጥ የ 13 ዲጂት አካውንት ቁጥሩን ፈልጎ መመዝገብ
                Pattern pattern = Pattern.compile("\\*847\\*1\\*2\\*(\\d+)#");
                Matcher matcher = pattern.matcher(ussdCode);
                if (matcher.find()) {
                    currentQueriedAccount = matcher.group(1);
                } else {
                    Pattern digits = Pattern.compile("(\\d{10,16})");
                    Matcher m2 = digits.matcher(ussdCode);
                    if (m2.find()) {
                        currentQueriedAccount = m2.group(1);
                    }
                }
                ussd.currentAccountNumber = currentQueriedAccount;

                // ሎዲንግ ማሳየት (ጥቁር ስክሪን እንዳይሆን ንፁህ ነጭ CBE ካርድ)
                showLoadingOverlay();

                String encoded = ussdCode.replace("#", "%23");
                Intent callIntent = new Intent(Intent.ACTION_CALL);
                callIntent.setData(Uri.parse("tel:" + encoded));
                startActivity(callIntent);
            } catch (Exception e) {
                try {
                    String encoded = ussdCode.replace("#", "%23");
                    Intent dialIntent = new Intent(Intent.ACTION_DIAL);
                    dialIntent.setData(Uri.parse("tel:" + encoded));
                    startActivity(dialIntent);
                } catch (Exception ex) {}
            }
        }

        @JavascriptInterface
        public void openInChrome(String url) {
            openInExternalChrome(url);
        }

        // ክፍያው ሲያልቅ ሜሴጁን ወደ Inbox መላኪያና Broadcast ማሰራጫ
        @JavascriptInterface
        public void postTransactionSms(final String smsBody, final String receiptUrl, final String recipient, final String amount) {
            postTransactionSms(smsBody, receiptUrl, recipient, amount, "");
        }

        @JavascriptInterface
        public void postTransactionSms(final String smsBody, final String receiptUrl, final String recipient, final String amount, final String balance) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    // 1. የስልኩ Inbox ውስጥ መፃፍ
                    try {
                        ContentValues values = new ContentValues();
                        values.put("address", "CBEBirr");
                        values.put("body", smsBody);
                        values.put("read", 0);
                        values.put("date", System.currentTimeMillis());
                        getContentResolver().insert(Uri.parse("content://sms/inbox"), values);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    // 2. Broadcast መላክ (ለ Messages app እና ለአጠቃላይ)
                    try {
                        Intent broadcastIntent = new Intent("com.cbe.TRANSACTION_RECEIVED");
                        broadcastIntent.putExtra("sms_body", smsBody);
                        broadcastIntent.putExtra("amount", amount);
                        broadcastIntent.putExtra("recipient", recipient);
                        broadcastIntent.putExtra("receipt_url", receiptUrl);
                        broadcastIntent.putExtra("balance", balance);
                        sendBroadcast(broadcastIntent);

                        try {
                            Intent directBroadcast = new Intent(broadcastIntent);
                            directBroadcast.setPackage("com.cbe.messages");
                            sendBroadcast(directBroadcast);
                        } catch (Exception ex1) {}
                    } catch (Exception e) {}

                    // 3. Notification ማሳየት (ሲነካ በቀጥታ ወደ Messages App ይወስዳል)
                    showNotification(smsBody, receiptUrl, recipient, amount, balance);
                }
            });
        }

        @JavascriptInterface
        public void postBalanceUpdate(final String newBalance) {
            try {
                Intent balanceIntent = new Intent("com.cbe.BALANCE_UPDATED");
                balanceIntent.putExtra("balance", newBalance);
                sendBroadcast(balanceIntent);

                try {
                    Intent directBalance = new Intent(balanceIntent);
                    directBalance.setPackage("com.cbe.messages");
                    sendBroadcast(directBalance);
                } catch (Exception ex2) {}
            } catch (Exception e) {}
        }

        @JavascriptInterface
        public void requestDefaultSmsApp() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    checkAndRequestDefaultSmsApp();
                }
            });
        }

        @JavascriptInterface
        public void openSmsApp(final String phone) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (webView != null) {
                        webView.loadUrl("file:///android_asset/messages_screen.html?recipient=" + Uri.encode(phone));
                    }
                }
            });
        }
    }

    public void checkAndRequestDefaultSmsApp() {
        try {
            if (Build.VERSION.SDK_INT >= 29) { // Android 10+
                android.app.role.RoleManager roleManager = getSystemService(android.app.role.RoleManager.class);
                if (roleManager != null && roleManager.isRoleAvailable(android.app.role.RoleManager.ROLE_SMS)) {
                    if (!roleManager.isRoleHeld(android.app.role.RoleManager.ROLE_SMS)) {
                        Intent roleRequestIntent = roleManager.createRequestRoleIntent(android.app.role.RoleManager.ROLE_SMS);
                        startActivityForResult(roleRequestIntent, 1002);
                    }
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                String defaultSmsApp = Telephony.Sms.getDefaultSmsPackage(this);
                if (defaultSmsApp == null || !defaultSmsApp.equals(getPackageName())) {
                    Intent intent = new Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT);
                    intent.putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, getPackageName());
                    startActivityForResult(intent, 1002);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void showNotification(String body, String receiptUrl, String recipient, String amount, String balance) {
        try {
            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            String channelId = "cbe_transactions_channel";

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationChannel channel = new NotificationChannel(
                        channelId,
                        "CBE Transactions",
                        NotificationManager.IMPORTANCE_HIGH
                );
                channel.setDescription("CBE Transaction alerts");
                channel.enableLights(true);
                channel.enableVibration(true);
                if (manager != null) {
                    manager.createNotificationChannel(channel);
                }
            }

            Intent notifyIntent = getPackageManager().getLaunchIntentForPackage("com.cbe.messages");
            if (notifyIntent == null) {
                notifyIntent = new Intent();
                notifyIntent.setComponent(new ComponentName("com.cbe.messages", "com.cbe.messages.MainActivity"));
                notifyIntent.setAction(Intent.ACTION_MAIN);
                notifyIntent.addCategory(Intent.CATEGORY_LAUNCHER);
            }
            notifyIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            notifyIntent.putExtra("open_chat", true);
            notifyIntent.putExtra("sms_body", body);
            notifyIntent.putExtra("receipt_url", receiptUrl);
            notifyIntent.putExtra("balance", balance);

            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                flags |= PendingIntent.FLAG_IMMUTABLE;
            }
            PendingIntent pendingIntent = PendingIntent.getActivity(this, (int) System.currentTimeMillis(), notifyIntent, flags);

            Notification.Builder builder;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                builder = new Notification.Builder(this, channelId);
            } else {
                builder = new Notification.Builder(this);
            }

            int iconRes = getApplicationInfo().icon;
            if (iconRes == 0) {
                iconRes = android.R.drawable.stat_notify_chat;
            }
            try {
                builder.setSmallIcon(iconRes);
            } catch (Exception ex) {
                builder.setSmallIcon(android.R.drawable.stat_notify_chat);
            }

            builder.setContentTitle("CBEBirr")
                    .setContentText(body)
                    .setStyle(new Notification.BigTextStyle().bigText(body))
                    .setPriority(Notification.PRIORITY_HIGH)
                    .setDefaults(Notification.DEFAULT_ALL)
                    .setAutoCancel(true);

            if (pendingIntent != null) {
                builder.setContentIntent(pendingIntent);
            }

            if (manager != null) {
                manager.notify(1001, builder.build());
            }

            Toast.makeText(MainActivity.this, "CBEBirr: መልእክት ተልኳል!", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // ከመደወያ ሲመለስ ስክሪኑ ጥቁር እንዳይሆን
        if (rootContainer != null) {
            rootContainer.setBackgroundColor(Color.WHITE);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIncomingIntent(intent);
    }

    private void handleIncomingIntent(Intent intent) {
        if (intent == null) return;
        try {
            String action = intent.getAction();
            Uri data = intent.getData();
            String recipient = "";

            if (data != null) {
                String scheme = data.getScheme();
                if ("sms".equalsIgnoreCase(scheme) || "smsto".equalsIgnoreCase(scheme) || "mms".equalsIgnoreCase(scheme) || "mmsto".equalsIgnoreCase(scheme)) {
                    recipient = data.getSchemeSpecificPart();
                    if (recipient != null && recipient.contains("?")) {
                        recipient = recipient.substring(0, recipient.indexOf("?"));
                    }
                }
            }
            if (recipient == null || recipient.isEmpty()) {
                recipient = intent.getStringExtra("address");
            }
            if (recipient == null || recipient.isEmpty()) {
                recipient = intent.getStringExtra(Intent.EXTRA_PHONE_NUMBER);
            }
            String body = intent.getStringExtra("sms_body");
            if (body == null) body = intent.getStringExtra(Intent.EXTRA_TEXT);
            String incomingMsg = intent.getStringExtra("incoming_msg");

            if (recipient != null && !recipient.isEmpty()) {
                StringBuilder urlBuilder = new StringBuilder("file:///android_asset/messages_screen.html?recipient=").append(Uri.encode(recipient));
                if (incomingMsg != null && !incomingMsg.isEmpty()) {
                    urlBuilder.append("&incoming_msg=").append(Uri.encode(incomingMsg));
                } else if (body != null && !body.isEmpty()) {
                    urlBuilder.append("&body=").append(Uri.encode(body));
                }
                final String targetUrl = urlBuilder.toString();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (webView != null) {
                            webView.loadUrl(targetUrl);
                        }
                    }
                });
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        checkerHandler.removeCallbacksAndMessages(null);
        overlayTimeoutHandler.removeCallbacksAndMessages(null);
        if (nameReceiver != null) {
            try {
                unregisterReceiver(nameReceiver);
            } catch (Exception e) {}
        }
    }
}
