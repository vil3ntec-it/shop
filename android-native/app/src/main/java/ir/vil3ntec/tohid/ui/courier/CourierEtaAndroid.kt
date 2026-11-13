package ir.vil3ntec.tohid.ui.courier

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.courier.CourierEta
import ir.vil3ntec.tohid.core.courier.MissionCard
import ir.vil3ntec.tohid.core.i18n.StrCourier
import ir.vil3ntec.tohid.data.CourierStore
import ir.vil3ntec.tohid.data.repo.Backend
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.v3.V3Type
import kotlinx.coroutines.launch

/**
 *  DLV-06 — «تقریباً کی می‌رسم» روی کارتِ مأموریتِ «در راه» (`CourierEta`): فقط با `etaEnabled` ِ `/courier/me`
 *  (پرچمِ `shop.delivery_customer_view`) و فقط آنلاین؛ هر گزینه یک `POST /courier/missions/:id/eta`. پاسخِ سرور
 *  (یا خطایش) زیرِ همان گزینه‌ها گفته می‌شود — هیچ «فرستاده شد» ِ پیش از پاسخ.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CourierEtaCard(c: MissionCard) {
  val context = LocalContext.current
  val enabled = remember(context) { CourierStore(context).etaEnabled }
  if (!CourierEta.visible(enabled, c.status)) return
  val scope = rememberCoroutineScope()
  var busy by remember(c.id) { mutableStateOf(false) }
  var said by remember(c.id) { mutableStateOf<String?>(null) }
  Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Text(StrCourier.etaTitle, style = V3Type.headline, color = PhoneV3.colors.ink)
    Text(said ?: StrCourier.etaHelp, style = V3Type.footnote, color = PhoneV3.colors.inkMuted)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
      CourierEta.CHOICES.forEach { m ->
        CourierSecondary(StrCourier.etaMinutes(m), {
          if (!busy) {
            busy = true
            scope.launch {
              Backend.courier(context).eta(c.id, m)
                .onSuccess { said = StrCourier.etaSent(m) }
                .onFailure { said = it.userMessage }
              busy = false
            }
          }
        }, height = 40.dp)
      }
    }
  }
}
