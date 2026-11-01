package ir.vil3ntec.tohid.core.pick

/**
 *  میزِ جمع‌آوری: قفسهٔ انتظار (PCK-10) و جمع‌آوریِ چند سفارش با هم (PCK-12) — منطقِ خالصِ رابط، commonMain
 *  (بی `java.*`، راهِ آیفون). تصمیمِ نهایی با سرور است (`server/src/lib/pick-shelf.js`)؛ این‌جا فقط
 *  پیش‌سنجش تا کاربر پیش از رفتن به سرور بداند چه اشتباه است.
 *
 *  ⛔ خواندنِ برچسب دوقلوی `pick-shelf.parseLabel` است و هر دو با بردارِ `core/test-vectors/pick-shelf-v1.json`
 *  سنجیده می‌شوند (`PickDeskVectorsTest` و `server/test/pick-shelf.test.js`).
 */
object PickDesk {

  const val MIN_BATCH = 2
  const val MAX_BATCH = 10
  const val MAX_BAGS = 50

  /** برچسبِ کیسه: `VLNB-<شمارهٔ سفارش>-<کیسه>-<همهٔ کیسه‌ها>` */
  data class Label(val orderNo: Long, val bag: Int, val of: Int)

  private val LABEL = Regex("^VLNB-(\\d{1,9})-(\\d{1,2})-(\\d{1,2})$")
  private val SPACES = Regex("[\\s\\u00A0\\u200C\\u200F\\u200E\\uFEFF]+")

  /** رقمِ فارسی و فاصلهٔ بارکدخوان پاک، حروف بزرگ؛ نامعتبر ⇒ null */
  fun parseLabel(raw: String): Label? {
    val s = PickCore.latin(raw).replace(SPACES, "").uppercase()
    val m = LABEL.matchEntire(s) ?: return null
    val out = Label(m.groupValues[1].toLong(), m.groupValues[2].toInt(), m.groupValues[3].toInt())
    if (out.bag < 1 || out.of < 1 || out.bag > out.of || out.of > MAX_BAGS) return null
    return out
  }

  /** جای قفسهٔ انتظار به همان ترتیبِ طبیعیِ سرور (۲ پیش از ۱۰، A2 پیش از A10) */
  fun slotOrder(slots: List<String>): List<String> = slots.sortedWith { a, b -> PickCore.compareNatural(a, b) }

  /** سفارشی که در دسته جا دارد: «قبول‌شده» یا «در حال جمع‌آوری» */
  fun batchable(status: String): Boolean = status == "accepted" || status == "picking"

  /** دکمهٔ «ساختنِ دسته» فقط با ۲ تا ۱۰ سفارش */
  fun canBatch(count: Int): Boolean = count in MIN_BATCH..MAX_BATCH

  /** پیشرفتِ قفسهٔ یک سفارش: همهٔ کیسه‌ها ⇒ آماده */
  fun ready(scanned: Int, bags: Int): Boolean = bags > 0 && scanned >= bags
}
