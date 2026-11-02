package android.app

/** shimِ iOS — در iOS اکتیویتی نیست: فقط نوع (برای `is Activity`) و ثابت‌های نتیجهٔ `ActivityResult` */
open class Activity : android.content.ContextWrapper(android.content.IosContext) {
  val window: android.view.Window = android.view.Window()

  companion object {
    const val RESULT_OK = -1
    const val RESULT_CANCELED = 0
    const val RESULT_FIRST_USER = 1
  }
}
