package ir.vil3ntec.tohid.core.i18n

/**
 *  نوشته‌های گروهِ G7 حساب، کارمندان، اشتراک، پشتیبانی (team-account.md) — صاحبش همان گروه است.
 *  هر ویژگی سه‌زبانه: `val x: String get() = t("…", "…", "…")`؛ قاعده‌ها سرِ `I18n.kt`.
 *  متنِ فارسیِ هر جمله همان متنِ صفحه‌های پیشین است (جز جایی که مقصدش عوض شد).
 */
@Suppress("unused")
object StrAccount {

  // «حساب من» (r4)
  val guest: String get() = t("مهمان", "Guest", "مېلمه")
  val noName: String get() = t("بی‌نام", "No name", "بې نومه")
  val roleOwner: String get() = t("صاحب دکان", "Shop owner", "د دوکان خاوند")
  val roleManager: String get() = t("مدیر", "Manager", "مدیر")
  val roleSeller: String get() = t("فروشنده", "Seller", "پلورونکی")
  val roleStaff: String get() = t("شاگرد", "Staff", "شاګرد")
  val roleCourier: String get() = t("پیک", "Courier", "رسوونکی")
  val statInvoices: String get() = t("فاکتور", "Invoices", "بلونه")
  val statItems: String get() = t("کالا", "Items", "توکي")
  val statDebtors: String get() = t("قرض‌دار", "Debtors", "پوروړي")
  val changePhotoA11y: String get() = t("عوض کردنِ عکس حساب", "Change account photo", "د حساب انځور بدلول")
  val accountPhoto: String get() = t("عکس حساب", "Account photo", "د حساب انځور")

  // نوارِ تیرهٔ اشتراک
  val bannerGuestTitle: String get() = t("وارد حساب نشده‌اید", "You're not signed in", "حساب ته نه یاست ننوتي")
  val bannerGuestBody: String get() = td("با حساب، دفترِ دکان پشتیبان می‌گیرد و روی گوشیِ دیگر هم می‌آید", "With an account the shop ledger is backed up and appears on your other phones too", "په حساب سره د دوکان دفتر بیک اپ کېږي او په بل تلیفون هم راځي", "With an account the shop ledger is backed up and appears on your other devices too", "په حساب سره د دوکان دفتر بیک اپ کېږي او په بله وسیله هم راځي")
  val bannerExpiredTitle: String get() = t("اشتراک تمام شده", "Subscription ended", "ګډون پای ته رسېدلی")
  val bannerExpiredBody: String get() = t("هیچ داده‌ای پاک نمی‌شود؛ خروجی و چاپ باز می‌ماند", "No data is deleted; export and printing stay open", "هېڅ معلومات نه ړنګېږي؛ صادرول او چاپ پرانیستي پاتې کېږي")
  fun bannerTrialDays(n: Any): String = t("آزمایشی: ${asIs(n)} روزِ دیگر", "Trial: $n more ${pl(n, "day", "days")}", "ازمایښتي: $n نورې ورځې")
  val bannerTrialLast: String get() = t("امروز آخرین روزِ آزمایشی", "Today is the last trial day", "نن د ازمایښت وروستۍ ورځ ده")
  val bannerTrialBody: String get() = t("برای ادامه پس از آزمایش، یکی از پلن‌ها را بگیرید", "To continue after the trial, pick one of the plans", "له ازمایښت وروسته د دوام لپاره یو پلان واخلئ")
  fun bannerActiveDays(n: Any): String = t("اشتراک: ${asIs(n)} روزِ دیگر", "Subscription: $n more ${pl(n, "day", "days")}", "ګډون: $n نورې ورځې")
  val bannerActiveLast: String get() = t("امروز آخرین روزِ اشتراک", "Today is the last subscription day", "نن د ګډون وروستۍ ورځ ده")
  val bannerSoonBody: String get() = t("به‌زودی تمام می‌شود؛ پیش از پایان تمدید کنید", "It ends soon; renew before it does", "ژر پای ته رسېږي؛ مخکې له پایه یې تمدید کړئ")
  fun bannerSoonBodyOn(day: String): String = t("روزِ \u2066${asIs(day)}\u2069 تمام می‌شود؛ پیش از آن تمدید کنید", "Ends on $day; renew before then", "د \u2066$day\u2069 په ورځ پای ته رسېږي؛ مخکې له هغې یې تمدید کړئ")
  fun bannerTrialBodyOn(day: String): String = t("آزمایشی روزِ \u2066${asIs(day)}\u2069 تمام می‌شود؛ یکی از پلن‌ها را بگیرید", "The trial ends on $day; pick one of the plans", "ازمایښت د \u2066$day\u2069 په ورځ پای ته رسېږي؛ یو پلان واخلئ")
  val bannerOkBody: String get() = t("همهٔ بخش‌ها باز است", "Every section is open", "ټولې برخې پرانیستې دي")
  val bannerNoneTitle: String get() = t("بدونِ اشتراک", "No subscription", "بې ګډونه")
  val bannerNoneBody: String get() = t("برای باز شدنِ همهٔ بخش‌ها یکی از پلن‌ها را بگیرید", "Pick a plan to open every section", "د ټولو برخو د پرانیستو لپاره یو پلان واخلئ")
  val plansButton: String get() = t("پلن‌ها", "Plans", "پلانونه")

  // فهرستِ «حساب من»
  val menuEdit: String get() = t("ویرایش حساب و دکان", "Edit account & shop", "حساب او دوکان سمول")
  val menuDevices: String get() = t("دستگاه‌ها", "Devices", "وسایل")
  val menuPlan: String get() = t("اشتراک و پرداخت", "Subscription & payments", "ګډون او تادیات")
  val menuStats: String get() = t("آمار", "Stats", "شمېرې")
  val menuSignOut: String get() = t("خروج از حساب", "Sign out", "له حساب وتل")

  // ویرایشِ حساب و دکان
  val changePhoto: String get() = t("عوض کردنِ عکس", "Change photo", "انځور بدلول")
  val photoLocal: String get() = td("عکسِ حساب فقط روی همین گوشی می‌ماند", "The account photo stays on this phone only", "د حساب انځور یوازې په همدې تلیفون پاتې کېږي", "The account photo stays on this computer only", "د حساب انځور یوازې په همدې کمپیوټر پاتې کېږي")
  val photoSaved: String get() = t("عکس حساب ثبت شد", "Account photo saved", "د حساب انځور ثبت شو")
  val photoFailed: String get() = t("عکس ذخیره نشد", "The photo wasn't saved", "انځور خوندي نه شو")
  val photoSource: String get() = t("انتخاب تصویر", "Choose a picture", "انځور وټاکئ")
  val camera: String get() = t("دوربین", "Camera", "کمره")
  val cameraBody: String get() = td("گرفتنِ عکسِ تازه", "Take a new photo", "نوی انځور واخلئ", "A photo on this computer, taken with a phone or webcam", "په کمپیوټر کې انځور، چې په موبایل یا ویب‌کم اخیستل شوی")
  val gallery: String get() = t("گالری", "Gallery", "ګالري")
  val galleryBody: String get() = td("انتخاب از عکس‌های گوشی", "Pick from the phone's photos", "د تلیفون له انځورونو وټاکئ", "Pick from the computer's pictures", "د کمپیوټر له انځورونو وټاکئ")
  val groupAccount: String get() = t("حساب", "Account", "حساب")
  val fullName: String get() = t("نام و نام خانوادگی", "Full name", "بشپړ نوم")
  val email: String get() = t("ایمیل", "Email", "ایمیل")
  val emailNone: String get() = t("ثبت نشده", "Not set", "نه دی ثبت")
  val emailLocked: String get() = t("ایمیلِ حساب عوض نمی‌شود — راهِ ورود و بازیابیِ رمز همین است", "The account email can't change — it's how you sign in and recover the password", "د حساب ایمیل نه بدلېږي — د ننوتو او پټنوم بیرته اخیستو لار همدا ده")
  val emailByPhone: String get() = t("شما با شماره وارد شده‌اید", "You signed in with a phone number", "تاسو په شمېره ننوتي یاست")
  val phone: String get() = t("شماره موبایل", "Mobile number", "د موبایل شمېره")
  val phoneOnceHelper: String get() = t("شماره پس از ثبت قفل می‌شود و از برنامه عوض نمی‌شود.", "Once saved, the number is locked and can't be changed from the app.", "شمېره له ثبت وروسته قفلېږي او له اپ نه بدلېږي.")
  val phoneLocked: String get() = t("شماره ثبت شده و قفل است", "The number is saved and locked", "شمېره ثبت او قفل ده")
  val notEditable: String get() = t("قابل تغییر نیست", "Can't be changed", "نه بدلېږي")
  val groupShop: String get() = t("دکان", "Shop", "دوکان")
  val shopName: String get() = t("نام فروشگاه", "Shop name", "د دوکان نوم")
  val shopNameHelper: String get() = t("این نام روی سربرگ فاکتور چاپ می‌شود.", "This name is printed on the invoice header.", "دا نوم د بل په سرلیک چاپېږي.")
  val groupMembership: String get() = t("عضویت", "Membership", "غړیتوب")
  val memberFor: String get() = t("مدت عضویت", "Member for", "د غړیتوب موده")
  fun memberSince(date: Any): String = t("عضو از ${asIs(date)}", "Member since $date", "له $date راهیسې غړی")
  val createdUnknown: String get() = t("روزِ ساختنِ حساب معلوم نیست", "The account's creation day isn't known", "د حساب د جوړېدو ورځ نه ده معلومه")
  val saveChanges: String get() = t("ذخیره تغییرات", "Save changes", "بدلونونه خوندي کړئ")
  val saving: String get() = t("در حال ذخیره…", "Saving…", "خوندي کېږي…")
  val nameRequired: String get() = t("نام را بنویسید", "Write your name", "نوم ولیکئ")
  val nameSavedOffline: String get() = td("نام روی گوشی ذخیره شد — برای ثبت روی سرور به اینترنت نیاز است", "The name was saved on this phone — saving it on the server needs internet", "نوم په تلیفون خوندي شو — په سرور د ثبت لپاره انټرنیټ پکار دی", "The name was saved on this computer — saving it on the server needs internet", "نوم په کمپیوټر خوندي شو — په سرور د ثبت لپاره انټرنیټ پکار دی")
  val phoneNeedsInternet: String get() = t("ثبتِ شماره به اینترنت نیاز دارد", "Saving the number needs internet", "د شمېرې ثبت انټرنیټ ته اړتیا لري")
  val phoneSaved: String get() = t("شماره ثبت شد و قفل شد", "The number was saved and locked", "شمېره ثبت او قفل شوه")
  //  AUTH-08: کادرِ کدِ شمارهٔ تازه (`ProfileEditModel.codeError`؛ برچسب و «کد رفت» در `StrShellPages`)
  val codeIncomplete: String get() = t(
    "کد کامل نیست؛ همان کدی را بنویسید که به این شماره پیامک شد",
    "The code is incomplete — enter the code texted to this number",
    "کوډ بشپړ نه دی؛ هغه کوډ ولیکئ چې دې شمېرې ته واستول شو",
  )
  val codeWrong: String get() = t(
    "کد درست نیست؛ کدِ پیامک‌شده را دوباره نگاه کنید",
    "That code isn't right — check the texted code again",
    "کوډ سم نه دی؛ استول شوی کوډ بیا وګورئ",
  )
  val codeDead: String get() = t(
    "این کد دیگر کار نمی‌کند (وقتش گذشته یا بیش از اندازه امتحان شده)",
    "This code no longer works (it expired or was tried too many times)",
    "دا کوډ نور کار نه کوي (موده یې تېره شوې یا ډېر ځله ازمویل شوی)",
  )
  val codeNotNeeded: String get() = t(
    "کدِ شماره دیگر لازم نیست؛ «ذخیره تغییرات» را دوباره بزنید",
    "A code is no longer needed — tap “Save changes” again",
    "د شمېرې کوډ نور اړین نه دی؛ «بدلونونه خوندي کړئ» بیا ووهئ",
  )
  val saved: String get() = t("ذخیره شد", "Saved", "خوندي شو")

