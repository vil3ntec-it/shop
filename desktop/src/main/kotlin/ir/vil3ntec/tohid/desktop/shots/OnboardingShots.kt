package ir.vil3ntec.tohid.desktop.shots

/**
 *  عکس‌های گروهِ G1 ورود — صاحبش همان گروه است؛ حالت‌های صفحه‌های تازه را این‌جا
 *  بیفزایید (`Shot(name, route)` یا `Shot(name, content = { … })`). امروز صفحه‌های
 *  پیشین از راهِ همان مسیرها.
 */
object OnboardingShots : GroupShots {
  override val group = "onboarding"
  override val shots = listOf(
    Shot("auth", route = "auth"),
  )
}
