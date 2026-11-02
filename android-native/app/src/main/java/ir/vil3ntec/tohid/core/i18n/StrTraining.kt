package ir.vil3ntec.tohid.core.i18n

/**
 *  حالتِ آموزشی (OPS-07) — تنظیماتِ همین دستگاه، نوارِ همیشه‌پیدا و پنجرهٔ خروج.
 *
 *  قاعده‌ها سرِ `I18n.kt`؛ منطق در `data/TrainingMode` و مرزِ دیسک در `data/TrainingLedgerAndroid`. ترجمه‌های
 *  نامطمئن در `docs/i18n-glossary.md` برای بازبینیِ گویشور.
 */
@Suppress("unused")
object StrTraining {
  val settingsNote: String get() = t(
    "دفتری جدا با کالا، مشتری و فروشِ نمونه برای یاد گرفتنِ برنامه. هیچ‌چیزش با دفترِ واقعی قاطی نمی‌شود: نه همگام‌سازی، نه سرور، نه شمارهٔ فاکتورِ واقعی؛ هر فاکتورش مهرِ «آموزشی» دارد.",
    "A separate ledger with sample products, customers and sales for learning the app. Nothing mixes with the real ledger: no sync, no server, no real invoice numbers; every receipt is stamped “Training”.",
    "یو جلا دفتر د بېلګې توکو، پېرودونکو او پلور سره د اپ زده کولو لپاره. هېڅ شی له اصلي دفتر سره نه ګډېږي: نه همغږي، نه سرور، نه اصلي بل شمېره؛ هر بل د «زده‌کړې» مهر لري.",
  )
  val showEntry: String get() = t("نشان دادنِ درِ ورود به آموزش", "Show the training entry", "د زده‌کړې ننوتنه وښیه")
  val staffToo: String get() = t("کارمند هم بتواند وارد شود", "Staff may enter too", "کارکوونکی هم ننوتلی شي")
  val staffOnlyOwner: String get() = t("فقط صاحب یا مدیر این را عوض می‌کند", "Only the owner or a manager can change this", "یوازې څښتن یا مدیر دا بدلولی شي")
  val enter: String get() = t("ورود به حالتِ آموزشی", "Enter training mode", "د زده‌کړې حالت ته ننوتل")
  val resumeNote: String get() = t("دفترِ آموزشیِ پیشین هنوز هست و همان باز می‌شود", "The previous training ledger is kept and will reopen", "پخوانی د زده‌کړې دفتر لا شته او همدا پرانیستل کېږي")
  val wipeSaved: String get() = t("پاک کردنِ دفترِ آموزشی", "Wipe the training ledger", "د زده‌کړې دفتر پاکول")
  val wiped: String get() = t("دفترِ آموزشی پاک شد؛ ورودِ بعدی با دادهٔ نمونهٔ تازه", "Training ledger wiped; next entry starts with fresh sample data", "د زده‌کړې دفتر پاک شو؛ راتلونکې ننوتنه د نوې بېلګې سره")
  val refusedRole: String get() = t("نقشِ شما اجازهٔ ورود به آموزش ندارد — صاحب یا مدیر «کارمند هم» را روشن کند", "Your role may not enter training — the owner or a manager can allow staff", "ستاسو رول زده‌کړې ته د ننوتلو اجازه نه لري — څښتن یا مدیر دې کارکوونکو ته اجازه ورکړي")
  val banner: String get() = t(
    "حالتِ آموزشی — دادهٔ نمونه؛ هیچ‌چیز ثبتِ واقعی یا همگام نمی‌شود",
    "Training mode — sample data; nothing is recorded for real or synced",
    "د زده‌کړې حالت — د بېلګې معلومات؛ هېڅ شی په رښتیا نه ثبتېږي او نه همغږي کېږي",
  )
  val exit: String get() = t("خروج از آموزش", "Exit training", "له زده‌کړې وتل")
  val exitTitle: String get() = t("خروج از حالتِ آموزشی", "Exit training mode", "د زده‌کړې له حالت وتل")
  val exitNote: String get() = t("دفترِ واقعی همان‌طور که بود برمی‌گردد.", "The real ledger comes back exactly as it was.", "اصلي دفتر هماغسې بېرته راځي لکه څنګه چې و.")
  val exitWipe: String get() = t("دفترِ آموزشی هم پاک شود", "Also wipe the training ledger", "د زده‌کړې دفتر هم پاک شي")
  val cancel: String get() = t("ماندن در آموزش", "Stay in training", "په زده‌کړه کې پاتې کېدل")
  val networkBlocked: String get() = t(
    "حالتِ آموزشی روشن است؛ چیزی به سرور فرستاده نمی‌شود",
    "Training mode is on; nothing is sent to the server",
    "د زده‌کړې حالت روښانه دی؛ هېڅ شی سرور ته نه لېږل کېږي",
  )
}
