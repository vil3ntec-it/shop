package ir.vil3ntec.tohid.data

import android.content.Context
import ir.vil3ntec.tohid.core.hw.WeightBarcode
import org.json.JSONArray
import org.json.JSONObject

/**
 *  برچسبِ ترازو در صندوق (POS-29، سناریوی S04).
 *
 *  ── پیش‌فرض خاموش ─────────────────────────────────────────────────
 *  بی‌قاعده هیچ بارکدی «وزنی» خوانده نمی‌شود؛ بارکدِ معمولیِ ۲۰… همان
 *  راهِ همیشگی را می‌رود. دکان از «دستگاه‌های جانبی ⇐ ترازو» قاعده‌اش را
 *  روشن می‌کند (قاعده‌ها روی همین دستگاه‌اند، چون هر ترازو یک‌جور تنظیم
 *  می‌شود).
 *
 *  ── پیدا کردنِ کالا ───────────────────────────────────────────────
 *  کدِ ترازو (PLU) یکی از بارکدهای کالاست: یا خودِ کد (`00150`) یا
 *  پیشوند و کد (`2100150`). وزن به واحدِ کالا برمی‌گردد (کیلو ⇐ همان،
 *  گرم ⇐ ×۱۰۰۰)؛ کالای غیرِ وزنی رد می‌شود. مبلغ را همان `SalesEngine`
 *  و قاعدهٔ گردکردنِ همیشگی حساب می‌کند.
 *  ──────────────────────────────────────────────────────────────────
 */
object ScaleLabels {

  private const val KEY = "scale_label_rules"

  sealed interface Match {
    data class Weighed(val product: Product, val quantity: Double, val kg: Double) : Match
    data class Rejected(val message: String) : Match
  }

  fun rules(context: Context): List<WeightBarcode.Rule> = runCatching {
    val raw = context.getSharedPreferences("tohid", Context.MODE_PRIVATE).getString(KEY, null) ?: return emptyList()
    val arr = JSONArray(raw)
    (0 until arr.length()).map { arr.getJSONObject(it) }.map {
      WeightBarcode.Rule(
        prefix = it.getString("prefix"),
        itemDigits = it.optInt("itemDigits", 5),
        valueDigits = it.optInt("valueDigits", 5),
        kind = WeightBarcode.Kind.of(it.optString("kind")),
        decimals = it.optInt("decimals", 3),
      )
    }.filter { it.valid }
  }.getOrDefault(emptyList())

  fun saveRules(context: Context, rules: List<WeightBarcode.Rule>) {
    val arr = JSONArray()
    rules.filter { it.valid }.forEach {
      arr.put(JSONObject().put("prefix", it.prefix).put("itemDigits", it.itemDigits).put("valueDigits", it.valueDigits)
        .put("kind", it.kind.key).put("decimals", it.decimals))
    }
    context.getSharedPreferences("tohid", Context.MODE_PRIVATE).edit().putString(KEY, arr.toString()).apply()
  }

  /** `null` ⇒ برچسبِ ترازو نیست؛ راهِ معمولیِ بارکد */
  fun match(rules: List<WeightBarcode.Rule>, code: String, index: Map<String, String>, products: List<Product>): Match? {
    if (rules.isEmpty()) return null
    val clean = ir.vil3ntec.tohid.normalizeDigits(code.trim())
    //  کدی که خودش بارکدِ کالایی است برچسبِ ترازو نیست — کالای ثبت‌شده جلوتر است
    if (index.containsKey(clean)) return null
    return when (val r = WeightBarcode.parse(clean, rules)) {
      WeightBarcode.Result.NotScale -> null
      is WeightBarcode.Result.BadCheck -> Match.Rejected("برچسبِ ترازو رد شد — رقمِ کنترلِ «$clean» درست نیست")
      is WeightBarcode.Result.Hit -> {
        val id = index[r.itemCode] ?: index[r.rule.prefix + r.itemCode]
          ?: return Match.Rejected("کالای ترازو با کدِ «${r.itemCode}» ثبت نشده — این کد را به بارکدهای کالا بیفزایید")
        val p = products.firstOrNull { it.id == id } ?: return Match.Rejected("کالای «${r.itemCode}» پیدا نشد")
        if (!p.active) return Match.Rejected("«${p.name}» غیرفعال است")
        if (r.rule.kind == WeightBarcode.Kind.PRICE) {
          return Match.Rejected("برچسبِ قیمتیِ ترازو هنوز پشتیبانی نمی‌شود — ترازو را روی «وزن» بگذارید")
        }
        val kg = r.value
        if (kg <= 0.0) return Match.Rejected("وزنِ برچسب صفر است")
        val qty = when (p.unit) {
          "کیلو", "کیلوگرم" -> kg
          //  از عددِ خامِ برچسب، تا ۲۵ گرم ۲۵ بماند نه ۲۵٫۰۰۰۰۰۰۰۰۰۰۰۰۰۴
          "گرم" -> r.raw * 1000.0 / Math.pow(10.0, r.rule.decimals.toDouble())
          else -> return Match.Rejected("«${p.name}» کالای وزنی نیست (واحد: ${p.unit.ifBlank { "—" }})")
        }
        Match.Weighed(p, qty, kg)
      }
    }
  }
}