  // خروج از حساب
  val signOutTitle: String get() = t("خروج از حساب؟", "Sign out?", "له حساب وځئ؟")
  val signOutBody: String get() = td("برای برگشتن، ایمیل و رمزتان را دوباره می‌زنید. دفترِ دکان روی همین گوشی سرِ جایش می‌ماند.", "To come back you enter your email and password again. The shop ledger stays on this phone.", "د بیرته راتګ لپاره به بیا ایمیل او پټنوم ولیکئ. د دوکان دفتر په همدې تلیفون پاتې کېږي.", "To come back you enter your email and password again. The shop ledger stays on this computer.", "د بیرته راتګ لپاره به بیا ایمیل او پټنوم ولیکئ. د دوکان دفتر په همدې کمپیوټر پاتې کېږي.")
  val signOutAction: String get() = t("خروج", "Sign out", "وتل")
  // AUTH-06 «خروج از همهٔ دستگاه‌ها» ِ خودِ کاربر
  val menuSignOutAll: String get() = t("خروج از همهٔ دستگاه‌ها", "Sign out of all devices", "له ټولو وسایلو وتل")
  val signOutAllTitle: String get() = t("خروج از همهٔ دستگاه‌ها؟", "Sign out of all devices?", "له ټولو وسایلو وځئ؟")
  val signOutAllBody: String get() = t(
    "همهٔ نشست‌های این حساب در دستگاه‌های دیگر هم بسته می‌شود؛ هر دستگاه برای برگشت ایمیل و رمز می‌خواهد. دفترِ دکان روی هر دستگاه سرِ جایش می‌ماند.",
    "Every session of this account on other devices is closed too; each device needs the email and password to come back. The shop ledger stays on every device.",
    "د دې حساب ټولې ناستې په نورو وسایلو هم تړل کېږي؛ هره وسیله د بیرته راتګ لپاره ایمیل او پټنوم غواړي. د دوکان دفتر په هره وسیله پاتې کېږي.",
  )
  val signOutAllAction: String get() = t("خروج از همه", "Sign out everywhere", "له ټولو ووځئ")
  val signedOutAll: String get() = t("از همهٔ دستگاه‌ها خارج شدید", "You signed out of all devices", "له ټولو وسایلو ووتلئ")
  val stay: String get() = t("ماندن", "Stay", "پاتې کېدل")
  val pendingTitle: String get() = t("هنوز همه‌چیز به سرور نرفته", "Not everything has reached the server yet", "لا ټول شیان سرور ته نه دي رسېدلي")
  val syncNow: String get() = t("همین حالا همگام کن", "Sync now", "همدا اوس همغږي کړئ")
  val forceSignOut: String get() = t("خروجِ اجباری…", "Force sign out…", "اجباري وتل…")
  fun pendingBody(pending: Any, online: Boolean): String = t(
    "${asIs(pending)} تغییرِ این دستگاه هنوز به سرور نرسیده است. تا این‌ها نروند، خروج از حساب بسته است؛ وگرنه این کارها روی سرور و دستگاه‌های دیگر دیده نمی‌شوند. " +
      if (online) "«همین حالا همگام کن» را بزنید و بمانید تا صف خالی شود." else "اینترنتِ این دستگاه وصل نیست؛ اول وصل شوید و بعد «همین حالا همگام کن» را بزنید.",
    "$pending ${pl(pending, "change", "changes")} from this device haven't reached the server yet. Until they do, signing out is locked; otherwise this work won't show on the server or your other devices. " +
      if (online) "Tap “Sync now” and wait until the queue is empty." else "This device is offline; connect first, then tap “Sync now”.",
    "د دې وسیلې $pending بدلونونه لا سرور ته نه دي رسېدلي. تر څو چې ولاړ نه شي، له حساب وتل بند دي؛ کنه دا کارونه په سرور او نورو وسایلو نه ښکاري. " +
      if (online) "«همدا اوس همغږي کړئ» ووهئ او انتظار وکړئ تر څو کتار تش شي." else "د دې وسیلې انټرنیټ نه دی وصل؛ لومړی وصل شئ بیا «همدا اوس همغږي کړئ» ووهئ.",
  )
  val allSentTitle: String get() = t("همه‌چیز به سرور رسید", "Everything reached the server", "ټول شیان سرور ته ورسېدل")
  val allSentBody: String get() = t("صف خالی است؛ حالا می‌توانید از حساب خارج شوید.", "The queue is empty; you can sign out now.", "کتار تش دی؛ اوس له حساب وتلای شئ.")
  val writingBackup: String get() = t("در حالِ نوشتنِ پشتیبان…", "Writing a backup…", "بیک اپ لیکل کېږي…")
  val backupFailed: String get() = t("پشتیبان نوشته نشد؛ خروجِ اجباری انجام نشد", "The backup wasn't written; the forced sign-out didn't happen", "بیک اپ ونه لیکل شو؛ اجباري وتل ونه شول")
  val forceTitle: String get() = t("خروجِ اجباری؟", "Force sign out?", "اجباري وتل؟")
  fun forceBody(pending: Any, path: Any): String = t(
    "پشتیبانِ کامل این‌جا نوشته شد:\n${asIs(path)}\n\n${asIs(pending)} تغییر هنوز به سرور نرفته و تا دوباره با همین حساب وارد نشوید فرستاده نمی‌شود. باز هم خارج می‌شوید؟",
    "A full backup was written here:\n$path\n\n$pending ${pl(pending, "change hasn't", "changes haven't")} reached the server and won't be sent until you sign in with this account again. Sign out anyway?",
    "بشپړ بیک اپ دلته ولیکل شو:\n$path\n\n$pending بدلونونه لا سرور ته نه دي تللي او تر هغه چې بیا په همدې حساب ننوځئ نه لېږل کېږي. بیا هم وځئ؟",
  )
  val signOutAnyway: String get() = t("باز هم خارج شو", "Sign out anyway", "بیا هم ووځه")

