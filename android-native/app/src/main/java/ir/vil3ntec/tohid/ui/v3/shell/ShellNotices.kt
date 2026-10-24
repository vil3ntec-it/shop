package ir.vil3ntec.tohid.ui.v3.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.BuildConfig
import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.StrHome
import ir.vil3ntec.tohid.sync.v1.SoftLock
import ir.vil3ntec.tohid.ui.AppNav
import ir.vil3ntec.tohid.ui.Platform
import ir.vil3ntec.tohid.ui.screens.v3Text
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.PhoneV3Roles
import ir.vil3ntec.tohid.ui.theme.V3Tone
import ir.vil3ntec.tohid.ui.v3.nav.LocalV3Nav
import ir.vil3ntec.tohid.ui.v3.v3Pressable
import ir.vil3ntec.tohid.update.ReleaseFeed
import ir.vil3ntec.tohid.update.UpdateManager

private fun openRoute(nav: ir.vil3ntec.tohid.ui.v3.nav.V3Nav?, route: String) {
  if (nav != null) nav.open(route) else AppNav.go(route)
}

/**
 *  اشتراک (بندِ ۲۱٫۸، همان قاعدهٔ `SubscriptionBanner`): هشدار از ۷ روز مانده، خطا پس از
 *  پایان؛ متن صریح می‌گوید داده پاک نمی‌شود. فقط تپشِ کش‌شده را می‌خواند ⇐ `subscription`.
 */
@Composable
fun SubscriptionNotice() {
  val context = LocalContext.current
  val nav = LocalV3Nav.current
  val locked by SoftLock.locked.collectAsState()
  LaunchedEffect(Unit) { runCatching { SoftLock.refresh(context) } }
  val warn = remember(locked) { runCatching { SoftLock.warnDays(context) }.getOrNull() }
  if (!locked && warn == null) return
  DraftNoticeBar(
    tone = if (locked) V3Tone.ERROR else V3Tone.WARNING,
    text = if (locked) StrHome.subNoticeEnded else StrHome.subNoticeDays(warn ?: 0),
    action = Str.mySubscription,
    onAction = { openRoute(nav, ShellTargets.SUBSCRIPTION) },
    onClick = { openRoute(nav, ShellTargets.SUBSCRIPTION) },
  )
}

/**
 *  «نسخهٔ تازه» (همان رفتارِ `UpdateBanner`): بررسیِ خودکار، نه در ساختِ بازار، گرفتن و
 *  نصب از همین دکمه، «بعداً» تا بازکردنِ بعدی — جز وقتی نسخه زیرِ حداقلِ پشتیبانی است.
 *  زدنِ نوار ⇐ `settings/about`.
 */
@Composable
fun UpdateNotice() {
  //  ⛔ نسخهٔ کافه‌بازار فقط از بازار به‌روز می‌شود
  if (BuildConfig.STORE == "bazaar") return
  val context = LocalContext.current
  val nav = LocalV3Nav.current
  LaunchedEffect(Unit) {
    val prefs = context.getSharedPreferences("tohid", android.content.Context.MODE_PRIVATE)
    val repo = prefs.getString("update_repo", "vil3ntec-it/shop") ?: "vil3ntec-it/shop"
    UpdateManager.startAuto(context, repo, BuildConfig.VERSION_NAME)
  }
  val found = UpdateManager.release ?: return
  val required = ReleaseFeed.belowMinimum(BuildConfig.VERSION_NAME, found.minVersion)
  var hidden by remember(found.version) { mutableStateOf(false) }
  if (hidden && !required) return
  val progress = UpdateManager.progress
  val ready = UpdateManager.ready
  val busy = UpdateManager.busy
  DraftNoticeBar(
    tone = if (required) V3Tone.ERROR else V3Tone.INFO,
    icon = Icons.Outlined.SystemUpdate,
    text = when {
      progress >= 0 -> StrHome.updateGetting(found.version, progress) + (UpdateManager.note?.let { " · $it" } ?: "")
      ready != null -> StrHome.updateReady(found.version)
      required -> StrHome.updateRequired(found.version)
      else -> StrHome.updateNew(found.version)
    },
    detail = UpdateManager.message?.takeIf { progress < 0 && ready == null && !busy },
    progress = progress.takeIf { it >= 0 }?.let { it / 100f },
    action = if (progress >= 0 || busy) null else if (ready != null && !Platform.DESKTOP) StrHome.install else StrHome.update,
    onAction = { if (ready != null) UpdateManager.install(context) else UpdateManager.download(context) },
    onClose = if (required || progress >= 0) null else ({ hidden = true }),
    onClick = { openRoute(nav, ShellTargets.ABOUT) },
  )
}

/**
 *  پیش‌نویسِ کیت (برای بردن به `V3Message` با گونهٔ فشرده): نوارِ ۴۴dp با رنگِ پیامِ §۹ —
 *  آیکنِ معنادار، یک خط متن (+ خطِ دومِ اختیاری و نوارِ پیشرفت)، کارِ برندرنگ و ×.
 */
@Composable
internal fun DraftNoticeBar(
  tone: V3Tone,
  text: String,
  onClick: () -> Unit,
  icon: ImageVector? = null,
  detail: String? = null,
  progress: Float? = null,
  action: String? = null,
  onAction: () -> Unit = {},
  onClose: (() -> Unit)? = null,
) {
  val c = PhoneV3.colors
  val pair = PhoneV3Roles.tone(c, tone)
  val shape = RoundedCornerShape(12.dp)
  val glyph = icon ?: when (tone) {
    V3Tone.ERROR -> Icons.Outlined.ErrorOutline
    V3Tone.WARNING -> Icons.Outlined.WarningAmber
    else -> Icons.Outlined.Info
  }
  Row(
    Modifier
      .fillMaxWidth()
      .heightIn(min = 44.dp)
      .clip(shape)
      .background(pair.bg)
      .border(1.dp, pair.border, shape)
      .v3Pressable(label = text, scale = 0.99f, onClick = onClick)
      .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(glyph, contentDescription = null, tint = PhoneV3Roles.toneIcon(c, tone), modifier = Modifier.size(18.dp))
    Spacer(Modifier.width(8.dp))
    Column(Modifier.weight(1f)) {
      Text(text, style = v3Text(13, FontWeight.SemiBold, 18), color = pair.fg, maxLines = 2, overflow = TextOverflow.Ellipsis)
      if (detail != null) Text(detail, style = v3Text(12, FontWeight.Medium, 16), color = pair.fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
      if (progress != null) LinearProgressIndicator(
        progress = { progress },
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        color = PhoneV3Roles.toneIcon(c, tone),
        trackColor = pair.border,
      )
    }
    if (action != null) {
      Text(
        action,
        style = v3Text(13, FontWeight.Bold, 18),
        color = pair.fg,
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).v3Pressable(label = action, haptic = true, onClick = onAction).padding(horizontal = 10.dp, vertical = 8.dp),
      )
    }
    if (onClose != null) {
      Icon(
        Icons.Outlined.Close, contentDescription = StrHome.later, tint = pair.fg,
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).v3Pressable(label = StrHome.later, scale = 0.92f, onClick = onClose).padding(8.dp).size(16.dp),
      )
    } else Spacer(Modifier.width(8.dp))
  }
}
