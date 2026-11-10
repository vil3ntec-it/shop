package ir.vil3ntec.tohid.core.shopper

import ir.vil3ntec.tohid.core.market.PublicOrder
import ir.vil3ntec.tohid.core.orders.DeliveryPrefs
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/*
 *  CHK-24 در برنامهٔ خرید — کارتِ «دستورِ تحویل» ِ پیگیری، همزادِ `renderPrefs` ِ `server/public/store/order.js`:
 *  «پیش از رسیدن زنگ بزنید»، «تحویل به…» و «نشانهٔ تازه» با همان درِ `POST /api/store/:slug/orders/:token/delivery` و
 *  `expectedVersion` ِ دیده‌شده (کهنه ⇒ ۴۰۹ ِ `version_conflict`، هرگز رونویسیِ بی‌صدا)؛ و «با کدام اسکناس…» (CHK-14) که
 *  سرور در همان در و با همان `checkTender` می‌پذیرد — گزینه‌ها همان [CheckoutRules.tenderOptions] برای جمعِ سفارش
 *  (بردارِ مشترکِ `core/test-vectors/cash-tender-v1.json`).
 *  ⛔ فقط با کلیدِ `deliveryPrefs` ِ پیگیری (پرچمِ `shop.orders.delivery_prefs`؛ خاموش ⇒ کارت نیست)؛ ویرایش فقط وقتی
 *     سرور `editable` می‌گوید (پیش از حرکتِ بسته). مبلغِ اسکناس هیچ اثری بر جمع ندارد و هرگز مبلغی که سرور رد می‌کند نمی‌رود.
 */

/** کلیدِ `deliveryPrefs` ِ پیگیری (`customerPrefs` ِ `lib/delivery-prefs.js`)؛ ⛔ نشانی و نشانه در نمای مشتری نیست */
@Serializable
data class CustomerDeliveryPrefs(
  val editable: Boolean = false,
  val version: Long = 0,
  val callBeforeArrival: Boolean = false,
  val leaveWith: String = "",
  /** اسکناسِ خودِ مشتری (یک‌صدم) یا null ⇒ «پول خرد لازم نیست» */
  val cashTenderMinor: Long? = null,
)

/** کادرهای کارت؛ [landmark] همیشه خالی آغاز می‌شود (مثلِ `op-lm` ِ وب) و فقط پُر رفته فرستاده می‌شود */
data class PrefsForm(
  val callBeforeArrival: Boolean = false,
  val leaveWith: String = "",
  val landmark: String = "",
  val cashTenderMinor: Long? = null,
)

/** پاسخِ ویرایش: نمای تازهٔ `order.deliveryPrefs` و «همان بود» (`unchanged`) */
class PrefsSaved(val prefs: CustomerDeliveryPrefs?, val unchanged: Boolean)

object PrefsRules {
  /** همان `text(v, 80)`/`text(v, 200)` ِ `parseEdit` ِ سرور (و `maxlength` ِ وب) */
  const val LEAVE_MAX = 80
  const val LANDMARK_MAX = 200

  /** کارت دیده می‌شود؟ کلیدِ سرور (پرچم)، ارسال، و سفارشی که ردشده/لغوشده نیست */
  fun shown(o: PublicOrder, p: CustomerDeliveryPrefs?): Boolean =
    p != null && o.fulfillment == CheckoutRules.DELIVERY && o.status !in TrackRules.STOP

  /** «با کدام اسکناس…»؟ همان شرطِ `checkTender` ِ سرور: ارسال با «نقد هنگامِ تحویل» */
  fun tenderShown(o: PublicOrder, p: CustomerDeliveryPrefs?): Boolean = shown(o, p) && o.paymentMethod == "cod"

  /** همان `parseTender` + `checkTender` ِ سرور برای جمعِ امروزِ سفارش */
  fun valid(tenderMinor: Long, totalMinor: Long): Boolean =
    tenderMinor > 0 && tenderMinor % 100 == 0L && DeliveryPrefs.tenderError(tenderMinor, totalMinor) == null

