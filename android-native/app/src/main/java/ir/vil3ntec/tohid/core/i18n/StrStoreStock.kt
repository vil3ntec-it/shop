package ir.vil3ntec.tohid.core.i18n

/**
 *  WEB-09 (§۶.۱۴): سیاستِ کالای ناموجود در ویترین — «ویترین ⇐ تنظیمات» (`StorefrontSettingsModel.hideOutOfStock`،
 *  `PUT /api/shop/storefront` با `hideOutOfStock`، مهاجرتِ ۲۴۰). پالایه فقط در سرور (`storefront-filters.hiding`):
 *  روشن ⇒ ناموجودِ دانسته از فهرست، جست‌وجو و دسته‌ها کنار؛ موجودیِ «در حال بررسی» هرگز پنهان نمی‌شود.
 *  ردیفِ «تنظیمات ⇐ فروش و پرداخت ⇐ آنلاین» به همان‌جا می‌رود (`SettingsLayout.STORE_STOCK`).
 *  جدا از `StrOnline` تا آن زیرِ سقفِ اندازه بماند.
 */
@Suppress("unused")
object StrStoreStock {
  val section: String get() = t("کالای ناموجود در ویترین", "Out-of-stock items on the storefront", "په ویترین کې نشته توکي")
  val line: String get() = t(
    "ناموجود در ویترین «ناموجود» بماند یا پنهان شود",
    "Show out-of-stock items as “Out of stock” or hide them",
    "نشته توکي په ویترین کې «نشته» ښکاره شي که پټ شي",
  )
  val hide: String get() = t("کالای ناموجود را پنهان کن", "Hide out-of-stock items", "نشته توکي پټ کړه")
  val help: String get() = t(
    "خاموش: کالای ناموجود با نشانِ «ناموجود» دیده می‌شود. روشن: از فهرست و جست‌وجوی ویترین برداشته می‌شود و با موجود شدن خودش برمی‌گردد. کالایی که موجودی‌اش هنوز روشن نیست هرگز پنهان نمی‌شود.",
    "Off: out-of-stock items show as “Out of stock”. On: they leave the storefront list and search, and come back by themselves when stocked. Items whose stock is not known yet are never hidden.",
    "بند: نشته توکي د «نشته» په نښه ښکاري. چالان: د ویترین له لېست او لټون څخه لرې کېږي او چې موجود شي پخپله بېرته راځي. هغه توکي چې موجودي یې لا نه ده معلومه هېڅکله نه پټېږي.",
  )
}
