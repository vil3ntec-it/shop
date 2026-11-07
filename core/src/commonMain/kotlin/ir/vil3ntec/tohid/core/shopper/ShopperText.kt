package ir.vil3ntec.tohid.core.shopper

import ir.vil3ntec.tohid.core.text.DariText

/** زبانِ برنامهٔ خرید — همان چهار زبانِ برنامهٔ دکان؛ دری = فارسی + لایهٔ واژهٔ `DariText`. */
enum class ShopperLang(val code: String, val rtl: Boolean, val native: String) {
  FA("fa", true, "فارسی"),
  PRS("prs", true, "دری"),
  EN("en", false, "English"),
  PS("ps", true, "پښتو");

  companion object {
    /** ناشناخته یا خالی ⇒ فارسی */
    fun of(code: String?): ShopperLang = entries.firstOrNull { it.code == code } ?: FA
  }
}

/**
 *  نوشته‌های برنامهٔ خرید (fa · en · ps، و دری از روی فارسی). واژه‌ها همان واژه‌نامهٔ ویترینِ وب‌اند
 *  (واژه‌نامه‌های `server/public/kit/i18n`) تا مشتری در وب و برنامه یک زبان ببیند.
 *
 *  ⛔ هر مقدارِ درونِ متنِ فارسی (نامِ کالا، دکان، مبلغ…) از [asIs] می‌گذرد تا لایهٔ دری آن را عوض نکند
 *  (آزمون: `ShopperTextTest`). متن را در `val` ِ سطحِ بالا نگه ندارید: هر بار خواندن، زبانِ همان لحظه است.
 */
object ShopperText {
  var lang: ShopperLang = ShopperLang.FA

  /** فقط آزمون: هر نوشته‌ای که خوانده شود */
  var audit: ((fa: String, en: String, ps: String) -> Unit)? = null

  fun t(fa: String, en: String, ps: String): String {
    audit?.invoke(fa, en, ps)
    return when (lang) {
      ShopperLang.FA -> DariText.strip(fa)
      ShopperLang.PRS -> DariText.of(fa)
      ShopperLang.EN -> en
      ShopperLang.PS -> ps
    }
  }

  /** جای خالیِ متنِ فارسی: در دری از لایهٔ واژه نمی‌گذرد */
  fun asIs(value: Any?): String = if (lang == ShopperLang.PRS) DariText.protect(value.toString()) else value.toString()

  // ── عدد، پول و مقدار (رقمِ فارسی برای فارسی، دری و پشتو؛ لاتین برای انگلیسی) ──

  private fun local(ascii: String): String =
    if (lang == ShopperLang.EN) ascii
    else buildString {
      for (c in ascii) append(
        when (c) {
          in '0'..'9' -> '۰' + (c - '0')
          ',' -> '٬'
          '.' -> '٫'
          '-' -> '−'
          else -> c
        },
      )
    }

  private fun grouped(n: Long): String {
    val s = (if (n < 0) -n else n).toString()
    val g = s.reversed().chunked(3).joinToString(",").reversed()
    return if (n < 0) "-$g" else g
  }

  /** عددِ صحیح با جداکنندهٔ هزارگان */
  fun num(n: Long): String = local(grouped(n))

  /** `n` با بخشِ اعشاری از واحدِ کوچک ([scale] = ۱۰۰ برای پول، ۱۰۰۰ برای مقدار)، بی صفرِ پایانی */
  private fun scaled(v: Long, scale: Long): String {
    val neg = v < 0
    val a = if (neg) -v else v
    val frac = (a % scale).toString().padStart(scale.toString().length - 1, '0').trimEnd('0')
    val body = grouped(a / scale) + if (frac.isEmpty()) "" else ".$frac"
    return local(if (neg) "-$body" else body)
  }

  fun money(minor: Long): String {
    val n = scaled(minor, 100)
    return t("${asIs(n)} افغانی", "$n AFN", "$n افغانۍ")
  }

  fun qty(milli: Long): String = scaled(milli, 1000)

  /** رقم‌های متنِ ASCII به رقمِ زبان (تاریخِ `YYYY-MM-DD` ِ سرور، ساعت)؛ بقیهٔ نویسه‌ها همان */
  fun digits(ascii: String): String =
    if (lang == ShopperLang.EN) ascii else buildString { for (c in ascii) append(if (c in '0'..'9') '۰' + (c - '0') else c) }

  /** دقیقه از نیمه‌شب ⇒ «۰۹:۳۰» */
  fun clock(minutes: Int): String =
    digits((minutes / 60).toString().padStart(2, '0') + ":" + (minutes % 60).toString().padStart(2, '0'))

