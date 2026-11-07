package ir.vil3ntec.tohid.core.i18n

import ir.vil3ntec.tohid.core.delivery.DeliveryText
import ir.vil3ntec.tohid.core.delivery.DeskActions
import ir.vil3ntec.tohid.core.orders.OrderText

/**
 *  نوشته‌های گروهِ G8 سفارش، ویترین، گفت‌وگو، تحویل، پیک (online.md) — صاحبش همان گروه است.
 *  هر ویژگی سه‌زبانه: `val x: String get() = t("…", "…", "…")`؛ قاعده‌ها سرِ `I18n.kt`.
 *
 *  واژه‌های موتور (`OrderText`، `DeliveryText`) فارسیِ همان‌جا را نگه می‌دارند؛ این‌جا
 *  فقط انگلیسی و پشتوی کنارشان است (`orderStatus(id)`، …)، و کدِ ناشناخته همان فارسیِ
 *  موتور (یا خودِ کد) می‌ماند.
 *
 *  هر عضوِ عمومیِ این جدول یک ردیف است که `I18nCoverageTest` با آرگومانِ ساختگی صدا می‌زند: باید از `t`/`td`
 *  بگذرد، تهی نباشد و آرگومان را نشان دهد. پس نگاشتِ کدِ ناشناخته به «هیچ»، آرگومانِ نوع‌دار (`DeskActions.Act`،
 *  مختصات، شمارهٔ روز) و قالب‌های بی‌ترجمه (`#n`، LRI…PDI) در `OnlineFmt` اند و نوشته‌هایشان همین‌جا.
 */
@Suppress("unused")
object StrOnline {

  // ── پول، زمان، شمار ─────────────────────────────────────────────────────────
  /** مبلغِ قالب‌خورده (`OrderText.money`)؛ از واحدِ خرد با `OnlineFmt.afn(minor)` */
  fun afnAmount(amount: String): String = t("${asIs(amount)} افغانی", "AFN $amount", "$amount افغانۍ")
  fun items(n: Int): String = t("${asIs(n)} قلم", "$n ${pl(n, "item", "items")}", "$n توکي")
  fun orderTitle(n: Long): String = t("سفارش #${asIs(n)}", "Order #$n", "فرمایش #$n")
  //  «چند وقت پیش»؛ از دو زمان با `OnlineFmt.ago(ms, now)`
  val agoNow: String get() = t("همین حالا", "Just now", "همدا اوس")
  fun agoMinutes(d: Long): String = t("${asIs(d)} دقیقه پیش", "$d min ago", "$d دقیقې مخکې")
  fun agoHours(h: Long): String = t("${asIs(h)} ساعت پیش", "$h h ago", "$h ساعته مخکې")
  fun agoDays(n: Long): String = t("${asIs(n)} روز پیش", "$n ${pl(n, "day", "days")} ago", "$n ورځې مخکې")

  // ── واژه‌های موتورِ سفارش (فارسی از `OrderText`) ───────────────────────────
  fun orderStatus(id: String): String = coded(id, OrderText.status(id), STATUS_EN[id], STATUS_PS[id])
  fun orderAction(id: String): String = coded(id, OrderText.action(id), ACTION_EN[id], ACTION_PS[id])
  fun orderReason(id: String): String = coded(id, OrderText.reason(id), REASON_EN[id], REASON_PS[id])
  fun orderTab(id: String): String = coded(id, OrderText.tab(id), TAB_EN[id], TAB_PS[id])
  fun orderSource(id: String): String = coded(id, OrderText.source(id), SOURCE_EN[id], SOURCE_PS[id])
  fun cod(id: String): String = coded(id, OrderText.COD[id] ?: id, COD_EN[id], COD_PS[id])
  //  برچسبِ پرداخت؛ از وضعیت و روش با `OnlineFmt.pay(status, method)`
  val payPaid: String get() = t("پرداخت‌شده", "Paid", "ورکړل شوی")
  val payCod: String get() = t("نقد هنگامِ تحویل", "Cash on delivery", "د سپارلو پر وخت نغدې")
  val payUnpaid: String get() = t("پرداخت‌نشده", "Unpaid", "نه دی ورکړل شوی")

  // ── واژه‌های موتورِ تحویل (فارسی از `DeliveryText`) ────────────────────────
  fun missionStatus(id: String): String = coded(id, DeliveryText.status(id), MISSION_EN[id], MISSION_PS[id])
  fun deliveryMode(id: String): String = coded(id, DeliveryText.mode(id), MODE_EN[id], MODE_PS[id])
  fun deliveryReason(id: String): String = coded(id, DeliveryText.reason(id), DREASON_EN[id], DREASON_PS[id])
  /** با نامِ `DeskActions.Act`؛ نامِ ناشناخته خودش می‌ماند. صدا زدن از `OnlineFmt.deskAction(act)` */
  fun deskActionLabel(name: String): String =
    DeskActions.Act.entries.firstOrNull { it.name == name }?.let { t(DeskActions.label(it), DESK_EN[it], DESK_PS[it]) } ?: t(asIs(name), name, name)

  // ── صفِ سفارش‌ها ───────────────────────────────────────────────────────────
  val ordersTitle: String get() = t("سفارش‌ها", "Orders", "فرمایشونه")
  val ordersCaption: String get() = t("ویترین، تلفن، تلگرام و واتس‌اپ — در یک صف", "Storefront, phone, Telegram and WhatsApp — one queue", "ویترین، تلیفون، ټلګرام او واټس‌اپ — په یوه کتار کې")
  val manualOrder: String get() = t("سفارشِ دستی", "Manual order", "لاسي فرمایش")
  val liveNote: String get() = t(
    "اعلانِ فوری (وقتی برنامه بسته است) هنوز وصل نیست؛ تا برنامه باز است، صف هر چند ثانیه خودش تازه می‌شود.",
    "Instant notifications (while the app is closed) aren't connected yet; while the app is open the queue refreshes every few seconds.",
    "سمدستي خبرتیا (کله چې اپ تړلی وي) لا نه ده نښلول شوې؛ تر څو چې اپ پرانیستی وي، کتار هر څو ثانیې وروسته تازه کېږي.",
  )
  val noOrders: String get() = t("سفارشی نیست", "No orders", "فرمایش نشته")
  fun emptyTab(tab: String): String = orderTab(tab).let { t("در برگهٔ «${asIs(it)}» سفارشی نیست.", "No orders in “$it”.", "په «$it» کې فرمایش نشته.") }
  val listFailed: String get() = t("فهرست نیامد", "The list didn't load", "لړلیک رانغی")
  val moreOrders: String get() = t("سفارش‌های بیشتر", "More orders", "نور فرمایشونه")
  val delivery: String get() = t("ارسال", "Delivery", "لېږل")
  val pickup: String get() = t("حضوری", "Pickup", "حضوري")
  fun forTime(time: String): String = t("برای ${asIs(time)}", "For $time", "د $time لپاره")
  val test: String get() = t("آزمایشی", "Test", "ازمېښتي")
  val newOrderArrived: String get() = t("سفارشِ تازه رسید", "New order arrived", "نوی فرمایش راغی")
  fun movedTo(n: Long, status: String): String = t("سفارش #${asIs(n)}: ${asIs(status)}", "Order #$n: $status", "فرمایش #$n: $status")
  fun created(n: Long): String = t("سفارش #${asIs(n)} ثبت شد", "Order #$n placed", "فرمایش #$n ثبت شو")

  // ── جزئیاتِ سفارش ─────────────────────────────────────────────────────────
  val customer: String get() = t("مشتری", "Customer", "پېرودونکی")
  val call: String get() = t("تماس", "Call", "زنګ")
  val copyNumber: String get() = t("کپیِ شماره", "Copy number", "شمېره کاپي کول")
  val numberCopied: String get() = t("شماره کپی شد", "Number copied", "شمېره کاپي شوه")
  val fullNumberHelper: String get() = t("شمارهٔ کامل فقط برای صاحب و مدیر", "The full number is for the owner and manager only", "بشپړه شمېره یوازې د خاوند او مدیر لپاره ده")
  val noNumber: String get() = t("شماره ندارد", "No number", "شمېره نشته")
  val address: String get() = t("نشانی", "Address", "پته")
  val landmark: String get() = t("نشانه", "Landmark", "نښه")
  val pin: String get() = t("پین روی نقشه", "Map pin", "په نقشه کې پن")
  val openMap: String get() = t("باز کردن روی نقشه", "Open in maps", "په نقشه کې پرانیستل")
  val itemsTitle: String get() = t("اقلام", "Items", "توکي")
  val subtotal: String get() = t("جمعِ اقلام", "Items total", "د توکو ټولګه")
  val discount: String get() = t("تخفیف", "Discount", "تخفیف")
  val deliveryFee: String get() = t("هزینهٔ ارسال", "Delivery fee", "د لېږد لګښت")
  val total: String get() = t("جمعِ کل", "Total", "ټوله بیه")
  val note: String get() = t("یادداشت", "Note", "یادښت")
  val timeline: String get() = t("خطِ زمانی", "Timeline", "مهال‌کرښه")
  fun statusMove(from: String, to: String): String = t("${asIs(from)} ← ${asIs(to)}", "$from → $to", "$from ← $to")
  val evCreated: String get() = t("ثبت شد", "Placed", "ثبت شو")
  fun evCod(state: String): String = t("نقد: ${asIs(state)}", "Cash: $state", "نغدې: $state")
  val actorSystem: String get() = t("سیستم", "System", "سیسټم")
  val actorStaff: String get() = t("کارمند", "Staff", "کارکوونکی")
  fun reasonLine(r: String): String = t("دلیل: ${asIs(r)}", "Reason: $r", "لامل: $r")
  fun readyBy(time: String): String = t("آماده تا ${asIs(time)}", "Ready by $time", "تر $time پورې چمتو")
  val staleStatus: String get() = t(
    "وضعیتِ این سفارش همین حالا جای دیگری عوض شد؛ وضعیتِ تازه را ببینید.",
    "This order's status just changed somewhere else; check the new status.",
    "د دې فرمایش حالت همدا اوس بل ځای بدل شو؛ نوی حالت وګورئ.",
  )
  val reload: String get() = t("تازه کردن", "Reload", "تازه کول")
  val orderFailed: String get() = t("سفارش نیامد", "The order didn't load", "فرمایش رانغی")
  val moreActions: String get() = t("کارهای دیگر", "More actions", "نور کارونه")
  val selectOrder: String get() = t("یک سفارش را از فهرست باز کنید.", "Open an order from the list.", "له لړلیک څخه یو فرمایش پرانیزئ.")

  // ── برگهٔ کارِ سفارش ───────────────────────────────────────────────────────
  fun actionTitle(action: String, n: Long): String = t("${asIs(action)} — سفارش #${asIs(n)}", "$action — order #$n", "$action — فرمایش #$n")
  val reason: String get() = t("دلیل", "Reason", "لامل")
  val explain: String get() = t("توضیح", "Explanation", "تشریح")
  val explainHelper: String get() = t("برای «دلیلِ دیگر» لازم است", "Required for “Other reason”", "د «بل لامل» لپاره اړین دی")
  val readyIn: String get() = t("زمانِ آماده شدن", "Ready in", "د چمتو کېدو وخت")
  val noTime: String get() = t("بی زمان", "No time", "بې وخته")
  fun minutes(m: Int): String = t("${asIs(m)} دقیقه", "$m min", "$m دقیقې")
  fun refundWarn(amount: String): String = t("پول به مشتری برمی‌گردد (${asIs(amount)}). مطمئنید؟", "Money goes back to the customer ($amount). Are you sure?", "پیسې پېرودونکي ته بېرته ورکول کېږي ($amount). ډاډه یاست؟")
  fun movesTo(status: String): String = t("سفارش به «${asIs(status)}» می‌رود.", "The order moves to “$status”.", "فرمایش «$status» ته ځي.")
  // ── FIN-01: «ثبتِ بازپرداخت» با مبلغ (دفترِ بازپرداختِ روشن) ──
  val refundAmount: String get() = t("مبلغِ بازپرداخت (افغانی، نقد)", "Refund amount (AFN, cash)", "د بېرته ورکړې اندازه (افغانۍ، نغدې)")
  fun refundRemaining(amount: String): String = t("ماندهٔ قابلِ برگشت: ${asIs(amount)}", "Refundable left: $amount", "د بېرته ورکړې پاتې: $amount")
  val refundReason: String get() = t("دلیلِ بازپرداخت", "Refund reason", "د بېرته ورکړې لامل")
  val refundBadAmount: String get() = t("مبلغ را درست بنویسید (بیش از صفر، حداکثر دو رقمِ اعشار)", "Enter a valid amount (above zero, at most two decimals)", "اندازه سمه ولیکئ (له صفر پورته، تر دوو اعشاري رقمونو)")
  fun refundMustEqual(amount: String): String = t("بازپرداختِ کامل همان مانده است: ${asIs(amount)}", "A full refund is exactly what is left: $amount", "بشپړه بېرته ورکړه همغه پاتې ده: $amount")
  fun refundMustBeBelow(amount: String): String = t("بازپرداختِ بخشی باید کمتر از مانده (${asIs(amount)}) باشد", "A partial refund must be less than what is left ($amount)", "برخه‌یي بېرته ورکړه باید له پاتې ($amount) لږه وي")
  val refundNeedsReason: String get() = t("دلیلِ بازپرداخت لازم است", "A refund reason is required", "د بېرته ورکړې لامل اړین دی")
  fun refundRecorded(amount: String, n: Long): String = t("بازپرداختِ ${asIs(amount)} برای سفارش #${asIs(n)} ثبت شد", "Refund of $amount recorded for order #$n", "د $amount بېرته ورکړه د #$n فرمایش لپاره ثبت شوه")

  // ── سفارشِ دستی ────────────────────────────────────────────────────────────
  val fromWhere: String get() = t("از کجا", "Source", "له کومه")
  val fulfilment: String get() = t("تحویل", "Fulfilment", "سپارنه")
  val pickupFromShop: String get() = t("حضوری (از دکان)", "Pickup (from shop)", "حضوري (له دوکانه)")
  val customerName: String get() = t("نامِ مشتری", "Customer name", "د پېرودونکي نوم")
  val phone: String get() = t("شماره", "Phone", "شمېره")
  fun lineN(i: Int): String = t("قلمِ ${asIs(i)}", "Item $i", "توکی $i")
  val lineName: String get() = t("نامِ قلم", "Item name", "د توکي نوم")
  val lineRemoved: String get() = t("قلم برداشته شد", "Item removed", "توکی لرې شو")
  fun lineDetail(qty: String, price: String): String = t("${asIs(qty)} × ${asIs(price)} افغانی", "$qty × AFN $price", "$qty × $price افغانۍ")
  val qty: String get() = t("مقدار", "Qty", "مقدار")
  val unitPrice: String get() = t("فی", "Unit price", "د یوه بیه")
  val addLine: String get() = t("قلمِ دیگر", "Another item", "بل توکی")
  val removeLine: String get() = t("حذفِ قلم", "Remove item", "توکی لرې کول")
  val testOrder: String get() = t("سفارشِ آزمایشی", "Test order", "ازمېښتي فرمایش")
  val testHelper: String get() = t("برای آزمودنِ صف و چاپ", "To try the queue and printing", "د کتار او چاپ د ازمېښت لپاره")
  val placeOrder: String get() = t("ثبتِ سفارش", "Place order", "فرمایش ثبتول")
  val retryOnce: String get() = t(
    "اتصال قطع شد. دوباره «ثبتِ سفارش» را بزنید — سفارش دو بار ثبت نمی‌شود.",
    "Connection lost. Tap “Place order” again — it won't be placed twice.",
    "اړیکه پرې شوه. بیا «فرمایش ثبتول» ووهئ — فرمایش دوه ځله نه ثبتېږي.",
  )
  fun linesSummary(n: Int, total: String): String = t("${asIs(n)} قلم · ${asIs(total)}", "$n ${pl(n, "item", "items")} · $total", "$n توکي · $total")
  val errName: String get() = t("نامِ مشتری لازم است", "Customer name is required", "د پېرودونکي نوم اړین دی")
  val errPhoneDelivery: String get() = t("برای ارسال، شمارهٔ مشتری لازم است", "A phone number is required for delivery", "د لېږد لپاره د پېرودونکي شمېره اړینه ده")
  val errAddress: String get() = t("نشانی یا نشانهٔ تحویل لازم است", "An address or landmark is required", "پته یا نښه اړینه ده")
  val errLineName: String get() = t("نامِ قلم لازم است", "Item name is required", "د توکي نوم اړین دی")
  val errQty: String get() = t("مقدار درست نیست", "Invalid quantity", "مقدار سم نه دی")
  val errPrice: String get() = t("قیمت درست نیست", "Invalid price", "بیه سمه نه ده")
  val errNoLines: String get() = t("دست‌کم یک قلم بنویسید", "Add at least one item", "لږ تر لږه یو توکی ولیکئ")
  val errFee: String get() = t("هزینهٔ ارسال درست نیست", "Invalid delivery fee", "د لېږد لګښت سم نه دی")


