package ir.vil3ntec.tohid.sync.v1

import ir.vil3ntec.tohid.core.platform.currentTimeMillis
import ir.vil3ntec.tohid.core.platform.locked
import ir.vil3ntec.tohid.core.platform.secureRandomBytes

/**
 *  ULID — شناسهٔ ردیف و شناسهٔ op.
 *
 *  ۴۸ بیت زمان + ۸۰ بیت تصادفیِ امن، در الفبای Crockford. مرتب بر اساس
 *  زمان است، پس opهایی که سه روز در صف مانده‌اند به همان ترتیبی که ساخته
 *  شده‌اند می‌روند.
 *
 *  ⚠️ **قرینهٔ دقیقِ `license/sync-core.js → ulid()`**. اگر یکی عوض شود
 *  و دیگری نه، شناسه‌های وب و اندروید دو شکل می‌شوند و مرتب‌سازیِ
 *  `op_id` روی سرور به‌هم می‌ریزد.
 *
 *  ⚠️ چند ULID در یک میلی‌ثانیه: بخشِ تصادفی **یکی بالا می‌رود**، نه
 *  اینکه دوباره تاس بیندازیم — وگرنه ترتیبِ ردیف‌های یک لحظه تصادفی
 *  می‌شد.
 */
object Ulid {

  private const val B32 = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"

  private var lastMs = -1L
  private var lastRand = ByteArray(0)

  fun next(nowMs: Long = currentTimeMillis()): String = locked { build(nowMs) }

  private fun build(nowMs: Long): String {
    val time = StringBuilder(10)
    var rest = nowMs
    val digits = CharArray(10)
    for (i in 9 downTo 0) {
      digits[i] = B32[(rest % 32).toInt()]
      rest /= 32
    }
    time.append(digits)

    val rnd: ByteArray
    if (nowMs == lastMs && lastRand.size == 10) {
      rnd = lastRand.copyOf()
      for (k in rnd.indices.reversed()) {
        val v = rnd[k].toInt() and 0xFF
        if (v < 255) { rnd[k] = (v + 1).toByte(); break }
        rnd[k] = 0
      }
    } else {
      rnd = secureRandomBytes(10)
    }
    lastMs = nowMs
    lastRand = rnd

    //  ده بایت = هشتاد بیت = دقیقاً شانزده نویسهٔ پنج‌بیتی
    val tail = StringBuilder(16)
    var acc = 0
    var bits = 0
    for (b in rnd) {
      acc = (acc shl 8) or (b.toInt() and 0xFF)
      bits += 8
      while (bits >= 5) {
        bits -= 5
        tail.append(B32[(acc ushr bits) and 31])
      }
    }
    return time.toString() + tail.toString()
  }
}
