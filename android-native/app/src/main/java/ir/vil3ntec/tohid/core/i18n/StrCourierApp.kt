package ir.vil3ntec.tohid.core.i18n

import ir.vil3ntec.tohid.core.courier.CourierInputText

/**
 *  حالتِ پیک (پوشهٔ `ui/courier`): خانه، مسیر، آمار، حساب، «نقدِ من»، جزئیاتِ سفارش، تحویل و ورود.
 *  این نوشته‌ها پیش‌تر متنِ خامِ فارسی در خودِ صفحه‌ها بودند و از هیچ زبانی نمی‌گذشتند (دری، انگلیسی و پشتو
 *  همه فارسی می‌دیدند). فارسی کلمه‌به‌کلمه همان پیشین است. قاعده‌ها سرِ `I18n.kt`.
 */
@Suppress("unused")
object StrCourierApp {

  // ── وضعیتِ هر مأموریت (برچسبِ کوتاه) ──
  val stRequested: String get() = t("تازه", "New", "نوې")
  val stAssigned: String get() = t("قبول شد", "Accepted", "ومنل شوه")
  val stPickedUp: String get() = t("بسته گرفته شد", "Picked up", "بسته واخیستل شوه")
  val stMoving: String get() = t("در راه", "On the way", "په لار کې")
  val stArrived: String get() = t("رسیده", "Arrived", "رسېدلی")
  val stFailed: String get() = t("ناموفق", "Failed", "ناکامه")
  val stDelivered: String get() = t("تحویل شد", "Delivered", "وسپارل شوه")
  val stRejected: String get() = t("رد شد", "Rejected", "رد شوه")
  val stWithdrawn: String get() = t("پس گرفته شد", "Withdrawn", "بېرته واخیستل شوه")
  val stReturned: String get() = t("برگشت به دکان", "Returned to shop", "دوکان ته بېرته")

  // ── جملهٔ بزرگِ هر پله ──
  val hlRequested: String get() = t("کارِ تازه — قبول یا رد", "New job — accept or reject", "نوې دنده — منل یا رد")
  val hlAssigned: String get() = t("بسته را از دکان بگیرید", "Pick up the parcel from the shop", "بسته له دوکانه واخلئ")
  val hlPickedUp: String get() = t("بسته دستِ شماست", "The parcel is with you", "بسته ستاسو په لاس کې ده")
  val hlMoving: String get() = t("در راهِ مشتری", "On the way to the customer", "د پېرودونکي په لار کې")
  val hlArrived: String get() = t("پیشِ مشتری هستید", "You are at the customer", "تاسو د پېرودونکي سره یاست")
  val hlFailed: String get() = t("تحویل نشد", "Not delivered", "ونه سپارل شوه")
  val hlRejected: String get() = t("این کار را رد کردید", "You rejected this job", "تاسو دا دنده رد کړه")
  val hlWithdrawn: String get() = t("دکان این کار را پس گرفت", "The shop withdrew this job", "دوکان دا دنده بېرته واخیسته")
  val hlReturned: String get() = t("بسته به دکان برگشت", "The parcel went back to the shop", "بسته دوکان ته بېرته لاړه")

  // ── دکمهٔ هر پله ──
  val acAccept: String get() = t("قبول", "Accept", "منل")
  val acReject: String get() = t("رد", "Reject", "رد")
  val acPickedUp: String get() = t("بسته را گرفتم", "I picked it up", "بسته مې واخیسته")
  val acRetry: String get() = t("تلاشِ دوباره", "Try again", "بیا هڅه")
  val acMoving: String get() = t("راه افتادم", "I'm on my way", "په لار شوم")
  val acArrived: String get() = t("رسیدم", "I've arrived", "ورسېدم")

  // ── دلیلِ رد یا ناموفق ──
  val rsBusy: String get() = t("سرم شلوغ است", "I'm busy", "بوخت یم")
  val rsTooFar: String get() = t("خیلی دور است", "Too far", "ډېر لرې دی")
  val rsCashLimit: String get() = t("سقفِ نقدم پر است", "My cash limit is full", "زما د نغدو سقف ډک دی")
  val rsNoAnswer: String get() = t("تماس بی‌پاسخ / کسی نبود", "No answer / nobody there", "ځواب نه و / څوک نه و")
  val rsWrongAddress: String get() = t("نشانی پیدا نشد", "Address not found", "پته ونه موندل شوه")
  val rsRefused: String get() = t("مشتری نپذیرفت", "Customer refused", "پېرودونکي ونه منله")
  val rsRecalled: String get() = t("دکان پس خواست", "Shop recalled it", "دوکان بېرته وغوښته")
  val rsPartialPayment: String get() = t("مشتری کمتر داد", "Customer paid less", "پېرودونکي لږ ورکړل")
  val rsRefusedAmount: String get() = t("مبلغ را نپذیرفت", "Refused the amount", "مبلغ یې ونه مانه")
  val rsDamagedOrShort: String get() = t("کالا آسیب یا کسری داشت", "Items damaged or short", "توکي زیانمن یا کم وو")
  val rsOther: String get() = t("دلیلِ دیگر (بنویسید)", "Other reason (write it)", "بل دلیل (ولیکئ)")

