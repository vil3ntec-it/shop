package ir.vil3ntec.tohid.desktop.shots

/**
 *  عکس‌های گروهِ G2 کالاها — صاحبش همان گروه است؛ حالت‌های صفحه‌های تازه را این‌جا
 *  بیفزایید (`Shot(name, route)` یا `Shot(name, content = { … })`). امروز صفحه‌های
 *  پیشین از راهِ همان مسیرها.
 */
object ProductsShots : GroupShots {
  override val group = "products"
  override val shots = listOf(
    Shot("list", route = "products"),
    Shot("new", route = "products/new"),
  )
}
