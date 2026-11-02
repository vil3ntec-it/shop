package android.content

/** shimِ iOS ِ `ContextWrapper` — همه‌چیز به Context ِ درونی (کدِ مشترک فقط از آن `baseContext` را می‌خواند) */
open class ContextWrapper(val baseContext: Context) : Context() {
  override val applicationContext: Context get() = baseContext.applicationContext
  override fun getSharedPreferences(name: String, mode: Int): SharedPreferences = baseContext.getSharedPreferences(name, mode)
  override fun getSystemService(name: String): Any? = baseContext.getSystemService(name)
  override fun startActivity(intent: Intent) = baseContext.startActivity(intent)
}