  val recallNote: String get() = t(
    "دکان این سفارش را پس خواسته است — بسته را برگردانید.",
    "The shop has recalled this order — bring the parcel back.",
    "دوکان دا فرمایش بېرته غوښتی — بسته بېرته راوړئ.",
  )

  // ── نوارِ پیشرفت ──
  val stepGot: String get() = t("گرفتم", "Picked up", "واخیستل")
  val stepDelivered: String get() = t("تحویل", "Delivered", "سپارل")

  // ── همگام‌سازی و صف ──
  val offlineQueued: String get() = t(
    "اینترنت نیست — کارها در صفِ گوشی می‌مانند",
    "No internet — jobs wait in the phone's queue",
    "انټرنټ نشته — کارونه د تلیفون په کتار کې پاتې کېږي",
  )
  val offlineShort: String get() = t("اینترنت نیست", "No internet", "انټرنټ نشته")
  val notSaved: String get() = t("ثبت نشد", "Not saved", "ثبت نه شو")
  val photoQueued: String get() = t("عکسِ بسته در صف است", "The parcel photo is queued", "د بستې انځور په کتار کې دی")
  val dialerFailed: String get() = t("شماره‌گیرِ گوشی باز نشد", "The phone dialer did not open", "د تلیفون شمېره‌ګیر خلاص نه شو")
  val shopPhoneMissing: String get() = t("شمارهٔ دکان هنوز ثبت نشده است", "The shop's number is not set yet", "د دوکان شمېره لا نه ده ثبت شوې")

  // ── «تماس با مشتری» و خطِ «ناحیه · دروازه» (DLV-02؛ فارسی همان رابطِ پیشین) ──
  val callCustomer: String get() = t("تماس با مشتری", "Call the customer", "پېرودونکي ته زنګ")
  val customerPhoneMissing: String get() = t("شمارهٔ مشتری نیامد", "The customer's number did not come through", "د پېرودونکي شمېره رانغله")
  val callOnlineOnly: String get() = td(
    "تماس فقط با اینترنت — شماره روی گوشی نگه داشته نمی‌شود",
    "Calling needs the internet — the number is not kept on the phone",
    "زنګ یوازې له انټرنټ سره — شمېره په تلیفون کې نه ساتل کېږي",
    "Calling needs the internet — the number is not kept on this computer",
    "زنګ یوازې له انټرنټ سره — شمېره په کمپیوټر کې نه ساتل کېږي",
  )
  // ── اعلانِ همیشگیِ سرویسِ موقعیت (DLV-16؛ فقط اندروید، فقط «در راه») ──
  val trackChannel: String get() = t("موقعیتِ پیک در راه", "Courier location on the way", "په لار کې د رسوونکي ځای")
  val trackTitle: String get() = t("موقعیتِ شما برای همین سفارش", "Your location for this order", "ستاسو ځای د همدې فرمایش لپاره")
  val trackLine: String get() = t(
    "فقط تا «رسیدم» و تقریبی به دکان و مشتری می‌رود",
    "Shared roughly with the shop and customer only until “I've arrived”",
    "یوازې تر «ورسېدم» پورې او نږدې‌والي سره دوکان او پېرودونکي ته ځي",
  )
  fun areaLine(area: String): String = t("ناحیه: ${asIs(area)}", "Area: $area", "سیمه: $area")
  fun gateLine(color: String): String = t("دروازهٔ ${asIs(color)}", "Gate: $color", "دروازه: $color")
  val sendingDots: String get() = t("در حالِ فرستادن…", "Sending…", "لېږل کېږي…")
  fun queuedOnPhone(n: String): String = t("${asIs(n)} کار در صفِ گوشی", "$n jobs queued on the phone", "$n کارونه د تلیفون په کتار کې")
  fun queuedShort(n: String): String = t("${asIs(n)} کار در صف", "$n jobs queued", "$n کارونه په کتار کې")
  val notConnected: String get() = t("وصل نیست", "Not connected", "نښلول شوی نه دی")
  val allReachedShop: String get() = t("همه به دکان رسید", "Everything reached the shop", "ټول دوکان ته ورسېدل")
  val allReached: String get() = t("همه رسید", "All delivered to the shop", "ټول ورسېدل")
  val queued: String get() = t("در صف", "Queued", "په کتار کې")
  val seen: String get() = t("دیدم", "Got it", "ومې لید")
  val refresh: String get() = t("به‌روز کردن", "Refresh", "تازه کول")
  fun actionNotSaved(action: String): String = t("«${asIs(action)}» ثبت نشد", "\"$action\" was not saved", "«$action» ثبت نه شو")

