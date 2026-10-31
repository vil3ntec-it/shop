package ir.vil3ntec.tohid.core.i18n

/**
 *  حساب و امنیت: «اشتراکِ من» و بنرِ اشتراک (`MySubscription`)، و تنظیمات ← امنیت با پنجرهٔ
 *  «دفترِ رمزدار باز نشد» (`SecuritySettings`، SEC-01).
 *
 *  ساخته‌شده از جدولِ سه‌زبانه؛ دستی هم ویرایش‌پذیر است. قاعده‌ها سرِ `I18n.kt`.
 *  ترجمه‌های نامطمئن در `docs/i18n-glossary.md` برای بازبینیِ گویشور.
 *  ⛔ نامِ پلن، برچسبِ روز و مبلغ از سرور می‌آیند و همان‌طور نشان داده می‌شوند.
 */
@Suppress("unused")
object StrSecurity {

  // اشتراکِ من
  val signInToSee: String get() = t("برای دیدنِ اشتراک، اول با ایمیل وارد شوید.", "To see your subscription, first sign in with your email.", "د ګډون د لیدو لپاره، لومړی په ایمیل ننوځئ.")
  val nothingYet: String get() = t(
    "هنوز چیزی از سرور نیامده. اینترنت که وصل شد، همین‌جا پر می‌شود.",
    "Nothing has come from the server yet. This fills in once the internet is connected.",
    "لا تر اوسه له سرور څخه څه نه دي راغلي. کله چې انټرنېټ وصل شي، همدلته ډکېږي.",
  )
  val plan: String get() = t("پلن", "Plan", "پلان")
  val permanent: String get() = t("دائمی ✓", "Permanent ✓", "تلپاتې ✓")
  fun daysLeft(n: Any): String = t("$n روز مانده", "$n ${pl(n, "day", "days")} left", "$n ${pl(n, "ورځ", "ورځې")} پاتې")
  val starts: String get() = t("آغاز", "Start", "پیل")
  val ends: String get() = t("پایان", "End", "پای")
  fun endsSoon(n: Any): String = t(
    "$n روز تا پایانِ اشتراک. پس از آن برنامه فقط‌خواندنی می‌شود — هیچ داده‌ای پاک نمی‌شود و خروجی و چاپ هم کار می‌کند.",
    "$n ${pl(n, "day", "days")} until the subscription ends. After that the app becomes read-only — no data is deleted, and export and printing still work.",
    "د ګډون تر پای $n ${pl(n, "ورځ", "ورځې")}. له هغې وروسته اپ یوازې د لوستلو کېږي — هېڅ معلومات نه ړنګېږي او ایستل او چاپ هم کار کوي.",
  )
  val ended: String get() = t(
    "اشتراک تمام شده. برنامه فقط‌خواندنی است — دفترتان دست‌نخورده سرِ جایش است و خروجی و چاپ هم کار می‌کند.",
    "The subscription has ended. The app is read-only — your ledger is untouched, and export and printing still work.",
    "ګډون پای ته رسېدلی. اپ یوازې د لوستلو دی — ستاسو دفتر روغ پر خپل ځای دی او ایستل او چاپ هم کار کوي.",
  )
  val activeDevices: String get() = t("دستگاه‌های فعال", "Active devices", "فعال وسایل")
  val noDevices: String get() = t("دستگاهی ثبت نشده.", "No devices registered.", "هېڅ وسیله نه ده ثبت شوې.")
  val payments: String get() = t("پرداخت‌ها", "Payments", "ورکړې")
  val noPayments: String get() = t("پرداختی ثبت نشده.", "No payments recorded.", "هېڅ ورکړه نه ده ثبت شوې.")
  val adminMessages: String get() = t("پیام‌های مدیر", "Messages from the admin", "د مدیر پیغامونه")
  val noMessages: String get() = t("پیامی نیست.", "No messages.", "هېڅ پیغام نشته.")
  val reading: String get() = t("در حالِ خواندن…", "Loading…", "لوستل کېږي…")
  val refresh: String get() = t("تازه کردن از سرور", "Refresh from server", "له سرور څخه تازه کول")
  val signInWithCode: String get() = t("ورود با ایمیل و کدِ شش‌رقمی", "Sign in with email and a six-digit code", "په ایمیل او شپږ عدده کوډ ننوتل")
  val bannerLocked: String get() = t(
    "اشتراک تمام شده — برنامه فقط‌خواندنی است. دفترتان دست‌نخورده سرِ جایش است و خروجی و چاپ کار می‌کند.",
    "Subscription ended — the app is read-only. Your ledger is untouched, and export and printing work.",
    "ګډون پای ته رسېدلی — اپ یوازې د لوستلو دی. ستاسو دفتر روغ پر خپل ځای دی او ایستل او چاپ کار کوي.",
  )
  fun bannerSoon(n: Any): String = t(
    "$n روز تا پایانِ اشتراک. پس از آن برنامه فقط‌خواندنی می‌شود؛ هیچ داده‌ای پاک نمی‌شود.",
    "$n ${pl(n, "day", "days")} until the subscription ends. After that the app becomes read-only; no data is deleted.",
    "د ګډون تر پای $n ${pl(n, "ورځ", "ورځې")}. له هغې وروسته اپ یوازې د لوستلو کېږي؛ هېڅ معلومات نه ړنګېږي.",
  )