  // خروجِ بی گم شدنِ داده: اول همه به سرور، بعد خروج (SafeSignOut)
  val checkingBeforeSignOut: String get() = t("در حالِ فرستادنِ همهٔ تغییرها به سرور…", "Sending every change to the server…", "ټول بدلونونه سرور ته لېږل کېږي…")
  fun unsyncedTitle(pending: Any): String = t(
    "${asIs(pending)} تغییرِ ثبت‌نشده هنوز به سرور نرسیده",
    "$pending unsaved ${pl(pending, "change hasn't", "changes haven't")} reached the server yet",
    "$pending نه ثبت شوي بدلونونه لا سرور ته نه دي رسېدلي",
  )
  val unsyncedOffline: String get() = t(
    "اینترنتِ این دستگاه وصل نیست. وصل شوید و «دوباره بکوش» را بزنید؛ تا آن وقت هیچ چیزی پاک نمی‌شود.",
    "This device is offline. Connect and tap “Try again”; nothing is deleted meanwhile.",
    "دا وسیله انټرنېټ نه لري. وصل شئ او «بیا هڅه» کېکاږئ؛ تر هغه هېڅ شی نه ړنګېږي.",
  )
  val unsyncedRejected: String get() = t(
    "سرور این تغییرها را نپذیرفت. بیرون نشوید و با پشتیبانی تماس بگیرید؛ یا با «خروجِ اجباری…» پشتیبان بگیرید و برای همین حساب نگه دارید.",
    "The server refused these changes. Stay signed in and contact support, or use “Force sign out…” to back them up and keep them for this account.",
    "سرور دا بدلونونه ونه منل. مه وځئ او له ملاتړ سره اړیکه ونیسئ؛ یا په «اجباري وتل…» یې بیک اپ کړئ او همدې حساب ته یې وساتئ.",
  )
  val unsyncedFailed: String get() = t(
    "سرور جواب نداد. چند لحظه بعد «دوباره بکوش» را بزنید؛ تا آن وقت هیچ چیزی پاک نمی‌شود.",
    "The server didn't answer. Tap “Try again” in a moment; nothing is deleted meanwhile.",
    "سرور ځواب ورنه کړ. یو څو شېبې وروسته «بیا هڅه» کېکاږئ؛ تر هغه هېڅ شی نه ړنګېږي.",
  )
  val unsyncedNeverSent: String get() = t(
    "دفترِ این دستگاه هنوز یک بار هم کامل به سرور نرسیده. «دوباره بکوش» را بزنید تا فرستاده و سنجیده شود.",
    "This device's ledger has never fully reached the server. Tap “Try again” to send and check it.",
    "د دې وسیلې دفتر لا یو ځل هم بشپړ سرور ته نه دی رسېدلی. «بیا هڅه» کېکاږئ چې ولېږل او وڅېړل شي.",
  )
  val unsyncedSessionEnded: String get() = t(
    "نشستِ این حساب تمام شده و تغییرها فرستاده نمی‌شوند. اول دوباره وارد شوید؛ بیرون رفتن آن‌ها را نمی‌فرستد.",
    "This account's session ended, so changes can't be sent. Sign in again first; signing out won't send them.",
    "د دې حساب ناسته پای ته رسېدلې او بدلونونه نه لېږل کېږي. لومړی بیا ننوځئ؛ وتل یې نه لېږي.",
  )
  val tryAgain: String get() = t("دوباره بکوش", "Try again", "بیا هڅه")
  val dontLeave: String get() = t("بیرون نشو", "Don't sign out", "مه وځه")
  fun forceKeptBody(pending: Any, path: Any): String = t(
    "پشتیبانِ کامل این‌جا نوشته شد:\n${asIs(path)}\n\n${asIs(pending)} تغییرِ ثبت‌نشده رمزدار روی همین دستگاه و فقط برای همین حساب نگه داشته می‌شود و با ورودِ دوباره به همین حساب خودکار به سرور می‌رود. باز هم خارج می‌شوید؟",
    "A full backup was written here:\n$path\n\n$pending unsaved ${pl(pending, "change is", "changes are")} kept encrypted on this device for this account only, and sent automatically when you sign in to it again. Sign out anyway?",
    "بشپړ بیک اپ دلته ولیکل شو:\n$path\n\n$pending نه ثبت شوي بدلونونه کوډ شوي په همدې وسیله یوازې د همدې حساب لپاره ساتل کېږي او بیا ننوتلو سره پخپله سرور ته ځي. بیا هم وځئ؟",
  )
  val signedOut: String get() = t("از حساب خارج شدید", "You signed out", "له حساب ووتلئ")
  val syncStarted: String get() = t("همگام‌سازی شروع شد", "Sync started", "همغږي پیل شوه")
  val syncFailed: String get() = t("همگام‌سازی ناموفق بود", "Sync failed", "همغږي ناکامه شوه")

  // کارمندان
  val teamIntro: String get() = t("کسانی که با شما روی همین دکان کار می‌کنند. هر کدام حساب خودش را دارد و کارهایش در سابقهٔ عملیات به نامِ خودش ثبت می‌شود.", "People who work on this shop with you. Each has their own account, and their work is logged under their own name.", "هغه کسان چې له تاسو سره په همدې دوکان کار کوي. هر یو خپل حساب لري او کارونه یې د هغه په نوم ثبتېږي.")
  val signInFirst: String get() = t("اول وارد حساب شوید", "Sign in first", "لومړی حساب ته ننوځئ")
  val teamSignInBody: String get() = td("کارمندان روی سرور نگه داشته می‌شوند تا روی هر گوشی یکی باشند. بدون حساب، چیزی برای نشان دادن نیست.", "Staff are kept on the server so every phone sees the same list. Without an account there's nothing to show.", "کارکوونکي په سرور ساتل کېږي څو په هر تلیفون یو شان وي. بې حسابه د ښودلو لپاره څه نشته.", "Staff are kept on the server so every device sees the same list. Without an account there's nothing to show.", "کارکوونکي په سرور ساتل کېږي څو په هره وسیله یو شان وي. بې حسابه د ښودلو لپاره څه نشته.")
  val shopCode: String get() = t("کد دکان شما", "Your shop code", "ستاسو د دوکان کوډ")
  val shopCodeHelper: String get() = t("این کد را به هر شاگردی که می‌خواهید بدهید. همیشه همین است و عوض نمی‌شود.", "Give this code to any staff member you want. It always stays the same.", "دا کوډ هر شاګرد ته ورکړئ. تل همدا دی او نه بدلېږي.")
  val copyCode: String get() = t("کپی کد", "Copy code", "کوډ کاپي کړئ")
  val codeCopied: String get() = t("کد کپی شد", "Code copied", "کوډ کاپي شو")
  val newShopCode: String get() = t("کد تازه", "New code", "نوی کوډ")
  val rotateTitle: String get() = t("کدِ تازهٔ دکان؟", "New shop code?", "د دوکان نوی کوډ؟")
  val rotateBody: String get() = t("«کد تازه» فقط وقتی لازم است که کد لو رفته باشد. شاگردهای فعلی بیرون نمی‌روند؛ فقط کد قبلی دیگر کسی را وارد نمی‌کند.", "A new code is only needed if the code leaked. Current staff stay; only the old code stops letting anyone in.", "نوی کوډ یوازې هغه وخت پکار دی چې کوډ ښکاره شوی وي. اوسني شاګردان نه وځي؛ یوازې زوړ کوډ نور څوک نه ننباسي.")
  val codeRotated: String get() = t("کدِ تازه ساخته شد", "New code created", "نوی کوډ جوړ شو")
  val disableCode: String get() = t("خاموش کردنِ کد", "Turn the code off", "کوډ بندول")
  val disableTitle: String get() = t("خاموش کردنِ کدِ دکان", "Turn off the shop code", "د دوکان کوډ بندول")
  val disableBody: String get() = t("از این پس کسی با کدِ ثابت وارد دکان نمی‌شود، تا وقتی «روشن کردن با کدِ تازه» را بزنید. شاگردهای فعلی سرِ کارشان می‌مانند.", "From now on nobody joins with the shop code until you tap “Turn on with a new code”. Current staff keep working.", "له دې وروسته هېڅوک په ثابت کوډ نه ننوځي، تر هغه چې «په نوي کوډ فعالول» ووهئ. اوسني شاګردان په کار پاتې کېږي.")
  val disableConfirm: String get() = t("خاموش کن", "Turn off", "بند یې کړه")
  val codeDisabled: String get() = t("کدِ دکان خاموش شد", "The shop code was turned off", "د دوکان کوډ بند شو")
  val codeOffBody: String get() = t("کدِ ثابتِ دکان خاموش است؛ کسی با آن وارد نمی‌شود. شاگردهای فعلی سرِ کارشان هستند.", "The shop code is off; nobody can join with it. Current staff keep working.", "د دوکان ثابت کوډ بند دی؛ څوک پرې نه ننوځي. اوسني شاګردان په کار دي.")
  val turnOnNewCode: String get() = t("روشن کردن با کدِ تازه", "Turn on with a new code", "په نوي کوډ فعالول")
  val staffTitle: String get() = t("کارمندان", "Staff", "کارکوونکي")
  fun staffCount(n: Any, max: Any): String = t("کارمندان (${asIs(n)} از ${asIs(max)})", "Staff ($n of $max)", "کارکوونکي ($n له $max)")
  val noMembers: String get() = t("هنوز کسی به دکان نپیوسته است.", "Nobody has joined the shop yet.", "لا څوک دوکان ته نه دی یوځای شوی.")
  val stateActive: String get() = t("فعال", "Active", "فعال")
  val stateClosed: String get() = t("بسته", "Closed", "بند")
  val remove: String get() = t("برداشتن", "Remove", "لرې کول")
  fun removeTitle(name: Any): String = t("برداشتنِ ${asIs(name)}؟", "Remove $name?", "$name لرې شي؟")
  val thisMember: String get() = t("این کارمند", "this staff member", "دا کارکوونکی")
  val removeBody: String get() = t("دسترسی‌اش به دکان بسته می‌شود. کارهایی که تا حالا ثبت کرده سرِ جایشان می‌مانند.", "Their access to the shop is closed. Everything they recorded so far stays.", "دوکان ته یې لاسرسی بندېږي. هر څه چې یې تر اوسه ثبت کړي پاتې کېږي.")
  val removeConfirm: String get() = t("بردار", "Remove", "لرې یې کړه")
  val memberRemoved: String get() = t("کارمند برداشته شد", "Staff member removed", "کارکوونکی لرې شو")
  val joinCodes: String get() = t("کدهای پیوستن", "Join codes", "د یوځای کېدو کوډونه")
  val noCodes: String get() = t("کدی ساخته نشده. با دکمهٔ پایین یکی بسازید.", "No codes yet. Create one with the button below.", "هېڅ کوډ نه دی جوړ. په لاندې تڼۍ یو جوړ کړئ.")
  fun usesOf(used: Any, max: Any): String = t("${asIs(used)} از ${asIs(max)} بار", "$used of $max ${pl(max, "use", "uses")}", "$used له $max ځله")
  val unlimited: String get() = t("بی‌شمار", "Unlimited", "بې حده")
  val revokedMark: String get() = t("(باطل)", "(void)", "(باطل)")
  val revoke: String get() = t("باطل کن", "Revoke", "باطل یې کړه")
  val codeRevoked: String get() = t("کد باطل شد", "Code revoked", "کوډ باطل شو")
  fun validUntil(date: Any): String = t("تا ${asIs(date)}", "Until $date", "تر $date")
  val validAlways: String get() = t("همیشه", "No expiry", "تل")
  val newJoinCode: String get() = t("کدِ پیوستنِ تازه", "New join code", "نوی د یوځای کېدو کوډ")

