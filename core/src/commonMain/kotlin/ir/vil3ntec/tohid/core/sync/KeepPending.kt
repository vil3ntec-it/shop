package ir.vil3ntec.tohid.core.sync

/**
 *  نشاندنِ opهای رسیده (Pull ِ Sync v1) بی بازنویسیِ تغییرِ محلی‌ای که هنوز در صف است — پرچمِ
 *  دکانِ `sync.keep_pending` (سرور در پاسخِ pull `keep_pending: true` می‌گذارد؛ پیش‌فرض خاموش).
 *
 *  ── مشکل ─────────────────────────────────────────────────────────────
 *  کاربر وسطِ یک Pull (پنجرهٔ واقعیِ انتظارِ پاسخ) قیمتِ کالا را ۲۰۰ می‌کند؛ op در صف می‌رود و سایه
 *  جلو. پاسخ می‌رسد با opِ دستگاهِ دیگر «۳۰۰» (پیش‌تر به سرور رسیده بود) و روی دفتر و سایه می‌نشیند ⇒
 *  دستگاه ۳۰۰ نشان می‌دهد. opِ ۲۰۰ بعداً می‌رود و چون «آخری که رسید» برنده است سرور و دستگاه‌های دیگر
 *  ۲۰۰ دارند، ولی opِ خودِ دستگاه هرگز به خودش برنمی‌گردد ⇒ ویرایشِ کاربر روی همین دستگاه گم و دفترش
 *  برای همیشه واگرا. همین برای حذف: حذفِ در صف و ویرایشِ رسیده ⇒ ردیفِ «شبح»؛ ویرایشِ در صف و حذفِ
 *  رسیده ⇒ ردیف از دفتر می‌رود ولی سرور با رسیدنِ ویرایش آن را زنده می‌کند.
 *
 *  ── قاعده ────────────────────────────────────────────────────────────
 *  هر opِ در صف **پس از** همهٔ opهای همین Pull به سرور می‌رسد (push پیش از pull است و op ِ تازه
 *  شماره‌ای بزرگ‌تر از سرِ دفترِ امروز می‌گیرد) و داوریِ سرور «آخری که رسید» است. پس دستگاه همین حالا
 *  همان چیزی را نشان می‌دهد که سرور پس از رسیدنِ صف خواهد داشت — op ِ رسیده پیش از نشاندن چنین می‌شود:
 *    • فیلدِ ساده‌ای که op ِ در صف (پس از آخرین حذفِ در صف) به آن مقدار داده ⇒ برداشته می‌شود
 *      (چه ساده چه `$inc` ِ رسیده: سرور اول آن را می‌گیرد، بعد مقدارِ ما را).
 *    • `$inc` ِ رسیده روی فیلدی که صف فقط `$inc` دارد ⇒ دست نمی‌خورد (جمع جابه‌جایی‌پذیر است).
 *    • مقدارِ سادهٔ رسیده روی فیلدی که صف فقط `$inc` دارد ⇒ «مقدار + جمعِ دلتاهای صف» (همان کارِ سرور).
 *    • ردیفی که آخرین op ِ در صفش حذف است ⇒ op ِ غیرِ حذفِ رسیده نشانده نمی‌شود.
 *    • ردیفی که در صف op ِ غیرِ حذف دارد ⇒ حذفِ رسیده نشانده نمی‌شود (سرور با رسیدنِ op ِ ما زنده‌اش
 *      می‌کند: «ویرایشِ پس از حذف ردیف را زنده می‌کند»).
 *  همین opِ تبدیل‌شده هم روی دفتر و هم روی سایه می‌نشیند (و در ژورنالِ `SyncCommit` همان می‌رود) ⇒
 *  تفاضلِ بعدی هیچ op ِ تازه‌ای نمی‌سازد. op ِ پذیرفته‌شده (رفته از صف) را این‌جا کاری نیست: آن مالِ
 *  پرچمِ سرورِ `sync.pull_own_newer` (#501) است.
 *
 *  قرینهٔ `keepPendingVerdicts` در `license/sync-core.js`؛ هر دو با
 *  `core/test-vectors/keep-pending-v1.json` سنجیده می‌شوند. Kotlinِ خالص (commonMain)، بی java.* و بی JSON:
 *  تبدیلِ op به [Op] و برگرداندنِ [Verdict] به op کارِ `LedgerDiff.keepPending` (برنامه) است.
 */
object KeepPending {

  /**
   *  یک op آن‌طور که این قاعده می‌بیند.
   *  @param set فیلدهایی که مقدارِ ساده می‌گیرند (هر چیزی جز `{"$inc": عدد}`)
   *  @param inc فیلد ⇐ دلتای `$inc`
   */
  data class Op(
    val table: String,
    val rowId: String,
    val delete: Boolean,
    val set: Set<String> = emptySet(),
    val inc: Map<String, Double> = emptyMap(),
  )

  sealed interface Verdict

  /** همان‌طور نشانده شود */
  data object Keep : Verdict

  /** اصلاً نشانده نشود */
  data object Drop : Verdict

  /**
   *  @param drop فیلدهایی که برداشته می‌شوند
   *  @param rebase فیلدِ ساده ⇐ دلتایی که به مقدارِ رسیده افزوده می‌شود (جمعِ `$inc` ِ صف)
   */
  data class Trim(val drop: Set<String>, val rebase: Map<String, Double>) : Verdict

  /** حالِ یک ردیف پس از opهای صف: [live] = آخرین op ِ صف غیرِ حذف است (وگرنه حذف) */
  private class Row {
    var live = false
    val set = HashSet<String>()
    val inc = HashMap<String, Double>()
  }

  private fun key(table: String, rowId: String) = "$table $rowId"

  /** حکمِ هر op ِ رسیده، به همان ترتیب — شرح سرِ همین فایل */
  fun verdicts(incoming: List<Op>, pending: List<Op>): List<Verdict> {
    if (pending.isEmpty()) return incoming.map { Keep }
    val rows = HashMap<String, Row>()
    for (p in pending) {
      val r = rows.getOrPut(key(p.table, p.rowId)) { Row() }
      if (p.delete) {
        r.live = false
        r.set.clear()
        r.inc.clear()
        continue
      }
      r.live = true
      for (f in p.set) {
        r.set += f
        r.inc.remove(f)
      }
      for ((f, d) in p.inc) if (f !in r.set) r.inc[f] = (r.inc[f] ?: 0.0) + d
    }
    return incoming.map { x ->
      val r = rows[key(x.table, x.rowId)] ?: return@map Keep
      when {
        x.delete -> if (r.live) Drop else Keep
        !r.live -> Drop
        else -> {
          val drop = (x.set + x.inc.keys).filterTo(LinkedHashSet()) { it in r.set }
          val rebase = LinkedHashMap<String, Double>()
          for (f in x.set) if (f !in r.set) r.inc[f]?.let { rebase[f] = it }
          if (drop.isEmpty() && rebase.isEmpty()) Keep else Trim(drop, rebase)
        }
      }
    }
  }
}