  // ── همگانی ──
  val appName get() = t("خرید", "Shopping", "پېرود")
  val back get() = t("برگشت", "Back", "شاته")
  val retry get() = t("دوباره بزنید", "Try again", "بیا هڅه وکړئ")
  val loading get() = t("در حالِ بار شدن…", "Loading…", "پورته کېږي…")
  val refresh get() = t("تازه کردن", "Refresh", "تازه کول")
  val settings get() = t("تنظیمات", "Settings", "امستنې")
  val language get() = t("زبان", "Language", "ژبه")
  val theme get() = t("ظاهر", "Appearance", "بڼه")
  val themeSystem get() = t("مثلِ گوشی", "Same as phone", "د موبایل په څېر")
  val themeLight get() = t("روشن", "Light", "روښانه")
  val themeDark get() = t("تیره", "Dark", "تیاره")
  val free get() = t("رایگان", "Free", "وړیا")
  val save get() = t("ذخیره", "Save", "خوندي کول")
  val server get() = t("نشانیِ سرور (برای آزمون)", "Server address (for testing)", "د سرور پته (د ازموینې لپاره)")
  val no get() = t("نه", "No", "نه")

  // ── خانه ──
  val homeTitle get() = t("خریدِ آنلاین", "Shop online", "آنلاین پېرود")
  val homeSub get() = t(
    "کالا را ببینید، سفارش بدهید و تا درِ خانه دنبالش کنید.",
    "Browse products, place an order and follow it to your door.",
    "توکي وګورئ، فرمایش ورکړئ او تر خپلې دروازې پورې یې تعقیب کړئ.",
  )
  val shopAddress get() = t("نشانیِ دکان", "Shop address", "د دوکان پته")
  val shopAddressHint get() = t("نشانی یا پیوندِ ویترین، مثلاً golestan", "Shop address or link, e.g. golestan", "د دوکان پته یا لینک، لکه golestan")
  val open get() = t("باز کن", "Open", "پرانیزئ")
  val badAddress get() = t("این نشانیِ دکان درست نیست.", "That shop address isn't valid.", "د دوکان دا پته سمه نه ده.")
  val searchShops get() = t("جست‌وجوی دکان…", "Search shops…", "د دوکان لټون…")
  val marketShops get() = t("دکان‌های بازارچه", "Marketplace shops", "د بازار دوکانونه")
  val marketOff get() = t(
    "بازارچه هنوز باز نیست؛ نشانیِ دکان را بنویسید.",
    "The marketplace isn't open yet — type a shop's address.",
    "بازار لا نه دی پرانیستل شوی؛ د دوکان پته ولیکئ.",
  )
  val noShops get() = t("دکانی پیدا نشد", "No shops found", "هېڅ دوکان ونه موندل شو")
  val recentShops get() = t("دکان‌های اخیر", "Recent shops", "وروستي دوکانونه")
  val myCarts get() = t("سبدهای این دستگاه", "Carts on this device", "پر دې وسیله ټوکرۍ")
  val myOrders get() = t("سفارش‌های من", "My orders", "زما فرمایشونه")
  fun cartOf(title: String, n: Int, sum: Long) = t(
    "${asIs(title)} — ${asIs(num(n.toLong()))} قلم، ${asIs(money(sum))}",
    "$title — $n ${if (n == 1) "item" else "items"}, ${money(sum)}",
    "$title — ${num(n.toLong())} توکي، ${money(sum)}",
  )

