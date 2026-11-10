package ir.vil3ntec.tohid.core.i18n

/**
 *  PRD-17 — عکس‌های تکمیلیِ کالا و PRD-27 — عکسِ جدای ویترین (`ui/v3/products/ProductGalleryPage.kt`؛
 *  منطق در `core/image/ProductGallery`).
 */
@Suppress("unused")
object StrProductGallery {

  val title: String get() = t("عکس‌های بیشتر و ویترین", "More photos and storefront", "نور انځورونه او ویټرین")
  val off: String get() = t("بانکِ تصاویر برای این دکان روشن نیست.", "The image bank is not turned on for this shop.", "د انځورونو بانک د دې دوکان لپاره فعال نه دی.")
  val loading: String get() = t("از سرور می‌آید…", "Loading from the server…", "له سرور څخه راځي…")
  val failed: String get() = t("از سرور نیامد. اینترنت را ببینید و دوباره بزنید.", "Could not load from the server. Check the internet and try again.", "له سرور څخه رانغی. انټرنېټ وګورئ او بیا هڅه وکړئ.")
  val retry: String get() = t("دوباره", "Retry", "بیا")
  val readOnly: String get() = t("عکسِ کالا در حدِ نقشِ شما نیست؛ فقط می‌بینید.", "Product photos are outside your role; view only.", "د توکي انځور ستاسو د رول په حد کې نه دی؛ یوازې ګورئ.")

  // ── ویترین (PRD-27) ──
  val storefront: String get() = t("عکسِ ویترین", "Storefront photo", "د ویټرین انځور")
  val storefrontNote: String get() = t(
    "ویترین می‌تواند عکسِ جدایی از عکسِ کالا در صندوق و فهرست داشته باشد. عکسِ تازهٔ کالا آن را خودکار عوض نمی‌کند.",
    "The storefront can show a photo separate from the product photo used at the till and in lists. A new product photo does not replace it automatically.",
    "ویټرین کولای شي له صندوق او لړلیک له انځور جلا انځور ولري. د توکي نوی انځور یې پخپله نه بدلوي.",
  )
  val shownDevice: String get() = t("ویترین همان عکسِ کالا را نشان می‌دهد.", "The storefront shows the product photo.", "ویټرین د توکي همغه انځور ښيي.")
  val shownOwn: String get() = t("ویترین عکسِ جدای خودش را نشان می‌دهد.", "The storefront shows its own separate photo.", "ویټرین خپل جلا انځور ښيي.")
  val shownCentral: String get() = t("ویترین عکسِ بانکِ مرکزی را نشان می‌دهد.", "The storefront shows the central bank photo.", "ویټرین د مرکزي بانک انځور ښيي.")
  val shownNone: String get() = t("ویترین برای این کالا عکسی ندارد.", "The storefront has no photo for this product.", "ویټرین د دې توکي لپاره انځور نه لري.")
  val setStorefront: String get() = t("گذاشتنِ عکسِ جدا برای ویترین", "Set a separate storefront photo", "د ویټرین لپاره جلا انځور کېښودل")
  val useProductPhoto: String get() = t("همان عکسِ کالا در ویترین", "Use the product photo in the storefront", "په ویټرین کې د توکي همغه انځور")
  val storefrontSaved: String get() = t("عکسِ ویترین گذاشته شد.", "Storefront photo set.", "د ویټرین انځور کېښودل شو.")
  val productPhotoBack: String get() = t("ویترین دوباره همان عکسِ کالا را نشان می‌دهد.", "The storefront shows the product photo again.", "ویټرین بیا د توکي همغه انځور ښيي.")

  // ── تکمیلی (PRD-17) ──
  val extras: String get() = t("عکس‌های تکمیلی", "Extra photos", "بشپړوونکي انځورونه")
  fun extrasFooter(max: Int): String = t(
    "پشت، برچسب، مدل یا جزئیاتِ کالا — حداکثر ${asIs(max)} عکس. نسخهٔ کوچک و بزرگ همین‌جا ساخته می‌شود؛ عکسِ اصلیِ کالا دست نمی‌خورد.",
    "Back, label, variant or details — up to $max photos. Small and large versions are made here; the main product photo is untouched.",
    "شا، لیبل، ماډل یا جزییات — تر $max انځوره. کوچنۍ او لویه نسخه همدلته جوړېږي؛ د توکي اصلي انځور نه بدلېږي.",
  )
  val noExtras: String get() = t("هنوز عکسِ تکمیلی ندارد.", "No extra photos yet.", "لا بشپړوونکی انځور نه لري.")
  val add: String get() = t("افزودنِ عکس", "Add photo", "انځور زیاتول")
  val full: String get() = t("سقفِ عکس‌های تکمیلی پر است؛ اول یکی را بردارید.", "The extra-photo limit is reached; remove one first.", "د بشپړوونکو انځورونو سقف ډک دی؛ لومړی یو لرې کړئ.")
  val kindDetail: String get() = t("جزئیات", "Details", "جزییات")
  val kindBack: String get() = t("پشت", "Back", "شا")
  val kindLabel: String get() = t("برچسب", "Label", "لیبل")
  val kindVariant: String get() = t("مدل", "Variant", "ماډل")
  val remove: String get() = t("برداشتن از کالا", "Remove from product", "له توکي لرې کول")
  val removed: String get() = t("عکس از کالا برداشته شد؛ اصلش روی سرور می‌ماند.", "Photo removed from the product; its original stays on the server.", "انځور له توکي لرې شو؛ اصلي یې په سرور کې پاتې کېږي.")
  val selected: String get() = t("این عکس همین حالا عکسِ ویترین است؛ اول عکسِ دیگری بگذارید.", "This photo is the storefront photo now; set another one first.", "دا انځور همدا اوس د ویټرین انځور دی؛ لومړی بل کېږدئ.")
  val saved: String get() = t("عکس افزوده شد.", "Photo added.", "انځور زیات شو.")
  val uploading: String get() = t("می‌فرستد…", "Sending…", "لېږي…")
  val notSent: String get() = t("نرفت. اینترنت را ببینید و دوباره بزنید.", "Not sent. Check the internet and try again.", "ونه لېږل شو. انټرنېټ وګورئ او بیا هڅه وکړئ.")
  val unreadable: String get() = t("این فایل خوانده نشد.", "This file could not be read.", "دا فایل ونه لوستل شو.")
}
