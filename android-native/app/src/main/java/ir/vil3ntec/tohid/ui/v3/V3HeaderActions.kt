package ir.vil3ntec.tohid.ui.v3

/**
 *  کارهای سربرگ — خالص و آزمون‌شده: دو آیکنِ اول پیدا، بقیه ردیف‌های نخستِ «…» (پیش از منوی خودِ صفحه). تا ۶۳r
 *  سومی و چهارمی بی‌صدا گم می‌شدند (`take(2)`)؛ حالا هیچ کاری بی‌راه نمی‌ماند. نقطهٔ کاری که به «…» رفت روی «…» می‌نشیند.
 */
object V3HeaderActions {
  /** بیشترین آیکنِ پیدای سربرگ (بی «…» و «راهنما») */
  const val VISIBLE = 2

  class Split(val shown: List<V3Action>, val menu: List<V3MenuItem>, val menuDot: Boolean)

  fun split(actions: List<V3Action>, overflow: List<V3MenuItem>): Split {
    val shown = actions.take(VISIBLE)
    val spill = actions.drop(VISIBLE)
    return Split(shown, spill.map { V3MenuItem(it.icon, it.label, onClick = it.onClick) } + overflow, spill.any { it.dot })
  }
}
