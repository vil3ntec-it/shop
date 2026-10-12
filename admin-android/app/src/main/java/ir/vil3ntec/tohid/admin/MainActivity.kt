package ir.vil3ntec.tohid.admin

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import ir.vil3ntec.tohid.admin.ui.AdminRoot
import ir.vil3ntec.tohid.admin.ui.AdminTheme

/**
 *  برنامهٔ مدیریتِ توحید — فقط برای صاحبِ سامانه.
 *
 *  بسته‌اش از برنامهٔ مشتری جداست، پس هر دو کنارِ هم روی یک گوشی نصب
 *  می‌شوند و هیچ‌کدام به دادهٔ دیگری دست ندارد.
 *
 *  همهٔ اجازه‌ها سمتِ سرور سنجیده می‌شوند: توکنِ این برنامه نوعِ `admin`
 *  دارد و برنامهٔ مشتری هرگز چنین توکنی نمی‌گیرد. اگر کسی این فایل را
 *  باز کند هم چیزی گیرش نمی‌آید — نه رمزی اینجاست نه کلیدی.
 */
class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    //  توکن، شماره‌ها و اشتراکِ همه در این صفحه‌هاست: نه عکسِ صفحه، نه پیش‌نمایشِ «برنامه‌های اخیر»
    window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
    enableEdgeToEdge()
    setContent { AdminTheme { AdminRoot() } }
  }
}