  // ── دکان ──
  val searchProducts get() = t("جست‌وجوی کالا در همین دکان…", "Search this shop…", "په همدې دوکان کې لټون…")
  val allCategories get() = t("همه", "All", "ټول")
  fun nothingFound(q: String) = t("برای «${asIs(q)}» چیزی پیدا نشد", "Nothing found for “$q”", "د «$q» لپاره هېڅ ونه موندل شو")
  val noProducts get() = t("هنوز کالایی در این بخش نیست", "No products here yet", "لا په دې برخه کې توکي نشته")
  val moreProducts get() = t("کالاهای بیشتر", "More products", "نور توکي")
  fun presence(badge: String?): String = when (badge) {
    "open_online" -> t("باز است", "Open", "پرانیستی دی")
    "open_unconfirmed" -> t("باز، اتصال تأیید نشده", "Open, connection not confirmed", "پرانیستی، اړیکه نه ده تایید شوې")
    "declared_until" -> t("آنلاین", "Online", "آنلاین")
    "closed" -> t("بسته است", "Closed", "تړلی دی")
    "offline" -> t("موقتاً آفلاین", "Temporarily offline", "لنډمهاله آفلاین")
    else -> ""
  }
  fun shipFee(fee: Long) = if (fee <= 0) t("ارسالِ رایگان", "Free delivery", "وړیا رسول")
    else t("ارسال ${asIs(money(fee))}", "Delivery ${money(fee)}", "رسول ${money(fee)}")
  fun shipFrom(fee: Long) = if (fee <= 0) t("ارسالِ رایگان", "Free delivery", "وړیا رسول")
    else t("ارسال از ${asIs(money(fee))}", "Delivery from ${money(fee)}", "لېږد له ${money(fee)}")
  fun minOrder(m: Long) = t("حداقلِ سفارش ${asIs(money(m))}", "Minimum order ${money(m)}", "لږ تر لږه فرمایش ${money(m)}")
  val pickupOnly get() = t("دریافت از دکان", "Pick up at the shop", "له دوکانه اخیستل")
  val notAccepting get() = t(
    "این فروشگاه فعلاً سفارشِ آنلاین نمی‌پذیرد",
    "This shop isn't taking online orders right now",
    "دا دوکان اوس مهال آنلاین فرمایش نه مني",
  )
  fun stock(status: String?, prepMinutes: Int?): String = when (status) {
    "in_stock" -> t("موجود", "In stock", "شته")
    "in_warehouse" -> if (prepMinutes != null && prepMinutes > 0) {
      t("در انبار، آماده‌سازی ~${asIs(num(prepMinutes.toLong()))} دقیقه", "In the storeroom, ready in ~$prepMinutes min", "په ګدام کې، ~${num(prepMinutes.toLong())} دقیقې چمتو کېدل")
    } else t("در انبار؛ آماده‌سازی لازم", "In the storeroom — needs preparing", "په ګدام کې؛ چمتو کولو ته اړتیا لري")
    "out_of_stock" -> t("ناموجود", "Out of stock", "نشته")
    "unavailable" -> t("قابلِ سفارش نیست", "Not available to order", "د فرمایش وړ نه دی")
    else -> t("موجودی در حال بررسی است", "Stock is being checked", "موجودي لا کتل کېږي")
  }
  val add get() = t("افزودن", "Add", "ور واچوئ")
  fun addOne(name: String) = t("افزودنِ ${asIs(name)} به سبد", "Add $name to cart", "$name ټوکرۍ ته ور واچوئ")
  fun less(name: String) = t("یکی کمتر ${asIs(name)}", "One less $name", "یو لږ $name")
  fun more(name: String) = t("یکی بیشتر ${asIs(name)}", "One more $name", "یو زیات $name")
  fun maxQty(q: String) = t("بیش از ${asIs(q)} در یک سفارش نمی‌شود", "No more than $q per order", "په یوه فرمایش کې له $q څخه زیات نه کېږي")
  fun cartBar(n: Int, sum: Long) = t(
    "سبد: ${asIs(num(n.toLong()))} قلم — ${asIs(money(sum))}",
    "Cart: $n ${if (n == 1) "item" else "items"} — ${money(sum)}",
    "ټوکرۍ: ${num(n.toLong())} توکي — ${money(sum)}",
  )
  fun perUnit(price: Long, unit: String) =
    if (unit.isBlank()) money(price) else t("${asIs(money(price))} / ${asIs(unit)}", "${money(price)} / $unit", "${money(price)} / $unit")
  val callShop get() = t("تماس با دکان", "Call the shop", "دوکان ته زنګ")

  // ── کالا ──
  val specs get() = t("مشخصات", "Details", "ځانګړنې")
  val similar get() = t("کالاهای مشابه", "Similar products", "ورته توکي")
  val together get() = t("با این کالا خریده‌اند", "Often bought together", "له دې سره اخیستل کېږي")

  // ── سبد ──
  val cart get() = t("سبد", "Cart", "ټوکرۍ")
  val emptyCart get() = t("سبد خالی است.", "Your cart is empty.", "ټوکرۍ تشه ده.")
  val remove get() = t("برداشتن", "Remove", "لرې کړئ")
  val lineGone get() = t("دیگر در ویترین نیست", "No longer in the shop", "نور په دوکان کې نشته")
  val cartBad get() = t(
    "کالاهای خط‌خورده دیگر در ویترین نیستند؛ اول آن‌ها را بردارید.",
    "Crossed-out items are no longer in the shop — remove them first.",
    "کرښه وهل شوي توکي نور په دوکان کې نشته؛ لومړی یې لرې کړئ.",
  )
  fun repricedCount(n: Int) = t(
    "قیمتِ ${asIs(num(n.toLong()))} کالای سبد با قیمتِ امروز تازه شد.",
    "Updated $n cart prices to today's price.",
    "د ټوکرۍ د ${num(n.toLong())} توکو بیه د نن ورځې پر بیه تازه شوه.",
  )
  fun goneCount(n: Int) = t(
    "${asIs(num(n.toLong()))} کالای سبد دیگر در ویترین نیست.",
    "$n cart items are no longer in the shop.",
    "د ټوکرۍ ${num(n.toLong())} توکي نور په دوکان کې نشته.",
  )
  val itemsTotal get() = t("جمعِ کالاها", "Items total", "د توکو ټولټال")
  val deliveryFee get() = t("هزینهٔ ارسال", "Delivery fee", "د رسولو لګښت")
  val total get() = t("جمع", "Total", "ټول")
  val zoneAfter get() = t(
    "هزینهٔ ارسال پس از گزینشِ ناحیه",
    "Delivery fee after you choose your area",
    "د لېږد لګښت د سیمې له ټاکلو وروسته",
  )
  fun zoneSmall(fee: Long, m: Long) = t(
    "زیرِ حداقلِ ${asIs(money(m))}: هزینهٔ سبدِ کوچک ${asIs(money(fee))}",
    "Below the ${money(m)} minimum: small-basket fee ${money(fee)}",
    "د ${money(m)} له لږ تر لږه کم: د کوچنۍ ټوکرۍ لګښت ${money(fee)}",
  )
  fun freeLeft(left: Long) = t("${asIs(money(left))} تا ارسالِ رایگان", "${money(left)} more for free delivery", "${money(left)} پاتې تر وړیا لېږد")
  fun minLeft(left: Long, m: Long) = t(
    "${asIs(money(left))} تا حداقلِ سفارش (${asIs(money(m))})",
    "${money(left)} to the minimum order (${money(m)})",
    "${money(left)} پاتې تر لږ تر لږه فرمایش (${money(m)})",
  )
  val toCheckout get() = t("ادامه و ثبتِ سفارش", "Continue to checkout", "ثبت ته دوام")