  // ── ویترین ─────────────────────────────────────────────────────────────────
  val sfTitle: String get() = t("ویترین", "Storefront", "ویترین")
  val sfCaption: String get() = t("نشانی، ساعتِ کاری، کالاهای آنلاین و وضعیتی که مشتری می‌بیند", "Address, hours, online products and what customers see", "پته، کاري ساعتونه، آنلاین توکي او هغه حالت چې پېرودونکی یې ویني")
  val sfTabStatus: String get() = t("وضعیت", "Status", "حالت")
  val sfTabSettings: String get() = t("تنظیمات", "Settings", "امستنې")
  val sfTabProducts: String get() = t("کالاها", "Products", "توکي")
  val sfTabMarket: String get() = t("بازارچه", "Market", "بازار")
  val sfTabDelivery: String get() = t("ارسال و زمان", "Delivery & times", "لېږد او وخت")
  val sfOffTitle: String get() = t("ویترین برای این دکان روشن نیست", "The storefront isn't on for this shop", "ویترین د دې دوکان لپاره نه دی فعال")
  val sfOffBody: String get() = t("این بخش پس از روشن شدن از طرفِ سامانه دیده می‌شود.", "This appears once the platform turns it on.", "دا برخه هغه وخت ښکاري چې سیسټم یې فعال کړي.")
  val sfOwnerTitle: String get() = t("فقط صاحب دکان یا مدیر", "Owner or manager only", "یوازې د دوکان خاوند یا مدیر")
  val sfOwnerBody: String get() = t("تنظیمِ ویترین کارِ صاحب دکان یا مدیر است.", "Only the shop owner or a manager can set up the storefront.", "د ویترین تنظیم د دوکان د خاوند یا مدیر کار دی.")
  val sfNotEntitled: String get() = t("اشتراکِ این دکان ویترین را در بر ندارد؛ تغییرها ذخیره نمی‌شوند.", "This shop's plan doesn't include the storefront; changes won't be saved.", "د دې دوکان ګډون ویترین نه لري؛ بدلونونه نه خوندي کېږي.")
  val sfLoadFailed: String get() = t("ویترین باز نشد", "The storefront didn't open", "ویترین پرانیستل نشو")
  val checkConnection: String get() = t("اتصال را بررسی کنید.", "Check the connection.", "اړیکه وګورئ.")
  val sfCreateFirst: String get() = t("اول ویترین را بسازید", "Create the storefront first", "لومړی ویترین جوړ کړئ")
  val sfCreateBody: String get() = t("در «تنظیمات» نشانی و نام را بنویسید و ذخیره کنید.", "Write the address and name in Settings and save.", "په «امستنو» کې پته او نوم ولیکئ او خوندي یې کړئ.")
  val sfGoSettings: String get() = t("رفتن به تنظیمات", "Go to settings", "امستنو ته تلل")
  val saved: String get() = t("ذخیره شد", "Saved", "خوندي شو")
  val error: String get() = t("خطا", "Error", "تېروتنه")
  val badAmount: String get() = t("مبلغ درست نیست", "Invalid amount", "مبلغ سم نه دی")
  val fixFields: String get() = t("چند کادر درست نیست؛ پیامِ زیرِ هر کادر را ببینید", "Some fields need fixing; see the note under each", "ځینې ځایونه سم نه دي؛ د هر ځای لاندې پیغام وګورئ")

  // وضعیت و اعلام
  val sfCustomerSees: String get() = t("مشتری الان این را می‌بیند", "What customers see now", "پېرودونکی اوس دا ویني")
  //  جملهٔ وضعیتِ ویترین؛ از نشان با `OnlineFmt.presenceBadge(badge, until)`
  val presOpenOnline: String get() = t("باز است · آنلاین", "Open · online", "پرانیستی · آنلاین")
  val presOpenUnconfirmed: String get() = t("باز است · اتصال تأیید نشده", "Open · connection not confirmed", "پرانیستی · اړیکه نه ده تایید شوې")
  fun presDeclaredUntil(until: String): String = t("تا ساعتِ ${asIs(until)} آنلاین اعلام شده", "Declared online until $until", "تر $until پورې آنلاین اعلان شوی")
  val presDeclared: String get() = t("آنلاین اعلام شده", "Declared online", "آنلاین اعلان شوی")
  val presClosed: String get() = t("بسته است", "Closed", "تړلی دی")
  val presOffline: String get() = t("موقتاً آفلاین", "Temporarily offline", "لنډمهاله آفلاین")
  val presUnknown: String get() = t("وضعیت نامعلوم", "Status unknown", "حالت نامعلوم")
  //  همان، کوتاه (سرِ صفحه)؛ با `OnlineFmt.presenceShort(badge, until)`
  val presShortOpenOnline: String get() = t("باز · آنلاین", "Open · online", "پرانیستی · آنلاین")
  val presShortOpenUnconfirmed: String get() = t("باز · تأییدنشده", "Open · unconfirmed", "پرانیستی · ناتایید")
  fun presShortUntil(until: String): String = t("آنلاین تا ${asIs(until)}", "Online until $until", "تر $until آنلاین")
  val presShortOnline: String get() = t("آنلاین", "Online", "آنلاین")
  val presShortClosed: String get() = t("بسته", "Closed", "تړلی")
  val presShortOffline: String get() = t("آفلاین", "Offline", "آفلاین")
  val presShortUnknown: String get() = t("نامعلوم", "Unknown", "نامعلوم")
  fun presenceMode(mode: String): String = when (mode) {
    "auto" -> t("طبقِ ساعتِ کاری", "By working hours", "د کاري ساعتونو له مخې")
    "online" -> t("آنلاین بی زمانِ پایان", "Online, no end time", "آنلاین، بې پای وخته")
    "online_until" -> t("آنلاین تا ساعتِ …", "Online until …", "تر … پورې آنلاین")
    "offline" -> t("آفلاین", "Offline", "آفلاین")
    "closed" -> t("تعطیل/بسته", "Closed", "رخصت/تړلی")
    else -> t(asIs(mode), mode, mode)
  }
  //  پذیرشِ سفارش؛ کدِ ناشناخته ⇐ هیچ (`OnlineFmt.acceptance`)
  val acceptAccepting: String get() = t("سفارش همین حالا به دکان می‌رسد.", "Orders reach the shop right now.", "فرمایش همدا اوس دوکان ته رسېږي.")
  val acceptDelayed: String get() = t("سفارش ثبت می‌شود، ولی مشتری می‌بیند که دیرتر بررسی می‌شود.", "Orders are placed, but customers see they'll be reviewed later.", "فرمایش ثبتېږي، خو پېرودونکی ویني چې وروسته کتل کېږي.")
  val acceptLater: String get() = t("سفارش ثبت می‌شود و مشتری می‌بیند که پس از باز شدنِ دکان بررسی می‌شود.", "Orders are placed and customers see they'll be reviewed once the shop opens.", "فرمایش ثبتېږي او پېرودونکی ویني چې د دوکان له پرانیستو وروسته کتل کېږي.")
  fun connection(at: String?): String = if (at == null) t("اتصالِ دستگاه‌های دکان هنوز تأیید نشده", "Shop devices' connection isn't confirmed yet", "د دوکان د وسایلو اړیکه لا نه ده تایید شوې")
  else t("آخرین تأییدِ اتصال: ${asIs(at)}", "Last connection check: $at", "د اړیکې وروستۍ تایید: $at")
  fun shopClock(time: String): String = t("ساعتِ دکان ${asIs(time)}", "Shop time $time", "د دوکان ساعت $time")
  val todayHoliday: String get() = t("امروز تعطیلِ استثنایی", "Special holiday today", "نن ځانګړې رخصتي")
  fun todayHours(h: String): String = t("امروز ${asIs(h)}", "Today $h", "نن $h")
  val todayClosed: String get() = t("امروز تعطیل", "Closed today", "نن تړلی")
  val hoursNotSet: String get() = t("ساعتِ کاری تنظیم نشده", "Working hours not set", "کاري ساعتونه نه دي ټاکل شوي")
  val statusNotYet: String get() = t("وضعیت هنوز از سرور نیامده.", "Status hasn't come from the server yet.", "حالت لا له سرور نه دی راغلی.")
  fun currentDeclaration(mode: String): String = t("اعلامِ فعلیِ شما: ${asIs(mode)}", "Your current setting: $mode", "ستاسو اوسنی اعلان: $mode")
  val sfOffNote: String get() = t("ویترین خاموش است", "Storefront is off", "ویترین بند دی")
  val declareTitle: String get() = t("اعلامِ شما به مشتری", "Tell customers", "پېرودونکو ته اعلان")
  val untilField: String get() = t("ساعتِ پایان (مثلاً 20:30)", "End time (e.g. 20:30)", "د پای ساعت (لکه 20:30)")
  val untilError: String get() = t("ساعت را مثلِ 20:30 بنویسید", "Write the time like 20:30", "ساعت لکه 20:30 ولیکئ")
  val submit: String get() = t("ثبت", "Submit", "ثبتول")
  val presenceRule: String get() = t(
    "«ثبت شد» فقط وقتی دیده می‌شود که سرور گرفته باشد. بی اینترنت، تغییر این‌جا می‌ماند و برای مشتری عوض نمی‌شود.",
    "“Saved” only appears once the server has it. Offline, the change waits here and customers don't see it.",
    "«ثبت شو» یوازې هغه وخت ښکاري چې سرور یې ترلاسه کړي. بې انټرنېټه بدلون دلته پاتې کېږي او پېرودونکي ته نه بدلېږي.",
  )
  val presenceNotConfirmed: String get() = t(ir.vil3ntec.tohid.core.storefront.StorefrontOwner.NOT_CONFIRMED, "This change hasn't reached the server yet; your online status isn't confirmed for customers.", "دا بدلون لا سرور ته نه دی رسېدلی؛ ستاسو آنلاین حالت پېرودونکو ته نه دی تایید شوی.")
  val presenceOverridden: String get() = t("ثبت شد، ولی وضعیتِ تازه‌تری از دستگاهِ دیگر جلوتر است؛ همان می‌ماند.", "Saved, but a newer status from another device wins; that one stays.", "ثبت شو، خو له بلې وسیلې نوی حالت مخکې دی؛ هماغه پاتې کېږي.")
  fun presenceReceipt(time: String): String = t("ثبت شد — زمانِ سرور: ${asIs(time)}", "Saved — server time: $time", "ثبت شو — د سرور وخت: $time")
  //  PRES-02: اعلام فقط بسته به دکانِ همین نشست در صف می‌نشیند؛ دکانِ نامعلوم ⇒ نه صف، نه فرستادن
  val presenceNoShop: String get() = t("دکانِ این نشست معلوم نیست؛ یک بار از حساب بیرون و دوباره وارد شوید.", "This session's shop isn't known; sign out and back in once.", "د دې ناستې دوکان معلوم نه دی؛ یو ځل له حساب څخه ووځئ او بیا ننوځئ.")
  val resend: String get() = t("دوباره بفرست", "Send again", "بیا یې ولېږه")
  val sending: String get() = t("در حالِ فرستادن…", "Sending…", "لېږل کېږي…")

  // تنظیماتِ ویترین
  val sfNameSection: String get() = t("نشانی و نام", "Address and name", "پته او نوم")
  val sfSlug: String get() = t("نشانی (زیرِ vill3n.top)", "Address (under vill3n.top)", "پته (د vill3n.top لاندې)")
  fun sfSlugHelper(address: String): String = t("نشانیِ مشتری: ${asIs(address)}", "Customer link: $address", "د پېرودونکي پته: $address")
  val sfName: String get() = t("نامِ ویترین (نامِ کسب‌وکار، نه نامِ شخص)", "Storefront name (business, not a person)", "د ویترین نوم (د کاروبار، نه د کس)")
  val sfTagline: String get() = t("یک جملهٔ کوتاه (اختیاری)", "Short tagline (optional)", "لنډه جمله (اختیاري)")
  val sfPhone: String get() = t("شمارهٔ تماسِ دکان", "Shop phone", "د دوکان د اړیکې شمېره")
  val sfAccent: String get() = t("رنگِ ویترین (اختیاری)", "Storefront colour (optional)", "د ویترین رنګ (اختیاري)")
  val sfNotice: String get() = t("پیامِ کوتاه کنارِ وضعیت (اختیاری)", "Short note next to the status (optional)", "د حالت تر څنګ لنډ پیغام (اختیاري)")
  val sfEnabled: String get() = t("ویترین روشن باشد", "Storefront on", "ویترین دې فعال وي")
  val sfFulfilSection: String get() = t("تحویل و هزینه", "Fulfilment and fees", "سپارنه او لګښت")
  val sfDeliveryToggle: String get() = t("ارسال به نشانیِ مشتری", "Deliver to the customer's address", "د پېرودونکي پتې ته لېږل")
  val sfPickupToggle: String get() = t("دریافت از دکان", "Pickup from the shop", "له دوکانه اخیستل")
  val sfMinOrder: String get() = t("حداقلِ سفارش", "Minimum order", "لږ تر لږه فرمایش")
  val sfHours: String get() = t("ساعتِ کاری", "Working hours", "کاري ساعتونه")
  val sfHoursHelp: String get() = t(
    "هر روز مثلِ 08:00-12:00، 14:00-20:00؛ خالی = تعطیل. شبِ پس از ۲۴ را در روزِ بعد بنویسید. همه خالی = ساعت تنظیم نشده.",
    "Each day like 08:00-12:00, 14:00-20:00; empty = closed. Write after-midnight hours on the next day. All empty = hours not set.",
    "هره ورځ لکه 08:00-12:00، 14:00-20:00؛ تش = رخصت. له نیمې شپې وروسته ساعتونه په بله ورځ ولیکئ. ټول تش = ساعتونه نه دي ټاکل شوي.",
  )
  val sfTimezone: String get() = t("منطقهٔ زمانیِ دکان", "Shop time zone", "د دوکان د وخت سیمه")
  //  نامِ روز به ترتیبِ `StorefrontOwner.DAY_NAMES` (۰ = یکشنبه)؛ با شماره از `OnlineFmt.dayName(d)`
  val daySunday: String get() = t(ir.vil3ntec.tohid.core.storefront.StorefrontOwner.DAY_NAMES[0], "Sunday", "یکشنبه")
  val dayMonday: String get() = t(ir.vil3ntec.tohid.core.storefront.StorefrontOwner.DAY_NAMES[1], "Monday", "دوشنبه")
  val dayTuesday: String get() = t(ir.vil3ntec.tohid.core.storefront.StorefrontOwner.DAY_NAMES[2], "Tuesday", "سه‌شنبه")
  val dayWednesday: String get() = t(ir.vil3ntec.tohid.core.storefront.StorefrontOwner.DAY_NAMES[3], "Wednesday", "چهارشنبه")
  val dayThursday: String get() = t(ir.vil3ntec.tohid.core.storefront.StorefrontOwner.DAY_NAMES[4], "Thursday", "پنجشنبه")
  val dayFriday: String get() = t(ir.vil3ntec.tohid.core.storefront.StorefrontOwner.DAY_NAMES[5], "Friday", "جمعه")
  val daySaturday: String get() = t(ir.vil3ntec.tohid.core.storefront.StorefrontOwner.DAY_NAMES[6], "Saturday", "شنبه")
  val sfClosed: String get() = t("تعطیلیِ استثنایی (تاریخ‌ها مثلِ 2026-10-20)", "Special closures (dates like 2026-10-20)", "ځانګړې رخصتۍ (نېټې لکه 2026-10-20)")
  val sfSave: String get() = t("ذخیرهٔ ویترین", "Save storefront", "ویترین خوندي کول")
  val sfSaved: String get() = t("ویترین ذخیره شد", "Storefront saved", "ویترین خوندي شو")
  val sfTitleRequired: String get() = t("برای روشن کردن، نامِ ویترین لازم است", "A name is needed to turn it on", "د فعالولو لپاره د ویترین نوم اړین دی")
  val sfAccentFormat: String get() = t("مثلِ #2c5ce6", "Like #2c5ce6", "لکه #2c5ce6")
  val sfOneFulfilment: String get() = t("دست‌کم یکی از ارسال یا دریافت از دکان", "At least one of delivery or pickup", "لږ تر لږه لېږل یا له دوکانه اخیستل")

