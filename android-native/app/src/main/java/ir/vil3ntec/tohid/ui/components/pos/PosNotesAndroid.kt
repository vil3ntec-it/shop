package ir.vil3ntec.tohid.ui.components.pos

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.StrSalePages
import ir.vil3ntec.tohid.core.pos.SaleNotes
import ir.vil3ntec.tohid.ui.theme.Shape

/**
 *  POS-11 — یادداشتِ ردیف و یادداشتِ فاکتور در صندوق، با قالب‌های تکراری؛ فایلِ مرزیِ پلتفرم.
 *
 *  تنظیمِ **همین دستگاه** (`tohid-ui:pos_notes`)، پیش‌فرض **روشن** (تنظیماتِ کم، ۱۴۰۵/۰۸/۱۱)؛ **خاموش**: خاموش ⇒ کادرِ ردیف و صندوق
 *  همان امروز، هیچ دکمه و کادرِ تازه‌ای نیست و فاکتور بی یادداشت (فیلد نوشته نمی‌شود). قالب‌ها
 *  (`tohid-ui:pos_note_templates`، هر خط یکی) هم مالِ همین دستگاه‌اند و همگام نمی‌شوند. متن فقط از
 *  `SaleNotes` (یک خط، حداکثر ۱۲۰ نویسه)؛ چاپ روی رسیدِ پیشین و نسخهٔ ۲.
 */
object PosNotesPrefs {
  val on = mutableStateOf(false)
  val templates = mutableStateOf(emptyList<String>())
  private var loaded = false
  private const val KEY = "pos_notes"
  private const val TEMPLATES = "pos_note_templates"

  fun load(context: android.content.Context) {
    if (loaded) return
    loaded = true
    runCatching {
      val p = context.getSharedPreferences("tohid-ui", 0)
      on.value = p.getBoolean(KEY, true)
      templates.value = SaleNotes.templates(p.getString(TEMPLATES, null))
    }
  }

  fun set(context: android.content.Context, value: Boolean) {
    on.value = value
    runCatching { context.getSharedPreferences("tohid-ui", 0).edit().putBoolean(KEY, value).apply() }
  }

  fun setTemplates(context: android.content.Context, list: List<String>) {
    templates.value = SaleNotes.templates(SaleNotes.encodeTemplates(list))
    runCatching {
      context.getSharedPreferences("tohid-ui", 0).edit().putString(TEMPLATES, SaleNotes.encodeTemplates(templates.value)).apply()
    }
  }
}

/** یادداشتِ یک ردیف برای `CartLineDialog`: [save] پس از «تأیید» با متنِ `SaleNotes.clean` شده */
class PosLineNote(val value: String?, val save: (String?) -> Unit)

/** `null` ⇒ تنظیم خاموش؛ کادرِ ردیف همان امروز */
@Composable
fun posLineNote(value: String?, save: (String?) -> Unit): PosLineNote? {
  PosNotesPrefs.load(LocalContext.current)
  return if (PosNotesPrefs.on.value) PosLineNote(value, save) else null
}

/** کادرِ یادداشت با قالب‌های همین دستگاه (لمسِ قالب ⇒ افزوده به متن، `SaleNotes.insert`) */
@Composable
fun PosNoteField(text: String, onText: (String) -> Unit, label: String) {
  val keys = rememberTouchTextKeys() // POS-31: کامپیوترِ لمسی ⇒ صفحه‌کلیدِ حروف با فوکوس
  OutlinedTextField(
    value = text,
    onValueChange = { onText(it.take(SaleNotes.MAX)) },
    singleLine = true,
    label = { Text(label) },
    modifier = Modifier.fillMaxWidth().touchKeysOnFocus(keys),
  )
  TouchTextKeysPanel(keys, text, onText, SaleNotes.MAX)
  val templates = PosNotesPrefs.templates.value
  if (templates.isNotEmpty()) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
      templates.forEach { t ->
        FilterChip(selected = false, onClick = { onText(SaleNotes.insert(text, t).orEmpty()) }, label = { Text(t) }, shape = Shape.chip)
      }
    }
  }
}

/**
 *  «یادداشتِ فاکتور» کنارِ سبد — فقط با تنظیمِ روشن و سبدِ ناخالی ([show]). [onNote] متنِ `SaleNotes.clean` شده (`null` = پاک)
 *  را به `CartStore.setNote` می‌دهد؛ `false` ⇒ وسطِ جابه‌جاییِ فاکتور بود و کادر باز می‌ماند.
 */
@Composable
fun InvoiceNoteButton(note: String?, show: Boolean, onNote: (String?) -> Boolean) {
  PosNotesPrefs.load(LocalContext.current)
  if (!PosNotesPrefs.on.value || !show) return
  var open by remember { mutableStateOf(false) }
  TextButton(onClick = { open = true }) {
    androidx.compose.material3.Icon(Icons.Filled.Edit, contentDescription = null)
    Text(
      note?.let { StrSalePages.noteLine(it) } ?: StrSalePages.invoiceNote,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
  }
  if (!open) return
  var text by remember { mutableStateOf(note.orEmpty()) }
  AlertDialog(
    onDismissRequest = { open = false },
    shape = Shape.dialog,
    title = { Text(StrSalePages.invoiceNote) },
    text = { Column { PosNoteField(text, { text = it }, StrSalePages.printedOnReceipt) } },
    confirmButton = { TextButton(onClick = { if (onNote(SaleNotes.clean(text))) open = false }) { Text(StrSalePages.record) } },
    dismissButton = { TextButton(onClick = { open = false }) { Text(Str.cancel) } },
  )
}
