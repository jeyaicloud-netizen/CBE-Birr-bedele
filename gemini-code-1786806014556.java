import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {

    private static final int SMS_PERMISSION_CODE = 100;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // ወደ Inbox የሚገባው የCBE መልእክት
        String cbeMessage = "የሂሳብ ቁጥርዎ 1000****1234 በ ETB 1,500.00 ገቢ ሆኗል። ስለተጠቀሙብን እናመሰግናለን! - CBE";

        // ፈቃድ ቀደም ብሎ ከተሰጠ ቀጥታ ይሰራል
        if (checkSmsPermission()) {
            SmsHelper.sendCbeSms(this, cbeMessage);
        } else {
            // ፈቃድ ካልተሰጠ አንዴ ብቻ ይጠይቃል
            requestSmsPermission();
        }
    }

    private boolean checkSmsPermission() {
        int result = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_SMS);
        return result == PackageManager.PERMISSION_GRANTED;
    }

    private void requestSmsPermission() {
        ActivityCompat.requestPermissions(this, 
                new String[]{Manifest.permission.WRITE_SMS, Manifest.permission.READ_SMS}, 
                SMS_PERMISSION_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == SMS_PERMISSION_CODE && grantResults.length > 0) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // ፈቃዱ እንደተሰጠ በ30 ሰከንድ ውስጥ ኤስኤምኤሱን ይልካል
                String cbeMessage = "የሂሳብ ቁጥርዎ 1000****1234 በ ETB 1,500.00 ገቢ ሆኗል። ስለተጠቀሙብን እናመሰግናለን! - CBE";
                SmsHelper.sendCbeSms(this, cbeMessage);
            }
        }
    }
}