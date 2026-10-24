package ir.vil3ntec.tohid.desktop.shots

/**
 *  عکس‌های گروهِ G4 پول — صاحبش همان گروه است؛ حالت‌های صفحه‌های تازه را این‌جا
 *  بیفزایید (`Shot(name, route)` یا `Shot(name, content = { … })`). امروز صفحه‌های
 *  پیشین از راهِ همان مسیرها.
 */
object MoneyShots : GroupShots {
  override val group = "money"
  override val shots = listOf(
    Shot("debtors", route = "debtors"),
    Shot("expenses", route = "expenses"),
    Shot("purchasing", route = "purchasing"),
  )
}