  // کالاهای ویترین
  val sfSearch: String get() = t("جست‌وجوی نامِ کالا", "Search product name", "د توکي نوم لټول")
  val all: String get() = t("همه", "All", "ټول")
  val sfInStore: String get() = t("در ویترین", "In storefront", "په ویترین کې")
  val sfOutStore: String get() = t("بیرون از ویترین", "Not in storefront", "له ویترین بهر")
  val sfInactive: String get() = t("غیرفعال در دفتر", "Inactive in the ledger", "په دفتر کې غیرفعال")
  val sfNoProducts: String get() = t("هنوز کالایی همگام نشده", "No products synced yet", "لا هېڅ توکی نه دی همغږی شوی")
  val sfNoProductsBody: String get() = t("کالاها پس از همگام‌سازی با سرور این‌جا دیده می‌شوند.", "Products show here after syncing with the server.", "توکي له سرور سره تر همغږۍ وروسته دلته ښکاري.")
  val sfNoMatch: String get() = t("کالایی با این جست‌وجو نیست", "No product matches", "په دې لټون هېڅ توکی نشته")
  fun onlinePrice(p: String): String = t("${asIs(p)} (آنلاین)", "$p (online)", "$p (آنلاین)")
  val noPrice: String get() = t("بی‌قیمت — در ویترین دیده نمی‌شود", "No price — not shown", "بې بیې — په ویترین کې نه ښکاري")
  val publish: String get() = t("انتشار", "Publish", "خپرول")
  val unpublish: String get() = t("برداشتن", "Remove", "لرې کول")
  fun bulkDone(ok: Int, failed: Int): String = if (failed == 0) t("${asIs(ok)} کالا انجام شد", "$ok ${pl(ok, "product", "products")} done", "$ok توکي ترسره شول")
  else t("${asIs(ok)} انجام شد، ${asIs(failed)} نشد", "$ok done, $failed failed", "$ok ترسره شول، $failed ونه شول")
  fun ledgerPrice(p: String?): String = t("قیمتِ دفتر: ${asIs(p ?: "ندارد")}", "Ledger price: ${p ?: "none"}", "د دفتر بیه: ${p ?: "نشته"}")
  val sfOnlinePrice: String get() = t("قیمتِ آنلاین (خالی = قیمتِ دفتر)", "Online price (empty = ledger price)", "آنلاین بیه (تش = د دفتر بیه)")
  fun sfMaxQty(unit: String): String = t("سقفِ هر سفارش (${asIs(unit.ifBlank { "واحد" })}، خالی = بی سقف)", "Per-order limit (${unit.ifBlank { "unit" }}, empty = none)", "د هر فرمایش سقف (${unit.ifBlank { "واحد" }}، تش = بې سقفه)")
  val sfDescription: String get() = t("توضیح برای مشتری (اختیاری)", "Description for customers (optional)", "پېرودونکي ته تشریح (اختیاري)")
  val sfSpecs: String get() = t("مشخصات — هر خط «نام: مقدار» (اختیاری)", "Specs — one “name: value” per line (optional)", "ځانګړنې — هره کرښه «نوم: ارزښت» (اختیاري)")
  val sfSpecsPlaceholder: String get() = t("وزن: ۱ کیلو", "Weight: 1 kg", "وزن: ۱ کیلو")
  val sfProductHelp: String get() = t("یادداشتِ داخلیِ دفتر هرگز به ویترین نمی‌رود.", "Internal ledger notes never go to the storefront.", "د دفتر دننۍ یادښت هېڅکله ویترین ته نه ځي.")
  fun inStoreToggle(name: String): String = t("در ویترین: ${asIs(name)}", "In storefront: $name", "په ویترین کې: $name")


  // بازارچه
  val mkTitle: String get() = t("نمایش در بازارچه", "Show in the market", "په بازار کې ښودل")
  val mkBody: String get() = t(
    "بازارچه فهرستِ عمومیِ دکان‌ها در سایتِ مشتری است؛ با روشن کردنِ این کلید، مشتریِ تازه دکان و ویترینتان را پیدا می‌کند.",
    "The market is the public list of shops on the customer site; turn this on so new customers can find your shop.",
    "بازار د پېرودونکي په سایټ کې د دوکانونو عام لړلیک دی؛ دا کلي فعال کړئ چې نوي پېرودونکي ستاسو دوکان ومومي.",
  )
  val mkToggle: String get() = t("در بازارچه نشان بده", "Show in the market", "په بازار کې یې وښیه")
  val mkCategory: String get() = t("دستهٔ دکان", "Shop category", "د دوکان ډله")
  val mkArea: String get() = t("ناحیه یا گذر (اختیاری)", "Area or street (optional)", "سیمه یا کوڅه (اختیاري)")
  val mkAreaPlaceholder: String get() = t("مثلاً کارتهٔ سه", "e.g. Karte-3", "لکه کارته سه")
  fun mkAreaHelper(count: String): String = t("${asIs(count)} — مشتری این را کنارِ نامِ دکان می‌بیند", "$count — customers see this next to the shop name", "$count — پېرودونکی دا د دوکان تر نوم څنګ ویني")
  val mkLocation: String get() = t("موقعیتِ تقریبیِ دکان (اختیاری)", "Approximate location (optional)", "د دوکان نږدې ځای (اختیاري)")
  val mkLocationBody: String get() = t(
    "فقط حدودِ یک کیلومتر نگه داشته می‌شود، نه نشانیِ دقیق؛ برای «نزدیکِ من» ِ مشتری. مشتری خودِ عدد را نمی‌بیند.",
    "Only about 1 km is kept, not the exact address — for customers' “near me”. Customers never see the numbers.",
    "یوازې شاوخوا یو کیلومتره ساتل کېږي، نه دقیقه پته؛ د پېرودونکي د «ما ته نږدې» لپاره. پېرودونکی پخپله شمېرې نه ویني.",
  )
  //  موقعیتِ تقریبی؛ از مختصات با `OnlineFmt.locationText(lat, lng)`
  val locNotSet: String get() = t("گذاشته نشده", "Not set", "نه دی ټاکل شوی")
  fun locAbout(coords: String): String = t("حدودِ ${asIs(coords)}", "About $coords", "شاوخوا $coords")
  val mkSetHere: String get() = t("گذاشتنِ موقعیتِ همین‌جا", "Use my current location", "همدا ځای ټاکل")
  val mkLocating: String get() = t("در حالِ یافتنِ موقعیت…", "Finding location…", "ځای موندل کېږي…")
  val mkRemoveLocation: String get() = t("برداشتنِ موقعیت", "Remove location", "ځای لرې کول")
  val mkDesktopNote: String get() = t("این دستگاه مکان‌یاب ندارد؛ موقعیت را از برنامهٔ تلفنِ همراه بگذارید.", "This device has no location; set it from the phone app.", "دا وسیله ځای‌موندونکی نه لري؛ ځای د ګرځنده اپ له لارې کېږدئ.")
  val mkNoFix: String get() = t("موقعیت پیدا نشد؛ مکان‌یابی را روشن کنید و دوباره بزنید.", "Location not found; turn on location and try again.", "ځای ونه موندل شو؛ ځای‌موندنه فعاله کړئ او بیا هڅه وکړئ.")
  val mkNoPermission: String get() = t("اجازهٔ مکان‌یابی داده نشد. بی موقعیت هم دکان در بازارچه دیده می‌شود.", "Location permission denied. The shop still shows in the market without it.", "د ځای اجازه ورنکړل شوه. بې ځایه هم دوکان په بازار کې ښکاري.")
  val mkSave: String get() = t("ذخیرهٔ بازارچه", "Save market", "بازار خوندي کول")
  val mkSavedListed: String get() = t("ذخیره شد — دکان در بازارچه نشان داده می‌شود.", "Saved — the shop shows in the market.", "خوندي شو — دوکان په بازار کې ښودل کېږي.")
  val mkSavedHidden: String get() = t("ذخیره شد — دکان در بازارچه نشان داده نمی‌شود.", "Saved — the shop is hidden from the market.", "خوندي شو — دوکان په بازار کې نه ښودل کېږي.")
  fun mkSaveFailed(e: String): String = t("ذخیره نشد: ${asIs(e)}", "Not saved: $e", "خوندي نشو: $e")
  val mkOffTitle: String get() = t("بازارچه هنوز باز نیست", "The market isn't open yet", "بازار لا نه دی پرانیستل شوی")
  val mkOffBody: String get() = t("بازارچه هنوز برای مشتری باز نیست. وقتی باز شد، همین‌جا می‌توانید دکان را در فهرستش بگذارید.", "The market isn't open to customers yet. When it opens, you can list your shop here.", "بازار لا پېرودونکو ته نه دی پرانیستل شوی. کله چې پرانیستل شو، همدلته کولای شئ دوکان پکې ثبت کړئ.")
  val mkStorefrontOffBody: String get() = t("نمایش در بازارچه پس از روشن شدنِ ویترین در دسترس است.", "Market listing is available once the storefront is on.", "په بازار کې ښودل د ویترین له فعالېدو وروسته شونی دی.")
  val mkOwnerBody: String get() = t("نمایش در بازارچه را صاحب دکان یا مدیر تنظیم می‌کند.", "The owner or a manager sets up market listing.", "په بازار کې ښودل د دوکان خاوند یا مدیر تنظیموي.")
  val mkLoadFailed: String get() = t("بخشِ بازارچه باز نشد", "The market section didn't open", "د بازار برخه پرانیستل نشوه")
  val mkListed: String get() = t("دکان در بازارچه نشان داده می‌شود.", "The shop shows in the market.", "دوکان په بازار کې ښودل کېږي.")
  val mkNotListed: String get() = t("دکان در بازارچه نشان داده نمی‌شود.", "The shop isn't shown in the market.", "دوکان په بازار کې نه ښودل کېږي.")
  val mkReadOnlyOwner: String get() = t("تغییرِ این بخش کارِ صاحب دکان یا مدیر است.", "Only the owner or a manager can change this.", "د دې برخې بدلون د خاوند یا مدیر کار دی.")
  val mkReadOnlyPlan: String get() = t("اشتراکِ این دکان ویترین را در بر ندارد؛ تغییرِ بازارچه ذخیره نمی‌شود.", "This shop's plan doesn't include the storefront; market changes won't be saved.", "د دې دوکان ګډون ویترین نه لري؛ د بازار بدلون نه خوندي کېږي.")
  val mkCategoryLabel: String get() = t("دسته", "Category", "ډله")
  val mkAreaLabel: String get() = t("ناحیه", "Area", "سیمه")
  fun mkHidden(storefrontExists: Boolean): String = if (!storefrontExists)
    t("ویترین هنوز ساخته نشده؛ تا ساخته و روشن نشود، دکان در بازارچه دیده نمی‌شود.", "The storefront isn't created yet; the shop won't show in the market until it's created and on.", "ویترین لا نه دی جوړ شوی؛ تر جوړېدو او فعالېدو پورې دوکان په بازار کې نه ښکاري.")
  else t("ویترین خاموش است؛ تا روشنش نکنید، دکان در بازارچه دیده نمی‌شود.", "The storefront is off; the shop won't show in the market until you turn it on.", "ویترین بند دی؛ تر هغه چې فعال یې نه کړئ، دوکان په بازار کې نه ښکاري.")
  val mkPickCategory: String get() = t("یک دسته انتخاب کنید", "Choose a category", "یوه ډله وټاکئ")


