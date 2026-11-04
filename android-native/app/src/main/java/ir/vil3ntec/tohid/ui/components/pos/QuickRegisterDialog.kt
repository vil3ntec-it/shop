package ir.vil3ntec.tohid.ui.components.pos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.StrGrid
import ir.vil3ntec.tohid.core.i18n.StrQuickRegister
import ir.vil3ntec.tohid.core.i18n.StrSale
import ir.vil3ntec.tohid.core.i18n.StrSalePages
import ir.vil3ntec.tohid.data.QuickRegister
import ir.vil3ntec.tohid.ui.screens.isPrimaryModifier
import ir.vil3ntec.tohid.ui.theme.Radius
import ir.vil3ntec.tohid.ui.theme.Shop

/** پیامِ بارکدِ ناشناس در صندوق (S03) */
val QUICK_UNKNOWN: String get() = StrSalePages.quickUnknown
val QUICK_ACTION: String get() = StrQuickRegister.action
val QUICK_FREE_ACTION: String get() = StrSale.freeItemAlt

/**
 *  «ثبتِ سریعِ کالا» — S03 (POS-17)، گوشی و کامپیوتر، پشتِ `TillGoodsPref.quickRegister` (پیش‌فرض خاموش).
 *  همان بارکد، نام، قیمتِ فروش، واحد و موجودیِ همان که در دست است؛ Ctrl+S (یا Enter در آخرین کادر) ثبت می‌کند.
 *  [onSave] پیامِ رد را برمی‌گرداند یا `null` (ثبت شد و صدازننده پنجره را می‌بندد). منطق در `QuickRegister`.
 */
@Composable
fun QuickRegisterDialog(barcode: String, onDismiss: () -> Unit, onSave: (QuickRegister.Draft) -> String?) {
  var draft by remember { mutableStateOf(QuickRegister.Draft()) }
  var error by remember { mutableStateOf<String?>(null) }
  val first = remember { FocusRequester() }
  fun save() { error = onSave(draft) }
  LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
  Dialog(onDismissRequest = onDismiss) {
    Surface(color = Shop.colors.surfaceSolid, shape = RoundedCornerShape(Radius.lg), modifier = Modifier.fillMaxWidth()) {
      Column(
        Modifier.padding(18.dp).onPreviewKeyEvent { e ->
          if (e.type == KeyEventType.KeyDown && e.isPrimaryModifier && e.key == Key.S) { save(); true } else false
        },
      ) {
        Text(StrQuickRegister.title, style = MaterialTheme.typography.titleMedium, color = Shop.colors.text)
        Text(StrSalePages.barcodeLine(barcode), style = MaterialTheme.typography.labelMedium, color = Shop.colors.muted)
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
          value = draft.name, onValueChange = { draft = draft.copy(name = it) }, label = { Text(StrQuickRegister.name) },
          singleLine = true, modifier = Modifier.fillMaxWidth().focusRequester(first),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedTextField(
            value = draft.salePrice, onValueChange = { draft = draft.copy(salePrice = ir.vil3ntec.tohid.numericInput(it)) },
            label = { Text(StrQuickRegister.price) }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f),
          )
          OutlinedTextField(
            value = draft.unit, onValueChange = { draft = draft.copy(unit = it) }, label = { Text(StrGrid.unit) },
            singleLine = true, modifier = Modifier.weight(0.7f),
          )
        }
        OutlinedTextField(
          value = draft.stock, onValueChange = { draft = draft.copy(stock = ir.vil3ntec.tohid.numericInput(it)) },
          label = { Text(StrQuickRegister.stock) }, singleLine = true,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
          keyboardActions = KeyboardActions(onDone = { save() }), modifier = Modifier.fillMaxWidth(),
        )
        error?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = Shop.colors.danger) }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
          TextButton(onClick = onDismiss) { Text(Str.cancel) }
          Button(onClick = { save() }) { Text(StrSalePages.saveAndAdd) }
        }
      }
    }
  }
}
