package ir.vil3ntec.tohid.core.i18n

/**
 *  جریانِ سفارشِ آنلاین (درخواستِ صاحب‌کار ۱۴۰۵/۰۸/۱۳): «چرا سفارش نمی‌رسد؟» (`arrival` ِ `/shop/orders/config`)،
 *  «پرداخت پس از پذیرش» (حالِ پرداخت برای فروشنده) و «به سبد / فاکتور» ِ یک‌ضرب. سرور: `lib/order-arrival.js`،
 *  `lib/order-flow.js`. جدا از `StrOnline` و `StrOrdersHub` تا آن‌ها زیرِ سقفِ اندازه بمانند.
 */
@Suppress("unused")
object StrOrderFlow {

  // ── «چرا سفارش نمی‌رسد؟» ──
  val arrivalTitle: String get() = t("چرا سفارشِ آنلاین نمی‌رسد", "Why online orders aren't arriving", "ولې آنلاین فرمایشونه نه راځي")
  val arrivalOk: String get() = t(
    "سفارشِ آنلاین باز است: ویترین روشن است، کالا منتشر شده و سفارش‌ها همین‌جا می‌رسند.",
    "Online orders are open: the storefront is on, products are published and orders arrive here.",
    "آنلاین فرمایشونه پرانیستي دي: ویترین روښانه دی، توکي خپاره شوي او فرمایشونه همدلته راځي.",
  )

  /** سرخطِ هر دروازه (کدِ `arrival` ِ سرور)؛ کدِ ناشناختهٔ سرورِ تازه‌تر ⇒ متنِ عمومی */
  fun blocker(code: String): String = when (code) {
    "orders_off" -> t("سفارشِ آنلاین برای این دکان خاموش است", "Online orders are switched off for this shop", "د دې دوکان لپاره آنلاین فرمایشونه بند دي")
    "no_subscription" -> t("برای دریافتِ سفارش اشتراک لازم است", "A subscription is needed to receive orders", "د فرمایش ترلاسه کولو لپاره ګډون اړین دی")
    "storefront_off" -> t("ویترینِ آنلاین برای این دکان روشن نشده", "The online storefront isn't enabled for this shop", "د دې دوکان لپاره آنلاین ویترین نه دی فعال شوی")
    "storefront_not_in_plan" -> t("اشتراکِ شما ویترین را ندارد", "Your plan doesn't include the storefront", "ستاسو ګډون ویترین نه لري")
    "trial_not_accepting" -> t("در دورهٔ آزمایشی سفارشِ تازه گرفته نمی‌شود", "New orders aren't accepted during the trial", "د ازمېښتي دورې پر مهال نوي فرمایشونه نه منل کېږي")
    "storefront_missing" -> t("ویترین هنوز ساخته نشده", "The storefront hasn't been set up yet", "ویترین لا نه دی جوړ شوی")
    "storefront_hidden" -> t("ویترین خاموش است", "The storefront is switched off", "ویترین بند دی")
    "shop_inactive" -> t("حسابِ دکان فعال نیست", "The shop account isn't active", "د دوکان حساب فعال نه دی")
    "storefront_suspended" -> t("ویترین معلق است", "The storefront is suspended", "ویترین ځنډول شوی")
    "no_products" -> t("هیچ کالایی در ویترین نیست", "No products are on the storefront", "په ویترین کې هېڅ توکی نشته")
    "phone_check_off" -> t("تأییدِ شمارهٔ مشتری روی سرور راه نیفتاده", "Customer phone verification isn't set up on the server", "د پېرودونکي د شمېرې تایید په سرور کې نه دی چالان شوی")
    "server_not_ready" -> t("ویترین روی سرور آماده نیست", "The storefront isn't ready on the server", "ویترین په سرور کې چمتو نه دی")
    "alerts_off" -> t("اعلانِ سفارشِ تازه خاموش است", "New-order alerts are off", "د نوي فرمایش خبرتیا بنده ده")
    else -> t("سفارشِ آنلاین به این دکان نمی‌رسد", "Online orders can't reach this shop", "آنلاین فرمایشونه دې دوکان ته نه رسېږي")
  }

