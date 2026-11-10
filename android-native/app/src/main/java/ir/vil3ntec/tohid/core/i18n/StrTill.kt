package ir.vil3ntec.tohid.core.i18n

/**
 *  صندوقِ چندکاربره و کلیدها: تعویضِ کاربر با PIN (S46)، تأییدِ تغییرِ قیمت (S45)، راهنمای کلیدِ هر صفحه (S50)،
 *  هشدارِ ساعتِ دستگاه (S28b). قاعده‌ها سرِ `I18n.kt`؛ ترجمه‌های نامطمئن در `docs/i18n-glossary.md`.
 */
@Suppress("unused")
object StrTill {

  // ── S46 · تعویضِ کاربر روی صندوق ──
  val switchTitle: String get() = t("تعویضِ کاربر روی صندوق (Ctrl+L)", "Switch user at the till (Ctrl+L)", "په صندوق کې د کارن بدلول (Ctrl+L)")
  val switchNote: String get() = t(
    "روشن: Ctrl+L صندوق را قفل می‌کند و هر کس با PIN ِ شخصیِ خودش باز می‌کند. هر کاربر سبدِ خودش را دارد و فروش و سابقه به نامِ همان کاربر ثبت می‌شود؛ کاربرِ صندوق هرگز بیش از حسابِ این دستگاه اختیار ندارد. حسابِ همین دستگاه هم PIN یا رمزِ دستگاه لازم دارد. فقط روی همین دستگاه.",
    "On: Ctrl+L locks the till and each person opens it with their own PIN. Each user has their own cart, and sales and history are recorded under that user; the till user never gets more rights than this device's account. This device's account also needs a PIN or the device code. This device only.",
    "روښانه: Ctrl+L صندوق قفلوي او هر څوک یې د خپل شخصي PIN سره خلاصوي. هر کارن خپله ټوکرۍ لري او پلور او تاریخچه د همغه کارن په نوم ثبتېږي؛ د صندوق کارن هېڅکله د دې وسیلې له حساب څخه زیات واک نه لري. د همدې وسیلې حساب هم PIN یا د وسیلې رمز ته اړتیا لري. یوازې همدا وسیله.",
  )
  val users: String get() = t("کاربرانِ این صندوق", "Users of this till", "د دې صندوق کارنان")
  val noUsers: String get() = t("هنوز کسی روی این دستگاه PIN ندارد.", "Nobody has a PIN on this device yet.", "تر اوسه په دې وسیله هېڅوک PIN نه لري.")
  val addUser: String get() = t("افزودنِ کاربر با PIN", "Add a user with a PIN", "د PIN سره کارن زیاتول")
  val remove: String get() = t("برداشتن", "Remove", "لرې کول")
  val pickMember: String get() = t("کدام عضوِ دکان؟ خودش PIN ِ تازه را می‌زند.", "Which shop member? They type their new PIN themselves.", "د دوکان کوم غړی؟ هغه پخپله نوی PIN ولیکي.")
  val membersOffline: String get() = t("فهرستِ اعضا بی اینترنت نمی‌آید.", "The member list needs the internet.", "د غړو لړلیک انټرنېټ ته اړتیا لري.")
  val needDeviceWay: String get() = t(
    "قفل با PIN ِ شخصی هنوز نمی‌افتد: حسابِ همین دستگاه PIN یا رمزِ دستگاه ندارد.",
    "The personal-PIN lock is not active yet: this device's account has no PIN or device code.",
    "د شخصي PIN قفل لا نه لګېږي: د همدې وسیلې حساب PIN یا د وسیلې رمز نه لري.",
  )
  val locked: String get() = t("صندوق قفل است", "The till is locked", "صندوق قفل دی")
  val who: String get() = t("کی پشتِ صندوق است؟", "Who is at the till?", "څوک په صندوق کې دی؟")
  val deviceCode: String get() = t("با رمزِ دستگاه", "With the device code", "د وسیلې د رمز سره")
  val back: String get() = t("برگشت به فهرست", "Back to the list", "لړلیک ته بېرته")
  fun atTill(name: String): String = t("پشتِ صندوق: ${asIs(name)}", "At the till: $name", "په صندوق کې: $name")
  val serverEnrollFailed: String get() = t(
    "PIN روی همین دستگاه گذاشته شد، ولی سرور آن را نپذیرفت؛ تا آن را دوباره نگذارید، سرور کارِ این کاربر را به نامِ حسابِ همین دستگاه می‌بیند.",
    "The PIN was set on this device, but the server did not accept it; until you set it again, the server records this user's work under this device's account.",
    "PIN په همدې وسیله کې کېښودل شو، خو سرور ونه مانه؛ تر هغه چې یې بیا کېنږدئ، سرور د دې کارن کار د همدې وسیلې د حساب په نوم ګڼي.",
  )
  fun lockedUser(name: String): String = t(
    "PIN ِ «${asIs(name)}» پس از خطاهای پیاپی قفل است.",
    "$name's PIN is locked after repeated mistakes.",
    "د «$name» PIN له پرله‌پسې تېروتنو وروسته قفل دی.",
  )