  // ── ثبت ──
  val checkout get() = t("ثبتِ سفارش", "Checkout", "فرمایش ثبتول")
  val shipToMe get() = t("ارسال به نشانیِ من", "Deliver to my address", "زما پتې ته یې راولېږئ")
  val pickup get() = t("دریافت از دکان", "Pick up at the shop", "له دوکانه اخیستل")
  val you get() = t("شما", "You", "تاسو")
  val name get() = t("نام", "Name", "نوم")
  val phone get() = t("شمارهٔ موبایل", "Mobile number", "د موبایل شمېره")
  val verifyText get() = t(
    "برای ثبت، شماره با یک کدِ یک‌بارمصرف تأیید می‌شود. دیدن و سبد حساب نمی‌خواهد.",
    "To place an order, we verify your number with a one-time code. Browsing and the cart need no account.",
    "د ثبت لپاره شمېره په یو ځلي کوډ تاییدېږي. لیدلو او ټوکرۍ ته حساب نه دی پکار.",
  )
  val sendCode get() = t("فرستادنِ کدِ تأیید", "Send code", "د تایید کوډ ولېږئ")
  val code get() = t("کدِ تأیید", "Verification code", "د تایید کوډ")
  val check get() = t("تأیید", "Verify", "تایید")
  val verified get() = t("شماره تأیید شده است.", "Number verified.", "شمېره تایید شوه.")
  val codeSent get() = t("کد فرستاده شد.", "Code sent.", "کوډ ولېږل شو.")
  val otherNumber get() = t("شمارهٔ دیگر", "Use another number", "بله شمېره")
  val verifyOff get() = t(
    "تأییدِ شماره هنوز روی این سرور راه‌اندازی نشده است؛ فعلاً برای سفارش با خودِ دکان تماس بگیرید.",
    "Number verification isn't set up on this server yet — for now, contact the shop to order.",
    "د شمېرې تایید لا پر دې سرور نه دی جوړ شوی؛ اوس د فرمایش لپاره له دوکان سره اړیکه ونیسئ.",
  )
  val address get() = t("نشانی", "Address", "پته")
  val addressHint get() = t("ناحیه، سرک، کوچه، شمارهٔ خانه", "District, road, street, house number", "ناحیه، سړک، کوڅه، د کور شمېره")
  val landmark get() = t("نشانهٔ نزدیک", "Nearby landmark", "نږدې نښه")
  val landmarkHint get() = t("مثلاً «پشتِ مسجد»", "e.g. “behind the mosque”", "لکه «د جومات تر شا»")
  val area get() = t("ناحیه (اختیاری)", "Area (optional)", "سیمه (اختیاري)")
  val gate get() = t("رنگِ دروازه (اختیاری)", "Gate colour (optional)", "د دروازې رنګ (اختیاري)")
  val callBefore get() = t("پیش از رسیدن زنگ بزنید", "Call me before arriving", "د رسېدو مخکې زنګ ووهئ")
  val leaveWith get() = t("تحویل به (اختیاری)", "Hand over to (optional)", "چا ته یې وسپارئ (اختیاري)")
  val note get() = t("یادداشت برای دکان (اختیاری)", "Note for the shop (optional)", "دوکان ته یادښت (اختیاري)")
  val zone get() = t("ناحیهٔ ارسال", "Delivery area", "د لېږد سیمه")
  fun zoneOption(name: String, fee: Long) = t(
    "${asIs(name)} — ارسال ${asIs(if (fee > 0) money(fee) else free)}",
    "$name — delivery ${if (fee > 0) money(fee) else free}",
    "$name — لېږد ${if (fee > 0) money(fee) else free}",
  )
  val slot get() = t("زمانِ تحویل", "Delivery time", "د سپارلو وخت")
  val asap get() = t("هرچه زودتر", "As soon as possible", "ژر تر ژره")
  val slotFull get() = t("پر شد", "full", "ډک شو")
  val slotFew get() = t("چند جای خالی", "few left", "لږ ځایونه پاتې")
  val slotNone get() = t(
    "برای روزهای پیشِ رو بازهٔ خالی نیست؛ بعداً دوباره ببینید یا روشِ دیگر را برگزینید.",
    "No free slot in the coming days; check back later or choose the other option.",
    "په راتلونکو ورځو کې تش وخت نشته؛ وروسته بیا وګورئ یا بله لاره وټاکئ.",
  )
  fun slot(date: String, startMin: Int, endMin: Int) = t(
    "${asIs(digits(date))}، ${asIs(clock(startMin))} تا ${asIs(clock(endMin))}",
    "$date, ${clock(startMin)}–${clock(endMin)}",
    "${digits(date)}، ${clock(startMin)} تر ${clock(endMin)}",
  )
  val slotFee get() = t("هزینهٔ زمانِ تحویل", "Time-slot fee", "د وخت لګښت")
  val pay get() = t("پرداخت", "Payment", "تادیه")
  val payCash get() = t("نقد هنگامِ تحویل", "Cash on delivery", "د سپارلو پر وخت نغدې")
  val payCashPickup get() = t("نقد هنگامِ دریافت از دکان", "Cash at pickup", "له دوکانه د اخیستو پر مهال نغدې")
  fun submit(sum: Long) = t("ثبتِ سفارش — ${asIs(money(sum))}", "Place order — ${money(sum)}", "فرمایش ثبت کړئ — ${money(sum)}")
  fun accept(sum: Long) = t("پذیرفتنِ قیمتِ تازه و ثبت — ${asIs(money(sum))}", "Accept new price and place order — ${money(sum)}", "نوې بیه ومنئ او ثبت یې کړئ — ${money(sum)}")
  val sending get() = t("در حالِ ثبت…", "Placing your order…", "ثبتېږي…")
  fun repriced(list: String, sum: Long) = t(
    "قیمت عوض شده است. ${asIs(list)} مبلغِ تازه: ${asIs(money(sum))}. اگر می‌پذیرید، دوباره بزنید.",
    "Prices have changed. $list New total: ${money(sum)}. If you accept, tap again.",
    "بیه بدله شوې ده. $list نوې بیه: ${money(sum)}. که یې منئ، بیا ټک وکړئ.",
  )
  fun priceMove(name: String, from: Long, to: Long) = t(
    "${asIs(name)}: ${asIs(money(from))} ← ${asIs(money(to))}",
    "$name: ${money(from)} → ${money(to)}",
    "$name: ${money(from)} ← ${money(to)}",
  )
  fun problem(p: CheckoutProblem): String = when (p) {
    CheckoutProblem.CLOSED -> notAccepting
    CheckoutProblem.EMPTY -> emptyCart
    CheckoutProblem.UNAVAILABLE_LINES -> cartBad
    CheckoutProblem.NO_FULFILLMENT -> t("این روشِ تحویل در این دکان نیست.", "This shop doesn't offer that delivery option.", "دا دوکان دا ډول سپارل نه لري.")
    CheckoutProblem.NAME -> t("نام را بنویسید.", "Please enter your name.", "خپل نوم ولیکئ.")
    CheckoutProblem.PHONE -> t("شمارهٔ موبایل درست نیست.", "That mobile number isn't valid.", "د موبایل شمېره سمه نه ده.")
    CheckoutProblem.VERIFY -> t("برای ثبت، اول همین شماره را تأیید کنید.", "Please verify this number before ordering.", "د ثبت لپاره لومړی همدا شمېره تایید کړئ.")
    CheckoutProblem.ADDRESS -> t("نشانی یا نشانهٔ نزدیک را بنویسید.", "Please give an address or a nearby landmark.", "پته یا نږدې نښه ورکړئ.")
    CheckoutProblem.ZONE -> t("ناحیهٔ ارسال را برگزینید.", "Choose your delivery area.", "د لېږد سیمه وټاکئ.")
    CheckoutProblem.SLOT -> t("یک بازهٔ زمانی را برگزینید.", "Choose a time slot.", "یو وخت وټاکئ.")
    CheckoutProblem.BELOW_MIN -> t("برای ادامه، سبد به حداقلِ سفارش برسد.", "Reach the minimum order to continue.", "د دوام لپاره ټوکرۍ باید لږ تر لږه فرمایش ته ورسېږي.")
  }