  // ساختنِ کدِ پیوستن و نمایشِ یک‌باره
  val newCodeTitle: String get() = t("کد پیوستن تازه", "New join code", "نوی د یوځای کېدو کوډ")
  val newCodeStaffBody: String get() = t("شاگرد این کد را در صفحهٔ ورود می‌زند و به همین دکان می‌پیوندد.", "Staff enter this code on the sign-in page and join this shop.", "شاګرد دا کوډ د ننوتو په پاڼه کې لیکي او همدې دوکان ته یوځای کېږي.")
  val newCodeCourierBody: String get() = t("پیک این کد را در «ورود با کدِ شاگرد» می‌زند. حسابش فقط مأموریت‌های خودش را می‌بیند — نه دفتر، نه کالا، نه گزارش.", "The courier enters this code in “Sign in with a staff code”. Their account sees only their own deliveries — no ledger, items or reports.", "رسوونکی دا کوډ په «د شاګرد په کوډ ننوتل» کې لیکي. حساب یې یوازې خپلې دندې ویني — نه دفتر، نه توکي، نه راپور.")
  val role: String get() = t("نقش", "Role", "رول")
  val roleStaffDetail: String get() = t("فروش و ثبت", "Sales and records", "پلور او ثبت")
  val roleManagerDetail: String get() = t("همه‌کاره جز حذف دکان", "Everything except deleting the shop", "ټول کارونه پرته د دوکان له ړنګولو")
  val roleCourierDetail: String get() = t("فقط مأموریت‌های خودش", "Only their own deliveries", "یوازې خپلې دندې")
  val usesField: String get() = t("چند بار قابل استفاده (۰ = بی‌شمار)", "How many uses (0 = unlimited)", "څو ځله کارول کېږي (۰ = بې حده)")
  val courierExpiry: String get() = t("مهلتِ کدِ پیک (۱ تا ۷ روز، یک بار مصرف)", "Courier code expiry (1–7 days, single use)", "د رسوونکي د کوډ موده (۱ تر ۷ ورځې، یو ځل)")
  val daysField: String get() = t("تا چند روز معتبر (۰ = همیشه)", "Valid for how many days (0 = always)", "څو ورځې معتبر (۰ = تل)")
  val create: String get() = t("بساز", "Create", "جوړ یې کړه")
  val revealTitle: String get() = t("این کد را به شاگردتان بدهید", "Give this code to your staff", "دا کوډ خپل شاګرد ته ورکړئ")
  val revealBody: String get() = t("فقط همین یک بار نشان داده می‌شود. بعد از این، فقط می‌شود باطلش کرد.", "It's shown only this once. After this it can only be revoked.", "یوازې همدا یو ځل ښودل کېږي. له دې وروسته یوازې باطلېدای شي.")
  val copyAction: String get() = t("رونوشت", "Copy", "کاپي")

  // صفحهٔ هر کارمند: مجوز و دستگاه
  val permsTitle: String get() = t("مجوزِ کارمندان", "Staff permissions", "د کارکوونکو اجازې")
  val permsHelper: String get() = t("هر کلید فقط می‌تواند کمتر از نقش بدهد؛ «عددهای مالی» فقط نمایش است و داده روی دستگاه هست.", "Each switch can only give less than the role; “money figures” only hides the view — the data is still on the device.", "هره کیلي یوازې له رول کمه ورکولای شي؛ «مالي شمېرې» یوازې ښودنه ده او معلومات په وسیله کې شته.")
  val permsOwnerOnly: String get() = t("فقط صاحب دکان این کلیدها را عوض می‌کند.", "Only the shop owner can change these switches.", "یوازې د دوکان خاوند دا کیلي بدلولای شي.")
  //  STF-06: محدودهٔ مکانِ کارمند
  val scopeTitle: String get() = t("مکان‌های مجاز", "Allowed locations", "اجازه لرونکي ځایونه")
  val scopeHelper: String get() = t("کارمند فقط موجودیِ مکان‌های روشن را می‌بیند و جابه‌جا می‌کند؛ همه روشن یعنی همه. سرور همین را اجرا می‌کند.", "The staff member only sees and moves stock in the switched-on locations; all on means all. The server enforces it.", "کارکوونکی یوازې د روښانه ځایونو زېرمه ویني او لېږدوي؛ ټول روښانه یعنې ټول. سرور همدا پلي کوي.")
  val placeShopFloor: String get() = t("قفسهٔ فروشگاه", "Shop floor", "د دوکان المارۍ")
  val placeWarehouse: String get() = t("انبار", "Warehouse", "ګودام")
  val permSettings: String get() = t("تنظیماتِ دکان", "Shop settings", "د دوکان امستنې")
  val permAudit: String get() = t("سابقهٔ عملیات", "Activity log", "د کړنو تاریخچه")
  val permMoney: String get() = t("دیدنِ عددهای مالی (درآمد، سود)", "See money figures (income, profit)", "مالي شمېرې لیدل (عاید، ګټه)")
  /** CRM-15 — `customers.contact` */
  val permContacts: String get() = t("دیدنِ شمارهٔ مشتریان (تماس و پیام)", "See customers' phone numbers (call and message)", "د پېرودونکو شمېرې لیدل (زنګ او پیغام)")
  //  STF-03: سقفِ فروشِ قرضیِ هر کارمند (سرور با پرچمِ `shop.credit_cap` می‌سنجد)
  val permCredit: String get() = t("فروشِ قرضی (به حساب)", "Selling on credit", "په پور پلورل")
  //  STF-03: سقفِ تغییرِ قیمتِ ردیف (درصد از قیمتِ فهرست؛ سرور با پرچمِ `shop.price_cap` می‌سنجد)
  val permPrice: String get() = t("تغییرِ قیمتِ ردیف", "Changing a line price", "د کرښې د بیې بدلول")
  //  POS-10 (تصمیمِ ۱-۱۱ (۲)): قلمِ آزاد — سقف روی قیمتِ واحد (سرور با همان پرچمِ `shop.price_cap` می‌سنجد)
  val permFreeLine: String get() = t("قلمِ آزاد (بی کارتِ کالا)", "Custom item (no product card)", "آزاد توکی (بې د توکي کارته)")
  //  CRM-03 (§۱۰.۱): تغییرِ سقفِ اعتبارِ مشتری (سرور با پرچمِ `shop.credit_limit_perm` می‌سنجد؛ برگهٔ مشتری `CreditLimitGate`)
  val permCreditLimit: String get() = t("تغییرِ سقفِ اعتبارِ مشتری", "Changing a customer's credit limit", "د پېرودونکي د پور حد بدلول")

