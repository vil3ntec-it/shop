package ir.vil3ntec.tohid.desktop.shots

/**
 *  عکس‌های گروهِ G10 پوسته — صاحبش همان گروه است؛ حالت‌های صفحه‌های تازه را این‌جا
 *  بیفزایید (`Shot(name, route)` یا `Shot(name, content = { … })`). امروز صفحه‌های
 *  پیشین از راهِ همان مسیرها.
 */
object ShellShots : GroupShots {
  override val group = "shell"
  override val shots = listOf(
    Shot("home", route = "home"),
    Shot("notifications", route = "notifications"),
    Shot("search", route = "search"),
  )
}
