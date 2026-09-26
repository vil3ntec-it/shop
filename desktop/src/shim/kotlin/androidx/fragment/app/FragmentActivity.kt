package androidx.fragment.app

/**
 *  روی کامپیوتر هیچ `Context`ی `FragmentActivity` نیست؛ پس `AppLock`
 *  اثرِ انگشت را پیشنهاد نمی‌کند و همان صفحه‌کلیدِ رمز می‌ماند.
 */
open class FragmentActivity : android.content.Context() {
  override val filesDir get() = error("no")
  override val cacheDir get() = error("no")
  override fun getSharedPreferences(name: String, mode: Int) = error("no")
  override val contentResolver get() = error("no")
  override val packageManager get() = error("no")
  override fun getSystemService(name: String): Any? = null
  override fun <T> getSystemService(serviceClass: Class<T>): T? = null
  override fun startActivity(intent: android.content.Intent) {}
}
