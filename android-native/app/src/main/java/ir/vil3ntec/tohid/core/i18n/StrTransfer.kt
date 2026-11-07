package ir.vil3ntec.tohid.core.i18n

/**
 *  INV-11 — انتقالِ موجودی بین قفسه و انبار با «در مسیر»، دریافتِ ناقص و دریافت با اسکن (`ui/v3/stock/TransferPages`).
 *
 *  قاعده‌ها سرِ `I18n.kt`. ⛔ یادداشتِ ردیف‌های دفتر («انتقالِ موجودی: …»، `StockTransferBook.notes`) و پیامِ ردِ سرور
 *  داده‌اند و همان فارسی می‌مانند.
 */
@Suppress("unused")
object StrTransfer {

  val title: String get() = t("انتقالِ موجودی", "Stock transfers", "د موجودي لېږد")
  val subtitle: String get() = t(
    "بین قفسهٔ فروشگاه و انبار، با «در مسیر» و دریافت در مقصد",
    "Between the shop floor and the store room, tracked in transit and received at the destination",
    "د پلورنځي المارۍ او زېرمې ترمنځ، «په لاره کې» او په منزل کې ترلاسه",
  )
  val shopFloor: String get() = t("قفسهٔ فروشگاه", "Shop floor", "د پلورنځي المارۍ")
  val warehouse: String get() = t("انبار", "Store room", "زېرمه")
  val from: String get() = t("از", "From", "له")
  val to: String get() = t("به", "To", "ته")
  val swap: String get() = t("جابه‌جاییِ مبدأ و مقصد", "Swap origin and destination", "د سرچینې او منزل بدلول")
  fun route(from: Any, to: Any): String = t("${asIs(from)} ⇐ ${asIs(to)}", "$from → $to", "$from ⇐ $to")
  val newTransfer: String get() = t("انتقالِ تازه", "New transfer", "نوی لېږد")
  val refresh: String get() = t("تازه کردن", "Refresh", "تازه کول")

  // ── فهرست ──
  val openSection: String get() = t("در مسیر", "In transit", "په لاره کې")
  val closedSection: String get() = t("بسته‌شده", "Closed", "تړل شوي")
  val inTransit: String get() = t("در مسیر", "In transit", "په لاره کې")
  val partial: String get() = t("نیمه‌رسیده", "Partly received", "نیمګړی رسېدلی")
  val received: String get() = t("رسید", "Received", "ورسېد")
  val rejected: String get() = t("رد و برگشت", "Rejected and returned", "رد او بېرته")
  val refused: String get() = t("سرور نپذیرفت", "Not accepted by the server", "سرور ونه منله")
  val queued: String get() = t("در صف — پس از وصل شدن ثبت می‌شود", "Queued — recorded once online", "په کتار کې — له نښلېدو وروسته ثبتېږي")
  val queuedPill: String get() = t("در صف", "Queued", "په کتار کې")
  fun offline(time: Any): String = t(
    "بی اینترنت: آخرین نمای سرور (${asIs(time)})؛ کارِ تازه در صف می‌ماند",
    "Offline: last server view ($time); new work stays queued",
    "پرته له انټرنېټه: د سرور وروستۍ بڼه ($time)؛ نوی کار په کتار کې پاتې کېږي",
  )
  val neverLoaded: String get() = t(
    "هنوز از سرور خوانده نشده است؛ کارِ تازه در صف می‌ماند",
    "Not loaded from the server yet; new work stays queued",
    "لا له سرور نه دی لوستل شوی؛ نوی کار په کتار کې پاتې کېږي",
  )
  val empty: String get() = t("انتقالی نیست", "No transfers", "لېږد نشته")
  val emptyBody: String get() = t(
    "کالا را از قفسه به انبار (یا برعکس) بفرستید؛ مقصد با اسکن دریافت می‌کند.",
    "Send goods from the shelf to the store room (or back); the destination receives them by scanning.",
    "توکي له المارۍ زېرمې ته (یا برعکس) ولېږئ؛ منزل یې په سکن ترلاسه کوي.",
  )
  fun lineCount(n: Any, pending: Any): String = t("${asIs(n)} کالا · در مسیر: ${asIs(pending)}", "$n items · in transit: $pending", "$n توکي · په لاره کې: $pending")
  fun refusedBecause(reason: Any): String = t("رد شد: ${asIs(reason)}", "Refused: $reason", "رد شو: $reason")

  // ── پیش‌نویس ──
  val addProduct: String get() = t("افزودنِ کالا", "Add product", "توکی زیاتول")
  val scanHint: String get() = t("اسکن یا بارکد — هر اسکن یک عدد", "Scan or type a barcode — one unit per scan", "سکن یا بارکوډ — هر سکن یو عدد")
  val camera: String get() = t("دوربین", "Camera", "کمره")
  val note: String get() = t("یادداشت (اختیاری)", "Note (optional)", "یادښت (اختیاري)")
  val qty: String get() = t("مقدار", "Quantity", "اندازه")
  val remove: String get() = t("برداشتن", "Remove", "لرې کول")
  val send: String get() = t("ارسال", "Send", "لېږل")
  val sent: String get() = t("ارسال ثبت شد", "Transfer sent", "لېږد ثبت شو")
  val draftKept: String get() = t("پیش‌نویس روی همین دستگاه می‌ماند تا ارسال کنید", "The draft stays on this device until you send it", "مسوده تر لېږلو پورې په همدې وسیله پاتې کېږي")
  val discardDraft: String get() = t("دور ریختنِ پیش‌نویس", "Discard draft", "مسوده لرې کول")
  fun draftSummary(n: Any): String = t("${asIs(n)} کالا", "$n items", "$n توکي")

