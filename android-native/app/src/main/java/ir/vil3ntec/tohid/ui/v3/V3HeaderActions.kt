package ir.vil3ntec.tohid.ui.v3

/**
 *  کارهای سربرگ — خالص و آزمون‌شده: دو آیکنِ اول پیدا، بقیه ردیف‌های نخستِ «…» (پیش از منوی خودِ صفحه). تا ۶۳r
 *  سومی و چهارمی بی‌صدا گم می‌شدند (`take(2)`)؛ حالا هیچ کاری بی‌راه نمی‌ماند. نقطهٔ کاری که به «…» رفت روی «…» می‌نشیند.
 */
object V3HeaderActions {
  /** بیشترین آیکنِ پیدای سربرگ (بی «…» و «راهنما») */
  const val VISIBLE = 2

  class Split(val shown: List<V3Action>, val menu: List<V3MenuItem>, val menuDot: Boolean)

  fun split(actions: List<V3Action>, overflow: List<V3MenuItem>, visible: Int = VISIBLE): Split {
    val shown = actions.take(visible)
    val spill = actions.drop(visible)
    return Split(shown, spill.map { V3MenuItem(it.icon, it.label, onClick = it.onClick) } + overflow, spill.any { it.dot })
  }

  /**
   *  ۶۵s (هماهنگ‌کننده: عنوانِ وسطِ نوار هرگز زیرِ آیکن نرود): چند کار از [total] پیدا بماند — دست‌بالا [VISIBLE] و آن‌قدر کم
   *  که [fits] (با شمارِ آیکن‌های همان سو، «…» هم یک آیکن) عنوانِ کامل را وسط جا دهد؛ بقیه به «…». اگر با یک «…» تنها هم جا
   *  نشد، صفر (عنوان در جای خالی کوتاه می‌شود — `V3HeaderBar`).
   */
  fun visibleFor(total: Int, hasOverflow: Boolean, fits: (icons: Int) -> Boolean): Int {
    var shown = minOf(total, VISIBLE)
    while (shown > 0) {
      val icons = shown + if (total > shown || hasOverflow) 1 else 0
      if (fits(icons)) return shown
      shown--
    }
    return 0
  }
}
