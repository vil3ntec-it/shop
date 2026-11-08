package ir.vil3ntec.tohid.core.i18n

/**
 *  پیام‌های لایهٔ دفتر (`data/`) بیرون از فروش و کالا — رمزِ دفتر، پشتیبان و دیسک، ورود با گوگل و اثرِ انگشت، اعلان‌ها و یادآوری‌ها،
 *  عکسِ گفت‌وگو، صفِ ویترین و پاسخِ سرور — و چند صفحهٔ زندهٔ قدیمیِ `ui/screens` (LOC-08 دری، دورِ ۷). پیش‌تر متنِ خامِ فارسی بودند؛ فارسی
 *  بایت‌به‌بایت همان پیشین، هر مقدار با `asIs`. قاعده‌ها سرِ `I18n.kt`.
 */
@Suppress("unused")
object StrAppData {

  // ── رمزِ دفتر (`AtRest`، `AtRestKeys`) ──
  val keyUnavailable: String get() = t("کلیدِ رمز در دسترس نیست", "The encryption key isn't available", "د کوډ کولو کیلي شتون نه لري")
  fun keyNote(where: Any?): String = t(
    "کلید در ${asIs(where)} است و از برنامه بیرون نمی‌آید. با پشتیبانِ خودکار یا انتقالِ اندروید به گوشیِ تازه نمی‌رود؛ برای همین وقتی رمز روشن است، دفتر و حالِ همگام‌سازی هم در آن پشتیبان نمی‌روند و گوشیِ تازه دفتر را از سرور می‌گیرد.",
    "The key is in $where and never leaves the app. It doesn't move with automatic backup or an Android transfer to a new phone; so while encryption is on, the ledger and sync state aren't in that backup either and the new phone gets the ledger from the server.",
    "کیلي په $where کې ده او له پروګرام نه وځي. له خپلکاره بک‌اپ یا نوي ټیلیفون ته د اندروید له لېږد سره نه ځي؛ نو کله چې کوډ روښانه وي، دفتر او د همغږۍ حالت هم په هغه بک‌اپ کې نه ځي او نوی ټیلیفون دفتر له سرور اخلي.",
  )
  fun keyChanged(id: Any?): String = t(
    "کلیدِ اصلیِ دستگاه عوض شده (${asIs(id)})",
    "The device's main key changed ($id)",
    "د وسیلې اصلي کیلي بدله شوې ($id)",
  )
  val keyCut: String get() = t("کلیدِ پیچیده بریده است", "The wrapped key is cut off", "پېچل شوې کیلي پرې شوې ده")
  val keystoreMissing: String get() = t(
    "کلیدِ Keystore روی این دستگاه نیست",
    "The Keystore key isn't on this device",
    "د Keystore کیلي په دې وسیله کې نشته",
  )
  val keystoreMismatch: String get() = t(
    "کلیدِ Keystore با کلیددان نمی‌خواند",
    "The Keystore key doesn't match the key file",
    "د Keystore کیلي له کیلي‌دان سره سمون نه خوري",
  )
  fun keystoreDead(why: Any?): String = t(
    "کلیدِ Keystore برای همیشه از کار افتاده: ${asIs(why)}",
    "The Keystore key stopped working for good: $why",
    "د Keystore کیلي د تل لپاره له کاره ولوېده: $why",
  )
  fun keystoreNoAnswer(why: Any?): String = t("Keystore جواب نداد: ${asIs(why)}", "Keystore didn't answer: $why", "Keystore ځواب ورنه کړ: $why")

