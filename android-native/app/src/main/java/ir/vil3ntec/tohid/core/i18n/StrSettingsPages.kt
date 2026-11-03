package ir.vil3ntec.tohid.core.i18n

/**
 *  صفحه‌های تنظیماتِ نسخهٔ ۳ که نوشتهٔ خامِ فارسی داشتند و از هیچ زبانی نمی‌گذشتند: گزارشِ خودکارِ صاحب
 *  (`OwnerReportScreen`)، زمانِ تقریبیِ تحویل (`StorefrontEtaScreen`)، تخفیفِ اولین خرید و سدِ سوءاستفاده
 *  (`PromoGuardScreen`)، موتورِ تازهٔ موجودی (`StockModeScreen`) و یادداشتِ صندوق (`PosNotesScreen`).
 *  فارسی کلمه‌به‌کلمه همان پیشین است. قاعده‌ها سرِ `I18n.kt`.
 */
@Suppress("unused")
object StrSettingsPages {

  // ── مشترک ──
  val off: String get() = t("خاموش", "Off", "بند")
  val on: String get() = t("روشن", "On", "روښانه")
  val save: String get() = t("ذخیره", "Save", "خوندي کول")
  val saved: String get() = t("ذخیره شد", "Saved", "خوندي شو")
  val fixFields: String get() = t(
    "چند کادر درست نیست؛ پیامِ زیرِ هر کادر را ببینید",
    "Some fields are not right; see the message under each field",
    "ځینې خانې سمې نه دي؛ د هرې خانې لاندې پیغام وګورئ",
  )

  // ── گزارشِ خودکارِ صاحب ──
  val ownerSent: String get() = t("فرستاده شد", "Sent", "ولېږل شو")
  val ownerError: String get() = t("نرسید", "Not delivered", "ونه رسېد")
  val ownerClaimed: String get() = t("در راه", "On the way", "په لار کې")
  fun ownerIntro(zone: String): String = t(
    "خلاصهٔ فروش، برگشتی، خالص، روشِ پرداخت و پرفروش‌ها از ردیف‌های همگام‌شدهٔ همین دکان، فقط برای صاحبِ دکان. " +
      "ساعت به وقتِ دکان${asIs(zone)}؛ هر روز حداکثر یک گزارش. واتس‌اپ هنوز وصل نیست.",
    "A summary of sales, returns, net, payment methods and best sellers from this shop's synced rows, for the shop owner only. " +
      "Times are in shop time$zone; at most one report a day. WhatsApp is not connected yet.",
    "د همدې دوکان له همغږي شویو قطارونو څخه د پلور، بېرته راګرځېدلو، خالص، د تادیې لارو او ډېر پلورل شویو لنډیز، یوازې د دوکان خاوند ته. " +
      "وخت د دوکان په وخت$zone؛ هره ورځ تر ټولو ډېر یو راپور. واټس‌اپ لا نه دی نښلول شوی.",
  )
  val daily: String get() = t("هر روز", "Daily", "هره ورځ")
  val weekly: String get() = t("هر هفته", "Weekly", "هره اونۍ")
  val weekday: String get() = t("روزِ هفته", "Day of week", "د اونۍ ورځ")
  val hour: String get() = t("ساعت", "Hour", "ساعت")
  val inApp: String get() = t("داخلِ برنامه", "In the app", "د اپ دننه")
  val push: String get() = t("پوش", "Push", "پوش")
  val email: String get() = t("ایمیل", "Email", "برېښنالیک")
  fun noEmail(label: String): String = t("${asIs(label)} (حساب ایمیل ندارد)", "$label (the account has no email)", "$label (حساب برېښنالیک نه لري)")
  val versionConflict: String get() = t(
    "تنظیم در این میان عوض شده؛ دوباره باز کنید",
    "The setting changed meanwhile; open it again",
    "ترتیب په دې منځ کې بدل شوی؛ بیا یې خلاص کړئ",
  )
  fun reportNet(amount: String): String = t(" — خالص ${asIs(amount)}", " — net $amount", " — خالص $amount")

