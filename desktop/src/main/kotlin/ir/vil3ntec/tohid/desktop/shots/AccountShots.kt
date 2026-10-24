package ir.vil3ntec.tohid.desktop.shots

/**
 *  عکس‌های گروهِ G7 حساب — صاحبش همان گروه است؛ حالت‌های صفحه‌های تازه را این‌جا
 *  بیفزایید (`Shot(name, route)` یا `Shot(name, content = { … })`). امروز صفحه‌های
 *  پیشین از راهِ همان مسیرها.
 */
object AccountShots : GroupShots {
  override val group = "account"
  override val shots = listOf(
    Shot("profile", route = "profile"),
    Shot("subscription", route = "subscription"),
  )
}
