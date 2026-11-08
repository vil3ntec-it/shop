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
  val badRepo: String get() = t("آدرس مخزن درست نیست", "The repository address isn't valid", "د زېرمې پته سمه نه ده")
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
  val githubLimited: String get() = t(
    "گیت‌هاب فعلاً جواب نمی‌دهد (سهمیهٔ ساعتی تمام شده) — کمی بعد دوباره بزنید",
    "GitHub isn't answering right now (the hourly quota is used up) — tap again a little later",
    "ګیټ‌هب اوس ځواب نه ورکوي (د ساعت برخه خلاصه شوې) — لږ وروسته بیا یې ووهئ",
  )
  val githubNoFile: String get() = t(
    "فایلِ نسخهٔ تازه روی گیت‌هاب پیدا نشد",
    "The new version's file wasn't found on GitHub",
    "د نوې نسخې فایل په ګیټ‌هب کې پیدا نه شو",
  )
  fun githubError(code: Any?): String = t(
    "گیت‌هاب خطا داد (${asIs(code)}) — کمی بعد دوباره بزنید",
    "GitHub gave an error ($code) — tap again a little later",
    "ګیټ‌هب تېروتنه ورکړه ($code) — لږ وروسته بیا یې ووهئ",
  )
  fun updateServerNoAnswer(code: Any?): String = t(
    "سرورِ به‌روزرسانی جواب نداد (${asIs(code)})",
    "The update server didn't answer ($code)",
    "د تازه کولو سرور ځواب ورنه کړ ($code)",
  )

  // ── کامپیوتر (`UpdaterDesk`) ──
  val checksumMismatch: String get() = t(
    "جمعِ کنترلیِ فایل با نسخهٔ منتشرشده یکی نیست — دوباره دانلود کنید",
    "The file's checksum isn't the same as the published version — download it again",
    "د فایل کنټرولي جمع له خپره شوې نسخې سره یو نه دی — بیا یې ډاونلوډ کړئ",
  )
  val installerFailedDesk: String get() = t("نصب‌کننده باز نشد", "The installer didn't open", "نصبوونکی خلاص نه شو")
  val githubBusy: String get() = t(
    "گیت‌هاب فعلاً جواب نمی‌دهد — کمی بعد دوباره بزنید",
    "GitHub isn't answering right now — tap again a little later",
    "ګیټ‌هب اوس ځواب نه ورکوي — لږ وروسته بیا یې ووهئ",
  )
}
