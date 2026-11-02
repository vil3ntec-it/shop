package ir.vil3ntec.tohid.data

import android.content.Context
import android.net.Uri

/** جایگزینِ iOS ِ `data/FileName.kt` (نامِ فایلِ برگزیده از ContentResolver ِ اندروید؛ همان ‎FileNameDesk‎): نامِ مسیرِ `file://` */
object FileName {
  fun of(context: Context, uri: Uri): String = uri.path?.let { java.io.File(it).name } ?: uri.lastPathSegment.orEmpty()
}