  // ── پس از ثبت و پیگیری ──
  val placedTitle get() = t("سفارش ثبت شد", "Order placed", "فرمایش ثبت شو")
  val placedText get() = t(
    "سفارش ثبت شد. دکان آن را بررسی می‌کند؛ صفحهٔ پیگیری وضعیت را نشان می‌دهد.",
    "Order placed. The shop is reviewing it — the tracking page shows its status.",
    "فرمایش ثبت شو. دوکان یې ګوري؛ د تعقیب پاڼه یې حالت ښيي.",
  )
  fun orderNo(n: Long) = t("سفارشِ شمارهٔ ${asIs(num(n))}", "Order #$n", "د فرمایش شمېره ${num(n)}")
  val track get() = t("پیگیریِ سفارش", "Track order", "د فرمایش تعقیب")
  val backToShop get() = t("برگشت به دکان", "Back to the shop", "دوکان ته بېرته")
  val discount get() = t("تخفیف", "Discount", "تخفیف")
  val finalTotal get() = t("مبلغِ نهایی", "Total", "وروستۍ بیه")
  val paid get() = t("پرداخت‌شده", "Paid", "ورکړل شوي")
  val due get() = t("مانده (نقد هنگامِ تحویل)", "Due (cash on delivery)", "پاتې (د سپارلو پر وخت نغدې)")
  val cancelOrder get() = t("لغوِ سفارش", "Cancel order", "فرمایش لغوه کړئ")
  val cancelQuestion get() = t(
    "این سفارش لغو شود؟ تا دکان قبول نکرده، لغو رایگان است.",
    "Cancel this order? It's free to cancel until the shop accepts it.",
    "دا فرمایش لغوه شي؟ تر هغو چې دوکان نه وي منلی، لغوه کول وړیا دي.",
  )
  val cancelYes get() = t("لغو کن", "Cancel it", "لغوه یې کړه")
  val cancelled get() = t("سفارش لغو شد.", "Order cancelled.", "فرمایش لغوه شو.")
  fun lineQty(q: Long, unit: String, price: Long) = t(
    "${asIs(qty(q))} ${asIs(unit)} × ${asIs(money(price))}",
    "${qty(q)} $unit × ${money(price)}",
    "${qty(q)} $unit × ${money(price)}",
  )
  fun step(id: String): String = when (id) {
    "placed" -> t("ثبت شد", "Placed", "ثبت شو")
    "accepted" -> t("دکان قبول کرد", "Accepted by the shop", "دوکان ومانه")
    "prep" -> t("آماده‌سازی", "Preparing", "چمتو کول")
    "ready" -> t("آمادهٔ دریافت", "Ready for pickup", "اخیستو ته چمتو")
    "way" -> t("در راه", "On the way", "په لار کې")
    "picked" -> t("تحویل گرفتید", "Picked up", "تاسو واخیست")
    "delivered" -> t("تحویل شد", "Delivered", "ورسېد")
    else -> status(id)
  }
  fun status(s: String): String = when (s) {
    "new" -> t("ثبت شد — منتظرِ تأییدِ دکان", "Placed — waiting for the shop", "ثبت شو — د دوکان تایید ته په تمه")
    "accepted" -> t("دکان قبول کرد", "Accepted by the shop", "دوکان ومانه")
    "rejected" -> t("دکان نپذیرفت", "Declined by the shop", "دوکان ونه مانه")
    "auto_rejected" -> t("بی پاسخ ماند", "No response from the shop", "ځواب ورنه کړل شو")
    "cancelled_by_customer" -> t("شما لغو کردید", "You cancelled", "تاسو لغوه کړ")
    "picking" -> t("در حالِ جمع‌آوری", "Being picked", "راټولېږي")
    "packed" -> t("بسته‌بندی شد", "Packed", "بسته شو")
    "ready_for_pickup" -> t("آمادهٔ دریافت از دکان", "Ready for pickup", "له دوکانه اخیستو ته چمتو")
    "out_for_delivery" -> t("در راه", "On the way", "په لار کې")
    "picked_up" -> t("تحویل گرفته شد", "Picked up", "واخیستل شو")
    "delivered" -> t("تحویل شد", "Delivered", "ورسېد")
    "cancelled" -> t("لغو شد", "Cancelled", "لغوه شو")
    "delivery_failed" -> t("تحویل نشد", "Delivery failed", "ونه رسېد")
    "retry_scheduled" -> t("تحویلِ دوباره", "Redelivery scheduled", "بیا رسول")
    "returned_to_store", "restocked" -> t("به دکان برگشت", "Returned to the shop", "دوکان ته بېرته راغی")
    "return_requested" -> t("درخواستِ مرجوعی", "Return requested", "د بېرته ورکولو غوښتنه")
    "refunded_partial" -> t("بخشی بازپرداخت شد", "Partly refunded", "یوه برخه پیسې بېرته ورکړل شوې")
    "refunded_full" -> t("بازپرداخت شد", "Refunded", "پیسې بېرته ورکړل شوې")
    else -> t("وضعیتِ سفارش: ${asIs(s)}", "Order status: $s", "د فرمایش حالت: $s")
  }

