package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.i18n.StrLine
import ir.vil3ntec.tohid.core.i18n.StrSale
import ir.vil3ntec.tohid.core.pos.LinePricing
import ir.vil3ntec.tohid.numberInvalid
import ir.vil3ntec.tohid.parseNumber
import kotlin.math.roundToLong

/**
 *  قیمتِ ردیف در صندوق — POS-08، POS-10، POS-20، POS-21 (هسته: `core/…/pos/LinePricing.kt`).
 *
 *  ⛔ پیش‌فرض **خاموش**، تنظیمِ همین دستگاه (`LinePolicyPrefs`). خاموش ⇒ صندوق عدد به عدد همان
 *  امروز: قیمتِ دستی آزاد، قلمِ آزاد بی سقف و سقفِ تخفیف فقط روی تخفیفِ فاکتور.
 *
 *  روشن:
 *   • قیمتِ دستیِ زیرِ فهرست و تخفیفِ ردیف (درصد یا مبلغ) هم تخفیف‌اند و با همان سقفِ نقش و
 *     «درخواستِ تأیید با PIN» ِ تخفیفِ فاکتور سنجیده می‌شوند (`DiscountGate.basis`)؛ کسی که
 *     تخفیف برایش «ممنوع» است قیمتِ ردیف را پایین نمی‌آورد؛
 *   • «قاعدهٔ جهت» (هر دو / فقط بالا / فقط پایین / هیچ) و سقفِ قیمتِ قلمِ آزاد برای همه جز
 *     صاحبِ دکان.
 *
 *  خالص — بی `Context`؛ خواندن و نوشتنِ تنظیم در فایلِ مرزیِ `LinePolicyAndroid.kt`.
 */
object LinePolicy {

  /** سیاستِ یک کاربر از تنظیم و نقشش */
  fun policy(on: Boolean, role: String, direction: LinePricing.Direction, freeLineCapMinor: Long?, mayDiscount: Boolean): LinePricing.Policy =
    LinePricing.Policy(
      on = on,
      exempt = role == ShopRole.OWNER,
      direction = direction,
      mayReduce = mayDiscount,
      freeLineCapMinor = freeLineCapMinor,
    )

  /**
   *  آنچه کادرِ «ردیفِ سبد» (`CartLineDialog`) نشان می‌دهد و ثبت می‌کند.
   *  @param canDiscount کادرِ تخفیفِ ردیف دیده شود (پرچمِ روشن و تخفیف «ممنوع» نیست؛ صاحب همیشه)
   *  @param problem متنِ رد؛ `null` ⇒ ثبت مجاز است
   *  @param net مبلغِ ردیف پس از تخفیف (افغانی)؛ `null` ⇒ تخفیفی نیست
   *  @param netUnit قیمتِ واحدِ پس از تخفیف برای `CartLine.unitPrice`؛ `null` ⇒ همان قیمتِ نوشته‌شده
   *  @param code کدِ ردِ هسته (`LinePricing.PRICE_UP_BLOCKED`…) برای `PriceApproval.askable`/`lifted` — نه [problem] که متنِ صفحه است
   */
  data class Dialog(val canDiscount: Boolean, val problem: String?, val net: Double?, val netUnit: Double?, val code: String? = null) {
    val ok: Boolean get() = problem == null
  }

  /**
   *  کادرِ ردیف از نوشته‌ها (افغانی و متن) ⇒ `LinePricing.edit`. پرچمِ خاموش ⇒ بی رد و بی تخفیف، حتی
   *  با عددِ بی‌معنا (همان کادرِ پیشین)؛ روشن و عددِ بیرون از بازهٔ امنِ هسته ⇒ «عدد بیش از اندازه».
   *
   *  @param price قیمتِ واحدِ دستیِ نوشته‌شده؛ `null` ⇒ قیمتِ خودِ کالا
   *  @param discountText نوشتهٔ کادرِ تخفیفِ ردیف (درصد اگر [percent]، وگرنه افغانی)
   */
  fun edit(
    p: LinePricing.Policy,
    free: Boolean,
    basePrice: Double,
    unitPrice: Double,
    price: Double?,
    quantity: Double?,
    percent: Boolean,
    discountText: String,
  ): Dialog {
    val canDiscount = p.on && (p.exempt || p.mayReduce)
    if (canDiscount && numberInvalid(discountText)) return Dialog(true, StrSale.badNumber(StrLine.lineDiscount), null, null)
    val typed = if (canDiscount) parseNumber(discountText)?.takeIf { it > 0 } ?: 0.0 else 0.0
    val qtyMilli = quantity?.takeIf { it > 0 && it.isFinite() }?.let { (it * 1000).roundToLong() } ?: 0L
    val e = runCatching {
      LinePricing.edit(
        p, free, DiscountGate.minor(basePrice), DiscountGate.minor(unitPrice), price?.let(DiscountGate::minor), qtyMilli,
        percent, DiscountGate.minor(typed),
      )
    }.getOrNull() ?: return Dialog(canDiscount, if (p.on) StrLine.tooLarge else null, null, null)
    val net = if (e.discountMinor > 0) (e.grossMinor - e.discountMinor) / 100.0 else null
    return Dialog(canDiscount, message(e.problem, p), net, e.netUnit, e.problem)
  }

