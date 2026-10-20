package ir.vil3ntec.tohid.desktop.shots

/**
 *  عکس‌های گروهِ G6 گزارش — صاحبش همان گروه است؛ حالت‌های صفحه‌های تازه را این‌جا
 *  بیفزایید (`Shot(name, route)` یا `Shot(name, content = { … })`). امروز صفحه‌های
 *  پیشین از راهِ همان مسیرها.
 */
object AnalyticsShots : GroupShots {
  override val group = "analytics"
  override val shots = listOf(
    Shot("reports", route = "reports"),
    Shot("admin", route = "admin"),
  )
}