  // ── زمانِ تقریبیِ تحویل ──
  val etaOn: String get() = t("روشن — مشتری بازهٔ تقریبی می‌بیند", "On — the customer sees an approximate window", "روښانه — پېرودونکی نږدې موده ویني")
  val etaIntro: String get() = t(
    "مشتری پیش از ثبت و در پیگیری یک بازه می‌بیند (نه یک وعده): صفِ سفارش‌ها، آماده‌سازی، شلوغی، پیک و راه. " +
      "با سه سفارشِ آماده‌شدهٔ اخیر، آماده‌سازی از خودِ سفارش‌ها حساب می‌شود.",
    "Before ordering and while tracking, the customer sees a window (not a promise): order queue, preparation, rush, courier and travel. " +
      "With three recently prepared orders, preparation time is computed from the orders themselves.",
    "پېرودونکی تر ثبت مخکې او د تعقیب پر مهال یوه موده ویني (ژمنه نه): د فرمایشونو کتار، چمتووالی، ګڼه‌ګوڼه، سپارونکی او لار. " +
      "له دریو وروستیو چمتو شویو فرمایشونو سره، چمتووالی پخپله له فرمایشونو حسابېږي.",
  )
  val etaHide: String get() = t("نشان داده نشود", "Don't show", "ونه ښودل شي")
  val etaShow: String get() = t("نشان داده شود", "Show", "وښودل شي")
  val etaPrep: String get() = t("دقیقهٔ آماده‌سازیِ هر سفارش (۱ تا ۲۴۰)", "Preparation minutes per order (1 to 240)", "د هر فرمایش د چمتووالي دقیقې (۱ تر ۲۴۰)")
  val etaTravel: String get() = t("دقیقهٔ راهِ پیک (۰ تا ۲۴۰)", "Courier travel minutes (0 to 240)", "د سپارونکي د لارې دقیقې (۰ تر ۲۴۰)")
  val etaParallel: String get() = t(
    "سفارشِ هم‌زمانی که دکان آماده می‌کند (۱ تا ۲۰)",
    "Orders the shop prepares at once (1 to 20)",
    "هغه فرمایشونه چې دوکان یوځای چمتو کوي (۱ تر ۲۰)",
  )
  val etaZones: String get() = t(
    "دقیقهٔ راهِ هر ناحیه (۰ تا ۲۴۰)؛ خالی = همان دقیقهٔ راهِ بالا",
    "Travel minutes per zone (0 to 240); empty = the travel minutes above",
    "د هرې سیمې د لارې دقیقې (۰ تر ۲۴۰)؛ تش = پورته د لارې همغه دقیقې",
  )
  fun inactiveZone(name: String): String = t("${asIs(name)} (غیرفعال)", "$name (inactive)", "$name (غیر فعال)")

  // ── تخفیفِ اولین خرید و سدِ سوءاستفاده ──
  val firstOff: String get() = t("اولین خرید خاموش", "First order off", "لومړی پېر بند")
  val firstOn: String get() = t("اولین خرید روشن", "First order on", "لومړی پېر روښانه")
  fun firstAmount(amount: String): String = t("اولین خرید: ${asIs(amount)}", "First order: $amount", "لومړی پېر: $amount")
  fun firstPercent(pct: String): String = t("اولین خرید: ${asIs(pct)}٪", "First order: $pct%", "لومړی پېر: $pct٪")
  fun suspects(n: String): String = t(" · ${asIs(n)} موردِ مشکوک", " · $n suspicious", " · $n شکمن موارد")
  val firstNote: String get() = t(
    "اولین خرید فقط یک بار برای هر شمارهٔ تأییدشده و هر دستگاه، وقتی آن شماره سفارشِ پیشینی در این دکان ندارد.",
    "The first-order offer applies once per verified number and device, when that number has no earlier order in this shop.",
    "د لومړي پېر تخفیف د هرې تایید شوې شمېرې او وسیلې لپاره یو ځل دی، کله چې هغه شمېره په دې دوکان کې پخوانی فرمایش نه لري.",
  )
  val percent: String get() = t("درصد", "Percent", "سلنه")
  val amount: String get() = t("مبلغ", "Amount", "مبلغ")
  val percentField: String get() = t("درصدِ تخفیف (تا ۱۰۰)", "Discount percent (up to 100)", "د تخفیف سلنه (تر ۱۰۰)")
  val maxField: String get() = t("سقفِ تخفیف به افغانی (خالی = بی سقف)", "Discount cap in AFN (empty = no cap)", "د تخفیف سقف په افغانیو (تش = بې سقفه)")
  val amountField: String get() = t("مبلغِ تخفیف به افغانی", "Discount amount in AFN", "د تخفیف مبلغ په افغانیو")
  val minSubtotalField: String get() = t("کفِ سبد به افغانی (خالی = بی کف)", "Minimum basket in AFN (empty = none)", "د ټوکرۍ لږترلږه په افغانیو (تش = نشته)")
  val stackCombine: String get() = t("با تخفیف‌های دیگر", "With other discounts", "له نورو تخفیفونو سره")
  val stackExclusive: String get() = t("انحصاری", "Exclusive", "انحصاري")
  val guardNote: String get() = t(
    "سدِ سوءاستفاده — برای همهٔ کدهای ویترین؛ خالی یا ۰ = بی سقف",
    "Abuse guard — for all storefront codes; empty or 0 = no cap",
    "د ناوړه ګټې مخنیوی — د ویترین ټولو کوډونو لپاره؛ تش یا ۰ = بې سقفه",
  )
  val codePerPhone: String get() = t("هر کد برای هر شماره چند بار (۰ تا ۱۰۰۰)", "Uses per code per number (0 to 1000)", "هر کوډ د هرې شمېرې لپاره څو ځله (۰ تر ۱۰۰۰)")
  val codePerDevice: String get() = t("هر کد برای هر دستگاه چند بار (۰ تا ۱۰۰۰)", "Uses per code per device (0 to 1000)", "هر کوډ د هرې وسیلې لپاره څو ځله (۰ تر ۱۰۰۰)")
  val maxDiscount: String get() = t("سقفِ تخفیفِ هر سفارش به افغانی", "Discount cap per order in AFN", "د هر فرمایش د تخفیف سقف په افغانیو")
  val recentDiscounts: String get() = t("تخفیف‌های نشستهٔ اخیر", "Recent applied discounts", "وروستي پلي شوي تخفیفونه")