  // ── پشتیبان و دیسک (`BackupBundle`، `BackupClock`، `BackupMirror`، `DiskWrite`، `ShopStore`) ──
  val notOurBackup: String get() = t("این فایل، پشتیبانِ فروشگاه من نیست", "This file isn't a My Shop backup", "دا فایل د زما دوکان بک‌اپ نه دی")
  val noBackupYet: String get() = t("هنوز پشتیبانی گرفته نشده", "No backup has been made yet", "لا هېڅ بک‌اپ نه دی اخیستل شوی")
  fun yesterdayOn(date: Any?): String = t("دیروز — ${asIs(date)}", "Yesterday — $date", "پرون — $date")
  fun daysAgoOn(date: Any?, days: Any): String = t(
    "${asIs(date)} (${asIs(days)} روز پیش)",
    "$date ($days ${pl(days, "day", "days")} ago)",
    "$date ($days ورځې مخکې)",
  )
  val noLedgerToBackup: String get() = t("دفتری برای پشتیبان نیست", "There is no ledger to back up", "د بک‌اپ لپاره دفتر نشته")
  val backupFolderMissing: String get() = t("پوشهٔ پشتیبان در دسترس نیست", "The backup folder isn't available", "د بک‌اپ فولډر شتون نه لري")
  fun folderNotWritable(path: Any?): String = t(
    "در پوشهٔ «${asIs(path)}» نمی‌شود نوشت",
    "Can't write in folder «$path»",
    "په «$path» فولډر کې نه شي لیکل کېدای",
  )
  val backupNameFailed: String get() = t("نامِ فایلِ پشتیبان گذاشته نشد", "The backup file's name couldn't be set", "د بک‌اپ فایل نوم کېښودل نه شو")
  fun folderUnknown(path: Any?): String = t("پوشهٔ «${asIs(path)}» معلوم نیست", "The folder of «$path» is unknown", "د «$path» فولډر معلوم نه دی")
  fun folderNotMade(path: Any?): String = t("پوشهٔ «${asIs(path)}» ساخته نشد", "Folder «$path» couldn't be made", "«$path» فولډر جوړ نه شو")
  val diskWriteFailed: String get() = t("نوشتن روی دیسک نشد", "Writing to the disk didn't work", "په ډیسک لیکل ونه شول")
  fun notReplaced(name: Any?): String = t(
    "«${asIs(name)}» روی دیسک جایگزین نشد",
    "«$name» wasn't replaced on the disk",
    "«$name» په ډیسک کې ونه بدل شو",
  )
  fun notReplacedWhy(name: Any?, why: Any?): String = t(
    "«${asIs(name)}» روی دیسک جایگزین نشد: ${asIs(why)}",
    "«$name» wasn't replaced on the disk: $why",
    "«$name» په ډیسک کې ونه بدل شو: $why",
  )
  val ledgerOpenNoRebuild: String get() = t(
    "دفترِ این دستگاه باز است؛ بازسازی لازم نیست.",
    "This device's ledger is open; no rebuild is needed.",
    "د دې وسیلې دفتر خلاص دی؛ بیا جوړولو ته اړتیا نشته.",
  )
  val rebuildV1Only: String get() = t(
    "بازسازی از سرور فعلاً فقط با همگام‌سازیِ نسخهٔ ۱ است؛ هیچ چیزی پاک نشده.",
    "Rebuilding from the server is only with sync version 1 for now; nothing was deleted.",
    "له سرور بیا جوړول اوس یوازې د همغږۍ له ۱ نسخې سره دي؛ هېڅ شی پاک نه شو.",
  )
  val backupRestored: String get() = t("پشتیبان بازیابی شد.", "The backup was restored.", "بک‌اپ بېرته راوړل شو.")
  fun restoreFailed(why: Any?): String = t(
    "بازیابی انجام نشد (${asIs(why)}). هیچ چیزی پاک نشده.",
    "The restore didn't work ($why). Nothing was deleted.",
    "بېرته راوړل ونه شول ($why). هېڅ شی پاک نه شو.",
  )
  fun rebuildFailed(why: Any?): String = t(
    "بازسازی انجام نشد (${asIs(why)}). هیچ چیزی پاک نشده؛ دوباره امتحان کنید.",
    "The rebuild didn't work ($why). Nothing was deleted; try again.",
    "بیا جوړول ونه شول ($why). هېڅ شی پاک نه شو؛ بیا هڅه وکړئ.",
  )
  fun queueLeft(n: Any): String = t(
    "${asIs(n)} تغییرِ این دستگاه هنوز به سرور نرسیده؛ اول باید برسند تا گم نشوند. به اینترنت وصل شوید و دوباره امتحان کنید.",
    "$n ${pl(n, "change", "changes")} from this device haven't reached the server yet; they must arrive first so they aren't lost. Connect to the internet and try again.",
    "د دې وسیلې $n بدلونونه لا سرور ته نه دي رسېدلي؛ لومړی باید ورسېږي چې ورک نه شي. انټرنېټ سره وصل شئ او بیا هڅه وکړئ.",
  )
  fun rebuiltRows(n: Any): String = t(
    "دفتر از سرور بازسازی شد (${asIs(n)} ردیف).",
    "The ledger was rebuilt from the server ($n ${pl(n, "row", "rows")}).",
    "دفتر له سرور بیا جوړ شو ($n کرښې).",
  )
  val backupRowsNotAdded: String get() = t(
    " ردیف‌هایی که فقط در پشتیبان بودند افزوده نشدند: معلوم نیست پس از پشتیبان حذف شده‌اند یا نه.",
    " Rows that were only in the backup weren't added: it isn't known whether they were deleted after the backup.",
    " هغه کرښې چې یوازې په بک‌اپ کې وې ور ګډې نه شوې: معلوم نه ده چې له بک‌اپ وروسته ړنګې شوې که نه.",
  )
  fun backupRowsAdded(done: Any?, n: Any): String = t(
    "${asIs(done)} ${asIs(n)} ردیفی که هرگز به سرور نرفته بود از پشتیبان افزوده شد.",
    "$done $n ${pl(n, "row", "rows")} that never went to the server were added from the backup.",
    "$done $n کرښې چې هېڅکله سرور ته نه وې تللې له بک‌اپ ور ګډې شوې.",
  )
  fun backupRowsNotWritten(done: Any?): String = t(
    "${asIs(done)} ولی ردیف‌های پشتیبان نوشته نشدند؛ دوباره بازیابی کنید.",
    "$done but the backup rows weren't written; restore again.",
    "$done خو د بک‌اپ کرښې ونه لیکل شوې؛ بیا یې بېرته راوړئ.",
  )
  val rebuiltNotSettled: String get() = t(
    "دفترِ بازسازی‌شده جا نیفتاد؛ با «دوباره امتحان» یا باز شدنِ دوبارهٔ برنامه تمام می‌شود",
    "The rebuilt ledger didn't settle in; it finishes with «Try again» or when the app opens again",
    "بیا جوړ شوی دفتر ځای پر ځای نه شو؛ په «بیا هڅه» یا د پروګرام په بیا خلاصېدو بشپړېږي",
  )
  val ledgerNotWritten: String get() = t("دفتر روی دیسک نوشته نشد", "The ledger wasn't written to the disk", "دفتر په ډیسک ونه لیکل شو")
  val unknownError: String get() = t("خطای نامشخص", "Unknown error", "نامعلومه تېروتنه")
  val staleBase: String get() = t(
    "ثبت نشد — این صفحه از دفتری ساخته شده بود که در این فاصله چند بار از دستگاه‌های دیگر تازه شد، و ثبتش ردیف‌های تازه را پاک می‌کرد. صفحه را دوباره باز کنید و دوباره ثبت کنید؛ چیزی پاک نشده.",
    "Not saved — this page was built from a ledger that was refreshed several times from other devices in the meantime, and saving it would erase the new rows. Open the page again and save again; nothing was deleted.",
    "ثبت نه شو — دا پاڼه له هغه دفتر جوړه شوې وه چې په دې منځ کې څو ځله له نورو وسیلو تازه شو، او ثبت به یې نوې کرښې پاکې کړې وای. پاڼه بیا خلاصه او بیا ثبت کړئ؛ هېڅ شی پاک نه شو.",
  )
  fun editsDropped(n: Any): String = t(
    "ثبت شد، جز ویرایشِ شما روی ${asIs(n)} ردیف که در همین فاصله در دستگاهِ دیگری حذف شده بود؛ آن ردیف‌ها حذف‌شده می‌مانند. اگر لازم‌اند، دوباره بسازیدشان.",
    "Saved, except your edits on $n ${pl(n, "row", "rows")} that were deleted on another device in the meantime; those rows stay deleted. If you need them, make them again.",
    "ثبت شو، پرته له هغو $n کرښو چې په دې منځ کې په بله وسیله ړنګې شوې وې؛ هغه کرښې ړنګې پاتې کېږي. که پکار وي، بیا یې جوړې کړئ.",
  )
  val staleUnreadable: String get() = t(
    "دفترِ روی دیسک در این فاصله عوض شده بود و دوباره خوانده نشد، پس این تغییر ثبت نشد تا چیزی از آن رونویسی نشود. دوباره امتحان کنید.",
    "The ledger on disk changed in the meantime and couldn't be read again, so this change wasn't saved so nothing in it is overwritten. Try again.",
    "په ډیسک کې دفتر په دې منځ کې بدل شوی و او بیا ونه لوستل شو، نو دا بدلون ثبت نه شو چې څه پرې ونه لیکل شي. بیا هڅه وکړئ.",
  )
  val trainingSealed: String get() = t(
    "حالتِ آموزشی روشن است: دفترِ واقعی دست نمی‌خورد و چیزی به سرور نمی‌رود. برای کارِ واقعی از نوارِ بالا «خروج از آموزش» را بزنید.",
    "Training mode is on: the real ledger isn't touched and nothing goes to the server. For real work, tap «Leave training» in the top bar.",
    "د زده‌کړې حالت روښانه دی: اصلي دفتر نه لمسېږي او سرور ته څه نه ځي. د اصلي کار لپاره په پورتنۍ پټه کې «له زده‌کړې وتل» ووهئ.",
  )
  val unreadable: String get() = t(
    "دفترِ رمزدارِ این دستگاه باز نشد، پس این تغییر ثبت نشد تا دفترِ واقعی رونویسی نشود. از «تنظیمات ← امنیت» دوباره امتحان یا «بازسازی از سرور» را بزنید.",
    "This device's encrypted ledger didn't open, so this change wasn't saved so the real ledger isn't overwritten. In «Settings ← Security» try again or tap «Rebuild from server».",
    "د دې وسیلې کوډ شوی دفتر خلاص نه شو، نو دا بدلون ثبت نه شو چې اصلي دفتر پرې ونه لیکل شي. په «امستنې ← امنیت» کې بیا هڅه وکړئ یا «له سرور بیا جوړول» ووهئ.",
  )
  val needSignIn: String get() = t(
    "برای بازسازی از سرور اول وارد حساب شوید؛ یا از «داده‌ها» یک پشتیبان را بازیابی کنید. هیچ چیزی پاک نشده.",
    "To rebuild from the server, sign in first; or restore a backup from «Data». Nothing was deleted.",
    "له سرور د بیا جوړولو لپاره لومړی حساب ته ننوځئ؛ یا له «معلومات» څخه یو بک‌اپ بېرته راوړئ. هېڅ شی پاک نه شو.",
  )
  val needSignInSynced: String get() = t(
    "این دفتر پیش‌تر با سرور همگام شده؛ برای بازیابیِ پشتیبان اول وارد حساب شوید تا دفتر از سرور ساخته شود و ردیف‌های حذف‌شده زنده نشوند. هیچ چیزی پاک نشده.",
    "This ledger was synced with the server before; to restore a backup, sign in first so the ledger is built from the server and deleted rows don't come back. Nothing was deleted.",
    "دا دفتر مخکې له سرور سره همغږی شوی؛ د بک‌اپ د بېرته راوړلو لپاره لومړی حساب ته ننوځئ چې دفتر له سرور جوړ شي او ړنګې کرښې بېرته ژوندۍ نه شي. هېڅ شی پاک نه شو.",
  )
  val needNet: String get() = t(
    "برای بازسازی از سرور اینترنت لازم است؛ دفترِ خوانده‌نشده با پشتیبانِ کهنه جایگزین نمی‌شود تا ردیف‌های تازه‌ترِ سرور حذف نشوند. هیچ چیزی پاک نشده.",
    "Rebuilding from the server needs the internet; the unread ledger isn't replaced with an old backup so the server's newer rows aren't deleted. Nothing was deleted.",
    "له سرور بیا جوړول انټرنېټ غواړي؛ نالوستی دفتر په زاړه بک‌اپ نه بدلېږي چې د سرور نوې کرښې ړنګې نه شي. هېڅ شی پاک نه شو.",
  )

