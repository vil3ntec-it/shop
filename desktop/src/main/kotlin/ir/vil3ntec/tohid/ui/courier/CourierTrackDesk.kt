package ir.vil3ntec.tohid.ui.courier

import android.content.Context

/**
 *  DLV-16 (کامپیوتر) — همان API ِ `ui/courier/CourierTrackAndroid.kt`. کامپیوتر GPS و سرویسِ پیش‌رو ندارد و پیک با
 *  کامپیوتر در راه نیست؛ پس هیچ موقعیتی فرستاده نمی‌شود و مشتری فقط همان وضعیت و زمانِ تقریبی را می‌بیند.
 */
object CourierTrackPlatform {
  @Suppress("UNUSED_PARAMETER")
  fun sync(context: Context, wanted: Boolean) = Unit
}
