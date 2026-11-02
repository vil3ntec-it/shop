package ir.vil3ntec.tohid.core.i18n

/**
 *  AI-11 — «جست‌وجو با عکس» در صندوق و فهرستِ کالاها (`ui/screens/VisualSearchUi.kt`، منطق در `core/product/VisualSearch`).
 *  ⛔ هیچ نوشته‌ای ادعای «همان کالا» نمی‌کند؛ برچسبِ هر ردیف فقط «مشابه».
 *  قاعده‌ها سرِ `I18n.kt`؛ ترجمه‌های نامطمئن در `docs/i18n-glossary.md`.
 */
@Suppress("unused")
object StrVisualSearch {

  val button: String get() = t("جست‌وجو با عکس", "Search by photo", "په انځور لټون")
  val title: String get() = t("کالاهای مشابه", "Similar items", "ورته توکي")
  val similar: String get() = t("مشابه", "Similar", "ورته")
  val photoNote: String get() = t(
    "عکس فقط برای همین جست‌وجو فرستاده می‌شود و جایی نگه داشته نمی‌شود",
    "The photo is sent only for this search and is not kept anywhere",
    "انځور یوازې د همدې لټون لپاره لېږل کېږي او چېرته نه ساتل کېږي",
  )
  val busy: String get() = t("در حالِ جست‌وجو…", "Searching…", "لټون روان دی…")
  val notice: String get() = t(
    "این‌ها فقط از روی عکس مشابه‌اند؛ پیش از هر کاری خودتان کالا را ببینید و انتخاب کنید.",
    "These only look similar in the photo; check and choose the item yourself first.",
    "دا یوازې د انځور له مخې ورته دي؛ مخکې له هر کار پخپله توکی وګورئ او وې ټاکئ.",
  )
  val none: String get() = t("کالای مشابهی پیدا نشد.", "No similar items found.", "ورته توکی ونه موندل شو.")
  val notIndexed: String get() = t(
    "هنوز عکسِ کالاها سنجیده نشده؛ چند دقیقهٔ دیگر دوباره بزنید.",
    "Item photos are not indexed yet; try again in a few minutes.",
    "د توکو انځورونه لا نه دي سنجول شوي؛ څو دقیقې وروسته بیا هڅه وکړئ.",
  )
  val unreadable: String get() = t("عکس خوانده نشد؛ عکسِ دیگری بگیرید.", "The photo could not be read; take another one.", "انځور ونه لوستل شو؛ بل انځور واخلئ.")
  val again: String get() = t("عکسِ دیگر", "Another photo", "بل انځور")
  val close: String get() = t("بستن", "Close", "بندول")
}