  /**
   *  S45 — کادرِ ردیف با «درخواستِ تأیید» (هر دو صندوق: `CartLineDialog` و برگهٔ ردیفِ گوشیِ نسخهٔ ۳، `LineModel.check`).
   *  @param first سنجشِ قاعده بی تأیید
   *  @param edit با تأییدی که هنوز به همین قیمت می‌خورد ([granted]، `PriceApproval.covers`): فقط همان قاعده برداشته (`lifted`)
   *  @param askCode ردِ برداشتنی (`PriceApproval.askable`) که هنوز تأیید ندارد ⇒ دکمهٔ «درخواستِ تأیید»؛ `null` ⇒ نه
   */
  data class Checked(val first: Dialog, val edit: Dialog, val granted: ir.vil3ntec.tohid.core.pos.PriceApproval.Grant?, val askCode: String?) {
    /** تأییدی که این ثبت واقعاً به کار برد (ردیفِ سابقه فقط برای همین، `approvalAudit`) */
    val used: ir.vil3ntec.tohid.core.pos.PriceApproval.Grant? get() = granted?.takeIf { first.problem != null }
  }

  /**
   *  [edit] با تأییدِ [grant] — @param price قیمتِ دستیِ نوشته‌شده (`null` ⇒ قیمتِ کالا؛ تأیید فقط به قیمتِ دستی می‌خورد)
   *  @param canAsk این کادر «درخواستِ تأیید» دارد (`PriceAsk` داده شده)
   */
  fun checked(
    p: LinePricing.Policy, free: Boolean, basePrice: Double, unitPrice: Double, price: Double?, quantity: Double?,
    percent: Boolean, discountText: String, grant: ir.vil3ntec.tohid.core.pos.PriceApproval.Grant?, canAsk: Boolean,
  ): Checked {
    val first = edit(p, free, basePrice, unitPrice, price, quantity, percent, discountText)
    val typedMinor = price?.let(DiscountGate::minor)
    val granted = grant?.takeIf { ir.vil3ntec.tohid.core.pos.PriceApproval.covers(it, typedMinor) }
    val lifted = if (granted == null) first
      else edit(ir.vil3ntec.tohid.core.pos.PriceApproval.lifted(p, granted.code), free, basePrice, unitPrice, price, quantity, percent, discountText)
    val ask = first.code?.takeIf { canAsk && granted == null && typedMinor != null && ir.vil3ntec.tohid.core.pos.PriceApproval.askable(p, it) }
    return Checked(first, lifted, granted, ask)
  }

  /**
   *  S45 — ردیفِ سابقهٔ «تغییرِ قیمتِ بیرون از قاعده با تأیید» (`PriceApproval`): درخواست‌کننده (`by`)،
   *  تأییدکننده (`approvedBy`) و دلیل، با کالای همان ردیف (`refId`). سرور سقفِ قیمت را نمی‌سنجد، پس همین
   *  ردیف سندِ تأیید است.
   */
  fun approvalAudit(g: ir.vil3ntec.tohid.core.pos.PriceApproval.Grant, product: String, refId: String?, now: Long, today: String): AuditEntry =
    AuditEntry(
      id = auditId(), type = ir.vil3ntec.tohid.core.pos.PriceApproval.AUDIT_TYPE, date = today, refId = refId,
      notes = "تغییرِ قیمتِ «$product» از ${ir.vil3ntec.tohid.money(g.listMinor / 100.0)} به ${ir.vil3ntec.tohid.money(g.priceMinor / 100.0)} افغانی بیرون از قاعده" +  // داده: یادداشتِ سابقهٔ دفتر (ذخیره و همگام می‌شود)
        " — درخواست: ${g.requester.trim()} — تأیید: ${g.by.trim()} — دلیل: ${g.reason.trim()}",  // داده: یادداشتِ سابقهٔ دفتر (ذخیره و همگام می‌شود)
      createdAt = now, amount = g.priceMinor / 100.0,
      by = g.requester.trim().ifEmpty { null }, approvedBy = g.by.trim(), reason = g.reason.trim(),
    )

  /** متنِ ردِ هسته برای صفحه؛ `null` ⇒ پذیرفته */
  fun message(code: String?, p: LinePricing.Policy): String? = when (code) {
    null -> null
    LinePricing.PRICE_UP_BLOCKED -> StrLine.priceUpBlocked
    LinePricing.PRICE_DOWN_BLOCKED -> StrLine.priceDownBlocked
    LinePricing.REDUCTION_DENIED -> StrLine.reductionDenied
    LinePricing.FREE_LINE_CLOSED -> StrLine.freeLineClosed
    LinePricing.FREE_LINE_OVER_CAP -> StrLine.freeLineOverCap(ir.vil3ntec.tohid.money((p.freeLineCapMinor ?: 0L) / 100.0))
    else -> StrLine.reductionDenied
  }
}

/**
 *  S45 — «درخواستِ تأیید» ِ کادرِ ردیف: نقش و نامِ کاربرِ صندوق (`TillUser`)، و [onGranted] پس از «ثبت» با همان
 *  تأیید (صدازننده ردیفِ سابقه را می‌نویسد: `LinePolicy.approvalAudit`). فقط با «قیمت و تخفیفِ ردیف» ِ روشن.
 */
class PriceAsk(
  val selfRole: String,
  val selfName: String,
  val requesterUserId: String,
  val onGranted: (ir.vil3ntec.tohid.core.pos.PriceApproval.Grant) -> Unit,
)
