package ir.vil3ntec.tohid.core.i18n

/**
 *  چتِ دکان — پیشرفتِ فرستادن و جای دستگاه (CHAT-05، CHAT-07؛ `ShopPolicy.chatMediaGuard`، پیش‌فرض خاموش).
 *  قاعده‌ها سرِ `I18n.kt`؛ ترجمه‌های نامطمئن در `docs/i18n-glossary.md`.
 *  ⛔ بقیهٔ متن‌های صفحهٔ چت هنوز در خودِ `ShopChat.kt` است؛ این جدول فقط نوشته‌های تازه را دارد.
 */
@Suppress("unused")
object StrChat {

  // تنظیمات (فقط وقتی چتِ دکان روشن است)
  val guard: String get() = t("چت: پیشرفت و جای دستگاه", "Chat: progress and storage", "چټ: پرمختګ او د وسیلې ځای")
  val guardOn: String get() = t("نوارِ پیشرفت و سنجشِ جا", "Progress bar and storage check", "د پرمختګ پټه او د ځای کتنه")
  val guardNote: String get() = t(
    "روشن: هنگامِ فرستادنِ عکس و پیامِ صوتی درصدِ رفته دیده می‌شود، و پیش از ضبط، پخش یا آماده کردنِ عکس اگر جای این دستگاه کم است پیامِ روشن می‌آید، نه خطای مبهم. فقط روی همین دستگاه.",
    "On: shows how much of a photo or voice message has been sent, and gives a clear message before recording, playing or preparing a photo when this device is low on storage, instead of a vague error. This device only.",
    "روښانه: د انځور او غږیز پیغام د لېږلو پر مهال لېږل شوې برخه ښکاري، او که د دې وسیلې ځای لږ وي د ثبت، غږولو یا انځور چمتو کولو مخکې روښانه پیغام راځي، نه مبهمه تېروتنه. یوازې همدا وسیله.",
  )

  // صفحهٔ گفت‌وگو
  fun sending(pct: Any): String = t("در حالِ فرستادن — ${asIs(pct)}٪", "Sending — $pct%", "لېږل کېږي — $pct٪")
  val storageLow: String get() = t(
    "جای این دستگاه کم است؛ برای ضبط، پخش یا فرستادنِ رسانه کمی جا باز کنید.",
    "This device is low on storage; free up some space to record, play or send media.",
    "د دې وسیلې ځای لږ دی؛ د ثبت، غږولو یا رسنۍ لېږلو لپاره یو څه ځای خالي کړئ.",
  )

  // CHAT-14 — پاسخ، ویرایش، سنجاق و جست‌وجو در گفت‌وگوی برنامهٔ مدیریت
  val actions: String get() = t("کارها", "Actions", "کړنې")
  val actionsTitle: String get() = t("کارهای این پیام", "Message actions", "د دې پیغام کړنې")
  val reply: String get() = t("پاسخ", "Reply", "ځواب")
  val edit: String get() = t("ویرایش", "Edit", "سمول")
  val pin: String get() = t("سنجاق در بالای گفت‌وگو", "Pin to the top of the chat", "د خبرو اترو سر ته ټاکل")
  val unpin: String get() = t("برداشتنِ سنجاق", "Unpin", "ټاکنه لرې کول")
  val pinNote: String get() = t(
    "سنجاق را همهٔ کارمندانِ این دکان می‌بینند، مشتری نه؛ تا ۵ پیام.",
    "Everyone on this shop's staff sees pins, the customer doesn't; up to 5 messages.",
    "ټاکنې د دې دوکان ټول کارکوونکي ویني، پېرودونکی نه؛ تر ۵ پیغامونو.",
  )
  val pinned: String get() = t("سنجاق‌شده", "Pinned", "ټاکل شوي")
  fun replyingTo(text: String): String = t("پاسخ به: ${asIs(text)}", "Replying to: $text", "ځواب ته: $text")
  val editing: String get() = t("ویرایشِ پیام — «فرستادن» متنِ تازه را می‌گذارد", "Editing message — \"Send\" saves the new text", "د پیغام سمول — «لېږل» نوی متن ساتي")
  val earlier: String get() = t("پیامِ پیشین", "Earlier message", "پخوانی پیغام")
  val inMessages: String get() = t("در پیام‌ها", "In messages", "په پیغامونو کې")
}
