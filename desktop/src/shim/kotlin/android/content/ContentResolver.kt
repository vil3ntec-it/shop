package android.content

import android.net.Uri
import java.io.File
import java.io.InputStream
import java.io.OutputStream

/** فایلِ پشتِ یک `Uri`ِ `file:` — همان چیزی که پنجرهٔ انتخابِ فایل می‌دهد. */
class ContentResolver {
  fun openInputStream(uri: Uri): InputStream? = uri.file()?.takeIf { it.isFile }?.inputStream()
  fun openOutputStream(uri: Uri): OutputStream? = uri.file()?.let { f ->
    f.parentFile?.mkdirs()
    f.outputStream()
  }
  fun openOutputStream(uri: Uri, mode: String): OutputStream? = openOutputStream(uri)
  fun getType(uri: Uri): String? = null

  private fun Uri.file(): File? = path?.let(::File)
}