  // ── ورود با گوگل و اثرِ انگشت (`GoogleSignIn`، `Fingerprint`) ──
  val googleOff: String get() = t(
    "ورود با گوگل روی این سرور روشن نیست",
    "Google sign-in isn't turned on for this server",
    "په دې سرور د ګوګل ننوتل فعال نه دي",
  )
  val googleNoAccount: String get() = t(
    "روی این گوشی حساب گوگلی نیست، یا گوگل حسابی برای این برنامه پیدا نکرد — از تنظیماتِ گوشی یک حساب گوگل اضافه کنید و دوباره بزنید",
    "There's no Google account on this device, or Google found no account for this app — add a Google account in the device settings and tap again",
    "په دې وسیله د ګوګل حساب نشته، یا ګوګل د دې پروګرام لپاره حساب پیدا نه کړ — د وسیلې په امستنو کې د ګوګل حساب ور ګډ کړئ او بیا یې ووهئ",
  )
  val googleHint: String get() = t(
    "اگر تکرار شد: شناسهٔ کلاینتِ سرور باید از نوعِ Web باشد و امضای SHA-1 این برنامه در Google Cloud ثبت شده باشد.",
    "If it happens again: the server's client id must be of type Web and this app's SHA-1 signature must be registered in Google Cloud.",
    "که بیا پېښ شو: د سرور د کلاینټ پېژندنه باید د Web ډول وي او د دې پروګرام SHA-1 لاسلیک باید په Google Cloud کې ثبت وي.",
  )
  val googleBadAnswer: String get() = t("پاسخِ گوگل شناخته نشد", "Google's answer wasn't recognised", "د ګوګل ځواب ونه پېژندل شو")
  val googleBadToken: String get() = t("توکنِ گوگل خوانده نشد", "The Google token couldn't be read", "د ګوګل ټوکن ونه لوستل شو")
  val fpTitle: String get() = t("باز کردن فروشگاه من", "Open My Shop", "زما دوکان خلاصول")
  val fpSubtitle: String get() = t("انگشتتان را روی حسگر بگذارید", "Put your finger on the sensor", "خپله ګوته پر سېنسر کېږدئ")
  val fpUsePin: String get() = t("با رمز وارد می‌شوم", "I'll sign in with the PIN", "په رمز ننوځم")

