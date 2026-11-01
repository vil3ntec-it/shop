package ir.vil3ntec.tohid.core.pay

import ir.vil3ntec.tohid.core.money.addExact

/**
 *  POS-40 — اصلاحِ روشِ پرداخت پس از فروش («نقد زدم، ولی HesabPay داد»).
 *
 *  ⛔ فاکتورِ ثبت‌شده ویرایش نمی‌شود (`shop.doc_chain`، S89: `payments` در `SALE_FIXED` است). اصلاح یک
 *  **سندِ جدا**ست — ردیفِ سابقهٔ `pay_fix` با فاکتور (`saleId`)، روشِ پیشین و تازه، مبلغ، `by`، `approvedBy`
 *  و دلیلِ اجباری — و فقط **جابه‌جایی** میانِ دو روشِ همان فاکتور است، پس جمعِ فاکتور همان می‌ماند. اثرش را
 *  صندوق با [apply] روی ردیف‌های همان فاکتور می‌بیند (نقدِ دخل و جمعِ هر روش).
 *
 *  ⛔ هم‌زادِ `payFix`، `payFixApply` و `payFixClosed` ِ `license/pay-core.js`؛ هر دو با بخشِ `payFix` ِ
 *  `core/test-vectors/pay-v1.json`. یکی را عوض کنی، دیگری و بردار هم.
 *
 *  قاعده‌ها:
 *   • فاکتورِ لغوشده، اینترنتی (پولش نزدِ درگاه یا پیک است) یا برگشت‌خورده (بازپرداخت به روش‌ها بسته است) نه.
 *   • روزِ بسته نه ([closed]): فاکتوری که در پنجرهٔ صندوقِ بسته‌ای است یا پیش از آخرین Z ِ همان روز ⇒ Z ثابت می‌ماند.
 *   • مجوزِ `sales.pay_fix` (perm-core): `allow` ⇒ تأییدکننده خودِ کاربر؛ `approve` ⇒ تأییدکننده‌ای جز خودش لازم؛
 *     هر چیزِ دیگر ⇒ رد.
 *   • فقط روش‌های شمردنیِ جدول (نقد، کیف پول، کارت، حواله، دیگر) — نه «به حسابِ مشتری» (بدهی دفترِ خودش را دارد)،
 *     نه اعتبار و کارتِ هدیه. روشِ تازه روشن، شمارهٔ مرجعِ روشِ مرجع‌دار و سقفِ روش.
 *   • مبلغ حداکثر همان که این لحظه در روشِ پیشین مانده (پس از اصلاح‌های پیشین).
 *   • یک بار: همان شناسه با همان محتوا ⇒ `duplicate`؛ با محتوای دیگر ⇒ `ID_CONFLICT`.
 *   • دو دستگاهِ آفلاین: [apply] اصلاح‌ها را به ترتیبِ (زمان، شناسه) می‌نشاند و اصلاحی را که دیگر جا ندارد
 *     کنار می‌گذارد (`skipped`) — بی وابستگی به ترتیبِ رسیدن، و هیچ روشی منفی نمی‌شود.
 *
 *  کاتلینِ خالص (commonMain): بی Double، بی ساعتِ دستگاه؛ هر مبلغ Long به پول.
 */
object PayFix {
  const val TYPE = "pay_fix"
  const val PERM = "sales.pay_fix"
  const val REASON_MIN = 3
  const val REASON_MAX = 120
  const val REF_MAX = 64

  /** یک ردیفِ پرداختِ فاکتور از نگاهِ صندوق (روش، مبلغ به پول) — همان `ShiftRow` */
  data class Row(val method: String, val amount: Long)

  /** سندِ ثبت‌شدهٔ اصلاح */
  data class Fix(
    val id: String,
    val saleId: String,
    val from: String,
    val to: String,
    val amount: Long,
    val at: Long,
    val by: String,
    val approvedBy: String,
    val reason: String,
    val ref: String? = null,
  )