  // ── فهرستِ مأموریت‌ها و مسیر ──
  val route: String get() = t("مسیر", "Route", "لار")
  fun tabActive(n: String): String = t("فعال · ${asIs(n)}", "Active · $n", "فعال · $n")
  fun tabFinished(n: String): String = t("پایان‌یافته · ${asIs(n)}", "Finished · $n", "بشپړ شوي · $n")
  val noMissionsTitle: String get() = t("فعلاً مأموریتی ندارید", "No jobs right now", "اوس مهال دنده نه لرئ")
  val noMissionsBody: String get() = t("دکان که بسپارد، همین‌جا دیده می‌شود.", "When the shop assigns one, it shows here.", "کله چې دوکان دنده درکړي، همدلته ښکاري.")
  val noFinishedTitle: String get() = t("هنوز مأموریتی تمام نشده است", "No finished jobs yet", "لا هېڅ دنده نه ده بشپړه شوې")
  val noFinishedBody: String get() = t(
    "کارهای تحویل‌شده و برگشتی این‌جا می‌آیند.",
    "Delivered and returned jobs show here.",
    "سپارل شوې او بېرته راغلې دندې دلته ښکاري.",
  )
  fun orderNo(no: String): String = t("سفارشِ #${asIs(no)}", "Order #$no", "فرمایش #$no")
  fun cashChip(amount: String): String = t("نقد: ${asIs(amount)}", "Cash: $amount", "نغدې: $amount")
  val deliveryCode: String get() = t("کدِ تحویل", "Delivery code", "د سپارلو کوډ")
  fun collectedChip(amount: String): String = t("گرفته شد: ${asIs(amount)}", "Collected: $amount", "واخیستل شوې: $amount")
  fun fareChip(amount: String): String = t("کرایه: ${asIs(amount)}", "Fare: $amount", "کرایه: $amount")

  // ── رد/ناموفق و تحویل ──
  val rejectTitle: String get() = t("ردِ این کار", "Reject this job", "د دې دندې ردول")
  val pickReason: String get() = t("دلیل را انتخاب کنید", "Choose a reason", "دلیل وټاکئ")
  val noteRequired: String get() = t("توضیح (لازم)", "Note (required)", "یادښت (اړین)")
  val noteOptional: String get() = t("توضیح (اختیاری)", "Note (optional)", "یادښت (اختیاري)")
  val save: String get() = t("ثبت", "Save", "ثبتول")
  val cancel: String get() = t("انصراف", "Cancel", "لغوه")
  val deliverTitle: String get() = t("تحویل به مشتری", "Deliver to customer", "پېرودونکي ته سپارل")
  val orderCash: String get() = t("نقدِ این سفارش", "Cash for this order", "د دې فرمایش نغدې")
  val codeAsk: String get() = t("کدِ تحویل — از مشتری بپرسید", "Delivery code — ask the customer", "د سپارلو کوډ — له پېرودونکي یې وپوښتئ")
  val cashTakenField: String get() = t("نقدِ گرفته‌شده (افغانی)", "Cash collected (AFN)", "اخیستل شوې نغدې (افغانۍ)")
  val amountDiffers: String get() = t("مبلغ با سفارش فرق دارد — دلیل:", "The amount differs from the order — reason:", "مبلغ له فرمایش سره توپیر لري — دلیل:")
  val reachedCustomerQ: String get() = t("بسته به مشتری رسید؟", "Did the parcel reach the customer?", "بسته پېرودونکي ته ورسېده؟")
  fun deliveredTook(amount: String): String = t("تحویل شد · ${asIs(amount)} گرفتم", "Delivered · collected $amount", "وسپارل شوه · $amount مې واخیستل")

  // ── خروج ──
  val signOutTitle: String get() = t("خروج از حسابِ پیک", "Sign out of courier account", "د سپارونکي له حساب څخه وتل")
  fun signOutWaiting(n: String): String = t(
    "${asIs(n)} کار هنوز به دکان نرسیده و با خروج از این گوشی پاک می‌شود. اول به اینترنت وصل شوید و «به‌روز کردن» را بزنید.",
    "$n jobs have not reached the shop yet and will be erased from this phone on sign-out. Connect to the internet first and tap \"Refresh\".",
    "$n کارونه لا دوکان ته نه دي رسېدلي او په وتلو سره له دې تلیفون څخه پاکېږي. لومړی انټرنټ ته وصل شئ او «تازه کول» ووهئ.",
  )
  val signOutBody: String get() = t("مأموریت‌ها و نشانی‌ها از این گوشی پاک می‌شوند.", "Jobs and addresses will be erased from this phone.", "دندې او پتې له دې تلیفون څخه پاکېږي.")
  val signOutAnyway: String get() = t("با وجودِ این خارج شو", "Sign out anyway", "بیا هم ووځه")
  val signOut: String get() = t("خروج", "Sign out", "وتل")