  // ارسال و زمانِ ویترین (ناحیه‌ها و بازه‌ها)
  val dzLoadFailed: String get() = t("ارسال و زمان باز نشد", "Delivery & times didn't open", "لېږد او وخت پرانیستل نشو")
  val dzOffTitle: String get() = t("ناحیه و زمان‌بندی روشن نیست", "Zones and scheduling aren't on", "سیمې او مهال‌وېش نه دي فعال")
  val dzZones: String get() = t("ناحیه‌های ارسال", "Delivery zones", "د لېږد سیمې")
  val dzZonesHelp: String get() = t(
    "بی ناحیه، همان هزینه و حداقلِ «تنظیمات» برای همه‌جا. با ناحیه، پینِ مشتری خودکار سنجیده می‌شود؛ بیرون از ناحیه‌ها ارسال پذیرفته نمی‌شود و «دریافت از دکان» پیشنهاد می‌شود.",
    "Without zones, the fee and minimum from Settings apply everywhere. With zones, the customer's pin is checked automatically; outside them delivery isn't accepted and pickup is offered.",
    "بې سیمو، د «امستنو» لګښت او لږترلږه په ټولو ځایونو کې. له سیمو سره، د پېرودونکي پن پخپله کتل کېږي؛ له سیمو بهر لېږل نه منل کېږي او «له دوکانه اخیستل» وړاندې کېږي.",
  )
  val dzNoZones: String get() = t("هنوز ناحیه‌ای نیست.", "No zones yet.", "لا هېڅ سیمه نشته.")
  fun dzZoneN(i: Int): String = t("ناحیهٔ ${asIs(i)}", "Zone $i", "سیمه $i")
  val dzName: String get() = t("نامِ ناحیه", "Zone name", "د سیمې نوم")
  val dzPolygon: String get() = t("چندضلعی", "Polygon", "څوګوټی")
  val dzRadius: String get() = t("دایره دورِ یک نقطه", "Circle around a point", "د یوه ټکي شاوخوا کړۍ")
  val dzCenter: String get() = t("مرکز (عرض، طول)", "Centre (lat, lng)", "مرکز (عرض، طول)")
  val dzRadiusKm: String get() = t("شعاع (کیلومتر)", "Radius (km)", "شعاع (کیلومتر)")
  val dzPoints: String get() = t("نقطه‌ها — هر خط «عرض، طول» (دست‌کم ۳)", "Points — one “lat, lng” per line (at least 3)", "ټکي — هره کرښه «عرض، طول» (لږ تر لږه ۳)")
  val dzFree: String get() = t("ارسالِ رایگان از (اختیاری)", "Free delivery from (optional)", "وړیا لېږل له (اختیاري)")
  val dzZoneActive: String get() = t("این ناحیه فعال باشد", "Zone active", "دا سیمه دې فعاله وي")
  val dzRemoveZone: String get() = t("حذفِ ناحیه", "Remove zone", "سیمه لرې کول")
  val dzAddZone: String get() = t("افزودنِ ناحیه", "Add zone", "سیمه زیاتول")
  val dzZoneRemoved: String get() = t("ناحیه برداشته شد", "Zone removed", "سیمه لرې شوه")
  val dzSlots: String get() = t("زمان‌بندی", "Scheduling", "مهال‌وېش")
  val dzSlotsHelp: String get() = t(
    "بازه‌ها به ساعتِ دکان و بی روزهای تعطیل نشان داده می‌شوند؛ پر شدنِ ظرفیت ⇒ «پر است». مشتری تعدادِ سفارش‌ها را نمی‌بیند.",
    "Slots show in shop time, skipping closed days; full capacity ⇒ “full”. Customers don't see order counts.",
    "وختونه د دوکان په ساعت او بې رخصتۍ ورځو ښودل کېږي؛ ډک ظرفیت ⇒ «ډک دی». پېرودونکی د فرمایشونو شمېر نه ویني.",
  )
  val dzAsap: String get() = t("سفارشِ «هرچه زودتر» هم پذیرفته شود", "Also accept “as soon as possible” orders", "د «ژر تر ژره» فرمایش هم ومنل شي")
  val dzDays: String get() = t("چند روزِ پیشِ رو نشان داده شود (۱ تا ۱۴)", "Days ahead to show (1–14)", "څو مخکې ورځې وښودل شي (۱ تر ۱۴)")
  val dzNoRules: String get() = t("هنوز بازه‌ای نیست؛ همه «هرچه زودتر».", "No slots yet; all “as soon as possible”.", "لا هېڅ وخت نشته؛ ټول «ژر تر ژره».")
  fun dzRuleN(i: Int): String = t("بازهٔ ${asIs(i)}", "Slot $i", "وخت $i")
  val dzRange: String get() = t("ساعت (مثلِ 10:00-12:00)", "Hours (like 10:00-12:00)", "ساعت (لکه 10:00-12:00)")
  val dzCapacity: String get() = t("ظرفیت (چند سفارش)", "Capacity (orders)", "ظرفیت (څو فرمایشه)")
  val dzCutoff: String get() = t("مهلتِ سفارش (دقیقه پیش از آغاز)", "Order cutoff (minutes before start)", "د فرمایش وروستی وخت (له پیل مخکې دقیقې)")
  val dzRuleActive: String get() = t("این بازه فعال باشد", "Slot active", "دا وخت دې فعال وي")
  val dzRemoveRule: String get() = t("حذفِ بازه", "Remove slot", "وخت لرې کول")
  val dzAddRule: String get() = t("افزودنِ بازه", "Add slot", "وخت زیاتول")
  val dzRuleRemoved: String get() = t("بازه برداشته شد", "Slot removed", "وخت لرې شو")
  val dzSave: String get() = t("ذخیرهٔ ارسال و زمان", "Save delivery & times", "لېږد او وخت خوندي کول")
  val dzSaved: String get() = t("ارسال و زمان ذخیره شد", "Delivery & times saved", "لېږد او وخت خوندي شول")


  // ── گفت‌وگو با مشتری ────────────────────────────────────────────────────────
  val ctTitle: String get() = t("گفت‌وگو با مشتری", "Customer chat", "له پېرودونکي سره خبرې")
  val ctCaption: String get() = t("پیام‌های مشتریانِ ویترین — بی اینترنت هم نوشته می‌شود", "Storefront customers' messages — works offline too", "د ویترین د پېرودونکو پیغامونه — بې انټرنېټه هم لیکل کېږي")
  val ctSearch: String get() = t("نامِ مشتری یا شماره…", "Customer name or number…", "د پېرودونکي نوم یا شمېره…")
  val ctOpen: String get() = t("باز", "Open", "پرانیستې")
  val ctClosed: String get() = t("بسته", "Closed", "تړلې")
  val ctOffline: String get() = t("اتصال نیست؛ فهرست پس از وصل شدن تازه می‌شود.", "Offline; the list refreshes once connected.", "اړیکه نشته؛ لړلیک له نښلېدو وروسته تازه کېږي.")
  val ctNoMatch: String get() = t("گفت‌وگویی با این نام یا شماره نیست.", "No chat with this name or number.", "د دې نوم یا شمېرې سره خبرې نشته.")
  val ctEmptyTitle: String get() = t("هنوز گفت‌وگویی نیست", "No chats yet", "لا خبرې نشته")
  val ctEmptyBody: String get() = t("وقتی مشتری از ویترین پیام بدهد، این‌جا می‌آید.", "When a customer messages from the storefront, it appears here.", "کله چې پېرودونکی له ویترین پیغام ولېږي، دلته راځي.")
  val ctNoMessageYet: String get() = t("هنوز پیامی نیست", "No messages yet", "لا پیغام نشته")
  val ctDeletedMsg: String get() = t("پیامِ پاک‌شده", "Deleted message", "ړنګ شوی پیغام")
  val ctThisDeleted: String get() = t("این پیام پاک شد", "This message was deleted", "دا پیغام ړنګ شو")
  val ctShopPrefix: String get() = t("دکان: ", "Shop: ", "دوکان: ")
  //  نوعِ پیام؛ نوعِ دیگر ⇐ هیچ (`OnlineFmt.ctKind`)
  val ctKindImage: String get() = t("عکس", "Photo", "انځور")
  val ctKindAudio: String get() = t("پیامِ صوتی", "Voice message", "غږیز پیغام")
  val ctKindFile: String get() = t("فایل", "File", "فایل")
  val ctBlocked: String get() = t("مسدود", "Blocked", "بند شوی")
  val ctCustomerBlockedShop: String get() = t("مشتری بسته است", "Customer blocked you", "پېرودونکي بند کړی")
  fun ctOrderChip(n: Long): String = t("سفارشِ ${asIs(n)}", "Order $n", "فرمایش $n")
  val ctSelect: String get() = t("یک گفت‌وگو را از فهرست باز کنید.", "Open a chat from the list.", "له لړلیک څخه یوه خبره پرانیزئ.")
  val ctCloseConv: String get() = t("بستنِ گفت‌وگو", "Close chat", "خبرې تړل")
  val ctReopen: String get() = t("باز کردنِ گفت‌وگو", "Reopen chat", "خبرې بیا پرانیستل")
  val ctReportConv: String get() = t("گزارشِ این گفت‌وگو", "Report this chat", "د دې خبرو راپور")
  val ctBlock: String get() = t("مسدود کردنِ مشتری", "Block customer", "پېرودونکی بندول")
  val ctUnblock: String get() = t("برداشتنِ مسدودی", "Unblock", "بندیز لرې کول")
  val ctReportTitleMsg: String get() = t("گزارشِ این پیام به مدیرِ سامانه", "Report this message to the platform admin", "د دې پیغام راپور د سیسټم مدیر ته")
  val ctReportTitleConv: String get() = t("گزارشِ این گفت‌وگو به مدیرِ سامانه", "Report this chat to the platform admin", "د دې خبرو راپور د سیسټم مدیر ته")
  fun ctReason(id: String): String = when (id) {
    "spam" -> t("هرزنامه", "Spam", "سپام")
    "abuse" -> t("توهین یا آزار", "Insult or harassment", "سپکاوی یا ځورونه")
    "fraud" -> t("کلاه‌برداری", "Fraud", "درغلي")
    "other" -> t("دیگر", "Other", "بل")
    else -> t(asIs(id), id, id)
  }
  val ctReportNote: String get() = t("توضیح (اختیاری)", "Note (optional)", "تشریح (اختیاري)")
  val ctSendReport: String get() = t("فرستادنِ گزارش", "Send report", "راپور لېږل")
  val ctReportDup: String get() = t("این مورد پیش‌تر گزارش شده بود.", "This was already reported.", "دا مخکې راپور شوی و.")
  val ctReportSent: String get() = t("گزارش رسید؛ مدیرِ سامانه بررسی می‌کند.", "Report received; the platform admin will review it.", "راپور ورسېد؛ د سیسټم مدیر یې ګوري.")
  val ctReport: String get() = t("گزارش", "Report", "راپور")
  val ctOfflineThread: String get() = t("اتصال نیست؛ پیام‌های نوشته‌شده در صف می‌مانند و پس از وصل می‌روند.", "Offline; written messages wait in the queue and go once connected.", "اړیکه نشته؛ لیکل شوي پیغامونه په کتار کې پاتې کېږي او له نښلېدو وروسته ځي.")
  val ctPreparingPhoto: String get() = t("آماده کردنِ عکس…", "Preparing photo…", "انځور چمتو کېږي…")
  fun ctPhotoReady(size: String): String = t("عکس آماده شد (${asIs(size)})", "Photo ready ($size)", "انځور چمتو شو ($size)")
  val ctPhotoFailed: String get() = t("عکس آماده نشد", "Photo couldn't be prepared", "انځور چمتو نشو")
  /** کیلوبایتِ گردشده؛ از بایت با `OnlineFmt.kb(bytes)` */
  fun kbAmount(n: Long): String = t("${asIs(n)} کیلوبایت", "$n KB", "$n کیلوبایټ")
  val ctReadOnly: String get() = t("نقشِ شما اجازهٔ پاسخ ندارد؛ فقط خواندن.", "Your role can't reply; read only.", "ستاسو رول د ځواب اجازه نه لري؛ یوازې لوستل.")
  val ctCustomerBlockedNote: String get() = t("مشتری پیامِ این دکان را بسته است.", "The customer has blocked this shop's messages.", "پېرودونکي د دې دوکان پیغامونه بند کړي.")
  val ctMessage: String get() = t("پیام…", "Message…", "پیغام…")
  val ctSend: String get() = t("فرستادن", "Send", "لېږل")
  val ctSendPhoto: String get() = t("فرستادنِ عکس", "Send photo", "انځور لېږل")
  val ctTapFull: String get() = t("عکس؛ برای نمایشِ کامل بزنید", "Photo; tap for full size", "انځور؛ د بشپړ لید لپاره یې ووهئ")
  val ctPlayVoice: String get() = t("پخشِ پیامِ صوتی", "Play voice message", "غږیز پیغام غږول")
  val ctSaveVoice: String get() = t("ذخیرهٔ پیامِ صوتی", "Save voice message", "غږیز پیغام خوندي کول")
  val ctPdf: String get() = t("فایلِ PDF", "PDF file", "PDF فایل")
  val ctEdited: String get() = t("ویرایش‌شده", "Edited", "سم شوی")
  val ctQueuedImage: String get() = t("عکسِ در صف", "Queued photo", "په کتار کې انځور")
  //  وضعیتِ پیام؛ وضعیتِ دیگر ⇐ هیچ (`OnlineFmt.ctState`)
  val ctStateQueued: String get() = t("در صفِ دستگاه", "In device queue", "د وسیلې په کتار کې")
  val ctStateSending: String get() = t("در حالِ ارسال…", "Sending…", "لېږل کېږي…")
  val ctStateWaiting: String get() = t("منتظرِ اینترنت", "Waiting for internet", "د انټرنېټ په تمه")
  val ctStateAuth: String get() = t("منتظرِ ورودِ دوباره", "Waiting for sign-in", "د بیا ننوتو په تمه")
  val ctStateFailed: String get() = t("نرفت", "Not sent", "ونه لېږل شو")
  val ctStateSent: String get() = t("به سرور رسید", "Reached the server", "سرور ته ورسېد")
  val ctStateDelivered: String get() = t("به دستگاهِ طرف رسید", "Delivered to their device", "د بل لوري وسیلې ته ورسېد")
  val ctStateRead: String get() = t("خوانده شد", "Read", "ولوستل شو")
  fun ctRecording(time: String): String = t("در حالِ ضبط ${asIs(time)}", "Recording $time", "ثبتېږي $time")
  val ctStopRec: String get() = t("پایانِ ضبط", "Stop", "ثبت بس")
  fun ctVoiceReady(time: String, size: String): String = t("پیامِ صوتی ${asIs(time)} · ${asIs(size)}", "Voice message $time · $size", "غږیز پیغام $time · $size")
  val ctTooBig: String get() = t("بزرگ‌تر از سقف", "over the limit", "له سقف لوی")
  val ctSendVoice: String get() = t("فرستادنِ صدا", "Send voice", "غږ لېږل")
  val ctHoldToRecord: String get() = t("برای ضبط نگه دارید", "Hold to record", "د ثبت لپاره ونیسئ")
  fun ctRecFailed(m: String): String = t("ضبط شروع نشد: ${asIs(m)}", "Recording didn't start: $m", "ثبت پیل نشو: $m")
  val ctNoMic: String get() = t("بی اجازهٔ میکروفون صدا ضبط نمی‌شود.", "Can't record without microphone permission.", "د مایکروفون له اجازې پرته غږ نه ثبتېږي.")
  val ctNoVoiceDesktop: String get() = t(
    "ضبطِ صدا روی کامپیوتر پشتیبانی نمی‌شود؛ پیامِ صوتیِ مشتری را می‌توانید ذخیره و گوش کنید.",
    "Voice recording isn't supported on the computer; you can save and listen to customers' voice messages.",
    "په کمپیوټر کې غږ ثبتول نه کېږي؛ د پېرودونکي غږیز پیغام خوندي کولای او اورېدای شئ.",
  )
  val ctVoiceNotPlayed: String get() = t("این پیامِ صوتی روی این گوشی پخش نشد", "This voice message didn't play on this phone", "دا غږیز پیغام په دې تلیفون کې ونه غږېد")


