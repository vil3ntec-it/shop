package ir.vil3ntec.tohid.data

import android.content.Context
import ir.vil3ntec.tohid.data.repo.Backend
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 *  پرچمِ ادغامِ سه‌طرفهٔ دفتر — `shop.ledger_merge` در `live_config` ِ سرور. **از ۱۴۰۵/۰۸/۱۸ پیش‌فرض روشن**
 *  (`docs/DATA-SAFETY-fa.md`: خاموش ⇒ ذخیرهٔ صفحهٔ کهنه فروشِ دستگاهِ دیگر را «حذف» می‌کرد؛ PR #407، ذخیرهٔ کهنه).
 *
 *  روشن ⇒ همتاها، سنجشِ دیسک و ادغامِ سه‌طرفه (`LedgerMerge`، `LedgerFollow`). خاموش (فقط با `false` ِ صریحِ
 *  `live_config` برای همین دکان یا `all`) ⇒ همان رفتارِ پیشین: هر `ShopStore` فقط دفترِ حافظهٔ خودش را دارد و
 *  `save` دفترِ صفحه را کامل می‌نویسد.
 *
 *  ── سازگاری ──────────────────────────────────────────────────────
 *  • برنامه با `?v=3` می‌پرسد (سرور `v >= 2` را یکی می‌بیند؛ `v=3` فقط `shadowFile` را می‌افزاید)؛ سرورِ تازه برای همین درخواست پیش‌فرضِ روشن را می‌گوید و پاسخ `v: 2` دارد. برنامهٔ
 *    کهنه (بی `v`) از سرور همان پیش‌فرضِ خاموشِ پیشین را می‌گیرد — رفتارش بیت‌به‌بیت همان می‌ماند.
 *  • سرورِ کهنه (پاسخِ بی `v`، یا ۴۰۴) پیش‌فرضِ خاموشِ خودش را می‌گوید؛ آن «خاموش» تصمیمِ صاحب‌کار نیست ⇒ همان
 *    پیش‌فرضِ روشن. فقط `enabled: true` ِ صریح از سرورِ کهنه نوشته می‌شود.
 *  • کلیدِ تازه (`on2:`): `false` ای که نسخهٔ پیشین از پیش‌فرضِ کهنهٔ سرور نگه داشته بود دیگر خوانده نمی‌شود.
 *
 *  تنها کارِ شبکه پرسیدنِ خودِ پرچم است (`GET /shop/ledger-merge/config`)، با اتصالِ سوکتِ زنده
 *  و حداکثر هر [CHECK_MS] یک بار — همان الگوی `ReceiptFlag`. مقدار به دکان بسته است.
 */
object LedgerMergeFlag {

  private const val PREFS = "tohid-ledger-merge"
  //  `v=3`: همان پاسخِ `v=2` و کلیدِ `shadowFile` (پرچمِ `shop.sync_shadow_file`، `sync/v1/ShadowFileFlag`)
  private const val CONFIG = "/shop/ledger-merge/config?v=3"

  /** هر چند وقت یک بار «روشن است؟» دوباره پرسیده شود */
  const val CHECK_MS = 10 * 60_000L

  /** پیش از نخستین پاسخِ سرور (و هر خطای خواندن): روشن */
  const val DEFAULT_ON = true

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
  private fun key(context: Context) = "on2:" + LedgerOwner.currentShop(context).ifBlank { "-" }

  fun enabled(context: Context): Boolean =
    runCatching { prefs(context).getBoolean(key(context), DEFAULT_ON) }.getOrDefault(DEFAULT_ON)

  /** مقدارِ پرسیده‌شده از سرور برای دکانِ همین حالا (و آزمون‌ها) */
  fun set(context: Context, on: Boolean) {
    prefs(context).edit().putBoolean(key(context), on).apply()
  }

  /**
   *  پاسخِ `GET /config` ⇒ مقدارِ نوشتنی، یا `null` (هیچ: همان مقدارِ پیشین/پیش‌فرض). سرورِ تازه (`v >= 2`) هر دو
   *  را می‌گوید؛ سرورِ کهنه فقط «روشن» ِ صریح را (خاموشِ آن همان پیش‌فرضِ کهنه است).
   */
  fun decide(enabled: Boolean?, version: Int?): Boolean? = when {
    enabled == null -> null
    (version ?: 1) >= 2 -> enabled
    enabled -> true
    else -> null
  }

  /** اتصالِ سوکتِ زنده (`hello`): پرچم را بپرس (نه زودتر از [CHECK_MS]) */
  fun kick(context: Context, now: Long = System.currentTimeMillis()) {
    val app = context.applicationContext
    val at = "${key(app)}:at"
    if (now - prefs(app).getLong(at, 0L) < CHECK_MS) return
    prefs(app).edit().putLong(at, now).apply()
    scope.launch {
      val body = runCatching { Backend.api(app).get(CONFIG) }.getOrNull() ?: return@launch
      if (body["enabled"] != null) ir.vil3ntec.tohid.sync.v1.ShadowFileFlag.set(app, ir.vil3ntec.tohid.sync.v1.ShadowFileFlag.decide(body))
      val on = decide(body["enabled"]?.jsonPrimitive?.booleanOrNull, body["v"]?.jsonPrimitive?.intOrNull) ?: return@launch
      set(app, on)
    }
  }
}
