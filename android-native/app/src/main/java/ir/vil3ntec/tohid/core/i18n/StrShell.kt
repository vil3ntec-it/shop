package ir.vil3ntec.tohid.core.i18n

/**
 *  پوستهٔ نسخهٔ ۲ (منوی کناری و سربرگِ کامپیوتر، «بیشتر»، تأییدِ دوبارهٔ هویت، نشانِ پیش‌نمایش)، بخشِ «پرداخت و صندوق» ِ تنظیمات و وصل کردنِ گوگل در صفحهٔ ورود.
 *
 *  ساخته‌شده از جدولِ سه‌زبانه؛ دستی هم ویرایش‌پذیر است. قاعده‌ها سرِ `I18n.kt`.
 *  ترجمه‌های نامطمئن در `docs/i18n-glossary.md` برای بازبینیِ گویشور.
 */
@Suppress("unused")
object StrShell {

  // OPS-03: برنامه دفعهٔ پیش ناگهان بسته شد (MainActivity)
  val crashTitle: String get() = t("برنامه ناگهان بسته شد", "The app closed unexpectedly", "پروګرام ناڅاپه بند شو")
  val crashBody: String get() = t("گزارشِ خطا بی هیچ دادهٔ شخصی برای پشتیبانی فرستاده می‌شود. اگر با پشتیبانی تماس گرفتید، این کد را بگویید:", "An error report with no personal data is being sent to support. If you contact support, give them this code:", "د تېروتنې راپور پرته له شخصي معلوماتو ملاتړ ته لېږل کېږي. که له ملاتړ سره اړیکه نیسئ، دا کوډ ورته ووایاست:")
  val crashOk: String get() = t("باشه", "OK", "سمه ده")

  // منو و سربرگ
  val promos: String get() = t("تخفیف‌ها", "Discounts", "تخفیفونه")
  val promosSub: String get() = t("تخفیفِ خودکار، بخر X بگیر Y، بسته، ساعتِ خوش و کدِ تخفیف", "Automatic discounts, buy X get Y, bundles, happy hours and discount codes", "اتومات تخفیف، X واخلئ Y ترلاسه کړئ، بنډل، د خوښۍ ساعتونه او د تخفیف کوډ")
  val openMenu: String get() = t("بازکردنِ منو", "Expand menu", "مېنو پراخ کړئ")
  val collapseMenuTip: String get() = t("جمع‌کردنِ منو (فقط آیکن)", "Collapse menu (icons only)", "مېنو راټول کړئ (یوازې نښې)")
  val collapseMenu: String get() = t("جمع‌کردنِ منو", "Collapse menu", "مېنو راټول کړئ")
  val keyboardShortcuts: String get() = t("میان‌برهای صفحه‌کلید (F1)", "Keyboard shortcuts (F1)", "د کیبورډ لنډلارې (F1)")
  val newSale: String get() = t("فروشِ تازه", "New sale", "نوی پلور")

  // تنظیمات ← پرداخت و صندوق
  val payAndRegister: String get() = t("پرداخت و صندوق", "Payments & register", "ورکړه او صندوق")
  val payMixed: String get() = t("ترکیبی و شیفت", "Mixed payments & shifts", "ګډه ورکړه او شیفت")
  val payLegacy: String get() = t("نقد و نسیه (امروز)", "Cash and credit (current)", "نغد او پور (اوسنی)")
  val payNote: String get() = t("«ترکیبی» یعنی: نقد و کیف پول/کارت/حواله در یک فاکتور (با شمارهٔ مرجع، به‌صورتِ «ثبتِ دستی، در انتظار»)، پولِ پس فقط از نقد، باقی‌مانده به حسابِ مشتری؛ و صندوقِ شیفت با ورود/خروجِ نقد، کشو بی فروش، گزارش X و بستن با شمارشِ اسکناس. فقط روی همین دستگاه. برنامه‌های قدیمی فاکتورِ تازه را می‌خوانند، ولی پرداختِ کیف‌پولی را در صندوقِ خودشان نقد می‌شمارند.", "“Mixed” means cash and wallet/card/transfer on one invoice (with a reference number, recorded as “manual entry, pending”), change given only from cash and the rest on the customer's account; plus a shift register with cash in/out, no-sale drawer opening, an X report and closing with a banknote count. Only on this device. Older app versions can read the new invoices but count wallet payments as cash in their own register.", "«ګډه» یعني: نغد او بټوه/کارت/حواله په یوه بل کې (د مرجع شمېرې سره، د «لاسي ثبت، په تمه» په توګه)، بېرته پیسې یوازې له نغدو او پاتې د پیرودونکي په حساب؛ او د شیفت صندوق د نغدو داخلولو/ایستلو، بې پلوره د دراز خلاصولو، X راپور او د نوټونو په شمېرلو سره تړلو. یوازې په همدې وسیله. زاړه اپونه نوي بلونه لوستلی شي، خو د بټوې ورکړه په خپل صندوق کې نغد شمېري.")

