package ir.vil3ntec.tohid.core.product

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

/**
 *  AI-11 — «جست‌وجو با عکس» برای کارمندِ دکان: منطقِ خالصِ صفحه (commonMain). سرور: `server/src/routes/shop-visual-search.js`
 *  (`GET` ⇒ «روشن است؟»، `POST` با بایت‌های عکس ⇒ فهرستِ «مشابه»).
 *
 *  ⛔ پرچمِ خاموشِ `shop.visual_search` ⇒ هر دو در ۴۰۴ ِ امروز ⇒ دکمه پنهان. هیچ‌چیز «روشن» فرض نمی‌شود: فقط
 *     `enabled: true` ِ پاسخِ موفق دکمه را نشان می‌دهد؛ «سرور گفت نه» پنهانش می‌کند و خطای گذرا همان حالِ پیشین را نگه می‌دارد.
 *  ⛔ هر پیشنهاد فقط «مشابه» است، هرگز «همان کالا»؛ هیچ پیشنهادی خودکار انتخاب یا به سبد افزوده نمی‌شود. زدنِ یک ردیف
 *     همان کارِ نتیجهٔ جست‌وجوی معمولی است: در صندوق کالا را در کادرِ جست‌وجو می‌نشاند و در فهرستِ کالاها بازش می‌کند.
 *  ⛔ نام، قیمت و عکس از دفترِ همین دستگاه؛ کالایی که این‌جا نیست (یا در صندوق غیرفعال است) نشان داده نمی‌شود.
 */
object VisualSearch {

  const val ROOT = "/shop/visual-search"

  /** همان `LABEL` ِ سرور — برچسبِ هر ردیف */
  const val LABEL = "مشابه"

  /** همان `RESULTS_DEFAULT`/`RESULTS_MAX` ِ سرور */
  const val LIMIT = 8
  const val LIMIT_MAX = 20

  fun searchPath(limit: Int = LIMIT): String = "$ROOT?limit=${limit.coerceIn(1, LIMIT_MAX)}"

  private val ID = Regex("^[A-Za-z0-9_.:-]{1,80}$")

  data class Hit(val productId: String, val name: String, val similarity: Int)

  data class Answer(val indexed: Int, val hits: List<Hit>)

  private fun str(o: JsonObject, k: String): String? = (o[k] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
  private fun int(o: JsonObject, k: String): Int? = (o[k] as? JsonPrimitive)?.takeIf { !it.isString }?.intOrNull

  /** پاسخِ `GET` — فقط `enabled: true` ِ صریح */
  fun enabled(o: JsonObject): Boolean = (o["enabled"] as? JsonPrimitive)?.takeIf { !it.isString }?.booleanOrNull == true

  /** پاسخِ `POST`: ردیفِ بی شناسهٔ درست یا تکراری کنار؛ ترتیبِ سرور می‌ماند */
  fun parse(o: JsonObject): Answer {
    val seen = HashSet<String>()
    val hits = (o["items"] as? JsonArray).orEmpty().mapNotNull { e ->
      val x = e as? JsonObject ?: return@mapNotNull null
      val id = str(x, "product_id")?.takeIf { ID.matches(it) && seen.add(it) } ?: return@mapNotNull null
      Hit(id, str(x, "name").orEmpty().trim().take(200), (int(x, "similarity") ?: 0).coerceIn(0, 100))
    }
    return Answer(indexed = (int(o, "indexed") ?: 0).coerceAtLeast(0), hits = hits.take(LIMIT_MAX))
  }

  /** کالای همین دستگاه برای ردیف */
  data class Local(val name: String, val price: Double, val unit: String, val photo: Boolean, val active: Boolean)

  data class Suggestion(
    val productId: String,
    val name: String,
    val price: Double,
    val unit: String,
    val photo: Boolean,
    val similarity: Int,
    /** همیشه «مشابه» */
    val label: String = LABEL,
  )

  /** کجا: صندوق (فقط کالای فعال، مثلِ کاتالوگِ فروش) یا فهرستِ کالاها */
  enum class Place { TILL, LIST }

  fun suggestions(a: Answer, place: Place, local: (String) -> Local?): List<Suggestion> = a.hits.mapNotNull { h ->
    val p = local(h.productId) ?: return@mapNotNull null
    if (place == Place.TILL && !p.active) return@mapNotNull null
    Suggestion(h.productId, p.name.ifBlank { h.name }, p.price, p.unit, p.photo, h.similarity)
  }

  /** زدنِ یک ردیف — فقط همان کارِ نتیجهٔ جست‌وجوی معمولی؛ هیچ گونه‌ای برای «افزودن به سبد» ندارد */
  sealed interface Tap {
    /** صندوق: کالا در کادرِ جست‌وجو می‌نشیند و فروشنده خودش آن را می‌زند */
    data class Search(val query: String) : Tap

    /** فهرستِ کالاها: همان بازکردنِ کالا */
    data class Open(val productId: String) : Tap
  }

  fun tap(s: Suggestion, place: Place): Tap = when (place) {
    Place.TILL -> Tap.Search(tillText(s.name))
    Place.LIST -> Tap.Open(s.productId)
  }

  /**
   *  کادرِ جست‌وجوی صندوق پیشوندِ شمارِ POS-03 را می‌خواند («۳*نام» ⇒ هر لمسِ کاشی سه تا)؛ نامی که خودش با رقم آغاز می‌شود
   *  («3x3 کابل»، «۲*۴ چوب») پیش از این همان پیشوند خوانده می‌شد و لمسِ بعدی چند تا می‌افزود ⇒ «1*» ِ صریح پیشش (یک دانه از
   *  همان نام). نامِ دیگر دست نمی‌خورد.
   */
  fun tillText(name: String): String {
    val n = name.trim()
    return if (n.firstOrNull()?.isDigit() == true) "1*$n" else name
  }

  /**
   *  دکمه دیده شود؟ [enabled] پاسخِ موفقِ سرور (null = نیامد)؛ [refused] «سرور گفت نه» (۴۰۳/۴۰۴/۴۰۱).
   *  خطای گذرا (شبکه) ⇒ همان [before].
   */
  fun visibleAfter(before: Boolean, enabled: Boolean?, refused: Boolean): Boolean = when {
    enabled != null -> enabled
    refused -> false
    else -> before
  }

  // ── برگهٔ نتیجه ──
  enum class Phase { IDLE, SENDING, DONE, FAILED }

  data class Sheet(
    val phase: Phase = Phase.IDLE,
    val rows: List<Suggestion> = emptyList(),
    val indexed: Int = 0,
    val error: String? = null,
  ) {
    val open: Boolean get() = phase != Phase.IDLE
  }

  fun sending(): Sheet = Sheet(Phase.SENDING)

  fun answered(a: Answer, place: Place, local: (String) -> Local?): Sheet =
    Sheet(Phase.DONE, suggestions(a, place, local), a.indexed)

  fun failed(message: String): Sheet = Sheet(Phase.FAILED, error = message)
}