  /** «چه باید کرد» ِ هر دروازه، به زبانِ ساده */
  fun blockerHow(code: String): String = when (code) {
    "orders_off" -> t(
      "تا روشن نشود هیچ سفارشی از ویترین و بازارچه نمی‌رسد. روشن کردنش با پشتیبانی است.",
      "Until it is on, no order from the storefront or the market reaches you. Support switches it on.",
      "تر څو فعال نه شي، له ویترین او بازارچې هېڅ فرمایش نه راځي. فعالول یې د ملاتړ کار دی.",
    )
    "no_subscription" -> t(
      "اشتراکِ دکان «سفارشِ آنلاین» را ندارد یا تمام شده است؛ اشتراک را تمدید کنید.",
      "The shop's subscription lacks online orders or has ended; renew it.",
      "د دوکان ګډون «آنلاین فرمایش» نه لري یا پای ته رسېدلی؛ ګډون نوی کړئ.",
    )
    "storefront_off" -> t(
      "مشتری بی ویترین نمی‌تواند سفارش بدهد؛ از پشتیبانی بخواهید ویترین را برای این دکان روشن کند.",
      "Customers can't order without a storefront; ask support to enable it for this shop.",
      "پېرودونکی بې ویترینه فرمایش نشي ورکولی؛ له ملاتړ وغواړئ چې ویترین فعال کړي.",
    )
    "storefront_not_in_plan" -> t(
      "اشتراکی بگیرید که «ویترین» را هم دارد.",
      "Choose a plan that includes the storefront.",
      "داسې ګډون واخلئ چې «ویترین» هم ولري.",
    )
    "trial_not_accepting" -> t(
      "طبقِ قاعدهٔ سامانه، سفارشِ آنلاینِ تازه فقط برای دکانِ دارای اشتراکِ پولی است؛ با خریدِ اشتراک روشن می‌شود.",
      "By the platform rule, new online orders are only for shops with a paid subscription; buying one turns them on.",
      "د سامانې د قاعدې له مخې، نوی آنلاین فرمایش یوازې د پیسو ګډون لرونکو دوکانونو لپاره دی؛ د ګډون په اخیستو فعالېږي.",
    )
    "storefront_missing" -> t(
      "در «ویترین» نام و نشانی بگذارید و روشنش کنید.",
      "In Storefront, set a name and address and switch it on.",
      "په «ویترین» کې نوم او پته کېږدئ او فعال یې کړئ.",
    )
    "storefront_hidden" -> t(
      "ویترین ساخته شده ولی روشن نیست و مشتری آن را نمی‌بیند. در «ویترین» روشنش کنید.",
      "The storefront exists but is off, so customers can't see it. Switch it on in Storefront.",
      "ویترین جوړ شوی خو فعال نه دی او پېرودونکی یې نه ویني. په «ویترین» کې یې فعال کړئ.",
    )
    "shop_inactive" -> t("با پشتیبانی تماس بگیرید.", "Contact support.", "له ملاتړ سره اړیکه ونیسئ.")
    "storefront_suspended" -> t(
      "تا رفعِ تعلیق، ویترین برای مشتری بسته است؛ با پشتیبانی پیگیری کنید.",
      "Until the suspension is lifted the storefront is closed to customers; follow up with support.",
      "تر څو ځنډ لرې شي، ویترین پېرودونکو ته تړلی دی؛ له ملاتړ سره یې تعقیب کړئ.",
    )
    "no_products" -> t(
      "کالاها را در «ویترین» منتشر کنید؛ کالای بی قیمت یا غیرفعال دیده نمی‌شود.",
      "Publish products in Storefront; items without a price or inactive ones aren't shown.",
      "توکي په «ویترین» کې خپاره کړئ؛ بې بیې یا غیرفعال توکي نه ښکاري.",
    )
    "phone_check_off", "server_not_ready" -> t(
      "مشتری اکنون نمی‌تواند سفارش را نهایی کند. این کارِ پشتیبانیِ سرور است.",
      "Customers can't complete an order right now. This is for server support to fix.",
      "پېرودونکی اوس فرمایش نشي بشپړولی. دا د سرور د ملاتړ کار دی.",
    )
    "alerts_off" -> t(
      "سفارش در «سفارش‌های آنلاین» می‌آید ولی زنگ نمی‌زند؛ از پشتیبانی بخواهید «اعلانِ سفارش» را روشن کند.",
      "Orders appear in Online orders but don't ring; ask support to turn on order alerts.",
      "فرمایش په «آنلاین فرمایشونو» کې راځي خو زنګ نه وهي؛ له ملاتړ وغواړئ چې د فرمایش خبرتیا فعاله کړي.",
    )
    else -> t("با پشتیبانی تماس بگیرید.", "Contact support.", "له ملاتړ سره اړیکه ونیسئ.")
  }

