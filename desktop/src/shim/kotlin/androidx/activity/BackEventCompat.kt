package androidx.activity

/** همان `BackEventCompat` ِ اندروید (فقط برای کامپایلِ کامپیوتر؛ آن‌جا رویدادِ پیش‌نمایی نمی‌آید) */
class BackEventCompat(
  val touchX: Float,
  val touchY: Float,
  val progress: Float,
  val swipeEdge: Int,
) {
  companion object {
    const val EDGE_LEFT = 0
    const val EDGE_RIGHT = 1
  }
}
