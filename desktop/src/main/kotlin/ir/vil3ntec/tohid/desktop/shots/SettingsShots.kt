package ir.vil3ntec.tohid.desktop.shots

/**
 *  عکس‌های گروهِ G9 تنظیمات — صاحبش همان گروه است؛ حالت‌های صفحه‌های تازه را این‌جا
 *  بیفزایید (`Shot(name, route)` یا `Shot(name, content = { … })`). امروز صفحه‌های
 *  پیشین از راهِ همان مسیرها.
 */
object SettingsShots : GroupShots {
  override val group = "settings"
  override val shots = listOf(
    Shot("root", route = "settings"),
    Shot("security", route = "settings/security"),
    Shot("app-lock", route = "settings/security/app-lock"),
  )
}
