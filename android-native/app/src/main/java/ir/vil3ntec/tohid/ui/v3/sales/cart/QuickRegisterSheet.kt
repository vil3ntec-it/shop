package ir.vil3ntec.tohid.ui.v3.sales.cart

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrQuickRegister
import ir.vil3ntec.tohid.data.QuickRegister
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.PhoneV3Roles
import ir.vil3ntec.tohid.ui.v3.V3Button
import ir.vil3ntec.tohid.ui.v3.V3FieldKind
import ir.vil3ntec.tohid.ui.v3.V3Sheet
import ir.vil3ntec.tohid.ui.v3.V3TextField
import ir.vil3ntec.tohid.ui.v3.V3Type
import ir.vil3ntec.tohid.ui.v3.v3Shake
import kotlinx.coroutines.launch

/**
 *  برگهٔ «ثبتِ سریعِ کالا» — S03 (POS-17) در صندوقِ گوشیِ نسخهٔ ۳؛ همان کادرهای پنجرهٔ صندوقِ کامپیوتر
 *  (`QuickRegisterDialog`): بارکد، نام، قیمتِ فروش، واحد و موجودیِ همان که در دست است. [onSave] پیامِ رد را
 *  برمی‌گرداند یا `null` (ثبت شد ⇒ برگه با حرکت بسته می‌شود)؛ رد ⇒ پیام زیرِ کادرها و لرزشِ کوتاه. تا ثبت تمام
 *  نشده برگه بسته نمی‌شود.
 */
@Composable
fun QuickRegisterSheet(barcode: String, onSave: suspend (QuickRegister.Draft) -> String?, onClose: () -> Unit) {
  val c = PhoneV3.colors
  val scope = rememberCoroutineScope()
  var draft by remember { mutableStateOf(QuickRegister.Draft()) }
  var error by remember { mutableStateOf<String?>(null) }
  var saving by remember { mutableStateOf(false) }
  var shake by remember { mutableIntStateOf(0) }

  V3Sheet(onDismiss = onClose, title = StrQuickRegister.title, dismissible = !saving, detents = false) { closer ->
    fun save() {
      if (saving) return
      saving = true
      scope.launch {
        val refused = onSave(draft)
        saving = false
        if (refused == null) closer.close() else { error = refused; shake++ }
      }
    }
    Text(StrQuickRegister.barcode(barcode), style = V3Type.subhead, color = c.inkMuted)
    Spacer(Modifier.height(14.dp))
    Column(Modifier.v3Shake(shake.takeIf { it > 0 })) {
      V3TextField(draft.name, { draft = draft.copy(name = it) }, StrQuickRegister.name, Modifier.fillMaxWidth(), required = true)
      Spacer(Modifier.height(12.dp))
      Row {
        V3TextField(
          draft.salePrice, { draft = draft.copy(salePrice = it) }, StrQuickRegister.price, Modifier.weight(1.4f),
          kind = V3FieldKind.MONEY, required = true,
        )
        Spacer(Modifier.width(10.dp))
        V3TextField(draft.unit, { draft = draft.copy(unit = it) }, StrQuickRegister.unit, Modifier.weight(1f), required = true)
      }
      Spacer(Modifier.height(12.dp))
      V3TextField(
        draft.stock, { draft = draft.copy(stock = it) }, StrQuickRegister.stock, Modifier.fillMaxWidth(),
        kind = V3FieldKind.NUMBER, imeAction = ImeAction.Done, onImeAction = ::save,
      )
    }
    error?.let {
      Spacer(Modifier.height(10.dp))
      Text(it, style = V3Type.footnote, color = PhoneV3Roles.errorText(c))
    }
    Spacer(Modifier.height(18.dp))
    V3Button(StrQuickRegister.save, ::save, Modifier.fillMaxWidth(), loading = saving, haptic = true)
  }
}
