package ir.vil3ntec.tohid.ui.components.pos

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrLoyalty
import ir.vil3ntec.tohid.core.i18n.StrShell
import ir.vil3ntec.tohid.data.LoyaltyPref
import ir.vil3ntec.tohid.data.LoyaltyShop
import ir.vil3ntec.tohid.data.ShopRole
import ir.vil3ntec.tohid.normalizeDigits
import ir.vil3ntec.tohid.ui.screens.SegmentedChoice
import ir.vil3ntec.tohid.ui.theme.Shop
import kotlinx.coroutines.launch

/**
 *  PRM-13 — بخشِ دومِ تنظیمِ امتیاز (درونِ `LoyaltySettingsWired`):
 *   • انقضای امتیازِ همین دستگاه (ماه، ۰ = هرگز؛ `LoyaltyPref.setExpiry`) — فقط صاحب/مدیر.
 *   • قاعدهٔ یکسانِ دکان (فقط وقتی سرور پرچمِ `shop.loyalty_rule` را روشن گفته): حالِ همین لحظه، و برای صاحب/مدیر
 *     «ذخیرهٔ قاعدهٔ همین دستگاه برای همهٔ دستگاه‌ها» با روشن/خاموشِ دکان (`LoyaltyShop.setRule`؛ نقش را سرور می‌سنجد).
 *  پرچمِ خاموش ⇒ فقط کادرِ انقضا؛ صندوق و دفتر همان امروز (انقضای ۰).
 */
@Composable
fun LoyaltyShopSettings(context: Context) {
  val scope = rememberCoroutineScope()
  val may = remember { !ShopRole.isStaff(context) }
  var shop by remember { mutableStateOf(LoyaltyShop.cached(context)) }
  var offline by remember { mutableStateOf(false) }
  LaunchedEffect(Unit) {
    offline = !LoyaltyShop.refresh(context)
    shop = LoyaltyShop.cached(context)
  }
  var months by remember { mutableStateOf(LoyaltyPref.rule(context).expiryMonths.toString()) }
  var shopOn by remember(shop) { mutableStateOf(shop?.on ?: LoyaltyPref.on(context)) }
  var note by remember { mutableStateOf<String?>(null) }
  var busy by remember { mutableStateOf(false) }

  Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
    Text(StrLoyalty.expiryNote, style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted2)
    OutlinedTextField(
      value = months, onValueChange = { months = it.filter { c -> c.isDigit() }.take(3) }, label = { Text(StrLoyalty.expiryTitle) },
      singleLine = true, enabled = may, modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    )
    if (may) OutlinedButton(onClick = {
      note = if (LoyaltyPref.setExpiry(context, normalizeDigits(months).toIntOrNull() ?: -1)) StrLoyalty.saved else StrLoyalty.badExpiry
    }, modifier = Modifier.padding(top = 8.dp)) { Text(StrLoyalty.expirySave) }

    val s = shop
    if (s != null) {
      Spacer(Modifier.height(12.dp))
      Text(StrLoyalty.shopTitle, style = MaterialTheme.typography.titleSmall, color = Shop.colors.text)
      val rule = s.rule
      if (rule != null) {
        Text(
          (if (s.on) StrShell.switchOn else StrShell.switchOff) + " · " + StrLoyalty.shopVersion(s.version) + " · " +
            StrLoyalty.ruleSummary(rule.everyAfn, rule.pointAfn, rule.maxSharePct, rule.expiryMonths),
          style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted,
        )
        Text(StrLoyalty.shopInForce, style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted2)
      } else {
        Text(StrLoyalty.shopNotSaved, style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted2)
      }
      if (offline) Text(StrLoyalty.shopOffline, style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted2)
      if (may && s.manage) {
        SegmentedChoice(
          options = listOf("off" to StrShell.switchOff, "on" to StrShell.switchOn),
          selected = if (shopOn) "on" else "off",
          onSelect = { shopOn = it == "on" },
          modifier = Modifier.padding(top = 6.dp),
        )
        OutlinedButton(enabled = !busy, onClick = {
          busy = true
          scope.launch {
            note = LoyaltyShop.setRule(context, shopOn, LoyaltyPref.rule(context)) ?: StrLoyalty.shopSaved
            shop = LoyaltyShop.cached(context)
            busy = false
          }
        }, modifier = Modifier.padding(top = 8.dp)) { Text(StrLoyalty.shopPublish) }
      } else if (!may) {
        Text(StrLoyalty.ownerOnly, style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted2)
      }
    }
    note?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted) }
  }
}
