package ir.vil3ntec.tohid.ui.components.sync

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrTill
import ir.vil3ntec.tohid.core.sync.ClockSkew
import ir.vil3ntec.tohid.plain
import ir.vil3ntec.tohid.ui.theme.Shop

/**
 *  S28b — نوارِ قرمزِ ساعتِ دستگاه (MASTER-SPEC بخشِ ۳.۸.۱ بندِ ۸، قاعده در `core/sync/ClockSkew`).
 *
 *  ⛔ فقط با Sync v3 ِ همین دستگاه (`SyncChoice`، تنظیمِ هر دستگاه، پیش‌فرض v1): اختلافِ ساعت از پاسخِ سرور
 *  (`V3Status.clockOffsetMs`) و «بالاترین زمانِ دیده‌شده» (`SyncStore.clockSeen`). v1 (پیش‌فرض) ⇒ هیچ.
 *  سند جابه‌جا نمی‌شود: با همان ساعتِ دستگاه ثبت می‌شود و «تاریخِ تصحیح‌شده» را سرور کنارش می‌گذارد.
 */
@Composable
fun clockSkewText(): String? {
  val context = LocalContext.current
  val v3 = remember { runCatching { ir.vil3ntec.tohid.sync.v3.SyncChoice.isV3(context) }.getOrDefault(false) }
  if (!v3) return null
  val status by ir.vil3ntec.tohid.sync.v3.SyncV3Runner.of(context).status.collectAsState()
  val seen = remember(status) { runCatching { ir.vil3ntec.tohid.sync.SyncStore(context).clockSeen }.getOrDefault(0L) }
  val offset = status?.takeIf { it.initialized }?.clockOffsetMs
  val level = ClockSkew.level(offset, System.currentTimeMillis(), seen)
  if (!ClockSkew.red(level)) return null
  val (d, h, m) = ClockSkew.gap(offset ?: (seen - System.currentTimeMillis()))
  val gap = listOfNotNull(
    StrTill.gapDays(plain(d.toInt())).takeIf { d > 0 },
    StrTill.gapHours(plain(h.toInt())).takeIf { h > 0 && d < 30 },
    StrTill.gapMinutes(plain(m.toInt())).takeIf { d == 0L && h == 0L },
  ).joinToString(" ")
  return if (level == ClockSkew.Level.AHEAD) StrTill.clockAhead(gap) else StrTill.clockBehind(gap)
}

/** نوارِ تمام‌عرضِ رابطِ امروز (زیرِ نوارِ اشتراک) */
@Composable
fun ClockSkewBanner() {
  val text = clockSkewText() ?: return
  Surface(color = Shop.colors.danger, modifier = Modifier.fillMaxWidth()) {
    Text(text, color = Color.White, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
  }
}
