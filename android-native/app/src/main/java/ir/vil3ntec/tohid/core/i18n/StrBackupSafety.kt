package ir.vil3ntec.tohid.core.i18n

/**
 *  OPS-02 — بازیابیِ آزموده و «عبارتِ بازیابی»: پیامِ پشتیبانِ ناقص/تازه‌تر/دکانِ دیگر، رمزِ نسخه‌های خودکار، و
 *  صفحهٔ «یک بار نشان بده، تأیید کن، چاپ کن». ساخته‌شده از جدولِ سه‌زبانه؛ قاعده‌ها سرِ `I18n.kt`.
 *  ⛔ عبارتِ بازیابی هرگز در هیچ متنی نمی‌آید (فقط در صفحهٔ خودش، جدا)؛ هر مقدارِ درونِ متن از `asIs` می‌گذرد.
 */
@Suppress("unused")
object StrBackupSafety {

  // ── بازیابی: پشتیبانی که نمی‌نشیند ──
  val damaged: String get() = t(
    "فایلِ پشتیبان ناقص یا خراب است — شاید کامل کپی یا دانلود نشده. هیچ چیزی پاک یا عوض نشد؛ نسخهٔ دیگری را برگزینید.",
    "The backup file is incomplete or damaged — it may not have been fully copied or downloaded. Nothing was deleted or changed; choose another copy.",
    "د بیک‌اپ فایل نیمګړی یا خراب دی — کېدای شي بشپړ کاپي یا ښکته شوی نه وي. هېڅ شی نه دی پاک یا بدل شوی؛ بله نسخه وټاکئ.",
  )
  val newerApp: String get() = t(
    "این پشتیبان را نسخهٔ تازه‌ترِ برنامه ساخته است؛ اول برنامه را به‌روز کنید. هیچ چیزی پاک یا عوض نشد.",
    "A newer version of the app made this backup; update the app first. Nothing was deleted or changed.",
    "دا بیک‌اپ د پروګرام نوې نسخې جوړ کړی؛ لومړی پروګرام تازه کړئ. هېڅ شی نه دی پاک یا بدل شوی.",
  )
  val otherShop: String get() = t(
    "این پشتیبان مالِ دکانِ دیگری است. برای برگرداندنش با حسابِ همان دکان وارد شوید؛ دفترِ این دکان دست نخورد.",
    "This backup belongs to another shop. Sign in with that shop's account to restore it; this shop's ledger was not touched.",
    "دا بیک‌اپ د بل دوکان دی. د بېرته راوستلو لپاره د هماغه دوکان په حساب ننوځئ؛ د دې دوکان دفتر لاس نه دی وهل شوی.",
  )

