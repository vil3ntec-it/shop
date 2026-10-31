package ir.vil3ntec.tohid.core.guide

/*
 *  OPS-06 — راهنمای هر صفحه (F1) و ویدیوی کوتاهِ آموزشی: **ساختار و جای خالی**.
 *
 *  امروز F1 فقط فهرستِ میان‌برهای صفحه‌کلید را نشان می‌دهد. این‌جا برای هر صفحه یک
 *  راهنمای کوتاه (چند گام) و یک ویدیوی کوتاهِ اختیاری جا دارد. **متن و ویدیو را صاحب محصول
 *  می‌دهد** (`docs/spec/OWNER-ACTIONS-fa.md` بخشِ «OPS-06»)؛ تا آن‌وقت هر صفحه
 *  [GuideStatus.PLACEHOLDER] است و **هیچ چیزی به کاربر نشان داده نمی‌شود** — نه «به‌زودی»،
 *  نه متنِ نیمه‌کاره.
 *
 *  پر کردنِ یک صفحه: در [ALL] همان ردیف را با `steps` (۱ تا [MAX_STEPS] گام، هر کدام حداکثر
 *  [MAX_STEP] نویسه، فارسی اجباری، انگلیسی و پشتو اختیاری) و اگر هست `video` پر کن و
 *  `status = READY` بگذار. [problems] قاعده‌ها را می‌سنجد و ردیفِ READY ِ ناقص هم نشان داده
 *  نمی‌شود (آزمون: `ScreenGuidesTest`).
 *
 *  شناسهٔ صفحه همان مسیرِ `AppRoot` است (`dashboard`، `sale`، …) — همان که
 *  `ui/AppNav.SHORTCUTS` و `AppNav.screen` می‌گویند.
 */

enum class GuideStatus { PLACEHOLDER, READY }

/** یک نوشته در سه زبان؛ انگلیسی یا پشتوی خالی ⇒ همان فارسی (قاعدهٔ `I18n.kt`) */
data class GuideText(val fa: String, val en: String = "", val ps: String = "") {
  fun of(lang: String): String = when (lang) {
    "en" -> en.ifBlank { fa }
    "ps" -> ps.ifBlank { fa }
    else -> fa
  }
}

/**
 *  ویدیوی کوتاه: فایلِ MP4 روی سرورِ خودمان (`/guides/<نام>.mp4`)، نه سرویسِ بیرونی
 *  (در افغانستان در دسترس نیست یا گران است). [seconds] برای نشان دادنِ «۴۵ ثانیه» پیش از دانلود.
 */
data class GuideVideo(val path: String, val seconds: Int, val bytes: Long)

data class ScreenGuide(
  val screen: String,
  val title: GuideText,
  val steps: List<GuideText> = emptyList(),
  val video: GuideVideo? = null,
  val status: GuideStatus = GuideStatus.PLACEHOLDER,
)

object ScreenGuides {

  const val MAX_STEPS = 7
  const val MAX_STEP = 160
  const val MIN_VIDEO_SECONDS = 15
  const val MAX_VIDEO_SECONDS = 120
  /** ویدیوی بزرگ‌تر برای اینترنتِ کند و گران نیست */
  const val MAX_VIDEO_BYTES = 8L * 1024 * 1024

  private val SCREEN_ID = Regex("^[a-z][a-z0-9_-]{1,30}$")
  private val VIDEO_PATH = Regex("^/guides/[a-z0-9][a-z0-9-]{0,60}\\.mp4$")

  private fun placeholder(screen: String, title: String) = ScreenGuide(screen, GuideText(title))

  /**
   *  همهٔ صفحه‌هایی که راهنما می‌گیرند — امروز همه جای خالی. ترتیب = ترتیبِ فهرستِ
   *  «چه چیزی را صاحب محصول بدهد».
   */
  val ALL: List<ScreenGuide> = listOf(
    placeholder("dashboard", "داشبورد"),
    placeholder("sale", "صندوقِ فروش"),
    placeholder("debtors", "مشتری‌ها و قرض‌داران"),
    placeholder("receipts", "دریافتی‌ها"),
    placeholder("products", "کالاها"),
    placeholder("warehouse", "انبار"),
    placeholder("purchasing", "خرید و تأمین‌کننده‌ها"),
    placeholder("sales", "تاریخچهٔ فروش"),
    placeholder("expenses", "مصارف"),
    placeholder("reports", "گزارش‌ها"),
    placeholder("orders", "سفارش‌های اینترنتی"),
    placeholder("storefront", "ویترینِ اینترنتی"),
    placeholder("promos", "تخفیف‌ها"),
    placeholder("team", "کارمندان"),
    placeholder("devices", "دستگاه‌ها و چاپگر"),
    placeholder("settings", "تنظیمات"),
  )

  /** قاعده‌های ردیفِ READY؛ خالی ⇒ درست. جای خالی همیشه درست است (نشان داده نمی‌شود). */
  fun problems(g: ScreenGuide): List<String> {
    val out = ArrayList<String>()
    if (!SCREEN_ID.matches(g.screen)) out += "شناسهٔ صفحه: ${g.screen}"
    if (g.title.fa.isBlank()) out += "عنوانِ فارسی خالی است"
    if (g.status == GuideStatus.PLACEHOLDER) return out
    if (g.steps.isEmpty() || g.steps.size > MAX_STEPS) out += "گام‌ها باید ۱ تا $MAX_STEPS باشند (${g.steps.size})"
    g.steps.forEachIndexed { i, s ->
      if (s.fa.isBlank()) out += "گامِ ${i + 1}: فارسی خالی است"
      for (t in listOf(s.fa, s.en, s.ps)) if (t.length > MAX_STEP) out += "گامِ ${i + 1}: بیش از $MAX_STEP نویسه"
    }
    g.video?.let { v ->
      if (!VIDEO_PATH.matches(v.path)) out += "نشانیِ ویدیو: ${v.path}"
      if (v.seconds !in MIN_VIDEO_SECONDS..MAX_VIDEO_SECONDS) out += "طولِ ویدیو ${v.seconds} ثانیه"
      if (v.bytes !in 1..MAX_VIDEO_BYTES) out += "حجمِ ویدیو ${v.bytes} بایت"
    }
    return out
  }

  /** راهنمای آمادهٔ همین صفحه؛ جای خالی، ناقص یا ناشناخته ⇒ null (هیچ چیز نشان داده نمی‌شود) */
  fun forScreen(screen: String?, all: List<ScreenGuide> = ALL): ScreenGuide? =
    all.firstOrNull { it.screen == screen && it.status == GuideStatus.READY && problems(it).isEmpty() }

  /** صفحه‌هایی که هنوز متن یا ویدیو ندارند — همان فهرستِ کارِ صاحب محصول */
  fun pending(): List<ScreenGuide> = ALL.filter { it.status == GuideStatus.PLACEHOLDER || it.video == null }
}
