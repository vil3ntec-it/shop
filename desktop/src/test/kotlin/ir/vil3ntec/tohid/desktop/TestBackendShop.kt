package ir.vil3ntec.tohid.desktop

import ir.vil3ntec.tohid.data.repo.Backend

/**
 *  `Backend.shop(context)` ِ صفحه‌ها ⇐ همان سرورِ ساختگیِ [TestBackend] — `TestBackend` فقط `api` ِ سیم‌کشی را عوض می‌کند و مخزنِ
 *  دکان (`Wiring.shop`) پیش از آن با `api` ِ پیشین ساخته شده است.
 */
internal fun TestBackend.wireShop() {
  val wiring = Backend::class.java.getDeclaredField("wiring").apply { isAccessible = true }.get(null)
  val cls = Class.forName("ir.vil3ntec.tohid.data.repo.Backend\$Wiring")
  cls.getDeclaredField("shop").apply { isAccessible = true }.set(wiring, ir.vil3ntec.tohid.data.repo.ShopRepository(api))
}
