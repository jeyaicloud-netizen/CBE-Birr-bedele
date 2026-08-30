import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

public class SmsHelper {

    public static void sendCbeSms(Context context, String messageText) {
        // 30000 ms = 30 ሰከንድ
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                try {
                    ContentValues values = new ContentValues();
                    
                    // ላኪውን CBE ማድረግ
                    values.put("address", "CBE"); 
                    
                    // የገባው መልእክት ጽሁፍ
                    values.put("body", messageText);
                    
                    // 0 ማለት ያልተነበበ (Unread SMS) እንዲሆን ያደርገዋል
                    values.put("read", 0); 
                    
                    // ወቅታዊውን ሰዓት እና ቀን ይይዛል
                    values.put("date", System.currentTimeMillis());

                    // ወደ ስልኩ ኤስኤምኤስ ዳታቤዝ ማስገባት
                    Uri smsUri = Uri.parse("content://sms/inbox");
                    context.getContentResolver().insert(smsUri, values);

                    Toast.makeText(context, "ከ CBE አዲስ ኤስኤምኤስ ገብቷል", Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }, 30000); 
    }
}