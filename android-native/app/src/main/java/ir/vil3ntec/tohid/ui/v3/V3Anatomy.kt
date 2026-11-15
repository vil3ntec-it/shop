package ir.vil3ntec.tohid.ui.v3

/**
 *  کالبدِ یکسانِ هر صفحهٔ گوشی (کارِ `claude/e-phone-p0`، صاحب‌کار ۱۴۰۵/۰۸/۲۴: «یک بخش یک مدل، یک بخش مدلِ دیگه … کجای
 *  این برنامه الان ساده است؟»). همهٔ صفحه‌ها یک کالبد دارند، به این ترتیب و فقط از همین اجزا:
 *
 *  1. **نوارِ بالا** (`V3Scaffold` ⇐ `V3HeaderBar`): برگشت (اگر ریشهٔ نوار نیست) · عنوانِ وسط · دست‌بالا دو آیکن · «…».
 *     هیچ عنوانِ درشتی در بدنه نیست (`largeTitle` فقط صفحه‌های تنظیمات از `SettingsPageFrame`، و خانه که سربرگِ خودش را دارد).
 *  2. **دست‌بالا یک خلاصه** (`V3Summary`/`V3SummaryFrame`) و فقط جایی که عدد تصمیم می‌سازد؛ هیچ کارت/نوارِ سرخطِ دیگری در بدنه.
 *  3. **پالایه‌ها یک ردیف** (`V3ChipRow`، یا دکمهٔ «فیلتر» که برگه باز می‌کند) درست زیرِ نوار (`V3Scaffold(chips = …)`).
 *  4. **محتوا**: فهرستِ داده `V3DataRow`، فهرستِ ناوبری `V3NavRow`؛ جزئیات با یک سبکِ کارتِ بخش (`V3Section`)؛ فرم با یک سبک
 *     (`V3Form`/`V3TextField`: برچسب بالای کادر، الزامی‌ها اول، اختیاری‌ها زیرِ «بیشتر»، انتخاب با کادرِ کشویی).
 *  5. **کارِ اصلی یک دکمهٔ شناور** (`V3Fab` با برچسب، از `V3Scaffold(fab = …)`، همه‌جا همان گوشه)؛ کارهای دیگر در «…».
 *  6. **خالی** `V3EmptyState` وسطِ صفحه؛ **برگه** فقط `V3Sheet`.
 *
 *  نشان‌های آزمونِ زیر را خودِ اجزای مشترک می‌گذارند (فقط `testTag`؛ صفحه‌خوان نمی‌خواند) تا `PageAnatomyGuardTest` و
 *  ممیزیِ عکس (`PhoneAnatomyAuditTest`) کالبدِ هر مسیر را از درختِ واقعیِ صفحه بشمارند، نه از حدس.
 */
object V3Anatomy {
  /** ریشهٔ هر `V3Scaffold` */
  const val PAGE = "anatomy.page"

  /** نوارِ بالا (`V3HeaderBar`) */
  const val BAR = "anatomy.bar"

  /** عنوانِ درشتِ زیرِ نوار (`V3Scaffold(largeTitle = true)`) */
  const val LARGE_TITLE = "anatomy.largeTitle"

  /** کارتِ خلاصهٔ بالای صفحه (`V3SummaryFrame`، پس هر `V3Summary` هم) */
  const val SUMMARY = "anatomy.summary"

  /** دکمهٔ شناورِ کارِ اصلی (`V3Fab`) */
  const val FAB = "anatomy.fab"

  /** ردیفِ پالایه/تراشه (`V3ChipRow`) */
  const val CHIPS = "anatomy.chips"

  /** کارتِ بخش (`V3Card`، پس هر `V3Section` هم) */
  const val CARD = "anatomy.card"

  /** ردیفِ سادهٔ `V3ListRow` (نه داده، نه ناوبری) */
  const val LIST_ROW = "anatomy.listRow"
}