  // ── جزئیاتِ سفارش ──
  val evRecall: String get() = t("دکان سفارش را پس خواست", "The shop recalled the order", "دوکان فرمایش بېرته وغوښت")
  val evPhoto: String get() = t("عکسِ بسته ثبت شد", "Parcel photo saved", "د بستې انځور ثبت شو")
  val evCreated: String get() = t("کارِ تازه سپرده شد", "New job assigned", "نوې دنده وسپارل شوه")
  val evAssigned: String get() = t("قبول کردید", "You accepted", "تاسو ومنله")
  val evRejected: String get() = t("رد کردید", "You rejected", "تاسو رد کړه")
  val evPickedUp: String get() = t("بسته را از دکان گرفتید", "You picked up the parcel from the shop", "تاسو بسته له دوکانه واخیسته")
  val evArrived: String get() = t("به مقصد رسیدید", "You arrived at the destination", "تاسو منزل ته ورسېدئ")
  val evDelivered: String get() = t("به مشتری تحویل شد", "Delivered to the customer", "پېرودونکي ته وسپارل شوه")
  val evWithdrawn: String get() = t("دکان پس گرفت", "The shop withdrew it", "دوکان بېرته واخیسته")
  val byPending: String get() = t("در صفِ گوشی — هنوز به دکان نرسیده", "In the phone's queue — not at the shop yet", "د تلیفون په کتار کې — لا دوکان ته نه ده رسېدلې")
  val byCourier: String get() = t("توسطِ شما", "By you", "ستاسو له خوا")
  val byShop: String get() = t("توسطِ دکان", "By the shop", "د دوکان له خوا")
  val tabTrack: String get() = t("جزئیاتِ پیگیری", "Tracking details", "د تعقیب جزییات")
  val tabShipping: String get() = t("جزئیاتِ ارسال", "Delivery details", "د لېږد جزییات")
  val from: String get() = t("از", "From", "له")
  val to: String get() = t("به", "To", "ته")
  val addressHidden: String get() = t(
    "نشانی پس از پایانِ کار دیگر نشان داده نمی‌شود",
    "The address is no longer shown after the job ends",
    "پته د دندې له پای وروسته نور نه ښودل کېږي",
  )
  val navigate: String get() = t("مسیریابی", "Navigate", "لارښوونه")
  val orderNumber: String get() = t("شمارهٔ سفارش", "Order number", "د فرمایش شمېره")
  val timelineLater: String get() = t(
    "خطِ زمانی پس از همگام‌سازی با دکان می‌آید.",
    "The timeline appears after syncing with the shop.",
    "مهالوېش له دوکان سره تر همغږۍ وروسته راځي.",
  )
  val itemsCount: String get() = t("تعدادِ قلم", "Items", "د توکو شمېر")
  val cashToCollect: String get() = t("نقدِ گرفتنی", "Cash to collect", "اخیستونکې نغدې")
  val orderCashLabel: String get() = t("نقدِ سفارش", "Order cash", "د فرمایش نغدې")
  val cash: String get() = t("نقد", "Cash", "نغدې")
  val noCashFromCustomer: String get() = t("از مشتری گرفته نمی‌شود", "Not collected from the customer", "له پېرودونکي نه اخیستل کېږي")
  val collected: String get() = t("گرفته شد", "Collected", "واخیستل شوې")
  val yourFare: String get() = t("کرایهٔ شما", "Your fare", "ستاسو کرایه")
  val askCustomer: String get() = t("از مشتری بپرسید", "Ask the customer", "له پېرودونکي یې وپوښتئ")
  val notNeeded: String get() = t("لازم نیست", "Not needed", "اړین نه دی")
  val parcelPhoto: String get() = t("عکسِ بسته", "Parcel photo", "د بستې انځور")
  val customerConsented: String get() = t("مشتری رضایت داده", "Customer agreed", "پېرودونکي رضایت ورکړی")
  val noPhoto: String get() = t("بی عکس", "No photo", "بې انځوره")
  val customer: String get() = t("مشتری", "Customer", "پېرودونکی")
  fun leaveWith(who: String): String = t("سپردن به: ${asIs(who)}", "Leave with: $who", "ورکړئ: $who")
  val callBefore: String get() = t("پیش از رسیدن خبر دهید", "Call before arriving", "تر رسېدو مخکې خبر ورکړئ")
  val deliveryNote: String get() = t("یادداشتِ تحویل", "Delivery note", "د سپارلو یادښت")
  val maskedPhone: String get() = t("شماره (پوشیده)", "Number (masked)", "شمېره (پټه)")
  val customerErased: String get() = t(
    "مشتری و نشانی پس از پایانِ کار از این گوشی پاک شد.",
    "The customer and address were erased from this phone after the job ended.",
    "پېرودونکی او پته د دندې له پای وروسته له دې تلیفون څخه پاک شول.",
  )
  val failedReturnNote: String get() = t(
    "اگر دوباره نمی‌روید، بسته را به دکان برگردانید؛ برگشت را دکان هنگامِ تحویل گرفتن ثبت می‌کند.",
    "If you are not going again, bring the parcel back to the shop; the shop records the return when it takes the parcel.",
    "که بیا نه ځئ، بسته دوکان ته بېرته راوړئ؛ دوکان بېرته راتګ د بستې د اخیستو پر مهال ثبتوي.",
  )
  val destination: String get() = t("مقصد", "Destination", "منزل")