  /** فاکتور از نگاهِ اصلاح: ردیف‌های پرداختِ ثبت‌شده (بی اصلاح) و سه حالتِ ردکننده */
  data class SaleInfo(
    val id: String,
    val rows: List<Row>,
    val cancelled: Boolean = false,
    val online: Boolean = false,
    val returned: Boolean = false,
  )

  data class Input(
    val id: String,
    val sale: SaleInfo,
    val from: String,
    val to: String,
    val amount: Long,
    val at: Long,
    val by: String?,
    /** `PermCore.evaluate(…, PERM).decision`: allow | approve | deny */
    val decision: String,
    val approvedBy: String? = null,
    val reason: String?,
    val ref: String? = null,
    /** روزِ فاکتور بسته است ([closed]) */
    val closed: Boolean = false,
    /** همهٔ اصلاح‌های ثبت‌شده (از هر دستگاه)؛ فقط همین فاکتور شمرده می‌شود */
    val existing: List<Fix> = emptyList(),
    val methods: List<PayMethod>? = null,
  )

  /** پنجرهٔ یک صندوق برای [closed]: باز شدن، بستن (null ⇒ باز) و روزِ باز شدن (`yyyy-MM-dd`) */
  data class Window(val openedAt: Long, val closedAt: Long?, val day: String)

  data class Applied(val rows: List<Row>, val applied: List<String>, val skipped: List<String>)

  private fun oneLine(s: String) = '\n' !in s && '\r' !in s

  /**
   *  روزِ فاکتور بسته است؟ آری اگر فاکتور در پنجرهٔ صندوقِ بسته‌ای است، یا پیش از آخرین بستنِ (Z) صندوق‌هایی
   *  است که همان روز باز شدند. صندوقی نبوده ⇒ نه.
   */
  fun closed(saleAt: Long, saleDay: String?, shifts: List<Window>): Boolean {
    if (shifts.any { it.closedAt != null && saleAt >= it.openedAt && saleAt <= it.closedAt }) return true
    val day = saleDay?.trim().orEmpty()
    if (day.isEmpty()) return false
    val last = shifts.filter { it.closedAt != null && it.day.trim() == day }.maxOfOrNull { it.closedAt!! } ?: return false
    return saleAt <= last
  }

  /**
   *  ردیف‌های فاکتورِ [saleId] پس از اصلاح‌هایی که تا [until] ثبت شدند، به ترتیبِ (زمان، شناسه). اصلاحی که
   *  نامعتبر است یا روشِ پیشینش دیگر آن مبلغ را ندارد کنار می‌رود (`skipped`). ترتیبِ روش‌ها همان نخستین دیدن؛
   *  روشِ صفر نمی‌آید.
   */
  fun apply(saleId: String, rows: List<Row>, fixes: List<Fix>, until: Long = Long.MAX_VALUE): Applied {
    val sums = LinkedHashMap<String, Long>()
    rows.forEach { r -> if (r.amount > 0) sums[r.method] = addExact(sums[r.method] ?: 0L, r.amount) }
    val applied = ArrayList<String>()
    val skipped = ArrayList<String>()
    val seen = HashSet<String>()
    fixes.filter { it.saleId == saleId && it.at <= until }
      .sortedWith(compareBy<Fix>({ it.at }, { it.id }))
      .forEach { f ->
        if (!seen.add(f.id)) return@forEach
        val have = sums[f.from] ?: 0L
        if (!posExact(f.amount) || f.from == f.to || f.from == Pay.ACCOUNT || f.to == Pay.ACCOUNT || f.amount > have) {
          skipped += f.id
          return@forEach
        }
        sums[f.from] = have - f.amount
        sums[f.to] = addExact(sums[f.to] ?: 0L, f.amount)
        applied += f.id
      }
    return Applied(sums.filter { it.value > 0 }.map { Row(it.key, it.value) }, applied, skipped)
  }

