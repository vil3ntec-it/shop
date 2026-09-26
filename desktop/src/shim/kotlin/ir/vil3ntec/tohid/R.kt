package ir.vil3ntec.tohid

/**
 *  `R`ِ اندروید — شناسهٔ عددی برای هر منبع، و نشانیِ همان فایل در
 *  منابعِ برنامهٔ کامپیوتر (`build.gradle.kts` پوشهٔ `res`ِ اندروید را
 *  زیرِ `tohid/` کپی می‌کند).
 */
object R {
  private val paths = HashMap<Int, String>()
  private fun reg(id: Int, path: String): Int { paths[id] = "tohid/$path"; return id }
  fun path(id: Int): String = paths[id] ?: error("منبعِ ناشناخته: $id")

  object font {
    val vazirmatn_regular = reg(1001, "font/vazirmatn_regular.ttf")
    val vazirmatn_medium = reg(1002, "font/vazirmatn_medium.ttf")
    val vazirmatn_semibold = reg(1003, "font/vazirmatn_semibold.ttf")
    val vazirmatn_bold = reg(1004, "font/vazirmatn_bold.ttf")
  }
  object drawable {
    val logo_mark = reg(2001, "drawable/logo_mark.png")
    val ic_launcher_monochrome = reg(2002, "drawable/ic_launcher_monochrome.png")
  }
  object raw {
    val scan_beep = reg(3001, "raw/scan_beep.mp3")
  }
}