  // ── میزِ تحویل ─────────────────────────────────────────────────────────────
  val dlTitle: String get() = t("میزِ تحویل", "Delivery desk", "د سپارنې مېز")
  val dlCaption: String get() = t("سپردن به پیک، نقدِ پیک‌ها، دریافتِ حضوری و شرکت‌های بیرونی", "Assign couriers, courier cash, pickup and external companies", "پیک ته سپارل، د پیکانو نغدې، حضوري اخیستل او بهرني شرکتونه")
  val dlTabAssign: String get() = t("سپردن", "Assign", "سپارل")
  val dlTabCouriers: String get() = t("پیک‌ها و نقد", "Couriers & cash", "پیکان او نغدې")
  val dlTabPickup: String get() = t("دریافتِ حضوری", "In-store pickup", "حضوري اخیستل")
  val dlTabExternal: String get() = t("شرکت‌های بیرونی", "External companies", "بهرني شرکتونه")
  val dlStatReady: String get() = t("آمادهٔ سپردن", "Ready to assign", "د سپارلو لپاره چمتو")
  val dlStatLive: String get() = t("مأموریتِ زنده", "Live missions", "ژوندۍ دندې")
  val dlStatCash: String get() = t("نقدِ دستِ پیک‌ها", "Cash with couriers", "د پیکانو په لاس نغدې")
  val dlStatAtCap: String get() = t("پیکِ به سقف‌رسیده", "Couriers at cap", "سقف ته رسېدلي پیکان")
  val dlNow: String get() = t("اکنون", "Now", "اوس")
  val dlUnsettled: String get() = t("تسویه‌نشده", "Unsettled", "نه تصفیه شوې")
  val dlNoAuto: String get() = t("سفارشِ خودکار نمی‌گیرد", "Gets no auto orders", "خپلکاره فرمایش نه اخلي")
  val dlNotEntitled: String get() = t("اشتراکِ تحویل برای این دکان فعال نیست؛ فقط دیدن ممکن است.", "Delivery isn't in this shop's plan; view only.", "د دې دوکان لپاره د سپارنې ګډون فعال نه دی؛ یوازې کتل کېږي.")
  val dlLoadFailed: String get() = t("میزِ تحویل نیامد", "The delivery desk didn't load", "د سپارنې مېز رانغی")
  fun dlLastFailed(e: String): String = t("آخرین تازه‌سازی نشد: ${asIs(e)}", "Last refresh failed: $e", "وروستی تازه کول ونه شول: $e")
  val dlReadyTitle: String get() = t("سفارش‌های آماده برای سپردن", "Orders ready to assign", "د سپارلو لپاره چمتو فرمایشونه")
  fun dlAutoAll(n: Int): String = t("سپردنِ خودکار (${asIs(n)})", "Auto-assign ($n)", "خپلکاره سپارل ($n)")
  val dlNoReady: String get() = t("سفارشِ ارسالیِ آماده‌ای که پیک نداشته باشد نیست.", "No delivery orders waiting for a courier.", "د لېږد داسې چمتو فرمایش نشته چې پیک ونه لري.")
  fun dlCollect(a: String): String = t("نقدِ گرفتنی: ${asIs(a)}", "Cash to collect: $a", "اخیستونکې نغدې: $a")
  val dlPaidNoCash: String get() = t("پرداخت‌شده — نقدی گرفته نمی‌شود", "Paid — no cash to collect", "ورکړل شوی — نغدې نه اخیستل کېږي")
  val dlAuto: String get() = t("خودکار", "Auto", "خپلکاره")
  val dlAssign: String get() = t("سپردن", "Assign", "سپارل")
  val dlLiveTitle: String get() = t("مأموریت‌های زنده", "Live missions", "ژوندۍ دندې")
  val dlNoLive: String get() = t("مأموریتِ زنده‌ای نیست.", "No live missions.", "ژوندۍ دنده نشته.")
  fun dlCash(a: String): String = t("نقد: ${asIs(a)}", "Cash: $a", "نغدې: $a")
  val dlNoCash: String get() = t("بی نقد", "No cash", "بې نغدو")
  fun dlFare(a: String): String = t("کرایه: ${asIs(a)}", "Fare: $a", "کرایه: $a")
  val dlNoFare: String get() = t("بی کرایه", "No fare", "بې کرایې")
  //  سرستون‌های جدولِ میز روی کامپیوترِ پهن
  val dlColOrder: String get() = t("سفارش", "Order", "فرمایش")
  val dlColCustomer: String get() = t("مشتری", "Customer", "پېرودونکی")
  val dlColStatus: String get() = t("وضعیت", "Status", "حالت")
  val dlColLandmark: String get() = t("نشانه", "Landmark", "نښه")
  val dlColCollect: String get() = t("نقدِ گرفتنی", "Cash to collect", "اخیستونکې نغدې")
  val dlColHow: String get() = t("روش", "Method", "لاره")
  val dlColFare: String get() = t("کرایه", "Fare", "کرایه")
  val dlRecallSent: String get() = t("فراخوانی فرستاده شد", "Recall sent", "بېرته غوښتنه ولېږل شوه")
  val dlCashReview: String get() = t("اختلافِ نقد — بررسی", "Cash difference — review", "د نغدو توپیر — کتنه")
  fun dlAttempt(n: Int): String = t("تلاشِ ${asIs(n)}", "Attempt $n", "هڅه $n")
  fun dlAssigned(n: Long, who: String): String = t("سفارش #${asIs(n)} سپرده شد: ${asIs(who)}", "Order #$n assigned: $who", "فرمایش #$n وسپارل شو: $who")
  val dlCourier: String get() = t("پیک", "Courier", "پیک")
  val dlNoCouriers: String get() = t("پیکی ثبت نشده", "No couriers yet", "هېڅ پیک نه دی ثبت شوی")
  val dlNoCouriersBody: String get() = t(
    "از «کارمندان» یک کدِ دعوت با نقشِ پیک بسازید؛ پیک با همان کد واردِ برنامهٔ پیک می‌شود.",
    "Create an invite code with the courier role in Team; the courier signs in to the courier app with it.",
    "له «کارکوونکو» څخه د پیک رول سره د بلنې کوډ جوړ کړئ؛ پیک په همدې کوډ د پیک اپ ته ننوځي.",
  )
  val dlSettleOwner: String get() = t("تسویه و دفترِ تسویه‌ها فقط با صاحب یا مدیرِ دکان است.", "Settlement and its ledger are for the owner or manager only.", "تصفیه او د تصفیو دفتر یوازې د دوکان د خاوند یا مدیر لپاره دی.")
  val dlInactive: String get() = t("غیرفعال", "Inactive", "غیرفعال")
  val dlOverCap: String get() = t("بالای سقف", "Over cap", "له سقف پورته")
  val dlAtCap: String get() = t("به سقف رسیده", "At cap", "سقف ته رسېدلی")
  val dlReadyState: String get() = t("آماده", "Ready", "چمتو")
  val dlHeld: String get() = t("در دست، تسویه‌نشده", "Held, unsettled", "په لاس، نه تصفیه شوې")
  fun dlLiveExpected(n: Int): String = t("گرفتنی از ${asIs(n)} مأموریتِ زنده", "To collect from $n live", "له $n ژوندیو دندو اخیستونکې")
  val dlRoom: String get() = t("جای خالی تا سقف", "Room under cap", "تر سقف تش ځای")
  fun dlCapPercent(p: Int, cap: String): String = t("${asIs(p)}٪ از سقفِ ${asIs(cap)}", "$p% of $cap cap", "$p٪ د $cap سقف")
  val dlSettle: String get() = t("تسویهٔ پایانِ روز", "End-of-day settlement", "د ورځې د پای تصفیه")
  val dlLedger: String get() = t("دفترِ نقد", "Cash ledger", "د نغدو دفتر")
  val dlCapAndState: String get() = t("سقف و وضعیت", "Cap and status", "سقف او حالت")
  fun dlSettled(no: Long, gap: String): String = t("تسویهٔ شمارهٔ ${asIs(no)} ثبت شد${asIs(gap)}", "Settlement no. $no recorded$gap", "د $no شمېرې تصفیه ثبت شوه$gap")
  fun dlShortage(a: String): String = t("کسری ${asIs(a)}", "Short $a", "کمښت $a")
  fun dlExcess(a: String): String = t("اضافه ${asIs(a)}", "Over $a", "زیاتوالی $a")
  fun dlSavedFor(name: String): String = t("${asIs(name)}: ذخیره شد", "$name: saved", "$name: خوندي شو")
  fun dlSettleTitle(name: String): String = t("تسویهٔ پایانِ روز — ${asIs(name)}", "End-of-day settlement — $name", "د ورځې د پای تصفیه — $name")
  val dlPreviewFailed: String get() = t("پیش‌نمایش نیامد", "Preview didn't load", "مخکتنه رانغله")
  val dlNothingToSettle: String get() = t("تحویلِ تسویه‌نشده‌ای نیست.", "Nothing to settle.", "د تصفیې لپاره هېڅ سپارنه نشته.")
  fun dlDeliveries(n: Int): String = t("${asIs(n)} تحویل", "$n ${pl(n, "delivery", "deliveries")}", "$n سپارنې")
  fun dlDiff(a: String): String = t("اختلاف ${asIs(a)}", "Diff $a", "توپیر $a")
  val dlExpectedFromCustomers: String get() = t("موردِ انتظار از مشتری‌ها", "Expected from customers", "له پېرودونکو تمه")
  val dlCourierGot: String get() = t("پیک گرفت", "Courier collected", "پیک واخیستې")
  val dlCustomerDiff: String get() = t("اختلافِ مشتری (جدا بررسی می‌شود)", "Customer difference (reviewed separately)", "د پېرودونکي توپیر (جلا کتل کېږي)")
  val dlFares: String get() = t("کرایه‌ها", "Fares", "کرایې")
  val dlFareFromCash: String get() = t("کرایه را پیک از همین نقد برداشت", "The courier took the fare from this cash", "پیک کرایه له همدې نغدو واخیسته")
  val dlHandoverDue: String get() = t("پیک باید تحویل دهد", "Courier must hand over", "پیک باید وسپاري")
  val dlHandedClaim: String get() = t("پیک می‌گوید تحویل داد", "Courier says handed over", "پیک وايي چې ورکړې")
  val dlManagerCount: String get() = t("شمارشِ مدیر — لازم", "Manager's count — required", "د مدیر شمېرنه — اړینه")
  val dlBadNumber: String get() = t("عدد درست نیست", "Invalid number", "شمېره سمه نه ده")
  val dlShort: String get() = t("کسری", "Short", "کمښت")
  val dlOver: String get() = t("اضافه", "Over", "زیاتوالی")
  val dlDiffLabel: String get() = t("اختلاف", "Difference", "توپیر")
  val dlNone: String get() = t("ندارد", "None", "نشته")
  val dlClaimMismatch: String get() = t("گفتهٔ پیک با شمارشِ مدیر یکی نیست.", "The courier's claim doesn't match the manager's count.", "د پیک خبره د مدیر له شمېرنې سره سمون نه لري.")
  val dlDiffReasonReq: String get() = t("دلیلِ اختلاف (لازم)", "Reason for difference (required)", "د توپیر لامل (اړین)")
  val dlReasonOpt: String get() = t("دلیل (اختیاری)", "Reason (optional)", "لامل (اختیاري)")
  val dlNoteOpt: String get() = t("یادداشت (اختیاری)", "Note (optional)", "یادښت (اختیاري)")
  val dlConfirmSettle: String get() = t("تأییدِ مدیر و بستنِ تسویه", "Manager confirms and closes", "د مدیر تایید او تصفیه تړل")
  fun dlLedgerTitle(name: String): String = t("دفترِ نقدِ ${asIs(name)}", "$name's cash ledger", "د $name د نغدو دفتر")
  val dlLedgerFailed: String get() = t("دفتر نیامد", "Ledger didn't load", "دفتر رانغی")
  val dlCollectedAll: String get() = t("نقدِ گرفته از مشتری (همه)", "Cash collected (all)", "له پېرودونکي اخیستې نغدې (ټولې)")
  val dlHandedBack: String get() = t("تحویلِ دکان شد (شمارشِ مدیر)", "Handed to shop (manager count)", "دوکان ته وسپارل شوې (د مدیر شمېرنه)")
  val dlOutstanding: String get() = t("مانده در دستِ پیک", "Still with the courier", "د پیک په لاس پاتې")
  val dlShortages: String get() = t("کسریِ تسویه‌ها", "Settlement shortages", "د تصفیو کمښت")
  val dlExcesses: String get() = t("اضافهٔ تسویه‌ها", "Settlement overages", "د تصفیو زیاتوالی")
  val dlRecent: String get() = t("تسویه‌های اخیر", "Recent settlements", "وروستۍ تصفیې")
  val dlNoSettlements: String get() = t("هنوز تسویه‌ای نیست.", "No settlements yet.", "لا تصفیه نشته.")
  fun dlSettleNo(n: Long): String = t("شمارهٔ ${asIs(n)}", "No. $n", "شمېره $n")
  fun dlSettleLine(count: Int, due: String, recv: String): String = t("${asIs(count)} تحویل · بدهی ${asIs(due)} · شمرده ${asIs(recv)}", "$count deliveries · due $due · counted $recv", "$count سپارنې · پور $due · شمېرل شوې $recv")
  fun dlCapTitle(name: String): String = t("سقف و وضعیتِ ${asIs(name)}", "$name — cap and status", "د $name سقف او حالت")
  val dlCapField: String get() = t("سقفِ نقد", "Cash cap", "د نغدو سقف")
  val dlCapHelper: String get() = t("بیش از این نقد در دستِ پیک باشد، سفارشِ خودکار نمی‌گیرد", "Above this much cash, the courier gets no auto orders", "له دې زیاتې نغدې چې د پیک په لاس وي، خپلکاره فرمایش نه اخلي")
  val dlNumberRequired: String get() = t("عدد لازم است", "A number is required", "شمېره اړینه ده")
  val dlActiveOn: String get() = t("فعال — کارِ تازه می‌گیرد", "Active — takes new jobs", "فعال — نوې دندې اخلي")
  val dlActiveOff: String get() = t("غیرفعال — کارِ تازه نمی‌گیرد", "Inactive — no new jobs", "غیرفعال — نوې دندې نه اخلي")
  val dlNoCompanies: String get() = t("هنوز به شرکتِ بیرونی نسپرده‌اید", "Nothing given to external companies yet", "تر اوسه مو بهرني شرکت ته نه دي سپارلي")
  val dlNoCompaniesBody: String get() = t(
    "در «سپردن» روشِ «شرکتِ بیرونی» را بزنید؛ نامِ شرکت و کرایهٔ هر سفارش همان‌جا ثبت می‌شود و این‌جا می‌آید.",
    "In Assign choose “External company”; the company name and fare are recorded there and appear here.",
    "په «سپارلو» کې «بهرنی شرکت» وټاکئ؛ د شرکت نوم او د هر فرمایش کرایه همدلته ثبتېږي او دلته راځي.",
  )
  fun dlLiveCount(n: Int): String = t("${asIs(n)} زنده", "$n live", "$n ژوندۍ")
  fun dlCompanyLine(fare: String, delivered: Int, all: Int, at: String): String = t(
    "کرایهٔ هر سفارش (آخرین): ${asIs(fare)} · تحویل‌شده ${asIs(delivered)} از ${asIs(all)} · آخرین بار ${asIs(at)}",
    "Last fare per order: $fare · delivered $delivered of $all · last used $at",
    "د هر فرمایش کرایه (وروستۍ): $fare · سپارل شوي $delivered له $all · وروستی ځل $at",
  )
  val dlInvoiceCheck: String get() = t("تطبیقِ صورت‌حساب", "Check invoice", "د بیل پرتله")
  fun dlInvoiceTitle(name: String): String = t("تطبیقِ صورت‌حسابِ ${asIs(name)}", "Invoice check — $name", "د $name د بیل پرتله")
  val dlThisMonth: String get() = t("این ماه", "This month", "دا میاشت")
  val dlPrevMonth: String get() = t("ماهِ پیش", "Last month", "تېره میاشت")
  val dlLast30: String get() = t("۳۰ روزِ اخیر", "Last 30 days", "وروستۍ ۳۰ ورځې")
  val dlGiven: String get() = t("سپرده به این شرکت", "Given to this company", "دې شرکت ته سپارل شوي")
  fun dlOrders(n: Int): String = t("${asIs(n)} سفارش", "$n ${pl(n, "order", "orders")}", "$n فرمایشونه")
  val dlDeliveredOurs: String get() = t("تحویل‌شده در دفترِ ما", "Delivered per our books", "زموږ په دفتر کې سپارل شوي")
  val dlFaresOwed: String get() = t("کرایهٔ تحویل‌شده‌ها (دکان به شرکت بدهکار)", "Fares of delivered (shop owes company)", "د سپارل شویو کرایې (دوکان شرکت ته پوروړی)")
  val dlCashOwed: String get() = t("نقدی که شرکت از مشتری‌ها گرفت (شرکت به دکان بدهکار)", "Cash the company collected (company owes shop)", "هغه نغدې چې شرکت له پېرودونکو واخیستې (شرکت دوکان ته پوروړی)")
  val dlNetOurs: String get() = t("خالص طبقِ دفترِ ما", "Net per our books", "زموږ د دفتر له مخې خالص")
  val dlNetInvoice: String get() = t("خالص طبقِ صورت‌حساب", "Net per invoice", "د بیل له مخې خالص")
  fun dlNetShopPays(l: String): String = t("${asIs(l)}: دکان به شرکت می‌دهد", "$l: shop pays company", "$l: دوکان شرکت ته ورکوي")
  fun dlNetCompanyPays(l: String): String = t("${asIs(l)}: شرکت به دکان می‌دهد", "$l: company pays shop", "$l: شرکت دوکان ته ورکوي")
  val dlSquare: String get() = t("بی‌حساب", "Square", "برابر")
  val dlNotDone: String get() = t("ناموفق، برگشت یا هنوز در راه", "Failed, returned or still on the way", "ناکام، راستون یا لا په لاره کې")
  val dlTruncated: String get() = t("بیش از ۱۰۰۰ سفارش — بازهٔ کوتاه‌تری بگیرید.", "Over 1000 orders — pick a shorter period.", "له ۱۰۰۰ زیات فرمایشونه — لنډه موده وټاکئ.")
  fun dlGivenTo(name: String): String = t("سفارش‌های سپرده به ${asIs(name)}", "Orders given to $name", "$name ته سپارل شوي فرمایشونه")
  fun dlFareBit(a: String): String = t("کرایه ${asIs(a)}", "fare $a", "کرایه $a")
  fun dlCashBit(a: String): String = t("نقدِ گرفته ${asIs(a)}", "cash $a", "اخیستې نغدې $a")
  fun dlExpectedBit(a: String): String = t("موردِ انتظار ${asIs(a)}", "expected $a", "تمه $a")
  val dlCompanyInvoice: String get() = t("صورت‌حسابِ شرکت", "Company invoice", "د شرکت بیل")
  val dlInvoiceTotal: String get() = t("مبلغِ کلِ صورت‌حساب — لازم", "Invoice total — required", "د بیل ټوله اندازه — اړینه")
  val dlInvoiceCount: String get() = t("شمارِ سفارش‌ها در صورت‌حساب (اختیاری)", "Orders on the invoice (optional)", "په بیل کې د فرمایشونو شمېر (اختیاري)")
  val dlInvoiceLines: String get() = t("ریزِ صورت‌حساب (اختیاری)", "Invoice lines (optional)", "د بیل جزئیات (اختیاري)")
  val dlInvoiceLinesHelp: String get() = t("هر خط: شمارهٔ رهگیری یا #شمارهٔ سفارش، و مبلغ — مثلاً «TRK-12 150»", "Each line: tracking no. or #order no., and amount — e.g. “TRK-12 150”", "هره کرښه: د تعقیب شمېره یا #د فرمایش شمېره، او مبلغ — لکه «TRK-12 150»")
  val dlCheck: String get() = t("بسنج", "Check", "وسنجه")
  val dlInvoiceOk: String get() = t("صورت‌حساب با دفترِ ما یکی است", "The invoice matches our books", "بیل زموږ له دفتر سره سمون لري")
  val dlInvoiceFaresOk: String get() = t("کرایه‌ها درست است — ولی نقدِ دکان نزدِ شرکت است؛ فقط خالص حساب شود", "Fares are right — but the shop's cash is with the company; settle the net only", "کرایې سمې دي — خو د دوکان نغدې له شرکت سره دي؛ یوازې خالص حساب شي")
  val dlInvoiceDiff: String get() = t("اختلاف هست — پیش از پرداخت بررسی کنید", "There's a difference — check before paying", "توپیر شته — له ورکړې مخکې یې وګورئ")
  val dlAmountDiff: String get() = t("مبلغ: صورت‌حساب − دفترِ ما", "Amount: invoice − our books", "مبلغ: بیل − زموږ دفتر")
  val dlCountDiff: String get() = t("شمار: صورت‌حساب − دفترِ ما", "Count: invoice − our books", "شمېر: بیل − زموږ دفتر")
  fun dlInvoiceKind(k: String): String = when (k) {
    "MATCHED" -> t("درست", "Matches", "سم")
    "AMOUNT_DIFF" -> t("مبلغ فرق دارد", "Amount differs", "مبلغ توپیر لري")
    "NOT_DELIVERED" -> t("در دفترِ ما تحویل نشده", "Not delivered per our books", "زموږ په دفتر کې نه دی سپارل شوی")
    "UNKNOWN" -> t("در دفترِ ما نیست", "Not in our books", "زموږ په دفتر کې نشته")
    "DUPLICATE" -> t("دو بار آمده", "Listed twice", "دوه ځله راغلی")
    "NOT_BILLED" -> t("تحویل شده ولی در صورت‌حساب نیست", "Delivered but not billed", "سپارل شوی خو په بیل کې نشته")
    else -> t(asIs(k), k, k)
  }
  fun dlBilledBit(a: String): String = t("صورت‌حساب ${asIs(a)}", "invoice $a", "بیل $a")
  fun dlOursBit(a: String): String = t("دفترِ ما ${asIs(a)}", "ours $a", "زموږ دفتر $a")
  fun dlAssignTitle(n: Long): String = t("سپردنِ سفارش #${asIs(n)}", "Assign order #$n", "د فرمایش #$n سپارل")
  val dlSuggest: String get() = t("پیشنهاد", "Suggested", "وړاندیز")
  fun dlCourierLine(live: Int, committed: String, cap: String, room: String): String = t(
    "مأموریتِ زنده ${asIs(live)} · نقدِ درگیر ${asIs(committed)} از ${asIs(cap)} · جای خالی ${asIs(room)}",
    "Live $live · committed $committed of $cap · room $room",
    "ژوندۍ $live · بندې نغدې $committed له $cap · تشه $room",
  )
  val dlWouldExceed: String get() = t("با این سفارش از سقفِ نقدش می‌گذرد", "This order would put them over the cash cap", "له دې فرمایش سره د نغدو له سقف تېرېږي")
  val dlOverride: String get() = t("آگاهانه بالای سقف بسپار — تا تسویه، این پیک سفارشِ خودکار نمی‌گیرد", "Knowingly assign over the cap — until settled, this courier gets no auto orders", "په پوهه له سقف پورته وسپاره — تر تصفیې پورې دا پیک خپلکاره فرمایش نه اخلي")
  val dlNoCouriersInline: String get() = t("پیکی ثبت نشده — از «کارمندان» کدِ دعوت با نقشِ پیک بسازید.", "No couriers — create a courier invite code in Team.", "پیک نشته — له «کارکوونکو» د پیک د بلنې کوډ جوړ کړئ.")
  val dlCompanyName: String get() = t("نامِ شرکتِ پیک", "Courier company name", "د پیک د شرکت نوم")
  val dlTracking: String get() = t("شمارهٔ رهگیریِ شرکت (اختیاری)", "Company tracking no. (optional)", "د شرکت د تعقیب شمېره (اختیاري)")
  val dlFareField: String get() = t("کرایهٔ این تحویل", "Fare for this delivery", "د دې سپارنې کرایه")
  val dlFareExternal: String get() = t("آن‌چه شرکت برای همین سفارش می‌گیرد", "What the company charges for this order", "هغه چې شرکت د همدې فرمایش لپاره اخلي")
  val dlFareCourier: String get() = t("دستمزدِ پیک برای همین تحویل؛ جدا از نقدِ مشتری", "The courier's pay for this delivery; separate from customer cash", "د همدې سپارنې لپاره د پیک مزد؛ د پېرودونکي له نغدو جلا")
  val dlAutoTitle: String get() = t("سپردنِ خودکار", "Auto-assign", "خپلکاره سپارل")
  val dlAutoHelp: String get() = t(
    "کم‌کارترین پیک انتخاب می‌شود (موقعیتِ پیک هنوز ثبت نمی‌شود). پیکی که با سفارش از سقفِ نقدش بگذرد هرگز خودکار انتخاب نمی‌شود.",
    "The least busy courier is chosen (courier location isn't tracked yet). A courier who'd go over their cash cap is never auto-picked.",
    "تر ټولو لږ بوخت پیک ټاکل کېږي (د پیک ځای لا نه ثبتېږي). هغه پیک چې له فرمایش سره د نغدو له سقف تېرېږي هېڅکله په خپله نه ټاکل کېږي.",
  )
  val dlMaxLive: String get() = t("سقفِ کارِ هم‌زمانِ هر پیک", "Max simultaneous jobs per courier", "د هر پیک د یوځای دندو سقف")
  val dlNoLimit: String get() = t("بی‌سقف", "No limit", "بې سقفه")
  val dlFareEach: String get() = t("کرایهٔ هر تحویل", "Fare per delivery", "د هرې سپارنې کرایه")
  val dlFareEachHelp: String get() = t("دستمزدِ پیک برای هر کدام از این سفارش‌ها", "Courier pay for each of these orders", "د دې هر فرمایش لپاره د پیک مزد")
  val dlUnplaced: String get() = t("جا نشد", "Couldn't place", "ځای یې ونه موند")
  val dlBlockedCouriers: String get() = t("پیک‌هایی که خودکار نمی‌گیرند", "Couriers who won't auto-take", "هغه پیکان چې په خپله نه اخلي")
  val dlNothingToAssign: String get() = t("سفارشی برای سپردن نیست", "Nothing to assign", "د سپارلو لپاره فرمایش نشته")
  fun dlConfirmAuto(n: Int): String = t("تأیید و سپردنِ ${asIs(n)} سفارش", "Confirm and assign $n", "تایید او $n فرمایشونه سپارل")
  fun dlAutoDone(n: Int): String = t("${asIs(n)} سفارش سپرده شد", "$n ${pl(n, "order", "orders")} assigned", "$n فرمایشونه وسپارل شول")
  val dlAutoFailed: String get() = t("سپرده نشد", "Not assigned", "ونه سپارل شول")
  val dlNewCodeNote: String get() = t("کدِ قبلی باطل می‌شود و کدِ تازه فقط یک بار نشان داده می‌شود.", "The old code stops working; the new one is shown only once.", "زوړ کوډ باطلېږي او نوی کوډ یوازې یو ځل ښودل کېږي.")
  val dlRecallNote: String get() = t(
    "پیک خبر می‌گیرد که بسته را برگرداند. اگر پیش از رسیدنِ خبر تحویل داده باشد، همان «تحویل شد» ثبت می‌ماند.",
    "The courier is told to bring the parcel back. If delivered before the message arrives, “delivered” stays.",
    "پیک ته خبر ورکول کېږي چې بسته راوګرځوي. که د خبر تر رسېدو مخکې یې سپارلې وي، هماغه «وسپارل شو» پاتې کېږي.",
  )
  val dlReturnNote: String get() = t("فقط وقتی بسته واقعاً به دکان رسیده بزنید.", "Only when the parcel is really back at the shop.", "یوازې هغه وخت یې ووهئ چې بسته رښتیا دوکان ته رسېدلې وي.")
  val dlCashCollected: String get() = t("نقدِ گرفته‌شده از مشتری", "Cash collected from the customer", "له پېرودونکي اخیستې نغدې")
  fun dlExpected(a: String): String = t("موردِ انتظار: ${asIs(a)}", "Expected: $a", "تمه: $a")
  val dlPaidUsuallyZero: String get() = t("پرداخت‌شده — معمولاً صفر", "Paid — usually zero", "ورکړل شوی — معمولاً صفر")
  val dlCashDiffers: String get() = t("مبلغ با سفارش فرق دارد — دلیل:", "Amount differs from the order — reason:", "مبلغ له فرمایش سره توپیر لري — لامل:")
  val dlCustomerCode: String get() = t("کدِ تحویلِ مشتری", "Customer's delivery code", "د پېرودونکي د سپارنې کوډ")
  val dlNoCodeHelp: String get() = t("مشتری کد ندارد؟ خالی بگذارید و توضیح بنویسید (تأییدِ دستیِ مدیر)", "No code? Leave it empty and explain (manager's manual approval)", "کوډ نشته؟ تش یې پرېږدئ او تشریح ولیکئ (د مدیر لاسي تایید)")
  val dlNoteRequired: String get() = t("توضیح (لازم)", "Explanation (required)", "تشریح (اړینه)")
  val dlNoteOptional: String get() = t("توضیح (اختیاری)", "Explanation (optional)", "تشریح (اختیاري)")
  fun dlRecalled(n: Long): String = t("سفارش #${asIs(n)}: فراخوانی فرستاده شد؛ پیک بسته را برمی‌گرداند", "Order #$n: recall sent; the courier brings the parcel back", "فرمایش #$n: بېرته غوښتنه ولېږل شوه؛ پیک بسته راګرځوي")
  val dlMissionStale: String get() = t("وضعیتِ این مأموریت همین حالا جای دیگری عوض شد؛ میز تازه شد.", "This mission's status just changed elsewhere; the desk refreshed.", "د دې دندې حالت همدا اوس بل ځای بدل شو؛ مېز تازه شو.")
  fun dlCodeTitle(n: Long): String = t("کدِ سفارش #${asIs(n)}", "Code for order #$n", "د فرمایش #$n کوډ")
  val dlCodeHelp: String get() = t(
    "این کد را به مشتری بدهید (پیامک یا تلفن). دوباره نشان داده نمی‌شود؛ اگر گم شد «کدِ تازه» بزنید.",
    "Give this code to the customer (SMS or call). It won't be shown again; if lost, tap “New code”.",
    "دا کوډ پېرودونکي ته ورکړئ (پیغام یا زنګ). بیا نه ښودل کېږي؛ که ورک شو «نوی کوډ» ووهئ.",
  )
  val dlSeen: String get() = t("دیدم", "Got it", "ومې لید")
  val dlCopy: String get() = t("کپی", "Copy", "کاپي")
  val dlCopied: String get() = t("کد کپی شد", "Code copied", "کوډ کاپي شو")
  val dlPickupSearch: String get() = t("شمارهٔ سفارش یا نامِ مشتری", "Order no. or customer name", "د فرمایش شمېره یا د پېرودونکي نوم")
  val dlNoPickups: String get() = t("سفارشِ دریافتِ حضوری نیست", "No pickup orders", "د حضوري اخیستلو فرمایش نشته")
  val dlNoPickupsBody: String get() = t("سفارش‌های «دریافت از دکان» که هنوز تحویل نشده‌اند این‌جا می‌آیند.", "Pickup orders not yet handed over appear here.", "د «له دوکانه اخیستلو» هغه فرمایشونه چې لا نه دي سپارل شوي دلته راځي.")
  fun dlNoPickupMatch(q: String): String = t("با «${asIs(q)}» چیزی پیدا نشد.", "Nothing found for “$q”.", "د «$q» لپاره هېڅ ونه موندل شو.")
  fun dlTakeAtTill(a: String): String = t("در صندوق گرفته شود: ${asIs(a)}", "Collect at the till: $a", "په صندوق کې واخیستل شي: $a")
  val dlNoCodeYet: String get() = t("هنوز کدِ دریافت ساخته نشده", "No pickup code yet", "لا د اخیستو کوډ نه دی جوړ شوی")
  val dlCodeLocked: String get() = t("کد پس از ۵ تلاشِ نادرست قفل شد — «کدِ تازه» یا تأییدِ دستیِ مدیر", "Code locked after 5 wrong tries — “New code” or manager's manual approval", "کوډ له ۵ ناسمو هڅو وروسته قفل شو — «نوی کوډ» یا د مدیر لاسي تایید")
  val dlCodeGiven: String get() = t("کد ساخته و به مشتری داده شده", "Code created and given to the customer", "کوډ جوړ او پېرودونکي ته ورکړل شوی")
  val dlNotReadyYet: String get() = t("تحویل وقتی ممکن است که سفارش «آمادهٔ دریافت» باشد (برگهٔ سفارش‌ها).", "Handover is possible once the order is “ready for pickup” (Orders).", "سپارنه هغه وخت کېږي چې فرمایش «د اخیستو لپاره چمتو» وي (فرمایشونه).")
  val dlMakeCode: String get() = t("ساختنِ کدِ دریافت", "Create pickup code", "د اخیستو کوډ جوړول")
  fun dlCodeExisted(n: Long): String = t("کدِ دریافتِ سفارش #${asIs(n)} پیش‌تر ساخته شده بود؛ برای دیدنش «کدِ تازه» بزنید.", "Order #$n's pickup code was already made; tap “New code” to get one.", "د فرمایش #$n د اخیستو کوډ مخکې جوړ شوی و؛ د لیدو لپاره «نوی کوډ» ووهئ.")
  fun dlNewCodeTitle(n: Long): String = t("کدِ تازه برای سفارش #${asIs(n)}", "New code for order #$n", "د فرمایش #$n لپاره نوی کوډ")
  val dlNewCodeBody: String get() = t("کدِ قبلی باطل می‌شود و قفلِ تلاش‌ها باز می‌شود. کدِ تازه را به مشتری بدهید.", "The old code stops working and the lock is cleared. Give the new code to the customer.", "زوړ کوډ باطلېږي او د هڅو قفل خلاصېږي. نوی کوډ پېرودونکي ته ورکړئ.")
  val dlMake: String get() = t("بساز", "Create", "جوړ یې کړه")
  fun dlHandoverTitle(n: Long): String = t("تحویلِ حضوری — سفارش #${asIs(n)}", "Pickup handover — order #$n", "حضوري سپارنه — فرمایش #$n")
  val dlMatchName: String get() = t("نامِ مشتری را تطبیق دهید:", "Check the customer's name:", "د پېرودونکي نوم پرتله کړئ:")
  val dlCodeShown: String get() = t("کدی که مشتری نشان می‌دهد (۶ رقم)", "Code the customer shows (6 digits)", "هغه کوډ چې پېرودونکی یې ښيي (۶ رقمه)")
  val dlManualApprove: String get() = t("مشتری کد ندارد — تأییدِ دستیِ مدیر (با توضیح، در سابقه)", "Customer has no code — manager's manual approval (with a note, logged)", "پېرودونکی کوډ نه لري — د مدیر لاسي تایید (له تشریح سره، په تاریخچه کې)")
  val dlCashAtTill: String get() = t("نقدِ گرفته‌شده در صندوق", "Cash taken at the till", "په صندوق کې اخیستې نغدې")
  fun dlOrderAmount(a: String): String = t("مبلغِ سفارش: ${asIs(a)}", "Order amount: $a", "د فرمایش مبلغ: $a")
  //  فروشِ تحویل (OrderFulfillment) روی صف و جزئیاتِ سفارش
  fun handoverBlocked(m: String): String = t("تحویل ثبت نشد — ${asIs(m)}", "Handover not sent — $m", "سپارل ونه لېږل شو — $m")
  fun ffReviewCount(n: Int): String = t("${asIs(n)} سفارش بررسیِ صاحب می‌خواهد", "$n orders need the owner's review", "$n فرمایشونه د څښتن کتنې ته اړتیا لري")
  val ffReview: String get() = t("بررسی", "Review", "کتنه")
  fun ffUnpostedCount(n: Int): String = t("${asIs(n)} سفارشِ تحویل‌شده هنوز فروش ندارد — موجودی کم نشده", "$n handed-over orders have no sale yet — stock not reduced", "$n سپارل شوي فرمایشونه لا پلور نه لري — زېرمه نه ده کمه شوې")
  val ffRetryAll: String get() = t("دوباره ثبت کن", "Record again", "بیا ثبت کړئ")
  fun ffWaitingCount(n: Int): String = t("${asIs(n)} سفارشِ تحویل‌شده منتظرِ دستگاهِ صاحب یا مدیر است", "$n handed-over orders are waiting for the owner's or a manager's device", "$n سپارل شوي فرمایشونه د څښتن یا مدیر وسیلې ته انتظار باسي")
  val ffWaitingBody: String get() = t("این حساب فروش یا لغوِ این‌ها را نمی‌تواند ثبت کند (اجازه یا سقفِ تخفیف)؛ با باز شدنِ برنامه روی دستگاهِ صاحب یا مدیر خودکار ثبت می‌شود.", "This account can't record these sales or cancellations (permission or discount cap); they're recorded automatically when the app opens on the owner's or a manager's device.", "دا حساب د دوی پلور یا لغوه نشي ثبتولی (اجازه یا د تخفیف حد)؛ د څښتن یا مدیر په وسیله د اپ په پرانیستو سره خپلکاره ثبتیږي.")
  fun ffShortCount(n: Int): String = t("موجودیِ ${asIs(n)} کالا پس از تحویل منفی شد — ورودِ انبار را درست کنید", "Stock of $n products went negative after handover — fix the stock entry", "د $n توکو زېرمه له سپارلو وروسته منفي شوه — د زېرمې ننوتنه سمه کړئ")
  fun ffSold(label: String): String = t("فروشِ تحویل: فاکتور #${asIs(label)} — کالایی که پیک برگرداند ⇒ همین سفارش را «برگشت به دکان» بزنید (فروش خودش لغو می‌شود)؛ «مرجوعی» نزنید", "Handover sale: invoice #$label — goods the courier brings back ⇒ mark this order \"Returned to shop\" (the sale cancels itself); don't use \"Return\"", "د سپارلو پلور: بل #$label — هغه توکي چې پیک یې راګرځوي ⇒ همدا فرمایش «دوکان ته راګرځېدلی» کړئ (پلور پخپله لغوه کیږي)؛ «بېرته ورکړه» مه کاروئ")
  val ffReviewTitle: String get() = t("بررسیِ صاحب — فروشِ این سفارش خودکار ثبت نمی‌شود", "Owner review — this order's sale isn't recorded automatically", "د څښتن کتنه — د دې فرمایش پلور خپلکاره نه ثبتیږي")
  val ffAck: String get() = t("بررسی شد", "Reviewed", "وکتل شو")
  val ffNoSaleTitle: String get() = t("فروشِ این سفارش هنوز در دفترِ دکان نیست — موجودی کم نشده", "This order's sale isn't in the shop ledger yet — stock not reduced", "د دې فرمایش پلور لا د دوکان په دفتر کې نشته — زېرمه نه ده کمه شوې")
  val ffNoSaleBody: String get() = t("کالا از دکان بیرون رفته؛ فروش را با قیمت‌های همین سفارش ثبت کنید.", "The goods have left the shop; record the sale at this order's prices.", "توکي له دوکانه وتلي؛ پلور د همدې فرمایش په بیو ثبت کړئ.")
  val ffRecord: String get() = t("ثبتِ فروش", "Record sale", "پلور ثبت کړئ")
  val ffReverseTitle: String get() = t("کالای این سفارش به دکان برگشته ولی فروشش هنوز لغو نشده", "This order's goods are back in the shop but its sale isn't cancelled yet", "د دې فرمایش توکي دوکان ته راګرځېدلي خو پلور یې لا نه دی لغوه شوی")
  val ffReverseBody: String get() = t("لغوِ همان فاکتور موجودی را برمی‌گرداند؛ پولی از دخل جابه‌جا نمی‌شود.", "Cancelling that invoice restores the stock; no money moves from the till.", "د همغه بل لغوه زېرمه بېرته راولي؛ له دخل څخه پیسې نه خوځیږي.")
  val ffReverse: String get() = t("لغوِ فروشِ تحویل", "Cancel handover sale", "د سپارلو پلور لغوه کړئ")
  fun ffSaved(n: Long, back: Boolean): String = if (back) t("فروشِ سفارش #${asIs(n)} لغو شد؛ کالا به موجودی برگشت و همگام می‌شود", "Order #$n's sale cancelled; goods back in stock, syncing", "د فرمایش #$n پلور لغوه شو؛ توکي زېرمې ته راستانه شول او همغږي کیږي")
    else t("فروشِ سفارش #${asIs(n)} ثبت شد؛ موجودی کم شد و همگام می‌شود", "Order #$n's sale recorded; stock reduced, syncing", "د فرمایش #$n پلور ثبت شو؛ زېرمه کمه شوه او همغږي کیږي")
  fun ffAcked(n: Long): String = t("سفارش #${asIs(n)}: «بررسی شد» ثبت شد؛ از فهرستِ همهٔ دستگاه‌ها رفت", "Order #$n: \"Reviewed\" saved; removed from every device's list", "فرمایش #$n: «وکتل شو» ثبت شو؛ د ټولو وسیلو له لېست څخه لاړ")
  fun dlSaleNoHook(n: Long): String = t("سفارش #${asIs(n)} تحویل شد؛ فروشش را برنامه از «سفارش‌ها ⇐ تحویلِ بی‌فروش» ثبت می‌کند.", "Order #$n handed over; the app records its sale from \"Orders ⇐ Handed over without sale\".", "فرمایش #$n وسپارل شو؛ پلور یې اپ د «فرمایشونه ⇐ بې پلوره سپارل» څخه ثبتوي.")
  fun dlSaleRecorded(n: Long): String = t("سفارش #${asIs(n)} تحویل و فروشش ثبت شد.", "Order #$n handed over and its sale recorded.", "فرمایش #$n وسپارل شو او پلور یې ثبت شو.")
  fun dlSaleAlready(n: Long): String = t("سفارش #${asIs(n)} تحویل شد؛ فروشش پیش‌تر ثبت شده بود.", "Order #$n handed over; its sale was already recorded.", "فرمایش #$n وسپارل شو؛ پلور یې مخکې ثبت شوی و.")
  fun dlSaleFailed(n: Long, m: String): String = t("سفارش #${asIs(n)} تحویل شد؛ فروش همین حالا ثبت نشد (${asIs(m)}) — برنامه دوباره می‌کوشد، در صندوق دستی نزنید.", "Order #$n handed over; the sale wasn't recorded just now ($m) — the app will retry, don't enter it at the till.", "فرمایش #$n وسپارل شو؛ پلور اوس ثبت نشو ($m) — اپ بیا هڅه کوي، په صندوق کې یې لاسي مه ثبتوئ.")
  fun dlSaleNeedsReview(n: Long, m: String): String = t("سفارش #${asIs(n)} تحویل شد؛ فروشش خودکار ثبت نمی‌شود — ${asIs(m)}", "Order #$n handed over; its sale won't be recorded automatically — $m", "فرمایش #$n وسپارل شو؛ پلور یې خپلکاره نه ثبتیږي — $m")

