package android.view

import android.content.Context

/** shimِ iOS — پنجره و نمای اندروید فقط برای نوع (رنگِ نوارِ وضعیت را iOS از تمِ روشن/تیره می‌گیرد) */
open class View(val context: Context)

class Window {
  var statusBarColor: Int = 0
  var navigationBarColor: Int = 0
  val decorView: View = View(android.content.IosContext)
}
