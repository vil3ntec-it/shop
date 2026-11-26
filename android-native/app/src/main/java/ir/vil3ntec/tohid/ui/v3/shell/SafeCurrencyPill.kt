package ir.vil3ntec.tohid.ui.v3.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrSafeCur
import ir.vil3ntec.tohid.ui.screens.v3Text
import ir.vil3ntec.tohid.ui.theme.HeroColors
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.PhoneV3Roles
import ir.vil3ntec.tohid.ui.theme.V3Tone
import ir.vil3ntec.tohid.ui.theme.V3ButtonKind
import ir.vil3ntec.tohid.ui.theme.V3Space
import ir.vil3ntec.tohid.ui.v3.V3Button
import ir.vil3ntec.tohid.ui.v3.V3Message
import ir.vil3ntec.tohid.ui.v3.V3TextField
import ir.vil3ntec.tohid.ui.v3.v3Pressable

/**
 *  ارزِ گاوصندوق در نسخهٔ ۳ (خواستهٔ صاحب‌کار ۱۴۰۵/۰۸/۲۴) — فقط کادر؛ قاعده در `core/safe/SafeCurrency` و
 *  `data/SafeCurrencies`، حالت و تنظیم در صفحه (`HeroMoneyRoute`، `rememberHeroInput`).
 *
 *  • [SafeCurrencyPill]: کادرِ کوچکِ «ارز» کنارِ مبلغِ برگهٔ «+» (همان بلندیِ کادرِ مبلغ) با فهرستِ ارزها و «+ ارزِ دیگر».
 *  • [HeroCurrencyChip]: تراشهٔ کوچکِ گوشهٔ نمودارِ گاوصندوق در کادرِ بالای خانه، با رنگ‌های همان کادر — فقط وقتی دفتر
 *    بیش از یک ارز دارد (دکانِ فقط‌افغانی همان کادرِ امروز را می‌بیند). بی نشانه و آیکن: فقط کدِ ارز.
 */
class SafeCurrencyChoice(
  /** ارزِ برگزیده */
  val code: String,
  /** گزینه‌ها، افغانی اول */
  val choices: List<String>,
  /** نامِ هر کد به زبانِ برنامه («دالر (USD)») */
  val name: (String) -> String,
  val onPick: (String) -> Unit,
  /** «+ ارزِ دیگر» (کد، نام) ⇒ پیامِ خطا یا null یعنی افزوده و برگزیده شد؛ null ⇒ بی این گزینه (کارمند) */
  val onAdd: ((String, String) -> String?)? = null,
)

/** کادرِ «ارز» کنارِ مبلغ؛ [onAddRequest] ⇐ «+ ارزِ دیگر» (فرمِ کوچکِ زیرِ مبلغ، [SafeCurrencyAddForm]) */
@Composable
fun SafeCurrencyPill(choice: SafeCurrencyChoice, enabled: Boolean, onAddRequest: () -> Unit, modifier: Modifier = Modifier) {
  val c = PhoneV3.colors
  var open by remember { mutableStateOf(false) }
  val shape = RoundedCornerShape(V3Space.controlRadius)
  Column(modifier) {
    Text(StrSafeCur.currency, style = v3Text(13, FontWeight.SemiBold, 18), color = c.inkMuted, maxLines = 1, modifier = Modifier.padding(start = 2.dp, bottom = 6.dp))
    Box {
      Row(
        Modifier
          .heightIn(min = V3Space.control)
          .clip(shape)
          .background(PhoneV3Roles.inputFill(c))
          .border(1.dp, PhoneV3Roles.inputBorder(c), shape)
          .v3Pressable(label = StrSafeCur.pick, role = Role.DropdownList, onClick = { if (enabled) open = true })
          .semantics { contentDescription = "${StrSafeCur.currency}: ${choice.name(choice.code)}" }
          .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(choice.code, style = v3Text(15, FontWeight.Bold, 20), color = c.ink, maxLines = 1)
        Spacer(Modifier.width(4.dp))
        Icon(Icons.Outlined.ExpandMore, contentDescription = null, tint = c.inkMuted, modifier = Modifier.size(18.dp))
      }
      CurrencyMenu(open, choice, onDismiss = { open = false }, onAddRequest = onAddRequest)
    }
  }
}

