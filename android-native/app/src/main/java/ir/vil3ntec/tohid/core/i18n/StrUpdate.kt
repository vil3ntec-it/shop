package ir.vil3ntec.tohid.core.i18n

/**
 *  پیام‌های لایهٔ به‌روزرسانیِ برنامه (`update/`) — بررسی، دانلودِ ادامه‌دار، نصب و سنجشِ امضا (LOC-08 دری، دورِ ۷). پیش‌تر متنِ خامِ
 *  فارسی بودند؛ فارسی بایت‌به‌بایت همان پیشین، هر مقدار با `asIs`. قاعده‌ها سرِ `I18n.kt`.
 */
@Suppress("unused")
object StrUpdate {

  // ── دانلودِ ادامه‌دار (`ResumableDownload`) ──
  val badRange: String get() = t("Content-Range نامعتبر", "Invalid Content-Range", "ناسم Content-Range")
  fun wrongStart(from: Any?, asked: Any?): String = t(
    "سرور از بایتِ ${asIs(from)} فرستاد، نه ${asIs(asked)}",
    "The server sent from byte $from, not $asked",
    "سرور له $from بایټ څخه ولېږل، نه $asked",
  )
  val sizeMismatch: String get() = t(
    "اندازهٔ فایلِ سرور با انتشار نمی‌خواند",
    "The server file's size doesn't match the release",
    "د سرور د فایل اندازه له خپرونې سره سمون نه خوري",
  )
  val changedSize: String get() = t("فایلِ سرور عوض شده (اندازه)", "The server file changed (size)", "د سرور فایل بدل شوی (اندازه)")
  val changedEtag: String get() = t("فایلِ سرور عوض شده (ETag)", "The server file changed (ETag)", "د سرور فایل بدل شوی (ETag)")
  val rangeMissing: String get() = t("بازهٔ خواسته‌شده روی سرور نیست", "The requested range isn't on the server", "غوښتل شوې برخه په سرور کې نشته")
  fun resumedAt(pct: Any?): String = t("ادامه از ${asIs(pct)}٪", "Continuing from $pct%", "له $pct٪ څخه دوام")
  fun retrying(n: Any?, of: Any?, sec: Any): String = t(
    "اتصال قطع شد — تلاشِ ${asIs(n)} از ${asIs(of)}، ${asIs(sec)} ثانیهٔ دیگر",
    "Connection lost — try $n of $of, in $sec ${pl(sec, "second", "seconds")}",
    "اړیکه پرې شوه — $n هڅه له $of څخه، $sec ثانیې وروسته",
  )
  val waitingNet: String get() = t("منتظرِ اینترنت…", "Waiting for the internet…", "د انټرنېټ په تمه…")
  fun resumeNote(pct: Any?): String = t(" (ادامه از ${asIs(pct)}٪)", " (continuing from $pct%)", " (له $pct٪ څخه دوام)")
  fun dlNoAnswer(code: Any?): String = t(
    "سرورِ دانلود جواب نداد (${asIs(code)})",
    "The download server didn't answer ($code)",
    "د ډاونلوډ سرور ځواب ورنه کړ ($code)",
  )
  val netFailed: String get() = t(
    "اینترنت وصل نشد — بعد دوباره بزنید؛ از همین‌جا ادامه پیدا می‌کند",
    "The internet didn't connect — tap again later; it continues from here",
    "انټرنېټ وصل نه شو — وروسته بیا یې ووهئ؛ له همدې ځایه دوام کوي",
  )
  val keepsChanging: String get() = t(
    "فایلِ سرور پشتِ هم عوض می‌شود — کمی بعد دوباره بزنید",
    "The server file keeps changing — tap again a little later",
    "د سرور فایل پرله‌پسې بدلېږي — لږ وروسته بیا یې ووهئ",
  )
  val tooLarge: String get() = t(
    "فایلِ گرفته‌شده از اندازهٔ انتشار بزرگ‌تر شد — دوباره بزنید",
    "The downloaded file got bigger than the release size — tap again",
    "اخیستل شوی فایل د خپرونې له اندازې لوی شو — بیا یې ووهئ",
  )
  val shortAnswer: String get() = t("پاسخ کوتاه بود", "The answer was short", "ځواب لنډ و")
  val connLost: String get() = t("اتصال قطع شد", "Connection lost", "اړیکه پرې شوه")
  fun dlGaveUp(tries: Any, why: Any?): String = t(
    "دانلود با ${asIs(tries)} بار تلاش تمام نشد (${asIs(why)})؛ با زدنِ دوباره از همان‌جا ادامه پیدا می‌کند",
    "The download didn't finish after $tries ${pl(tries, "try", "tries")} ($why); tapping again continues from there",
    "ډاونلوډ له $tries هڅو وروسته بشپړ نه شو ($why)؛ په بیا وهلو له همغه ځایه دوام کوي",
  )
  fun dlGaveUpAt(tries: Any, why: Any?, pct: Any?): String = t(
    "دانلود با ${asIs(tries)} بار تلاش تمام نشد (${asIs(why)})؛ ${asIs(pct)}٪ گرفته شده و با زدنِ دوباره از همان‌جا ادامه پیدا می‌کند",
    "The download didn't finish after $tries ${pl(tries, "try", "tries")} ($why); $pct% was downloaded and tapping again continues from there",
    "ډاونلوډ له $tries هڅو وروسته بشپړ نه شو ($why)؛ $pct٪ اخیستل شوی او په بیا وهلو له همغه ځایه دوام کوي",
  )
  fun oldNotDeleted(name: Any?): String = t(
    "فایلِ قبلیِ ${asIs(name)} پاک نشد",
    "The previous file $name wasn't deleted",
    "پخوانی فایل $name پاک نه شو",
  )