  // ── CRM-07: سفارش‌های باز/لغوشدهٔ مشتری در پروندهٔ او (`CustomerOnlineOrdersPanel`) ──────────
  fun customerOrders(n: Any): String = t("سفارش‌های اینترنتیِ باز یا لغوشده (${asIs(n)})", "Open or cancelled online orders ($n)", "پرانیستي یا لغوه شوي آنلاین فرمایشونه ($n)")
  fun customerOrderLine(no: Any, amount: Any, ago: Any): String = t("#${asIs(no)} · ${asIs(amount)} ؋ · ${asIs(ago)}", "#$no · $amount ؋ · $ago", "#$no · $amount ؋ · $ago")
  val customerOrdersMore: String get() = t("سفارش‌های بیشتری هست — در «سفارش‌ها» ببینید", "There are more — see Orders", "نور هم شته — په «فرمایشونه» کې یې وګورئ")
  val customerOrderOpen: String get() = t("دیدنِ همین سفارش", "Open this order", "همدا فرمایش وګورئ")

  // ── جدول‌های ترجمهٔ واژه‌های موتور ───────────────────────────────────────────
  private val STATUS_EN = mapOf(
    "new" to "New", "accepted" to "Accepted", "rejected" to "Rejected", "auto_rejected" to "Auto-rejected",
    "cancelled_by_customer" to "Cancelled by customer", "picking" to "Picking", "packed" to "Packed",
    "ready_for_pickup" to "Ready for pickup", "out_for_delivery" to "Out for delivery", "delivered" to "Delivered",
    "delivery_failed" to "Delivery failed", "retry_scheduled" to "Retry scheduled", "returned_to_store" to "Returned to shop",
    "restocked" to "Restocked", "cancelled" to "Cancelled", "picked_up" to "Picked up by customer",
    "return_requested" to "Return requested", "refunded_partial" to "Partially refunded", "refunded_full" to "Fully refunded",
  )
  private val STATUS_PS = mapOf(
    "new" to "نوی", "accepted" to "منل شوی", "rejected" to "رد شوی", "auto_rejected" to "په خپله رد شوی",
    "cancelled_by_customer" to "د پېرودونکي له خوا لغوه", "picking" to "د راټولولو په حال کې", "packed" to "بسته شوی",
    "ready_for_pickup" to "په دوکان کې د سپارلو لپاره چمتو", "out_for_delivery" to "په لاره کې", "delivered" to "سپارل شوی",
    "delivery_failed" to "ونه سپارل شو", "retry_scheduled" to "بیا هڅه", "returned_to_store" to "دوکان ته راستون",
    "restocked" to "زېرمتون ته راستون", "cancelled" to "لغوه شوی", "picked_up" to "پېرودونکي واخیست",
    "return_requested" to "د راستنولو غوښتنه", "refunded_partial" to "برخه‌یي بېرته ورکړه", "refunded_full" to "بشپړه بېرته ورکړه",
  )
  private val ACTION_EN = mapOf(
    "accepted" to "Accept", "rejected" to "Reject", "auto_rejected" to "Auto-reject", "cancelled_by_customer" to "Customer cancelled",
    "picking" to "Start picking", "packed" to "Packed", "ready_for_pickup" to "Ready for pickup", "out_for_delivery" to "Sent out",
    "delivered" to "Delivered", "delivery_failed" to "Not delivered", "retry_scheduled" to "Retry", "returned_to_store" to "Back at shop",
    "restocked" to "Back to stock", "cancelled" to "Cancel", "picked_up" to "Customer picked up", "return_requested" to "Return request",
    "refunded_partial" to "Partial refund", "refunded_full" to "Full refund",
  )
  private val ACTION_PS = mapOf(
    "accepted" to "منل", "rejected" to "ردول", "auto_rejected" to "په خپله ردول", "cancelled_by_customer" to "پېرودونکي لغوه کړ",
    "picking" to "راټولول پیل", "packed" to "بسته شو", "ready_for_pickup" to "د سپارلو لپاره چمتو", "out_for_delivery" to "ولېږل شو",
    "delivered" to "وسپارل شو", "delivery_failed" to "ونه سپارل شو", "retry_scheduled" to "بیا هڅه", "returned_to_store" to "دوکان ته راستون",
    "restocked" to "زېرمتون ته راستون", "cancelled" to "لغوه", "picked_up" to "پېرودونکي واخیست", "return_requested" to "د راستنولو غوښتنه",
    "refunded_partial" to "برخه‌یي بېرته ورکړه", "refunded_full" to "بشپړه بېرته ورکړه",
  )
  private val REASON_EN = mapOf(
    "out_of_stock" to "Out of stock", "closed" to "Shop is closed", "busy" to "Busy", "out_of_area" to "Out of area",
    "other" to "Other reason", "timeout" to "Timed out", "customer_request" to "Customer request", "no_show" to "Customer didn't come",
    "no_answer" to "No answer", "wrong_address" to "Wrong address", "refused" to "Customer refused",
    "recalled" to "Shop recalled after the courier left",
  )
  private val REASON_PS = mapOf(
    "out_of_stock" to "نشته", "closed" to "دوکان تړلی دی", "busy" to "بوخت", "out_of_area" to "له سیمې بهر",
    "other" to "بل لامل", "timeout" to "وخت پای ته ورسېد", "customer_request" to "د پېرودونکي غوښتنه", "no_show" to "پېرودونکی رانغی",
    "no_answer" to "ځواب یې ورنکړ", "wrong_address" to "ناسمه پته", "refused" to "پېرودونکي ونه مانه",
    "recalled" to "دوکان د پیک له تګ وروسته بېرته وغوښت",
  )
  private val TAB_EN = mapOf(
    "new" to "New", "preparing" to "Preparing", "ready" to "Ready", "on_the_way" to "On the way", "scheduled" to "Scheduled", "done" to "Done",
  )
  private val TAB_PS = mapOf(
    "new" to "نوي", "preparing" to "چمتو کېږي", "ready" to "چمتو", "on_the_way" to "په لاره کې", "scheduled" to "مهال‌ټاکلي", "done" to "پای ته رسېدلي",
  )
  private val SOURCE_EN = mapOf(
    "phone" to "Phone", "telegram" to "Telegram", "whatsapp" to "WhatsApp", "manual" to "Manual", "web" to "Storefront", "storefront" to "Storefront",
  )
  private val SOURCE_PS = mapOf(
    "phone" to "تلیفوني", "telegram" to "ټلګرام", "whatsapp" to "واټس‌اپ", "manual" to "لاسي", "web" to "ویترین", "storefront" to "ویترین",
  )
  private val COD_EN = mapOf(
    "to_collect" to "To collect", "collected_by_courier" to "Collected by courier", "collected_in_store" to "Collected in shop",
    "settled_to_shop" to "Settled to shop",
  )
  private val COD_PS = mapOf(
    "to_collect" to "د اخیستو په تمه", "collected_by_courier" to "پیک واخیست", "collected_in_store" to "په دوکان کې واخیستل شو",
    "settled_to_shop" to "دوکان ته تصفیه شو",
  )
  private val MISSION_EN = mapOf(
    "requested" to "Awaiting courier", "assigned" to "Assigned", "picked_up" to "Picked up the parcel", "moving" to "On the way",
    "arrived" to "Arrived", "delivered" to "Delivered", "failed" to "Failed", "rejected" to "Courier declined",
    "withdrawn" to "Withdrawn", "returned" to "Returned to shop",
  )
  private val MISSION_PS = mapOf(
    "requested" to "د پیک د منلو په تمه", "assigned" to "پیک ته وسپارل شو", "picked_up" to "بسته یې واخیسته", "moving" to "په لاره کې",
    "arrived" to "ورسېد", "delivered" to "وسپارل شو", "failed" to "ناکام", "rejected" to "پیک رد کړ",
    "withdrawn" to "بېرته واخیستل شو", "returned" to "دوکان ته راستون",
  )
  private val MODE_EN = mapOf("shop_courier" to "Shop courier", "external" to "External company", "pickup" to "In-store pickup", "platform" to "Courier network")
  private val MODE_PS = mapOf("shop_courier" to "د دوکان پیک", "external" to "بهرنی شرکت", "pickup" to "حضوري اخیستل", "platform" to "د پیکانو شبکه")
  private val DREASON_EN = mapOf(
    "busy" to "Busy", "too_far" to "Too far", "cash_limit" to "Cash cap", "reassign" to "Give to someone else",
    "order_cancelled" to "Order cancelled", "customer_request" to "Customer request", "no_answer" to "Customer absent / no answer",
    "wrong_address" to "Address not found", "refused" to "Customer refused", "recalled" to "Recalled", "out_of_stock" to "Item ran out",
    "wrong_order" to "Wrong order", "partial_payment" to "Partial payment", "refused_amount" to "Refused the amount",
    "damaged_or_short" to "Damaged or short", "other" to "Other (explain)",
  )
  private val DREASON_PS = mapOf(
    "busy" to "بوخت دی", "too_far" to "ډېر لرې دی", "cash_limit" to "د نغدو سقف", "reassign" to "بل چا ته سپارل",
    "order_cancelled" to "فرمایش لغوه شو", "customer_request" to "د پېرودونکي غوښتنه", "no_answer" to "پېرودونکی نه و / ځواب یې ورنکړ",
    "wrong_address" to "پته ونه موندل شوه", "refused" to "پېرودونکي ونه مانه", "recalled" to "بېرته وغوښتل شو", "out_of_stock" to "توکی خلاص شو",
    "wrong_order" to "ناسم فرمایش", "partial_payment" to "نیمګړې ورکړه", "refused_amount" to "مبلغ یې ونه مانه",
    "damaged_or_short" to "زیانمن یا کم توکي", "other" to "بل (له تشریح سره)",
  )
  /** موتور برای کدِ ناشناخته خودِ کد را برمی‌گرداند (`fa == id`)؛ همان کد در هر سه زبان می‌ماند، نه «ترجمهٔ جاافتاده» */
  private fun coded(id: String, fa: String, en: String?, ps: String?): String = if (fa == id) t(asIs(id), id, id) else t(fa, en, ps)

