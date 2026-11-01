package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.pay.ShiftReview

/**
 *  PAY-14 — دفترِ دکان ⇄ هستهٔ بررسیِ بستنِ صندوق (`core/pay/ShiftReview`، commonMain).
 *
 *  بررسی یک ردیفِ تازهٔ سابقه است (`type = shift_review`، `refId` = شناسهٔ `shift_close`، `by`، `note`) و مثلِ
 *  بقیهٔ سابقه همگام می‌شود؛ ردیفِ بستن و Zِ آن دست نمی‌خورند. فقط از صندوقِ شیفتِ پرچم‌دار (`ShopPolicy.payV2`)
 *  نوشته می‌شود؛ پرچم خاموش ⇒ هیچ ردیفی.
 */
object ShiftReviewFlow {

  /** همهٔ بررسی‌های ثبت‌شدهٔ دفتر (از هر دستگاه) */
  fun reviews(d: ShopData): List<ShiftReview.Review> = d.auditLog
    .filter { it.type == ShiftReview.TYPE && !it.refId.isNullOrEmpty() }
    .map { ShiftReview.Review(it.id, it.refId.orEmpty(), it.createdAt, it.by.orEmpty(), it.note.orEmpty()) }

  /** نقشِ ذخیره‌شدهٔ برنامه (`ShopRole.current`) ⇒ نقشِ هسته؛ ناشناخته = شاگرد (تنگ‌ترین) */
  fun roleOf(role: String): ShiftReview.Role = when (role) {
    ShopRole.OWNER -> ShiftReview.Role.OWNER
    ShopRole.MANAGER -> ShiftReview.Role.MANAGER
    else -> ShiftReview.Role.STAFF
  }

  /** `needsReview` ِ Zِ ذخیره‌شدهٔ همان بستن؛ ردیفِ بی Z (قدیمی) ⇒ null */
  fun needsReview(close: AuditEntry): Boolean? = close.z?.let(PayFlow::reportOf)?.needsReview

  fun state(d: ShopData, close: AuditEntry): ShiftReview.State =
    ShiftReview.state(needsReview(close), close.id, reviews(d))

  fun reviewOf(d: ShopData, close: AuditEntry): ShiftReview.Review? = ShiftReview.reviewOf(close.id, reviews(d))

  /** ردیفِ `shift_review` ِ تازه برای `close`، یا کدِ رد ِ هسته */
  fun row(
    d: ShopData, close: AuditEntry, role: ShiftReview.Role, by: String, note: String,
    date: String, now: Long, newId: () -> String,
  ): PayFlow.Outcome<AuditEntry> {
    if (close.type != CashShift.CLOSE) return PayFlow.Outcome.Bad(PayFlow.Problem("NOT_NEEDED"))
    return when (val r = ShiftReview.review(newId(), close.id, needsReview(close), close.by, role, by, note, now, reviews(d))) {
      is ShiftReview.Result.Err -> PayFlow.Outcome.Bad(PayFlow.Problem(r.code))
      is ShiftReview.Result.Ok -> PayFlow.Outcome.Ok(
        AuditEntry(
          id = r.review.id, type = ShiftReview.TYPE, date = date, refId = close.id, createdAt = now,
          //  داده: متنِ سابقهٔ عملیات (همگام می‌شود و با زبانِ صفحه عوض نمی‌شود)
          notes = "بررسیِ اختلافِ صندوق: ${r.review.note}", by = r.review.by, note = r.review.note,
        ),
      )
    }
  }
}
