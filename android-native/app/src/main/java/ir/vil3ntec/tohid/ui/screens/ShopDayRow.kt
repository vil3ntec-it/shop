package ir.vil3ntec.tohid.ui.screens

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.calendar.ShopDay
import ir.vil3ntec.tohid.core.i18n.StrSettings
import ir.vil3ntec.tohid.displayDate
import ir.vil3ntec.tohid.todayIso
import ir.vil3ntec.tohid.ui.theme.Shop

/**
 *  «پایانِ روزِ کاری» در «تاریخ و رقم» (LOC-03): نیمه‌شب (پیش‌فرض، رفتارِ امروز) تا ۰۴:۰۰.
 *  روشن ⇒ زیرش تاریخی که فروشِ همین لحظه با آن ثبت می‌شود، تا اثرِ انتخاب پیدا باشد.
 *  ذخیره با صدازننده است (`ShopDayChoice.set`)؛ این‌جا هیچ `Context`ی نیست.
 */
@Composable
fun ShopDayRow(hours: Int, onSelect: (Int) -> Unit) {
  Text(
    StrSettings.dayEndNote,
    style = MaterialTheme.typography.labelSmall,
    color = Shop.colors.muted2,
  )
  Spacer(Modifier.height(8.dp))
  SegmentedChoice(
    options = ShopDay.CHOICES.map { it.toString() to ShopDay.label(it) },
    selected = ShopDay.clamp(hours).toString(),
    onSelect = { onSelect(it.toIntOrNull() ?: ShopDay.OFF) },
  )
  if (ShopDay.clamp(hours) != ShopDay.OFF) {
    Spacer(Modifier.height(6.dp))
    Text(
      StrSettings.dayEndNow(displayDate(todayIso())),
      style = MaterialTheme.typography.labelSmall,
      color = Shop.colors.muted,
    )
  }
}