  // امنیت: رمزگذاریِ دفتر روی همین دستگاه (SEC-01)
  val security: String get() = t("امنیت", "Security", "امنیت")
  val encSubtitleOn: String get() = t("رمزگذاریِ دفتر: روشن", "Ledger encryption: on", "د دفتر رمزي کول: روښانه")
  val encSubtitleOff: String get() = t("رمزگذاریِ دفتر: خاموش", "Ledger encryption: off", "د دفتر رمزي کول: بند")
  val settingNotSaved: String get() = t("تنظیم ذخیره نشد", "The setting wasn't saved", "امستنه خوندي نه شوه")
  fun filesNotConverted(n: Any): String = t(
    "$n فایل کوچ نشد و دست‌نخورده ماند؛ دوباره امتحان کنید",
    "$n ${pl(n, "file wasn't", "files weren't")} converted and stayed untouched; try again",
    "$n ${pl(n, "فایل", "فایلونه")} ونه لېږدول شول او روغ پاتې شول؛ بیا هڅه وکړئ",
  )
  fun encTurnedOn(n: Any): String = t("رمزگذاری روشن شد ($n فایل رمز شد)", "Encryption turned on ($n ${pl(n, "file", "files")} encrypted)", "رمزي کول روښانه شو ($n ${pl(n, "فایل", "فایلونه")} رمزي شول)")
  fun encTurnedOff(n: Any): String = t("رمزگذاری خاموش شد ($n فایل ساده شد)", "Encryption turned off ($n ${pl(n, "file", "files")} decrypted)", "رمزي کول بند شو ($n ${pl(n, "فایل", "فایلونه")} ساده شول)")
  val whatIsEncrypted: String get() = t(
    "دفترِ دکان، دفترِ حساب‌های دیگرِ همین دستگاه، بایگانی، نسخهٔ ایمنیِ پیش از بازیابی و نسخه‌های خودکار روی همین دستگاه رمز می‌شوند؛ کسی که فقط این فایل‌ها را بردارد، بی کلیدِ همین دستگاه چیزی نمی‌خواند.",
    "The shop ledger, the ledgers of other accounts on this device, the archive, the safety copy made before a restore and the automatic copies are encrypted on this device; anyone who takes only these files can't read anything without this device's key.",
    "د دوکان دفتر، په همدې وسیله کې د نورو حسابونو دفترونه، آرشیف، له بېرته راګرځولو مخکې خوندي نسخه او اتومات نسخې په همدې وسیله رمزي کېږي؛ څوک چې یوازې دا فایلونه یوسي، د همدې وسیلې له کیلي پرته هېڅ نشي لوستلی.",
  )
  val plainInPhase1: String get() = t(
    "ساده می‌ماند (فاز ۱): صفِ تغییرهای همگام‌سازی، ژورنال‌ها و سایهٔ همگام‌سازی (نسخه‌ای از ردیف‌های همگام‌شده)، خلاصهٔ دفتر (ledger-summary.json) و سبدِ نیمه‌کاره (sale-draft.json). توکن‌های ورود از پیش جدا رمزند.",
    "Stays unencrypted (phase 1): the sync change queue, journals and the sync shadow (a copy of synced rows), the ledger summary (ledger-summary.json) and the unfinished cart (sale-draft.json). Sign-in tokens are already encrypted separately.",
    "ساده پاتې کېږي (لومړی پړاو): د همغږۍ د بدلونونو کتار، ژورنالونه او د همغږۍ سیوری (د همغږي شویو کرښو نسخه)، د دفتر لنډیز (ledger-summary.json) او نیمګړې ټوکرۍ (sale-draft.json). د ننوتلو ټوکنونه له مخکې جلا رمزي دي.",
  )
  val encOnThisDevice: String get() = t("رمزگذاریِ دفتر روی این دستگاه", "Encrypt the ledger on this device", "په دې وسیله کې د دفتر رمزي کول")
  val converting: String get() = t("در حالِ کوچِ فایل‌ها…", "Converting files…", "فایلونه لېږدول کېږي…")
  fun filesState(sealed: Any, plain: Any): String = t(
    "$sealed فایلِ رمزدار، $plain فایلِ ساده",
    "$sealed encrypted ${pl(sealed, "file", "files")}, $plain plain ${pl(plain, "file", "files")}",
    "$sealed رمزي ${pl(sealed, "فایل", "فایلونه")}، $plain ساده ${pl(plain, "فایل", "فایلونه")}",
  )
  fun keyUnavailable(r: Any): String = t(
    "کلیدِ رمز همین حالا در دسترس نیست ($r) — دفتر فعلاً ساده نوشته می‌شود.",
    "The encryption key isn't available right now ($r) — the ledger is written unencrypted for now.",
    "د رمز کیلي همدا اوس نشته ($r) — دفتر اوس مهال ساده لیکل کېږي.",
  )
  fun keyLost(r: Any): String = t("کلیدِ رمزِ قبلی روی این دستگاه نیست ($r).", "The previous encryption key isn't on this device ($r).", "پخوانۍ د رمز کیلي په دې وسیله کې نشته ($r).")
  val keyNone: String get() = t(
    "کلیدِ رمز هنوز ساخته نشده — دفتر فعلاً ساده نوشته می‌شود.",
    "No encryption key has been created yet — the ledger is written unencrypted for now.",
    "د رمز کیلي لا نه ده جوړه شوې — دفتر اوس مهال ساده لیکل کېږي.",
  )
  fun lastSaveNotSealed(e: Any): String = t("آخرین ذخیره رمز نشد: $e", "The last save wasn't encrypted: $e", "وروستی خوندي کول رمزي نه شول: $e")
  val backupsNotSealed: String get() = t(
    "فایل‌های پشتیبانی که بیرون می‌روند (فایل، پوشه، ابر) رمزِ این دستگاه را ندارند.",
    "Backup files that leave the device (file, folder, cloud) don't carry this device's encryption.",
    "هغه بیک‌اپ فایلونه چې بهر ځي (فایل، فولډر، ورېځ) د دې وسیلې رمز نه لري.",
  )
  fun leftovers(n: Any, files: Any): String = t(
    "$n فایلِ کنارگذاشته روی دستگاه مانده (پاک نشده‌اند): $files",
    "$n set-aside ${pl(n, "file remains", "files remain")} on the device (not deleted): $files",
    "$n یوې خوا ته شوي ${pl(n, "فایل", "فایلونه")} په وسیله کې پاتې دي (نه دي ړنګ شوي): $files",
  )
  val retry: String get() = t("دوباره امتحان", "Try again", "بیا هڅه")
  val ledgerOpened: String get() = t("دفتر باز شد", "The ledger opened", "دفتر پرانیستل شو")
  val stillNotOpened: String get() = t("هنوز باز نشد", "Still didn't open", "لا نه دی پرانیستل شوی")
  val askOnTitle: String get() = t("رمزگذاریِ دفتر روشن شود؟", "Turn on ledger encryption?", "د دفتر رمزي کول روښانه شي؟")
  val turnOn: String get() = t("روشن کن", "Turn on", "روښانه یې کړئ")