  // ── ورودِ پیک ──
  val joinTitle: String get() = t("ورودِ پیک", "Courier sign-in", "د سپارونکي ننوتل")
  val joinBody: String get() = t(
    "کدِ دعوتی را که صاحب دکان به شما داده بزنید. ایمیل و رمز لازم نیست.",
    "Enter the invite code the shop owner gave you. No email or password needed.",
    "هغه د بلنې کوډ ولیکئ چې د دوکان خاوند درکړی. برېښنالیک او پټنوم ته اړتیا نشته.",
  )
  val yourName: String get() = t("نام شما", "Your name", "ستاسو نوم")
  val nameExample: String get() = t("مثلاً کریم", "e.g. Karim", "لکه کریم")
  val inviteCode: String get() = t("کدِ دعوت", "Invite code", "د بلنې کوډ")
  val joinOwnOnly: String get() = t("فقط مأموریت‌های خودتان، با نشانه و نشانی", "Only your own jobs, with landmark and address", "یوازې خپلې دندې، له نښې او پتې سره")
  val joinOffline: String get() = t("بی اینترنت هم ثبت می‌شود و بعد خودش می‌رود", "Works offline and sends later by itself", "بې انټرنټه هم ثبتېږي او وروسته پخپله ځي")
  val joinCash: String get() = t("نقدِ هر سفارش جدا ثبت می‌شود", "Each order's cash is recorded separately", "د هر فرمایش نغدې جلا ثبتېږي")
  val join: String get() = t("پیوستن", "Join", "یوځای کېدل")

