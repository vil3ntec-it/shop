package android.content

/*
 *  shimِ iOS ِ «گیرندهٔ پخش» ِ اندروید (دستهٔ یازدهم: جست‌وجوی بلوتوثِ «دستگاه‌ها»، `ScanBootReceiver`). iOS پخشِ
 *  سیستمیِ اندروید ندارد: ثبت‌کردن چیزی نمی‌رساند (`Context.registerReceiver` ⇐ null) و هیچ `onReceive` صدا زده
 *  نمی‌شود — کدِ مشترک پیش از آن «بلوتوث نیست» را دیده است (‎android/bluetooth‎).
 */
abstract class BroadcastReceiver {
  abstract fun onReceive(context: Context, intent: Intent)
}

class IntentFilter(action: String? = null) {
  private val actions = ArrayList<String>().apply { action?.let { add(it) } }
  fun addAction(action: String) { actions += action }
  fun hasAction(action: String): Boolean = action in actions
  fun countActions(): Int = actions.size
}