  // ── بررسی و نصب (`UpdateManager`، `Updater`) ──
  val checking: String get() = t("در حال بررسی…", "Checking…", "کتل کېږي…")
  val upToDate: String get() = t("نسخهٔ شما تازه‌ترین است.", "Your version is the latest.", "ستاسو نسخه تر ټولو نوې ده.")
  fun alreadyDownloaded(v: Any?): String = t(
    "نسخهٔ ${asIs(v)} از قبل گرفته شده — آمادهٔ نصب.",
    "Version $v is already downloaded — ready to install.",
    "نسخه $v له مخکې اخیستل شوې — د نصب لپاره چمتو.",
  )
  val checkFailed: String get() = t("بررسی ناموفق بود", "The check didn't work", "کتنه ناکامه شوه")
  val downloadFailed: String get() = t("دانلود ناموفق بود", "The download didn't work", "ډاونلوډ ناکام شو")
  val needInstallPermission: String get() = t(
    "فایل آماده است، ولی گوشی هنوز اجازهٔ نصب از این برنامه را نداده. دکمهٔ زیر را بزنید، «اجازه» را روشن کنید و برگردید.",
    "The file is ready, but the phone hasn't allowed installing from this app yet. Tap the button below, turn on «Allow» and come back.",
    "فایل چمتو دی، خو ټیلیفون لا له دې پروګرام د نصب اجازه نه ده ورکړې. لاندې تڼۍ ووهئ، «اجازه» روښانه کړئ او بېرته راشئ.",
  )
  val installerFailed: String get() = t("نصب‌کنندهٔ اندروید باز نشد", "Android's installer didn't open", "د اندروید نصبوونکی خلاص نه شو")
  val permissionScreenFailed: String get() = t(
    "صفحهٔ تنظیماتِ اجازهٔ نصب باز نشد",
    "The install-permission settings page didn't open",
    "د نصب د اجازې د امستنو پاڼه خلاصه نه شوه",
  )
  val browserFailed: String get() = t("مرورگر باز نشد", "The browser didn't open", "براوزر خلاص نه شو")
  val downloadStopped: String get() = t(
    "دانلود متوقف شد — با زدنِ دوباره از همین‌جا ادامه پیدا می‌کند.",
    "The download stopped — tapping again continues from here.",
    "ډاونلوډ ودرېد — په بیا وهلو له همدې ځایه دوام کوي.",
  )
  val badRepo: String get() = t("نشانیِ به‌روزرسانی درست نیست", "The update address isn't valid", "د تازه کولو پته سمه نه ده")
  val noRelease: String get() = t("نسخه‌ای برای دانلود پیدا نشد", "No version was found to download", "د ډاونلوډ لپاره نسخه پیدا نه شوه")
  val notApk: String get() = t(
    "چیزی که گرفته شد فایلِ نصبی نیست — دوباره بزنید",
    "What was downloaded isn't an install file — tap again",
    "څه چې واخیستل شول د نصب فایل نه دی — بیا یې ووهئ",
  )
  val apkSizeMismatch: String get() = t(
    "اندازهٔ فایلِ گرفته‌شده با انتشار نمی‌خواند — دوباره بزنید",
    "The downloaded file's size doesn't match the release — tap again",
    "د اخیستل شوي فایل اندازه له خپرونې سره سمون نه خوري — بیا یې ووهئ",
  )
  val noSignedList: String get() = t(
    "این نسخه فهرستِ امضاشده ندارد؛ برای امنیت نصب نشد",
    "This version has no signed list; it wasn't installed for safety",
    "دا نسخه لاسلیک شوی لېست نه لري؛ د امنیت لپاره نصب نه شوه",
  )
  val noChecksums: String get() = t(
    "فهرستِ جمعِ کنترلی گرفته نشد؛ برای امنیت نصب نشد — دوباره بزنید",
    "The checksum list couldn't be downloaded; it wasn't installed for safety — tap again",
    "د کنټرولي جمع لېست وانه خیستل شو؛ د امنیت لپاره نصب نه شوه — بیا یې ووهئ",
  )
  val badSignature: String get() = t(
    "امضای این نسخه با کلیدِ برنامه نمی‌خواند؛ نصب نشد",
    "This version's signature doesn't match the app's key; it wasn't installed",
    "د دې نسخې لاسلیک د پروګرام له کیلي سره سمون نه خوري؛ نصب نه شوه",
  )
  val notInSignedList: String get() = t(
    "این نسخه در فهرستِ امضاشده نیست؛ نصب نشد",
    "This version isn't in the signed list; it wasn't installed",
    "دا نسخه په لاسلیک شوي لېست کې نشته؛ نصب نه شوه",
  )
  val hashMismatch: String get() = t(
    "فایلِ گرفته‌شده با نسخهٔ منتشرشده یکی نیست؛ پاک شد — دوباره بزنید",
    "The downloaded file isn't the same as the published version; it was deleted — tap again",
    "اخیستل شوی فایل له خپره شوې نسخې سره یو نه دی؛ پاک شو — بیا یې ووهئ",
  )
  //  ⛔ نامِ میزبانِ فایل‌های نسخه و کدِ HTTP روی صفحه نمی‌آید («هیچ جزئیاتِ فنی/زیرساخت در صفحه»، `docs/RULES-fa.md`)
  val githubLimited: String get() = t(
    "سرورِ به‌روزرسانی فعلاً جواب نمی‌دهد — کمی بعد دوباره بزنید",
    "The update server isn't answering right now — tap again a little later",
    "د تازه کولو سرور اوس ځواب نه ورکوي — لږ وروسته بیا یې ووهئ",
  )
  val githubNoFile: String get() = t(
    "فایلِ نسخهٔ تازه پیدا نشد",
    "The new version's file wasn't found",
    "د نوې نسخې فایل پیدا نه شو",
  )
  //  بی کدِ HTTP: کد فقط در لاگ/گزارشِ خطاست، نه روی صفحه (صدازننده‌ها `update/Updater.kt` و `UpdaterDesk.kt`)
  val updateServerError: String get() = t(
    "سرورِ به‌روزرسانی خطا داد — کمی بعد دوباره بزنید",
    "The update server gave an error — tap again a little later",
    "د تازه کولو سرور تېروتنه ورکړه — لږ وروسته بیا یې ووهئ",
  )
  val updateServerNoAnswer: String get() = t(
    "سرورِ به‌روزرسانی جواب نداد",
    "The update server didn't answer",
    "د تازه کولو سرور ځواب ورنه کړ",
  )

