package ir.vil3ntec.tohid.core.i18n

/**
 *  SYN-07 / S28 — صندوقِ تعارضِ همگام‌سازیِ v3 (`core/sync/V3Inbox`، `/api/sync/v3/conflicts`، پرچمِ `shop.sync_v3_inbox`):
 *  نوارِ پوسته، فهرست، دو سوی هر تعارض و سه کار. ساخته‌شده از جدولِ سه‌زبانه؛ قاعده‌ها سرِ `I18n.kt`.
 *  ⛔ نامِ ردیف و فیلد، مقدارها، شناسهٔ دستگاه، زمان و کدِ خطا از `asIs` می‌گذرند (لایهٔ دری دستشان نمی‌زند).
 */
@Suppress("unused")
object StrV3Inbox {
  val title: String get() = t("تعارض‌های همگام‌سازی", "Sync conflicts", "د همغږۍ ټکرونه")
  val intro: String get() = t(
    "وقتی دو دستگاه یک چیز را جدا جدا عوض کنند، سرور یکی را نگه می‌دارد و دیگری این‌جا می‌ماند. هر دو را ببینید و انتخاب کنید؛ بی اینترنت هم تصمیم در صف می‌ماند و پس از وصل شدن فرستاده می‌شود.",
    "When two devices change the same thing separately, the server keeps one and the other waits here. See both and choose; without internet your choice is queued and sent once you are connected.",
    "کله چې دوه وسیلې یو شی جلا جلا بدل کړي، سرور یو ساتي او بل دلته پاتې کېږي. دواړه وګورئ او وټاکئ؛ له انټرنېټ پرته هم ستاسو پرېکړه په لیکه کې پاتې کېږي او له وصل کېدو وروسته لېږل کېږي.",
  )
  val empty: String get() = t("تعارضی نمانده است.", "No conflicts left.", "هېڅ ټکر نه دی پاتې.")
  val emptyClosed: String get() = t("هنوز چیزی بسته نشده.", "Nothing has been closed yet.", "تر اوسه هېڅ شی نه دی تړل شوی.")
  fun pending(n: Any): String = t(
    "${asIs(n)} تعارضِ همگام‌سازی منتظرِ تصمیمِ شماست",
    "$n sync ${pl(n, "conflict is", "conflicts are")} waiting for you",
    "$n د همغږۍ ${pl(n, "ټکر", "ټکرونه")} ستاسو د پرېکړې په انتظار دي",
  )
  val open: String get() = t("دیدن", "Open", "کتل")
  val tabOpen: String get() = t("باز", "Open", "پرانیستي")
  val tabClosed: String get() = t("بسته‌ها", "Closed", "تړل شوي")
  val loadFailed: String get() = t(
    "فهرست از سرور نیامد؛ آنچه پیش‌تر آمده بود نشان داده می‌شود.",
    "The list did not come from the server; showing what was loaded before.",
    "لړلیک له سرور رانغی؛ هغه څه ښودل کېږي چې مخکې راغلي وو.",
  )
  fun queuedCount(n: Any): String = t(
    "${asIs(n)} تصمیم در صف است و با وصل شدن فرستاده می‌شود",
    "$n ${pl(n, "decision is", "decisions are")} queued and will be sent once connected",
    "$n ${pl(n, "پرېکړه", "پرېکړې")} په لیکه کې دي او له وصل کېدو سره لېږل کېږي",
  )

