package ir.vil3ntec.tohid.ui.components.common

import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.ui.desk.Desk
import ir.vil3ntec.tohid.ui.desk.DeskDialogSize
import ir.vil3ntec.tohid.ui.desk.DeskDivider
import ir.vil3ntec.tohid.ui.desk.DeskIconButton
import ir.vil3ntec.tohid.ui.desk.DeskRadius
import ir.vil3ntec.tohid.ui.desk.DeskSize
import ir.vil3ntec.tohid.ui.desk.DeskSpace
import ir.vil3ntec.tohid.ui.desk.DeskType
import ir.vil3ntec.tohid.ui.desk.deskCard
import ir.vil3ntec.tohid.ui.desk.deskClickable
import ir.vil3ntec.tohid.ui.v3.nav.LocalDeskLayout

/*
 *  پلِ پنجره‌های مشترکِ گوشی و کامپیوتر (`claude/e-desk-d3d`) — به‌جای `AlertDialog`/`TextButton`/`Button`/`OutlinedButton` ِ Material در
 *  پنجره‌های پرونده‌های مشترک. همان پارامترها، پس هر جای صدا زدن فقط نامش عوض می‌شود:
 *   • گوشی (و پیش‌نمایشِ گوشی، `LocalDeskLayout` = false): **بایت‌به‌بایت همان** `AlertDialog` و دکمه‌های Material با همان آرگومان‌ها؛
 *   • کامپیوتر: کارتِ `ui/desk` (سطح، خطِ مویی، گِردیِ ۱۲)، سرخطِ ۱۶ با «بستن»، بدنه و ردیفِ دکمه‌های کامپیوتر — «تأیید» با خطِ دورِ برند
 *     (کارِ پاک‌کننده با [danger]: خط و نوشتهٔ سرخ)، «انصراف» خطِ مویی، دکمهٔ درونِ بدنه بی‌زمینه. Esc یا زدنِ بیرون = همان `onDismissRequest`؛
 *     Enter = همان دکمهٔ «تأیید» وقتی روشن است، مگر کارِ پاک‌کننده که فوکوسِ آغازش روی «انصراف» است (Enter ⇒ انصراف).
 *  هیچ کار، سنجش یا نوشتنی این‌جا نیست: دکمه‌ها همان `onClick` ِ صفحه را صدا می‌زنند.
 */

/** جای دکمه در پنجرهٔ کامپیوتر: «تأیید»، «انصراف» یا درونِ بدنه */
enum class ShopDialogSlot { CONFIRM, DISMISS, BODY }

/** وضعیتِ پنجرهٔ کامپیوتر برای دکمه‌هایش: کارِ «تأیید» برای Enter و فوکوسِ امنِ «انصراف» */
@Stable
class ShopDialogHost internal constructor() {
  internal var danger by mutableStateOf(false)
  internal var confirm: (() -> Unit)? = null
  internal var confirmEnabled = true
  internal val safe = FocusRequester()
}

val LocalShopDialogSlot = compositionLocalOf<ShopDialogSlot?> { null }

/** برچسبِ آزمونِ کارتِ پنجرهٔ کامپیوتر (Esc/Enter در آزمونِ صفحه) */
const val SHOP_DIALOG_TAG = "shop-dialog"

val LocalShopDialogHost = compositionLocalOf<ShopDialogHost?> { null }

/**
 *  همان `AlertDialog` ِ Material (گوشی) یا پنجرهٔ کامپیوتر. [danger] فقط کامپیوتر را عوض می‌کند (کارِ پاک‌کننده: Enter نمی‌زند، فوکوسِ آغاز روی
 *  «انصراف»، دکمهٔ سرخِ خط‌دار)؛ گوشی آن را نمی‌بیند.
 */
