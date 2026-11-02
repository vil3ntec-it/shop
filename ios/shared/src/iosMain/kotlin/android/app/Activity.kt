package android.app

/** shimِ iOS — فقط ثابت‌های نتیجهٔ `ActivityResult` (در iOS Activity ِ اندروید نیست) */
open class Activity {
  companion object {
    const val RESULT_OK = -1
    const val RESULT_CANCELED = 0
    const val RESULT_FIRST_USER = 1
  }
}