  fun check(input: Input): PayResult<Fix> {
    val id = input.id.trim()
    if (id.isEmpty()) return PayResult.Err("ID_REQUIRED")
    val sale = input.sale
    val saleId = sale.id.trim()
    if (saleId.isEmpty()) return PayResult.Err("SALE_REQUIRED")
    val by = input.by?.trim().orEmpty()
    val reason = input.reason?.trim().orEmpty()
    val refText = input.ref?.trim().orEmpty()
    input.existing.firstOrNull { it.id == id }?.let { old ->
      val same = old.saleId == saleId && old.from == input.from && old.to == input.to && old.amount == input.amount &&
        old.by == by && old.reason == reason && (old.ref ?: "") == refText
      return if (same) PayResult.Ok(old, duplicate = true) else PayResult.Err("ID_CONFLICT")
    }
    if (sale.cancelled) return PayResult.Err("SALE_CANCELLED")
    if (sale.online) return PayResult.Err("SALE_ONLINE")
    if (sale.returned) return PayResult.Err("SALE_RETURNED")
    if (input.closed) return PayResult.Err("DAY_CLOSED")
    val decision = input.decision
    if (decision != "allow" && decision != "approve") return PayResult.Err("PERM_DENIED")
    if (by.isEmpty()) return PayResult.Err("BY_REQUIRED")
    val approver = input.approvedBy?.trim().orEmpty()
    if (decision == "approve") {
      if (approver.isEmpty()) return PayResult.Err("APPROVAL_REQUIRED")
      if (approver == by) return PayResult.Err("SELF_APPROVAL")
    }
    if (reason.length < REASON_MIN) return PayResult.Err("REASON_REQUIRED")
    if (reason.length > REASON_MAX) return PayResult.Err("REASON_TOO_LONG")
    if (!oneLine(reason)) return PayResult.Err("BAD_REASON")

    val t = when (val r = Pay.table(input.methods)) { is PayResult.Ok -> r.value; is PayResult.Err -> return r }
    fun usable(m: String): PayMethod? = if (m == Pay.ACCOUNT) null else t[m]?.takeIf { it.kind in Pay.COUNTABLE }
    val from = usable(input.from) ?: return PayResult.Err("UNKNOWN_METHOD", field = "from")
    val to = usable(input.to) ?: return PayResult.Err("UNKNOWN_METHOD", field = "to")
    if (!to.enabled) return PayResult.Err("METHOD_DISABLED", field = "to")
    if (from.id == to.id) return PayResult.Err("SAME_METHOD")
    if (!posExact(input.amount)) return PayResult.Err("INVALID_AMOUNT")
    if (!posExact(input.at)) return PayResult.Err("INVALID_TIME")
    if (sale.rows.any { !nonNegExact(it.amount) }) return PayResult.Err("INVALID_AMOUNT", field = "rows")

    val now = apply(saleId, sale.rows, input.existing).rows
    fun have(m: String): Long = now.filter { it.method == m }.fold(0L) { a, r -> addExact(a, r.amount) }
    if (input.amount > have(from.id)) return PayResult.Err("FIX_EXCEEDS", method = from.id)
    val ref = if (to.kind == Pay.CASH) "" else refText
    if (to.needsRef && ref.isEmpty()) return PayResult.Err("REF_REQUIRED", method = to.id)
    if (ref.length > REF_MAX || !oneLine(ref)) return PayResult.Err("BAD_REF", method = to.id)
    if (to.limit != null && addExact(have(to.id), input.amount) > to.limit) return PayResult.Err("METHOD_LIMIT", method = to.id)

    val approvedBy = if (decision == "approve") approver else approver.ifEmpty { by }
    return PayResult.Ok(Fix(id, saleId, from.id, to.id, input.amount, input.at, by, approvedBy, reason, ref.ifEmpty { null }))
  }
}
