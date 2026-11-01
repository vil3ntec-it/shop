package ir.vil3ntec.tohid.data

import android.content.Context
import ir.vil3ntec.tohid.core.hw.WeightBarcode
import ir.vil3ntec.tohid.core.pos.ScaleSale
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
 *  و قاعدهٔ گردکردنِ همیشگی حساب می‌کند. برچسبِ قیمتی (قاعده با `kind = price`)
 *  مقدار را از مبلغ ÷ قیمتِ کالا می‌گیرد و فقط وقتی پذیرفته است که مبلغِ ردیف همان
 *  مبلغِ برچسب شود (هستهٔ `ScaleSale`).
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

  /**
   *  `null` ⇒ برچسبِ ترازو نیست؛ راهِ معمولیِ بارکد. حساب فقط در هستهٔ `ScaleSale.label`
   *  (برچسبِ وزنی و قیمتی، `ScaleSaleTest`)؛ این‌جا فقط کالا و متن.
   */
  fun match(rules: List<WeightBarcode.Rule>, code: String, index: Map<String, String>, products: List<Product>): Match? {
    val clean = ir.vil3ntec.tohid.normalizeDigits(code.trim())
    val byId = products.associateBy { it.id }
    val m = ScaleSale.label(rules, clean, index::get) { id ->
      byId[id]?.let { ScaleSale.Item(it.id, it.name, it.unit, it.active, Math.round(it.salePrice * 100)) }
    } ?: return null
    return when (m) {
      is ScaleSale.Label.Weighed -> byId[m.item.id]?.let { Match.Weighed(it, m.quantity, m.kg) } ?: Match.Rejected(notFound(m.item.id))
      is ScaleSale.Label.Rejected -> Match.Rejected(message(m))
    }
  }

  private fun notFound(code: String) = "کالای «$code» پیدا نشد"

  private fun message(m: ScaleSale.Label.Rejected): String = when (m.why) {
    ScaleSale.Why.BAD_CHECK -> "برچسبِ ترازو رد شد — رقمِ کنترلِ «${m.arg}» درست نیست"
    ScaleSale.Why.UNKNOWN_CODE -> "کالای ترازو با کدِ «${m.arg}» ثبت نشده — این کد را به بارکدهای کالا بیفزایید"
    ScaleSale.Why.NOT_FOUND -> notFound(m.arg)
    ScaleSale.Why.INACTIVE -> "«${m.arg}» غیرفعال است"
    ScaleSale.Why.ZERO -> "وزن یا مبلغِ برچسب صفر است"
    ScaleSale.Why.NOT_WEIGHED -> "«${m.arg}» کالای وزنی نیست (واحد: ${m.item?.unit.orEmpty().ifBlank { "—" }})"
    ScaleSale.Why.NO_PRICE -> "«${m.arg}» قیمتِ فروش ندارد — برچسبِ قیمتی بی قیمتِ کالا خوانده نمی‌شود"
    ScaleSale.Why.PRICE_MISMATCH -> "برچسبِ قیمتیِ «${m.arg}» با قیمتِ کالا به همان مبلغ نمی‌رسد — کالا را دوباره روی ترازو بگذارید"
  }
}