@Composable
fun ShopAlertDialog(
  onDismissRequest: () -> Unit,
  confirmButton: @Composable () -> Unit,
  modifier: Modifier = Modifier,
  dismissButton: @Composable (() -> Unit)? = null,
  icon: @Composable (() -> Unit)? = null,
  title: @Composable (() -> Unit)? = null,
  text: @Composable (() -> Unit)? = null,
  shape: Shape = AlertDialogDefaults.shape,
  containerColor: Color = AlertDialogDefaults.containerColor,
  iconContentColor: Color = AlertDialogDefaults.iconContentColor,
  titleContentColor: Color = AlertDialogDefaults.titleContentColor,
  textContentColor: Color = AlertDialogDefaults.textContentColor,
  tonalElevation: Dp = AlertDialogDefaults.TonalElevation,
  properties: DialogProperties = DialogProperties(),
  danger: Boolean = false,
) {
  if (!LocalDeskLayout.current) {
    AlertDialog(
      onDismissRequest = onDismissRequest, confirmButton = confirmButton, modifier = modifier, dismissButton = dismissButton,
      icon = icon, title = title, text = text, shape = shape, containerColor = containerColor, iconContentColor = iconContentColor,
      titleContentColor = titleContentColor, textContentColor = textContentColor, tonalElevation = tonalElevation, properties = properties,
    )
    return
  }
  val c = Desk.colors
  val host = remember { ShopDialogHost() }
  host.danger = danger
  val card = remember { FocusRequester() }
  val safeFirst = danger && dismissButton != null
  Dialog(
    onDismissRequest = onDismissRequest,
    properties = DialogProperties(dismissOnBackPress = properties.dismissOnBackPress, dismissOnClickOutside = properties.dismissOnClickOutside, usePlatformDefaultWidth = false),
  ) {
    Column(
      modifier
        .padding(DeskSpace.x6)
        .widthIn(max = DeskDialogSize.form)
        .fillMaxWidth()
        .heightIn(max = DeskDialogSize.bodyMax + 160.dp)
        .deskCard(c)
        .testTag(SHOP_DIALOG_TAG)
        .onKeyEvent { e ->
          //  Enter ِ بی Shift/Alt که هیچ جزئی نگرفت ⇒ همان «تأیید»؛ کارِ پاک‌کننده نه
          val enter = e.key == Key.Enter || e.key == Key.NumPadEnter
          val ok = host.confirm
          if (e.type == KeyEventType.KeyDown && enter && !e.isShiftPressed && !e.isAltPressed && !host.danger && ok != null && host.confirmEnabled) { ok(); true } else false
        }
        .focusRequester(card)
        .focusable(),
    ) {
      LaunchedEffect(Unit) { runCatching { if (safeFirst) host.safe.requestFocus() else card.requestFocus() } }
      CompositionLocalProvider(LocalShopDialogHost provides host) {
        Row(
          Modifier.fillMaxWidth().padding(start = DeskSpace.card, end = DeskSpace.x3, top = DeskSpace.x4, bottom = DeskSpace.x3),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          if (icon != null) {
            CompositionLocalProvider(LocalContentColor provides if (danger) c.negative else c.icon) { icon() }
            Spacer(Modifier.width(DeskSpace.x3))
          }
          Box(Modifier.weight(1f)) {
            if (title != null) CompositionLocalProvider(LocalContentColor provides c.ink, LocalTextStyle provides DeskType.sectionTitle) { title() }
          }
          DeskIconButton(Icons.Outlined.Close, Str.close, onDismissRequest)
        }
        DeskDivider()
        if (text != null) {
          Box(Modifier.weight(1f, fill = false).fillMaxWidth().padding(horizontal = DeskSpace.card, vertical = DeskSpace.x4)) {
            CompositionLocalProvider(
              LocalContentColor provides c.inkSecondary, LocalTextStyle provides DeskType.body, LocalShopDialogSlot provides ShopDialogSlot.BODY,
            ) { text() }
          }
          DeskDivider()
        }
        Row(
          Modifier.fillMaxWidth().padding(horizontal = DeskSpace.x4, vertical = DeskSpace.x3),
          horizontalArrangement = Arrangement.spacedBy(DeskSpace.x2, Alignment.End),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          if (dismissButton != null) CompositionLocalProvider(LocalShopDialogSlot provides ShopDialogSlot.DISMISS) { dismissButton() }
          CompositionLocalProvider(LocalShopDialogSlot provides ShopDialogSlot.CONFIRM) { confirmButton() }
        }
      }
    }
  }
}