  //  یک ردیف
  fun kind(code: String): String = when (code) {
    "lww" -> t("دو دستگاه یک چیز را عوض کردند", "Two devices changed the same thing", "دوو وسیلو یو شی بدل کړ")
    "clock_skew" -> t("ساعتِ دستگاه بیش از ۵ دقیقه جلو بود؛ تغییرش نبرد", "The device clock was over 5 minutes ahead; its change did not win", "د وسیلې ساعت له ۵ دقیقو زیات مخکې و؛ بدلون یې ونه ګټل")
    "stale_after_delete" -> t("ویرایشی که از حذفِ همین ردیف کهنه‌تر بود", "An edit older than this row’s deletion", "داسې سمون چې د همدې کرښې له ړنګېدو زوړ و")
    "stale_delete" -> t("حذفی که از ویرایشِ تازه‌تر کهنه‌تر بود", "A deletion older than a newer edit", "داسې ړنګول چې له نوي سمون زوړ و")
    "append_only_edit" -> t("سندِ مالی ویرایش نمی‌شود؛ ویرایش کنار ماند", "Money documents are not edited; the edit was kept aside", "مالي سند نه سمېږي؛ سمون یو طرف ته پاتې شو")
    "negative_stock" -> t("موجودی منفی شد — بررسی کنید", "Stock went negative — please check", "موجودي منفي شوه — وې ګورئ")
    "duplicate_barcode" -> t("دو کالا با یک بارکد — ادغام یا اصلاح کنید", "Two products share a barcode — merge or fix", "دوه توکي یو بارکوډ لري — یو ځای یا سم یې کړئ")
    "duplicate_customer" -> t("دو مشتری با یک شماره — در صورتِ لزوم ادغام کنید", "Two customers share a number — merge them if needed", "دوه پېرودونکي یوه شمېره لري — که اړتیا وي یو ځای یې کړئ")
    else -> t("تعارض: ${asIs(code)}", "Conflict: $code", "ټکر: $code")
  }
  fun table(code: String): String = when (code) {
    "products" -> t("کالا", "Product", "توکی")
    "debtors" -> t("قرض‌دار", "Debtor", "پوروړی")
    "suppliers" -> t("تأمین‌کننده", "Supplier", "برابروونکی")
    "expenses" -> t("مصرف", "Expense", "لګښت")
    else -> t("ردیف (${asIs(code)})", "Row ($code)", "کرښه ($code)")
  }
  fun row(kind: String, name: String): String = t("${asIs(kind)}: ${asIs(name)}", "$kind: $name", "$kind: $name")
  fun field(name: String): String = when (name) {
    "name" -> t("نام", "Name", "نوم")
    "salePrice" -> t("قیمتِ فروش", "Sale price", "د پلور بیه")
    "purchasePrice" -> t("قیمتِ خرید", "Purchase price", "د پېر بیه")
    "wholesalePrice" -> t("قیمتِ عمده", "Wholesale price", "د عمده بیه")
    "minStock" -> t("کمترین موجودی", "Minimum stock", "لږه موجودي")
    "barcode", "barcodes" -> t("بارکد", "Barcode", "بارکوډ")
    "phone" -> t("شماره", "Phone", "شمېره")
    "category" -> t("دسته", "Category", "ډله")
    "unit" -> t("واحد", "Unit", "واحد")
    "address" -> t("نشانی", "Address", "پته")
    "notes", "note" -> t("یادداشت", "Note", "یادښت")
    else -> t(asIs(name), name, name)
  }

  //  دو سو
  val current: String get() = t("مقدارِ فعلی (نگه داشته شد)", "Current value (kept)", "اوسنی ارزښت (وساتل شو)")
  val other: String get() = t("مقدارِ دیگر (نبرد)", "Other value (did not win)", "بل ارزښت (ونه ګټل)")
  val thisDevice: String get() = t("همین دستگاه", "This device", "همدا وسیله")
  fun otherDevice(id: String): String = t("دستگاهِ دیگر ·${asIs(id)}", "Another device ·$id", "بله وسیله ·$id")
  val server: String get() = t("سرور", "Server", "سرور")
  val unknownDevice: String get() = t("دستگاهِ نامعلوم", "Unknown device", "نامعلومه وسیله")
  fun at(time: String): String = t("زمان: ${asIs(time)}", "Time: $time", "وخت: $time")
  val deleted: String get() = t("ردیف حذف شده است", "The row is deleted", "کرښه ړنګه شوې ده")
  val rowKept: String get() = t("ردیف با ویرایشِ تازه‌تر می‌ماند", "The row stays with the newer edit", "کرښه له نوي سمون سره پاتې کېږي")
  val yes: String get() = t("بله", "Yes", "هو")
  val no: String get() = t("نه", "No", "نه")
  val emptyValue: String get() = t("خالی", "Empty", "خالي")
  fun fields(n: Any): String = t("${asIs(n)} فیلد", "$n ${pl(n, "field", "fields")}", "$n ${pl(n, "ځای", "ځایونه")}")

