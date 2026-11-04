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

  // <moved-raw-text>
  // ── `OwnerReportPlan.kt` ──

  // متنِ یاورِ «گزارشِ خودکارِ صاحب» (`OwnerReportPlan`) — پیش‌تر متنِ خام
  val reportYesterday: String get() = t("دیروز (کامل)", "Yesterday (full)", "پرون (بشپړ)")
  fun reportToday(hour: Any?): String = t("امروز تا ساعتِ ${asIs(hour)}", "Today until $hour", "نن تر $hour پورې")
  fun reportWeekly(day: Any?, hour: Any?, span: Any?): String = t(
    "هر ${asIs(day)} ساعتِ ${asIs(hour)}: هفت روزِ تا ${asIs(span)}",
    "Every $day at $hour: seven days up to $span",
    "هر $day په $hour: تر $span پورې اوه ورځې",
  )
  fun reportDaily(hour: Any?, span: Any?): String = t(
    "هر روز ساعتِ ${asIs(hour)}: ${asIs(span)}",
    "Every day at $hour: $span",
    "هره ورځ په $hour: $span",
  )

  // ── `StorefrontEtaApi.kt` ──

  // متنِ یاورِ «زمانِ تقریبیِ تحویل» (`StorefrontEtaApi`) — پیش‌تر متنِ خام
  fun etaWholeRange(lo: Any?, hi: Any?): String = t(
    "عددِ صحیح از ${asIs(lo)} تا ${asIs(hi)}",
    "A whole number from $lo to $hi",
    "له $lo څخه تر $hi پورې بشپړ عدد",
  )
  fun etaTooManyZones(n: Any?): String = t("بیش از ${asIs(n)} ناحیه", "More than $n zones", "له $n سیمو زیات")
  fun etaMinutes(lo: Any?, hi: Any?): String = t("${asIs(lo)} تا ${asIs(hi)} دقیقه", "$lo to $hi minutes", "$lo تر $hi دقیقې")
  val etaWhyHidden: String get() = t("نمایش خاموش است", "Display is off", "ښودنه بنده ده")
  val etaWhyPaused: String get() = t("پذیرشِ سفارش موقتاً متوقف است", "Taking orders is paused for now", "د فرمایشونو منل لنډمهاله درول شوي دي")
  val etaWhyClosed: String get() = t("دکان الان بسته است", "The shop is closed now", "دوکان اوس تړلی دی")
  val etaWhyUnconfirmed: String get() = t(
    "اتصالِ دکان هنوز تأیید نشده",
    "The shop's connection is not confirmed yet",
    "د دوکان نښلول لا نه دي تایید شوي",
  )
  val etaWhyOther: String get() = t("الان عددی نشان داده نمی‌شود", "No number is shown right now", "اوس هېڅ شمېره نه ښودل کېږي")
  val etaNoStorefront: String get() = t(
    "ویترین هنوز ساخته نشده؛ پیش‌نمایشی نیست",
    "The storefront isn't built yet; there's no preview",
    "ویترین لا نه دی جوړ شوی؛ مخکتنه نشته",
  )
  fun etaNoNumber(reason: Any?): String = t(
    "مشتری الان عددی نمی‌بیند: ${asIs(reason)}",
    "The customer sees no number right now: $reason",
    "پېرودونکی اوس هېڅ شمېره نه ویني: $reason",
  )
  fun etaDelivery(range: Any?): String = t("ارسال: ${asIs(range)}", "Delivery: $range", "لېږل: $range")
  fun etaPickup(range: Any?): String = t("دریافت از دکان: ${asIs(range)}", "Pickup at the shop: $range", "له دوکانه اخیستل: $range")
  val etaZone: String get() = t("ناحیه", "Zone", "سیمه")
  fun etaQueue(n: Any?): String = t("صف: ${asIs(n)} سفارش", "Queue: $n orders", "کتار: $n فرمایشونه")
  fun etaPrepNow(n: Any?): String = t(" · آماده‌سازی: ${asIs(n)} دقیقه", " · Preparation: $n min", " · چمتووالی: $n دقیقې")
  val etaFromHistory: String get() = t(" (از سفارش‌های اخیر)", " (from recent orders)", " (له وروستیو فرمایشونو)")
  val etaFromSetting: String get() = t(
    " (از تنظیم؛ هنوز سفارشِ کافی نیست)",
    " (from the setting; not enough orders yet)",
    " (له ترتیب څخه؛ لا کافي فرمایشونه نشته)",
  )
  fun etaBusyNow(n: Any?): String = t(" · شلوغ: +${asIs(n)} دقیقه", " · Busy: +$n min", " · ګڼه‌ګوڼه: +$n دقیقې")
  fun etaCouriersNow(live: Any?, cap: Any?): String = t(
    " · پیک: ${asIs(live)} در راه از ${asIs(cap)}",
    " · Couriers: $live on the way of $cap",
    " · سپارونکي: له $cap څخه $live په لار کې",
  )
  val etaConflict: String get() = t(
    "تنظیم در این میان جای دیگری عوض شد؛ دوباره باز کنید",
    "The setting changed elsewhere meanwhile; open it again",
    "ترتیب په دې منځ کې بل ځای بدل شو؛ بیا یې خلاص کړئ",
  )
  val etaDisabled: String get() = t(
    "زمانِ تقریبیِ تحویل برای این دکان روشن نیست",
    "Approximate delivery time is not on for this shop",
    "د سپارلو نږدې وخت د دې دوکان لپاره روښانه نه دی",
  )
  val saveFailed: String get() = t("ذخیره نشد؛ دوباره تلاش کنید", "Not saved; try again", "خوندي نه شو؛ بیا هڅه وکړئ")

  // ── `PromoGuardApi.kt` ──

  // متنِ یاورِ «اولین خرید و سدِ سوءاستفاده» (`PromoGuardApi`) — پیش‌تر متنِ خام
  val guardAfsRange: String get() = t(
    "افغانیِ درست، بی اعشار، تا ۱٬۰۰۰٬۰۰۰ (خالی = بی حد)",
    "Whole afghanis, no decimals, up to 1,000,000 (empty = no limit)",
    "پوره افغانۍ، بې اعشاریه، تر ۱٬۰۰۰٬۰۰۰ پورې (تش = بې حده)",
  )
  val guardPercentRange: String get() = t(
    "درصد از ۰ تا ۱۰۰، با حداکثر دو رقمِ اعشار",
    "A percent from 0 to 100, with at most two decimal places",
    "سلنه له ۰ تر ۱۰۰، تر ټولو ډېر له دوو اعشاري رقمونو سره",
  )
  val guardCountRange: String get() = t(
    "عددِ صحیح از ۰ تا ۱٬۰۰۰ (خالی = بی سقف)",
    "A whole number from 0 to 1,000 (empty = no cap)",
    "بشپړ عدد له ۰ تر ۱٬۰۰۰ (تش = بې سقفه)",
  )
  val guardPercentZero: String get() = t(
    "برای اولین خریدِ روشن، درصد بیش از صفر باشد",
    "With the first-order offer on, the percent must be above zero",
    "کله چې د لومړي پېر تخفیف روښانه وي، سلنه باید له صفر زیاته وي",
  )
  val guardAmountZero: String get() = t(
    "برای اولین خریدِ روشن، مبلغ بیش از صفر باشد",
    "With the first-order offer on, the amount must be above zero",
    "کله چې د لومړي پېر تخفیف روښانه وي، مبلغ باید له صفر زیات وي",
  )
  fun guardOneDevice(device: Any?, n: Any?): String = t(
    "یک دستگاه (${asIs(device)}) با ${asIs(n)} شماره",
    "One device ($device) with $n numbers",
    "یوه وسیله ($device) له $n شمېرو سره",
  )
  fun guardOnePhone(phone: Any?, n: Any?): String = t(
    "شمارهٔ ${asIs(phone)} روی ${asIs(n)} دستگاه",
    "Number $phone on $n devices",
    "شمېره $phone په $n وسیلو",
  )
  fun guardRule(n: Any?): String = t(
    "${asIs(n)} شماره یا بیشتر از یک دستگاه، یا ${asIs(n)} دستگاه یا بیشتر برای یک شماره",
    "$n or more numbers from one device, or $n or more devices for one number",
    "له یوې وسیلې $n یا ډېرې شمېرې، یا د یوې شمېرې لپاره $n یا ډېرې وسیلې",
  )
  fun guardNoSuspects(rule: Any?): String = t(
    "موردِ مشکوکی نیست (${asIs(rule)}، ۹۰ روزِ اخیر)",
    "Nothing suspicious ($rule, last 90 days)",
    "شکمن مورد نشته ($rule، وروستۍ ۹۰ ورځې)",
  )
  fun guardSuspects(n: Any?, rule: Any?): String = t("${asIs(n)} موردِ مشکوک (${asIs(rule)})", "$n suspicious ($rule)", "$n شکمن موارد ($rule)")
  val promoKindFirst: String get() = t("اولین خرید", "First order", "لومړی پېر")
  val promoKindCode: String get() = t("کد", "Code", "کوډ")
  val promoKindAuto: String get() = t("خودکار", "Automatic", "اتومات")
  val guardDisabled: String get() = t(
    "سدِ تخفیفِ ویترین برای این دکان روشن نیست",
    "The storefront discount guard is not on for this shop",
    "د ویترین د تخفیف مخنیوی د دې دوکان لپاره روښانه نه دی",
  )
  val guardUnknownDevice: String get() = t("نامعلوم", "unknown", "نامعلوم")

  // ── `SmartInsightsAndroid.kt` ──

  // عنوان و خطِ بخش‌های تنظیمات که پیش‌تر خودِ کلیدِ فارسی یا متنِ خام بودند (`SettingsLayout.labelOf`)
  val titleSmartForecast: String get() = t(
    "پیش‌بینیِ تقاضا (آزمایشی)",
    "Demand forecast (experimental)",
    "د غوښتنې وړاندوینه (ازمایښتي)",
  )
  val titleAnomaly: String get() = t(
    "تشخیصِ ناهنجاری (آزمایشی)",
    "Anomaly detection (experimental)",
    "د غیر عادي حالتونو موندنه (ازمایښتي)",
  )
  val smartForecastNote: String get() = t(
    "در «پیشنهادِ سفارش»: کالای پرفروش با میانگینِ متحرکِ ۲۸ روز و روزِ هفته، کالای کم‌فروش با روشِ Croston؛ پیش‌بینیِ هر روزِ آینده، دلیل از همان عددها و اطمینان از درازای داده و نوسان. بی اینترنت، روی دفترِ همین دستگاه.",
    "In \"suggested order\": fast sellers with a 28-day moving average and day of week, slow sellers with Croston's method; a forecast for each coming day, the reason from the same numbers, and confidence from the data length and swings. No internet needed, on this device's books.",
    "په «وړاندیز شوي فرمایش» کې: ډېر پلورل کېدونکي توکي د ۲۸ ورځو له خوځنده منځني او د اونۍ له ورځې سره، لږ پلورل کېدونکي د Croston په لاره؛ د هرې راتلونکې ورځې وړاندوینه، دلیل له همغو شمېرو او باور د معلوماتو له اوږدوالي او بدلون څخه. بې انټرنټه، د همدې وسیلې په دفتر.",
  )
  val anomalyNote: String get() = t(
    "در زبانهٔ «ضدتقلب» ِ گزارشِ تازه: لغو و تخفیف و کشوی هر صندوقدار، اختلافِ صندوق، کمِ موجودی با «اصلاح»، فروش زیرِ بها، فاکتورِ نیمه‌شب و روزهای بیرون از معمولِ برگشتی و تخفیف. هر مورد فقط «نیازمندِ بررسی» است و چیزی را نمی‌بندد.",
    "In the \"anti-fraud\" tab of the new report: each cashier's voids, discounts and drawer opens, till differences, stock shortfalls with \"corrections\", sales below cost, midnight invoices and days with unusual returns and discounts. Each item is only \"needs review\" and blocks nothing.",
    "د نوي راپور په «د درغلۍ ضد» برخه کې: د هر صندوقدار لغوه، تخفیف او دراز، د صندوق توپیر، د «سمون» سره د موجودۍ کمښت، له بیې ټیټ پلور، د نیمې شپې بلونه او هغه ورځې چې ستنېدنې او تخفیفونه یې له عادي بهر دي. هر مورد یوازې «کتنې ته اړتیا» ده او هېڅ شی نه بندوي.",
  )

  // ── `EasySetupSettingsAndroid.kt` ──
  val titleEasySetup: String get() = t(
    "راه‌اندازیِ آسانِ چاپگر و بارکدخوان (آزمایشی)",
    "Easy printer and scanner setup (experimental)",
    "د چاپګر او بارکوډ لوستونکي اسانه چمتو کول (ازمایښتي)",
  )
  val easySetupNote: String get() = t(
    "در «دستگاه‌ها»: پیدا کردنِ خودکارِ چاپگرِ شبکه (فقط زیرشبکهٔ همین وای‌فای/LAN، درگاهِ ۹۱۰۰) و بلوتوثِ نزدیک، چاپِ شناسایی، برگهٔ بارکدِ آزمون و «آزمونِ بارکدخوان» که پیشوند و پسوندِ بارکدخوان را خودش پیدا می‌کند.",
    "In \"Devices\": automatic discovery of network printers (only this Wi-Fi/LAN subnet, port 9100) and nearby Bluetooth, an identification print, a barcode test sheet and a \"scanner test\" that finds the scanner's prefix and suffix by itself.",
    "په «وسیلو» کې: د شبکې د چاپګر اتومات موندل (یوازې د همدې وای‌فای/LAN فرعي شبکه، دروازه ۹۱۰۰) او نږدې بلوتوث، د پېژندنې چاپ، د بارکوډ ازموینې پاڼه او «د لوستونکي ازموینه» چې د لوستونکي مختاړی او وروستاړی پخپله پیدا کوي.",
  )

  // ── `StockPlacesSheet.kt` ──
  val titleStockPlaces: String get() = t(
    "محل‌ها و بسته‌بندیِ دوباره",
    "Places and repacking",
    "ځایونه او بیا بسته بندي",
  )

  // ── `TrainingAndroid.kt` ──
  val titleTraining: String get() = t(
    "حالتِ آموزشی",
    "Training mode",
    "د زده کړې حالت",
  )

  // ── `OwnerReportSettings.kt` ──
  val titleOwnerReport: String get() = t(
    "گزارشِ خودکار برای صاحب",
    "Automatic report for the owner",
    "خاوند ته اتومات راپور",
  )

  // ── `StorefrontOwnerSettings.kt` ──
  val titleStorefrontEta: String get() = t(
    "زمانِ تقریبیِ تحویلِ ویترین",
    "Storefront approximate delivery time",
    "د ویترین د سپارلو نږدې وخت",
  )
  val titlePromoGuard: String get() = t(
    "تخفیفِ اولین خرید و سدِ سوءاستفاده",
    "First-order discount and abuse guard",
    "د لومړي پېر تخفیف او د ناوړه ګټې مخنیوی",
  )

  // ── `TillGoodsSettingsAndroid.kt` ──
  val titleTillGoods: String get() = t(
    "ثبتِ سریع و واحدِ بزرگ در صندوق",
    "Quick add and big units at the till",
    "په صندوق کې چټک ثبت او لوی واحد",
  )
  // </moved-raw-text>
}
