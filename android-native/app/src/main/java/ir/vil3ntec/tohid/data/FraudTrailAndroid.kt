package ir.vil3ntec.tohid.data

import android.content.Context
import ir.vil3ntec.tohid.core.audit.LineVoid
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 *  RPT-09 — سمتِ پلتفرمِ «سابقهٔ ضدتقلب» (`FraudTrail`): تنظیمِ **همین دستگاه** کنارِ سیاست‌های
 *  دیگرِ دکان (`ShopPolicy`، پروندهٔ `tohid-policy` ‹`fraud_trail`›)، نامِ حسابِ همین دستگاه و
 *  سیم‌کشیِ «ردیفِ حذف‌شده از فاکتورِ باز» ⇐ سابقهٔ دکان. پیش‌فرض **خاموش** = همان رفتارِ امروز.
 */
object FraudTrailPref {

  private const val PREFS = "tohid-policy"
  private const val KEY = "fraud_trail"

  private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

  fun enabled(context: Context): Boolean = runCatching { prefs(context).getBoolean(KEY, false) }.getOrDefault(false)

  fun setEnabled(context: Context, on: Boolean) {
    prefs(context).edit().putBoolean(KEY, on).apply()
  }

  /** نامِ کاربرِ همین دستگاه برای لغو و مرجوعی وقتی سابقه روشن است؛ خاموش یا بی نام ⇒ `null` (دفتر همان main) */
  fun by(context: Context): String? {
    if (!enabled(context)) return null
    return LineVoid.who(runCatching { ir.vil3ntec.tohid.sync.SyncStore(context).accountName }.getOrNull())
  }

  /**
   *  هر ردیفی که کاربر از فاکتورِ باز حذف کرد ⇐ ردیفِ `line_delete` در سابقهٔ دکان (یک نوشتن برای همهٔ
   *  ردیف‌های یک کار، روی دفترِ همان لحظه و زیرِ قفلِ نوشتن — `ShopStore.appendAudits`). تنظیم در هر حذف
   *  سنجیده می‌شود: خاموش ⇒ هیچ نوشتنی. یک بار پس از بارِ دفتر (`AppRoot`).
   */
  fun attach(context: Context, cart: CartStore, store: ShopStore, scope: CoroutineScope) {
    val app = context.applicationContext ?: context
    cart.onLinesRemoved = { lines, draftKey ->
      if (enabled(app)) {
        val by = by(app)
        val now = System.currentTimeMillis()
        val today = ir.vil3ntec.tohid.todayIso()
        //  شناسه پیش از نوشتن، تا ساختنِ دوبارهٔ ردیف روی دفترِ تازه همان ردیف باشد
        val rows = lines.map { it to auditId() }
        scope.launch {
          store.appendAudits { d -> rows.map { (line, id) -> FraudTrail.lineDeleteRow(d, line, draftKey, by, today, now, id) } }
        }
      }
    }
  }
}
