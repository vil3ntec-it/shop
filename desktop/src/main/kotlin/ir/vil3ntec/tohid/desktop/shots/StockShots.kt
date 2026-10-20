package ir.vil3ntec.tohid.desktop.shots

/**
 *  عکس‌های گروهِ G3 انبار — صاحبش همان گروه است؛ حالت‌های صفحه‌های تازه را این‌جا
 *  بیفزایید (`Shot(name, route)` یا `Shot(name, content = { … })`). امروز صفحه‌های
 *  پیشین از راهِ همان مسیرها.
 */
object StockShots : GroupShots {
  override val group = "stock"
  override val shots = listOf(
    Shot("list", route = "stock"),
    Shot("entry", route = "stock/entry"),
  )
}