  //  STF-01: نقشِ سفارشی — نام + یکی از نقش‌های کارمند + مجوز و سقفِ خودش (سرور با پرچمِ `shop.custom_roles`)
  val roleCashier: String get() = t("صندوق‌دار", "Cashier", "صندوق‌دار")
  val roleStorekeeper: String get() = t("مسئولِ انبار", "Storekeeper", "ګدام‌دار")
  val rolePicker: String get() = t("جمع‌آورِ سفارش", "Order picker", "د فرمایش راټولوونکی")
  val roleAccountant: String get() = t("حسابدار (فقط دیدن)", "Accountant (view only)", "محاسب (یوازې لیدل)")
  val customRolesTitle: String get() = t("نقش‌های سفارشی", "Custom roles", "ځانګړي رولونه")
  val customRolesHelper: String get() = t(
    "هر نقش یکی از نقش‌های کارمند است با مجوز و سقفِ خودش، هرگز بیش از همان نقش. سرور همین را اجرا می‌کند.",
    "Each role is one of the staff roles with its own permissions and limits, never more than that role. The server enforces it.",
    "هر رول د کارکوونکو یو رول دی له خپلو اجازو او حدونو سره، هېڅکله له هماغه رول ډېر نه. سرور همدا پلي کوي.",
  )
  val customRoleNew: String get() = t("نقشِ تازه", "New role", "نوی رول")
  val customRoleEdit: String get() = t("ویرایشِ نقش", "Edit role", "رول سمول")
  val customRoleName: String get() = t("نامِ نقش", "Role name", "د رول نوم")
  val customRoleNameBad: String get() = t("نام لازم است (حداکثر ۴۰ نویسه)", "A name is required (up to 40 characters)", "نوم اړین دی (تر ۴۰ تورو)")
  val customRoleBase: String get() = t("بر پایهٔ نقشِ", "Based on role", "د رول پر بنسټ")
  fun customRoleSubtitle(base: String, n: Int): String =
    t("بر پایهٔ ${asIs(base)} · ${asIs(n)} کارمند", "Based on $base · $n ${pl(n, "member", "members")}", "د $base پر بنسټ · $n کارکوونکي")
  val customRoleNone: String get() = t("بی نقشِ سفارشی", "No custom role", "بې ځانګړي رول")
  val customRoleSection: String get() = t("نقشِ سفارشی", "Custom role", "ځانګړی رول")
  val customRoleMemberHelper: String get() = t(
    "نقشِ کارمند همان نقشِ پایه می‌شود؛ مجوزِ جداگانهٔ همین کارمند روی آن می‌نشیند.",
    "The member's role becomes the base role; this member's own permissions apply on top.",
    "د کارکوونکي رول هماغه بنسټیز رول کېږي؛ د همدې کارکوونکي خپلې اجازې پرې پلي کېږي.",
  )
  val customRoleDelete: String get() = t("پاک کردنِ نقش", "Delete role", "رول ړنګول")
  fun customRoleDeleteTitle(name: String): String = t("«${asIs(name)}» پاک شود؟", "Delete “$name”?", "«$name» دې ړنګ شي؟")
  val customRoleDeleteBody: String get() = t(
    "کارمندانِ این نقش روی همان نقشِ پایه با مجوزهای امروزشان می‌مانند.",
    "Members with this role stay on the base role with their current permissions.",
    "د دې رول کارکوونکي پر هماغه بنسټیز رول له اوسنیو اجازو سره پاتې کېږي.",
  )
  val customRoleSaved: String get() = t("نقش ذخیره شد", "Role saved", "رول خوندي شو")
  val customRoleDeleted: String get() = t("نقش پاک شد", "Role deleted", "رول ړنګ شو")
  val customRoleAssigned: String get() = t("نقشِ کارمند عوض شد", "Member's role changed", "د کارکوونکي رول بدل شو")
  val customRolePerms: String get() = t("مجوزها و سقف‌ها", "Permissions and limits", "اجازې او حدونه")
  val devicesTitle: String get() = t("دستگاه‌ها", "Devices", "وسایل")
  val noDevices: String get() = t("دستگاهی ثبت نشده است.", "No devices registered.", "هېڅ وسیله نه ده ثبت.")
  val device: String get() = t("دستگاه", "Device", "وسیله")
  val staffMember: String get() = t("کارمند", "Staff member", "کارکوونکی")
  val deviceThis: String get() = t("همین دستگاه", "This device", "همدا وسیله")
  val deviceOut: String get() = t("خارج‌شده", "Signed out", "وتلې")
  val deviceBlocked: String get() = t("از کار افتاده", "Disabled", "بنده شوې")
  fun blockedAttempts(n: Any): String = t("${asIs(n)} تلاشِ ردشده", "$n blocked ${pl(n, "attempt", "attempts")}", "$n رد شوې هڅې")
  fun lastSeen(date: Any): String = t("آخرین بار: ${asIs(date)}", "Last seen: $date", "وروستی ځل: $date")
  val block: String get() = t("از کار انداختن", "Disable", "بندول")
  val unblock: String get() = t("برگرداندن", "Re-enable", "بیرته فعالول")
  val blockTitle: String get() = t("از کار انداختنِ این دستگاه", "Disable this device", "دا وسیله بندول")
  val blockBody: String get() = t("همهٔ نشست‌های این دستگاه بسته می‌شود و از این پس هیچ چیزی از آن پذیرفته نمی‌شود. فروش‌هایی که هنوز بالا نرفته‌اند روی خودِ دستگاه و در پشتیبانِ آن می‌مانند.", "Every session on this device is closed and nothing from it is accepted from now on. Sales not yet uploaded stay on the device and in its backup.", "د دې وسیلې ټولې ناستې بندېږي او له دې وروسته ترې هېڅ نه منل کېږي. هغه پلورونه چې لا نه دي پورته شوي په وسیله او د هغې په بیک اپ کې پاتې کېږي.")
  val blockConfirm: String get() = t("از کار بینداز", "Disable", "بند یې کړه")
  val deviceDisabled: String get() = t("دستگاه از کار افتاد", "Device disabled", "وسیله بنده شوه")
  val deviceEnabled: String get() = t("دستگاه برگشت؛ کارمند باید دوباره وارد شود", "Device re-enabled; the staff member must sign in again", "وسیله بیرته فعاله شوه؛ کارکوونکی باید بیا ننوځي")
  val memberMissing: String get() = t("این کارمند پیدا نشد", "This staff member wasn't found", "دا کارکوونکی ونه موندل شو")

  // دستگاه‌های شناختهٔ ورود با کد (رازِ دستگاه، `GET /api/shop/staff-keys`)
  val deviceKeysTitle: String get() = t("دستگاه‌های ورود با کد", "Code sign-in devices", "د کوډ ننوتلو وسایل")
  val deviceKeysHelper: String get() = t(
    "این دستگاه‌ها با کدِ شاگرد بی کدِ تازه برمی‌گردند. باطل کردن نشست‌های همان دستگاه را هم می‌بندد؛ فروش و سابقه می‌ماند.",
    "These devices can sign back in with the staff code without a new code. Revoking also closes that device's sessions; sales and history stay.",
    "دا وسایل د شاګرد په کوډ پرته له نوي کوډه بېرته ننوځي. باطلول د همدې وسیلې ناستې هم بندوي؛ پلورونه او تاریخچه پاتې کېږي.",
  )
  fun deviceKeyMeta(id: Any, used: Any, created: Any): String = t(
    "${asIs(id)} · آخرین استفاده: ${asIs(used)} · ساخته: ${asIs(created)}",
    "$id · Last used: $used · Added: $created",
    "$id · وروستۍ کارونه: $used · جوړ شو: $created",
  )
  val notUsedYet: String get() = t("هنوز نه", "Not yet", "لا نه")
  val revokeDeviceKey: String get() = t("باطل کردنِ این دستگاه", "Revoke this device", "دا وسیله باطلول")
  val revokeDeviceKeyBody: String get() = t(
    "این دستگاه دیگر با کدِ شاگرد برنمی‌گردد و نشست‌هایش همین حالا بسته می‌شود. برای برگشت کدِ تازه بدهید؛ آن هم حسابِ تازه می‌سازد. فروش و سابقهٔ کارمند می‌ماند.",
    "This device can no longer sign back in with the staff code and its sessions close now. To return, give a new code; it creates a new account. The member's sales and history stay.",
    "دا وسیله نور د شاګرد په کوډ بېرته نه ننوځي او ناستې یې همدا اوس بندېږي. د بېرته راتګ لپاره نوی کوډ ورکړئ؛ هغه نوی حساب جوړوي. د کارکوونکي پلورونه او تاریخچه پاتې کېږي.",
  )
  val revokeDeviceKeyConfirm: String get() = t("باطل کن", "Revoke", "باطل یې کړه")
  val deviceKeyRevoked: String get() = t("دستگاه باطل شد", "Device revoked", "وسیله باطله شوه")
  val logoutEverywhere: String get() = t("خروج از همهٔ دستگاه‌ها", "Sign out of all devices", "له ټولو وسایلو وتل")
  fun logoutEverywhereTitle(name: Any): String = t("خروجِ ${asIs(name)} از همهٔ دستگاه‌ها؟", "Sign $name out of all devices?", "$name له ټولو وسایلو وباسئ؟")
  val logoutEverywhereBody: String get() = t(
    "همهٔ نشست‌های این کارمند بسته و دستگاه‌هایش باطل می‌شوند؛ برای برگشت کدِ تازه لازم است. فروش و سابقه‌اش می‌ماند.",
    "All of this member's sessions close and their devices are revoked; a new code is needed to return. Their sales and history stay.",
    "د دې کارکوونکي ټولې ناستې بندېږي او وسایل یې باطلېږي؛ د بېرته راتګ لپاره نوی کوډ پکار دی. پلورونه او تاریخچه یې پاتې کېږي.",
  )
  val logoutEverywhereConfirm: String get() = t("بیرون کن", "Sign out", "ویې باسه")
  val loggedOutEverywhere: String get() = t("کارمند از همهٔ دستگاه‌ها بیرون رفت", "Signed out of all devices", "له ټولو وسایلو ووت")
  // STF-04 «PIN ِ صندوق» ِ هر کارمند (صفحهٔ کارمند؛ پرچمِ `shop.till_pin`)
  val tillPinTitle: String get() = t("PIN ِ صندوق", "Till PIN", "د صندوق PIN")
  val tillPinHelp: String get() = t(
    "پس از پنج PIN ِ غلط قفل می‌شود. باز کردن شمارِ خطا را صفر نمی‌کند؛ برداشتن یعنی کارمند تا PIN ِ تازه صندوق را باز نمی‌کند.",
    "It locks after five wrong PINs. Unlocking does not reset the failure count; removing means the member cannot open the till until a new PIN is set.",
    "له پنځو غلطو PINونو وروسته تړل کېږي. پرانیستل د تېروتنو شمېر صفر نه کوي؛ لرې کول دا دي چې کارکوونکی تر نوي PIN پورې صندوق نه پرانیزي.",
  )
  val tillPinNone: String get() = t("PIN گذاشته نشده", "No PIN set", "PIN نه دی ایښودل شوی")
  val tillPinSet: String get() = t("PIN گذاشته شده", "PIN set", "PIN ایښودل شوی")
  val tillPinVoid: String get() = t("PIN باطل شده؛ PIN ِ تازه لازم است", "PIN revoked; a new PIN is needed", "PIN باطل شوی؛ نوی PIN پکار دی")
  val tillPinLocked: String get() = t("قفل است (PIN ِ غلطِ پیاپی)", "Locked (repeated wrong PIN)", "تړل شوی دی (پرله‌پسې غلط PIN)")
  val tillPinUnlock: String get() = t("باز کردنِ قفل", "Unlock", "قفل پرانیستل")
  val tillPinUnlocked: String get() = t("قفلِ PIN باز شد", "PIN unlocked", "د PIN قفل پرانیستل شو")
  val tillPinRemove: String get() = t("برداشتنِ PIN", "Remove PIN", "PIN لرې کول")
  fun tillPinRemoveTitle(name: Any): String = t("PIN ِ ${asIs(name)} برداشته شود؟", "Remove $name's PIN?", "د $name PIN لرې شي؟")
  val tillPinRemoveBody: String get() = t(
    "نوبتِ بازِ صندوقِ او هم بسته می‌شود؛ تا PIN ِ تازه با PIN صندوق را باز نمی‌کند.",
    "Their open till session is closed too; they cannot open the till with a PIN until a new one is set.",
    "د هغه پرانیستې صندوق نوبت هم تړل کېږي؛ تر نوي PIN پورې صندوق په PIN نه پرانیزي.",
  )
  val tillPinRemoved: String get() = t("PIN برداشته شد", "PIN removed", "PIN لرې شو")
  fun staleHint(name: Any): String = t("شاید همان ${asIs(name)} است با دستگاهِ تازه", "Maybe the same $name on a new device", "شاید همغه $name وي په نوې وسیله")
  fun staleHintBody(device: Any): String = t(
    "دستگاهِ ${asIs(device)} پیش‌تر مالِ عضوِ کهنه بود. حساب‌ها خودبه‌خود یکی نمی‌شوند؛ اگر همان آدم است عضوِ کهنه را بردارید — فروش و سابقه‌اش می‌ماند.",
    "Device $device belonged to the older member before. Accounts are never merged automatically; if it's the same person, remove the old member. Their sales and history stay.",
    "وسیله $device مخکې د زاړه غړي وه. حسابونه پخپله نه یو کېږي؛ که همغه کس وي زوړ غړی لرې کړئ — پلورونه او تاریخچه یې پاتې کېږي.",
  )
  val staleSameName: String get() = t("نامش هم یکی است.", "The name matches too.", "نوم یې هم یو دی.")
  val removeStale: String get() = t("برداشتنِ عضوِ کهنه", "Remove the old member", "زوړ غړی لرې کول")
  fun removeStaleTitle(name: Any): String = t("برداشتنِ عضوِ کهنه (${asIs(name)})؟", "Remove the old member ($name)?", "زوړ غړی ($name) لرې شي؟")
  val removeStaleBody: String get() = t(
    "عضوِ کهنه از دکان برداشته می‌شود و دیگر وارد نمی‌شود. حساب‌ها یکی نمی‌شوند؛ فروش و سابقه‌اش پاک نمی‌شود.",
    "The old member is removed from the shop and can no longer sign in. Accounts are not merged; their sales and history are not deleted.",
    "زوړ غړی له دوکانه لرې کېږي او نور نه شي ننوتلی. حسابونه نه یو کېږي؛ پلورونه او تاریخچه یې نه ړنګېږي.",
  )

