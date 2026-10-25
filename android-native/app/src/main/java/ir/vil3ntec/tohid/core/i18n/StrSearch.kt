package ir.vil3ntec.tohid.core.i18n

/**
 *  نوشته‌های گروهِ G10 جست‌وجو و فهرستِ فرمان (shell.md) — صاحبش همان گروه است.
 *  هر ویژگی سه‌زبانه: `val x: String get() = t("…", "…", "…")`؛ قاعده‌ها سرِ `I18n.kt`.
 */
@Suppress("unused")
object StrSearch {
  val placeholder: String get() = t("کالا، مشتری، فاکتور یا بخش…", "Items, customers, invoices or sections…", "توکي، پېرودونکي، بلونه یا برخې…")
  val products: String get() = t("کالاها", "Items", "توکي")
  val customers: String get() = t("مشتری‌ها", "Customers", "پېرودونکي")
  val invoices: String get() = t("فاکتورها", "Invoices", "بلونه")
  val sections: String get() = t("بخش‌ها", "Sections", "برخې")
  val allSections: String get() = t("همهٔ بخش‌ها", "All sections", "ټولې برخې")
  fun showAll(n: Any): String = t("همهٔ $n نتیجه", "All $n results", "ټولې $n پایلې")
  val showLess: String get() = t("کمتر", "Show less", "لږ")
  val nothing: String get() = t("چیزی پیدا نشد.", "Nothing found.", "هېڅ ونه موندل شو.")
  val nothingBody: String get() = t("نامِ دیگر، بارکد یا شمارهٔ فاکتور را امتحان کنید.", "Try another name, a barcode or an invoice number.", "بل نوم، بارکوډ یا د بل شمېره وازمویئ.")
  val scanTitle: String get() = t("اسکنِ بارکد برای جست‌وجو", "Scan a barcode to search", "د لټون لپاره بارکوډ سکن کړئ")
  fun productHint(stock: Any, price: Any): String = t("موجودی $stock · $price ؋", "In stock $stock · $price ؋", "موجودي $stock · $price ؋")
  fun debtorHint(debt: Any): String = t("بدهی $debt ؋", "Owes $debt ؋", "پور $debt ؋")
  fun invoiceTitle(label: Any): String = t("فاکتورِ $label", "Invoice $label", "بل $label")
  val quickSale: String get() = t("فروشِ سریع", "Quick sale", "چټک پلور")

  // فهرستِ فرمانِ کامپیوتر (Ctrl+K)
  val palettePlaceholder: String get() = t("صفحه، کالا، بارکد یا قرض‌دار…", "Page, item, barcode or debtor…", "پاڼه، توکی، بارکوډ یا پوروړی…")
  val paletteKeys: String get() = t("↑ ↓ انتخاب · Enter باز کردن · Esc بستن", "↑ ↓ select · Enter open · Esc close", "↑ ↓ ټاکل · Enter خلاصول · Esc بندول")
  val page: String get() = t("صفحه", "Page", "پاڼه")
}
