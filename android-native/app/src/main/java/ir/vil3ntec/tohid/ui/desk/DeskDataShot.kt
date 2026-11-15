package ir.vil3ntec.tohid.ui.desk

import androidx.compose.runtime.mutableStateOf

/**
 *  فقط برای عکس‌های خودکار (`D3bShotsTest`) — صفحه‌های داده‌ایِ کامپیوتر (`claude/e-desk-d3b`) با [pick] = «first» نخستین
 *  ردیف را برمی‌گزینند تا پنلِ جزئیات در عکس دیده شود، و «خرید و تأمین‌کننده» با [tab] زبانه‌اش را. در کارِ عادی دست نمی‌خورد.
 */
object DeskDataShot {
  val pick = mutableStateOf<String?>(null)
  val tab = mutableStateOf<String?>(null)
}
