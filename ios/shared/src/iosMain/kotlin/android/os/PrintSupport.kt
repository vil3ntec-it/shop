package android.os

import java.io.File
import java.io.FileDescriptor

/*
 *  shimِ iOS ِ سه نامِ `android.os` که سازگارکنندهٔ چاپِ اندروید (`PrintDocumentAdapter`) می‌گیرد — گامِ ۴
 *  (`Bundle` ِ `extras` ِ اعلان هم، ‎android/app/Notification.kt‎).
 *  `ParcelFileDescriptor` در iOS فقط «پرونده‌ای در cache» است که `IosPrint` می‌سازد و کدِ مشترک با
 *  `FileOutputStream(destination.fileDescriptor)` در آن PDF می‌نویسد.
 */

/** کیسهٔ کلید/مقدار؛ چاپ همیشه null می‌دهد (همان `extras` ِ خالیِ اندروید) */
class Bundle {
  private val map = HashMap<String, Any?>()
  fun putString(key: String, value: String?) { map[key] = value }
  fun putCharSequence(key: String, value: CharSequence?) { map[key] = value }
  fun getCharSequence(key: String): CharSequence? = map[key] as? CharSequence
  fun getString(key: String): String? = map[key] as? String
  fun putInt(key: String, value: Int) { map[key] = value }
  fun getInt(key: String, defaultValue: Int = 0): Int = map[key] as? Int ?: defaultValue
  fun putBoolean(key: String, value: Boolean) { map[key] = value }
  fun getBoolean(key: String, defaultValue: Boolean = false): Boolean = map[key] as? Boolean ?: defaultValue
  fun containsKey(key: String): Boolean = key in map
  fun isEmpty(): Boolean = map.isEmpty()
}

/** لغو؛ پنجرهٔ چاپِ iOS پس از نوشتنِ PDF باز می‌شود، پس هیچ نوشتنی وسطِ کار لغو نمی‌شود */
class CancellationSignal {
  var isCanceled: Boolean = false
    private set

  fun cancel() { isCanceled = true }
}

class ParcelFileDescriptor internal constructor(internal val file: File) {
  val fileDescriptor: FileDescriptor = FileDescriptor(file.path)

  fun close() {}
}