  // ── اعلان و یادآوری (`Watchman`، `Reminders`، `PhotoBot`) ──
  val chStockTitle: String get() = t("کالای تمام‌شده", "Out-of-stock items", "خلاص شوي توکي")
  val chStockAbout: String get() = t("وقتی موجودی کالایی صفر شود", "When an item's stock reaches zero", "کله چې د یو توکي موجودي صفر شي")
  val chDebtTitle: String get() = t("قرض زیاد", "High debt", "ډېر پور")
  val chDebtAbout: String get() = t("وقتی قرضِ یک نفر از حد بگذرد", "When someone's debt goes over the limit", "کله چې د یو چا پور له حد واوړي")
  val chSubAbout: String get() = t("پیش از تمام شدن اشتراک", "Before the subscription ends", "د ګډون له ختمېدو مخکې")
  val chNewsTitle: String get() = t("خبر دکان", "Shop news", "د دوکان خبر")
  val chNewsAbout: String get() = t("کارِ بقیهٔ اعضای دکان", "What the other shop members do", "د دوکان د نورو غړو کار")
  fun itemOut(name: Any?): String = t("${asIs(name)} تمام شد", "$name ran out", "$name خلاص شو")
  fun itemsOut(n: Any?): String = t("${asIs(n)} کالا تمام شد", "$n items ran out", "$n توکي خلاص شول")
  fun debtHigh(name: Any?): String = t("قرضِ ${asIs(name)} زیاد شد", "$name's debt got high", "د $name پور ډېر شو")
  fun debtsHigh(n: Any?): String = t("${asIs(n)} نفر قرضِ زیاد دارند", "$n people have high debt", "$n کسان ډېر پور لري")
  fun nameAfn(name: Any?, amount: Any?): String = t("${asIs(name)}: ${asIs(amount)} افغانی", "$name: $amount afghani", "$name: $amount افغانۍ")
  val subEnded: String get() = t("اشتراک تمام شد", "The subscription ended", "ګډون پای ته ورسېد")
  val subEndedDo: String get() = t(
    "برای باز شدن دوبارهٔ صندوق فروش، اشتراک را تمدید کنید.",
    "Renew the subscription to open the sales till again.",
    "د پلور د دخل د بیا خلاصېدو لپاره ګډون نوی کړئ.",
  )
  val subTomorrow: String get() = t("اشتراک فردا تمام می‌شود", "The subscription ends tomorrow", "ګډون سبا پای ته رسېږي")
  val subTomorrowDo: String get() = t(
    "پیش از تمام شدن تمدید کنید تا کارتان نخوابد.",
    "Renew before it ends so your work doesn't stop.",
    "له پای ته رسېدو مخکې یې نوی کړئ چې کار مو ودرېږي نه.",
  )
  val subInDaysDo: String get() = t(
    "همین حالا تمدید کنید تا بعداً عجله نشود.",
    "Renew now so there's no rush later.",
    "همدا اوس یې نوی کړئ چې وروسته بیړه ونه شي.",
  )
  fun subInDays(days: Any): String = t(
    "اشتراک تا ${asIs(days)} روز دیگر تمام می‌شود",
    "The subscription ends in $days ${pl(days, "day", "days")}",
    "ګډون په $days ورځو کې پای ته رسېږي",
  )
  fun shopNews(n: Any): String = t("${asIs(n)} خبر تازه از دکان", "$n new ${pl(n, "update", "updates")} from the shop", "له دوکانه $n نوي خبرونه")
  val remChannel: String get() = t("یادآوری دکان", "Shop reminders", "د دوکان یادونه")
  val remChannelAbout: String get() = t(
    "موجودی تمام‌شده و قرض‌های سررسیده",
    "Stock that ran out and debts that are due",
    "خلاصه موجودي او هغه پورونه چې نېټه یې رسېدلې",
  )
  fun remOut(n: Any): String = t("${asIs(n)} کالا تمام شده", "$n ${pl(n, "item", "items")} ran out", "$n توکي خلاص شوي")
  fun remLow(n: Any): String = t(
    "${asIs(n)} کالا رو به اتمام است",
    "$n ${pl(n, "item is", "items are")} running low",
    "$n توکي د خلاصېدو په حال کې دي",
  )
  val remOwed: String get() = t("طلب از مشتریان", "Money owed by customers", "له پېرودونکو پور")
  fun remItemOut(name: Any?): String = t("${asIs(name)}: تمام شد", "$name: ran out", "$name: خلاص شو")
  fun remItemLeft(name: Any?, qty: Any?): String = t("${asIs(name)}: ${asIs(qty)} مانده", "$name: $qty left", "$name: $qty پاتې")
  fun remOwedSum(amount: Any?, n: Any): String = t(
    "${asIs(amount)} افغانی طلب از ${asIs(n)} نفر",
    "$amount afghani owed by $n ${pl(n, "person", "people")}",
    "$amount افغانۍ پور له $n کسانو",
  )
  val photosReady: String get() = t("همهٔ عکس‌ها از قبل آماده بودند", "All photos were already ready", "ټول انځورونه له مخکې چمتو وو")
  fun photosBetter(n: Any): String = t(
    "${asIs(n)} عکس بهتر شد — اصلِ هر کدام نگه داشته شد",
    "$n ${pl(n, "photo", "photos")} improved — each one's original was kept",
    "$n انځورونه ښه شول — د هر یو اصل وساتل شو",
  )
  fun photosRestored(n: Any): String = t(
    "${asIs(n)} عکس به اصلش برگشت",
    "$n ${pl(n, "photo", "photos")} went back to the original",
    "$n انځورونه خپل اصل ته ستانه شول",
  )