  /** کارتِ «پرداخت» ِ پیگیری (`paymentStep` ِ `lib/order-flow.js`) — همان `paystep.*.json` ِ وب */
  fun payState(state: String, cash: String, amount: Long): String {
    val a = money(amount)
    return when (state) {
      "awaiting_accept" -> t(
        "منتظرِ پذیرشِ فروشنده. پس از پذیرش، روشِ پرداختِ ${asIs(a)} را همین‌جا انتخاب می‌کنید.",
        "Waiting for the seller to accept. After that you choose here how to pay $a.",
        "د پلورونکي د منلو په تمه. له منلو وروسته د $a د تادیې لاره همدلته ټاکئ.",
      )
      "awaiting_choice" -> t(
        "فروشنده سفارش را پذیرفت. لطفاً روشِ پرداختِ ${asIs(a)} را انتخاب کنید.",
        "The seller accepted your order. Please choose how to pay $a.",
        "پلورونکي فرمایش ومانه. مهرباني وکړئ د $a د تادیې لاره وټاکئ.",
      )
      "online_pending" -> t(
        "پرداختِ آنلاین در جریان است؛ وضعیت همین‌جا به‌روز می‌شود.",
        "Online payment in progress; this page updates by itself.",
        "آنلاین تادیه روانه ده؛ حالت همدلته تازه کېږي.",
      )
      "paid_online" -> t("پرداخت شد (حساب‌پی) — ${asIs(a)}", "Paid (HesabPay) — $a", "تادیه شوه (حساب‌پې) — $a")
      "cash_collected", "cash_settled" -> t("نقد گرفته شد — ${asIs(a)}", "Cash received — $a", "نغدې واخیستل شوې — $a")
      "refunded" -> t("پولِ این سفارش برگشت داده شد.", "The money for this order was refunded.", "د دې فرمایش پیسې بېرته ورکړل شوې.")
      "closed" -> t("این سفارش بسته شد؛ پرداختی لازم نیست.", "This order is closed; no payment is needed.", "دا فرمایش وتړل شو؛ تادیې ته اړتیا نشته.")
      else -> if (cash == "pickup") t("پرداخت: نقد هنگامِ برداشت از دکان — ${asIs(a)}", "Payment: cash at pickup — $a", "تادیه: له دوکانه د اخیستو پر مهال نغدې — $a")
        else t("پرداخت: نقد هنگامِ تحویل — ${asIs(a)}", "Payment: cash on delivery — $a", "تادیه: د سپارلو پر مهال نغدې — $a")
    }
  }
  fun payCashButton(cash: String) = if (cash == "pickup") t("نقد هنگامِ برداشت از دکان می‌پردازم", "I'll pay cash at pickup", "له دوکانه د اخیستو پر مهال نغدې ورکوم")
    else t("نقد هنگامِ تحویل می‌پردازم", "I'll pay cash on delivery", "د سپارلو پر مهال نغدې ورکوم")
  val payCashDone get() = t("روشِ پرداخت ثبت شد: نقد", "Payment method saved: cash", "د تادیې لاره ثبت شوه: نغدې")

