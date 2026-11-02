package ir.vil3ntec.tohid.ui.v3.settings.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrShell
import ir.vil3ntec.tohid.core.pos.SaleNotes
import ir.vil3ntec.tohid.ui.components.pos.PosNotesPrefs
import ir.vil3ntec.tohid.ui.screens.SettingsLayout
import ir.vil3ntec.tohid.ui.v3.V3Divider
import ir.vil3ntec.tohid.ui.v3.V3ListRow
import ir.vil3ntec.tohid.ui.v3.V3Section
import ir.vil3ntec.tohid.ui.v3.V3Segmented
import ir.vil3ntec.tohid.ui.v3.V3TextButton
import ir.vil3ntec.tohid.ui.v3.V3TextField
import ir.vil3ntec.tohid.ui.v3.nav.RouteScope
import ir.vil3ntec.tohid.ui.v3.settings.SettingsBlock
import ir.vil3ntec.tohid.ui.v3.settings.SettingsNote
import ir.vil3ntec.tohid.ui.v3.settings.SettingsPageFrame

/**
 *  `settings/sales/notes`: یادداشتِ ردیف و فاکتور (POS-11) — همان `PosNotesSettingsWired` ِ رابطِ امروز: کلیدِ همین
 *  دستگاه (`PosNotesPrefs.set`)، و با روشن، قالب‌ها (`PosNotesPrefs.setTemplates`) با همان `SaleNotes.addTemplate`،
 *  `removeTemplate`، سقفِ `MAX_TEMPLATES` و پاک‌سازیِ `SaleNotes.clean`. متن‌ها همان، فارسی.
 */
@Composable
internal fun RouteScope.PosNotesScreen(onBack: (() -> Unit)?) {
  val context = LocalContext.current
  remember { PosNotesPrefs.load(context) }
  val on = PosNotesPrefs.on.value
  SettingsPageFrame(
    title = SettingsLayout.labelOf(SettingsLayout.POS_NOTES),
    subtitle = if (on) StrShell.switchOn else StrShell.switchOff,
    onBack = onBack,
  ) {
    item(key = "choice") {
      SettingsBlock { V3Segmented(SWITCH, if (on) 1 else 0, { PosNotesPrefs.set(context, it == 1) }) }
      SettingsNote(
        "یادداشتِ هر ردیف (در کادرِ ویرایشِ ردیف) و یادداشتِ کلِ فاکتور، روی رسید چاپ می‌شوند. قالب‌ها با یک لمس افزوده می‌شوند. انتخابِ همین دستگاه است.",
      )
    }
    if (on) {
      item(key = "templates") {
        val list = PosNotesPrefs.templates.value
        if (list.isNotEmpty()) {
          V3Section(null) {
            list.forEachIndexed { i, t ->
              if (i > 0) V3Divider(start = 16.dp)
              V3ListRow(
                t,
                chevron = false,
                trailing = { V3TextButton("حذف", { PosNotesPrefs.setTemplates(context, SaleNotes.removeTemplate(list, t)) }, danger = true) },
              )
            }
          }
        }
        if (list.size < SaleNotes.MAX_TEMPLATES) {
          var draft by remember { mutableStateOf("") }
          SettingsBlock {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
              V3TextField(draft, { draft = it.take(SaleNotes.MAX) }, "قالبِ تازه", Modifier.weight(1f))
              if (SaleNotes.clean(draft) != null) {
                V3TextButton("افزودن", { PosNotesPrefs.setTemplates(context, SaleNotes.addTemplate(list, draft)); draft = "" }, Modifier.padding(bottom = 6.dp))
              }
            }
          }
        }
      }
    }
  }
}