  // ── خانه، آمار، حساب، «نقدِ من» ──
  val neverSynced: String get() = t("هنوز با دکان همگام نشده است", "Not synced with the shop yet", "لا له دوکان سره همغږی شوی نه دی")
  fun lastSync(date: String, time: String): String = t(
    "آخرین به‌روزرسانی: ${asIs(date)} · ${asIs(time)}",
    "Last update: $date · $time",
    "وروستی تازه کول: $date · $time",
  )
  val asOfUnknown: String get() = t("زمانِ این عددها معلوم نیست", "The time of these figures is unknown", "د دې شمېرو وخت معلوم نه دی")
  fun asOf(date: String, time: String): String = t(
    "تا ${asIs(date)} · ${asIs(time)}، طبقِ سرور",
    "As of $date · $time, per the server",
    "تر $date · $time پورې، د سرور له مخې",
  )
  fun staleNote(error: String): String = t(
    "تازه نشد: ${asIs(error)} — عددها مالِ همان زمانِ پایین‌اند.",
    "Not refreshed: $error — the figures are from the time below.",
    "تازه نه شو: $error — شمېرې د لاندې وخت دي.",
  )
  val courier: String get() = t("پیک", "Courier", "سپارونکی")
  val messages: String get() = t("پیام‌ها", "Messages", "پیغامونه")
  val trackOrder: String get() = t("پیگیریِ سفارش", "Track an order", "د فرمایش تعقیب")
  val orderSearchHint: String get() = t("شمارهٔ سفارش…", "Order number…", "د فرمایش شمېره…")
  val orderNotFound: String get() = t("سفارشی با این شماره یا نشانه پیدا نشد.", "No order with this number or landmark.", "په دې شمېره یا نښه فرمایش ونه موندل شو.")
  val currentMission: String get() = t("مأموریتِ جاری", "Current job", "اوسنۍ دنده")
  val all: String get() = t("همه", "All", "ټول")
  val shortcuts: String get() = t("میان‌برها", "Shortcuts", "لنډلارې")
  val missions: String get() = t("مأموریت‌ها", "Jobs", "دندې")
  val myCash: String get() = t("نقدِ من", "My cash", "زما نغدې")
  val callShop: String get() = t("تماس با دکان", "Call the shop", "دوکان ته زنګ")
  val deliveredList: String get() = t("تحویل‌شده‌ها", "Delivered", "سپارل شوي")
  val recentWork: String get() = t("کارهای اخیر", "Recent jobs", "وروستي کارونه")
  val noWorkYet: String get() = t("هنوز کاری ثبت نشده است.", "No jobs recorded yet.", "لا هېڅ کار نه دی ثبت شوی.")
  val noLiveMission: String get() = t("مأموریتِ زنده‌ای ندارید", "No live job", "ژوندۍ دنده نه لرئ")
  val noLiveMissionBody: String get() = t("دکان که بسپارد، همین‌جا می‌آید.", "When the shop assigns one, it comes here.", "کله چې دوکان یې درکړي، همدلته راځي.")
  val order: String get() = t("سفارش", "Order", "فرمایش")
  val details: String get() = t("جزئیات", "Details", "جزییات")
  val status: String get() = t("وضعیت", "Status", "حالت")
  val endedAddressErased: String get() = t("پایان یافت · نشانی پاک شد", "Ended · address erased", "پای ته ورسېده · پته پاکه شوه")
  val navHome: String get() = t("خانه", "Home", "کور")
  val navStats: String get() = t("آمار", "Stats", "احصایه")
  val navAccount: String get() = t("حساب", "Account", "حساب")
  val today: String get() = t("امروز", "Today", "نن")
  val thisWeek: String get() = t("این هفته", "This week", "دا اونۍ")
  val statsNotYet: String get() = t("آمار هنوز از دکان نیامده", "Stats have not come from the shop yet", "احصایه لا له دوکانه نه ده راغلې")
  val statsNotYetBody: String get() = t(
    "به اینترنت که وصل شوید، شمارِ تحویل‌ها و نقدِ گرفته‌شده این‌جا می‌آید.",
    "Once you are online, delivery counts and collected cash show here.",
    "کله چې انټرنټ ته وصل شئ، د سپارنو شمېر او اخیستل شوې نغدې دلته راځي.",
  )
  val deliveredStat: String get() = t("تحویل‌شده", "Delivered", "سپارل شوي")
  val cashCollected: String get() = t("نقدِ گرفته‌شده", "Cash collected", "اخیستل شوې نغدې")
  val myFare: String get() = t("کرایهٔ من", "My fare", "زما کرایه")
  val cashInHand: String get() = t("نقدِ در دستِ شما", "Cash in your hands", "ستاسو په لاس کې نغدې")
  val seeMyCash: String get() = t("دیدنِ نقدِ من", "See my cash", "زما نغدې وګورئ")
  fun fromWeekStart(date: String): String = t("از آغازِ هفتهٔ دکان، ${asIs(date)}", "Since the shop's week start, $date", "د دوکان د اونۍ له پیله، $date")
  val fromTodayStart: String get() = t("از آغازِ امروزِ دکان", "Since the start of the shop's day", "د دوکان د نن ورځې له پیله")
  val notSet: String get() = t("ثبت نشده", "Not set", "نه دی ثبت شوی")
  val sync: String get() = t("همگام‌سازی", "Sync", "همغږي")
  val notFromShopYet: String get() = t("هنوز از دکان نیامده", "Not received from the shop yet", "لا له دوکانه نه دی راغلی")
  val cashNotYetBody: String get() = t(
    "به اینترنت که وصل شوید، نقدِ در دست و تسویه‌ها این‌جا می‌آید.",
    "Once you are online, cash in hand and settlements show here.",
    "کله چې انټرنټ ته وصل شئ، په لاس کې نغدې او تصفیې دلته راځي.",
  )
  fun settleNote(asOf: String): String = t(
    "تسویه را دکان ثبت می‌کند؛ این‌جا فقط دیدن است. ${asIs(asOf)}",
    "The shop records settlements; this is view-only. $asOf",
    "تصفیه دوکان ثبتوي؛ دا ځای یوازې د لیدو لپاره دی. $asOf",
  )
  val unsettledHead: String get() = t("تحویل‌شده، هنوز تسویه‌نشده", "Delivered, not settled yet", "سپارل شوي، لا نه تصفیه شوي")
  val noUnsettled: String get() = t("نقدِ تسویه‌نشده‌ای نزدِ شما نیست.", "You hold no unsettled cash.", "تاسو سره نه تصفیه شوې نغدې نشته.")
  val noCash: String get() = t("بی نقد", "No cash", "بې نغدو")
  fun ofExpected(amount: String): String = t("از ${asIs(amount)}", "of $amount", "له $amount")
  fun moreInTotal(n: String): String = t(
    "+${asIs(n)} موردِ دیگر — در جمعِ «نقدِ در دست» آمده است",
    "+$n more — included in the \"cash in hand\" total",
    "+$n نور — د «په لاس کې نغدې» په ټولګه کې راغلي",
  )
  val settleHistory: String get() = t("تاریخچهٔ تسویه", "Settlement history", "د تصفیې تاریخچه")
  val noSettleYet: String get() = t("هنوز تسویه‌ای ثبت نشده است.", "No settlement recorded yet.", "لا هېڅ تصفیه نه ده ثبت شوې.")
  fun settleNo(no: String): String = t("تسویهٔ #${asIs(no)}", "Settlement #$no", "تصفیه #$no")
  fun settleOrders(n: String): String = t("${asIs(n)} سفارش · دکان گرفت", "$n orders · taken by the shop", "$n فرمایشونه · دوکان واخیستل")
  fun shortage(amount: String): String = t("کسری: ${asIs(amount)}", "Short: $amount", "کمښت: $amount")
  fun excess(amount: String): String = t("اضافه: ${asIs(amount)}", "Over: $amount", "زیات: $amount")
  val noDiff: String get() = t("بی اختلاف", "No difference", "بې توپیره")
  val zeroCap: String get() = t(
    "سقفِ نقدِ شما صفر است: نقد نگیرد — سفارشِ نقدی سپرده نمی‌شود",
    "Your cash limit is zero: no cash — cash orders are not assigned to you",
    "ستاسو د نغدو سقف صفر دی: نغدې مه اخلئ — نغدي فرمایش نه درسپارل کېږي",
  )
  fun capFill(pct: String): String = t("پُریِ سقف (در دست + در راه): ${asIs(pct)}٪", "Limit used (in hand + on the way): $pct%", "د سقف ډکوالی (په لاس کې + په لار کې): $pct٪")
  val cap: String get() = t("سقف", "Limit", "سقف")
  val noCashTaking: String get() = t("نقد نگیرد", "No cash", "نغدې نه اخلي")
  val headroom: String get() = t("جای خالی", "Room left", "خالي ځای")
  val noNewMessage: String get() = t("پیامِ تازه‌ای نیست.", "No new messages.", "نوی پیغام نشته.")
  fun newJobs(n: String): String = t("${asIs(n)} کارِ تازه — قبول یا رد", "$n new jobs — accept or reject", "$n نوې دندې — منل یا رد")
  fun orderRecalled(no: String): String = t("دکان سفارشِ #${asIs(no)} را پس خواسته است", "The shop recalled order #$no", "دوکان #$no فرمایش بېرته غوښتی")
  fun jobsNotSaved(n: String): String = t("${asIs(n)} کار ثبت نشد — ببینید", "$n jobs were not saved — check them", "$n کارونه ثبت نه شول — وې ګورئ")
  val close: String get() = t("بستن", "Close", "بندول")

