package ir.vil3ntec.tohid.ui.screens

import ir.vil3ntec.tohid.core.i18n.StrSale
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.money

/*
 *  جایگزینِ iOS ِ دو کمکیِ مشترکی که در `ui/screens/Sale.kt` (صندوقِ قدیمی، با پنجرهٔ چاپگرِ USB/بلوتوث) جا
 *  دارند و ۷۴ فایل صدایشان می‌زنند. خودِ Sale.kt هنوز iOS نیست (چاپگر، گامِ ۳)؛ این دو **عینِ** همان‌اند —
 *  هر تغییرِ آن‌جا باید این‌جا هم بیاید (پیشنهادِ گامِ ۳: بردنشان به فایلی بی چاپگر در اندروید).
 */
fun newId(): String = ir.vil3ntec.tohid.sync.v1.Ulid.next()

/** حالِ حسابِ یک قرض‌دار، با همان جمله‌های نسخهٔ وب */
fun debtStateText(d: ShopData, debtorId: String): String {
  val amount = ShopStore.debt(d, debtorId)
  return when {
    amount > 0 -> StrSale.owes(money(amount))
    amount < 0 -> StrSale.hasCredit(money(-amount))
    else -> StrSale.settled
  }
}