  val fixStorefront: String get() = t("باز کردنِ ویترین", "Open storefront", "ویترین پرانیستل")
  val fixProducts: String get() = t("کالاهای ویترین", "Storefront products", "د ویترین توکي")
  val fixSubscribe: String get() = t("اشتراک", "Subscription", "ګډون")
  val fixSupport: String get() = t("پشتیبانی", "Support", "ملاتړ")

  val foregroundOnly: String get() = t(
    "سفارشِ تازه فقط وقتی این برنامه باز است خبر می‌دهد (هر ۲۰ ثانیه می‌پرسد)؛ برنامهٔ بسته زنگ نمی‌زند.",
    "New orders only alert while this app is open (it checks every 20 seconds); a closed app doesn't ring.",
    "نوی فرمایش یوازې هغه وخت خبر ورکوي چې دا برنامه پرانیستې وي (هر ۲۰ ثانیې پوښتي)؛ تړلې برنامه زنګ نه وهي.",
  )
  val notificationsBlocked: String get() = t(
    "اجازهٔ اعلانِ این برنامه بسته است؛ از تنظیماتِ گوشی اجازه بدهید تا سفارشِ تازه زنگ بزند.",
    "Notifications are blocked for this app; allow them in the phone settings so new orders ring.",
    "د دې برنامې د خبرتیا اجازه بنده ده؛ د تلیفون له تنظیماتو اجازه ورکړئ چې نوی فرمایش زنګ ووهي.",
  )
  val roleCannotSee: String get() = t(
    "این حساب «سفارش‌ها» را نمی‌بیند؛ صاحبِ دکان باید اجازه بدهد.",
    "This account can't see orders; the shop owner must allow it.",
    "دا حساب «فرمایشونه» نه ویني؛ د دوکان خاوند باید اجازه ورکړي.",
  )

  // ── تنظیمات: «سفارشِ آنلاین» ──
  val settingsTitle: String get() = t("سفارشِ آنلاین", "Online orders", "آنلاین فرمایشونه")
  val settingsLine: String get() = t("چرا سفارش نمی‌رسد، و پرداخت پس از پذیرش", "Why orders aren't arriving, and payment after accepting", "ولې فرمایش نه راځي، او له منلو وروسته تادیه")
  val settingsExample: String get() = t(
    "مثلاً اگر ویترین خاموش است یا کالایی منتشر نشده، همین‌جا گفته می‌شود و با یک دکمه درستش می‌کنید.",
    "For example, if the storefront is off or no product is published, it says so here and one button takes you to fix it.",
    "د بېلګې په توګه، که ویترین بند وي یا هېڅ توکی خپور شوی نه وي، همدلته ویل کېږي او په یوه تڼۍ یې سموئ.",
  )