  // ── دریافت ──
  val receiveTitle: String get() = t("دریافتِ انتقال", "Receive transfer", "د لېږد ترلاسه کول")
  fun lineState(sent: Any, got: Any, pending: Any): String = t(
    "فرستاده ${asIs(sent)} · رسیده ${asIs(got)} · در مسیر ${asIs(pending)}",
    "Sent $sent · received $got · in transit $pending",
    "لېږل شوي $sent · رسېدلي $got · په لاره کې $pending",
  )
  fun shortLine(short: Any): String = t("کسری: ${asIs(short)}", "Short: $short", "کمښت: $short")
  val counted: String get() = t("رسیده", "Arrived", "رسېدلي")
  val allArrived: String get() = t("همه رسید", "All arrived", "ټول ورسېدل")
  val partialReceive: String get() = t("ثبتِ رسیده‌ها (بقیه در مسیر)", "Record arrivals (rest stays in transit)", "رسېدلي ثبت کړئ (پاتې په لاره کې)")
  val finalReceive: String get() = t("بستن و دریافت", "Close and receive", "تړل او ترلاسه کول")
  fun shortages(n: Any): String = t("کسری در ${asIs(n)} کالا — دلیل لازم است", "Short on $n items — a reason is required", "په $n توکو کې کمښت — دلیل اړین دی")
  val reason: String get() = t("دلیل (کسری یا اختلاف)", "Reason (shortage or discrepancy)", "دلیل (کمښت یا توپیر)")
  val reject: String get() = t("رد و برگشت به مبدأ", "Reject and return to origin", "رد او سرچینې ته بېرته")
  val rejectReason: String get() = t("دلیلِ رد", "Reason for rejecting", "د رد دلیل")
  val receivedSaved: String get() = t("دریافت ثبت شد", "Receipt recorded", "ترلاسه ثبت شو")
  val rejectedSaved: String get() = t("رد ثبت شد", "Rejection recorded", "رد ثبت شو")
  val notFound: String get() = t("این انتقال پیدا نشد", "Transfer not found", "دا لېږد ونه موندل شو")
  fun scanned(name: Any, q: Any): String = t("«${asIs(name)}»: ${asIs(q)}", "“$name”: $q", "«$name»: $q")
  fun unknown(code: Any): String = t("بارکدِ «${asIs(code)}» مالِ هیچ کالایی نیست", "Barcode “$code” doesn't belong to any product", "بارکوډ «$code» د هېڅ توکي نه دی")
  fun weighed(name: Any): String = t("«${asIs(name)}» وزنی است؛ وزنش را بنویسید", "“$name” is sold by weight; type its weight", "«$name» وزني دی؛ وزن یې ولیکئ")
  fun notInTransfer(name: Any): String = t("«${asIs(name)}» در این انتقال نیست", "“$name” isn't in this transfer", "«$name» په دې لېږد کې نشته")
  fun overScan(name: Any, pending: Any): String = t(
    "«${asIs(name)}»: بیش از ماندهٔ در مسیر (${asIs(pending)}) شمرده نمی‌شود",
    "“$name”: can't count more than what is in transit ($pending)",
    "«$name»: له په لاره کې پاتې ($pending) نه ډېر نه شمېرل کېږي",
  )

  // ── پیام‌های رد ──
  val samePlace: String get() = t("مبدأ و مقصد یکی است", "Origin and destination are the same", "سرچینه او منزل یو دي")
  val needLines: String get() = t("دست‌کم یک کالا بیفزایید", "Add at least one product", "لږ تر لږه یو توکی زیات کړئ")
  fun tooMany(n: Any): String = t("حداکثر ${asIs(n)} کالا در یک انتقال", "At most $n products per transfer", "په یوه لېږد کې تر $n توکو")
  val badQty: String get() = t("مقدارها را درست بنویسید", "Enter valid quantities", "سمې اندازې ولیکئ")
  val over: String get() = t("بیش از ماندهٔ در مسیر نمی‌رسد", "More than what is in transit can't arrive", "له په لاره کې پاتې نه ډېر نه رسېږي")
  val nothing: String get() = t("در این گام چیزی نرسیده است", "Nothing arrived in this step", "په دې ګام کې هېڅ نه دي رسېدلي")
  val needReason: String get() = t("دلیل را بنویسید", "Write the reason", "دلیل ولیکئ")
  val closed: String get() = t("این انتقال دیگر باز نیست (یا گامِ پایانی‌اش در صف است)", "This transfer is no longer open (or its last step is queued)", "دا لېږد نور خلاص نه دی (یا وروستی ګام یې په کتار کې دی)")
  val notSaved: String get() = t("ثبت نشد؛ دوباره بکوشید", "Not saved; try again", "ثبت نه شو؛ بیا هڅه وکړئ")
}