  // ── عکسِ بسته ──
  fun photoConsent(no: String): String = t(
    "مشتریِ سفارشِ #${asIs(no)} به عکسِ تحویل رضایت داده است. عکس فقط از بسته گرفته شود؛ چهرهٔ کسی در عکس نباشد.",
    "The customer of order #$no agreed to a delivery photo. Photograph only the parcel; no one's face in the photo.",
    "د #$no فرمایش پېرودونکي د سپارلو انځور ته رضایت ورکړی. یوازې د بستې انځور واخلئ؛ د چا مخ دې په انځور کې نه وي.",
  )
  val parcelOnly: String get() = t("عکس فقط از بسته است", "The photo shows only the parcel", "انځور یوازې د بستې دی")
  val takePhoto: String get() = t("گرفتنِ عکس", "Take photo", "انځور اخیستل")
  val photoNote: String get() = t(
    "فقط بسته؛ عکس پس از رسیدن به دکان از این گوشی پاک می‌شود",
    "Parcel only; the photo is erased from this phone once it reaches the shop",
    "یوازې بسته؛ انځور دوکان ته له رسېدو وروسته له دې تلیفون څخه پاکېږي",
  )
  val photoUnread: String get() = t("عکس خوانده نشد؛ دوباره بگیرید", "The photo could not be read; take it again", "انځور ونه لوستل شو؛ بیا یې واخلئ")