  // «اشتراکِ من»
  val refreshFromServer: String get() = t("تازه کردن از سرور", "Refresh from server", "له سرور تازه کول")
  val reading: String get() = t("در حالِ خواندن…", "Loading…", "لوستل کېږي…")
  val plan: String get() = t("پلن", "Plan", "پلان")
  fun daysLeft(n: Any): String = t("${asIs(n)} روز مانده", "$n ${pl(n, "day", "days")} left", "$n ورځې پاتې")
  val permanent: String get() = t("دائمی ✓", "Permanent ✓", "تلپاتې ✓")
  val starts: String get() = t("آغاز", "Start", "پیل")
  val ends: String get() = t("پایان", "End", "پای")
  val status: String get() = t("وضعیت", "Status", "حالت")
  fun warnTitle(n: Any): String = t("${asIs(n)} روز تا پایانِ اشتراک", "$n ${pl(n, "day", "days")} until the subscription ends", "د ګډون تر پایه $n ورځې")
  /** S47: همان هشدار با روزِ دقیقِ پایان (`day` از `AccountFormat.date`، جدا از جهتِ جمله) */
  fun warnTitleOn(n: Any, day: String): String = t(
    "${asIs(n)} روز تا پایانِ اشتراک — روزِ \u2066${asIs(day)}\u2069",
    "$n ${pl(n, "day", "days")} until the subscription ends — on $day",
    "د ګډون تر پایه $n ورځې — \u2066$day\u2069",
  )
  val warnBody: String get() = t("پس از آن برنامه فقط‌خواندنی می‌شود — هیچ داده‌ای پاک نمی‌شود و خروجی و چاپ هم کار می‌کند.", "After that the app becomes read-only — no data is deleted and export and printing keep working.", "له هغې وروسته اپ یوازې د لوستلو کېږي — هېڅ معلومات نه ړنګېږي او صادرول او چاپ کار کوي.")
  val expiredBody: String get() = t("برنامه فقط‌خواندنی است — دفترتان دست‌نخورده سرِ جایش است و خروجی و چاپ هم کار می‌کند.", "The app is read-only — your ledger is untouched and export and printing still work.", "اپ یوازې د لوستلو دی — دفتر مو روغ پاتې دی او صادرول او چاپ کار کوي.")
  val nothingYet: String get() = t("هنوز چیزی از سرور نیامده. اینترنت که وصل شد، همین‌جا پر می‌شود.", "Nothing has come from the server yet. Once you're online it fills in here.", "لا له سرور څه نه دي راغلي. انټرنیټ چې وصل شو دلته ډکېږي.")
  val subSignInBody: String get() = t("برای دیدنِ اشتراک، اول با ایمیل وارد شوید.", "Sign in with your email to see your subscription.", "د ګډون لیدو لپاره لومړی په ایمیل ننوځئ.")
  val codeSignIn: String get() = t("ورود با ایمیل و کدِ شش‌رقمی", "Sign in with email and a 6-digit code", "په ایمیل او شپږ عددي کوډ ننوتل")
  val activeDevices: String get() = t("دستگاه‌های فعال", "Active devices", "فعالې وسیلې")
  val noActiveDevices: String get() = t("دستگاهی ثبت نشده.", "No devices registered.", "هېڅ وسیله نه ده ثبت.")
  val payments: String get() = t("پرداخت‌ها", "Payments", "تادیات")
  val noPayments: String get() = t("پرداختی ثبت نشده.", "No payments recorded.", "هېڅ تادیه نه ده ثبت.")
  val adminNotices: String get() = t("پیام‌های مدیر", "Messages from the admin", "د مدیر پیغامونه")
  val noNotices: String get() = t("پیامی نیست.", "No messages.", "هېڅ پیغام نشته.")