  // ── «پرداخت پس از پذیرش» (تنظیمِ دکان) ──
  val payAfterAccept: String get() = t("پرداخت پس از پذیرشِ فروشنده", "Payment after the seller accepts", "د پلورونکي له منلو وروسته تادیه")
  val payAfterAcceptHelp: String get() = t(
    "روشن: مشتری تا «قبول» نزنید نمی‌تواند آنلاین بپردازد؛ پس از قبول، صفحهٔ پیگیریِ او «پرداخت» را نشان می‌دهد: نقد هنگامِ تحویل یا برداشت، یا حساب‌پی وقتی برای دکان روشن است. خاموش: مثلِ امروز.",
    "On: customers can't pay online until you accept; after that their tracking page shows Payment — cash on delivery or at pickup, or HesabPay when it is on for the shop. Off: as today.",
    "فعال: پېرودونکی تر څو تاسو «منل» ونه کړئ آنلاین نشي ورکولی؛ له منلو وروسته د تعقیب پاڼه «تادیه» ښيي: د سپارلو یا اخیستو پر مهال نغدې، یا حساب‌پې کله چې فعال وي. بند: د نن په څېر.",
  )
  val payAfterAcceptNoStore: String get() = t(
    "این تنظیم پس از ساختنِ ویترین پیدا می‌شود.",
    "This setting appears once the storefront is set up.",
    "دا تنظیم د ویترین له جوړېدو وروسته ښکاري.",
  )
  val saved: String get() = t("ثبت شد", "Saved", "ثبت شو")

  // ── حالِ پرداختِ مشتری (جزئیاتِ سفارش، `payStep`) ──
  val payTitle: String get() = t("پرداختِ مشتری", "Customer payment", "د پېرودونکي تادیه")
  fun payState(state: String, pickup: Boolean, chosen: Boolean): String = when (state) {
    "awaiting_accept" -> t("پس از «قبول» از مشتری روشِ پرداخت پرسیده می‌شود.", "After you accept, the customer is asked how to pay.", "له منلو وروسته له پېرودونکي د تادیې لاره پوښتل کېږي.")
    "awaiting_choice" -> t("منتظرِ انتخابِ مشتری؛ اگر انتخاب نکند، نقد هنگامِ تحویل.", "Waiting for the customer to choose; if they don't, cash on delivery.", "د پېرودونکي د ټاکنې په تمه؛ که ونه ټاکي، د سپارلو پر مهال نغدې.")
    "online_pending" -> t("پرداختِ آنلاین در جریان است.", "Online payment is in progress.", "آنلاین تادیه روانه ده.")
    "paid_online" -> t("پرداخت‌شده با حساب‌پی.", "Paid with HesabPay.", "په حساب‌پې ورکړل شوې.")
    "cash_collected" -> t("نقد گرفته شد؛ منتظرِ تسویهٔ پیک.", "Cash collected; waiting for the courier to settle.", "نغدې واخیستل شوې؛ د پیک د تصفیې په تمه.")
    "cash_settled" -> t("نقد به دکان رسید.", "The cash reached the shop.", "نغدې دوکان ته ورسېدې.")
    "refunded" -> t("پول برگشت داده شد.", "The money was refunded.", "پیسې بېرته ورکړل شوې.")
    "closed" -> t("سفارش بسته است.", "The order is closed.", "فرمایش تړل شوی.")
    else -> (if (pickup) t("نقد هنگامِ برداشت — سرِ پیشخوان.", "Cash at pickup — at the counter.", "د اخیستو پر مهال نغدې — په کاونټر کې.")
    else t("نقد هنگامِ تحویل — پیک می‌گیرد.", "Cash on delivery — the courier collects.", "د سپارلو پر مهال نغدې — پیک یې اخلي.")) +
      (if (chosen) " " + t("(انتخابِ مشتری)", "(customer's choice)", "(د پېرودونکي ټاکنه)") else "")
  }

