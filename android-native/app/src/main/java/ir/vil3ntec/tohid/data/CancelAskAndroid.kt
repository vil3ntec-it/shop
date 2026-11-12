package ir.vil3ntec.tohid.data

import android.content.Context
import ir.vil3ntec.tohid.core.perm.ApprovalSlip
import ir.vil3ntec.tohid.core.perm.CancelApproval

/**
 *  STF-02 — «لغو با برگهٔ تأییدِ مدیر» برای همین نقش روی همین دستگاه: خوانندهٔ یگانهٔ ورودهای لغوِ گوشیِ نسخهٔ ۳ (`InvoiceRoute`،
 *  `HistoryHost`، `CancelRoute`) و جدولِ فاکتورهای کامپیوتر (`SalesHistoryScreen`). فقط نقشی که خودش لغو نمی‌کند، تصمیمِ
 *  `sales.cancel` اش «نیازمندِ تأیید» است و دستگاه روی Sync v3 است (`CancelApproval.offered`)؛ دکانِ v1 همان امروز (بسته).
 *  «اصلاحِ فاکتور» را برگه نمی‌پوشاند (`SalesVoid.run` فقط برای لغو برگه می‌گیرد) ⇒ اصلاح همان `canCancelSale`.
 */
object CancelAsk {

  fun offered(context: Context): Boolean = !ShopRole.canCancelSale(context) && CancelApproval.offered(
    ShopRole.decide(context, CancelApproval.PERM).decision,
    runCatching { ir.vil3ntec.tohid.sync.v3.SyncChoice.isV3(context) }.getOrDefault(false),
  )

  /** نیازِ برگه برای همین فاکتور: سندِ `sales/<id>` و مبلغ (فقط برای سابقه) */
  fun need(sale: Sale): ApprovalSlip.Need = CancelApproval.need(sale.id, Math.round(sale.finalTotal * 100.0))
}
