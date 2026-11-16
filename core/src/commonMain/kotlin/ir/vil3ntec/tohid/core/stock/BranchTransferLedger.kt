package ir.vil3ntec.tohid.core.stock

/*
 *  INV-11/INV-01 — آینهٔ انتقالِ میانِ شعبه در **دفترِ دستگاهِ** هر شعبه (فازِ ۴ ِ `docs/BRANCHES-fa.md` §۱۰، مهاجرتِ ۹۴۶).
 *
 *  موجودیِ برنامه دفترِ همان شعبه است (Σ ورود − فروش). انتقالِ میانِ شعبه برخط و سرورمرجع است و حرکت‌هایش همان دم در دفترِ
 *  موجودیِ سرورِ هر سو می‌نشیند؛ اگر دفترِ دستگاه آن را نداند، الف کالای فرستاده را هنوز «دارد» و ب کالای رسیده را نمی‌فروشد. پس
 *  پس از هر پاسخِ سرور (و هر بار که فهرست خوانده می‌شود) دفترِ همین شعبه **یک ردیفِ «اصلاح»** برای هر (انتقال، کالا، سو) می‌گیرد
 *  که مقدارش همیشه برابرِ اثرِ کنونیِ همان انتقال روی همین شعبه است:
 *
 *    فرستنده (الف)  −(فرستاده − برگشته)   — ارسال کم می‌کند؛ رد/لغوِ فرستاده آن‌چه برگشت را پس می‌دهد؛ کسری دیگر برنمی‌گردد
 *    گیرنده  (ب)    +رسیده               — هر گامِ دریافت
 *    پیش‌نویس و لغوِ پیش‌نویس ⇒ هیچ
 *
 *  ⛔ شناسهٔ ردیف ثابت است ([rowId]: انتقال + سو + کالا) و مقدار «تنظیم» می‌شود، نه «افزوده» ⇒ خواندنِ دوباره، دو دستگاهِ همان
 *     شعبه یا پاسخِ تکراریِ سرور هرگز دو بار کم/زیاد نمی‌کند ([plan] فقط تفاوت با ردیفِ موجود).
 *  ⛔ سرور همین ردیف را با نشانِ [ADJ_KIND] + `refId` = شناسهٔ انتقالِ واقعیِ همین شعبه در موجودیِ سرور **نمی‌شمرد**
 *     (`catalog.ingestTx` ⇐ `lib/branch-transfer-mirror.js`) — حرکت‌های `bxfer:` همان دم نشسته‌اند.
 *  منطقِ خالص (iOS هم)؛ آزمون: `BranchTransferLedgerTest` (commonTest).
 */
object BranchTransferLedger {

  /** `adjKind` ِ ردیفِ آینه (همان `BXFER_KIND` ِ `server/src/lib/catalog.js`) */
  const val ADJ_KIND = "branch_transfer"
  const val OUT = "out"
  const val IN = "in"

  /** یک نوشتن در دفتر: ردیفِ [rowId] برای کالای [productId] باید مقدارِ [milli] (هزارم، علامت‌دار) داشته باشد */
  data class Write(val rowId: String, val transferId: String, val productId: String, val side: String, val milli: Long)

  /** اثرِ کنونیِ انتقالِ [t] روی شعبهٔ [here] برای هر کالا (هزارم؛ منفی = بیرون) — شعبه‌ای که سرش نیست ⇒ هیچ */
  fun effect(t: BranchTransfer.Transfer, here: String): Map<String, Long> = when (here) {
    t.fromBranch.id -> if (t.sentAt == null || t.status == BranchTransfer.Status.DRAFT) emptyMap()
      else t.lines.associate { it.productId to -(it.sentMilli - it.returnedMilli) }
    t.toBranch.id -> if (t.status == BranchTransfer.Status.DRAFT) emptyMap() else t.lines.associate { it.productId to (it.receivedMilli ?: 0L) }
    else -> emptyMap()
  }

  /** سوی همین شعبه در انتقال */
  fun sideOf(t: BranchTransfer.Transfer, here: String): String? = when (here) {
    t.fromBranch.id -> OUT
    t.toBranch.id -> IN
    else -> null
  }

  /** FNV-1a ِ ۶۴ بیتی به هگز — شناسهٔ کوتاه و ثابتِ کالا در نامِ ردیف (شناسهٔ کالا هر نویسه‌ای می‌تواند باشد) */
  private fun fnv(s: String): String {
    var h = -0x340d631b7bdddcdbL
    for (b in s.encodeToByteArray()) {
      h = h xor (b.toLong() and 0xFF)
      h *= 0x100000001b3L
    }
    return h.toULong().toString(16).padStart(16, '0')
  }

  /** شناسهٔ ثابتِ ردیفِ آینه: `bxw-<انتقال>-<سو>-<کالا>` */
  fun rowId(transferId: String, side: String, productId: String): String = "bxw-$transferId-$side-${fnv(productId)}"

  /**
   *  نوشتن‌های لازم تا دفترِ شعبهٔ [here] اثرِ کنونیِ [t] را داشته باشد. [have] = مقدارِ ردیف‌های آینهٔ موجود (شناسهٔ ردیف ⇒ هزارم).
   *  ردیفِ نبوده با مقدارِ صفر ساخته نمی‌شود؛ ردیفِ موجود با همان مقدار دست نمی‌خورد.
   */
  fun plan(t: BranchTransfer.Transfer, here: String, have: Map<String, Long>): List<Write> {
    val side = sideOf(t, here) ?: return emptyList()
    return effect(t, here).mapNotNull { (pid, milli) ->
      val id = rowId(t.id, side, pid)
      val now = have[id]
      when {
        now == null && milli == 0L -> null
        now == milli -> null
        else -> Write(id, t.id, pid, side, milli)
      }
    }
  }

  /** همان برای چند انتقال (فهرستِ سرور)؛ هر انتقال یک بار */
  fun planAll(ts: List<BranchTransfer.Transfer>, here: String, have: Map<String, Long>): List<Write> =
    ts.distinctBy { it.id }.flatMap { plan(it, here, have) }
}
