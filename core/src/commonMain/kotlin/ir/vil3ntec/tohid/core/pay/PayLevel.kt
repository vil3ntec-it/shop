package ir.vil3ntec.tohid.core.pay

/**
 *  PAY-08 — سه سطحِ جدای تأییدِ یک ردیفِ پرداختِ غیرنقد (همان `verification` ِ ردیف):
 *   • [MANUAL] «ثبتِ دستی» — `manual` (صندوقدار با شمارهٔ مرجع، PAY-06؛ کارت‌خوانِ دستی) یا `cashier` («پول رسید» ِ حساب‌پیِ
 *     «ساده»). به تصمیمِ صاحب‌کار «تأییدشده» شمرده می‌شود (`PayStatus.initial`) — صندوقدار پولِ رسیده را زد.
 *   • [DEVICE] «تأییدِ دستگاه» — `device` (دستگاهِ پرداختِ یکپارچه خودش تأیید کرد و کلید، فاکتور و مبلغ را بازگرداند؛
 *     `core/terminal/DevicePay`).
 *   • [PROVIDER] «تأییدِ ارائه‌دهنده» — `gateway`/`provider` (قصدِ سرور با وب‌هوکِ امضاشده و تأییدِ دوم؛ وضعیت فقط از سرور).
 *  ⛔ «موفق» ([success]) فقط با وضعیتِ تأییدشده **و** یکی از این سه سطح؛ ردیفِ غیرنقدِ بی سطح هرگز موفق خوانده نمی‌شود.
 */
enum class PayLevel(val wire: Set<String>) {
  MANUAL(setOf("manual", "cashier")),
  DEVICE(setOf("device")),
  PROVIDER(setOf("gateway", "provider"));

  companion object {
    fun of(verification: String?): PayLevel? = entries.firstOrNull { verification in it.wire }

    /**
     *  نقد و «به حساب» تأییدِ جدا ندارند؛ بقیه فقط با سطحِ شناخته و وضعیتِ پولِ رسیده (ثبتِ دستیِ `pending` همان
     *  «تأییدشده» ِ `PayStatus.initial`).
     */
    fun success(kind: String, status: String, verification: String?): Boolean {
      val st = if (status == PayStatus.PENDING && verification == PayStatus.MANUAL) PayStatus.CONFIRMED else status
      return st in PayStatus.RECEIVED && (kind == "cash" || kind == "on_account" || of(verification) != null)
    }
  }
}
