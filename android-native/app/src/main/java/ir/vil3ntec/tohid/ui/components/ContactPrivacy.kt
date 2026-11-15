package ir.vil3ntec.tohid.ui.components

import ir.vil3ntec.tohid.core.crm.CustomerMask
import ir.vil3ntec.tohid.data.ShopRole

/**
 *  CRM-15 — شمارهٔ مشتری برای نمایش: `LocaleText.phoneDisplay` و، بی مجوزِ `customers.contact`، پوشیده با هستهٔ مشترکِ
 *  `core/crm/CustomerMask`: پرچمِ نقش‌محورِ `shop.customer_role_mask` روشن ⇒ «•••• ۰۰۸» (سه رقمِ آخر)، خاموش ⇒ همان
 *  `ContactMask` (دو رقمِ آخر). [visible] از `rememberCanSeeContacts()` (`ContactPrivacyAndroid.kt`) می‌آید که پرچم را هم
 *  تازه می‌کند (`ShopRole.customerMask`).
 */
fun customerPhone(phone: String, visible: Boolean): String =
  CustomerMask.show(ir.vil3ntec.tohid.LocaleText.phoneDisplay(phone), visible, ShopRole.customerMask.value)

/** CRM-15 — نشانیِ مشتری فقط برای بیننده‌ای که شمارهٔ کامل را می‌بیند؛ وگرنه `null` (پنهان، نه پوشیده) */
fun customerAddress(text: String?, visible: Boolean): String? =
  CustomerMask.address(text, if (visible) CustomerMask.Level.FULL else CustomerMask.Level.HIDDEN)

/**
 *  CRM-15 — شماره در خروجی (اکسل، برگهٔ گزارش): پرچمِ نقش‌محور روشن و بیننده بی مجوز ⇒ «•••• ۰۰۸» (بی جهت‌نما)؛ وگرنه
 *  همان امروز (کامل)
 */
fun customerPhoneExport(phone: String, visible: Boolean): String =
  if (visible || !ShopRole.customerMask.value) phone else CustomerMask.masked(phone)
