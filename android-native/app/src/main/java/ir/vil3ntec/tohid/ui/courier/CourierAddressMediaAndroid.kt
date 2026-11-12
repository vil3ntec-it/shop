package ir.vil3ntec.tohid.ui.courier

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import ir.vil3ntec.tohid.core.orders.OrderAddressMedia
import ir.vil3ntec.tohid.data.repo.Backend
import ir.vil3ntec.tohid.ui.v3.online.orders.AddressMediaSection

/**
 *  CRM-09/CHK-08 — برنامهٔ پیک: «عکسِ دروازه» و «یادداشتِ صوتی» ِ مشتری برای نشانیِ همین مأموریت، فقط تا مأموریت زنده است
 *  (سرور خودش می‌سنجد: فقط پیکِ همین مأموریت، پس از «قبول» و پیش از «تحویل شد»). پرچمِ خاموش یا هیچ ⇒ هیچ.
 */
@Composable
internal fun CourierAddressMediaV3(assignmentId: String) {
  val summary = OrderAddressMedia.missionPath(assignmentId) ?: return
  val context = LocalContext.current
  val api = remember(context) { Backend.api(context) }
  CourierOnDark { AddressMediaSection("mission-$assignmentId", summary, { OrderAddressMedia.missionBytesPath(assignmentId, it) }, api) }
}