  // ── رمزِ نسخه‌های خودکار ──
  val sealTitle: String get() = t("رمزِ نسخه‌های خودکار", "Encrypted automatic copies", "د خپلکاره نسخو کوډ")
  val sealToggle: String get() = t("رمز با عبارتِ بازیابی", "Encrypt with a recovery phrase", "د بېرته راګرځولو په عبارت کوډ")
  val sealFooter: String get() = t(
    "نسخه‌های نیم‌ساعتهٔ دفتر روی همین دستگاه با کلیدی رمز می‌شوند که از عبارتِ بازیابیِ شما ساخته می‌شود. دستگاهِ تازه فقط همان عبارت را لازم دارد. خودِ دفتر و پشتیبانِ سرور مثلِ پیش می‌مانند؛ پشتیبانِ دستی و پوشه فقط با کلیدِ پایین رمزدار می‌شوند.",
    "The half-hourly copies of the ledger on this device are encrypted with a key made from your recovery phrase. A new device only needs that phrase. The ledger itself and server backups stay as before; manual and folder backups are encrypted only with the switch below.",
    "د دفتر نیم ساعته نسخې په همدې وسیله د هغې کیلي په واسطه کوډ کېږي چې ستاسو د بېرته راګرځولو له عبارت جوړېږي. نوې وسیله یوازې همغه عبارت ته اړتیا لري. پخپله دفتر او د سرور بیک‌اپ لکه پخوا پاتې کېږي؛ لاسي او فولډر بیک‌اپونه یوازې د لاندې کیلي سره کوډ کېږي.",
  )
  val sealOnLine: String get() = t("روشن — نسخه‌های تازه رمزدارند", "On — new copies are encrypted", "روښانه — نوې نسخې کوډ لري")
  val sealKeyMissing: String get() = t(
    "کلیدِ رمز روی این دستگاه باز نشد؛ نسخه‌های تازه مثلِ پیش (بی این رمز) گرفته می‌شوند تا هیچ نسخه‌ای جا نماند. «عبارتِ تازه» را بزنید.",
    "The encryption key couldn't be opened on this device; new copies are taken as before (without this encryption) so no copy is skipped. Tap “New phrase”.",
    "د کوډ کیلي په دې وسیله پرانیستل نه شوه؛ نوې نسخې لکه پخوا (له دې کوډ پرته) اخیستل کېږي څو هېڅ نسخه پاتې نه شي. «نوی عبارت» کېکاږئ.",
  )
  val newPhrase: String get() = t("عبارتِ تازه", "New phrase", "نوی عبارت")
  val newPhraseLine: String get() = t(
    "نسخه‌های بعدی با عبارتِ تازه رمز می‌شوند؛ نسخه‌های پیشین با عبارتِ خودشان باز می‌شوند.",
    "Later copies use the new phrase; earlier copies still open with their own phrase.",
    "راتلونکې نسخې په نوي عبارت کوډ کېږي؛ پخوانۍ نسخې په خپل عبارت پرانیستل کېږي.",
  )
  val offQ: String get() = t("رمزِ نسخه‌ها خاموش شود؟", "Turn off copy encryption?", "د نسخو کوډ بند شي؟")
  val offWarn: String get() = t(
    "نسخه‌های بعدی بی این رمز گرفته می‌شوند. نسخه‌های رمزدارِ پیشین می‌مانند و با همان عبارت باز می‌شوند.",
    "Later copies are taken without this encryption. Earlier encrypted copies stay and open with the same phrase.",
    "راتلونکې نسخې له دې کوډ پرته اخیستل کېږي. پخوانۍ کوډ شوې نسخې پاتې کېږي او په همغه عبارت پرانیستل کېږي.",
  )
  val offDo: String get() = t("خاموش کن", "Turn off", "بند یې کړه")
  val sealedOn: String get() = t("رمزِ نسخه‌های خودکار روشن شد", "Copy encryption is on", "د نسخو کوډ روښانه شو")
  val sealedOff: String get() = t("رمزِ نسخه‌های خودکار خاموش شد", "Copy encryption is off", "د نسخو کوډ بند شو")
  val keyFailed: String get() = t("کلید ساخته نشد؛ چیزی عوض نشد", "The key wasn't made; nothing changed", "کیلي جوړه نه شوه؛ هېڅ شی بدل نه شو")

  // ── صفحهٔ عبارت ──
  val phraseTitle: String get() = t("عبارتِ بازیابی", "Recovery phrase", "د بېرته راګرځولو عبارت")
  val introBody: String get() = t(
    "۲۵ نویسه که فقط یک بار نشان داده می‌شود. آن را روی کاغذ بنویسید یا چاپ کنید و جای امنی بیرون از دکان نگه دارید. اگر این دستگاه گم یا عوض شد، نسخه‌های رمزدار فقط با همین عبارت باز می‌شوند — ما هم نمی‌توانیم بازشان کنیم.",
    "25 characters, shown only once. Write it on paper or print it and keep it somewhere safe outside the shop. If this device is lost or replaced, the encrypted copies open only with this phrase — we can't open them either.",
    "۲۵ توري چې یوازې یو ځل ښودل کېږي. په کاغذ یې ولیکئ یا یې چاپ کړئ او له دوکان بهر په خوندي ځای کې یې وساتئ. که دا وسیله ورکه یا بدله شي، کوډ شوې نسخې یوازې په همدې عبارت پرانیستل کېږي — موږ یې هم نه شو پرانیستلی.",
  )
  val showIt: String get() = t("نشان بده", "Show it", "وې ښیه")
  val writeItDown: String get() = t(
    "همین حالا بنویسید یا چاپ کنید؛ پس از تأیید دیگر نشان داده نمی‌شود.",
    "Write it down or print it now; it won't be shown again after you confirm.",
    "همدا اوس یې ولیکئ یا یې چاپ کړئ؛ له تایید وروسته بیا نه ښودل کېږي.",
  )
  val printIt: String get() = t("چاپ", "Print", "چاپ")
  val wroteIt: String get() = t("نوشتم", "I've written it down", "ومې لیکه")
  val confirmBody: String get() = t(
    "برای اطمینان، همان عبارت را از روی کاغذ بنویسید (خط‌تیره و فاصله لازم نیست).",
    "To be sure, type the same phrase from your paper (dashes and spaces are optional).",
    "د ډاډ لپاره، همغه عبارت له کاغذ څخه ولیکئ (لنډه کرښه او تشه اړینه نه ده).",
  )
  val confirmLabel: String get() = t("عبارتِ بازیابی", "Recovery phrase", "د بېرته راګرځولو عبارت")
  val confirmDo: String get() = t("تأیید و روشن کردن", "Confirm and turn on", "تایید او روښانه کول")
  val showAgain: String get() = t("دوباره ببینم", "Show it again", "بیا یې وګورم")
  val typo: String get() = t(
    "یک نویسه درست نیست؛ دوباره از روی کاغذ نگاه کنید.",
    "A character isn't right; check your paper again.",
    "یو توری سم نه دی؛ بیا کاغذ ته وګورئ.",
  )
  val mismatch: String get() = t(
    "این عبارتِ همین صفحه نیست؛ همان ۲۵ نویسهٔ بالا را بنویسید.",
    "That isn't the phrase from this screen; type the 25 characters shown above.",
    "دا د دې پاڼې عبارت نه دی؛ پورته ښودل شوي ۲۵ توري ولیکئ.",
  )
  val making: String get() = t("در حالِ ساختنِ کلید…", "Making the key…", "کیلي جوړېږي…")