  // ورود ← وصل کردنِ گوگل
  val codeNotSent: String get() = t("کد فرستاده نشد", "The code wasn't sent", "کوډ ونه لېږل شو")
  val notConnected: String get() = t("وصل نشد", "Couldn't connect", "وصل نه شو")
  val linkGoogleTitle: String get() = t("وصل کردنِ گوگل به حسابِ موجود", "Link Google to your existing account", "ګوګل له موجوده حساب سره وصل کړئ")
  val linkGoogleNote: String get() = t("این ایمیل از قبل حساب دارد. برای وصل کردنِ گوگل، رمزِ همان حساب یا کدی را که به ایمیل می‌فرستیم وارد کنید.", "This email already has an account. To link Google, enter that account's password or the code we email you.", "دا ایمیل دمخه حساب لري. د ګوګل د وصلولو لپاره، د همغه حساب پټنوم یا هغه کوډ ولیکئ چې ایمیل ته یې درلېږو.")
  val emailCode: String get() = t("کدِ ایمیل", "Email code", "د ایمیل کوډ")
  val thisAccountPassword: String get() = t("رمزِ همین حساب", "This account's password", "د همدې حساب پټنوم")
  val sendCodeInstead: String get() = t("رمز را نمی‌دانم — کد به ایمیل بفرست", "I don't know the password — email me a code", "پټنوم نه پېژنم — ایمیل ته کوډ واستوئ")
  val codeSentToEmail: String get() = t("کد به همین ایمیل رفت.", "The code was sent to this email.", "کوډ همدې ایمیل ته ولېږل شو.")
  val linkAndSignIn: String get() = t("وصل و ورود", "Link and sign in", "وصل کړئ او ننوځئ")
  val dontLink: String get() = t("وصل نکن", "Don't link", "مه یې وصلوئ")

  // تأییدِ دوبارهٔ هویت (ReauthHost)
  val codeSentShort: String get() = t("کد فرستاده شد.", "Code sent.", "کوډ ولېږل شو.")
  fun codeSentTo(d: Any): String = t("کد به $d فرستاده شد.", "Code sent to $d.", "$d ته کوډ ولېږل شو.")
  val reauthTitle: String get() = t("تأییدِ دوبارهٔ هویت", "Confirm it's you", "د هویت بیا تایید")
  val reauthNote: String get() = t("برای امنیتِ حساب، این کار فقط چند دقیقه پس از ورود ممکن است.", "For account security, this can only be done within a few minutes of signing in.", "د حساب د خوندیتوب لپاره، دا کار یوازې له ننوتلو څو دقیقې وروسته کېدای شي.")
  val enterSentCode: String get() = t("کدی را که برایتان فرستاده شد بزنید.", "Enter the code we sent you.", "هغه کوډ ولیکئ چې درته ولېږل شو.")
  val reenterPassword: String get() = t("رمزِ حساب را دوباره بزنید.", "Enter your account password again.", "د حساب پټنوم بیا ولیکئ.")
  val accountPassword: String get() = t("رمزِ حساب", "Account password", "د حساب پټنوم")
  val noPasswordSendCode: String get() = t("رمز ندارم — کد بفرست", "No password — send a code", "پټنوم نه لرم — کوډ واستوئ")
  val verifyCode: String get() = t("کدِ تأیید", "Verification code", "د تایید کوډ")
  val resendCode: String get() = t("فرستادنِ دوبارهٔ کد", "Resend code", "کوډ بیا ولېږئ")
  val confirm: String get() = t("تأیید", "Confirm", "تایید")

