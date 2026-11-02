package androidx.core.content

import android.content.Context
import android.net.Uri
import java.io.File

/** shimِ iOS — در iOS پرونده‌ها مستقیم با `file://` داده می‌شوند (برنامهٔ دیگری برای «فراهم‌کننده» نیست) */
object FileProvider {
  fun getUriForFile(context: Context, authority: String, file: File): Uri = Uri.fromFile(file)
}