  private val DESK_EN = mapOf(
    DeskActions.Act.PICKUP_HANDOVER to "Hand over with code", DeskActions.Act.NEW_CODE to "New code", DeskActions.Act.WITHDRAW to "Withdraw",
    DeskActions.Act.HAND_TO_COMPANY to "Handed to company", DeskActions.Act.DELIVER to "Delivered", DeskActions.Act.FAIL to "Failed",
    DeskActions.Act.RETRY to "Retry", DeskActions.Act.RETURN to "Parcel returned", DeskActions.Act.RECALL to "Recall", DeskActions.Act.FIX_ADDRESS to "Fix address",
  )
  private val DESK_PS = mapOf(
    DeskActions.Act.PICKUP_HANDOVER to "په کوډ سپارل", DeskActions.Act.NEW_CODE to "نوی کوډ", DeskActions.Act.WITHDRAW to "بېرته اخیستل",
    DeskActions.Act.HAND_TO_COMPANY to "شرکت ته ورکړل شو", DeskActions.Act.DELIVER to "وسپارل شو", DeskActions.Act.FAIL to "ناکام",
    DeskActions.Act.RETRY to "بیا هڅه", DeskActions.Act.RETURN to "بسته راستنه شوه", DeskActions.Act.RECALL to "بېرته غوښتل", DeskActions.Act.FIX_ADDRESS to "پته سمول",
  )