  //  کارها
  val keepMine: String get() = t("مالِ من بماند", "Keep mine", "زما دې پاتې شي")
  val keepTheirs: String get() = t("مالِ دستگاهِ دیگر بماند", "Keep theirs", "د بلې وسیلې دې پاتې شي")
  val keepCurrent: String get() = t("مقدارِ فعلی بماند", "Keep current", "اوسنی دې پاتې شي")
  val takeOther: String get() = t("مقدارِ دیگر برگردد", "Use the other value", "بل ارزښت دې راوګرځي")
  val merge: String get() = t("ادغام، فیلد به فیلد", "Merge field by field", "یو ځای کول، ځای په ځای")
  val reviewed: String get() = t("دیدم، بسته شود", "Seen, close it", "ومې لیدل، دې وتړل شي")
  val mergeTitle: String get() = t("ادغام", "Merge", "یو ځای کول")
  val mergeBody: String get() = t(
    "برای هر فیلد بگویید کدام مقدار بماند. فیلدی که «دیگر» بزنید از سوی دیگر برمی‌گردد و بقیه همان فعلی می‌مانند.",
    "For each field, choose which value stays. Fields set to “Other” come back from the other side; the rest stay as they are.",
    "د هر ځای لپاره ووایاست کوم ارزښت دې پاتې شي. هغه ځای چې «بل» یې وټاکئ له بل لوري راګرځي او نور همداسې پاتې کېږي.",
  )
  val pickCurrent: String get() = t("فعلی", "Current", "اوسنی")
  val pickOther: String get() = t("دیگر", "Other", "بل")
  val apply: String get() = t("ثبتِ تصمیم", "Save decision", "پرېکړه ثبت کړئ")

  //  پس از تصمیم
  val queued: String get() = t("در صف — با وصل شدن فرستاده می‌شود", "Queued — sent once connected", "په لیکه کې — له وصل کېدو سره لېږل کېږي")
  val done: String get() = t("تصمیم ثبت شد", "Decision saved", "پرېکړه ثبت شوه")
  val doneAlready: String get() = t("این تصمیم پیش‌تر ثبت شده بود", "This decision was already saved", "دا پرېکړه مخکې ثبت شوې وه")
  val closedElsewhere: String get() = t(
    "کسِ دیگری یا دستگاهِ دیگری پیش‌تر این را بسته بود؛ حالِ سرور نشان داده می‌شود.",
    "Someone else or another device had already closed this; showing the server’s state.",
    "بل چا یا بلې وسیلې دا مخکې تړلی و؛ د سرور حالت ښودل کېږي.",
  )
  val refused: String get() = t(
    "سرور این تصمیم را نپذیرفت؛ تعارض باز می‌ماند و می‌توانید کارِ دیگری برگزینید.",
    "The server did not accept this decision; the conflict stays open and you can choose another action.",
    "سرور دا پرېکړه ونه منله؛ ټکر پرانیستی پاتې کېږي او بل کار ټاکلی شئ.",
  )
  fun closedAs(status: String, who: String): String = t("${asIs(status)} — ${asIs(who)}", "$status — $who", "$status — $who")
  val statusKept: String get() = t("فعلی ماند", "Current kept", "اوسنی پاتې شو")
  val statusReverted: String get() = t("مقدارِ دیگر برگشت", "Other value restored", "بل ارزښت راوګرځېد")
  val statusMerged: String get() = t("ادغام شد", "Merged", "یو ځای شو")
  val statusDismissed: String get() = t("دیده و بسته شد", "Seen and closed", "ولیدل شو او وتړل شو")
}
