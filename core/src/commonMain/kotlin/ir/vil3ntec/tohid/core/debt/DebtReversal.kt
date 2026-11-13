package ir.vil3ntec.tohid.core.debt

/**
 *  S89 — سندِ معکوس به‌جای حذفِ قرض/رسیدِ ثبت‌شده (تصمیمِ صاحب‌کار ۱۴۰۵/۰۸/۲۳، پرسشِ ۲۳). منطقِ خالص و **یکتا**؛
 *  برنامه فقط ردیفِ دفتر را به [Row] می‌برد (`data/DebtReverse`).
 *
 *  قاعده‌ها:
 *    • فقط با کلیدِ `shop.doc_chain` (همهٔ دستگاه‌ها نسخهٔ تازه). خاموش ⇒ [Plan.Delete]، یعنی دقیقاً حذفِ امروز.
 *    • روشن: «حذف» ِ ردیفِ دستی (`kind` تهی) یک ردیفِ تازه است — نوعِ مخالف، همان مبلغ، `kind` = [KIND]، `refId` = اصل،
 *      شناسهٔ پایدارِ [idOf] (دو دستگاهِ بی‌اینترنت که هم‌زمان معکوس کنند یک ردیف می‌سازند). اصل می‌ماند و سابقه روشن است.
 *    • معکوسِ معکوس، اصلِ معکوس‌شده و ردیفِ ساختهٔ سندِ دیگر (نسیهٔ فاکتور، لغو، برگشت، امانی، اعتبار…) رد —
 *      سرورِ زنجیره‌دار هم حذفِ آن‌ها را نمی‌پذیرد؛ اصلاحشان از همان سند است.
 *    • جفتِ «اصل + معکوس» ([neutral]) جمعِ حساب را عوض نمی‌کند و از نمای مؤثر بیرون می‌رود: کهنگیِ قرض، سررسید،
 *      امتیاز و گزارش‌ها همان می‌شوند که حذفِ امروز می‌داد. ردیفِ معکوسِ ناجور (مبلغ/نوعِ دیگر، اصلِ نبود) جفت نیست و
 *      ردیفِ عادی می‌ماند — پس جمعِ خام و جمعِ مؤثر همیشه یکی‌اند.
 *    • دخل: رسیدِ **نقدیِ** معکوس‌شده از دخلِ **روزِ معکوس** کم می‌شود ([cashBack])؛ روزِ اصل دست نمی‌خورد (Z ِ بسته ثابت).
 *      ردیفِ معکوس خودش هرگز رسیدِ نقد نیست.
 */
object DebtReversal {

  /** `DebtTransaction.kind` ِ ردیفِ معکوس */
  const val KIND = "reversal"
  const val GIVE = "give"
  const val RECEIVE = "receive"

  /** پسوندِ شناسهٔ پایدار: `<اصل>-rv` */
  private const val SUFFIX = "-rv"

  /** نمای کوچکِ یک ردیفِ دفترِ قرض؛ [cash] = رسیدی که پولِ نقد به دخل آورد (`CashShift.isCashReceipt`) */
  data class Row(
    val id: String,
    val type: String,
    val amountMinor: Long,
    val kind: String? = null,
    val refId: String? = null,
    val cash: Boolean = false,
  )

  enum class Refusal { NOT_FOUND, IS_REVERSAL, ALREADY_REVERSED, SYSTEM_ROW }

  sealed interface Plan {
    /** کلیدِ خاموش: حذفِ امروز */
    data object Delete : Plan
    /** ردیفِ تازه: [id]، نوعِ [type]، همان مبلغِ اصل، `refId` = [refId] */
    data class Reverse(val id: String, val type: String, val amountMinor: Long, val refId: String) : Plan
    data class Refused(val why: Refusal) : Plan
  }

  fun isReversal(kind: String?): Boolean = kind == KIND

  fun idOf(originalId: String): String = originalId + SUFFIX

  private fun opposite(type: String): String = if (type == GIVE) RECEIVE else GIVE

  /** «حذف» ِ [target] با کلیدِ [chainOn]؛ [rows] همهٔ ردیف‌های دفتر (برای «پیش‌تر معکوس شده») */
  fun plan(chainOn: Boolean, target: Row?, rows: List<Row>): Plan {
    if (target == null) return Plan.Refused(Refusal.NOT_FOUND)
    if (!chainOn) return Plan.Delete
    if (isReversal(target.kind)) return Plan.Refused(Refusal.IS_REVERSAL)
    if (target.kind != null) return Plan.Refused(Refusal.SYSTEM_ROW)
    if (target.id in pairs(rows)) return Plan.Refused(Refusal.ALREADY_REVERSED)
    return Plan.Reverse(idOf(target.id), opposite(target.type), target.amountMinor, target.id)
  }

  /**
   *  حذفِ خودِ قرض‌دار همهٔ ردیف‌هایش را پاک می‌کند — با کلیدِ روشن این همان حذفِ سندِ ثبت‌شده است (سرور «rejected» می‌کند)،
   *  پس فقط قرض‌دارِ بی ردیف پاک می‌شود؛ کلیدِ خاموش همان امروز.
   */
  fun canDeleteDebtor(chainOn: Boolean, rows: Int): Boolean = !chainOn || rows == 0

  /**
   *  اصل ⇒ معکوسِ جفتش. جفت = ردیفِ [KIND] با `refId` ِ ردیفی موجود که خودش معکوس نیست، نوعِ مخالف و همان مبلغ.
   *  چند معکوس برای یک اصل (دو دستگاه با شناسهٔ دیگر) ⇒ فقط یکی جفت است: شناسهٔ پایدار، وگرنه کوچک‌ترین شناسه —
   *  مستقل از ترتیبِ ردیف‌ها.
   */
  fun pairs(rows: List<Row>): Map<String, String> {
    if (rows.none { isReversal(it.kind) }) return emptyMap()
    val byId = rows.associateBy { it.id }
    val out = HashMap<String, String>()
    for (r in rows) {
      if (!isReversal(r.kind)) continue
      val ref = r.refId ?: continue
      val o = byId[ref] ?: continue
      if (isReversal(o.kind) || o.type == r.type || o.amountMinor != r.amountMinor) continue
      val have = out[ref]
      if (have == null || better(r.id, have, ref)) out[ref] = r.id
    }
    return out
  }

  private fun better(candidate: String, have: String, ref: String): Boolean {
    val stable = idOf(ref)
    if (have == stable) return false
    if (candidate == stable) return true
    return candidate < have
  }

  /** شناسهٔ همهٔ ردیف‌های جفت‌شده (اصل و معکوس) — بیرون از نمای مؤثر */
  fun neutral(rows: List<Row>): Set<String> {
    val p = pairs(rows)
    if (p.isEmpty()) return emptySet()
    return HashSet<String>(p.size * 2).apply { addAll(p.keys); addAll(p.values) }
  }

  /** معکوس‌هایی که رسیدِ نقد را برگرداندند — همان مبلغ در لحظهٔ خودِ ردیفِ معکوس از دخل کم می‌شود */
  fun cashBack(rows: List<Row>): Set<String> {
    val p = pairs(rows)
    if (p.isEmpty()) return emptySet()
    val byId = rows.associateBy { it.id }
    return p.filter { (orig, _) -> byId[orig]?.let { it.type == RECEIVE && it.cash } == true }.values.toSet()
  }
}