  // ── WEB-24: بنرهای زمان‌بندی‌شدهٔ ویترین (برگهٔ «بنرها») ────────────────────────
  val sfTabBanners: String get() = t("بنرها", "Banners", "بنرونه")
  val bnSection: String get() = t("بنرهای زمان‌بندی‌شده", "Scheduled banners", "مهال‌وېشل شوي بنرونه")
  fun bnHelp(max: Int): String = t(
    "بالای کاتالوگِ ویترین، فقط در همان روزها و همان ناحیه‌ها دیده می‌شوند و خودبه‌خود نمی‌چرخند. حداکثر ${asIs(max)} بنر.",
    "Shown above the storefront catalogue only on those days and in those zones, without auto-rotation. Up to $max banners.",
    "د ویترین د لړلیک پورته یوازې په هماغو ورځو او سیمو کې ښکاري او پخپله نه اوړي. تر $max بنرونو پورې.",
  )
  val bnEmpty: String get() = t("هنوز بنری نیست.", "No banners yet.", "تر اوسه بنر نشته.")
  val bnAdd: String get() = t("افزودنِ بنر", "Add banner", "بنر زیاتول")
  fun bnN(i: Int): String = t("بنرِ ${asIs(i)}", "Banner $i", "بنر $i")
  val bnTitle: String get() = t("عنوان", "Title", "سرلیک")
  val bnBody: String get() = t("متن (اختیاری)", "Text (optional)", "متن (اختیاري)")
  val bnFrom: String get() = t("از روزِ", "From", "له ورځې")
  val bnTo: String get() = t("تا روزِ (خودش هم)", "Until (inclusive)", "تر ورځې (پخپله هم)")
  val bnWeek: String get() = t("یک هفته", "One week", "یوه اونۍ")
  val bnMonth: String get() = t("یک ماه", "One month", "یوه میاشت")
  val bnLink: String get() = t("پیوند", "Link", "لینک")
  val bnLinkNone: String get() = t("بی پیوند", "No link", "بې لینک")
  val bnLinkProduct: String get() = t("کالا", "Product", "توکی")
  val bnLinkCategory: String get() = t("دسته", "Category", "کټګوري")
  val bnFindProduct: String get() = t("کالای ویترین را بجویید", "Search a storefront product", "د ویترین توکی ولټوئ")
  val bnNoProducts: String get() = t("کالای منتشرشده‌ای با این نام نیست", "No published product with this name", "په دې نوم خپور شوی توکی نشته")
  fun bnLinkedTo(name: String): String = t("پیوند به: ${asIs(name)}", "Links to: $name", "لینک ته: $name")
  val bnCategory: String get() = t("نامِ دسته (همان نامِ ویترین)", "Category name (as in the storefront)", "د کټګورۍ نوم (لکه په ویترین کې)")
  val bnZones: String get() = t("کجا دیده شود", "Where to show", "چېرته وښودل شي")
  val bnEverywhere: String get() = t("همه‌جا", "Everywhere", "هرچېرې")
  fun bnSort(max: Int): String = t("اولویت (۰ تا ${asIs(max)}؛ بیشتر جلوتر)", "Priority (0 to $max; higher first)", "لومړیتوب (۰ تر $max؛ لوړ مخکې)")
  val bnRemove: String get() = t("برداشتنِ بنر", "Remove banner", "بنر لرې کول")
  val bnRemoved: String get() = t("بنر برداشته شد", "Banner removed", "بنر لرې شو")
  val bnSave: String get() = t("ذخیرهٔ بنرها", "Save banners", "بنرونه خوندي کول")
  val bnSaved: String get() = t("بنرها ذخیره شد", "Banners saved", "بنرونه خوندي شول")
  val bnLoadFailed: String get() = t("بنرها نیامد", "Couldn't load banners", "بنرونه رانغلل")
  val bnLive: String get() = t("اکنون دیده می‌شود", "Showing now", "اوس ښکاري")
  val bnLater: String get() = t("هنوز نرسیده", "Not started yet", "لا نه دی پیل شوی")
  val bnEnded: String get() = t("تمام شده", "Ended", "پای ته رسېدلی")
  val bnZoneGone: String get() = t("ناحیهٔ برداشته‌شده", "Removed zone", "لرې شوې سیمه")
  val bnErrTitle: String get() = t("عنوان لازم است", "A title is required", "سرلیک اړین دی")
  fun bnErrTooLong(max: Int): String = t("حداکثر ${asIs(max)} حرف", "At most $max characters", "تر $max تورو پورې")
  val bnErrDate: String get() = t("تاریخ خوانا نیست", "The date can't be read", "نېټه نه لوستل کېږي")
  val bnErrEndBefore: String get() = t("«تا» پیش از «از» است", "“Until” is before “From”", "«تر» له «له» مخکې دی")
  fun bnErrSpan(days: Int): String = t("حداکثر ${asIs(days)} روز", "At most $days days", "تر $days ورځو پورې")
  val bnErrProduct: String get() = t("کالای پیوند را برگزینید", "Choose the linked product", "د لینک توکی وټاکئ")
  val bnErrCategory: String get() = t("نامِ دسته لازم است", "A category name is required", "د کټګورۍ نوم اړین دی")
  fun bnErrSort(max: Int): String = t("عددِ ۰ تا ${asIs(max)}", "A number from 0 to $max", "له ۰ تر $max پورې شمېره")
  val bnErrZonesGone: String get() = t(
    "ناحیه‌های این بنر برداشته شده‌اند؛ ناحیه‌ای برگزینید یا «همه‌جا»",
    "This banner's zones were removed; choose a zone or “Everywhere”",
    "د دې بنر سیمې لرې شوې؛ یوه سیمه یا «هرچېرې» وټاکئ",
  )
  fun bnErrTooMany(max: Int): String = t("حداکثر ${asIs(max)} بنر", "At most $max banners", "تر $max بنرونو پورې")

  // ── CHK-10/S34: «خبرم کن» ِ بیرونِ محدوده در «ارسال و زمان» ──────────────────────
  val arTitle: String get() = t("«خبرم کن» ِ بیرونِ محدوده", "“Notify me” outside the zones", "له سیمو بهر «خبر مې کړه»")
  val arHelp: String get() = t(
    "شماره فقط برای محلی دیده می‌شود که اکنون ناحیه‌ای پوشانده است. سرور پیامی نمی‌فرستد؛ خودتان زنگ بزنید یا پیام بدهید و بعد «خبر دادم» را بزنید.",
    "A number is shown only for a place a zone now covers. The server sends no message; call or message them yourself, then tap “Notified”.",
    "شمېره یوازې د هغه ځای لپاره ښکاري چې اوس یوه سیمه یې پوښي. سرور پیغام نه لېږي؛ پخپله زنګ ووهئ یا پیغام ورکړئ او بیا «خبر مې ورکړ» ووهئ.",
  )
  fun arSummary(total: Int, covered: Int): String = t(
    "${asIs(total)} درخواست؛ ${asIs(covered)} تا اکنون در ناحیهٔ ارسال است",
    "$total ${pl(total, "request", "requests")}; $covered now inside a delivery zone",
    "$total غوښتنې؛ $covered یې اوس د لېږد په سیمه کې دي",
  )
  val arNoContacts: String get() = t(
    "کسی برای خبر دادن نیست؛ وقتی ناحیه‌ای محلِ درخواستی را بپوشاند، شماره‌اش این‌جا می‌آید.",
    "No one to notify yet; when a zone covers a requested place, the number appears here.",
    "تر اوسه څوک د خبرولو لپاره نشته؛ کله چې یوه سیمه د غوښتنې ځای وپوښي، شمېره یې دلته راځي.",
  )
  fun arContactSub(zone: String, ago: String): String = t("ناحیهٔ «${asIs(zone)}» · ${asIs(ago)}", "Zone “$zone” · $ago", "سیمه «$zone» · $ago")
  val arNotified: String get() = t("خبر دادم", "Notified", "خبر مې ورکړ")
  val arNotifiedDone: String get() = t("ثبت شد؛ از فهرست برداشته شد", "Saved; removed from the list", "ثبت شو؛ له لېست څخه لرې شو")
  val arCellsTitle: String get() = t("تقاضا به ازای محل (خانهٔ ۳۰۰ متری، بی شماره)", "Demand by place (300 m cells, no numbers)", "د ځای له مخې غوښتنه (۳۰۰ متره خونې، بې شمېرې)")
  fun arCell(count: Int, lat: String, lng: String): String = t(
    "${asIs(count)} درخواست نزدیکِ ${asIs(lat)}، ${asIs(lng)}",
    "$count ${pl(count, "request", "requests")} near $lat, $lng",
    "$count غوښتنې د $lat، $lng ته نږدې",
  )
  fun arCovered(zone: String): String = t("اکنون در ناحیهٔ «${asIs(zone)}»", "Now in zone “$zone”", "اوس د «$zone» په سیمه کې")
  val arOutside: String get() = t("هنوز بیرون از ناحیه‌ها", "Still outside all zones", "لا هم له ټولو سیمو بهر")
  val arLoadFailed: String get() = t("فهرستِ «خبرم کن» نیامد", "Couldn't load the “notify me” list", "د «خبر مې کړه» لېست رانغی")
}