  // ── کامپیوتر (`UpdaterDesk`) ──
  val checksumMismatch: String get() = t(
    "جمعِ کنترلیِ فایل با نسخهٔ منتشرشده یکی نیست — دوباره دانلود کنید",
    "The file's checksum isn't the same as the published version — download it again",
    "د فایل کنټرولي جمع له خپره شوې نسخې سره یو نه دی — بیا یې ډاونلوډ کړئ",
  )
  val installerFailedDesk: String get() = t("نصب‌کننده باز نشد", "The installer didn't open", "نصبوونکی خلاص نه شو")
  val githubBusy: String get() = t(
    "سرورِ به‌روزرسانی فعلاً جواب نمی‌دهد — کمی بعد دوباره بزنید",
    "The update server isn't answering right now — tap again a little later",
    "د تازه کولو سرور اوس ځواب نه ورکوي — لږ وروسته بیا یې ووهئ",
  )

  // ── برنامه با نامِ تازه (`AppMove`، برگهٔ `AppMoveSheet`) — تصمیمِ صاحب‌کار ۱۴۰۵/۰۸/۲۴ ──
  val movedBar: String get() = t(
    "نسخهٔ تازهٔ برنامه با نامِ تازه آمده — این‌جا بزنید",
    "The new version of the app comes under a new name — tap here",
    "د اپ نوې نسخه په نوي نوم راغلې — دلته ووهئ",
  )
  val movedAction: String get() = t("راهنما", "What to do", "لارښود")
  val movedTitle: String get() = t("برنامه با نامِ تازه آمده", "The app has a new name", "اپ نوی نوم لري")
  val movedBody: String get() = t(
    "نسخهٔ تازهٔ برنامه با نامِ تازه آمده: اول مطمئن شوید همه‌چیز همگام شده، برنامهٔ تازه را نصب و وارد شوید، بعد این برنامهٔ قدیمی را پاک کنید.",
    "The new version of the app comes under a new name: first make sure everything is synced, install the new app and sign in, then remove this old app.",
    "د اپ نوې نسخه په نوي نوم راغلې: لومړی ډاډ ترلاسه کړئ چې هر څه همغږي شوي، نوی اپ نصب کړئ او ننوځئ، بیا دا زوړ اپ لرې کړئ.",
  )
  val movedSide: String get() = t(
    "برنامهٔ تازه کنارِ همین برنامه نصب می‌شود، نه رویش؛ این برنامه و دفترش سرِ جایش می‌ماند تا خودتان پاکش کنید.",
    "The new app installs next to this one, not over it; this app and its ledger stay until you remove it yourself.",
    "نوی اپ د دې اپ تر څنګ نصبېږي، نه پر هغه؛ دا اپ او دفتر یې تر هغه پاتې کېږي چې تاسو یې پخپله لرې کړئ.",
  )
  fun movedPending(n: Any): String = t(
    "هنوز ${asIs(n)} تغییرِ این دستگاه به سرور نرسیده. تا همه نرسیده‌اند این برنامه را پاک نکنید؛ وگرنه آن کارها در برنامهٔ تازه نیستند.",
    "$n ${pl(n, "change", "changes")} from this device ${pl(n, "hasn't", "haven't")} reached the server yet. Don't remove this app until they all have; otherwise that work won't be in the new app.",
    "د دې وسیلې $n بدلونونه لا سرور ته نه دي رسېدلي. تر څو چې ټول ونه رسېږي دا اپ مه لرې کوئ؛ کنه هغه کارونه په نوي اپ کې نه وي.",
  )
  val movedNotSynced: String get() = t(
    "دفترِ این دستگاه هنوز کامل به سرور نرسیده. «همین حالا همگام کن» را بزنید و تا آن وقت این برنامه را پاک نکنید.",
    "This device's ledger hasn't fully reached the server yet. Tap “Sync now”, and don't remove this app until then.",
    "د دې وسیلې دفتر لا بشپړ سرور ته نه دی رسېدلی. «همدا اوس همغږي کړئ» ووهئ او تر هغه دا اپ مه لرې کوئ.",
  )
  val movedNotSignedIn: String get() = t(
    "این دستگاه به حسابی وصل نیست، پس دفترش فقط روی همین دستگاه است. اول در همین برنامه وارد شوید و بگذارید همه‌چیز همگام شود؛ تا آن وقت این برنامه را پاک نکنید.",
    "This device isn't signed in to an account, so its ledger is only on this device. Sign in here first and let everything sync; don't remove this app until then.",
    "دا وسیله په کوم حساب کې نه ده ننوتې، نو دفتر یې یوازې په همدې وسیله کې دی. لومړی په همدې اپ کې ننوځئ او پرېږدئ چې هر څه همغږي شي؛ تر هغه دا اپ مه لرې کوئ.",
  )
  val movedSafe: String get() = t(
    "همه‌چیز به سرور رسیده. وقتی در برنامهٔ تازه وارد شدید و دفترتان را آن‌جا دیدید، می‌توانید این برنامه را پاک کنید.",
    "Everything has reached the server. Once you've signed in to the new app and see your ledger there, you can remove this app.",
    "ټول شیان سرور ته رسېدلي. کله چې په نوي اپ کې ننوتئ او خپل دفتر مو هلته ولید، دا اپ لرې کولی شئ.",
  )
  //  e-newinstall: گام‌های جابه‌جایی (`AppMove.steps`)
  val movedStepsTitle: String get() = t("جابه‌جایی بی گم شدنِ داده", "Moving without losing data", "پرته له معلوماتو د ورکېدو لېږد")
  val movedStepKeep: String get() = t(
    "این برنامهٔ قدیم را نگه دارید تا «همگام‌سازی»اش بگوید «همه‌چیز روی سرور نشسته».",
    "Keep this old app until its “Sync” page says “Everything is on the server”.",
    "دا زوړ اپ وساتئ تر څو یې «همغږي» ووايي «هر څه په سرور کې دي».",
  )
  val movedStepAccount: String get() = t(
    "در برنامهٔ تازه با همان حساب (همان ایمیل) وارد شوید — نه «ادامه بدون حساب» و نه حسابِ تازه. دکان، کالاها، موجودی و مشتری‌ها از سرور می‌آیند.",
    "In the new app, sign in with the same account (the same email) — not “Continue without an account” and not a new account. The shop, products, stock and customers come from the server.",
    "په نوي اپ کې په هماغه حساب (هماغه برېښنالیک) ننوځئ — نه «بې حسابه دوام» او نه نوی حساب. دوکان، توکي، زېرمه او پیرودونکي له سرور راځي.",
  )
  val movedStepBackup: String get() = t(
    "اگر چیزی همگام نشد: در همین برنامهٔ قدیم «پشتیبان و بازگردانی» ⇐ پشتیبانِ کامل بگیرید و فایل را در «دانلودها»ی گوشی ذخیره کنید؛ بعد در برنامهٔ تازه (واردشده با همان حساب) همان‌جا «بازگردانی» را بزنید و همان فایل را بردارید.",
    "If something didn't sync: in this old app open “Backup & restore” ⇒ take a full backup and save the file in the phone's “Downloads”; then in the new app (signed in with the same account) tap “Restore” in the same place and pick that file.",
    "که څه همغږي نه شول: په همدې زاړه اپ کې «بیک‌اپ او بېرته راوستل» ⇐ بشپړ بیک‌اپ واخلئ او دوتنه د تلیفون په «ډاونلوډونو» کې خوندي کړئ؛ بیا په نوي اپ کې (په هماغه حساب ننوتي) په همغه ځای «بېرته راوستل» ووهئ او هماغه دوتنه وټاکئ.",
  )
  val movedInstall: String get() = t("گرفتن و نصبِ برنامهٔ تازه", "Get and install the new app", "نوی اپ ترلاسه او نصب کړئ")
  val movedInstallReady: String get() = t("نصبِ برنامهٔ تازه", "Install the new app", "نوی اپ نصب کړئ")
  val movedLater: String get() = t("بعداً", "Later", "وروسته")
}