  // ── خطاها (همان `err.*` ِ وب؛ کدِ ناشناخته ⇒ پیامِ فارسیِ سرور در فارسی/دری، وگرنه پیامِ همگانی) ──
  fun error(e: ShopperError): String = when {
    e.network -> t("اتصال برقرار نشد؛ اینترنت را ببینید و دوباره بزنید.", "No connection — check your internet and try again.", "اړیکه ونه شوه؛ انټرنېټ وګورئ او بیا هڅه وکړئ.")
    else -> known(e.code) ?: when {
      e.status == 429 -> known("rate_limited")!!
      e.message.isNotBlank() && lang == ShopperLang.FA -> e.message
      e.message.isNotBlank() && lang == ShopperLang.PRS -> DariText.of(e.message)
      else -> t("خطا؛ دوباره بزنید.", "Something went wrong — please try again.", "تېروتنه وشوه؛ بیا هڅه وکړئ.")
    }
  }

  private fun known(code: String): String? = when (code) {
    "rate_limited" -> t("درخواست‌ها زیاد شد؛ کمی بعد دوباره بزنید.", "Too many requests — please try again in a moment.", "غوښتنې ډېرې شوې؛ لږ وروسته بیا هڅه وکړئ.")
    "bad_phone" -> problem(CheckoutProblem.PHONE)
    "verification_unavailable" -> verifyOff
    "verification_required" -> t("برای ادامه، شمارهٔ خود را دوباره تأیید کنید.", "Please verify your number again to continue.", "د دوام لپاره خپله شمېره بیا تایید کړئ.")
    "phone_not_verified" -> problem(CheckoutProblem.VERIFY)
    "otp_required" -> t("کد را بنویسید.", "Please enter the code.", "کوډ ولیکئ.")
    "otp_wrong" -> t("کد درست نیست.", "That code isn't right.", "کوډ سم نه دی.")
    "otp_expired" -> t("مهلتِ این کد تمام شده است؛ کدِ تازه بخواهید.", "This code has expired — ask for a new one.", "د دې کوډ وخت تېر شوی؛ نوی کوډ وغواړئ.")
    "otp_locked" -> t("تلاش زیاد شد؛ کدِ تازه بخواهید.", "Too many tries — ask for a new code.", "هڅې ډېرې شوې؛ نوی کوډ وغواړئ.")
    "otp_used" -> t("این کد پیش‌تر به کار رفته است؛ کدِ تازه بخواهید.", "This code was already used — ask for a new one.", "دا کوډ مخکې کارول شوی؛ نوی کوډ وغواړئ.")
    "otp_not_found" -> t("برای این شماره کدی فرستاده نشده؛ اول «فرستادنِ کد» را بزنید.", "No code was sent to this number — tap “Send code” first.", "دې شمېرې ته کوډ نه دی لېږل شوی؛ لومړی «کوډ ولېږئ» ووهئ.")
    "otp_resend_wait" -> t("کمی صبر کنید و دوباره کد بخواهید.", "Please wait a moment before asking for another code.", "لږ صبر وکړئ او بیا کوډ وغواړئ.")
    "otp_daily_limit" -> t("امروز کد زیاد خواسته شده است؛ فردا دوباره بزنید.", "Too many codes today — please try again tomorrow.", "نن ډېر کوډونه غوښتل شوي؛ سبا بیا هڅه وکړئ.")
    "product_unavailable" -> t("برخی کالاها دیگر در ویترین نیستند؛ آن‌ها را از سبد بردارید.", "Some items are no longer in the shop — remove them from your cart.", "ځینې توکي نور په دوکان کې نشته؛ له ټوکرۍ یې لرې کړئ.")
    "qty_over_limit" -> t("این تعداد از حدِ هر سفارش بیشتر است.", "That quantity is over the per-order limit.", "دا شمېر په هر فرمایش کې له حد څخه زیات دی.")
    "below_min_order" -> t("سبد از حداقلِ سفارش کمتر است.", "Your cart is below the minimum order.", "ټوکرۍ له لږ تر لږه فرمایش څخه کمه ده.")
    "fulfillment_unavailable" -> problem(CheckoutProblem.NO_FULFILLMENT)
    "bad_address" -> problem(CheckoutProblem.ADDRESS)
    "bad_customer" -> t("نام و شماره را درست بنویسید.", "Please check your name and number.", "نوم او شمېره سمه ولیکئ.")
    "bad_lines" -> t("سبد درست نیست؛ کالاها را دوباره ببینید.", "Something is wrong with the cart — please review the items.", "ټوکرۍ سمه نه ده؛ توکي بیا وګورئ.")
    "store_not_found" -> t("این ویترین پیدا نشد.", "Shop not found.", "دا دوکان ونه موندل شو.")
    "market_not_found" -> marketOff
    "order_not_found" -> t("این سفارش پیدا نشد.", "Order not found.", "دا فرمایش ونه موندل شو.")
    "link_expired", "link_revoked" -> t("این لینکِ پیگیری دیگر کار نمی‌کند.", "This tracking link no longer works.", "د تعقیب دا لینک نور کار نه کوي.")
    "cannot_cancel" -> t("سفارش قبول شده است؛ برای لغو با دکان تماس بگیرید.", "The shop has accepted this order — call the shop to cancel.", "دوکان فرمایش منلی؛ د لغوه کولو لپاره دوکان ته زنګ ووهئ.")
    "out_of_zone" -> t("این نشانی بیرون از محدودهٔ ارسالِ دکان است؛ «دریافت از دکان» را برگزینید.", "This address is outside the shop's delivery area; choose pickup.", "دا پته د لېږد له سیمې بهر ده؛ «له دوکان څخه ترلاسه کول» وټاکئ.")
    "zone_required" -> problem(CheckoutProblem.ZONE)
    "bad_zone" -> t("این ناحیه دیگر در فهرستِ ارسال نیست؛ دوباره برگزینید.", "This area is no longer in the delivery list; choose again.", "دا سیمه نور د لېږد په لړ کې نشته؛ بیا یې وټاکئ.")
    "slot_required" -> problem(CheckoutProblem.SLOT)
    "slot_unavailable", "slot_full" -> t("این بازه دیگر در دسترس نیست؛ بازهٔ دیگری برگزینید.", "That time slot isn't available any more — choose another.", "دا وخت نور نشته؛ بل وخت وټاکئ.")
    "store_not_accepting_orders" -> notAccepting
    "store_paused" -> t("سفارش‌گیریِ این دکان موقتاً متوقف است؛ کمی بعد دوباره بزنید.", "This shop has paused orders for a while — try again a little later.", "دې دوکان لنډمهاله فرمایش اخیستل درولي؛ لږ وروسته بیا هڅه وکړئ.")
    "out_of_stock" -> t("موجودیِ برخی کالاها کم است؛ مقدار را کم کنید.", "Not enough stock for some items — lower the quantity.", "د ځینو توکو زېرمه کمه ده؛ شمېر یې کم کړئ.")
    "idempotency_key_reused" -> t("این سفارش پیش‌تر با محتوای دیگری فرستاده شده؛ دوباره بزنید.", "This order was already sent with different contents — please try again.", "دا فرمایش مخکې له بل منځپانګې سره لېږل شوی؛ بیا هڅه وکړئ.")
    ShopperError.PRICE_CHANGED -> t("قیمت عوض شده است.", "Prices have changed.", "بیه بدله شوې ده.")
    else -> null
  }
}
