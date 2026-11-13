package ir.vil3ntec.tohid.core.image

/*
 *  «گزارشِ عکسِ اشتباه یا بستهٔ قدیمی» ِ کارتِ منبعِ عکس — v3 §۳۷.۷ (`POST /api/shop/images/:id/report`، پرچمِ
 *  `shop.image_bank`؛ مدیرِ سامانه گزارش‌ها را در پنل می‌بیند: `GET /api/admin/image-bank/reports`).
 *
 *  • فقط عکسِ مرکزیِ دیده‌شده (حتی بایگانی‌شده)؛ عکسِ خودِ دکان را خودش عوض می‌کند.
 *  • نوع فقط از همان `REPORT_KINDS` ِ سرور، بی «پروانه» (کارِ مدیرِ بانک است)؛ یادداشت تا ۵۰۰ نویسه (`str(body.note, 500)`).
 *  • سرور خواندنی بودنِ عکس برای همین دکان را دوباره می‌سنجد (وگرنه ۴۰۴ و سابقهٔ امنیتی).
 *
 *  منطقِ خالص، بی JSON و بی android.*؛ آزمون: `BankPhotoReportTest` (commonTest).
 */
object BankPhotoReport {

  val KINDS: List<String> = listOf("wrong_product", "old_packaging", "quality", "other")
  const val NOTE_MAX = 500

  /** شناسهٔ عکسِ مرکزیِ گزارش‌شدنی، وگرنه null (بی دکمه) */
  fun reportable(r: BankPhotoCard.Resolved): String? =
    if (r.source == BankPhotoCard.Source.CENTRAL && r.imageId.isNotBlank() && r.version > 0) r.imageId else null

  fun path(imageId: String): String = "/shop/images/" + ImageChanges.encode(imageId) + "/report"

  /** (نوع، یادداشتِ کوتاه‌شده) — نوعِ بیرون از فهرست ⇒ null (هیچ درخواستی) */
  fun body(kind: String, note: String): Pair<String, String>? =
    if (kind in KINDS) kind to note.trim().take(NOTE_MAX) else null
}
