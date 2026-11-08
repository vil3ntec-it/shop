package ir.vil3ntec.tohid.sync.v1

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import ir.vil3ntec.tohid.platform.identityMap

/**
 *  PERF-LEDGER — متنِ سایه (همان `JsonObject.toString()`) که هر ذخیره در prefs می‌نشاند، با رونوشتِ تکه‌های متنِ سایهٔ
 *  پیشین برای ردیف‌هایی که **همان شیء**‌اند (`docs/PERF-LEDGER-fa.md`).
 *
 *  ── چرا ──────────────────────────────────────────────────────────────
 *  سایه کلِ دفتر است و `toString` ِ تودرتوی kotlinx برای هر ردیف و هر فیلد رشتهٔ میانی می‌ساخت: دفترِ ۵۰هزار فاکتوری
 *  ۳ ثانیه روی JVM فقط برای همین، در هر فروش. ردیفِ دست‌نخورده همان شیءِ درختِ پیشین است (`LedgerTrees`)، پس متنش
 *  همان تکهٔ متنِ پیشین است — رونوشتِ نویسه‌ها، نه نوشتنِ دوباره.
 *
 *  ── چرا همان متن ─────────────────────────────────────────────────────
 *  نویسنده ([LedgerDiff.text]) وابسته به جا نیست: متنِ یک شاخه فقط تابعِ خودِ شاخه است. [spans] جای متنِ هر عنصرِ هر
 *  فهرستِ بالایی را در همان [text] نگه می‌دارد؛ عنصری که `===` ِ عنصرِ پیشین است همان تکه را می‌گیرد. هر ناهمخوانی ⇒
 *  نوشتنِ تازهٔ همان عنصر. `LedgerPerfEquivalenceTest` متن را نویسه‌به‌نویسه با `toString` برابر کرده است.
 */
internal class ShadowText private constructor(val text: String, val spans: Map<String, IntArray>) {

  companion object {
    private const val POSITIONAL_MISSES = 64

    /**
     *  @param prev متنِ سایهٔ پیشین با جای تکه‌هایش؛ [prevTree] همان درختی که [prev] از آن نوشته شد — هر دو یا هیچ
     */
    fun of(tree: JsonObject, prev: ShadowText? = null, prevTree: JsonObject? = null): ShadowText {
      val sb = StringBuilder((prev?.text?.length ?: 0) + 4096)
      val keys = HashMap<String, String>()
      val spans = HashMap<String, IntArray>()
      sb.append('{')
      var first = true
      for ((k, v) in tree) {
        if (!first) sb.append(',')
        first = false
        sb.append(keys.getOrPut(k) { JsonPrimitive(k).toString() })
        sb.append(':')
        if (v is JsonArray) {
          val old = if (prev != null) prevTree?.get(k) as? JsonArray else null
          spans[k] = array(sb, v, prev?.text, old, prev?.spans?.get(k), keys)
        } else {
          LedgerDiff.write(sb, v, keys)
        }
      }
      sb.append('}')
      return ShadowText(sb.toString(), spans)
    }

    /** `[`، عنصرها با `,`، `]`؛ جای هر عنصر: `[شروع₀، پایان₀، شروع₁، …]` */
    private fun array(
      sb: StringBuilder,
      arr: JsonArray,
      oldText: String?,
      old: JsonArray?,
      oldSpan: IntArray?,
      keys: HashMap<String, String>,
    ): IntArray {
      val span = IntArray(arr.size * 2)
      val usable = oldText != null && old != null && oldSpan != null && oldSpan.size == old.size * 2
      var byElement: MutableMap<JsonElement, Int>? = null
      var misses = 0
      sb.append('[')
      for (i in arr.indices) {
        if (i > 0) sb.append(',')
        val e = arr[i]
        val start = sb.length
        var j = -1
        if (usable) {
          if (i < old!!.size && old[i] === e) {
            j = i
          } else {
            if (byElement == null && ++misses > POSITIONAL_MISSES) {
              byElement = identityMap<JsonElement, Int>(old.size).also { m -> for (k in old.indices) m[old[k]] = k }
            }
            j = byElement?.get(e) ?: -1
          }
        }
        if (j >= 0) sb.appendRange(oldText!!, oldSpan!![2 * j], oldSpan[2 * j + 1]) else LedgerDiff.write(sb, e, keys)
        span[2 * i] = start
        span[2 * i + 1] = sb.length
      }
      sb.append(']')
      return span
    }
  }
}
