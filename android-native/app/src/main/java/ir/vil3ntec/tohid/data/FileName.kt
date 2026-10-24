package ir.vil3ntec.tohid.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns

/**
 *  نامِ فایلی که کاربر برداشت (برای تطبیقِ «نامِ فایل = بارکد یا نامِ کالا»
 *  در ورودِ گروهیِ عکس). کامپیوتر: `FileNameDesk.kt`.
 */
object FileName {
  fun of(context: Context, uri: Uri): String = runCatching {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
      if (c.moveToFirst()) c.getString(0) else null
    }
  }.getOrNull() ?: uri.lastPathSegment.orEmpty()
}
