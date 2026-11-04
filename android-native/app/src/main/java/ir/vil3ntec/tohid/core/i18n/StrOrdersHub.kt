package ir.vil3ntec.tohid.core.i18n

/**
 *  «سفارش‌های آنلاین» ِ نسخهٔ ۳ (خواستِ صاحب‌کار): کاشیِ خانه با نشانِ زنده، برگه‌ها و کارتِ صف، کارتِ «آماده‌سازی و
 *  تحویل» ِ هر سفارش، و چرخهٔ عمرِ گفت‌وگوی هر سفارش (یک رشته برای هر سفارش، «بسته شد»/«مشکل دارم»؛ سرور:
 *  `lib/shop-chat-life.js`). جدا از `StrOnline` تا آن زیرِ سقفِ اندازه بماند.
 */
@Suppress("unused")
object StrOrdersHub {

  // ── کاشیِ خانه ──
  val tile: String get() = t("سفارش‌های آنلاین", "Online orders", "آنلاین فرمایشونه")
  /** نامِ دسترس‌پذیرِ کاشی با سفارشِ تازه (انتخابِ متن: `HomeTiles.a11y`) */
  fun tileFresh(n: Int): String = t("سفارش‌های آنلاین، ${asIs(n)} سفارشِ تازه", "Online orders, $n new", "آنلاین فرمایشونه، $n نوي")
  /** … بی تازه، با سفارش‌های در کار */
  fun tileActive(n: Int): String = t("سفارش‌های آنلاین، ${asIs(n)} سفارشِ در کار", "Online orders, $n in progress", "آنلاین فرمایشونه، $n په کار کې")
  /** … وقتی بخشِ سفارش برای این دکان خاموش است (کاشی می‌ماند و «تنظیمات ← سفارشِ آنلاین» را باز می‌کند) */
  val tileOff: String get() = t("سفارش‌های آنلاین، هنوز روشن نیست", "Online orders, not turned on yet", "آنلاین فرمایشونه، لا نه دي فعال")
  val saleFab: String get() = t("فروشِ تازه", "New sale", "نوی پلور")

  // ── برگه‌های صف (نامِ صاحب‌کار؛ هسته و وب همان `OrderText` می‌مانند) ──
  fun tab(id: String): String = when (id) {
    "new" -> t("جدید", "New", "نوي")
    "preparing" -> t("در حالِ آماده‌سازی", "Preparing", "د چمتو کېدو په حال کې")
    "ready" -> t("آمادهٔ تحویل", "Ready for handover", "د سپارلو لپاره چمتو")
    "on_the_way" -> t("در راه", "On the way", "په لاره کې")
    "scheduled" -> t("زمان‌بندی‌شده", "Scheduled", "مهال‌ویشل شوي")
    "done" -> t("تمام‌شده", "Done", "بشپړ شوي")
    else -> StrOnline.orderTab(id)
  }

  // ── کارتِ صف ──
  val onlineBadge: String get() = t("سفارشِ آنلاین", "Online order", "آنلاین فرمایش")
  val byCourier: String get() = t("با پیک", "By courier", "د پیک له لارې")
  val selfPickup: String get() = t("خودش می‌آید", "Customer picks up", "پخپله راځي")
  fun itemsPreview(names: String, more: Int): String =
    if (more > 0) t("${asIs(names)} و ${asIs(more)} قلمِ دیگر", "$names and $more more", "$names او $more نور توکي") else names

