package ir.vil3ntec.tohid.core.staff

/**
 *  STF-06 — دسترسیِ کارمند محدود به شعبه یا انبار (§۶.۹). کاتلینِ خالص؛ همان قاعدهٔ
 *  `server/src/lib/staff-scope.js` که **مرجع** است — برنامه فقط پنهان می‌کند و دکمه را می‌بندد.
 *
 *  • مکان‌ها همان دو سرِ انتقالِ موجودیِ سرور‌اند: قفسهٔ فروشگاه (`shop_floor`) و انبار (`warehouse`).
 *    «خراب» و «در مسیر» انبارِ مشترکِ همهٔ دکان‌اند، نه مکان.
 *  • فهرستِ خالی یعنی «همه» (همان رفتارِ پیش از STF-06)؛ انتخابِ همهٔ مکان‌ها هم «همه» است.
 *  • کارِ عضوِ محدود: همهٔ مکان‌هایی که نام می‌برد در محدوده و دست‌کم یکی — وگرنه سرور ۴۰۳ ِ
 *    `location_out_of_scope` می‌دهد.
 *  • صاحب با کلیدها محدوده می‌دهد؛ خاموش کردنِ آخرین مکانِ روشن پذیرفته نیست (چون خالی = همه).
 */
object StaffScope {

  const val SHOP_FLOOR = "shop_floor"
  const val WAREHOUSE = "warehouse"
  val PLACES: List<String> = listOf(SHOP_FLOOR, WAREHOUSE)

  /** فقط مکان‌های شناخته، به ترتیبِ [PLACES]؛ همه = خالی (= بی‌محدودیت) */
  fun normalize(places: Collection<String>): List<String> {
    val known = PLACES.filter { it in places }
    return if (known.size == PLACES.size) emptyList() else known
  }

  fun restricted(mine: Collection<String>): Boolean = normalize(mine).isNotEmpty()

  /** مکان‌هایی که صفحه نشان می‌دهد: بی‌محدودیت ⇒ همان فهرست، وگرنه فقط محدوده */
  fun visible(locations: List<String>, mine: Collection<String>): List<String> {
    val scope = normalize(mine)
    return if (scope.isEmpty()) locations else locations.filter { it in scope }
  }

  /** همان `allows` ِ سرور */
  fun allows(mine: Collection<String>, named: List<String>): Boolean {
    val scope = normalize(mine)
    if (scope.isEmpty()) return true
    val places = named.filter { it in PLACES }
    return places.isNotEmpty() && places.all { it in scope }
  }

  /** کلیدِ این مکان در صفحهٔ صاحب روشن است؟ (خالی = همه روشن) */
  fun isOn(current: Collection<String>, place: String): Boolean = normalize(current).let { it.isEmpty() || place in it }

  /**
   *  محدودهٔ تازه پس از زدنِ کلیدِ یک مکان؛ `null` یعنی پذیرفته نیست (مکانِ ناشناخته، یا
   *  خاموش کردنِ آخرین مکانِ روشن که «همه» می‌شد).
   */
  fun toggle(current: Collection<String>, place: String, on: Boolean): List<String>? {
    if (place !in PLACES) return null
    val now = normalize(current).ifEmpty { PLACES }
    val next = if (on) (now + place).distinct() else now - place
    if (next.isEmpty()) return null
    return normalize(next)
  }
}
