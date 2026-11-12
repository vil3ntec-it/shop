package ir.vil3ntec.tohid.data

import android.content.Context

/** `TillGuards` از تنظیمِ همین دستگاه (`tohid-policy`): «فروخته نشود»، «منقضی فقط با مجوز» و قاعده‌های پایانِ فاکتور */
object TillGuardsPref {
  fun get(context: Context): TillGuards =
    TillGuards(ShopPolicy.blockExpired(context), ExpiryPref.gate(context), CheckoutRulesPref.get(context))
}
