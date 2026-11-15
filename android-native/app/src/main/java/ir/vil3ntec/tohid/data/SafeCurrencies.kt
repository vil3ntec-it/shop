package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.i18n.StrFx
import ir.vil3ntec.tohid.core.i18n.StrSafeCur
import ir.vil3ntec.tohid.core.pay.CashKeys
import ir.vil3ntec.tohid.core.safe.SafeCore
import ir.vil3ntec.tohid.core.safe.SafeCurrency
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.unitMoney
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 *  فهرستِ ارزهای گاوصندوق و نوشتنِ مبلغِ هر ارز — خالص (نه Context، نه ساعت). قاعدهٔ ارز در `core/safe/SafeCurrency`.
 *
 *  • فهرست = ارزهای همیشه‌آماده ([SafeCurrency.BUILT_IN]: افغانی، دالر، یورو، ریال، کلدار) + ارزهایی که دکان افزود
 *    (تنظیمِ **دکان** `tohid-policy:safe_currencies`، JSON ِ [encode]، همان همگام‌سازیِ تنظیماتِ دکان — `SafeCurrencyPref`).
 *  • افغانی همان نوشتهٔ امروز است (`؋`، بی اعشار)؛ ارزِ دیگر با کدش و اعشارِ واقعی (`12.50 USD`) — هیچ ارزی به دیگری
 *    تبدیل نمی‌شود و هیچ جا دو ارز با هم جمع نمی‌شوند.
 */
object SafeCurrencies {

  /** سقفِ ارزهای افزوده و درازای نام */
  const val MAX_CUSTOM = 20
  const val MAX_NAME = 40

  /** ارزی که دکان افزود: کد و نامی که خودش نوشت (داده، ترجمه نمی‌شود) */
  data class Item(val code: String, val name: String)

  // ── تنظیمِ دکان ─────────────────────────────────────────
  /** متنِ تنظیم ⇒ فهرست؛ خراب یا ردیفِ نادرست ⇒ همان ردیف کنار می‌رود (ارزهای همیشه‌آماده همیشه هستند) */
  fun decode(text: String?): List<Item> {
    if (text.isNullOrBlank()) return emptyList()
    val arr = runCatching { Json.parseToJsonElement(text) }.getOrNull() as? JsonArray ?: return emptyList()
    val out = ArrayList<Item>()
    for (e in arr) {
      val o = e as? JsonObject ?: continue
      val code = (o["code"] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: continue
      val name = (o["name"] as? JsonPrimitive)?.takeIf { it.isString }?.content?.trim().orEmpty()
      if (!SafeCurrency.isCode(code) || code in SafeCurrency.BUILT_IN || out.any { it.code == code } || name.isEmpty()) continue
      out += Item(code, name.take(MAX_NAME))
      if (out.size == MAX_CUSTOM) break
    }
    return out
  }

  fun encode(list: List<Item>): String = buildJsonArray {
    list.forEach { add(buildJsonObject { put("code", it.code); put("name", it.name) }) }
  }.toString()

  /** همهٔ ارزهای کادر، افغانی اول: همیشه‌آماده‌ها، افزوده‌های دکان و هر ارزی که دفتر دارد ولی فهرست ندارد */
  fun all(custom: List<Item>, inLedger: List<String> = emptyList()): List<String> =
    (SafeCurrency.BUILT_IN + custom.map { it.code } + inLedger.filter(SafeCurrency::isCode)).distinct()

  sealed interface Added {
    data class Ok(val list: List<Item>, val code: String) : Added
    data class Bad(val message: String) : Added
  }

  /** «+ ارزِ دیگر»: کد (حرفِ کوچک بزرگ می‌شود) سه حرفِ لاتین، تازه، با نام */
  fun add(custom: List<Item>, code: String, name: String): Added {
    val c = code.trim().uppercase()
    val n = name.trim()
    return when {
      !SafeCurrency.isCode(c) -> Added.Bad(StrSafeCur.badCode)
      c in all(custom) -> Added.Bad(StrSafeCur.duplicate)
      n.isEmpty() -> Added.Bad(StrSafeCur.nameRequired)
      n.length > MAX_NAME -> Added.Bad(StrSafeCur.nameTooLong)
      custom.size >= MAX_CUSTOM -> Added.Bad(StrSafeCur.tooMany(MAX_CUSTOM))
      else -> Added.Ok(custom + Item(c, n), c)
    }
  }

  /** ارزِ پیش‌فرضِ کادر: آخرین ارزِ این دستگاه اگر هنوز در فهرست است، وگرنه افغانی */
  fun pick(last: String?, choices: List<String>): String = last?.takeIf { it in choices } ?: SafeCurrency.AFN

  // ── نوشتن ─────────────────────────────────────────────
  /** نامِ ارز به زبانِ برنامه («دالر (USD)»)؛ ارزِ افزوده «نام (کد)»؛ ناشناخته همان کد */
  fun name(code: String, custom: List<Item> = emptyList()): String = when (code) {
    SafeCurrency.AFN -> StrSafeCur.afn
    "USD" -> StrFx.usd
    "EUR" -> StrSafeCur.eur
    "IRR" -> StrFx.irr
    "PKR" -> StrFx.pkr
    else -> custom.firstOrNull { it.code == code }?.let { "${it.name} (${it.code})" } ?: code
  }

  /** نشانهٔ کنارِ عدد: افغانی همان `؋` ِ امروز، ارزِ دیگر کدش */
  fun symbol(code: String): String = if (code == SafeCurrency.AFN) "؋" else code

  /** مبلغ به واحدِ کوچکِ [code] ⇒ نوشته؛ افغانی بیت‌به‌بیت همان `safeAfn` ِ امروز */
  fun format(minor: Long, code: String): String =
    if (code == SafeCurrency.AFN) "${money(minor / 100.0)} ؋"
    else "${unitMoney(SafeCurrency.major(minor, code))} $code"

  /** فقط عدد (برای نمودار و کادرِ درشت) — همان عددِ [format] بی نشانه */
  fun number(minor: Long, code: String): String =
    if (code == SafeCurrency.AFN) money(minor / 100.0) else unitMoney(SafeCurrency.major(minor, code))

  /**
   *  متنِ کادرِ مبلغ ⇒ واحدِ کوچکِ [code] (همان خواندنِ رقم‌به‌رقمِ `CashKeys.cashMinor`)؛ خالی ⇒ 0؛ نامعتبر، اعشاری که ارز
   *  ندارد (ریالِ ۱۲٫۵) یا بیش از سقفِ ردیف ⇒ null.
   */
  fun parse(text: String, code: String): Long? {
    val t = ir.vil3ntec.tohid.latinDigits(text).replace(",", "").replace("٬", "").trim()
    val cents = CashKeys.cashMinor(t) ?: return null
    val m = SafeCurrency.fromCents(cents, code) ?: return null
    return m.takeIf { it <= SafeCore.MAX_AMOUNT }
  }
}
