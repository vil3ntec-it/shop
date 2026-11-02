package ir.vil3ntec.tohid

/*
 *  `R` ِ iOS — همان شناسه‌های `desktop/src/shim/kotlin/ir/vil3ntec/tohid/R.kt`؛ پرونده‌ها از
 *  `android-native/app/src/main/res` در بستهٔ برنامهٔ آیفون کپی می‌شوند (مرحلهٔ Resources ِ
 *  `ios/iosApp`). فعلاً فقط قلم‌ها در بسته‌اند؛ تصویر و صدا کارِ گامِ ۲.
 */
object R {
  private val names = HashMap<Int, String>()
  private fun reg(id: Int, name: String): Int { names[id] = name; return id }

  /** نامِ پرونده در بستهٔ برنامه (بی پسوند) و پسوندش */
  fun file(id: Int): Pair<String, String> = (names[id] ?: error("منبعِ ناشناخته: $id")).let { it.substringBeforeLast('.') to it.substringAfterLast('.') }

  object font {
    val vazirmatn_regular = reg(1001, "vazirmatn_regular.ttf")
    val vazirmatn_medium = reg(1002, "vazirmatn_medium.ttf")
    val vazirmatn_semibold = reg(1003, "vazirmatn_semibold.ttf")
    val vazirmatn_bold = reg(1004, "vazirmatn_bold.ttf")
  }
}
