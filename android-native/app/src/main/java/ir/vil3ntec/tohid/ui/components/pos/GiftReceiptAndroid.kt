package ir.vil3ntec.tohid.ui.components.pos

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrGift
import ir.vil3ntec.tohid.core.i18n.StrShell
import ir.vil3ntec.tohid.data.Sale
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.ui.screens.SegmentedChoice
import ir.vil3ntec.tohid.ui.screens.SettingsSection
import ir.vil3ntec.tohid.ui.theme.Shop
import kotlinx.coroutines.launch

/**
 *  POS-34 — روشن/خاموشِ «رسیدِ هدیه» روی همین دستگاه (`tohid-ui:gift_receipt`)؛ فایلِ مرزیِ پلتفرم
 *  (`SharedPreferences`). پیش‌فرض **خاموش**: فاکتور همان پیشین است و هیچ دکمه‌ای نمی‌آید.
 */
object GiftReceiptPrefs {
  val on = mutableStateOf(false)
  private var loaded = false
  private const val KEY = "gift_receipt"

  fun load(context: android.content.Context) {
    if (loaded) return
    loaded = true
    on.value = context.getSharedPreferences("tohid-ui", 0).getBoolean(KEY, false)
  }

  fun set(context: android.content.Context, value: Boolean) {
    on.value = value
    context.getSharedPreferences("tohid-ui", 0).edit().putBoolean(KEY, value).apply()
  }
}

/**
 *  دکمهٔ «رسیدِ هدیه» زیرِ فاکتور: انتخابِ چاپگر (همان `PrinterDialog`) ⇐ چاپِ چیدمانِ بی قیمتِ هسته
 *  (`core/receipt/GiftReceiptLayout`، `PrintJob.printGift`). تنظیمِ خاموش یا فاکتورِ لغوشده ⇒ هیچ.
 *  چاپِ دوباره و صفِ چاپِ فاکتور دست نمی‌خورند: این کاغذِ جداست، نه نسخهٔ دیگرِ فاکتور.
 */
@Composable
fun GiftReceiptButton(d: ShopData, sale: Sale, onMessage: (String) -> Unit) {
  val context = LocalContext.current
  GiftReceiptPrefs.load(context)
  if (!GiftReceiptPrefs.on.value || sale.status == "cancelled") return
  val scope = rememberCoroutineScope()
  var pick by remember { mutableStateOf(false) }
  Spacer(Modifier.height(8.dp))
  OutlinedButton(onClick = { pick = true }, modifier = Modifier.fillMaxWidth()) {
    Icon(Icons.Filled.CardGiftcard, contentDescription = null, tint = Shop.colors.primary)
    Spacer(Modifier.width(6.dp))
    Text(StrGift.button, style = MaterialTheme.typography.labelLarge)
  }
  if (pick) {
    ir.vil3ntec.tohid.ui.screens.PrinterDialog(
      onDismiss = { pick = false },
      onPrint = { address, width ->
        pick = false
        onMessage(StrGift.sending)
        scope.launch { onMessage(ir.vil3ntec.tohid.print.PrintJob.printGift(context, d, sale, address, width) ?: StrGift.printed) }
      },
    )
  }
}

/** بخشِ تنظیمات با خواندن و نوشتنِ همین تنظیم — تا `Settings` یک خط بخواهد */
@Composable
fun GiftReceiptSettingsWired(context: android.content.Context) {
  GiftReceiptPrefs.load(context)
  val on = GiftReceiptPrefs.on.value
  SettingsSection(
    icon = Icons.Filled.CardGiftcard,
    title = StrGift.section,
    subtitle = if (on) StrShell.switchOn else StrShell.switchOff,
    tint = if (on) Shop.colors.success else Shop.colors.muted,
  ) {
    Text(StrGift.note, style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted2)
    Spacer(Modifier.height(8.dp))
    SegmentedChoice(
      options = listOf("off" to StrShell.switchOff, "on" to StrShell.switchOn),
      selected = if (on) "on" else "off",
      onSelect = { GiftReceiptPrefs.set(context, it == "on") },
    )
  }
}
