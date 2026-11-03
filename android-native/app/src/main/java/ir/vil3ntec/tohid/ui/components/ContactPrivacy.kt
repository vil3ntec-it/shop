package ir.vil3ntec.tohid.ui.components

import ir.vil3ntec.tohid.core.text.ContactMask

/**
 *  CRM-15 — شمارهٔ مشتری برای نمایش: `LocaleText.phoneDisplay` و، بی مجوزِ `customers.contact`، پوشیده (`ContactMask`،
 *  جز دو رقمِ آخر). [visible] از `rememberCanSeeContacts()` (`ContactPrivacyAndroid.kt`) می‌آید.
 */
fun customerPhone(phone: String, visible: Boolean): String =
  ContactMask.show(ir.vil3ntec.tohid.LocaleText.phoneDisplay(phone), visible)
