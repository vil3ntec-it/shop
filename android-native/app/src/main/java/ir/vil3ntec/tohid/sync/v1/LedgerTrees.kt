package ir.vil3ntec.tohid.sync.v1

import ir.vil3ntec.tohid.data.LedgerTables
import ir.vil3ntec.tohid.data.ShopData
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import ir.vil3ntec.tohid.platform.SoftRef
import ir.vil3ntec.tohid.platform.identityMap

/**
 *  PERF-LEDGER — درختِ JSON ِ دفتر برای `SyncCommit.record` (هر ذخیره)، بی رمزگذاریِ دوبارهٔ ردیفی که از ثبتِ پیشین
 *  دست نخورده است (`docs/PERF-LEDGER-fa.md`).
 *
 *  ── چرا ──────────────────────────────────────────────────────────────
 *  ذخیرهٔ یک فروش کلِ دفتر را با `LedgerDiff.toJson` به درخت می‌برد — دکانِ چندساله ده‌ها هزار فاکتور و صدها هزار
 *  قلم دارد و هر بار همه از نو رمزگذاری می‌شدند، برای سه ردیفِ تازه.
 *
 *  ── قاعده (و چرا نتیجه همان است) ─────────────────────────────────────
 *  ردیف‌های دفتر `data class` ِ تغییرناپذیرند و صفحه دفتر را با `copy` عوض می‌کند: ردیفِ دست‌نخورده **همان شیء** است.
 *  رمزگذاریِ ردیف تابعِ خالصِ خودِ ردیف است، پس شاخهٔ ردیفی که همان شیءِ ثبتِ پیشین است همان شاخهٔ پیشین است.
 *  فقط بر پایهٔ **همان شیء** (`===`)، هرگز برابری؛ هر چیزِ دیگری (کلیدها، ترتیب، فهرست‌های نام، شمارهٔ فاکتور) از
 *  همان رمزگذارِ `LedgerDiff.toJson` روی دفترِ بی ردیف می‌آید. پس خروجی همیشه برابرِ `LedgerDiff.toJson(data)` است —
 *  `LedgerTreesTest` همین را با دفترِ ساختگی، ویرایشِ وسط، حذف، جابه‌جایی و ردیفِ تکراری قفل کرده است.
 *
 *  نگه‌داری: فقط شاخه‌های آخرین دفتر (همان که سایهٔ نشسته هم دارد، `SyncCommit.seated`)؛ دفترِ تازه جای کهنه را می‌گیرد.
 *  فراخوان قفلِ موتور را دارد (`SyncV1Engine.record` ِ `@Synchronized`).
 */
internal class LedgerTrees {

  /** فهرستِ ردیف‌های ثبتِ پیشین و شاخه‌های همان ترتیب */
  private class Seen(val rows: List<*>, val tree: JsonArray)

  /**
   *  **نرم** نگه داشته (`SoftRef`): روی گوشیِ کم‌حافظه پیش از کمبودِ حافظه رها می‌شود و ثبتِ بعدی همه را از نو
   *  رمزگذاری می‌کند — همان راهِ پیشین.
   */
  private var seenRef = SoftRef(HashMap<String, Seen>())

  private val seen: HashMap<String, Seen>
    get() = seenRef.get() ?: HashMap<String, Seen>().also { seenRef = SoftRef(it) }

  fun toJson(data: ShopData): JsonObject {
    val seen = seen
    val bare = LedgerDiff.toJson(LedgerTables.bare(data))
    //  ⛔ کلیدی که رمزگذار ننوشت (نباید پیش بیاید: `encodeDefaults`) ⇒ همان راهِ همیشه، بی هیچ میان‌بُر
    if (LedgerTables.ALL.any { !bare.containsKey(it.key) }) return LedgerDiff.toJson(data)
    val out = LinkedHashMap<String, JsonElement>(bare)
    for (t in LedgerTables.ALL) out[t.key] = array(t, data, seen)
    return JsonObject(out)
  }

  /** همان کارِ [toJson] روی یک جدول؛ ردیفِ همان‌شیء ⇒ همان شاخه، بقیه رمزگذاریِ تازه */
  private fun <T> array(t: LedgerTables.Table<T>, data: ShopData, seen: HashMap<String, Seen>): JsonArray {
    val rows = t.rows(data)
    val was = seen[t.key]
    if (was != null && was.rows === rows) return was.tree
    val old = was?.rows
    val oldTree = was?.tree
    var byRow: MutableMap<Any?, JsonElement>? = null
    var misses = 0
    val out = ArrayList<JsonElement>(rows.size)
    for (i in rows.indices) {
      val r = rows[i]
      var e: JsonElement? = null
      if (old != null && oldTree != null) {
        if (i < old.size && old[i] === r) {
          e = oldTree[i]
        } else {
          //  جابه‌جایی (ردیفِ تازه در آغاز، حذفِ میانه): پس از چند ناهمخوانی، نگاشتِ همان‌شیء
          if (byRow == null && ++misses > POSITIONAL_MISSES) {
            byRow = identityMap<Any?, JsonElement>(old.size).also { m -> for (k in old.indices) m[old[k]] = oldTree[k] }
          }
          e = byRow?.get(r)
        }
      }
      out += e ?: LedgerDiff.rowJson(t.serializer, r)
    }
    return JsonArray(out).also { seen[t.key] = Seen(rows, it) }
  }

  internal companion object {
    private const val POSITIONAL_MISSES = 64

    /** برای آزمون: جدول‌هایی که میان‌بُر دارند — باید همان `LedgerDiff.COLLECTIONS` باشد */
    val KEYS: List<String> get() = LedgerTables.ALL.map { it.key }
  }
}