/** همان `TextButton` (گوشی)؛ درونِ [ShopAlertDialog] ِ کامپیوتر دکمهٔ کامپیوتر */
@Composable
fun ShopTextButton(
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  shape: Shape = ButtonDefaults.textShape,
  colors: ButtonColors = ButtonDefaults.textButtonColors(),
  contentPadding: PaddingValues = ButtonDefaults.TextButtonContentPadding,
  content: @Composable RowScope.() -> Unit,
) {
  val slot = LocalShopDialogSlot.current
  if (slot == null) TextButton(onClick, modifier, enabled, shape = shape, colors = colors, contentPadding = contentPadding, content = content)
  else DeskSlotButton(slot, onClick, modifier, enabled, filled = false, content)
}

/** همان `Button` (گوشی)؛ درونِ [ShopAlertDialog] ِ کامپیوتر دکمهٔ کامپیوتر */
@Composable
fun ShopButton(
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  shape: Shape = ButtonDefaults.shape,
  colors: ButtonColors = ButtonDefaults.buttonColors(),
  contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
  content: @Composable RowScope.() -> Unit,
) {
  val slot = LocalShopDialogSlot.current
  if (slot == null) Button(onClick, modifier, enabled, shape = shape, colors = colors, contentPadding = contentPadding, content = content)
  else DeskSlotButton(slot, onClick, modifier, enabled, filled = true, content)
}

/** همان `OutlinedButton` (گوشی)؛ درونِ [ShopAlertDialog] ِ کامپیوتر دکمهٔ کامپیوتر */
@Composable
fun ShopOutlinedButton(
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  colors: ButtonColors = ButtonDefaults.outlinedButtonColors(),
  content: @Composable RowScope.() -> Unit,
) {
  val slot = LocalShopDialogSlot.current
  if (slot == null) OutlinedButton(onClick, modifier, enabled, colors = colors, content = content)
  else DeskSlotButton(slot, onClick, modifier, enabled, filled = false, content)
}

/**
 *  دکمهٔ کامپیوترِ پنجره (۳۶dp، گِردیِ کنترل). «تأیید» خطِ دورِ برند و نوشتهٔ برند (کارِ پاک‌کننده: خط و نوشتهٔ سرخ)، «انصراف» و دکمهٔ پُرِ
 *  بدنه خطِ دورِ مویی، دکمهٔ بی‌زمینهٔ بدنه با نوشتهٔ برند — همه روی سطحِ کارت، تا رنگِ دستیِ `Text` ِ صفحه (سرخِ «پاک کن»، رنگِ اصلیِ
 *  «بازسازی») همیشه خوانا بماند.
 */
@Composable
private fun DeskSlotButton(slot: ShopDialogSlot, onClick: () -> Unit, modifier: Modifier, enabled: Boolean, filled: Boolean, content: @Composable RowScope.() -> Unit) {
  val c = Desk.colors
  val host = LocalShopDialogHost.current
  val danger = host?.danger == true
  val latest by rememberUpdatedState(onClick)
  if (slot == ShopDialogSlot.CONFIRM && host != null) SideEffect { host.confirm = { latest() }; host.confirmEnabled = enabled }
  val confirm = slot == ShopDialogSlot.CONFIRM
  val outlined = confirm || slot == ShopDialogSlot.DISMISS || filled
  val fg = when {
    !enabled -> c.inkMuted
    confirm && danger -> c.negative
    confirm -> c.brandInk
    slot == ShopDialogSlot.BODY && !filled -> c.brandInk
    else -> c.ink
  }
  val edge = when {
    !enabled -> c.hairline
    confirm && danger -> c.negative
    confirm -> c.brandInk
    else -> c.hairline
  }
  Row(
    modifier
      .heightIn(min = DeskSize.control)
      .then(if (slot == ShopDialogSlot.DISMISS && danger && host != null) Modifier.focusRequester(host.safe) else Modifier)
      .then(if (outlined) Modifier.border(if (confirm) 1.5.dp else DeskSize.hairline, edge, DeskRadius.controlShape) else Modifier)
      .deskClickable(enabled = enabled, rest = if (outlined) c.surface else Color.Transparent, onClick = onClick)
      .padding(horizontal = DeskSpace.x3 + 2.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.Center,
  ) {
    CompositionLocalProvider(LocalContentColor provides fg, LocalTextStyle provides DeskType.bodyStrong, LocalShopDialogSlot provides null) { content() }
  }
}
