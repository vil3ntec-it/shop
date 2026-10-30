package ir.vil3ntec.tohid.desktop.shots

/**
 *  عکس‌های گروهِ G5 فروش — صاحبش همان گروه است؛ حالت‌های صفحه‌های تازه را این‌جا
 *  بیفزایید (`Shot(name, route)` یا `Shot(name, content = { … })`). امروز صفحه‌های
 *  پیشین از راهِ همان مسیرها.
 */
object SalesShots : GroupShots {
  override val group = "sales"
  override val shots = listOf(
    Shot("sale", route = "sale"),
    Shot("history", route = "sales"),
  )
}
