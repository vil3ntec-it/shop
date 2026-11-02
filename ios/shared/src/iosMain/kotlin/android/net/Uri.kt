package android.net

import java.io.File
import java.net.URLDecoder
import java.net.URLEncoder

/** shimِ iOS ِ `android.net.Uri` — همان `desktop/src/shim/kotlin/android/net/Uri.kt`: فقط متنِ نشانی و پرونده */
class Uri private constructor(private val raw: String) {
  val scheme: String? get() = raw.substringBefore(':', "").ifBlank { null }
  val path: String? get() = if (scheme == "file") URLDecoder.decode(raw.removePrefix("file://").replace("+", "%2B"), "UTF-8") else null
  val lastPathSegment: String? get() = (path ?: raw).substringAfterLast('/').ifBlank { null }
  override fun toString(): String = raw
  override fun equals(other: Any?) = other is Uri && other.raw == raw
  override fun hashCode() = raw.hashCode()

  companion object {
    val EMPTY = Uri("")
    fun parse(s: String): Uri = Uri(s)
    fun fromFile(f: File): Uri = Uri("file://" + f.path.split('/').joinToString("/") { encode(it) })
    fun encode(s: String?): String = URLEncoder.encode(s ?: "", "UTF-8").replace("+", "%20")
  }
}
