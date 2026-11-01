package ir.vil3ntec.tohid.hw

import android.content.Context

/**
 *  HW-13 روی کامپیوتر: ویندوز، مک و لینوکس اجازهٔ «شبکهٔ محلی» ِ اندروید ۱۷ را ندارند؛ چاپگرِ
 *  شبکه همیشه همان راهِ امروز را می‌رود. همان API ِ `hw/LocalNetworkAndroid.kt`.
 */
object LocalNetworkAccess {
  const val PERMISSION = "android.permission." + ir.vil3ntec.tohid.core.hw.LocalNetwork.PERMISSION_NAME

  fun needed(context: Context): Boolean = false

  fun granted(context: Context): Boolean = true

  fun blocked(context: Context, host: String): Boolean = false
}
