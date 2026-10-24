package ir.vil3ntec.tohid.desktop.shots

/**
 *  عکس‌های گروهِ حالتِ خرید — صاحبش همان گروه است؛ حالت‌های صفحه‌های تازه را این‌جا
 *  بیفزایید (`Shot(name, route)` یا `Shot(name, content = { … })`). امروز صفحه‌های
 *  پیشین از راهِ همان مسیرها.
 */
object ShopperShots : GroupShots {
  override val group = "shopper"
  override val shots = listOf(
    Shot("home", route = "shopper/home"),
    Shot("shop", route = "shopper/shop/karim-store"),
    Shot("account", route = "shopper/account"),
  )
}
