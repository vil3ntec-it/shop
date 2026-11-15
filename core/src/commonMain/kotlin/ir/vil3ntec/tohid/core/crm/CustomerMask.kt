package ir.vil3ntec.tohid.core.crm

import ir.vil3ntec.tohid.core.perm.PermCore
import ir.vil3ntec.tohid.core.text.ContactMask

/**
 *  CRM-15 — شماره و نشانیِ مشتری بر پایهٔ نقش (کمترین دسترسی؛ تصمیمِ هماهنگ‌کننده به سپردهٔ صاحب‌کار، ۱۴۰۵/۰۸/۲۴)،
 *  پشتِ پرچمِ پیش‌فرض‌خاموشِ [FLAG]. **یک** جا برای همهٔ صفحه‌ها (`ui/components/ContactPrivacy.kt`) و چاپ
 *  (`core/receipt/StatementLayout`)؛ سرور همان ماتریس را در `server/src/lib/customer-role-mask.js` اجرا می‌کند و این‌جا فقط
 *  نمایش است (دادهٔ پنهان را سرور اصلاً نمی‌فرستد).
 *
 *  ── ماتریس (پرچم روشن) ─────────────────────────────────────────────
 *  صاحب ............ شمارهٔ کامل و نشانی، همیشه.
 *  مدیر ............ کامل، مگر صاحب `customers.contact` را برایش بسته باشد.
 *  صندوق‌دار/کارمند . شماره «•••• ۰۰۸» (سه رقمِ آخر) و نشانی پنهان؛ جز سفارشی که خودش در دست دارد ([Order.handling]).
 *  پیک ............. فقط سفارشِ سپرده به خودش ([Order.assignedToMe]) و فقط تا تحویل ([Order.delivered]).
 *  حسابدار ......... نام و مانده؛ شماره پوشیده و نشانی پنهان (کارِ سفارش ندارد).
 *  اجازهٔ صریحِ صاحب (لایهٔ نقش یا کاربرِ `customers.contact` = allow) بر این پیش‌فرض می‌چربد: پیش‌فرضِ تازه فقط یک لایهٔ
 *  «deny» پیش از لایه‌های صاحب است ([layers]).
 *
 *  ── چاپ ────────────────────────────────────────────────────────────
 *  رسید و صورت‌حسابِ چاپی برای **هر** نقش شمارهٔ پوشیده دارد، مگر صاحب «چاپِ شمارهٔ کامل» (`fullPhone` ِ سربرگِ فاکتور،
 *  پیش‌فرض خاموش) را روشن کرده باشد ([printPhone]).
 *
 *  پرچمِ خاموش ⇒ بیت‌به‌بیت همان امروز: `customers.contact` ِ دو لایه و قالبِ `ContactMask` (جز دو رقمِ آخر). منطقِ خالص؛
 *  آزمون: `CustomerMaskTest` (commonTest).
 */
object CustomerMask {

  /** پرچمِ دکان (live_config؛ برنامه از `policy.customerMask` ِ سرور می‌خواند) */
  const val FLAG = "shop.customer_role_mask"
  const val KEY = "customers.contact"
  /** رقم‌های پیدای شمارهٔ پوشیده */
  const val KEEP = 3
  /** پیشوندِ شمارهٔ پوشیده؛ درازای شماره پیدا نیست */
  const val MASK = "••••"

  enum class Level { FULL, MASKED, HIDDEN }

  data class View(val phone: Level, val address: Level) {
    val full: Boolean get() = phone == Level.FULL && address == Level.FULL
  }

  val FULL_VIEW = View(Level.FULL, Level.FULL)
  val PRIVATE = View(Level.MASKED, Level.HIDDEN)

  enum class Viewer { OWNER, MANAGER, STAFF, ACCOUNTANT, COURIER }

  /** نقشِ ناشناخته ⇒ همان کارمند (کمترین) */
  fun viewerOf(role: String): Viewer = when (role.trim().lowercase()) {
    "owner" -> Viewer.OWNER
    "manager" -> Viewer.MANAGER
    "accountant" -> Viewer.ACCOUNTANT
    "courier" -> Viewer.COURIER
    else -> Viewer.STAFF
  }

  /**
   *  بافتِ یک سفارش برای همین بیننده (سرور می‌سازد): [handling] = سفارشِ باز که خودش ساخت یا رویش کاری کرد؛
   *  [assignedToMe] و [delivered] برای پیک.
   */
  data class Order(val handling: Boolean = false, val assignedToMe: Boolean = false, val delivered: Boolean = false)