  // ── گفت‌وگو، خروجی و پاسخِ سرور (`ChatMedia`، `ShopChatSync`، `OwnerExport`، `repo/`) ──
  val notImageOrUnread: String get() = t(
    "این فایل عکس نیست یا خوانده نشد",
    "This file isn't a photo or couldn't be read",
    "دا فایل انځور نه دی یا ونه لوستل شو",
  )
  val photoNoFit: String get() = t(
    "عکس در سقفِ مجاز جا نشد",
    "The photo didn't fit within the allowed size",
    "انځور په اجازه لرونکې اندازه کې ځای نه شو",
  )
  val noVoice: String get() = t("ضبطِ صدا روی این دستگاه نیست", "Voice recording isn't available on this device", "په دې وسیله غږ ثبتول نشته")
  val chatFileGone: String get() = t(
    "فایلِ این پیام روی این دستگاه نمانده",
    "This message's file isn't on this device any more",
    "د دې پیغام فایل په دې وسیله پاتې نه دی",
  )
  val exportBad: String get() = t("فایلِ خروجی معتبر نیست", "The export file isn't valid", "د صادرولو فایل سم نه دی")
  val exportNotOurs: String get() = t(
    "این فایل، خروجیِ کاملِ فروشگاه من نیست",
    "This file isn't a full My Shop export",
    "دا فایل د زما دوکان بشپړ صادرول نه دی",
  )
  val exportNoVersion: String get() = t("نسخهٔ قالب در فایل نیست", "The format version isn't in the file", "د بڼې نسخه په فایل کې نشته")
  fun exportNewer(v: Any?): String = t(
    "این خروجی با قالبِ ${asIs(v)} ساخته شده و این نسخهٔ برنامه آن را نمی‌شناسد",
    "This export was made with format $v and this version of the app doesn't know it",
    "دا صادرول د $v بڼې سره جوړ شوی او د پروګرام دا نسخه یې نه پېژني",
  )
  val serverEmpty: String get() = t("پاسخِ سرور خالی بود", "The server's answer was empty", "د سرور ځواب تش و")
  fun noTokenSent(keys: Any?): String = t(
    "توکن در پاسخ نبود — سرور فرستاد: ${asIs(keys)}",
    "There was no token in the answer — the server sent: $keys",
    "په ځواب کې ټوکن نه و — سرور ولېږل: $keys",
  )
  val serverShapeBad: String get() = t(
    "پاسخِ سرور شکلِ درستی نداشت",
    "The server's answer wasn't in the right shape",
    "د سرور ځواب سمه بڼه نه درلوده",
  )
  val noToken: String get() = t("توکن نیامد", "The token didn't arrive", "ټوکن رانغی")
  val noPublicKey: String get() = t("کلید عمومی نیامد", "The public key didn't arrive", "عامه کیلي رانغله")
  val sqBadData: String get() = t(
    "دادهٔ این تغییر روی دستگاه خراب بود؛ دوباره ذخیره کنید.",
    "This change's data on the device was damaged; save it again.",
    "د دې بدلون معلومات په وسیله کې خراب وو؛ بیا یې خوندي کړئ.",
  )
  val sqSettingsSaved: String get() = t(
    "تنظیماتِ ویترین روی سرور ثبت شد",
    "Storefront settings were saved on the server",
    "د ویترین امستنې په سرور ثبت شوې",
  )
  val sqDeliverySaved: String get() = t("ارسال و زمان روی سرور ثبت شد", "Delivery and times were saved on the server", "لېږل او وخت په سرور ثبت شول")
  val sqMarketSaved: String get() = t(
    "نمایش در بازار روی سرور ثبت شد",
    "Showing in the market was saved on the server",
    "په بازار کې ښودل په سرور ثبت شول",
  )
  val sqListed: String get() = t(
    "ثبت شد — دکان در بازار نشان داده می‌شود",
    "Saved — the shop is shown in the market",
    "ثبت شو — دوکان په بازار کې ښودل کېږي",
  )
  val sqUnlisted: String get() = t(
    "ثبت شد — دکان در بازار نشان داده نمی‌شود",
    "Saved — the shop isn't shown in the market",
    "ثبت شو — دوکان په بازار کې نه ښودل کېږي",
  )
  val sqBadPresence: String get() = t(
    "اعلامِ ذخیره‌شده خراب بود؛ دوباره اعلام کنید.",
    "The saved announcement was damaged; announce again.",
    "خوندي شوی اعلان خراب و؛ بیا یې اعلان کړئ.",
  )
  fun sqSavedAt(time: Any?): String = t("ثبت شد — زمانِ سرور: ${asIs(time)}", "Saved — server time: $time", "ثبت شو — د سرور وخت: $time")

