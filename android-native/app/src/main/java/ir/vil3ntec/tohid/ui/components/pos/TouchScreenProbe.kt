package ir.vil3ntec.tohid.ui.components.pos

/**
 *  POS-31 — صفحهٔ لمسیِ کامپیوتر (§۵.۳). گوشی همیشه لمسی است و این پرسش را ندارد (`TouchMode.touch`)؛ برنامهٔ
 *  کامپیوتر جایگزینِ خودش را دارد (‎desktop/…/ui/components/pos/TouchScreenProbeDesk.kt‎: ویندوز و لینوکس از سیستم‌عامل).
 */
object TouchScreenProbe {
  val detected: Boolean get() = false
}
