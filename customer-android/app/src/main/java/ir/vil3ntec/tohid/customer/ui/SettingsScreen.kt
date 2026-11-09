package ir.vil3ntec.tohid.customer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.shopper.ShopperLang
import ir.vil3ntec.tohid.core.shopper.ShopperText

/** تنظیمات: فقط زبان و ظاهر؛ نشانیِ سرور برای آزمون پایینِ صفحه (ساختِ debug ِ مرحلهٔ ۱). */
@Composable
fun SettingsScreen(model: CustomerModel) {
  val platform = LocalPlatform.current
  var server by remember { mutableStateOf(platform.server()) }
  Column(Modifier.fillMaxSize()) {
    TopBar(ShopperText.settings, onBack = { model.back() })
    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      SectionTitle(ShopperText.language)
      ShopperLang.entries.forEach { l -> Option(l.native, model.lang == l) { model.setLanguage(l) } }
      SectionTitle(ShopperText.theme)
      listOf("system" to ShopperText.themeSystem, "light" to ShopperText.themeLight, "dark" to ShopperText.themeDark).forEach { (k, label) ->
        Option(label, model.saved.theme == k) { model.setTheme(k) }
      }
      AccountSettings(model)
      SectionTitle(ShopperText.server)
      OutlinedTextField(
        value = server, onValueChange = { server = it.trim() }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
      )
      OutlinedButton(onClick = { platform.setServer(server); model.serverChanged() }, modifier = Modifier.fillMaxWidth()) { Text(ShopperText.save) }
      Text(platform.version, style = MaterialTheme.typography.bodySmall)
    }
  }
}

@Composable
private fun Option(text: String, selected: Boolean, onClick: () -> Unit) {
  Row(
    Modifier.fillMaxWidth().selectable(selected = selected, role = Role.RadioButton, onClick = onClick).padding(vertical = 4.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    RadioButton(selected = selected, onClick = null)
    Text(text, Modifier.padding(start = 8.dp), style = MaterialTheme.typography.bodyLarge)
  }
}
