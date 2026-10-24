package ir.vil3ntec.tohid.ui.v3.online.storefront

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Sensors
import androidx.compose.material.icons.outlined.ShoppingCartCheckout
import androidx.compose.material.icons.outlined.WifiTethering
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrOnline
import ir.vil3ntec.tohid.data.repo.StorefrontSettingsDto
import ir.vil3ntec.tohid.ui.theme.V3Tone
import ir.vil3ntec.tohid.ui.v3.V3Button
import ir.vil3ntec.tohid.ui.v3.V3Collapse
import ir.vil3ntec.tohid.ui.v3.V3Divider
import ir.vil3ntec.tohid.ui.v3.V3HeroCard
import ir.vil3ntec.tohid.ui.v3.V3Hue
import ir.vil3ntec.tohid.ui.v3.V3ListRow
import ir.vil3ntec.tohid.ui.v3.V3Message
import ir.vil3ntec.tohid.ui.v3.V3Section
import ir.vil3ntec.tohid.ui.v3.V3SuccessCheck
import ir.vil3ntec.tohid.ui.v3.V3TextField
import ir.vil3ntec.tohid.ui.v3.v3Shake

/**
 *  برگهٔ «وضعیت»: کادرِ بزرگ (`V3HeroCard`) «مشتری الان این را می‌بیند» با وضعیتِ کوتاه، جملهٔ
 *  کاملِ ویترین و ساعتِ دکان؛ پذیرشِ سفارش و آخرین تأییدِ اتصال؛ صفِ اعلامِ نرسیده با «دوباره
 *  بفرست»؛ و پنج اعلام به شکلِ کارت‌های انتخاب با «ثبت». «ثبت شد» فقط با رسیدِ سرور.
 */
@Composable
fun StatusPane(
  settings: StorefrontSettingsDto,
  pending: Boolean,
  sending: Boolean,
  notice: String?,
  confirmed: Int,
  onDeclare: (mode: String, untilMinutes: Int?) -> Unit,
  onResend: () -> Unit,
  pad: PaddingValues,
) {
  val st = settings.status
  var mode by rememberSaveable(settings.updatedAt) { mutableStateOf(st?.mode?.takeIf { it in PresenceModel.MODES } ?: "auto") }
  var until by rememberSaveable { mutableStateOf("") }
  var untilError by rememberSaveable { mutableStateOf<String?>(null) }
  var shake by rememberSaveable { mutableIntStateOf(0) }
  Column(
    Modifier.fillMaxSize().verticalScroll(rememberScrollState())
      .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = pad.calculateBottomPadding()),
  ) {
    if (st == null) V3Message(V3Tone.INFO, StrOnline.statusNotYet)
    else V3HeroCard(
      title = StrOnline.sfCustomerSees,
      value = PresenceModel.headline(st),
      label = PresenceModel.sentence(st),
      caption = PresenceModel.caption(st),
    )
    V3Section(null) {
      if (st != null) {
        StrOnline.acceptance(st.orderAcceptance).takeIf { it.isNotEmpty() }?.let {
          V3ListRow(it, icon = Icons.Outlined.ShoppingCartCheckout, hue = V3Hue.MINT, subtitleLines = 3, chevron = false)
          V3Divider(start = 64.dp)
        }
        V3ListRow(PresenceModel.connection(st), icon = Icons.Outlined.Sensors, hue = V3Hue.SKY, subtitleLines = 2, chevron = false)
        V3Divider(start = 64.dp)
        V3ListRow(StrOnline.currentDeclaration(StrOnline.presenceMode(st.mode)), icon = Icons.Outlined.WifiTethering, hue = V3Hue.VIOLET, chevron = false)
        V3Divider(start = 64.dp)
      }
      V3ListRow(
        StorefrontSettingsModel.address(settings.slug),
        subtitle = if (!settings.enabled) StrOnline.sfOffNote else null,
        icon = Icons.Outlined.Link,
        hue = V3Hue.BLUE,
        chevron = false,
      )
    }
    if (pending) {
      Spacer(Modifier.height(12.dp))
      V3Message(V3Tone.WARNING, StrOnline.presenceNotConfirmed, action = if (sending) null else StrOnline.resend, onAction = onResend)
    }
    V3Collapse(notice != null) {
      Column(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        V3SuccessCheck(confirmed > 0 && !pending, size = 40.dp)
        Spacer(Modifier.height(6.dp))
        V3Message(if (pending) V3Tone.WARNING else V3Tone.SUCCESS, notice.orEmpty())
      }
    }
    V3Section(StrOnline.declareTitle, footer = StrOnline.presenceRule) {
      PresenceModel.MODES.forEachIndexed { i, m ->
        if (i > 0) V3Divider(start = 64.dp)
        V3ListRow(
          StrOnline.presenceMode(m),
          icon = modeIcon(m),
          hue = modeHue(m),
          selected = mode == m,
          onClick = { mode = m; untilError = null },
        )
        if (m == "online_until") V3Collapse(mode == m) {
          V3TextField(
            until, { until = it; untilError = null }, StrOnline.untilField,
            placeholder = "20:30", error = untilError,
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp).v3Shake(shake.takeIf { it > 0 }),
          )
        }
      }
    }
    Spacer(Modifier.height(16.dp))
    V3Button(
      StrOnline.submit,
      {
        if (mode == "online_until") {
          val m = PresenceModel.untilMinutes(until)
          if (m == null) { untilError = StrOnline.untilError; shake++ } else onDeclare(mode, m)
        } else onDeclare(mode, null)
      },
      Modifier.fillMaxWidth(),
      enabled = !pending,
      loading = sending,
      haptic = true,
    )
    Spacer(Modifier.height(24.dp))
  }
}

private fun modeIcon(m: String): ImageVector = when (m) {
  "online_until" -> Icons.Outlined.Schedule
  "online" -> Icons.Outlined.Language
  "offline" -> Icons.Outlined.Bedtime
  "closed" -> Icons.Outlined.Block
  else -> Icons.Outlined.WifiTethering
}

private fun modeHue(m: String): V3Hue = when (m) {
  "online_until" -> V3Hue.AMBER
  "online" -> V3Hue.MINT
  "offline" -> V3Hue.VIOLET
  "closed" -> V3Hue.CORAL
  else -> V3Hue.SKY
}
