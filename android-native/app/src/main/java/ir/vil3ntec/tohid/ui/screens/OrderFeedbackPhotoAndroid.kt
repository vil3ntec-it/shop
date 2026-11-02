package ir.vil3ntec.tohid.ui.screens

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import ir.vil3ntec.tohid.Jalali

/*
 *  مرزِ پلتفرمِ «گزارش‌ها و نظرها» (NTF-07/09): رمزگشاییِ عکسِ گزارش — همان راهِ رسانهٔ چتِ دکان
 *  (`ShopChat.loadImage`؛ کامپیوتر: shim ِ `BitmapFactory`) — و زمانِ محلی به تقویمِ خورشیدی.
 */

internal fun feedbackImage(bytes: ByteArray): ImageBitmap? =
  BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()

/** «1405/8/11 14:05»؛ زمانِ نامعلوم ⇒ خالی */
internal fun feedbackStamp(ms: Long): String {
  if (ms <= 0) return ""
  val cal = java.util.Calendar.getInstance().apply { timeInMillis = ms }
  val j = Jalali.of(cal.get(java.util.Calendar.YEAR), cal.get(java.util.Calendar.MONTH) + 1, cal.get(java.util.Calendar.DAY_OF_MONTH))
  val hh = cal.get(java.util.Calendar.HOUR_OF_DAY).toString().padStart(2, '0')
  val mm = cal.get(java.util.Calendar.MINUTE).toString().padStart(2, '0')
  return "${j.year}/${j.month}/${j.day} $hh:$mm"
}