  // «اشتراک و قیمت‌ها»
  fun trialInviteTitle(n: Any): String = t("${asIs(n)} روز رایگان", "$n days free", "$n ورځې وړیا")
  fun trialInviteBody(n: Any): String = t("حساب بسازید و همهٔ قابلیت‌ها را ${asIs(n)} روز رایگان امتحان کنید. اطلاعاتی که ثبت می‌کنید در حساب خودتان می‌ماند.", "Create an account and try every feature free for $n days. What you record stays in your own account.", "حساب جوړ کړئ او ټولې ځانګړنې $n ورځې وړیا وازمویئ. هر څه چې ثبتوئ ستاسو په خپل حساب کې پاتې کېږي.")
  val trialTitle: String get() = t("دورهٔ آزمایشی", "Trial period", "ازمایښتي موده")
  val activeTitle: String get() = t("اشتراک فعال است", "Subscription is active", "ګډون فعال دی")
  val endingTitle: String get() = t("اشتراک رو به پایان است", "Subscription is ending", "ګډون پای ته نږدې دی")
  val graceTitle: String get() = t("اشتراک تمام شده — مهلتِ تمدید", "Subscription ended — renewal grace period", "ګډون پای ته ورسېد — د تمدید مهلت")
  val pendingPlanTitle: String get() = t("اشتراک هنوز شروع نشده", "Subscription hasn't started yet", "ګډون لا نه دی پیل شوی")
  val invalidTitle: String get() = t("مجوزِ اشتراک خوانده نشد", "The subscription licence couldn't be read", "د ګډون جواز ونه لوستل شو")
  val lastDay: String get() = t("امروز آخرین روز است", "Today is the last day", "نن وروستۍ ورځ ده")
  val renewNow: String get() = t("تمدید کنید", "Please renew", "تمدید یې کړئ")
  val trialBody: String get() = t("تا پایانِ این مدت همهٔ قابلیت‌ها باز است؛ برای ادامه، یکی از پلن‌های پایین را بگیرید.", "Every feature is open until then; to continue, pick one of the plans below.", "تر دې مودې پورې ټولې ځانګړنې پرانیستې دي؛ د دوام لپاره له لاندې پلانونو یو واخلئ.")
  val activeBody: String get() = t("برای ادامه، پیش از تمام شدن تمدید کنید.", "To continue, renew before it ends.", "د دوام لپاره مخکې له پایه تمدید کړئ.")
  fun untilDate(date: Any): String = t("تا تاریخ ${asIs(date)}", "Until $date", "تر $date پورې")
  fun endedOn(date: Any): String = t("پایان: ${asIs(date)}", "Ended: $date", "پای: $date")
  fun planName(name: Any): String = t("پلن: ${asIs(name)}", "Plan: $name", "پلان: $name")
  val planMonthly: String get() = t("ماهانه", "Monthly", "میاشتنی")
  val planSixMonths: String get() = t("۶ ماهه", "6 months", "۶ میاشتنی")
  val planYearly: String get() = t("۱ ساله", "1 year", "یو کلن")
  val badgeRecommended: String get() = t("پیشنهاد ما", "Recommended", "زموږ وړاندیز")
  val badgeBestValue: String get() = t("بیشترین صرفه", "Best value", "تر ټولو ګټور")
  val chooseDuration: String get() = t("مدت اشتراک را انتخاب کنید", "Choose the subscription length", "د ګډون موده وټاکئ")
  fun perDay(amount: Any): String = t("روزی ${asIs(amount)}", "$amount a day", "ورځنی $amount")
  fun percentOff(n: Any): String = t("٪${asIs(n)} تخفیف", "$n% off", "$n٪ تخفیف")
  val selectedMark: String get() = t("انتخاب شد", "Selected", "وټاکل شو")
  val freeAlways: String get() = t("همیشه رایگان", "Always free", "تل وړیا")
  val withSubscription: String get() = t("با اشتراک باز می‌شود", "Opens with a subscription", "په ګډون پرانیستل کېږي")
  val featStock: String get() = t("انبار و موجودی", "Warehouse & stock", "ګدام او موجودي")
  val featExpenses: String get() = t("مصارف دکان", "Shop expenses", "د دوکان لګښتونه")
  val featPurchasing: String get() = t("خریداری و تأمین‌کننده", "Purchasing & suppliers", "پېرود او عرضه‌کوونکي")
  val featReports: String get() = t("گزارش‌ها و سود", "Reports & profit", "راپورونه او ګټه")
  val featAudit: String get() = t("دفتر رویدادها", "Activity log", "د پېښو دفتر")
  val featBackup: String get() = t("پشتیبان‌گیری", "Backups", "بیک اپ")
  val featExcel: String get() = t("خروجی اکسل", "Excel export", "اکسل ته صادرول")
  val featSale: String get() = t("فروش (صندوق)", "Sale (register)", "پلور (صندوق)")
  val featDebtors: String get() = t("قرض‌داران", "Debtors", "پوروړي")
  val featScanner: String get() = t("اسکنر بارکد", "Barcode scanner", "د بارکوډ سکنر")
  val featMultiUser: String get() = t("چند کاربر روی یک دکان", "Several users on one shop", "په یوه دوکان څو کاروونکي")
  val buySignInTitle: String get() = t("برای خرید اشتراک اول وارد حساب شوید", "Sign in first to buy a subscription", "د ګډون د اخیستو لپاره لومړی ننوځئ")
  val buySignInBody: String get() = t("کار رایگان با برنامه حساب نمی‌خواهد — فقط خرید اشتراک لازم دارد.", "Free use of the app needs no account — only buying a subscription does.", "د اپ وړیا کار حساب نه غواړي — یوازې د ګډون اخیستل یې غواړي.")
  fun buyCta(plan: Any, price: Any): String = t("گرفتن اشتراک ${asIs(plan)} — ${asIs(price)}", "Get the $plan plan — $price", "د $plan ګډون اخیستل — $price")
  val buyFootnote: String get() = t("بدون قرارداد. بدون ریسک. هماهنگی و پرداخت از راه واتساپ انجام می‌شود.", "No contract. No risk. Arrangement and payment happen over WhatsApp.", "بې قرارداده. بې خطره. همغږي او تادیه د واټس اپ له لارې کېږي.")
  val bazaarNote: String get() = t("اشتراکِ حسابِ شما خودکار همین‌جا دیده می‌شود؛ کار با انبار، کالا، خرید و مصارف همیشه رایگان است.", "Your account's subscription shows up here automatically; warehouse, items, purchases and expenses are always free.", "ستاسو د حساب ګډون په خپله دلته ښکاري؛ ګدام، توکي، پېرود او لګښتونه تل وړیا دي.")
  val haveCode: String get() = t("کد اشتراک دارم", "I have a subscription code", "د ګډون کوډ لرم")
  val noOnlinePay: String get() = t("پرداخت آنلاین فعلاً ناموجود است", "Online payment isn't available yet", "آنلاین تادیه لا نشته")
  val contactWays: String get() = t("راه‌های تماس", "Contact", "د اړیکې لارې")
  val whatsapp: String get() = t("واتساپ", "WhatsApp", "واټس اپ")

  // کدِ اشتراک (برگه)
  val sixDigitCode: String get() = t("کدِ شش‌رقمی", "6-digit code", "شپږ عددي کوډ")
  val sixDigitHint: String get() = t("کدِ شش‌رقمی که به ایمیلتان آمده", "The 6-digit code that came to your email", "هغه شپږ عددي کوډ چې ایمیل ته مو راغلی")
  val activate: String get() = t("فعال کردن", "Activate", "فعالول")
  val activated: String get() = t("اشتراک شما فعال شد.", "Your subscription is active.", "ستاسو ګډون فعال شو.")
  val redeemSignIn: String get() = t("برای فعال کردنِ کد، اول وارد حساب شوید — اشتراک روی دکانِ شما می‌نشیند.", "Sign in first to activate the code — the subscription goes on your shop.", "د کوډ فعالولو لپاره لومړی ننوځئ — ګډون ستاسو په دوکان کېني.")
  val offlineCode: String get() = t("کدِ آفلاین", "Offline code", "آفلاین کوډ")
  val offlineTitle: String get() = t("کدِ اشتراکِ آفلاین (بی اینترنت)", "Offline subscription code (no internet)", "آفلاین د ګډون کوډ (بې انټرنیټه)")
  fun offlineActive(plan: Any): String = t("فعال — ${asIs(plan)}", "Active — $plan", "فعال — $plan")
  val offlineHint: String get() = t("کدی که فقط روی همین کامپیوتر کار می‌کند", "A code that works only on this computer", "هغه کوډ چې یوازې په همدې کمپیوټر کار کوي")
  val computerId: String get() = t("شناسهٔ این کامپیوتر — همین را برای فروشنده بفرستید:", "This computer's ID — send it to the seller:", "د دې کمپیوټر پېژند — همدا پلورونکي ته ولېږئ:")
  val idCopied: String get() = t("شناسه رونوشت شد", "ID copied", "پېژند کاپي شو")
  val offlineField: String get() = t("کدِ اشتراکِ آفلاین (۱۳۸ نویسه)", "Offline subscription code (138 characters)", "آفلاین د ګډون کوډ (۱۳۸ توري)")
  val applyCode: String get() = t("ثبتِ کد", "Apply code", "کوډ ثبتول")
  fun offlineOk(span: Any): String = t("اشتراک روی همین کامپیوتر باز شد${asIs(span)}. اینترنت که وصل شد، خودش به حسابتان ثبت می‌شود.", "The subscription is open on this computer$span. Once online it's added to your account by itself.", "ګډون په همدې کمپیوټر پرانیستل شو$span. انټرنیټ چې وصل شو، په خپله ستاسو حساب ته ثبتېږي.")
  val offlinePermanent: String get() = t(" — دائمی", " — permanent", " — تلپاتې")
  val offlineOnce: String get() = t("هر کد فقط برای همین شناسه و فقط یک بار است؛ روی کامپیوترِ دیگر یا دوباره پذیرفته نمی‌شود.", "Each code is for this ID only and works once; it isn't accepted on another computer or twice.", "هر کوډ یوازې د همدې پېژند لپاره او یو ځل دی؛ په بل کمپیوټر یا دوهم ځل نه منل کېږي.")

