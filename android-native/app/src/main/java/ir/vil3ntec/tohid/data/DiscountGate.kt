package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.perm.ApprovalSlip
import ir.vil3ntec.tohid.core.perm.PermCore
import ir.vil3ntec.tohid.core.pos.LinePricing
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlin.math.roundToLong

/**
 *  دروازهٔ تخفیفِ صندوق — STF-02/03، سناریوی S08 (بخشِ ۱۰.۱).
 *
 *  همان حسابِ سرور (`needOf`ِ `server/src/lib/staff-approval.js`): مبلغ به Minor،
 *  درصد با `PermCore.discountBp` (نیم افغانیِ گرد کردن بخشیده)، و تصمیم با
 *  همان `PermCore.evaluate`. پس صندوق دقیقاً همان‌جا «درخواستِ تأیید»
 *  می‌خواهد که سرور بی برگه رد می‌کند. صفحهٔ گوشی و کامپیوتر هر دو همین را
 *  می‌خوانند. Kotlinِ خالص، بی android.*.
 */
object DiscountGate {

  enum class State {
    /** تخفیف نیست یا در اختیارِ خودِ کاربر است */
    FREE,
    /** بیش از سقف/«نیازمند تأیید» و هنوز برگه‌ای که بپوشاند نیست */
    NEEDS_APPROVAL,
    /** برگهٔ امضاشده همین مقدار را می‌پوشاند */
    APPROVED,
    /** «ممنوع» — با تأیید هم نه */
    DENIED,
  }

  fun minor(afn: Double): Long = if (afn.isFinite()) (afn * 100).roundToLong() else 0L

  /** درخواستِ عددی برای `PermCore.evaluate` (همان سرور) */
  fun req(discount: Double, subtotal: Double): Map<String, Double> {
    val d = minor(discount)
    return mapOf("pct" to PermCore.discountBp(d, minor(subtotal)) / 100.0, "amount" to d.toDouble())
  }

  fun need(saleId: String, discount: Double, subtotal: Double): ApprovalSlip.Need =
    ApprovalSlip.discountNeed(saleId, minor(discount), minor(subtotal))

  /**
   *  @param decision `PermCore.evaluate(...).decision` با `req(...)`ِ همین فاکتور
   *  @param approval برگه‌ای که پیش‌تر برای همین سبد گرفته شد (اگر هست)
   */
  fun state(decision: String, saleId: String?, discount: Double, subtotal: Double, approval: ApprovalSlip.Slip?): State {
    if (minor(discount) <= 0) return State.FREE
    return when (decision) {
      "allow" -> State.FREE
      "approve" -> {
        if (saleId.isNullOrBlank() || approval == null) State.NEEDS_APPROVAL
        //  زمان را سرور می‌سنجد؛ این‌جا فقط «همین سند و تا همین مقدار»
        else if (ApprovalSlip.covers(approval, need(saleId, discount, subtotal)) == null) State.APPROVED
        else State.NEEDS_APPROVAL
      }
      else -> State.DENIED
    }
  }

  /**
   *  POS-20/21 — کاهشِ دلخواهِ ردیف‌های سبد (Minor): هر ردیفِ کالا که قیمتِ دستی‌اش زیرِ
   *  قیمتِ فهرست است (تخفیفِ ردیف هم در همان قیمت می‌نشیند، `LinePricing.unitForNet`).
   *  قلمِ آزاد فهرست ندارد (سقفش `LinePricing.checkFreeLine`). ردیفی که تخفیفِ خودکار گرفته
   *  ([promo]) دلخواهِ صندوقدار نیست و شمرده نمی‌شود؛ `PromoEngine` به ردیفِ با قیمتِ دستی
   *  تخفیفِ خودکار نمی‌دهد، پس در سبدِ پس از تخفیف ([cart] = `priced`) هم این دو جدا می‌مانند.
   */
  fun reductionMinor(d: ShopData, cart: List<SalesEngine.CartLine>, promo: PromoEngine.Outcome? = null): Long {
    var sum = 0L
    for (line in cart) {
      val unit = line.unitPrice ?: continue
      if (line.free || !(line.quantity > 0)) continue
      if (promo?.tags?.get(line.productId)?.promo?.isNotEmpty() == true) continue
      val p = d.products.find { it.id == line.productId } ?: continue
      sum += LinePricing.reductionMinor(minor(ProductPlansMap.price(p)), minor(unit), (line.quantity * 1000).roundToLong())
    }
    return sum
  }

  /** آنچه به `req`، `state` و `need` می‌رود (افغانی) — همان دو عددِ `totals` وقتی پرچم خاموش است */
  data class Basis(val discount: Double, val subtotal: Double)

  /**
   *  POS-21 — پایهٔ سنجشِ `sales.discount`: پرچمِ [on] (`LinePolicy.on`) خاموش ⇒ دقیقاً
   *  (`totals.discount`، `totals.subtotal`)ِ امروز. روشن ⇒ کاهش‌های ردیف به هر دو افزوده
   *  (`LinePricing.basis`)؛ برگه‌ای که با این گرفته شود نیازِ سرور (فقط تخفیفِ فاکتور) را هم
   *  می‌پوشاند. عددِ بیرون از بازهٔ امن ⇒ «همهٔ فاکتور تخفیف است» تا بی تأیید رد شود، نه آزاد.
   */
  fun basis(on: Boolean, d: ShopData, cart: List<SalesEngine.CartLine>, promo: PromoEngine.Outcome?, totals: SalesEngine.Totals): Basis {
    if (!on) return Basis(totals.discount, totals.subtotal)
    val b = runCatching {
      LinePricing.basis(LinePricing.Policy(on = true), minor(totals.discount), minor(totals.subtotal), reductionMinor(d, cart, promo))
    }.getOrNull() ?: return Basis(maxOf(totals.subtotal, 1.0), totals.subtotal)
    return Basis(b.discountMinor / 100.0, b.baseMinor / 100.0)
  }

  /** برگه ⇐ JSONِ `Sale.approval` (همان کلیدهای سرور) */
  fun json(s: ApprovalSlip.Slip): JsonObject = buildJsonObject {
    ApprovalSlip.toMap(s).forEach { (k, v) ->
      put(
        k,
        when (v) {
          null -> JsonNull
          is Number -> JsonPrimitive(v)
          else -> JsonPrimitive(v.toString())
        },
      )
    }
  }
}