  // ── سربرگِ فاکتور و نقشِ کاربر (`ReceiptProfileStore`، `RoleGate`) ──
  val logoNotSaved: String get() = t("لوگو ذخیره نشد", "The logo wasn't saved", "لوګو خوندي نه شو")
  val headerNotUpdated: String get() = t("سربرگِ فاکتور به‌روز نشد", "The invoice header wasn't updated", "د بل سرپاڼه تازه نه شوه")
  val tblDebtors: String get() = t("قرض‌داران", "Debtors", "پوروړي")
  val tblTransactions: String get() = t("قرض و پرداخت", "Debts and payments", "پور او ورکړه")
  val tblExpenses: String get() = t("مصارف", "Expenses", "لګښتونه")
  val tblProducts: String get() = t("کالاها", "Items", "توکي")
  val tblPrices: String get() = t("قیمتِ کالا", "Item prices", "د توکو بیې")
  val tblStockIn: String get() = t("رسیدِ انبار", "Stock receipts", "د ګدام رسیدونه")
  val tblSales: String get() = t("فروش", "Sales", "پلور")
  val tblReturns: String get() = t("برگشتِ کالا", "Returns", "بېرته ورکړې")
  val tblSuppliers: String get() = t("تأمین‌کننده‌ها", "Suppliers", "برابروونکي")
  val tblPurchases: String get() = t("خرید", "Purchases", "پېرودونه")
  val tblSupplierPay: String get() = t("پرداخت به تأمین‌کننده", "Supplier payments", "برابروونکو ته ورکړې")
  val tblMoves: String get() = t("حرکتِ انبار", "Stock movements", "د ګدام خوځښتونه")
  val tblAudit: String get() = t("سابقهٔ عملیات", "Activity history", "د کارونو تاریخچه")

  // ── صفحه‌های زندهٔ قدیمیِ `ui/screens` (دورِ ۷) ──
  val versionUpdates: String get() = t("نسخه و به‌روزرسانی", "Version and updates", "نسخه او تازه کول")
  fun profitBy(dim: Any?): String = t("سود و حاشیه به تفکیک ${asIs(dim)}", "Profit and margin by $dim", "ګټه او حاشیه د $dim له مخې")
  val serverNoDb: String get() = t(
    "سرور بالاست ولی دیتابیسش وصل نیست",
    "The server is up but its database isn't connected",
    "سرور چالان دی خو ډېټابېس یې وصل نه دی",
  )
  val voicePrompt: String get() = t("بگویید دنبالِ چه می‌گردید", "Say what you are looking for", "ووایاست څه لټوئ")

