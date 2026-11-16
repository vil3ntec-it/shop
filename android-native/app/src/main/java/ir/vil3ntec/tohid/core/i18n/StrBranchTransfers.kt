package ir.vil3ntec.tohid.core.i18n

import ir.vil3ntec.tohid.core.stock.BranchTransfer

/**
 *  INV-11/S21 و POS-16/S17 (فازِ ۴ ِ شعبه‌ها، `docs/BRANCHES-fa.md` §۱۰) — «انتقال میانِ شعبه‌ها» ِ گوشی و کامپیوتر
 *  (`ui/v3/branches/BranchTransfersPages.kt`، `DeskBranchTransfers.kt`) و کلیدهای شعبه‌ایِ صاحب در صفحهٔ «شعبه‌ها».
 */
@Suppress("unused")
object StrBranchTransfers {

  val title: String get() = t("انتقال میانِ شعبه‌ها", "Transfers between branches", "د څانګو ترمنځ لېږد")
  val newTransfer: String get() = t("انتقالِ تازه", "New transfer", "نوی لېږد")
  val editDraft: String get() = t("ویرایشِ پیش‌نویس", "Edit draft", "مسوده سمول")
  val detail: String get() = t("انتقال", "Transfer", "لېږد")
  val loading: String get() = t("از سرور می‌آید…", "Loading from the server…", "له سرور څخه راځي…")
  val failed: String get() = t("از سرور نیامد. اینترنت را ببینید و دوباره بزنید.", "It did not load. Check the internet and try again.", "رانغی. انټرنېټ وګورئ او بیا هڅه وکړئ.")
  val off: String get() = t("انتقال میانِ شعبه‌ها برای این دکان روشن نیست.", "Transfers between branches are not turned on for this shop.", "د څانګو ترمنځ لېږد د دې دوکان لپاره فعال نه دی.")
  val retry: String get() = t("دوباره", "Retry", "بیا")
  val refresh: String get() = t("تازه کردن", "Refresh", "تازه کول")
  val notFound: String get() = t("این انتقال پیدا نشد.", "This transfer was not found.", "دا لېږد پیدا نه شو.")

  //  فهرست
  val incoming: String get() = t("به این شعبه", "To this branch", "دې څانګې ته")
  val outgoing: String get() = t("از این شعبه", "From this branch", "له دې څانګې")
  val drafts: String get() = t("پیش‌نویس‌ها", "Drafts", "مسودې")
  val closed: String get() = t("بسته‌شده", "Closed", "تړل شوي")
  val incomingLabel: String get() = t("در راه به این شعبه", "On the way to this branch", "دې څانګې ته په لاره کې")
  fun summaryCaption(out: Int, drafts: Int): String =
    t("از این شعبه در راه: ${asIs(out)} · پیش‌نویس: ${asIs(drafts)}", "Leaving this branch: $out · Drafts: $drafts", "له دې څانګې په لاره کې: $out · مسودې: $drafts")
  fun toBranch(name: String): String = t("به ${asIs(name)}", "To $name", "$name ته")
  fun fromBranch(name: String): String = t("از ${asIs(name)}", "From $name", "له $name")
  fun rowLine(items: Int, date: String): String = t("${asIs(items)} کالا · ${asIs(date)}", "$items items · $date", "$items توکي · $date")
  val emptyIncoming: String get() = t("هیچ انتقالی در راهِ این شعبه نیست.", "Nothing is on the way to this branch.", "دې څانګې ته هېڅ لېږد په لاره کې نشته.")
  val emptyList: String get() = t("در این فهرست انتقالی نیست.", "There are no transfers in this list.", "په دې لېست کې لېږد نشته.")
  val note: String get() = t(
    "کالا تا دریافت مالِ شعبهٔ فرستنده است و «در راه» ِ همان‌جا دیده می‌شود. موجودیِ همین دستگاه پس از هر گام خودش با سرور هم‌تراز می‌شود.",
    "Goods belong to the sending branch until received and show there as “on the way”. This device’s stock follows the server after each step.",
    "توکي تر ترلاسه کېدو پورې د لېږونکې څانګې دي او هلته «په لاره کې» ښکاري. د دې وسیلې موجودي له هر ګام وروسته له سرور سره برابرېږي.",
  )

