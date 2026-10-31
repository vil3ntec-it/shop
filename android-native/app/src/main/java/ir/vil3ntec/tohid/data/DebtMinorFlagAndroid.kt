package ir.vil3ntec.tohid.data

import android.content.Context

/**
 *  مرزِ سکوی [DebtMinorFlag]: پرچمِ `shop.debt_minor` ِ دکانِ همین لحظه، همان‌جا که `ReceiptFlag.kick` از
 *  پاسخِ `/config` نوشته (`tohid-receipt`، کلیدِ `debt-minor:v2:<دکان>`). هر بار خوانده می‌شود، پس با عوض‌شدنِ
 *  حساب/دکان پرچمِ دکانِ پیشین نمی‌ماند. `ShopStore` آن را یک بار وصل می‌کند.
 */
internal fun DebtMinorFlag.bind(context: Context) {
  val app = context.applicationContext
  bindOnce { ReceiptFlag.prefs(app).getBoolean(ReceiptFlag.DEBT_MINOR + ReceiptFlag.key(app), false) }
}
