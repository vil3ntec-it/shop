package ir.vil3ntec.tohid.ui.v3.onboarding

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.LocationOff
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrAuth
import ir.vil3ntec.tohid.core.i18n.StrKit
import ir.vil3ntec.tohid.data.DeviceLocation
import ir.vil3ntec.tohid.data.LocationFix
import ir.vil3ntec.tohid.data.Terms
import ir.vil3ntec.tohid.ui.components.common.openOnMap
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.PhoneV3Roles
import ir.vil3ntec.tohid.ui.theme.V3ButtonKind
import ir.vil3ntec.tohid.ui.v3.V3Divider
import ir.vil3ntec.tohid.ui.v3.V3Hue
import ir.vil3ntec.tohid.ui.v3.V3IconTile
import ir.vil3ntec.tohid.ui.v3.V3ListRow
import ir.vil3ntec.tohid.ui.v3.V3PageSwitch
import ir.vil3ntec.tohid.ui.v3.V3Springs
import ir.vil3ntec.tohid.ui.v3.V3TextButton
import ir.vil3ntec.tohid.ui.v3.V3Type
import ir.vil3ntec.tohid.ui.v3.v3Shake
import kotlinx.coroutines.launch

/**
 *  گامِ ۳ از ۳: لوکیشنِ دکان و پذیرشِ شرایط — حساب همین‌جا ساخته می‌شود، نه زودتر. جست‌وجوی لوکیشن
 *  با باز شدنِ صفحه شروع می‌شود (اجازه یک بار پرسیده می‌شود و «نه» راه را نمی‌بندد)؛ پیدا نشد ⇒ باز هم
 *  حساب ساخته می‌شود. پذیرشِ شرایط اجباری است و متنِ کاملش صفحهٔ خودش را دارد (همین‌جا، تا لوکیشنِ
 *  پیداشده با رفتن و آمدن گم نشود).
 */
@Composable
internal fun TermsStep(ui: AuthUi, env: AuthEnv, act: AuthActions, onBack: (() -> Unit)?) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  var fix by remember { mutableStateOf<LocationFix?>(null) }
  var looking by remember { mutableStateOf(true) }
  fun look(force: Boolean) {
    looking = true
    scope.launch {
      fix = DeviceLocation.current(context, force = force)
      looking = false
    }
  }
  val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
    if (granted.values.any { it }) look(true) else looking = false
  }
  LaunchedEffect(Unit) {
    if (DeviceLocation.granted(context)) look(false) else ask.launch(DeviceLocation.PERMISSIONS)
  }
  V3PageSwitch(target = ui.step, depth = { it.depth }) { step ->
    if (step == AuthStep.TERMS_TEXT) {
      TermsPage(accepting = !ui.accepted, onClose = { act.edit { it.closeTermsText() } }, onAccept = { act.edit { it.acceptTerms() } })
    } else {
      TermsFrame(
        ui, env, act, onBack, fix, looking,
        onRetry = { if (DeviceLocation.granted(context)) look(true) else ask.launch(DeviceLocation.PERMISSIONS) },
        onMap = { f -> openOnMap(context, f.lat, f.lng) },
      )
    }
  }
}