  // ── S45 · تأییدِ تغییرِ قیمت ──
  val askApproval: String get() = t("درخواستِ تأیید", "Ask for approval", "د تایید غوښتنه")
  val priceTitle: String get() = t("تغییرِ قیمت بیرون از قاعده", "Price change outside the rule", "له قاعدې بهر د بیې بدلون")
  fun priceSummary(product: String, from: String, to: String): String = t(
    "«${asIs(product)}»: از ${asIs(from)} به ${asIs(to)}",
    "\"$product\": from $from to $to",
    "«$product»: له $from څخه $to ته",
  )
  fun approvedBy(name: String): String = t("تأیید شد: ${asIs(name)}", "Approved: $name", "تایید شو: $name")

  // ── S50 · راهنمای کلیدِ هر صفحه ──
  val keyHelpTitle: String get() = t("راهنمای کلیدِ هر صفحه (F1 و Ctrl)", "Per-screen key help (F1 and Ctrl)", "د هرې پاڼې د کیلي لارښود (F1 او Ctrl)")
  val keyHelpNote: String get() = t(
    "روشن: F1 فهرستِ کلیدهای همان صفحه را از نقشهٔ کلیدها (keymap.json) نشان می‌دهد و با نگه‌داشتنِ Ctrl نیم ثانیه برچسبِ کلیدهای Ctrl ِ همان صفحه پیدا می‌شود. خاموش: همان فهرستِ پیشین. فقط روی همین دستگاه.",
    "On: F1 shows the keys of the current screen from the key map (keymap.json), and holding Ctrl for half a second shows that screen's Ctrl key labels. Off: the previous list. This device only.",
    "روښانه: F1 د همدې پاڼې کیلي د کیلي له نقشې (keymap.json) ښیي او Ctrl نیمه ثانیه نیول د همدې پاڼې د Ctrl کیلي نښې ښکاره کوي. بند: پخوانی لړلیک. یوازې همدا وسیله.",
  )
  val keyHelpScreen: String get() = t("کلیدهای همین صفحه", "Keys of this screen", "د همدې پاڼې کیلي")

  // ── S28b · ساعتِ دستگاه ──
  fun clockBehind(gap: String): String = t(
    "ساعتِ این دستگاه ${asIs(gap)} عقب است — ساعت و تاریخ را درست کنید. فاکتور با ساعتِ دستگاه ثبت می‌شود و «تاریخِ تصحیح‌شده» را سرور کنارش می‌گذارد.",
    "This device's clock is $gap behind — fix the time and date. Invoices keep the device time and the server adds the corrected date.",
    "د دې وسیلې ساعت $gap وروسته دی — ساعت او نېټه سمه کړئ. بل د وسیلې په ساعت ثبتېږي او سرور «سمه شوې نېټه» ورسره ږدي.",
  )
  fun clockAhead(gap: String): String = t(
    "ساعتِ این دستگاه ${asIs(gap)} جلو است — ساعت و تاریخ را درست کنید. فاکتور با ساعتِ دستگاه ثبت می‌شود و «تاریخِ تصحیح‌شده» را سرور کنارش می‌گذارد.",
    "This device's clock is $gap ahead — fix the time and date. Invoices keep the device time and the server adds the corrected date.",
    "د دې وسیلې ساعت $gap مخکې دی — ساعت او نېټه سمه کړئ. بل د وسیلې په ساعت ثبتېږي او سرور «سمه شوې نېټه» ورسره ږدي.",
  )
  fun gapDays(days: String): String = t("${asIs(days)} روز", "$days days", "$days ورځې")
  fun gapHours(hours: String): String = t("${asIs(hours)} ساعت", "$hours hours", "$hours ساعته")
  fun gapMinutes(minutes: String): String = t("${asIs(minutes)} دقیقه", "$minutes minutes", "$minutes دقیقې")
  val correctedDate: String get() = t("تاریخِ تصحیح‌شده", "Corrected date", "سمه شوې نېټه")
  /** S46/STF-04: Ctrl+L بی «تعویضِ کاربر» و بی رمزِ برنامه — قفلی نیست که بیفتد، فقط راهش */
  val lockNeedsPassword: String get() = t(
    "برای قفلِ صندوق با Ctrl+L، اول در «تنظیمات» رمزِ برنامه را بگذارید.",
    "To lock the till with Ctrl+L, first set an app password in “Settings”.",
    "د Ctrl+L سره د صندوق د قفلولو لپاره، لومړی په «تنظیمات» کې د اپ پټنوم کېږدئ.",
  )
}