  // ── کامپیوتر (`AtRestKeysDesk`، `GoogleSignInDesk`) ──
  val deskWhere: String get() = t(
    "فایلِ کلید در پوشهٔ دادهٔ همین کامپیوتر",
    "a key file in this computer's data folder",
    "د همدې کمپیوټر د معلوماتو په فولډر کې د کیلي فایل",
  )
  val deskNote1: String get() = t(
    "کلید در فایلی کنارِ داده‌هاست (پوشهٔ keys کنارِ پوشهٔ files در پوشهٔ دادهٔ برنامه)، نه در خودِ ویندوز یا مک. ",
    "The key is in a file next to the data (the keys folder next to the files folder in the app's data folder), not in Windows or macOS itself. ",
    "کیلي د معلوماتو تر څنګ په یو فایل کې ده (د پروګرام د معلوماتو په فولډر کې د files فولډر تر څنګ keys فولډر)، نه پخپله په وینډوز یا مک کې. ",
  )
  val deskNote2: String get() = t(
    "پس این رمز فقط وقتی کمک می‌کند که پوشهٔ files (دفتر و نسخه‌ها) جدا کپی یا فرستاده شود؛ ",
    "So this encryption only helps when the files folder (ledger and copies) is copied or sent on its own; ",
    "نو دا کوډ یوازې هغه وخت مرسته کوي چې د files فولډر (دفتر او نسخې) جلا کاپي یا ولېږل شي؛ ",
  )
  val deskNote3: String get() = t(
    "هر کس کلِ پوشهٔ داده یا دیسک را داشته باشد، کلید را هم دارد. ",
    "anyone who has the whole data folder or disk has the key too. ",
    "هر څوک چې د معلوماتو ټول فولډر یا ډیسک ولري، کیلي هم لري. ",
  )
  val deskAclOn: String get() = t(
    "روی ویندوز فایلِ کلید فقط برای حسابِ ویندوزِ خودتان، SYSTEM و مدیرانِ دستگاه خواندنی است (ACL).",
    "On Windows the key file can only be read by your own Windows account, SYSTEM and the computer's administrators (ACL).",
    "په وینډوز کې د کیلي فایل یوازې ستاسو د وینډوز حساب، SYSTEM او د وسیلې مدیرانو ته د لوستلو وړ دی (ACL).",
  )
  val deskAclOff: String get() = t(
    "روی ویندوز اجازهٔ فایلِ کلید محدود **نشد**؛ هر حسابی که پوشه را می‌خواند، کلید را هم می‌خواند.",
    "On Windows the key file's permission was **not** restricted; any account that reads the folder reads the key too.",
    "په وینډوز کې د کیلي فایل اجازه محدوده **نه شوه**؛ هر حساب چې فولډر لولي، کیلي هم لولي.",
  )
  val deskAclUnknown: String get() = t(
    "اجازهٔ فایلِ کلید روی ویندوز هنوز سنجیده نشده.",
    "The key file's permission on Windows hasn't been checked yet.",
    "په وینډوز کې د کیلي فایل اجازه لا نه ده کتل شوې.",
  )
  val deskAclUnix: String get() = t(
    "فایلِ کلید فقط برای حسابِ خودتان خواندنی است (۶۰۰).",
    "The key file can only be read by your own account (600).",
    "د کیلي فایل یوازې ستاسو حساب ته د لوستلو وړ دی (۶۰۰).",
  )
  val deskKeyBroken: String get() = t("فایلِ کلید خراب است", "The key file is damaged", "د کیلي فایل خراب دی")
  fun deskKeyChanged(id: Any?): String = t(
    "کلیدِ اصلیِ کامپیوتر عوض شده (${asIs(id)})",
    "The computer's main key changed ($id)",
    "د کمپیوټر اصلي کیلي بدله شوې ($id)",
  )
  val deskKeyMissing: String get() = t(
    "فایلِ کلیدِ رمز روی این کامپیوتر نیست",
    "The encryption key file isn't on this computer",
    "د کوډ کولو د کیلي فایل په دې کمپیوټر کې نشته",
  )
  val deskKeyMismatch: String get() = t(
    "فایلِ کلید با کلیددان نمی‌خواند",
    "The key file doesn't match the key store",
    "د کیلي فایل له کیلي‌دان سره سمون نه خوري",
  )
  fun deskKeyUnread(why: Any?): String = t(
    "فایلِ کلید خوانده نشد: ${asIs(why)}",
    "The key file couldn't be read: $why",
    "د کیلي فایل ونه لوستل شو: $why",
  )

