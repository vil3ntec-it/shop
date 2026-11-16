package ir.vil3ntec.tohid.core.pay

import ir.vil3ntec.tohid.core.gateway.HesabPayShop
import ir.vil3ntec.tohid.core.gateway.TillHesab

/**
 *  روش‌های پرداختِ صندوق (e-paymethods، صاحب‌کار ۱۴۰۵/۰۸/۲۵: «جای پرداخت چرا حساب‌های کاربری نمیاد، مثل کیو‌آر که ثبت کرده
 *  بود از حساب‌پی یا ان‌اف‌سی یا کارت‌خوانِ دستگاه — این‌ها همه باید بیایند یا نقدی»). کاتلینِ خالص؛ صفحه‌ها (تسویهٔ گوشیِ
 *  نسخهٔ ۳ و پنلِ پرداختِ کامپیوتر) فقط کاشی می‌کشند و کارِ هر کاشی را به پنلِ ترکیبی (`PayFlow`) یا برگهٔ حساب‌پی می‌دهند.
 *
 *  ترتیب و قاعده:
 *   - **نقد** همیشه.
 *   - **کارت** (کارت‌خوانِ دستگاه، ثبتِ دستیِ شمارهٔ تأیید — PAY-06/PAY-08؛ تأییدِ خودکارِ کارت‌خوان تا سندِ D-08 نیست) اگر
 *     دکان روشِ کارت را برنداشته.
 *   - **حساب‌پی (QR/NFC)** فقط با حسابِ خودِ دکان ([hesab]): آماده ⇒ زدنی؛ حسابِ تازه در ۲۴ ساعتِ امنیتی و بی حسابِ فعال ⇒
 *     کاشیِ خاموش با زمانِ فعال شدن (مهلت کوتاه نمی‌شود)؛ راه‌اندازی‌نشده ⇒ هیچ کاشی (صفحه برای صاحبِ دکان فقط پیوندِ
 *     «راه‌اندازیِ حساب‌پی» می‌گذارد، [Hesab.Setup]).
 *   - **کیف پول** و **حواله** اگر دکان دست‌کم یک روشِ همان نوع را روشن دارد.
 *   - **نسیه** (به حسابِ مشتری) همیشه؛ بی مشتری صفحه اول «انتخابِ مشتری» را باز می‌کند (`CreditDebtor`).
 *  روشی که دکان برداشته (`PayMethodsPref`) در [panel] نیست و کاشی نمی‌گیرد.
 */
object TillMethods {

  enum class Kind { CASH, CARD, HESABPAY, WALLET, TRANSFER, CREDIT }

  /** حالِ حساب‌پیِ خودِ دکان برای صندوق */
  sealed interface Hesab {
    /** نه پرچم، نه نت، یا حالتی که صندوق کاری با آن ندارد ⇒ هیچ */
    data object Off : Hesab
    /** پرچم روشن و دکان هنوز هیچ حسابی نگذاشته ⇒ فقط پیوندِ راه‌اندازی برای صاحب */
    data object Setup : Hesab
    /** حسابِ تازه در ۲۴ ساعتِ امنیتی و حسابِ فعالی نیست ⇒ کاشیِ خاموش تا [until] (ساعتِ سرور) */
    data class Pending(val until: Long) : Hesab
    /** گیرندهٔ معلوم و (درگاهِ خودکار یا «ساده» با پرچم) — همان `TillHesab.tillNfc` */
    data class Ready(val auto: Boolean) : Hesab
  }

  /** یک کاشی: نوع، روشِ پیش‌فرضِ ردیفِ پنل (فقط کارت/کیف پول/حواله)، زدنی بودن و زمانِ فعال شدنِ حساب‌پی */
  data class Tile(val kind: Kind, val method: String? = null, val enabled: Boolean = true, val until: Long? = null)

  fun hesab(t: TillHesab, now: Long): Hesab {
    if (t.tillNfc) return Hesab.Ready(t.gateway)
    if (!t.flag) return Hesab.Off
    val until = t.pendingUntil
    if (t.payee == null && until != null && until > now) return Hesab.Pending(until)
    if (t.mode == HesabPayShop.OFF && until == null) return Hesab.Setup
    return Hesab.Off
  }

  /** نوعِ کاشیِ یک روشِ جدولِ هسته (نقد و حساب نه — آن‌ها کاشیِ خودشان را دارند) */
  fun kindOf(m: PayMethod): Kind? = when (m.kind) {
    "card" -> Kind.CARD
    "wallet" -> Kind.WALLET
    "transfer" -> Kind.TRANSFER
    else -> null
  }

  /**
   *  کاشی‌های صندوق به ترتیبِ نقد، کارت، حساب‌پی، کیف پول، حواله، نسیه. [panel] = روش‌های غیرنقدِ روشنِ پنل
   *  (`PayFlow.panelMethods` با برداشته‌های دکان). کیف پولِ پیش‌فرض با حساب‌پیِ آماده هر کیف پولی جز ثبتِ دستیِ حساب‌پی است
   *  (حساب‌پی کاشیِ خودش را دارد؛ ثبتِ دستی‌اش همچنان در تراشه‌های ردیف هست).
   */
  fun tiles(panel: List<PayMethod>, hesab: Hesab): List<Tile> = buildList {
    fun first(k: Kind, avoid: String? = null) =
      panel.filter { it.enabled && kindOf(it) == k }.let { l -> l.firstOrNull { it.id != avoid } ?: l.firstOrNull() }
    add(Tile(Kind.CASH, Pay.CASH))
    first(Kind.CARD)?.let { add(Tile(Kind.CARD, it.id)) }
    when (hesab) {
      is Hesab.Ready -> add(Tile(Kind.HESABPAY))
      is Hesab.Pending -> add(Tile(Kind.HESABPAY, enabled = false, until = hesab.until))
      Hesab.Setup, Hesab.Off -> Unit
    }
    first(Kind.WALLET, avoid = if (hesab is Hesab.Ready) HESABPAY_MANUAL else null)?.let { add(Tile(Kind.WALLET, it.id)) }
    first(Kind.TRANSFER)?.let { add(Tile(Kind.TRANSFER, it.id)) }
    add(Tile(Kind.CREDIT))
  }

  /** شناسهٔ ثبتِ دستیِ حساب‌پی در جدولِ هسته (`Pay.DEFAULT_METHODS`) */
  const val HESABPAY_MANUAL = "hesabpay"
}
