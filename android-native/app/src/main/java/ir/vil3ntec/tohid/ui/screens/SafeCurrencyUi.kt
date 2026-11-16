package ir.vil3ntec.tohid.ui.screens

import ir.vil3ntec.tohid.ui.components.common.ShopAlertDialog
import ir.vil3ntec.tohid.ui.components.common.ShopTextButton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrSafe
import ir.vil3ntec.tohid.core.i18n.StrSafeCur
import ir.vil3ntec.tohid.core.safe.SafeCore
import ir.vil3ntec.tohid.data.SafeCurrencies
import ir.vil3ntec.tohid.ui.components.common.PillTone
import ir.vil3ntec.tohid.ui.components.common.StatusPill
import ir.vil3ntec.tohid.ui.theme.Shop
import ir.vil3ntec.tohid.ui.v3.shell.SafeCurrencyChoice

/*
 *  ارزهای گاوصندوق در کارتِ «گاوصندوق» ِ تاریخچهٔ فروش (`SafePanel`، گوشی و کامپیوتر) — فقط کادر؛ قاعده در
 *  `core/safe/SafeCurrency`، فهرست و نوشتن در `data/SafeCurrencies`، تنظیم در `SafeCurrencyPref` (صفحه).
 *  • [SafeCurrencyButton]: کادرِ کوچکِ «ارز» کنارِ مبلغِ پنجرهٔ ورود/خروج/شمارش/بانک، با «+ ارزِ دیگر» ([SafeAddCurrencyDialog]).
 *  • [SafeBalanceList]: ماندهٔ هر ارز در فهرستِ فشرده، افغانی اول — هر ارز جدا، هرگز جمع یا تبدیل.
 */

/** کادرِ «ارز»: کدِ ارزِ برگزیده؛ زدن ⇒ فهرستِ ارزها و «+ ارزِ دیگر» (فقط صاحب/مدیر) */
@Composable
internal fun SafeCurrencyButton(choice: SafeCurrencyChoice, enabled: Boolean = true) {
  var open by remember { mutableStateOf(false) }
  var adding by remember { mutableStateOf(false) }
  Box {
    OutlinedButton(
      onClick = { open = true }, enabled = enabled,
      modifier = Modifier.semantics { contentDescription = "${StrSafeCur.currency}: ${choice.name(choice.code)}" },
    ) {
      Text(choice.code, fontWeight = FontWeight.SemiBold)
      Spacer(Modifier.width(2.dp))
      Icon(Icons.Outlined.ExpandMore, contentDescription = null, modifier = Modifier.size(18.dp))
    }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
      choice.choices.forEach { code ->
        DropdownMenuItem(
          text = { Text(choice.name(code), fontWeight = if (code == choice.code) FontWeight.Bold else FontWeight.Normal, maxLines = 1) },
          onClick = { open = false; choice.onPick(code) },
        )
      }
      if (choice.onAdd != null) {
        DropdownMenuItem(text = { Text(StrSafeCur.addCurrency, maxLines = 1) }, onClick = { open = false; adding = true })
      }
    }
  }
  val add = choice.onAdd
  if (adding && add != null) SafeAddCurrencyDialog(add) { adding = false }
}

/** «+ ارزِ دیگر»: کد و نام؛ خطای سنجش در همان پنجره، افزودنِ درست ارز را برمی‌گزیند و پنجره را می‌بندد */
@Composable
internal fun SafeAddCurrencyDialog(onAdd: (String, String) -> String?, onDismiss: () -> Unit) {
  var code by remember { mutableStateOf("") }
  var name by remember { mutableStateOf("") }
  var error by remember { mutableStateOf<String?>(null) }
  ShopAlertDialog(
    onDismissRequest = onDismiss,
    containerColor = Shop.colors.surfaceSolid,
    title = { Text(StrSafeCur.addTitle, color = Shop.colors.text) },
    text = {
      Column {
        Text(StrSafeCur.addNote, style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
          value = code, onValueChange = { code = it.take(3); error = null }, label = { Text(StrSafeCur.code) },
          singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
          value = name, onValueChange = { name = it.take(SafeCurrencies.MAX_NAME); error = null }, label = { Text(StrSafeCur.name) },
          singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
        error?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = Shop.colors.danger, modifier = Modifier.padding(top = 6.dp)) }
      }
    },
    confirmButton = { ShopTextButton(onClick = { error = onAdd(code, name); if (error == null) onDismiss() }) { Text(StrSafeCur.add) } },
    dismissButton = { ShopTextButton(onClick = onDismiss) { Text(StrSafe.cancel) } },
  )
}

/** ماندهٔ هر ارز، افغانی اول ([views] به ترتیبِ `SafeCore.views`)؛ هر ردیف نشانِ «زیرِ صفر»/«نیازمندِ بررسی» ِ خودش */
@Composable
internal fun SafeBalanceList(views: Map<String, SafeCore.View>, name: (String) -> String) {
  Text(StrSafeCur.balances, style = MaterialTheme.typography.labelMedium, color = Shop.colors.muted)
  views.entries.forEachIndexed { i, (code, v) ->
    if (i > 0) HorizontalDivider(Modifier.padding(vertical = 2.dp), color = Shop.colors.border)
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
      Text(name(code), style = MaterialTheme.typography.bodySmall, color = Shop.colors.muted, maxLines = 1, modifier = Modifier.weight(1f))
      if (v.negative) StatusPill(StrSafe.negative, PillTone.DANGER)
      if (v.needsReview.isNotEmpty()) StatusPill(StrSafe.needsReview(v.needsReview.size), PillTone.WARNING)
      Text(SafeCurrencies.format(v.balanceMinor, code), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = Shop.colors.text, maxLines = 1)
    }
  }
}