@Composable
internal fun TermsFrame(
  ui: AuthUi,
  env: AuthEnv,
  act: AuthActions,
  onBack: (() -> Unit)?,
  fix: LocationFix?,
  looking: Boolean,
  onRetry: () -> Unit,
  onMap: (LocationFix) -> Unit,
) {
  GateBackdrop {
    GateFrame(
      title = StrAuth.oneStepLeft,
      subtitle = StrAuth.locationAndTerms,
      onBack = onBack,
      stage = AuthRules.signupStage(ui),
      cardPadding = PaddingValues(vertical = 8.dp),
    ) {
      LocationRow(fix, looking, onRetry, onMap)
      V3Divider(start = 64.dp)
      V3ListRow(
        title = Terms.TITLE,
        subtitle = StrAuth.termsTap(Terms.SECTIONS.size),
        icon = Icons.Outlined.Description,
        hue = V3Hue.VIOLET,
        onClick = { act.edit { it.openTermsText() } },
      )
      V3Divider(start = 64.dp)
      AcceptRow(
        checked = ui.accepted,
        shake = ui.failures.takeIf { ui.termsMissing && it > 0 },
        onToggle = { v -> act.edit { it.setAccepted(v) } },
      )
      Column(Modifier.padding(horizontal = 20.dp)) {
        FieldNote(error = if (ui.termsMissing) StrAuth.mustAcceptTerms else null)
        GateMessages(ui.error, ui.note, ui.failures)
        Spacer(Modifier.height(16.dp))
        SubmitButton(StrAuth.createAndSignIn, enabled = !ui.busy, loading = ui.busy, done = env.done, onClick = { act.completeSignup(fix) })
        Spacer(Modifier.height(12.dp))
      }
    }
  }
}

/** در حال پیدا کردن… / پیدا شد «روی نقشه ببینید» / پیدا نشد + «دوباره» */
@Composable
private fun LocationRow(fix: LocationFix?, looking: Boolean, onRetry: () -> Unit, onMap: (LocationFix) -> Unit) {
  val c = PhoneV3.colors
  val found = fix != null
  V3ListRow(
    title = when {
      looking -> StrAuth.findingLocation
      found -> StrAuth.locationFound
      else -> StrAuth.locationNotFound
    },
    subtitle = when {
      fix != null -> AuthRules.coordinates(fix.lat, fix.lng)
      looking -> StrAuth.locationSaving
      else -> StrAuth.locationOptional
    },
    leading = {
      if (looking) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(c.tile), contentAlignment = Alignment.Center) {
          CircularProgressIndicator(Modifier.size(20.dp), color = PhoneV3Roles.focus(c), strokeWidth = 2.dp)
        }
      } else {
        V3IconTile(if (found) Icons.Outlined.LocationOn else Icons.Outlined.LocationOff, if (found) V3Hue.MINT else V3Hue.AMBER)
      }
    },
    chevron = false,
    trailing = when {
      looking -> null
      fix != null -> ({ V3TextButton(StrAuth.seeOnMap, { onMap(fix) }) })
      else -> ({ V3TextButton(StrKit.retry, onRetry) })
    },
    onClick = fix?.let { f -> { onMap(f) } },
  )
}

/** «شرایط و ضوابط را خوانده‌ام و می‌پذیرم.» — مربعِ تیک با فنرِ شاد؛ نپذیرفته ⇐ لرزشِ کیت */
@Composable
private fun AcceptRow(checked: Boolean, shake: Any?, onToggle: (Boolean) -> Unit) {
  val c = PhoneV3.colors
  val pair = PhoneV3Roles.button(c, V3ButtonKind.PRIMARY)
  val pop by animateFloatAsState(if (checked) 1f else 0f, V3Springs.playful(), label = "acceptPop")
  val shape = RoundedCornerShape(7.dp)
  Row(
    Modifier
      .fillMaxWidth()
      .v3Shake(shake)
      .toggleable(value = checked, role = Role.Checkbox, onValueChange = onToggle)
      .padding(horizontal = 20.dp, vertical = 14.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(
      Modifier
        .size(24.dp)
        .clip(shape)
        .background(if (checked) pair.bg else c.card)
        .border(1.5.dp, if (checked) pair.bg else PhoneV3Roles.inputBorder(c), shape),
      contentAlignment = Alignment.Center,
    ) {
      Icon(Icons.Outlined.Check, contentDescription = null, tint = pair.fg, modifier = Modifier.size(16.dp).graphicsLayer { scaleX = pop; scaleY = pop; alpha = pop })
    }
    Spacer(Modifier.width(14.dp))
    Text(StrAuth.acceptTerms, style = V3Type.body, color = c.ink, modifier = Modifier.weight(1f))
  }
}