  // بازسازی از سرور و «دفترِ رمزدار باز نشد»
  val rebuild: String get() = t("بازسازی از سرور", "Rebuild from server", "له سرور څخه بیا جوړول")
  fun rebuildAsk(r: Any): String = t("$r؟", "$r?", "$r؟")
  val rebuildBody: String get() = t(
    "اول تغییرهایی که هنوز از این دستگاه نرفته‌اند فرستاده می‌شوند؛ اگر نرسیدند، هیچ چیزی عوض نمی‌شود. بعد دفتر از روی سرور ساخته می‌شود. دفترِ رمزدارِ فعلی پاک نمی‌شود، کنار می‌رود. اینترنت لازم است.",
    "First, changes not yet sent from this device are sent; if they don't arrive, nothing changes. Then the ledger is built from the server. The current encrypted ledger isn't deleted; it's set aside. Internet is required.",
    "لومړی هغه بدلونونه لېږل کېږي چې لا له دې وسیلې نه دي تللي؛ که ونه رسېدل، هېڅ نه بدلېږي. بیا دفتر له سرور څخه جوړېږي. اوسنی رمزي دفتر نه ړنګېږي، یوې خوا ته کېږي. انټرنېټ ته اړتیا ده.",
  )
  val unreadableTitle: String get() = t("دفترِ رمزدار باز نشد", "The encrypted ledger didn't open", "رمزي دفتر پرانیستل نه شو")
  val recordingBlocked: String get() = t(
    "برای اینکه دفترِ واقعی رونویسی نشود، ثبت بسته است. ",
    "Recording is blocked so the real ledger isn't overwritten. ",
    "د دې لپاره چې اصلي دفتر له سره ونه لیکل شي، ثبت بند دی. ",
  )
  val rebuilding: String get() = t("در حالِ بازسازی…", "Rebuilding…", "بیا جوړېږي…")
  val later: String get() = t("بعداً", "Later", "وروسته")
  fun unreadableKeyUnavailable(r: Any): String = t(
    "دفترِ رمزدار همین حالا باز نشد (کلید در دسترس نبود). دوباره امتحان کنید؛ اگر نشد، «$r» دفتر را از سرور می‌گیرد.",
    "The encrypted ledger didn't open just now (the key wasn't available). Try again; if it still fails, “$r” gets the ledger from the server.",
    "رمزي دفتر همدا اوس پرانیستل نه شو (کیلي نه وه). بیا هڅه وکړئ؛ که نه شو، «$r» دفتر له سرور څخه راوړي.",
  )
  fun unreadableKeyMissing(device: Any, r: Any): String = t(
    "دفترِ این دستگاه رمزدار است ولی کلیدش روی این دستگاه نیست (نصبِ دوباره، انتقال به $device تازه یا از کار افتادنِ کلید). هیچ چیزی پاک نشده. «$r» اول تغییرهای نرفتهٔ این دستگاه را می‌فرستد و بعد دفتر را از سرور می‌سازد؛ بازیابیِ پشتیبان هم از همین راه است و فقط ردیف‌هایی را می‌افزاید که هرگز به سرور نرفته بودند؛ ردیفِ حذف‌شده زنده نمی‌شود.",
    "This device's ledger is encrypted but its key isn't on this device (a reinstall, a move to a new $device, or a broken key). Nothing has been deleted. “$r” first sends this device's unsent changes and then rebuilds the ledger from the server; restoring a backup works the same way and only adds rows that never reached the server; a deleted row doesn't come back.",
    "د دې وسیلې دفتر رمزي دی خو کیلي یې په دې وسیله کې نشته (بیا نصبول، نوي $device ته لېږد یا د کیلي خرابېدل). هېڅ شی نه دی ړنګ شوی. «$r» لومړی د دې وسیلې نه تللي بدلونونه لېږي او بیا دفتر له سرور څخه جوړوي؛ د بیک‌اپ بېرته راګرځول هم همداسې دي او یوازې هغه کرښې زیاتوي چې هېڅکله سرور ته نه وې تللې؛ ړنګه شوې کرښه بېرته نه راژوندۍ کېږي.",
  )
  fun unreadableIo(r: Any): String = t(
    "بازسازیِ قبلی نیمه‌کاره ماند و دفترِ تازه جا نیفتاد؛ برای اینکه دفترِ قدیم کنارِ همگام‌سازیِ تازه ذخیره نشود، ثبت بسته است. «دوباره امتحان» همان جابه‌جایی را تمام می‌کند؛ اگر نشد، «$r» را دوباره بزنید. هیچ چیزی پاک نشده.",
    "The previous rebuild didn't finish and the new ledger wasn't put in place; recording is blocked so the old ledger isn't saved next to the new sync. “Try again” finishes the swap; if it doesn't, press “$r” again. Nothing has been deleted.",
    "پخوانی بیا جوړول نیمګړی پاتې شو او نوی دفتر ځای پر ځای نه شو؛ د دې لپاره چې زوړ دفتر د نوې همغږۍ تر څنګ خوندي نه شي، ثبت بند دی. «بیا هڅه» هماغه بدلون بشپړوي؛ که نه شو، «$r» بیا ووهئ. هېڅ شی نه دی ړنګ شوی.",
  )
  fun unreadableDamaged(r: Any): String = t(
    "فایلِ دفترِ رمزدار خراب است و باز نشد. هیچ چیزی پاک نشده؛ «$r» دفتر را از سرور می‌سازد.",
    "The encrypted ledger file is damaged and didn't open. Nothing has been deleted; “$r” rebuilds the ledger from the server.",
    "د رمزي دفتر فایل خراب دی او پرانیستل نه شو. هېڅ شی نه دی ړنګ شوی؛ «$r» دفتر له سرور څخه جوړوي.",
  )
  /** نامِ دستگاه در «انتقال به … تازه» — با کسرهٔ اضافه در فارسی */
  val newComputerOf: String get() = t("کامپیوترِ", "computer", "کمپیوټر")
  val newPhoneOf: String get() = t("گوشیِ", "phone", "موبایل")
  /** نامِ دستگاه در «کلید فقط روی همین …» */
  val thisComputer: String get() = t("کامپیوتر", "computer", "کمپیوټر")
  val thisPhone: String get() = t("گوشی", "phone", "موبایل")
  fun keyOnlyHere(device: Any): String = t("کلید فقط روی همین $device است. ", "The key is only on this $device. ", "کیلي یوازې په همدې $device کې ده. ")
  fun keyLostComesBack(r: Any): String = t(
    " اگر کلید از دست برود، دفتر با «$r» برمی‌گردد — پس پیش از روشن کردن مطمئن شوید همگام‌سازی کار می‌کند.",
    " If the key is lost, the ledger comes back with “$r” — so make sure sync works before turning this on.",
    " که کیلي ورکه شي، دفتر په «$r» بېرته راځي — نو له روښانه کولو مخکې ډاډه شئ چې همغږي کار کوي.",
  )
  val desktopDowngradeWarn: String get() = t(
    "\n\n⚠️ پیش از نصبِ نسخهٔ قدیمی‌ترِ برنامه این را خاموش کنید. نسخه‌های پیش از این، دفترِ رمزدار را نمی‌شناسند: دفتر را خالی نشان می‌دهند و نخستین ذخیره‌شان دفترِ رمزدار را رونویسی می‌کند و همهٔ ردیف‌ها را از سرور هم پاک می‌کند.",
    "\n\n⚠️ Turn this off before installing an older version of the app. Earlier versions don't recognise the encrypted ledger: they show it as empty, and their first save overwrites the encrypted ledger and deletes all rows from the server too.",
    "\n\n⚠️ د اپ د زړې نسخې له نصبولو مخکې دا بند کړئ. له دې مخکې نسخې رمزي دفتر نه پېژني: دفتر تش ښيي او لومړی خوندي کول یې رمزي دفتر له سره لیکي او ټولې کرښې له سرور څخه هم ړنګوي.",
  )
}
