package android.net

import java.io.File
import java.net.URI
import java.net.URLEncoder

/** `Uri` — روی کامپیوتر یا نشانیِ وب است یا فایلی روی دیسک. */
class Uri private constructor(private val raw: String) {
  val scheme: String? get() = raw.substringBefore(':', "").ifBlank { null }
  val path: String? get() = if (scheme == "file") runCatching { File(URI(raw)).path }.getOrNull() else null
  val lastPathSegment: String? get() = (path ?: raw).substringAfterLast('/').ifBlank { null }
  override fun toString(): String = raw
  override fun equals(other: Any?) = other is Uri && other.raw == raw
  override fun hashCode() = raw.hashCode()

  companion object {
    @JvmField val EMPTY = Uri("")
    fun parse(s: String): Uri = Uri(s)
    fun fromFile(f: File): Uri = Uri(f.toURI().toString())
    fun encode(s: String?): String =
      URLEncoder.encode(s ?: "", Charsets.UTF_8).replace("+", "%20")
  }
}