  //  وضعیت
  val statusDraft: String get() = t("پیش‌نویس", "Draft", "مسوده")
  val statusInTransit: String get() = t("در راه", "On the way", "په لاره کې")
  val statusPartial: String get() = t("نیمه‌رسیده", "Partly received", "نیمګړی رسېدلی")
  val statusReceived: String get() = t("رسید", "Received", "ترلاسه شو")
  val statusRejected: String get() = t("رد شد", "Rejected", "رد شو")
  val statusCancelled: String get() = t("لغو شد", "Cancelled", "لغوه شو")

  //  فرم
  val destination: String get() = t("به کدام شعبه", "To which branch", "کومې څانګې ته")
  val noDestination: String get() = t("شعبهٔ دیگری برای فرستادن نیست.", "There is no other branch to send to.", "د لېږلو لپاره بله څانګه نشته.")
  val fromPlace: String get() = t("از (این شعبه)", "From (this branch)", "له (دې څانګې)")
  val toPlace: String get() = t("به (شعبهٔ مقصد)", "To (destination branch)", "ته (موخه څانګه)")
  val origin: String get() = t("مبدأ", "Origin", "سرچینه")
  val target: String get() = t("مقصد", "Destination", "موخه")
  val items: String get() = t("کالاها", "Items", "توکي")
  val addProduct: String get() = t("افزودنِ کالا", "Add an item", "توکی ورزیاتول")
  val remove: String get() = t("برداشتن", "Remove", "لرې کول")
  val noteField: String get() = t("یادداشت", "Note", "یادښت")
  val send: String get() = t("ارسال", "Send", "لېږل")
  val saveDraft: String get() = t("ذخیرهٔ پیش‌نویس", "Save draft", "مسوده خوندي کول")
  val save: String get() = t("ذخیره", "Save", "خوندي کول")
  val sentOk: String get() = t("فرستاده شد", "Sent", "ولېږل شو")
  val draftSaved: String get() = t("پیش‌نویس ذخیره شد", "Draft saved", "مسوده خوندي شوه")
  val noItems: String get() = t("هنوز کالایی نیست — «افزودنِ کالا» را بزنید.", "No items yet — tap “Add an item”.", "لا توکی نشته — «توکی ورزیاتول» ووهئ.")