  // قفلِ بخشِ پولی
  fun lockedNeedAccount(label: Any): String = t("برای «${asIs(label)}» باید حساب بسازید", "“$label” needs an account", "«$label» حساب غواړي")
  fun lockedNeedPlan(label: Any): String = t("«${asIs(label)}» با اشتراک باز می‌شود", "“$label” opens with a subscription", "«$label» په ګډون پرانیستل کېږي")
  val lockedAccountBody: String get() = t("ساختنِ حساب مجانی است و هفت روز آزمایشی دارد — با آن همه‌چیز باز می‌شود. بقیهٔ برنامه هم بدونِ حساب باز است: انبار، مصارف، خرید، گزارش‌ها و پشتیبان‌گیری.", "Creating an account is free and comes with a seven-day trial that opens everything. The rest of the app works without an account: warehouse, expenses, purchases, reports and backups.", "حساب جوړول وړیا دي او اوه ورځې ازمایښت لري — ټول شیان پرانیزي. پاتې اپ بې حسابه هم پرانیستی دی: ګدام، لګښتونه، پېرود، راپورونه او بیک اپ.")
  val lockedPlanBody: String get() = t("بقیهٔ برنامه — انبار، مصارف، خرید، گزارش‌ها و پشتیبان‌گیری — باز است و همیشه باز می‌ماند.", "The rest of the app — warehouse, expenses, purchases, reports and backups — is open and always stays open.", "پاتې اپ — ګدام، لګښتونه، پېرود، راپورونه او بیک اپ — پرانیستی دی او تل پرانیستی پاتې کېږي.")
  //  O-09: اشتراک هست ولی جای دستگاه نیست (`device_limit`) — راه «دستگاه‌ها» است، نه خریدِ اشتراک
  fun lockedDeviceFull(label: Any): String = t("«${asIs(label)}» روی این دستگاه بسته است", "“$label” is closed on this device", "«$label» په دې وسیله تړل شوی")
  val lockedDeviceBody: String get() = t("اشتراکِ دکان برای همهٔ دستگاه‌هایش پر است. صاحبِ دکان یک دستگاهِ کهنه را برمی‌دارد تا این‌جا باز شود؛ بقیهٔ برنامه همیشه باز است.", "The shop's plan has no free device slot. The shop owner removes an old device to open it here; the rest of the app always stays open.", "د دوکان ګډون د ټولو وسایلو لپاره ډک دی. د دوکان خاوند یوه زړه وسیله لرې کوي څو دلته پرانیستل شي؛ پاتې اپ تل پرانیستی دی.")

  // پشتیبانی
  val supportSubtitle: String get() = t("معمولاً همان روز جواب می‌گیرید", "You usually get an answer the same day", "معمولاً همدا ورځ ځواب ترلاسه کوئ")
  val supportIntro: String get() = t("هر مشکلی یا سؤالی دارید همین‌جا بنویسید — پاسخ می‌دهیم.", "Write any problem or question here — we'll answer.", "هره ستونزه یا پوښتنه چې لرئ همدلته ولیکئ — ځواب درکوو.")
  val messageHint: String get() = t("پیام شما…", "Your message…", "ستاسو پیغام…")
  val send: String get() = t("فرستادن", "Send", "لېږل")
  val systemSender: String get() = t("خبر از فروشگاه من", "News from My Shop", "د زما دوکان خبر")
  val notSent: String get() = t("فرستاده نشد", "Not sent", "ونه لېږل شو")

  // حذفِ حساب (قاعدهٔ Google Play؛ `DeleteAccountSheet`، سرور: `routes/account-deletion.js`)
  val deleteAccount: String get() = t("حذفِ حساب", "Delete account", "حساب ړنګول")
  val deleteAccountLine: String get() = t("حساب و دادهٔ روی سرور برای همیشه پاک می‌شود", "Removes the account and its server data for good", "حساب او د سرور معلومات د تل لپاره ړنګېږي")
  val privacyPolicy: String get() = t("حریمِ خصوصی", "Privacy policy", "د محرمیت تګلاره")
  val privacyLine: String get() = t("چه داده‌ای جمع می‌شود و چطور پاکش کنید", "What data is collected and how to delete it", "کوم معلومات راټولېږي او څنګه یې ړنګ کړئ")
  val deleteTitle: String get() = t("حذفِ همیشگیِ حساب؟", "Delete your account for good?", "حساب د تل لپاره ړنګ شي؟")
  val deleteBody: String get() = t("خودِ حساب، دکان‌هایی که صاحبشان هستید با همهٔ دادهٔ روی سرورشان (کالا، فروش، مشتری، قرض، همگام‌سازی و پشتیبان) و همهٔ نشست‌ها و دستگاه‌ها پاک می‌شود. برگشت ندارد.", "The account, the shops you own with all their server data (items, sales, customers, debts, sync and backups) and every session and device are deleted. This can't be undone.", "حساب، هغه دوکانونه چې تاسو یې خاوند یاست د سرور له ټولو معلوماتو سره (توکي، پلور، پېرودونکي، پور، همغږي او بیک اپ) او ټولې ناستې او وسیلې ړنګېږي. بېرته نه راځي.")
  val deleteKept: String get() = td("می‌ماند: سندِ پرداخت‌های اشتراک، سابقهٔ خودِ حذف بی ایمیل و شماره، و نسخه‌های پشتیبانِ کلِ سرور تا پایانِ دوره‌شان. دفترِ روی همین گوشی تا پاک کردنِ برنامه می‌ماند.", "Kept: subscription payment records, a record of the deletion itself without email or phone, and whole-server backups until they expire. The ledger on this phone stays until you uninstall the app.", "پاتې کېږي: د ګډون د تادیو سندونه، د ړنګولو ثبت پرته له ایمیل او شمېرې، او د ټول سرور بیک اپونه تر خپلې مودې پورې. په دې تلیفون کې دفتر تر هغه پاتې کېږي چې اپ ړنګ کړئ.", "Kept: subscription payment records, a record of the deletion itself without email or phone, and whole-server backups until they expire. The ledger on this computer stays until you uninstall the app.", "پاتې کېږي: د ګډون د تادیو سندونه، د ړنګولو ثبت پرته له ایمیل او شمېرې، او د ټول سرور بیک اپونه تر خپلې مودې پورې. په دې کمپیوټر کې دفتر تر هغه پاتې کېږي چې اپ ړنګ کړئ.")
  fun deleteShop(name: Any): String = t("دکانِ «${asIs(name)}» هم پاک می‌شود.", "The shop “$name” is deleted too.", "«$name» دوکان هم ړنګېږي.")
  fun deleteShopStaff(name: Any, others: Any): String = t("دکانِ «${asIs(name)}» هم پاک می‌شود و ${asIs(others)} کارمندِ دیگرش دسترسی را از دست می‌دهند.", "The shop “$name” is deleted too and its $others other staff lose access.", "«$name» دوکان هم ړنګېږي او د هغه $others نور کارکوونکي لاسرسی له لاسه ورکوي.")
  val deleteTypeEmail: String get() = t("برای تأیید، ایمیلِ حساب را بنویسید", "To confirm, type the account email", "د تایید لپاره د حساب ایمیل ولیکئ")
  val deleteTypePhone: String get() = t("برای تأیید، شمارهٔ حساب را بنویسید", "To confirm, type the account phone number", "د تایید لپاره د حساب شمېره ولیکئ")
  val deleteTypeId: String get() = t("برای تأیید، شناسهٔ حساب را بنویسید", "To confirm, type the account ID", "د تایید لپاره د حساب پېژند ولیکئ")
  fun deleteTypeHelper(value: Any): String = t("همین را بنویسید: ${asIs(value)}", "Type exactly: $value", "همدا ولیکئ: $value")
  val deleteAction: String get() = t("حذفِ همیشگی", "Delete for good", "د تل لپاره ړنګول")
  val deleteSupportTitle: String get() = t("حذف با پشتیبانی", "Deletion through support", "د ملاتړ له لارې ړنګول")
  val deleteSupportBody: String get() = t("حذف از خودِ برنامه هنوز روشن نشده است. درخواستتان را به پشتیبانی بفرستید تا حساب و داده‌هایش را پاک کنیم.", "Deleting from the app isn't switched on yet. Send your request to support and we'll delete the account and its data.", "له اپ څخه ړنګول لا نه دي فعال شوي. خپله غوښتنه ملاتړ ته ولېږئ چې حساب او معلومات یې ړنګ کړو.")
  val deletePumpBody: String get() = t("این حساب پمپ هم دارد؛ حذفش را پشتیبانی انجام می‌دهد تا دادهٔ پمپ بی‌خبر پاک نشود.", "This account also has a fuel station; support deletes it so the station's data isn't removed by surprise.", "دا حساب پمپ هم لري؛ ړنګول یې ملاتړ کوي چې د پمپ معلومات بې خبره ړنګ نه شي.")
  val deleteAskSupport: String get() = t("درخواست از پشتیبانی", "Ask support", "له ملاتړ غوښتنه")
  val deleteSupportMessage: String get() = t("درخواستِ حذفِ حساب: لطفاً حسابِ من و داده‌هایش را از سرور پاک کنید.", "Account deletion request: please delete my account and its data from the server.", "د حساب د ړنګولو غوښتنه: مهرباني وکړئ زما حساب او معلومات یې له سرور ړنګ کړئ.")
  val deleteRequested: String get() = t("درخواستِ حذف به پشتیبانی رفت", "Deletion request sent to support", "د ړنګولو غوښتنه ملاتړ ته ولېږل شوه")
  val deleteLoadFailed: String get() = t("اطلاعاتِ حذف نیامد؛ اینترنت را ببینید و دوباره بزنید.", "Couldn't load the deletion details; check the internet and try again.", "د ړنګولو معلومات رانغلل؛ انټرنېټ وګورئ او بیا هڅه وکړئ.")
  val deleteRetry: String get() = t("دوباره", "Retry", "بیا")
  val deleteDone: String get() = t("حساب و داده‌هایش از سرور پاک شد", "The account and its data were deleted from the server", "حساب او معلومات یې له سرور ړنګ شول")
}
