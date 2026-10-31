package ir.vil3ntec.tohid.core.i18n

/**
 *  کادرهای مشترکِ فرم (`Fields`، `EntryFields`، `CategoryPick`): تاریخ، دسته‌بندی و انتخاب از فهرست —
 *  در مصارف، خرید، انبار، قرض‌داران و کالا یکی‌اند.
 *
 *  ساخته‌شده از جدولِ سه‌زبانه؛ دستی هم ویرایش‌پذیر است. قاعده‌ها سرِ `I18n.kt`.
 *  ترجمه‌های نامطمئن در `docs/i18n-glossary.md` برای بازبینیِ گویشور.
 */
@Suppress("unused")
object StrFields {

  // تاریخ
  val date: String get() = t("تاریخ", "Date", "نېټه")
  val yesterday: String get() = t("دیروز", "Yesterday", "پرون")
  val solarDate: String get() = t("تاریخ خورشیدی", "Solar (Hijri Shamsi) date", "لمریزه نېټه")
  val dateUnreadable: String get() = t(
    "تاریخ خوانده نشد — مثل ۱۴۰۵/۰۶/۰۶ بنویسید",
    "Couldn't read the date — write it like 1405/06/06",
    "نېټه ونه لوستل شوه — داسې یې ولیکئ: 1405/06/06",
  )

  // دسته‌بندی و انتخاب از فهرست
  val category: String get() = t("دسته‌بندی", "Category", "ډله")
  val newCategory: String get() = t("دسته‌بندی تازه", "New category", "نوې ډله")
  val addNew: String get() = t("+ تازه", "+ New", "+ نوی")
  val add: String get() = t("افزودن", "Add", "اضافه کول")
  val choose: String get() = t("انتخاب کنید", "Choose", "وټاکئ")
  val search: String get() = t("جستجو", "Search", "لټون")
  val noMatch: String get() = t("چیزی با این جستجو پیدا نشد", "Nothing matches this search", "په دې لټون هېڅ ونه موندل شول")
}
