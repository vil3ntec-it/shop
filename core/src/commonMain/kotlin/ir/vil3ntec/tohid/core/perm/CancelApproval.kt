package ir.vil3ntec.tohid.core.perm

/**
 *  STF-02 — «درخواستِ تأیید» برای **لغوِ فاکتور** (برگهٔ امضاشدهٔ مدیر یا صاحب؛ همان [ApprovalSlip]).
 *
 *  ⛔ فقط روی دستگاهی که Sync v3 دارد ([offered]): در Sync v1 (`server/src/lib/staff-approval.js` ‹`v1Guard`›) نقشی که
 *  پیش‌فرضِ `sales.cancel` اش «ممنوع» است (شاگرد، صندوق‌دار…) با برگه فقط `approval_needs_v3` می‌گیرد، و برگهٔ مدیرِ «نیازمند
 *  تأیید» در فیلدِ `approval` ِ فاکتور با «سندِ قطعی» (`finalized-sales.js`، پرچمِ `shop.doc_chain`) ویرایشِ فاکتور حساب
 *  می‌شود. پس دکانِ v1 همان امروز است: لغو برای این نقش‌ها بسته.
 *  برگه در فیلدِ جدای `Sale.cancelApproval` می‌نشیند (برگهٔ تخفیفِ `approval` دست نمی‌خورد) و مبدلِ v3
 *  (`V3Convert.fromLegacyOp`) آن را `approval` ِ همان op ِ «لغو» می‌کند — همان که سرور در `needOf` می‌سنجد.
 *  Kotlin خالص.
 */
object CancelApproval {

  const val PERM = "sales.cancel"

  /** نیازِ برگه: سندِ `sales/<id>`؛ مبلغ فقط برای سابقه (لغو سقف ندارد) */
  fun need(saleId: String, totalMinor: Long?): ApprovalSlip.Need = ApprovalSlip.Need(PERM, "sales/$saleId", null, totalMinor, null)

  /** «درخواستِ تأیید» دیده شود؟ تصمیمِ نقش «نیازمند تأیید» و دستگاه روی Sync v3 */
  fun offered(decision: String, v3: Boolean): Boolean = decision == "approve" && v3
}
