package ir.vil3ntec.tohid.core.i18n

/**
 *  CRM-15 — «چه کسی این پرونده را دید» (سابقهٔ دسترسی) در پروندهٔ مشتریِ گوشیِ نسخهٔ ۳ و کامپیوتر
 *  (`ui/screens/CustomerAccessPanelAndroid.kt`؛ منطق در `core/crm/CustomerAccess` و `data/CustomerAccessLog`).
 */
@Suppress("unused")
object StrCustomerAccess {

  val title: String get() = t("چه کسی این پرونده را دید", "Who viewed this file", "دا دوسیه چا ولیده")
  val hint: String get() = t(
    "باز کردنِ پرونده، دیدنِ شماره و خروجی؛ از هر کس در هر ۱۰ دقیقه یک بار. فقط صاحب و کسی که «سابقهٔ عملیات» را می‌بیند.",
    "Opening the file, seeing the number and exports; once per person every 10 minutes. Only the owner and those who can see the activity log.",
    "د دوسیې پرانیستل، د شمېرې لیدل او وېستنه؛ له هر چا په هرو ۱۰ دقیقو کې یو ځل. یوازې مالک او هغه څوک چې د عملیاتو تاریخچه ویني.",
  )
  val empty: String get() = t("هنوز کسی این پرونده را ندیده است.", "Nobody has viewed this file yet.", "تر اوسه چا دا دوسیه نه ده لیدلې.")
  val kindDetail: String get() = t("پرونده", "File", "دوسیه")
  val kindPhone: String get() = t("شماره و نشانی", "Number and address", "شمېره او پته")
  val kindExport: String get() = t("خروجی", "Export", "وېستنه")
  val kindExportAll: String get() = t("خروجیِ همهٔ مشتریان", "All-customers export", "د ټولو پېرودونکو وېستنه")
  val fromServer: String get() = t("از سرور", "via server", "له سرور")
  val unknownViewer: String get() = t("ناشناس", "Unknown", "ناپېژندل شوی")
  fun times(n: String): String = t("${asIs(n)} بار", "$n times", "$n ځله")
  /** نوعِ ردیف در «سابقهٔ عملیات» (`StrReports.kind`) */
  val auditKind: String get() = t("دیدنِ پروندهٔ مشتری", "Customer file viewed", "د پېرودونکي دوسیه ولیدل شوه")
}