  //  جزئیات
  fun route(from: String, to: String): String = t("${asIs(from)} ⇐ ${asIs(to)}", "$from → $to", "$from ⇐ $to")
  fun figures(sent: String, received: String, pending: String): String = t(
    "فرستاده ${asIs(sent)} · رسیده ${asIs(received)} · در راه ${asIs(pending)}",
    "Sent $sent · Received $received · On the way $pending",
    "لېږل شوي $sent · رسېدلي $received · په لاره کې $pending",
  )
  fun short(q: String): String = t("کسری ${asIs(q)}", "Short $q", "کمښت $q")
  fun returned(q: String): String = t("برگشت به مبدأ ${asIs(q)}", "Returned $q", "بېرته ستون $q")
  val receive: String get() = t("دریافت", "Receive", "ترلاسه کول")
  val receivePartial: String get() = t("ثبتِ رسیده؛ بقیه در راه", "Record received; rest stays on the way", "رسېدلي ثبت؛ پاتې په لاره کې")
  val receiveFinal: String get() = t("بستنِ دریافت", "Finish receiving", "ترلاسه کول بشپړ")
  val allArrived: String get() = t("همه رسید", "All arrived", "ټول ورسېدل")
  val shortReason: String get() = t("دلیلِ کسری", "Reason for the shortage", "د کمښت دلیل")
  val reject: String get() = t("رد", "Reject", "رد")
  val rejectReason: String get() = t("دلیلِ رد", "Reason for rejecting", "د رد دلیل")
  val cancelDraft: String get() = t("لغوِ پیش‌نویس", "Cancel draft", "مسوده لغوه")
  val cancelSent: String get() = t("لغوِ ارسال", "Cancel sending", "لېږل لغوه")
  val cancelReason: String get() = t("دلیلِ لغو", "Reason for cancelling", "د لغوې دلیل")
  val edit: String get() = t("ویرایش", "Edit", "سمول")
  val history: String get() = t("گام‌ها", "Steps", "ګامونه")
  val stepDraft: String get() = t("پیش‌نویس ساخته شد", "Draft created", "مسوده جوړه شوه")
  val stepEdit: String get() = t("پیش‌نویس ویرایش شد", "Draft edited", "مسوده سمه شوه")
  val stepSend: String get() = t("فرستاده شد", "Sent", "ولېږل شو")
  val stepReceive: String get() = t("دریافت ثبت شد", "Receipt recorded", "ترلاسه کول ثبت شول")
  val stepReject: String get() = t("رد شد", "Rejected", "رد شو")
  val stepCancel: String get() = t("لغو شد", "Cancelled", "لغوه شو")
  val receivedOk: String get() = t("دریافت ثبت شد", "Receipt recorded", "ترلاسه کول ثبت شول")
  val rejectedOk: String get() = t("رد شد؛ مانده به شعبهٔ فرستنده برگشت", "Rejected; the rest went back to the sending branch", "رد شو؛ پاتې لېږونکې څانګې ته ستون شول")
  val cancelledOk: String get() = t("لغو شد", "Cancelled", "لغوه شو")
  val ledgerSynced: String get() = t("موجودیِ این دستگاه با انتقال‌ها هم‌تراز شد", "This device’s stock now follows the transfers", "د دې وسیلې موجودي له لېږدونو سره برابره شوه")
  fun failedWith(why: String): String = t("ثبت نشد: ${asIs(why)}", "Not saved: $why", "ثبت نه شو: $why")

  val pNoDestination: String get() = t("شعبهٔ مقصد را بزنید.", "Pick the destination branch.", "موخه څانګه وټاکئ.")
  val pSameBranch: String get() = t("مبدأ و مقصد یک شعبه‌اند؛ انتقالِ درونِ شعبه از «انبار» است.", "Same branch; transfers inside a branch are in Stock.", "یوه څانګه ده؛ دننه لېږد په «ګدام» کې دی.")
  val pBadLocation: String get() = t("مکانِ مبدأ یا مقصد درست نیست.", "The location is not valid.", "ځای سم نه دی.")
  val pEmpty: String get() = t("دست‌کم یک کالا لازم است.", "Add at least one item.", "لږ تر لږه یو توکی پکار دی.")
  val pTooMany: String get() = t("حداکثر ${asIs(BranchTransfer.MAX_LINES)} کالا در یک انتقال.", "At most ${BranchTransfer.MAX_LINES} items per transfer.", "په یوه لېږد کې تر ${BranchTransfer.MAX_LINES} توکو.")
  val pBadQty: String get() = t("مقدار باید عددِ مثبت باشد.", "The quantity must be a positive number.", "شمېر باید مثبت وي.")
  val pDuplicate: String get() = t("هر کالا یک بار.", "Each item once.", "هر توکی یو ځل.")
  val pBadKey: String get() = t("دوباره بزنید.", "Try again.", "بیا هڅه وکړئ.")
  val pReasonRequired: String get() = t("دلیل لازم است.", "A reason is required.", "دلیل پکار دی.")
  val pOverReceived: String get() = t("بیش از ماندهٔ «در راه» نمی‌رسد؛ اضافه را جدا ثبت کنید.", "More than what is on the way; record extras separately.", "له «په لاره کې» زیات نه رسي؛ اضافه جلا ثبت کړئ.")
  val pNotInTransfer: String get() = t("این کالا در این انتقال نیست.", "This item is not in this transfer.", "دا توکی په دې لېږد کې نشته.")
  val pNothingReceived: String get() = t("در این گام چیزی نرسیده است.", "Nothing was received in this step.", "په دې ګام کې څه ونه رسېدل.")
  val pNotDestination: String get() = t("فقط شعبهٔ مقصد دریافت یا رد می‌کند.", "Only the destination branch can receive or reject.", "یوازې موخه څانګه ترلاسه یا رد کوي.")
  val pNotSender: String get() = t("فقط شعبهٔ فرستنده این کار را می‌کند.", "Only the sending branch can do this.", "یوازې لېږونکې څانګه دا کار کوي.")
  val pClosed: String get() = t("این انتقال بسته شده است.", "This transfer is closed.", "دا لېږد تړل شوی.")
  val pPartialReceived: String get() = t("بخشی رسیده است؛ فقط شعبهٔ مقصد دریافت یا رد می‌کند.", "Part has arrived; only the destination can receive or reject.", "یوه برخه رسېدلې؛ یوازې موخه څانګه ترلاسه یا رد کوي.")
  val pBadState: String get() = t("این کار در این وضعیت نمی‌شود.", "Not possible in this state.", "په دې حالت کې نه کېږي.")


