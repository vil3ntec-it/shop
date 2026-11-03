package ir.vil3ntec.tohid.ui.courier

import android.content.Context
import android.content.Intent
import android.net.Uri
import ir.vil3ntec.tohid.core.courier.MissionCard
import ir.vil3ntec.tohid.core.i18n.StrCourierApp
import ir.vil3ntec.tohid.ui.components.common.openOnMap

/**
 *  مرزِ پلتفرمِ «مسیریابی» ِ کارتِ مأموریت (DLV-05). پیوندهای سرور (`MissionCard.navigation`، فقط مختصات،
 *  پرچمِ `shop.delivery_map`) به ترتیب: مسیریابِ خودِ گوشی (`geo:`، هر برنامهٔ نقشه، آفلاین هم) ⇐ OsmAnd ⇐
 *  Google Maps — نخستین که باز شد. نبودشان (پرچمِ خاموش) ⇒ همان رفتارِ پیشین (`openOnMap`).
 *  قالبِ پیوندها را `MissionNav.of` در هسته سنجیده است؛ این‌جا فقط باز کردن.
 */
internal fun openMissionRoute(context: Context, c: MissionCard) {
  val opened = c.navigation?.links.orEmpty().any { link ->
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link))) }.isSuccess
  }
  val lat = c.lat
  val lng = c.lng
  if (!opened && lat != null && lng != null) openOnMap(context, lat, lng, c.landmark.ifBlank { StrCourierApp.destination })
}
