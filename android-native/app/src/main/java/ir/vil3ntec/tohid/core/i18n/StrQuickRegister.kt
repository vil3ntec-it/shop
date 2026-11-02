package ir.vil3ntec.tohid.core.i18n

/**
 *  «ثبتِ سریعِ کالا» در خودِ صندوق (S03، POS-17): برگهٔ صندوقِ گوشیِ نسخهٔ ۳ (`QuickRegisterSheet`) و پیامِ پایانیِ
 *  ثبت در هر دو صندوق (`saveQuick`). منطق در `data/QuickRegister`. قاعده‌ها سرِ `I18n.kt`.
 */
@Suppress("unused")
object StrQuickRegister {
  val title: String get() = t("ثبتِ سریعِ کالا", "Quick add item", "د توکي چټک ثبت")
  val action: String get() = t("ثبتِ سریع", "Quick add", "چټک ثبت")
  fun barcode(code: Any): String = t("بارکد: ${asIs(code)}", "Barcode: $code", "بارکوډ: $code")
  val name: String get() = t("نامِ کالا", "Item name", "د توکي نوم")
  val price: String get() = t("قیمتِ فروش", "Sale price", "د پلور بیه")
  val unit: String get() = t("واحد", "Unit", "واحد")
  val stock: String get() = t("موجودیِ همین که در دست است", "Stock on hand", "په لاس کې موجودي")
  val save: String get() = t("ثبت و افزودن", "Save and add", "ثبت او زیات کړئ")
  fun added(name: Any): String = t("«${asIs(name)}» ثبت و به فاکتور اضافه شد", "“$name” saved and added to the invoice", "«$name» ثبت او بل ته زیات شو")
  fun notAdded(name: Any): String = t(
    "«${asIs(name)}» ثبت شد؛ موجودی ندارد و به فاکتور نرفت",
    "“$name” saved; it has no stock, so it wasn't added to the invoice",
    "«$name» ثبت شو؛ موجودي نه لري او بل ته زیات نه شو",
  )
  val notSaved: String get() = t("ثبت نشد؛ دوباره بزنید", "Not saved; try again", "ثبت نه شو؛ بیا هڅه وکړئ")
  val unavailable: String get() = t("ثبتِ سریع این‌جا باز نیست", "Quick add isn't available here", "چټک ثبت دلته نشته")
}