  // نشانِ نسخهٔ پیش‌نمایش
  val previewBuild: String get() = t("نسخهٔ پیش‌نمایش", "Preview build", "د مخکتنې نسخه")
  val gotIt: String get() = t("فهمیدم", "Got it", "پوه شوم")
  val previewNote: String get() = t("پیش‌نمایش است؛ برای دیدن، نه کارِ روزانه.", "This is a preview — for a look, not for daily work.", "دا مخکتنه ده؛ د کتلو لپاره، نه د ورځني کار.")

  // کلیدِ روشن/خاموش و «گزارش‌های مشتری» (کامپیوتر)
  val switchOn: String get() = t("روشن", "On", "روښانه")
  val switchOff: String get() = t("خاموش", "Off", "بند")
  val crmReports: String get() = t("گزارش‌های مشتری", "Customer reports", "د پیرودونکو راپورونه")
  //  CRM-03 / S07 — سقفِ اعتبار در صندوق
  val creditGate: String get() = t("سقفِ اعتبار در صندوق", "Credit limit at the register", "په صندوق کې د اعتبار سقف")
  val creditGateOn: String get() = t("بیش از سقف، تأییدِ مدیر", "Over the limit needs a manager", "له سقف پورته، د مدیر تایید")
  val creditGateNote: String get() = t(
    "روشن: فروشِ نسیه‌ای که ماندهٔ مشتری را از سقفِ اعتبارش بیشتر کند، بی تأییدِ مدیر یا صاحبِ دکان ثبت نمی‌شود. صندوقدار «درخواستِ تأیید» می‌زند و مدیر نام، دلیل و PINِ خودش را می‌دهد؛ مدیر و صاحب خودشان با دلیل تأیید می‌کنند. با PINِ غلط یا انصراف هیچ چیز ثبت نمی‌شود و هر تأیید با نامِ تأییدکننده و دلیل در سابقه می‌ماند. خاموش: صندوق سقف را نمی‌سنجد (فقط دفترِ قرض).",
    "On: a credit sale that takes the customer's balance over their credit limit is not recorded without a manager's or the owner's approval. The cashier taps “Request approval” and the manager gives their name, a reason and their PIN; managers and the owner approve their own sales with a reason. A wrong PIN or Cancel records nothing, and every approval is kept in the history with the approver's name and reason. Off: the register does not check the limit (only the debt book does).",
    "روښانه: هغه نسیه پلور چې د پیرودونکي پاتې له سقف څخه ډېروي، د مدیر یا د دوکان د خاوند له تایید پرته نه ثبتېږي. صندوقدار «د تایید غوښتنه» کوي او مدیر خپل نوم، لامل او PIN ورکوي؛ مدیر او خاوند خپل پلور په لامل سره تاییدوي. په غلط PIN یا لغوه کولو هېڅ نه ثبتېږي او هر تایید د تاییدونکي له نوم او لامل سره په تاریخچه کې پاتې کېږي. بند: صندوق سقف نه ګوري (یوازې د پور کتاب).",
  )
  /** کنارِ تنظیم‌هایی که فقط همین دستگاه را می‌بندند (سقفِ اعتبار، قاعده‌های پایانِ فاکتور) — سرور آن‌ها را نمی‌سنجد */
  val thisDeviceOnly: String get() = t(
    "فقط روی همین دستگاه: سرور این را نمی‌سنجد و هر دستگاهِ دیگرِ دکان جدا تنظیم می‌شود.",
    "This device only: the server does not check it, and each of the shop's other devices is set separately.",
    "یوازې په همدې وسیله: سرور یې نه ګوري، او د دوکان هره بله وسیله جلا تنظیمېږي.",
  )
  //  گزارش ← PDF ِ کامپیوتر (RPT-11): بیش از سقفِ صفحه ⇒ PDF ِ ناقص ساخته نمی‌شود
  fun pdfTooManyPages(max: Any): String = t(
    "PDF ساخته نشد: گزارش بیش از $max صفحه است. بازهٔ کوتاه‌تری بگیرید یا خروجیِ Excel بگیرید.",
    "PDF not created: the report is longer than $max pages. Choose a shorter period or export to Excel.",
    "PDF جوړ نه شو: راپور له $max پاڼو اوږد دی. لنډه موده وټاکئ یا Excel ته یې وباسئ.",
  )
  //  RPT-09 — «سابقهٔ ضدتقلب» (`FraudTrailPref`، پیش‌فرض خاموش)
  val fraudTrail: String get() = t("سابقهٔ ضدتقلب", "Anti-fraud trail", "د درغلۍ ضد تاریخچه")
  val fraudTrailOn: String get() = t("لغو، مرجوعی و حذفِ ردیف با نامِ کاربر", "Voids, returns and line deletes with the user's name", "لغوه، بېرته راوړل او د کرښې ړنګول د کارن په نوم")
  val fraudTrailNote: String get() = t(
    "روشن: روی همین دستگاه، لغوِ فاکتور و مرجوعی با نامِ کاربرِ همین دستگاه ثبت می‌شوند، و هر ردیفی که از فاکتورِ باز حذف شود (کلیدِ Delete، کم کردن تا صفر، حذف در پنجرهٔ ردیف یا خالی کردنِ فاکتور) با مبلغش در سابقهٔ دکان می‌ماند. زبانهٔ «ضدتقلب» ِ «گزارشِ تازه» همین‌ها را به تفکیکِ کاربر می‌شمارد. ثبتِ فروش و جابه‌جاییِ فاکتورِ نگه‌داشته حذف شمرده نمی‌شوند. هر صندوق جدا روشن می‌شود. خاموش: مثلِ امروز؛ لغو و مرجوعی «نامعلوم» و حذفِ ردیف صفر دیده می‌شوند.",
    "On: on this device, voided invoices and returns are recorded with this device's user name, and every line removed from an open invoice (the Delete key, reducing to zero, removing it in the line window or clearing the invoice) is kept in the shop history with its amount. The “Anti-fraud” tab of “New report” counts these per user. Recording a sale and switching held invoices are not counted as deletes. Turn it on for each register separately. Off: as today; voids and returns show as “unknown” and line deletes as zero.",
    "روښانه: په همدې وسیله، د بل لغوه کول او بېرته راوړل د همدې وسیلې د کارن په نوم ثبتېږي، او هره کرښه چې له پرانیستي بل څخه لرې شي (د Delete کیلي، تر صفر پورې کمول، د کرښې په کړکۍ کې لرې کول یا د بل خالي کول) له خپلې اندازې سره د دوکان په تاریخچه کې پاتې کېږي. د «نوی راپور» د «د درغلۍ ضد» پاڼه دا د هر کارن له مخې شمېري. د پلور ثبتول او د ساتل شوي بل بدلول ړنګول نه ګڼل کېږي. هر صندوق جلا روښانه کېږي. بند: لکه نن؛ لغوه او بېرته راوړل «نامعلوم» او د کرښې ړنګول صفر ښکاري.",
  )
  val crmShownInReports: String get() = t("در «گزارش‌ها» دیده می‌شوند", "Shown under “Reports”", "په «راپورونه» کې ښکاري")
  val crmNote: String get() = t("چهار زبانهٔ تازه در «گزارش‌ها»: سنِ بدهی (۰–۳۰، ۳۱–۶۰، ۶۱–۹۰، ۹۰+ روز)، تحلیلِ خریدِ مشتریان (RFM)، مشتریانِ تکراری و یادآوریِ امروز. خودِ گزارش‌ها فقط خواندنی‌اند. با روشن بودن، اگر مشتری‌ای ادغام شده باشد، فروش یا قرضی که بعداً روی نامِ کهنه‌اش رسید هم پس از هر همگام‌سازی به همان مشتریِ ماندگار می‌رود. در صفحهٔ فعلیِ گزارش‌ها می‌آیند، نه در «گزارشِ تازه». پس از تغییر، «گزارش‌ها» را دوباره باز کنید.", "Four new tabs under “Reports”: debt age (0–30, 31–60, 61–90, 90+ days), customer purchase analysis (RFM), repeat customers and today's reminders. The reports themselves are read-only. While this is on, if a customer has been merged, a sale or credit that later arrives under the old name is moved to the surviving customer after each sync. They appear on the current reports screen, not in “New report”. After changing this, reopen “Reports”.", "په «راپورونه» کې څلور نوې پاڼې: د پور عمر (۰–۳۰، ۳۱–۶۰، ۶۱–۹۰، ۹۰+ ورځې)، د پیرودونکو د پېرود شننه (RFM)، تکراري پیرودونکي او د نن یادونې. راپورونه پخپله یوازې د لوستلو دي. کله چې دا روښانه وي، که کوم پیرودونکی یوځای شوی وي، هغه پلور یا پور چې وروسته د هغه په زاړه نوم راشي، له هرې همغږۍ وروسته هماغه پاتې پیرودونکي ته ځي. په اوسني راپورونو پاڼه کې راځي، نه په «نوی راپور» کې. له بدلون وروسته «راپورونه» بیا پرانیزئ.")
  // مشتری ← متنِ یادآوریِ بدهی (CRM-06) — جای‌نگهدارها ({نام}…) خودِ قالب‌اند و ترجمه نمی‌شوند
  val reminderTemplate: String get() = t("متنِ یادآوریِ بدهی", "Debt reminder text", "د پور د یادونې متن")
  fun reminderTemplateNote(slots: String): String = t(
    "همین متن در زبانهٔ «یادآوریِ امروز» ِ «گزارش‌ها» و برگهٔ چاپ و اکسلِ آن می‌آید. جای‌نگهدارها: $slots — هر کدام با مقدارِ همان مشتری پر می‌شود.",
    "This text is used in the “Today's reminders” tab under “Reports” and its print and Excel sheet. Placeholders: $slots — each is filled with that customer's value.",
    "همدا متن د «راپورونه» د «د نن یادونې» په پاڼه او د هغې د چاپ او اکسل په پاڼه کې راځي. ځای نیونکي: $slots — هر یو د هماغه پیرودونکي په ارزښت ډکېږي.",
  )
  fun reminderPreview(text: String): String = t("نمونه: $text", "Preview: $text", "بېلګه: $text")
  val reminderSampleName: String get() = t("احمد", "Ahmad", "احمد")
  val reminderSampleStore: String get() = t("دکانِ شما", "Your shop", "ستاسو دوکان")
  val reminderDefault: String get() = t("همان متنِ پیش‌فرض", "Use the default text", "اصلي متن")
  val reminderSaved: String get() = t("متنِ یادآوری ذخیره شد", "Reminder text saved", "د یادونې متن خوندي شو")
  val reminderEmpty: String get() = t("متنِ یادآوری خالی است", "The reminder text is empty", "د یادونې متن خالي دی")
  fun reminderTooLong(max: Int): String = t("متنِ یادآوری حداکثر $max حرف", "The reminder text can have at most $max characters", "د یادونې متن تر $max تورو پورې")
  fun reminderUnknown(slots: String): String = t("جای‌نگهدارِ ناشناخته: $slots", "Unknown placeholder: $slots", "ناپېژندل شوی ځای نیونکی: $slots")
  // «تحلیلِ موجودی» (INV-15، INV-16) — گزارشِ کالاها، پیش‌فرض خاموش
  val stockInsight: String get() = t("تحلیلِ موجودی", "Stock analysis", "د زېرمې شننه")
  val stockInsightShown: String get() = t("در گزارشِ کالاها دیده می‌شود", "Shown in the products report", "د توکو په راپور کې ښکاري")
  val stockInsightNote: String get() = t("در گزارشِ کالاها: ارزشِ موجودی به قیمتِ خرید و به قیمتِ فروش، سودِ بالقوه، و تحلیلِ ABC (کدام کالاها بیشترِ فروش را می‌سازند). فقط خواندنی است و چیزی نمی‌نویسد. سود تقریبی است، چون از قیمتِ خریدِ ثبت‌شدهٔ هر کالا حساب می‌شود. پس از تغییر، «گزارش‌ها» را دوباره باز کنید.", "In the products report: stock value at purchase price and at sale price, potential profit, and ABC analysis (which products make most of your sales). It is read-only and writes nothing. The profit is approximate, because it uses each product's recorded purchase price. After changing this, reopen “Reports”.", "د توکو په راپور کې: د زېرمې ارزښت د پېر په بیه او د پلور په بیه، شونې ګټه، او د ABC شننه (کوم توکي د پلور ډېره برخه جوړوي). یوازې د لوستلو لپاره دی او هېڅ نه لیکي. ګټه اټکلي ده، ځکه چې د هر توکي له ثبت شوې پېر بیې حسابېږي. له بدلون وروسته «راپورونه» بیا پرانیزئ.")
  // «نقطهٔ سفارش و بیشینه» (INV-12) — پیش‌فرض خاموش
  val stockLevels: String get() = t("نقطهٔ سفارش و بیشینه", "Reorder point and maximum", "د امر ټکی او اعظمي کچه")
  val stockLevelsShown: String get() = t("در فرمِ کالا و گزارش‌ها", "In the product form and reports", "د توکي په فورمه او راپورونو کې")
  val stockLevelsNote: String get() = t("برای هر کالا، کنارِ «حد کم بودن»، «نقطهٔ سفارش» (وقتی موجودی به آن رسید، وقتِ سفارش است) و «بیشینه» (سفارش تا همین‌جا پر می‌کند) را بنویسید؛ در کامپیوتر برای چند کالا با هم هم می‌شود. گزارش‌ها فهرستِ «نیاز به سفارش» را با مقدارِ پیشنهادی نشان می‌دهند. سفارشی خودکار ثبت نمی‌شود. پس از تغییر، صفحه را دوباره باز کنید.", "For each product, next to “Low stock level”, set a “Reorder point” (when stock reaches it, it is time to order) and a “Maximum” (an order fills up to it); on the computer you can set them for many products at once. Reports show a “Needs ordering” list with a suggested quantity. No order is placed automatically. After changing this, reopen the page.", "د هر توکي لپاره، د «لږې زېرمې کچه» تر څنګ، «د امر ټکی» (کله چې زېرمه ورته ورسېږي، د امر وخت دی) او «اعظمي کچه» (امر تر همدې ځایه ډکوي) ولیکئ؛ په کمپیوټر کې د څو توکو لپاره یوځای هم کېږي. راپورونه د «امر ته اړتیا» لړ له وړاندیز شوي مقدار سره ښيي. هېڅ امر په خپله نه ثبتېږي. له بدلون وروسته، پاڼه بیا پرانیزئ.")
  // مشتری ← ادغامِ تکراری‌ها (CRM-10)
  val mergeNotOnV3: String get() = t("روی همگام‌سازیِ تازه هنوز نیست", "Not available on the new sync yet", "په نوې همغږۍ کې لا نشته")
  val mergeNotSaved: String get() = t("ادغام ثبت نشد", "The merge wasn't saved", "یوځای کول ثبت نه شو")
  // «گزارش‌های مشتری» در گوشی: پروندهٔ مشتری در «قرض‌داران» (CRM-02/10/11)
  val crmNotePhone: String get() = t(
    "در «قرض‌داران»: شمارهٔ دوم و برچسب در فرمِ مشتری، صافیِ برچسب، و ادغامِ مشتریانِ تکراری. با ادغام هیچ ردیفی پاک نمی‌شود.",
    "In “Debtors”: a second phone and tags on the customer form, a tag filter, and merging duplicate customers. Merging never deletes a record.",
    "په «پوروړي» کې: د پیرودونکي په فورمه کې دویمه شمېره او ټګونه، د ټګ فلټر، او د تکراري پیرودونکو یوځای کول. یوځای کول هېڅ ریکارډ نه ړنګوي.",
  )
}
