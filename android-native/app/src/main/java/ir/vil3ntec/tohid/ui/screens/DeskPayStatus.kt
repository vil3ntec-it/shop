package ir.vil3ntec.tohid.ui.screens

// i18n: سه‌زبانه — نوشته‌ها از `StrPayStatus` (همان برگهٔ گوشی)

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrPayStatus
import ir.vil3ntec.tohid.core.pay.PayStatus
import ir.vil3ntec.tohid.data.PayStatusFlow
import ir.vil3ntec.tohid.data.Sale
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.sync.v1.Ulid
import ir.vil3ntec.tohid.todayIso
import ir.vil3ntec.tohid.ui.theme.Shop
import ir.vil3ntec.tohid.ui.v3.sales.history.PayStatusText
import kotlinx.coroutines.launch

/*
 *  PAY-07 — وضعیتِ پرداخت در فاکتورهای کامپیوتر (`DeskInvoices`): ستونِ «وضعیت» ِ جدول و بخشِ «وضعیتِ پرداخت»
 *  کنارِ اقلامِ فاکتور. همان منطقِ برگهٔ گوشی (`PayStatusFlow.desk`/`record`، `core/pay/PayStatus`): ثبتِ دستی
 *  «تأییدشده» است؛ فقط صاحب و مدیر آن را به «نیازمند بررسی»/«ناموفق»/«لغو» می‌برند و ردیفِ منتظر را صندوقدار با
 *  «پول رسید» تأیید می‌کند. فاکتور دست نمی‌خورد (سندِ جدای `pay_status`)؛ X/Z همان `PayFlow.ledgerEvents`.
 *  فاکتورِ بی ردیفِ غیرنقد ⇒ هیچ (همان صفحهٔ امروز).
 */

/** نوشته و رنگِ ستونِ «وضعیت»: لغو ⇒ «لغوشده»؛ وضعیتِ پرداختِ غیرِ «تأییدشده» ⇒ نامِ آن؛ وگرنه «ثبت‌شده» */
internal object DeskPayCell {
  fun text(cancelled: Boolean, chip: String?, recorded: String, cancelledText: String): String = when {
    cancelled -> cancelledText
    chip != null -> PayStatusText.name(chip)
    else -> recorded
  }

  @Composable
  fun color(cancelled: Boolean, chip: String?): Color = when {
    cancelled -> Shop.colors.danger
    chip == PayStatus.FAILED || chip == PayStatus.CANCELLED -> Shop.colors.danger
    chip != null -> Shop.colors.warning
    else -> Shop.colors.success
  }
}

/** کاربرِ همین صفحه: [sell] = نقش فروش می‌نویسد، [manage] = صاحب یا مدیر، [me] = نامِ ثبت‌کننده (از `DeskInvoices`) */
internal data class DeskPayUser(val sell: Boolean, val manage: Boolean, val me: String)

@Composable
internal fun DeskPayStatus(store: ShopStore, d: ShopData, sale: Sale, user: DeskPayUser, onMessage: (String) -> Unit) {
  val scope = rememberCoroutineScope()
  val sell = user.sell
  val manage = user.manage
  val me = user.me
  val rows = remember(d, sale.id, sell, manage) { PayStatusFlow.desk(d, sale, sell, manage) }
  if (rows.isEmpty()) return
  //  یک ثبت در هر زمان: ضربهٔ دوم ردیفِ دوم نمی‌سازد (کلید پیش از ثبت ساخته می‌شود)
  var busy by remember(sale.id) { mutableStateOf(false) }
  Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
    Text(StrPayStatus.section, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Shop.colors.text)
    rows.forEach { r ->
      Spacer(Modifier.height(4.dp))
      Row {
        Text(PayStatusText.line(r.view), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = Shop.colors.text)
        Text(PayStatusText.name(r.view.status), color = DeskPayCell.color(false, r.view.status.takeIf { it != PayStatus.CONFIRMED }),
          style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
      }
      if (!r.view.manual) Text(StrPayStatus.serverOnly, style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted)
      if (r.targets.isNotEmpty()) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          r.targets.forEach { to ->
            OutlinedButton(enabled = !busy, onClick = {
              busy = true
              val key = Ulid.next()
              val req = PayStatusFlow.Request(sale.id, r.view.index, to, me, sell, manage)
              scope.launch {
                val out = PayStatusFlow.record(store, key, req, todayIso(), ir.vil3ntec.tohid.data.ReportBridge.nowMs())
                onMessage(
                  when (out) {
                    is PayStatusFlow.Saved.Ok -> StrPayStatus.done
                    is PayStatusFlow.Saved.Bad -> PayStatusText.error(out.code)
                    PayStatusFlow.Saved.NotSaved -> StrPayStatus.errOther
                  },
                )
                busy = false
              }
            }) { Text(PayStatusText.action(to), style = MaterialTheme.typography.labelMedium) }
          }
        }
      }
    }
  }
}