  /**
   *  گزینه‌ها: همان [CheckoutRules.tenderOptions] ِ جمعِ سفارش، به‌علاوهٔ اسکناسِ کنونی اگر هنوز درست است ولی در فهرست نیست
   *  (دکان شاید مبلغِ دیگری نوشته) تا ذخیرهٔ کادرِ دیگر آن را بی‌صدا پاک نکند.
   */
  fun tenders(o: PublicOrder, p: CustomerDeliveryPrefs?): List<Long> {
    if (!tenderShown(o, p)) return emptyList()
    val base = CheckoutRules.tenderOptions(o.totalMinor)
    val cur = p?.cashTenderMinor?.takeIf { it !in base && valid(it, o.totalMinor) } ?: return base
    return (base + cur).sorted()
  }

  /** نمای سرور ⇒ کادرها؛ اسکناسی که با جمعِ امروز دیگر درست نیست ⇒ «پول خرد لازم نیست» (همان `tenderOf` ِ ثبت) */
  fun formOf(o: PublicOrder, p: CustomerDeliveryPrefs): PrefsForm =
    PrefsForm(p.callBeforeArrival, p.leaveWith, "", p.cashTenderMinor?.takeIf { it in tenders(o, p) })

  /** آنچه واقعاً می‌رود (همان برش‌های سرور)؛ اسکناس فقط وقتی پرسیده می‌شود و یکی از [tenders] است */
  fun sent(o: PublicOrder, p: CustomerDeliveryPrefs, f: PrefsForm): PrefsForm = PrefsForm(
    f.callBeforeArrival, f.leaveWith.trim().take(LEAVE_MAX), f.landmark.trim().take(LANDMARK_MAX),
    f.cashTenderMinor?.takeIf { tenderShown(o, p) && it in tenders(o, p) },
  )

  /**
   *  بدنهٔ `POST …/delivery` از [sent]: همیشه «زنگ» و «تحویل به»؛ اسکناس فقط با «نقد هنگامِ تحویل» (null ⇒ برداشته)؛
   *  نقد نیست ولی اسکناسی مانده (پس از ثبت با حساب‌پی پرداخت شد) ⇒ null ِ صریح، همان وب (`store/order.js`)، وگرنه سرور
   *  `bad_cash_tender` می‌داد؛ نشانه فقط پُر؛ و نسخهٔ دیده‌شده — پس تکرارِ همین بدنه پس از پاسخِ گم‌شده یا ۴۰۹ است یا
   *  «همان بود»، نه ویرایشِ دوم.
   */
  fun body(o: PublicOrder, p: CustomerDeliveryPrefs, sent: PrefsForm): JsonObject {
    val d = LinkedHashMap<String, JsonElement>()
    d["callBeforeArrival"] = JsonPrimitive(sent.callBeforeArrival)
    d["leaveWith"] = JsonPrimitive(sent.leaveWith)
    if (tenderShown(o, p)) {
      d["cashTenderMinor"] = sent.cashTenderMinor?.let { JsonPrimitive(it) } ?: JsonNull
    } else if (p.cashTenderMinor != null) {
      d["cashTenderMinor"] = JsonNull
    }
    val out = LinkedHashMap<String, JsonElement>()
    out["delivery"] = JsonObject(d)
    if (sent.landmark.isNotEmpty()) out["address"] = JsonObject(mapOf("landmark" to JsonPrimitive(sent.landmark)))
    out["expectedVersion"] = JsonPrimitive(p.version)
    return JsonObject(out)
  }

  /**
   *  پس از پاسخِ گم‌شده یا ۴۰۹: نمای تازهٔ سرور همان است که فرستاده شد ⇒ «ثبت شد». نشانه در نمای مشتری نیست ⇒ با نشانه
   *  هرگز (پیامِ خطا می‌ماند و مشتری خودش می‌بیند).
   */
  fun landed(sent: PrefsForm, o: PublicOrder, fresh: CustomerDeliveryPrefs): Boolean =
    sent.landmark.isEmpty() && fresh.callBeforeArrival == sent.callBeforeArrival && fresh.leaveWith == sent.leaveWith &&
      (!tenderShown(o, fresh) || fresh.cashTenderMinor == sent.cashTenderMinor)
}