/** فهرستِ ارزها (همان برای کادر و تراشه): نامِ هر ارز، برگزیده پررنگ، و «+ ارزِ دیگر» اگر دکان می‌افزاید */
@Composable
private fun CurrencyMenu(open: Boolean, choice: SafeCurrencyChoice, onDismiss: () -> Unit, onAddRequest: (() -> Unit)?) {
  DropdownMenu(expanded = open, onDismissRequest = onDismiss) {
    choice.choices.forEach { code ->
      DropdownMenuItem(
        text = { Text(choice.name(code), fontWeight = if (code == choice.code) FontWeight.Bold else FontWeight.Normal, maxLines = 1) },
        onClick = { onDismiss(); choice.onPick(code) },
      )
    }
    if (choice.onAdd != null && onAddRequest != null) {
      DropdownMenuItem(text = { Text(StrSafeCur.addCurrency, maxLines = 1) }, onClick = { onDismiss(); onAddRequest() })
    }
  }
}

/** فرمِ «+ ارزِ دیگر» زیرِ مبلغ: کد و نام؛ خطای سنجش زیرِ آن، افزودنِ درست ارز را برمی‌گزیند و فرم را می‌بندد */
@Composable
fun SafeCurrencyAddForm(choice: SafeCurrencyChoice, onDone: () -> Unit) {
  val add = choice.onAdd ?: return
  var code by remember { mutableStateOf("") }
  var name by remember { mutableStateOf("") }
  var error by remember { mutableStateOf<String?>(null) }
  Column(Modifier.fillMaxWidth()) {
    Text(StrSafeCur.addTitle, style = v3Text(15, FontWeight.Bold, 22), color = PhoneV3.colors.ink)
    Spacer(Modifier.height(4.dp))
    Text(StrSafeCur.addNote, style = v3Text(12, FontWeight.Medium, 17), color = PhoneV3.colors.inkMuted)
    Spacer(Modifier.height(8.dp))
    V3TextField(code, { code = it.take(3); error = null }, StrSafeCur.code)
    Spacer(Modifier.height(8.dp))
    V3TextField(name, { name = it.take(ir.vil3ntec.tohid.data.SafeCurrencies.MAX_NAME); error = null }, StrSafeCur.name)
    error?.let {
      Spacer(Modifier.height(8.dp))
      V3Message(V3Tone.ERROR, it)
    }
    Spacer(Modifier.height(8.dp))
    Row {
      V3Button(StrSafeCur.add, { error = add(code, name); if (error == null) onDone() }, Modifier.weight(1f), kind = V3ButtonKind.SECONDARY)
    }
  }
}

/**
 *  تراشهٔ ارزِ سربرگِ گاوصندوق (کادرِ بالای خانه): کدِ ارزِ نمودار روی شیارِ همان کادر؛ زدن ⇒ فهرستِ ارزهای دفتر.
 *  ارزِ تازه این‌جا افزوده نمی‌شود (فقط دیدن) — افزودن در برگهٔ «+».
 */
@Composable
internal fun HeroCurrencyChip(choice: SafeCurrencyChoice, hc: HeroColors, modifier: Modifier = Modifier) {
  var open by remember { mutableStateOf(false) }
  Box(modifier) {
    Row(
      Modifier
        .clip(CircleShape)
        .background(hc.track)
        .border(1.dp, hc.edge, CircleShape)
        .v3Pressable(label = StrSafeCur.pick, role = Role.DropdownList, onClick = { open = true })
        .semantics { contentDescription = StrSafeCur.chartCurrency(choice.name(choice.code)) }
        .heightIn(min = 44.dp)
        .padding(horizontal = 11.dp, vertical = 3.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(choice.code, color = hc.ink, style = v3Text(12, FontWeight.Bold, 16), maxLines = 1)
    }
    CurrencyMenu(open, choice, onDismiss = { open = false }, onAddRequest = null)
  }
}
