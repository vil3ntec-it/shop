package ir.vil3ntec.tohid.desktop.shots

/**
 *  عکس‌های گروهِ G8 آنلاین — صاحبش همان گروه است؛ حالت‌های صفحه‌های تازه را این‌جا
 *  بیفزایید (`Shot(name, route)` یا `Shot(name, content = { … })`). امروز صفحه‌های
 *  پیشین از راهِ همان مسیرها.
 */
object OnlineShots : GroupShots {
  override val group = "online"
  override val shots = listOf(
    Shot("orders", route = "orders"),
    Shot("storefront", route = "storefront"),
  )
}