  // ── آماده‌سازی و تحویل (جزئیاتِ سفارش) ──
  val prepTitle: String get() = t("آماده‌سازی و تحویل", "Prepare and hand over", "چمتو کول او سپارل")
  val sendPhoto: String get() = t("عکس برای مشتری", "Photo to customer", "پېرودونکي ته انځور")
  val sendPhotoHint: String get() = t(
    "عکسِ کالاهای آماده یا بستهٔ بسته‌بندی‌شده در گفت‌وگوی همین سفارش به مشتری می‌رود.",
    "A photo of the prepared items or the packed bag goes to the customer in this order's chat.",
    "د چمتو شویو توکو یا تړل شوې کڅوړې انځور د همدې فرمایش په خبرو کې پېرودونکي ته ځي.",
  )
  val chatWithCustomer: String get() = t("گفت‌وگو با مشتری", "Chat with customer", "له پېرودونکي سره خبرې")
  val markPacked: String get() = t("بسته شد", "Mark packed", "وتړل شو")
  val packedDone: String get() = t("بسته‌بندی شده است", "Packed", "تړل شوی")
  val toCourier: String get() = t("سپردن به پیک", "Hand to courier", "پیک ته سپارل")
  val readyPickup: String get() = t("آماده است؛ خودش می‌آید", "Ready — customer picks up", "چمتو دی؛ پخپله راځي")
  fun methodLine(courier: Boolean): String =
    if (courier) t("مشتری «ارسال با پیک» خواسته است.", "The customer chose delivery by courier.", "پېرودونکي د پیک له لارې لېږل غوښتي.")
    else t("مشتری خودش برای گرفتن می‌آید.", "The customer will pick it up.", "پېرودونکی پخپله د اخیستو لپاره راځي.")
  val methodNote: String get() = t(
    "روشِ تحویل را مشتری پیش از جمع‌آوری از پیگیریِ سفارش عوض می‌کند.",
    "The customer can change the delivery method from order tracking before picking starts.",
    "پېرودونکی د راټولولو تر پیل مخکې د فرمایش له تعقیب څخه د سپارلو لاره بدلولی شي.",
  )
  val chatOff: String get() = t("گفت‌وگو با مشتری برای این دکان روشن نیست.", "Customer chat is not enabled for this shop.", "د پېرودونکي خبرې د دې دوکان لپاره فعالې نه دي.")
  val noPhone: String get() = t("این سفارش شمارهٔ مشتری ندارد؛ گفت‌وگو ممکن نیست.", "This order has no customer number; chat isn't possible.", "دا فرمایش د پېرودونکي شمېره نه لري؛ خبرې ممکنې نه دي.")

  // ── چرخهٔ عمرِ گفت‌وگو (دیدِ دکان) ──
  val lifeClosed: String get() = t("بسته شد", "Closed", "وتړل شو")
  val lifeReopened: String get() = t("مشتری «مشکل دارم» زد", "Customer reported a problem", "پېرودونکي «ستونزه لرم» کېکاږه")
  fun closedOrder(until: String?): String = if (until != null) t(
    "سفارش تمام شده و این گفت‌وگو فقط خواندنی است. مشتری تا ${asIs(until)} می‌تواند «مشکل دارم» بزند و همین گفت‌وگو دوباره باز شود.",
    "The order is complete and this chat is read-only. Until $until the customer can tap “I have a problem” to reopen it.",
    "فرمایش بشپړ شوی او دا خبرې یوازې د لوستلو دي. پېرودونکی تر $until پورې «ستونزه لرم» کېکاږلی شي او همدا خبرې بیا پرانیستل کېږي.",
  ) else t(
    "سفارش تمام شده و مهلتِ «مشکل دارم» گذشته است؛ این گفت‌وگو فقط خواندنی است.",
    "The order is complete and the problem window has passed; this chat is read-only.",
    "فرمایش بشپړ شوی او د «ستونزه لرم» موده تېره شوې؛ دا خبرې یوازې د لوستلو دي.",
  )
  val closedIdle: String get() = t(
    "این گفت‌وگو پس از چند روز بی‌پیامی بسته شد؛ پرسشِ تازهٔ مشتری دوباره بازش می‌کند.",
    "This chat closed after a few days without messages; a new question from the customer reopens it.",
    "دا خبرې له څو ورځو بې پیغامه وروسته وتړل شوې؛ د پېرودونکي نوې پوښتنه یې بیا پرانیزي.",
  )
  val reopenedNote: String get() = t(
    "مشتری دربارهٔ این سفارشِ تمام‌شده «مشکل دارم» زد؛ برگشت یا اختلاف را همین‌جا پیگیری کنید.",
    "The customer reported a problem with this completed order; handle the return or dispute here.",
    "پېرودونکي د دې بشپړ شوي فرمایش په اړه «ستونزه لرم» کېکاږه؛ بېرته ورکړه یا اختلاف همدلته تعقیب کړئ.",
  )
  fun earlier(n: Int): String = t("گفت‌وگوهای پیشینِ این مشتری (${asIs(n)})", "Earlier chats with this customer ($n)", "له دې پېرودونکي سره پخوانۍ خبرې ($n)")
  val generalThread: String get() = t("گفت‌وگوی عمومی", "General chat", "عمومي خبرې")
  fun orderThread(n: Long): String = t("سفارشِ #${asIs(n)}", "Order #$n", "فرمایش #$n")
  val earlierEmpty: String get() = t("پیامی نیست", "No messages", "پیغام نشته")
  val earlierFailed: String get() = t("گفت‌وگوهای پیشین خوانده نشد", "Earlier chats could not be loaded", "پخوانۍ خبرې ونه لوستل شوې")
}