  // ── کامپیوتر: کلید در کلیددانِ سیستم (`desk.keystore`، `desktop/keystore`) ──
  val deskStoreDpapi: String get() = t("DPAPI ویندوز", "Windows DPAPI", "د وینډوز DPAPI")
  val deskStoreKeychain: String get() = t("Keychain مک", "the macOS Keychain", "د مک Keychain")
  val deskStoreSecret: String get() = t("Secret Service لینوکس", "the Linux Secret Service", "د لینوکس Secret Service")
  fun deskOsWhere(store: Any?): String = t(
    "${asIs(store)} روی همین کامپیوتر",
    "$store on this computer",
    "په دې کمپیوټر کې $store",
  )
  fun deskOsNote(store: Any?): String = t(
    "کلید با ${asIs(store)} نگه‌داری می‌شود، نه به‌شکلِ فایلِ ساده کنارِ داده‌ها: کسی که فقط پوشهٔ داده یا دیسک را کپی کند کلید را ندارد. برنامه‌ای که با همین حسابِ کاربری روی همین کامپیوتر اجرا شود هنوز می‌تواند آن را بخواند. ",
    "The key is kept by $store, not as a plain file next to the data: someone who only copies the data folder or the disk doesn't get the key. A program running under this same user account on this computer can still read it. ",
    "کیلي د $store له لارې ساتل کېږي، د معلوماتو تر څنګ د ساده فایل په بڼه نه: څوک چې یوازې د معلوماتو فولډر یا ډیسک کاپي کړي کیلي نه لري. هغه پروګرام چې په همدې کمپیوټر کې د همدې کارن حساب لاندې چلېږي لا هم کولی شي هغه ولولي. ",
  )
  val deskOsPlainPending: String get() = t(
    "فایلِ سادهٔ پیشینِ کلید تا اجرای بعدیِ برنامه می‌ماند و پس از بازشدنِ درستِ دفتر پاک می‌شود. ",
    "The old plain key file stays until the app's next start and is removed once the ledger opens correctly. ",
    "د کیلي پخوانی ساده فایل د پروګرام تر راتلونکي پیل پورې پاتې کېږي او کله چې دفتر سم پرانیستل شي پاکېږي. ",
  )
  val deskOsPlainStray: String get() = t(
    "یک فایلِ کلیدِ ساده هم هست که با کلیدِ سیستم یکی نیست؛ به آن دست زده نشد. ",
    "There is also a plain key file that doesn't match the system's key; it was left untouched. ",
    "یو ساده کیلي فایل هم شته چې د سیسټم له کیلي سره سمون نه خوري؛ لاس ورته نه دی وړل شوی. ",
  )
  fun deskOsFallback(why: Any?): String = t(
    "نگه‌داریِ کلید در سیستم روشن است ولی نشد (${asIs(why)})؛ کلید مثلِ پیش در فایل ماند. ",
    "Keeping the key in the system is on but didn't work ($why); the key stayed in the file as before. ",
    "په سیسټم کې د کیلي ساتل روښانه دي خو ونه شو ($why)؛ کیلي د پخوا په څېر په فایل کې پاتې شوه. ",
  )
  fun deskOsUnread(store: Any?, why: Any?): String = t(
    "کلید از ${asIs(store)} خوانده نشد: ${asIs(why)}",
    "The key couldn't be read from $store: $why",
    "کیلي له $store څخه ونه لوستل شوه: $why",
  )
  val deskOsMissing: String get() = t(
    "این سیستم آن را ندارد یا در دسترس نیست",
    "this system doesn't have it or it isn't reachable",
    "دا سیسټم یې نه لري یا لاسرسی ورته نشته",
  )
  val deskOsLocked: String get() = t("قفل است یا اجازه نداد", "it is locked or didn't allow it", "قلف دی یا یې اجازه ورنه کړه")
  val deskOsNotFound: String get() = t("کلید آن‌جا پیدا نشد", "the key wasn't found there", "کیلي هلته پیدا نه شوه")
  val deskOsReadBack: String get() = t(
    "پس از نوشتن، همان کلید بازخوانی نشد",
    "after writing, the same key couldn't be read back",
    "له لیکلو وروسته، هماغه کیلي بېرته ونه لوستل شوه",
  )
  val deskOsWrongValue: String get() = t(
    "کلیدی که برگشت با نشانِ فایل یکی نیست",
    "the key that came back doesn't match the file's check",
    "هغه کیلي چې راوګرځېده د فایل له نښې سره سمون نه خوري",
  )
  val deskOsTimeout: String get() = t("در زمانِ مقرر جواب نداد", "it didn't answer in time", "په ټاکلي وخت کې یې ځواب ورنه کړ")
  fun deskOsError(what: Any?): String = t("خطای ${asIs(what)}", "error $what", "تېروتنه $what")
  fun deskOsRefused(code: Any?): String = t("نپذیرفت (${asIs(code)})", "it refused ($code)", "ونه منله ($code)")
  val deskOsBroken: String get() = t(
    "فایلِ نشانِ کلیدِ سیستم خراب است",
    "The system key's record file is damaged",
    "د سیسټم د کیلي د ثبت فایل خراب دی",
  )
  val deskNoGoogle: String get() = t(
    "ورود با گوگل روی برنامهٔ کامپیوتر نیست — با ایمیل وارد شوید",
    "Google sign-in isn't on the computer app — sign in with email",
    "د کمپیوټر په پروګرام کې د ګوګل ننوتل نشته — په برېښنالیک ننوځئ",
  )

  // ── DATA-SAFETY: فایلِ خراب «خالی» نیست (`LedgerRecovery.strict`، `BackupMirror`) ──
  fun notReadKept(name: Any?): String = t(
    "«${asIs(name)}» خوانده نشد؛ برای اینکه رونویسی نشود، کاری انجام نشد",
    "«$name» couldn't be read; nothing was done so it isn't overwritten",
    "«$name» ونه لوستل شو؛ د دې لپاره چې له سره ونه لیکل شي، هېڅ کار ونه شو",
  )
  fun damagedSetAside(name: Any?, copy: Any?): String = t(
    "«${asIs(name)}» خراب است (رونوشتش کنار رفت: ${asIs(copy)})؛ کاری انجام نشد",
    "«$name» is damaged (a copy was set aside: $copy); nothing was done",
    "«$name» خراب دی (کاپي یې یوې خوا ته شوه: $copy)؛ هېڅ کار ونه شو",
  )
  val mirrorSkippedDamaged: String get() = t(
    "دفتر خراب خوانده شد؛ پشتیبانِ پوشه گرفته نشد تا پشتیبان‌های سالم بمانند",
    "The ledger read as damaged; no folder backup was made so the good backups stay",
    "دفتر خراب ولوستل شو؛ د فولډر بک‌اپ ونه نیول شو چې روغ بک‌اپونه پاتې شي",
  )
}
