package ir.vil3ntec.tohid.data

import android.content.Context

/**
 *  `TillGuards` از تنظیمِ همین دستگاه (`tohid-policy`): «فروخته نشود»، «منقضی فقط با مجوز»، قاعده‌های پایانِ فاکتور، مالیاتِ
 *  فاکتور و پلهٔ گردِ نقدی — تنها خوانندهٔ صندوقِ کامپیوتر، گوشیِ نسخهٔ ۳ و فروشِ «تعویض» ِ هر دو برگهٔ برگشت
 */
object TillGuardsPref {
  fun get(context: Context): TillGuards = TillGuards(
    ShopPolicy.blockExpired(context), ExpiryPref.gate(context), CheckoutRulesPref.get(context),
    tax = SaleTaxPref.active(context), cashRounding = CashRoundPref.step(context),
  )
}