  // ── برگهٔ چاپ ──
  val printKeep: String get() = t(
    "این برگه را جای امنی بیرون از دکان نگه دارید. هر کس آن را داشته باشد نسخه‌های رمزدارِ دفتر را می‌تواند باز کند.",
    "Keep this sheet somewhere safe outside the shop. Anyone who has it can open the encrypted copies of the ledger.",
    "دا پاڼه له دوکان بهر په خوندي ځای کې وساتئ. هر څوک چې دا ولري د دفتر کوډ شوې نسخې پرانیستلی شي.",
  )
  val printHow: String get() = t(
    "برای بازکردن: تنظیمات ⇐ داده‌ها ⇐ نسخه‌های خودکار ⇐ همین عبارت.",
    "To open: Settings ⇒ Data ⇒ Automatic copies ⇒ this phrase.",
    "د پرانیستلو لپاره: تنظیمات ⇐ معلومات ⇐ خپلکاره نسخې ⇐ همدا عبارت.",
  )
  // ── رمزِ پشتیبانِ دستی و پوشه (`BackupSeal`) ──
  val backupSealToggle: String get() = t("پشتیبانِ دستی و پوشه هم رمزدار", "Encrypt manual and folder backups too", "لاسي او فولډر بیک‌اپونه هم کوډ شي")
  val backupSealOnLine: String get() = t(
    "روشن — پشتیبانِ تازه فقط با همین عبارت باز می‌شود",
    "On — new backups open only with this phrase",
    "روښانه — نوی بیک‌اپ یوازې په همدې عبارت پرانیستل کېږي",
  )
  val backupSealKeyMissing: String get() = t(
    "کلیدِ رمز روی این دستگاه باز نشد؛ پشتیبانِ دستی و پوشه ساخته نشد تا بی‌رمز بیرون نرود. «عبارتِ تازه» را بزنید یا این رمز را خاموش کنید.",
    "The encryption key couldn't be opened on this device; the manual or folder backup wasn't made so it doesn't leave unencrypted. Tap “New phrase” or turn this encryption off.",
    "د کوډ کیلي په دې وسیله پرانیستل نه شوه؛ لاسي او فولډر بیک‌اپ جوړ نه شو څو بې کوډه بهر ونه وځي. «نوی عبارت» کېکاږئ یا دا کوډ بند کړئ.",
  )
  val fileNeedsPhrase: String get() = t(
    "این پشتیبان با عبارتِ بازیابی رمز شده و کلیدش روی این دستگاه نیست؛ از «تنظیمات ⇐ داده‌ها» با همان عبارت بازش کنید. هیچ چیزی عوض نشد.",
    "This backup is encrypted with a recovery phrase and its key isn't on this device; open it from “Settings ⇐ Data” with that phrase. Nothing changed.",
    "دا بیک‌اپ د بېرته راګرځولو په عبارت کوډ شوی او کیلي یې په دې وسیله کې نشته؛ له «امستنې ⇐ معلومات» څخه یې په همغه عبارت پرانیزئ. هېڅ شی بدل نه شو.",
  )
  val wrongPhraseFile: String get() = t(
    "این عبارت این پشتیبان را باز نکرد — عبارتِ دیگری است، یا فایل دست‌خورده یا ناقص است. هیچ چیزی عوض نشد.",
    "That phrase didn't open this backup — it's a different phrase, or the file was changed or is incomplete. Nothing changed.",
    "دې عبارت دا بیک‌اپ پرانیستلی نه شو — بل عبارت دی، یا فایل لاس وهل شوی یا نیمګړی دی. هېڅ شی بدل نه شو.",
  )
  val unlockFileTitle: String get() = t("این پشتیبان رمزدار است", "This backup is encrypted", "دا بیک‌اپ کوډ لري")
  fun backupTooBigToSeal(mb: Any, freeMb: Any): String = t(
    "این پشتیبان (حدودِ ${asIs(mb)} مگابایت) برای رمز کردن روی این دستگاه بزرگ است (حافظهٔ آزاد ${asIs(freeMb)} مگابایت)؛ ساخته نشد تا بی‌رمز بیرون نرود. آن را روی کامپیوتر بگیرید یا «پشتیبانِ دستی و پوشه هم رمزدار» را خاموش کنید.",
    "This backup (about $mb MB) is too big to encrypt on this device ($freeMb MB of memory free); it wasn't made so it doesn't leave unencrypted. Make it on the computer, or turn off “Encrypt manual and folder backups too”.",
    "دا بیک‌اپ (نږدې $mb مېګابایټه) په دې وسیله کې د کوډ کولو لپاره لوی دی ($freeMb مېګابایټه حافظه خالي ده)؛ جوړ نه شو څو بې کوډه بهر ونه وځي. په کمپیوټر یې جوړ کړئ یا «لاسي او فولډر بیک‌اپونه هم کوډ شي» بند کړئ.",
  )
  fun backupTooBigToOpen(mb: Any): String = t(
    "این پشتیبانِ رمزدار برای حافظهٔ این دستگاه بزرگ است (بیش از ${asIs(mb)} مگابایت)؛ آن را روی کامپیوتر بازیابی کنید. هیچ چیزی عوض نشد.",
    "This encrypted backup is too big for this device's memory (over $mb MB); restore it on the computer. Nothing changed.",
    "دا کوډ شوی بیک‌اپ د دې وسیلې د حافظې لپاره لوی دی (له $mb مېګابایټه زیات)؛ په کمپیوټر یې بېرته راوګرځوئ. هېڅ شی بدل نه شو.",
  )
  fun mirrorFailed(at: Any, why: Any): String = t(
    "پشتیبانِ پوشه ساخته نشد (${asIs(at)}): ${asIs(why)}",
    "The folder backup wasn't made ($at): $why",
    "د فولډر بیک‌اپ جوړ نه شو ($at): $why",
  )
  val ledgerOnlySealedDesc: String get() = t(
    "بدون عکس، رمزدار با عبارتِ بازیابی — برنامه بازش می‌کند، نسخهٔ وب نه",
    "Without photos, encrypted with the recovery phrase — the app opens it, the web version doesn't",
    "بې انځورونو، د بېرته راګرځولو په عبارت کوډ شوی — پروګرام یې پرانیزي، وېب نسخه یې نه",
  )

  fun printDate(d: Any): String = t("ساخته‌شده: ${asIs(d)}", "Made: $d", "جوړ شوی: $d")

  // ── بازکردنِ نسخهٔ رمزدار ──
  val unlockTitle: String get() = t("این نسخه رمزدار است", "This copy is encrypted", "دا نسخه کوډ لري")
  val unlockBody: String get() = t(
    "کلیدش روی این دستگاه نیست. عبارتِ بازیابی‌ای را که هنگامِ روشن کردنِ رمز نوشتید وارد کنید.",
    "Its key isn't on this device. Enter the recovery phrase you wrote down when you turned encryption on.",
    "د دې کیلي په دې وسیله کې نشته. هغه د بېرته راګرځولو عبارت ولیکئ چې د کوډ د روښانه کولو پر مهال مو لیکلی و.",
  )
  val unlockDo: String get() = t("باز کن", "Open", "پرانیزه")
  val wrongPhrase: String get() = t(
    "این عبارت این نسخه را باز نکرد؛ هیچ چیزی عوض نشد.",
    "That phrase didn't open this copy; nothing changed.",
    "دې عبارت دا نسخه پرانیستې نه شوه؛ هېڅ شی بدل نه شو.",
  )
}