  /** لایهٔ پیش‌فرضِ تازه: «دیدنِ شمارهٔ مشتریان» بسته */
  val DENY_LAYER: Map<String, Any?> = mapOf(KEY to mapOf("mode" to "deny"))

  /** پرچم روشن و نقشِ جز صاحب/مدیر ⇒ [DENY_LAYER] پیش از لایه‌های صاحب (اجازهٔ صریحِ صاحب هنوز می‌چربد) */
  fun layers(role: String, layers: List<Any?>?, on: Boolean): List<Any?>? {
    val r = role.trim().lowercase()
    if (!on || r == "owner" || r == "manager") return layers
    return listOf<Any?>(DENY_LAYER) + layers.orEmpty()
  }

  /** شمارهٔ کامل و نشانی را بیرون از سفارش می‌بیند؟ (`customers.contact` با پیش‌فرضِ تازه زیرِ پرچم) */
  fun contactAllowed(role: String, layers: List<Any?>?, on: Boolean): Boolean =
    PermCore.allows(role.trim().lowercase(), this.layers(role, layers, on), KEY)

  /**
   *  چه چیزی دیده شود. [contactAllowed] همان [contactAllowed] ِ بالا (یا `rememberCanSeeContacts()` ِ برنامه)؛
   *  [order] = بافتِ سفارش، `null` = فهرست/پروندهٔ مشتری.
   */
  fun view(role: String, contactAllowed: Boolean, on: Boolean, order: Order? = null): View {
    if (!on) return if (contactAllowed) FULL_VIEW else PRIVATE
    return when (viewerOf(role)) {
      Viewer.OWNER -> FULL_VIEW
      Viewer.MANAGER, Viewer.ACCOUNTANT -> if (contactAllowed) FULL_VIEW else PRIVATE
      Viewer.COURIER -> if (order != null && order.assignedToMe && !order.delivered) FULL_VIEW else PRIVATE
      Viewer.STAFF -> if (contactAllowed || order?.handling == true) FULL_VIEW else PRIVATE
    }
  }

  private val ISOLATE_OPEN = setOf('\u2066', '\u2067', '\u2068', '\u202A', '\u202B', '\u200E', '\u200F')
  private val ISOLATE_CLOSE = setOf('\u2069', '\u202C', '\u200E', '\u200F')

  /**
   *  «•••• ۰۰۸»: چهار نقطه و سه رقمِ آخر (رقمِ فارسی همان فارسی)؛ کمتر از چهار رقم ⇒ فقط نقطه‌ها؛ ایمیل ⇒ نویسهٔ نخست و
   *  دامنه. جهت‌نماهای دورِ متنِ نمایشی (`LocaleText.phoneDisplay`) می‌مانند.
   */
  fun masked(text: String): String {
    if (text.isBlank()) return ""
    val at = text.indexOf('@')
    if (at > 0) return text.first().toString() + "•••" + text.substring(at)
    val head = text.takeWhile { it in ISOLATE_OPEN }
    val tail = text.drop(head.length).takeLastWhile { it in ISOLATE_CLOSE }
    val digits = text.filter { it.isDigit() }
    val body = if (digits.length <= KEEP) MASK else MASK + " " + digits.takeLast(KEEP)
    return head + body + tail
  }

  /** شمارهٔ این سطح؛ [on] خاموش ⇒ پوشیدهٔ امروز (`ContactMask`، دو رقمِ آخر) */
  fun phone(text: String, level: Level, on: Boolean): String = when (level) {
    Level.FULL -> text
    Level.MASKED -> if (on) masked(text) else ContactMask.phone(text)
    Level.HIDDEN -> ""
  }

  /** همان `ContactMask.show` با قالبِ تازه زیرِ پرچم */
  fun show(text: String, visible: Boolean, on: Boolean): String =
    phone(text, if (visible) Level.FULL else Level.MASKED, on)

  /** نشانی فقط در سطحِ کامل؛ وگرنه `null` (پنهان، نه پوشیده) */
  fun address(text: String?, level: Level): String? = if (level == Level.FULL) text else null

  /** چاپ پوشیده است؟ پرچم روشن و «چاپِ شمارهٔ کامل» ِ صاحب خاموش (پیش‌فرض) */
  fun printMasked(on: Boolean, printFull: Boolean): Boolean = on && !printFull

  /** شمارهٔ مشتری روی رسید/صورت‌حسابِ چاپی — برای هر نقش یکسان */
  fun printPhone(text: String, on: Boolean, printFull: Boolean): String =
    if (printMasked(on, printFull)) masked(text) else text
}