  // ── موتورِ تازهٔ موجودی ──
  val stockModeOn: String get() = t("موتورِ تازه روشن (آزمایشی)", "New engine on (experimental)", "نوی انجن روښانه (ازمایښتي)")
  val stockModeNote: String get() = t(
    "روشن: بهای میانگینِ موزون با هزینهٔ جانبیِ خرید، سری و انقضا با برداشتِ FEFO سرِ صندوق، دفترِ حرکت با کاربر و دلیل، " +
      "شمارش با بازبینی، انتقال بین محل‌ها و «وظیفهٔ بررسی» برای موجودیِ منفی. برای همهٔ دستگاه‌های همین دکان یکی است.",
    "On: weighted average cost with purchase extras, batches and expiry with FEFO picking at the till, a movement ledger with user and reason, " +
      "counts with review, transfers between places and a \"review task\" for negative stock. Same for all devices of this shop.",
    "روښانه: د پېر له اضافي لګښت سره وزن لرونکې منځنۍ بیه، سري او د پای نېټه په صندوق کې د FEFO اخیستو سره، د حرکت دفتر له کارن او دلیل سره، " +
      "شمېرنه له بیا کتنې سره، د ځایونو ترمنځ لېږد او د منفي موجودۍ لپاره «د کتنې دنده». د همدې دوکان د ټولو وسیلو لپاره یو شان دی.",
  )
  val stockModeTrial: String get() = t(
    "آزمایشی: روشِ بهای تمام‌شده هنوز تصمیمِ باز است (D-23)، پس بها و سود «تقریبی»اند.",
    "Experimental: the costing method is still an open decision (D-23), so cost and profit are \"approximate\".",
    "ازمایښتي: د تمام شوې بیې لاره لا پرانیستې پرېکړه ده (D-23)، نو بیه او ګټه «نږدې» دي.",
  )
  val stockModeAskOn: String get() = t("موتورِ تازهٔ موجودی و بها روشن شود؟", "Turn on the new stock and cost engine?", "د موجودۍ او بیې نوی انجن روښانه شي؟")
  val stockModeAskOff: String get() = t("موتورِ تازه خاموش شود؟", "Turn off the new engine?", "نوی انجن بند شي؟")
  val stockModeOnBody: String get() = t(
    "روی همهٔ دستگاه‌های این دکان روشن می‌شود. موجودیِ هر کالا همان عددِ امروز می‌ماند.",
    "It turns on for all devices of this shop. Each item's stock stays at today's number.",
    "د دې دوکان په ټولو وسیلو روښانه کېږي. د هر توکي موجودي د نن همغه شمېر پاتې کېږي.",
  )
  val stockModeOffBody: String get() = t(
    "فروش و خرید به رفتارِ امروز برمی‌گردد. چیزی از دفتر پاک نمی‌شود.",
    "Sales and purchases go back to today's behaviour. Nothing is erased from the ledger.",
    "پلور او پېر د نن چلند ته بېرته ګرځي. له دفتر څخه هېڅ نه پاکېږي.",
  )
  val turnOn: String get() = t("روشن کن", "Turn on", "روښانه یې کړه")
  val turnOff: String get() = t("خاموش کن", "Turn off", "بند یې کړه")

  // ── یادداشتِ صندوق ──
  val posNotesNote: String get() = t(
    "یادداشتِ هر ردیف (در کادرِ ویرایشِ ردیف) و یادداشتِ کلِ فاکتور، روی رسید چاپ می‌شوند. قالب‌ها با یک لمس افزوده می‌شوند. انتخابِ همین دستگاه است.",
    "Each line's note (in the line edit box) and the whole-invoice note are printed on the receipt. Templates are added with one tap. This device's choice.",
    "د هر قطار یادښت (د قطار د سمون په خانه کې) او د ټول بل یادښت په رسید چاپېږي. کېڼلارې په یوه ټک زیاتېږي. د همدې وسیلې انتخاب دی.",
  )
  val delete: String get() = t("حذف", "Delete", "ړنګول")
  val newTemplate: String get() = t("قالبِ تازه", "New template", "نوې کېڼلاره")
  val add: String get() = t("افزودن", "Add", "زیاتول")
}
