package ir.vil3ntec.tohid.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 *  CRM-15/SEC-10 (د۱۸) — «فرمانِ سرور ⇐ دستگاه» پس از حذفِ دادهٔ مشتری (`server/src/lib/customer-erasure-ledger.js`):
 *  نام، شماره‌ها، یادداشت، نشانی و پینِ تحویل و کدِ کارتِ پرونده‌هایی که سرور می‌گوید در دفترِ همین دستگاه پاک می‌شوند ([scrub])؛
 *  ذخیرهٔ عادی ⇒ op ⇒ دفترِ سرور و همهٔ دستگاه‌ها. سندهای مالی، سقف، گروه و ردیف‌های `customer_view` ِ سابقه (فقط شناسهٔ پرونده،
 *  مُهرِ STF-09) دست نمی‌خورند — حالا به پرونده‌ای بی‌نام اشاره می‌کنند. خالص؛ مرزِ سکو در `CustomerErasureLedgerAndroid.kt`.
 */
object CustomerErasureLedger {
  const val PATH = "/shop/customer-erasure/ledger"
  const val APPLIED = "/shop/customer-erasure/ledger/applied"
  /** نامِ پروندهٔ پاک‌شده (دادهٔ دفتر، همان در هر زبان؛ برنامه نامِ خالی را نمی‌پذیرد) */
  const val ERASED_NAME = "مشتریِ حذف‌شده"

  /** پاسخِ `GET` ⇒ شناسهٔ پرونده‌ها */
  fun ids(o: JsonObject): List<String> = (o["debtors"] as? JsonArray).orEmpty().mapNotNull {
    ((it as? JsonObject)?.get("debtorId") as? JsonPrimitive)?.takeIf { p -> p.isString }?.contentOrNull?.takeIf { s -> s.isNotBlank() }
  }

  /** پرونده دیگر دادهٔ شخصی ندارد (همان سنجهٔ سرور) */
  fun clean(d: Debtor): Boolean =
    d.phone.isBlank() && d.phone2.isNullOrBlank() && d.notes.isBlank() && d.deliveryAddress == null && d.deliveryPin == null && d.cardCode == null

  fun scrubbed(d: Debtor): Debtor =
    d.copy(name = ERASED_NAME, phone = "", phone2 = null, notes = "", deliveryAddress = null, deliveryPin = null, cardCode = null, marketingConsent = null)

  /** دفترِ پاک‌شده، یا `null` وقتی چیزی برای پاک کردن نیست (پرونده نیست یا از پیش پاک است) */
  fun scrub(d: ShopData, ids: Set<String>): ShopData? {
    if (ids.isEmpty() || d.debtors.none { it.id in ids && (!clean(it) || it.name != ERASED_NAME) }) return null
    return d.copy(debtors = d.debtors.map { if (it.id in ids) scrubbed(it) else it })
  }

  /** پرونده‌هایی که در این دفتر پاک‌اند (یا نیستند) ⇒ برای «انجام شد» ِ سرور */
  fun done(d: ShopData, ids: List<String>): List<String> {
    val byId = d.debtors.associateBy { it.id }
    return ids.filter { id -> byId[id]?.let(::clean) ?: true }
  }

  fun appliedBody(ids: List<String>): JsonObject = JsonObject(mapOf("debtorIds" to JsonArray(ids.map(::JsonPrimitive))))
}
