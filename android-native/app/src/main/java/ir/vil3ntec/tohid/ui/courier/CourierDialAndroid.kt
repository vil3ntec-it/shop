package ir.vil3ntec.tohid.ui.courier

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * مرزِ پلتفرمِ «تماس با مشتری» (DLV-02): فقط صفحهٔ شماره‌گیرِ گوشی باز می‌شود و تماس را خودِ پیک
 * می‌زند. شماره جایی نگه داشته نمی‌شود. برنامهٔ تماس نباشد ⇒ استثنا، و فراخواننده پیام می‌دهد.
 */
internal fun openDialer(context: Context, phone: String) {
  context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(phone))))
}
