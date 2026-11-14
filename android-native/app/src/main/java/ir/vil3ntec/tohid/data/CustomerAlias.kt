package ir.vil3ntec.tohid.data

/**
 *  CRM-10 — «پیوندِ ادغام»: سندی که هنوز به مشتریِ ادغام‌شده اشاره می‌کند، زیرِ پروندهٔ ماندگار خوانده می‌شود.
 *
 *  ── چرا ───────────────────────────────────────────────────────────────
 *  روی همگام‌سازیِ v3 سندِ مالی (قرض، فاکتور) فقط افزودنی است؛ عوض کردنِ `debtorId` ِ آن حتی از سرور به صندوقِ
 *  تعارض می‌رود (`sync-v3-merge.js`، `append_only_edit`). پس ادغام آن‌جا **هیچ سندی را ویرایش نمی‌کند**:
 *  فقط `Debtor.mergedInto` ِ تکراری (جدولِ پایهٔ `debtors`، «آخرین نوشتن» ِ هر فیلد) پُر می‌شود
 *  (`CustomerMerge.merge(docs = false)`) و هر خواننده‌ای که مانده، صورت‌حساب، سنِ بدهی یا فاکتورهای مشتری را
 *  می‌سازد، از همین‌جا می‌پرسد. سندِ تکراری با همان مشتری، تاریخ و مبلغ می‌ماند (سابقه دست‌نخورده) و جمعِ پولِ
 *  دکان همان است؛ برداشتنِ `mergedInto` ادغام را بی هیچ سندِ تازه‌ای برمی‌گرداند.
 *
 *  روی v1 ادغام سندها را جابه‌جا می‌کند (همان رفتارِ پیشین) و `sweep` ردیفِ دیررسیده را می‌برد؛ این‌جا برای آن
 *  دفتر کاری نمی‌ماند (هیچ سندی روی ادغام‌شده نیست)، پس خواندن همان است.
 *
 *  قاعدهٔ ماندگار همان `CustomerCrm.resolve` است (زنجیره، مقصدِ ناپیدا، حلقه ⇒ کوچک‌ترین شناسه).
 *  ⛔ فقط خواندنی: هیچ دفتری با این نما ذخیره نمی‌شود.
 */
object CustomerAlias {

  /** سندهای یک پرونده: خودِ [id] و هر ادغام‌شده‌ای که به آن می‌رسد. پروندهٔ ادغام‌شده خودش سندی ندارد (همه زیرِ ماندگار) */
  class Of internal constructor(private val one: String?, private val ids: Set<String>?) {
    operator fun contains(debtorId: String?): Boolean = debtorId != null && (if (ids == null) debtorId == one else debtorId in ids)
  }

  private class Map2(val keeper: Map<String, String>, val members: Map<String, Set<String>>)

  @Volatile private var last: Pair<List<Debtor>, Map2>? = null

  private fun map(d: ShopData): Map2 {
    val src = d.debtors
    last?.let { if (it.first === src) return it.second }
    val merged = src.filter { !CustomerCrm.isActive(it) }
    val out = if (merged.isEmpty()) EMPTY else {
      val byId = src.associateBy { it.id }
      val keeper = merged.associate { it.id to CustomerCrm.resolve(byId, it.id) }.filter { (a, b) -> a != b }
      Map2(keeper, keeper.entries.groupBy({ it.value }, { it.key }).mapValues { (_, v) -> v.toSet() })
    }
    last = src to out
    return out
  }

  private val EMPTY = Map2(emptyMap(), emptyMap())

  /** هیچ مشتریِ ادغام‌شده‌ای نیست ⇒ هر شناسه پروندهٔ خودش است (راهِ تند) */
  fun none(d: ShopData): Boolean = map(d).keeper.isEmpty()

  /** پرونده‌ای که سندِ [debtorId] زیرِ آن خوانده می‌شود */
  fun keeperOf(d: ShopData, debtorId: String): String = map(d).keeper[debtorId] ?: debtorId

  /** سندهای پروندهٔ [id]: `x.debtorId in CustomerAlias.of(d, id)` */
  fun of(d: ShopData, id: String): Of {
    val m = map(d)
    if (m.keeper.isEmpty()) return Of(id, null)
    if (id in m.keeper) return Of(null, emptySet())
    return Of(id, m.members[id].orEmpty() + id)
  }

  /** همان `groupBy { debtorId }` با کلیدِ ماندگار — ترتیبِ ردیف‌ها همان ترتیبِ دفتر (پس جمع همان جمع) */
  fun <T> groupByKeeper(d: ShopData, rows: List<T>, debtorId: (T) -> String): Map<String, List<T>> {
    val keeper = map(d).keeper
    return if (keeper.isEmpty()) rows.groupBy(debtorId) else rows.groupBy { r -> debtorId(r).let { keeper[it] ?: it } }
  }
}