  //  صفحهٔ «شعبه‌ها» ِ صاحب (POS-16/S17 و INV-11)
  val rules: String get() = t("قاعده‌های شعبه‌ها", "Branch rules", "د څانګو قاعدې")
  val staffOtherStock: String get() = t("شاگردان موجودیِ شعبه‌های دیگر را ببینند", "Staff see other branches’ stock", "شاګردان د نورو څانګو موجودي ووینې")
  val staffOtherStockHelp: String get() = t(
    "صاحب و مدیر همیشه می‌بینند؛ فقط عدد، بی بها و فروش. خاموش ⇒ شاگرد فقط شعبهٔ خودش.",
    "The owner and managers always see it; numbers only, no prices or sales. Off ⇒ staff see only their own branch.",
    "خاوند او مدیر تل ویني؛ یوازې شمېر، بې بیې او پلور. بند ⇒ شاګرد یوازې خپله څانګه.",
  )
  val transferNegative: String get() = t("ارسالِ بیش از موجودی", "Send more than in stock", "له موجودۍ زیات لېږل")
  val transferNegativeHelp: String get() = t(
    "روشن ⇒ ارسال با وظیفهٔ بررسی ثبت می‌شود؛ خاموش ⇒ رد می‌شود.",
    "On ⇒ the transfer is recorded with a review task; off ⇒ it is refused.",
    "روښانه ⇒ لېږد د کتنې دندې سره ثبتېږي؛ بند ⇒ ردېږي.",
  )
  val memberOtherStock: String get() = t("موجودیِ شعبه‌های دیگر", "Other branches’ stock", "د نورو څانګو موجودي")
  val ownerOnlyRules: String get() = t("این قاعده‌ها را فقط صاحبِ دکان عوض می‌کند.", "Only the shop owner changes these rules.", "دا قاعدې یوازې د دوکان خاوند بدلوي.")

  //  کامپیوتر
  val colRoute: String get() = t("مسیر", "Route", "لار")
  val colItems: String get() = t("کالاها", "Items", "توکي")
  val colPending: String get() = t("در راه", "On the way", "په لاره کې")
  val colStatus: String get() = t("وضعیت", "Status", "حالت")
  val colDate: String get() = t("تاریخ", "Date", "نېټه")
  val pickRow: String get() = t("یک انتقال را بزنید تا جزئیاتش این‌جا بیاید.", "Select a transfer to see its details here.", "یو لېږد وټاکئ چې جزییات یې دلته راشي.")
  val searchProduct: String get() = t("جست‌وجوی کالا", "Search items", "توکي لټول")
  val close: String get() = t("بستن", "Close", "تړل")
}
