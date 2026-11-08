package ir.vil3ntec.tohid.customer.ios

import ir.vil3ntec.tohid.core.shopper.ShopperKeyValue
import platform.Foundation.NSUserDefaults

/**
 *  نگه‌داریِ روی آیفون: NSUserDefaults ِ همین برنامه — همتای `StoreAndroid.kt`. سبد، سفارش‌ها، نشانی‌ها، صفِ گفت‌وگو و
 *  بلیتِ کوتاه‌عمرِ شماره؛ نشستِ بلندِ حسابِ مشتری (پس از D-09) در Keychain می‌رود، نه این‌جا.
 */
class StoreIos : ShopperKeyValue {
  private val d = NSUserDefaults.standardUserDefaults

  override fun get(key: String): String? = d.stringForKey(key)

  override fun put(key: String, value: String?) {
    if (value == null) d.removeObjectForKey(key) else d.setObject(value, forKey = key)
  }
}
