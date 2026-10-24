package ir.vil3ntec.tohid.data

import android.content.Context
import android.net.Uri

/** نامِ فایلی که کاربر از پوشهٔ کامپیوتر برداشت — همان نامِ روی دیسک (نامِ فارسی هم) */
object FileName {
  fun of(context: Context, uri: Uri): String =
    uri.path?.let { java.io.File(it).name } ?: uri.lastPathSegment.orEmpty()
}
