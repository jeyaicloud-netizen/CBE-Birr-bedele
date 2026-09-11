package com.example.myapplication;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ussd extends AccessibilityService {

    public static volatile String lastFetchedName = null;
    public static volatile String lastFetchedAccount = null;
    public static volatile String currentAccountNumber = null;

    private static final Pattern[] NAME_PATTERNS = new Pattern[]{
            // 1. መደበኛ የ CBE Birr ፎርማት: "AccountName : ABEBE KEBEDE"
            Pattern.compile("AccountName\\s*[:\\-]\\s*([^\\r\\n]+)", Pattern.CASE_INSENSITIVE),
            // 2. በክፍተት: "Account Name : ABEBE KEBEDE"
            Pattern.compile("Account\\s*Name\\s*[:\\-]\\s*([^\\r\\n]+)", Pattern.CASE_INSENSITIVE),
            // 3. የደንበኛ ስም: "Customer Name : ABEBE KEBEDE"
            Pattern.compile("Customer\\s*Name\\s*[:\\-]\\s*([^\\r\\n]+)", Pattern.CASE_INSENSITIVE),
            // 4. የማስተላለፊያ: "transferring to ABEBE KEBEDE"
            Pattern.compile("transferring\\s+to\\s+([^(:\\r\\n]+)", Pattern.CASE_INSENSITIVE),
            // 5. በአማርኛ: "የተጠቃሚ ስም : አበበ ከበደ"
            Pattern.compile("የተጠቃሚ\\s*ስም\\s*[:\\-]\\s*([^\\r\\n]+)", Pattern.CASE_INSENSITIVE),
            // 6. አጭር ቅጽ: "To : ABEBE KEBEDE"
            Pattern.compile("^To\\s*[:\\-]\\s*([^\\r\\n]+)", Pattern.CASE_INSENSITIVE)
    };

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) return;

        AccessibilityNodeInfo source = getRootInActiveWindow();
        if (source == null) {
            source = event.getSource();
        }

        List<String> textList = new ArrayList<>();
        if (event.getText() != null) {
            for (CharSequence cs : event.getText()) {
                if (cs != null) textList.add(cs.toString());
            }
        }

        if (source != null) {
            extractAllTexts(source, textList);
        }

        for (String fullText : textList) {
            if (fullText == null || fullText.trim().isEmpty()) continue;

            String extractedName = findNameInText(fullText);
            if (extractedName != null && !extractedName.isEmpty()) {
                onNameSuccessfullyCaptured(extractedName, source);
                break;
            }
        }
    }

    private void extractAllTexts(AccessibilityNodeInfo node, List<String> list) {
        if (node == null) return;
        if (node.getText() != null && node.getText().length() > 0) {
            list.add(node.getText().toString());
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                extractAllTexts(child, list);
                child.recycle();
            }
        }
    }

    private String findNameInText(String text) {
        for (Pattern pattern : NAME_PATTERNS) {
            Matcher matcher = pattern.matcher(text);
            if (matcher.find()) {
                String candidate = matcher.group(1);
                return cleanRecipientName(candidate);
            }
        }

        // CBE Birr የ USSD መልስ ውስጥ "AccountName" ካለ በመስመር መፈተሽ
        if (text.contains("AccountName") || text.contains("Account Name")) {
            String[] lines = text.split("[\\r\\n]+");
            for (String line : lines) {
                for (Pattern p : NAME_PATTERNS) {
                    Matcher m = p.matcher(line);
                    if (m.find()) {
                        return cleanRecipientName(m.group(1));
                    }
                }
            }
        }
        return null;
    }

    private String cleanRecipientName(String raw) {
        if (raw == null) return "";
        String cleaned = raw.trim();

        // የማዕረግ ስሞችን ማፅዳት (Ato, W/ro, Mr, Mrs, Dr ወዘተ)
        cleaned = cleaned.replaceAll("(?i)^(Mr|Mrs|Ms|Ato|W/ro|Dr)\\.?\\s*", "");

        // አማራጮችን ማፅዳት (1. OK, 0. Cancel ወዘተ እንዳይገባ)
        cleaned = cleaned.replaceAll("(?i)\\b\\d+\\s*(OK|Cancel|Continue|Next|ሰርዝ|እሺ).*$", "");

        // ሥርዓተ-ነጥቦችን ማፅዳት
        cleaned = cleaned.replace(":", "").replace(";", "").trim();
        return cleaned;
    }

    private void onNameSuccessfullyCaptured(final String name, AccessibilityNodeInfo rootNode) {
        final String account = (currentAccountNumber != null && !currentAccountNumber.isEmpty())
                ? currentAccountNumber
                : MainActivity.currentQueriedAccount;

        lastFetchedName = name;
        lastFetchedAccount = account;

        // 1. Broadcast መላክ
        Intent intent = new Intent("CBE_NAME_RECEIVED");
        intent.putExtra("account_name", name);
        intent.putExtra("account_number", account);
        sendBroadcast(intent);

        // 2. MainActivity ክፍት ከሆነ በቀጥታ ማስገባት
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                if (MainActivity.instance != null) {
                    MainActivity.instance.injectNameToWeb(name, account);
                }
            }
        });

        // 3. የ USSD Dialogን ራሱ በራስ ሰርዝ/እሺ ብሎ መዝጋት (ስክሪን እንዳይቆለፍ)
        dismissUssdDialog(rootNode);

        // 4. ወደ CBE Birr Plus አፕ ተመልሶ ስክሪኑ ንፁህ ሆኖ እንዲታይ ማድረግ
        bringAppToFront();
    }

    private void dismissUssdDialog(AccessibilityNodeInfo root) {
        if (root == null) {
            performGlobalAction(GLOBAL_ACTION_BACK);
            return;
        }

        // Cancel / OK / ሰርዝ የሚሉ አዝራሮችን ፈልጎ ጠቅ ማድረግ
        String[] buttonLabels = new String[]{"Cancel", "ሰርዝ", "OK", "እሺ", "Dismiss", "Close"};
        boolean clicked = false;
        for (String label : buttonLabels) {
            List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByText(label);
            if (nodes != null && !nodes.isEmpty()) {
                for (AccessibilityNodeInfo btn : nodes) {
                    if (btn.isClickable()) {
                        btn.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                        clicked = true;
                        break;
                    } else if (btn.getParent() != null && btn.getParent().isClickable()) {
                        btn.getParent().performAction(AccessibilityNodeInfo.ACTION_CLICK);
                        clicked = true;
                        break;
                    }
                }
            }
            if (clicked) break;
        }

        // አዝራሩ ካልተገኘ በ Global Back መዝጋት
        if (!clicked) {
            performGlobalAction(GLOBAL_ACTION_BACK);
        }
    }

    private void bringAppToFront() {
        try {
            Intent bringToFront = new Intent(this, MainActivity.class);
            bringToFront.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(bringToFront);
        } catch (Exception e) {}
    }

    @Override
    public void onInterrupt() {}
}