  // ── خطای ورودیِ صفِ پیک (`CourierQueue` ِ هسته) — فارسی همان ثابتِ `CourierInputText`، بایت‌به‌بایت ──
  val errEventId: String get() = t(CourierInputText.BAD_EVENT_ID, "The event id is not valid", "د پېښې پېژند سم نه دی")
  val errNoMission: String get() = t(CourierInputText.NO_MISSION, "Delivery job not found", "دنده ونه موندل شوه")
  val errNotCourier: String get() = t(CourierInputText.NOT_COURIER, "This action isn't the courier's", "دا کار د سپارونکي نه دی")
  val errTransition: String get() = t(CourierInputText.BAD_TRANSITION, "This status change is not allowed", "د حالت دا بدلون اجازه نه لري")
  val errQueueFull: String get() = t(CourierInputText.QUEUE_FULL, "The event queue is full; connect to the internet first", "د پېښو کتار ډک دی؛ لومړی له انټرنټ سره ونښلئ")
  val errReasonRequired: String get() = t(CourierInputText.REASON_REQUIRED, "A reason is required", "دلیل اړین دی")
  val errReason: String get() = t(CourierInputText.BAD_REASON, "The reason is not valid", "دلیل سم نه دی")
  val errNoteRequired: String get() = t(CourierInputText.NOTE_REQUIRED, "A note is required", "توضیح اړینه ده")
  val errCash: String get() = t(CourierInputText.BAD_CASH, "The collected amount is not valid", "اخیستل شوی مبلغ سم نه دی")
  val errCashReason: String get() = t(CourierInputText.BAD_CASH_REASON, "The cash difference reason is not valid", "د نغدو د توپیر دلیل سم نه دی")
  val errBags: String get() = t(CourierInputText.BAD_BAGS, "The bag labels are not valid", "د کڅوړو لیبلونه سم نه دي")
  val errPosition: String get() = t(CourierInputText.BAD_POSITION, "The location is not valid", "ځای سم نه دی")
  val errKeyReused: String get() = t(CourierInputText.KEY_REUSED, "This id is already in the queue", "دا پېژند دمخه په کتار کې دی")

  // ── S94: «اشتراکِ موقعیت» ِ خودِ پیک (برگهٔ «حساب») ──
  val shareLocation: String get() = t("اشتراکِ موقعیت", "Share my location", "زما د ځای شریکول")
  val shareLocationOn: String get() = t(
    "روشن — مشتری جای شما را فقط «در راه» می‌بیند",
    "On — the customer sees where you are only while on the way",
    "روښانه — پېرودونکی ستاسو ځای یوازې «په لار کې» ویني",
  )
  val shareLocationOff: String get() = t("خاموش — هیچ موقعیتی فرستاده نمی‌شود", "Off — no location is sent", "بند — هېڅ ځای نه لېږل کېږي")
  val shareLocationHint: String get() = t(
    "هر وقت خواستید خاموش کنید؛ با خاموش کردن، موقعیت‌های فرستاده هم پاک می‌شوند.",
    "Turn it off any time; turning it off also deletes the locations already sent.",
    "هر وخت چې وغواړئ بند یې کړئ؛ په بندولو سره، لېږل شوي ځایونه هم پاکېږي.",
  )
  val shareLocationDone: String get() = t("اشتراکِ موقعیت روشن شد", "Location sharing is on", "د ځای شریکول روښانه شو")
  val shareLocationStopped: String get() = t(
    "اشتراکِ موقعیت خاموش شد و موقعیت‌های پیشین پاک شد",
    "Location sharing is off and earlier locations were deleted",
    "د ځای شریکول بند شو او پخواني ځایونه پاک شول",
  )
  val shareLocationOnline: String get() = t("برای این کار به اینترنت وصل شوید", "Connect to the internet to change this", "د دې بدلون لپاره انټرنټ ته وصل شئ")
}

/**
 *  پیامِ [ir.vil3ntec.tohid.core.courier.CourierInputError] به زبانِ صفحه. فارسی همان پیامِ هسته است (ثابت‌های
 *  `CourierInputText`)؛ پیامِ ناشناخته همان‌طور که آمده.
 */
fun courierInputText(message: String?): String? = when (message) {
  CourierInputText.BAD_EVENT_ID -> StrCourierApp.errEventId
  CourierInputText.NO_MISSION -> StrCourierApp.errNoMission
  CourierInputText.NOT_COURIER -> StrCourierApp.errNotCourier
  CourierInputText.BAD_TRANSITION -> StrCourierApp.errTransition
  CourierInputText.QUEUE_FULL -> StrCourierApp.errQueueFull
  CourierInputText.REASON_REQUIRED -> StrCourierApp.errReasonRequired
  CourierInputText.BAD_REASON -> StrCourierApp.errReason
  CourierInputText.NOTE_REQUIRED -> StrCourierApp.errNoteRequired
  CourierInputText.BAD_CASH -> StrCourierApp.errCash
  CourierInputText.BAD_CASH_REASON -> StrCourierApp.errCashReason
  CourierInputText.BAD_BAGS -> StrCourierApp.errBags
  CourierInputText.BAD_POSITION -> StrCourierApp.errPosition
  CourierInputText.KEY_REUSED -> StrCourierApp.errKeyReused
  else -> message
}
