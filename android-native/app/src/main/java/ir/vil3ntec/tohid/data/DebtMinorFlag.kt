package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.money.LedgerBalance

/**
 *  پرچمِ `shop.debt_minor` (`live_config` ِ سرور، **پیش‌فرض خاموش**) — ماندهٔ حساب به پول.
 *  RULES: رفتارِ تازه در پول فقط پشتِ پرچم.
 *
 *  - خاموش ⇒ بیت‌به‌بیت همان main: جمعِ Double ِ ردیف‌ها (`sumOf`، همان ترتیب) و `> 0` / `< 0` / `== 0.0` ِ همان عدد.
 *  - روشن ⇒ `core/money/LedgerBalance`: هر ردیف دقیقاً مثلِ `DebtBook` به پول گرد و جمعِ صحیح؛ تسویهٔ دقیقِ
 *    چند ردیفِ اعشاری صفرِ دقیق و «حساب صاف» است (بازبینیِ #470) و نیم‌پول دوطرفه یکی است (#610).
 *
 *  پرچم از همان پاسخِ `GET /shop/receipt-profile/config` ِ `ReceiptFlag` می‌آید (بی درخواستِ تازه) و به حسابِ
 *  دکان بسته است. `ShopStore.debt` و موتورهای خالص به سکو دسترسی ندارند، پس — مثلِ `ShopDayChoice.hours` و
 *  `I18n.source` — از [on] می‌خوانند؛ نخستین `ShopStore` (صفحه، کارگرِ همگام‌سازی، کامپیوتر) خواننده را
 *  وصل می‌کند (`DebtMinorFlagAndroid.kt`). هیچ چیزی ذخیره یا همگام نمی‌شود؛ فقط نمایش و جمعِ مانده.
 */
object DebtMinorFlag {

  /** خوانندهٔ پرچمِ ذخیره‌شدهٔ همین دکان؛ پیش از نخستین `ShopStore` نیست ⇒ خاموش */
  @Volatile private var stored: (() -> Boolean)? = null

  /** فقط آزمون: جای پرچم را می‌گیرد؛ `null` ⇒ همان پرچمِ ذخیره‌شده */
  @Volatile var override: Boolean? = null

  /** فقط یک بار: خوانندهٔ پرچم (`bind` ِ `DebtMinorFlagAndroid.kt`) */
  fun bindOnce(reader: () -> Boolean) { if (stored == null) stored = reader }

  val on: Boolean get() = override ?: runCatching { stored?.invoke() == true }.getOrDefault(false)

  /** ماندهٔ ردیف‌ها (برد +، رسید −) */
  fun balance(rows: List<DebtTransaction>): Double =
    if (on) LedgerBalance.sum(rows, plus = { it.type == "give" }, amount = { it.amount })
    else rows.sumOf { if (it.type == "give") it.amount else -it.amount }

  /** ۱ بدهکار، −۱ موجودی، ۰ صاف — خاموش ⇒ همان `> 0` / `< 0` ِ Double */
  fun sign(balance: Double): Int =
    if (on) LedgerBalance.sign(balance) else if (balance > 0) 1 else if (balance < 0) -1 else 0

  /** «حساب صاف»؟ — خاموش ⇒ همان `== 0.0` */
  fun settled(balance: Double): Boolean = if (on) LedgerBalance.sign(balance) == 0 else balance == 0.0
}