  // ── «به سبد / فاکتور» ──
  val invoiceTitle: String get() = t("فاکتور", "Invoice", "بل")
  val toInvoice: String get() = t("به سبد / فاکتور", "To cart / invoice", "ټوکرۍ / بل ته")
  val packAndInvoice: String get() = t("بسته شد و فاکتور", "Packed — invoice it", "وتړل شو او بل")
  val invoiceAndHandOver: String get() = t("فاکتور و تحویل به مشتری", "Invoice and hand to customer", "بل او پېرودونکي ته سپارل")
  fun invoiceHint(lines: Int, total: String): String = t(
    "همهٔ ${asIs(lines)} قلم با همان قیمتِ سفارش (جمع ${asIs(total)}) یک‌جا فاکتور می‌شود و موجودی همان لحظه کم می‌شود؛ تحویلِ بعدی فاکتورِ دوم نمی‌سازد.",
    "All $lines items at the order's prices (total $total) go on one invoice and stock drops right then; the later handover won't make a second invoice.",
    "ټول $lines توکي د فرمایش په همغه بیه (ټول $total) په یوه بل کې راځي او زېرمه همغه وخت کمېږي؛ وروسته سپارل دوهم بل نه جوړوي.",
  )
  val payCourier: String get() = t("پول: نقد نزدِ پیک تا تسویه (به دخل نمی‌آید).", "Money: cash stays with the courier until settlement (not in the drawer).", "پیسې: نغدې تر تصفیې د پیک سره (صندوق ته نه راځي).")
  val payOnline: String get() = t("پول: پرداخت‌شده آنلاین.", "Money: paid online.", "پیسې: آنلاین ورکړل شوې.")
  val payCounter: String get() = t("پول: نقدِ پیشخوان، همین حالا به دخل؛ کالا به مشتری داده می‌شود.", "Money: cash at the counter, into the drawer now; the goods go to the customer.", "پیسې: د کاونټر نغدې، همدا اوس صندوق ته؛ توکي پېرودونکي ته ورکول کېږي.")
  val packFirst: String get() = t("نخست «بسته شد» زده می‌شود؛ پس از بسته‌بندی هیچ قلمی عوض نمی‌شود.", "It marks the order packed first; no item changes after packing.", "لومړی «وتړل شو» کېږي؛ له تړلو وروسته هېڅ توکی نه بدلېږي.")
  val confirmInvoice: String get() = t("ثبتِ فاکتور", "Record invoice", "بل ثبتول")
  val notNow: String get() = t("نه، بعداً", "Not now", "نه، وروسته")
  fun invoiced(label: String, at: String): String = t("فاکتور شد: ${asIs(label)} · ${asIs(at)}", "Invoiced: $label · $at", "بل شو: $label · $at")
  val invoicedElsewhere: String get() = t(
    "فاکتور را دستگاهِ دیگری زد؛ پس از همگام‌سازی این‌جا هم پیداست.",
    "Another device made the invoice; it appears here after sync.",
    "بل بلې وسیلې جوړ کړ؛ له همغږۍ وروسته دلته هم ښکاري.",
  )
  fun invoiceFailed(m: String): String = t("فاکتور ثبت نشد — ${asIs(m)}", "Invoice not recorded — $m", "بل ثبت نه شو — $m")
  val ledgerNotReady: String get() = t("دفترِ دکان هنوز باز نشده؛ کمی بعد دوباره بزنید.", "The shop ledger isn't open yet; try again shortly.", "د دوکان دفتر لا نه دی پرانیستل شوی؛ لږ وروسته بیا یې ووهئ.")
  val notWritten: String get() = t("فاکتور روی این دستگاه نوشته نشد (قفلِ برنامه؟)؛ دوباره بزنید.", "The invoice wasn't written on this device (app lock?); tap again.", "بل په دې وسیله ونه لیکل شو (د برنامې قفل؟)؛ بیا یې ووهئ.")
  fun invoiceDone(orderNo: Long): String = t("فاکتورِ سفارش #${asIs(orderNo)} ثبت شد", "Invoice for order #$orderNo recorded", "د #$orderNo فرمایش بل ثبت شو")
}
