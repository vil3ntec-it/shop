package ir.vil3ntec.tohid.ui.desk

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.StrKit

/*
 *  پنجرهٔ گفت‌وگوی کامپیوتر (`claude/e-desk-d3c`) — جای `AlertDialog` ِ Material و برگهٔ تأییدِ پایینِ گوشی روی کامپیوتر.
 *  کارتِ وسطِ صفحه از [DeskStyle] (سطحِ کارت، خطِ مویی، گِردیِ ۱۲)، سرخطِ ۱۶ با کاشیِ آیکنِ اختیاری، متنِ دومِ کم‌رنگ، بدنهٔ
 *  پیمایشی و ردیفِ دکمه‌های [DeskButton] در پایان: «انصراف» (دوم) و کارِ اصلی (پُر؛ کارِ پاک‌کننده سرخ). Esc یا زدنِ بیرون =
 *  انصراف (همان `Dialog`)، Enter = کارِ اصلیِ روشن — مگر کارِ پاک‌کننده: آن‌جا فوکوس از آغاز روی «انصراف» است و Enter همان را
 *  می‌زند (۵.۱ بند ۱۱). فقط نمایش؛ کار و سنجش از صفحه می‌آید. بی انیمیشنِ پیوسته، بی تاری. گوشی این را نمی‌کشد.
 *  ⚠️ API افزودنی است: نام عوض نمی‌شود.
 */

/** یک دکمهٔ پنجره: نوشته، کار، روشن/خاموش، پاک‌کننده (سرخ) و میان‌برِ اختیاری؛ [modifier] برای `focusRequester` ِ صفحه */
class DeskDialogAction(
  val text: String,
  val enabled: Boolean = true,
  val danger: Boolean = false,
  val shortcut: String? = null,
  val modifier: Modifier = Modifier,
  val onClick: () -> Unit,
)

/** پهنای پنجره‌ها: پرسشِ کوتاه، فرمِ کوچک، فهرست/فرمِ پهن */
object DeskDialogSize {
  val narrow: Dp = 440.dp
  val form: Dp = 520.dp
  val wide: Dp = 680.dp
  /** بلندیِ بیشترِ بدنهٔ پیمایشی (پنجرهٔ ۷۶۸ هم جا می‌شود) */
  val bodyMax: Dp = 520.dp
}

/**
 *  پنجرهٔ گفت‌وگوی کامپیوتر. [confirm] کارِ اصلی (پُر؛ `danger` ⇒ سرخ)، [dismiss] انصراف (دوم)، [extra] کارهای کمترِ بی‌زمینه در
 *  آغازِ ردیفِ دکمه‌ها. بی هیچ دکمه، فقط «بستن» ِ سرخط. [content] بدنه است (متن، کادرها، فهرست).
 */
@Composable
fun DeskDialog(
  title: String,
  onDismiss: () -> Unit,
  modifier: Modifier = Modifier,
  subtitle: String? = null,
  icon: ImageVector? = null,
  danger: Boolean = false,
  confirm: DeskDialogAction? = null,
  dismiss: DeskDialogAction? = null,
  extra: List<DeskDialogAction> = emptyList(),
  width: Dp = DeskDialogSize.narrow,
  scroll: Boolean = true,
  content: @Composable ColumnScope.() -> Unit = {},
) {
  val c = Desk.colors
  val card = remember { FocusRequester() }
  val safe = remember { FocusRequester() }
  //  کارِ پاک‌کننده با فوکوسِ پیش‌فرض روی «انصراف»؛ بقیه روی خودِ پنجره تا Enter به کارِ اصلی برسد
  val safeFirst = confirm?.danger == true && dismiss != null
  Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
    Column(
      modifier
        .padding(DeskSpace.x6)
        .widthIn(max = width)
        .fillMaxWidth()
        .deskCard(c)
        .semantics { paneTitle = title }
        .onKeyEvent { e ->
          //  Enter ِ بی Shift/Alt که هیچ جزئی نگرفت (کادرِ یک‌خطی آن را رها می‌کند) ⇒ کارِ اصلی؛ کارِ پاک‌کننده نه
          val enter = e.key == Key.Enter || e.key == Key.NumPadEnter
          if (e.type == KeyEventType.KeyDown && enter && !e.isShiftPressed && !e.isAltPressed && confirm != null && confirm.enabled && !confirm.danger) {
            confirm.onClick(); true
          } else false
        }
        .focusRequester(card)
        .focusable(),
    ) {
      LaunchedEffect(Unit) { runCatching { if (safeFirst) safe.requestFocus() else card.requestFocus() } }
      Row(
        Modifier.fillMaxWidth().padding(start = DeskSpace.card, end = DeskSpace.x3, top = DeskSpace.x4, bottom = DeskSpace.x3),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        if (icon != null) {
          DeskIconTile(icon, c.deskTone(if (danger) DeskTone.NEGATIVE else DeskTone.INFO), size = DeskSize.tile, iconSize = DeskSize.tileIcon)
          Spacer(Modifier.width(DeskSpace.x3))
        }
        Column(Modifier.weight(1f)) {
          Text(title, Modifier.semantics { heading() }, style = DeskType.sectionTitle, color = c.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
          if (!subtitle.isNullOrEmpty()) Text(subtitle, style = DeskType.secondary, color = c.inkSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        DeskIconButton(Icons.Outlined.Close, Str.close, onDismiss)
      }
      DeskDivider()
      Column(
        Modifier
          .fillMaxWidth()
          .heightIn(max = DeskDialogSize.bodyMax)
          .then(if (scroll) Modifier.verticalScroll(rememberScrollState()) else Modifier)
          .padding(horizontal = DeskSpace.card, vertical = DeskSpace.x4),
        verticalArrangement = Arrangement.spacedBy(DeskSpace.x3),
        content = content,
      )
      if (confirm != null || dismiss != null || extra.isNotEmpty()) {
        DeskDivider()
        Row(
          Modifier.fillMaxWidth().padding(horizontal = DeskSpace.x4, vertical = DeskSpace.x3),
          horizontalArrangement = Arrangement.spacedBy(DeskSpace.x2),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          extra.forEach { a -> DeskButton(a.text, a.onClick, a.modifier, kind = DeskButtonKind.GHOST, shortcut = a.shortcut, enabled = a.enabled) }
          Spacer(Modifier.weight(1f))
          if (dismiss != null) {
            DeskButton(
              dismiss.text, dismiss.onClick, dismiss.modifier.then(if (safeFirst) Modifier.focusRequester(safe) else Modifier),
              shortcut = dismiss.shortcut, enabled = dismiss.enabled,
            )
          }
          if (confirm != null) {
            DeskButton(
              confirm.text, confirm.onClick, confirm.modifier,
              kind = if (confirm.danger) DeskButtonKind.DANGER else DeskButtonKind.PRIMARY,
              shortcut = confirm.shortcut, enabled = confirm.enabled,
            )
          }
        }
      }
    }
  }
}

/**
 *  پرسشِ تأییدِ کامپیوتر — همتای `V3ConfirmSheet` ِ گوشی: عنوان، پیامد (یک جمله)، «انصراف» و کارِ اصلی (پاک‌کننده ⇒ سرخ و
 *  فوکوسِ آغاز روی «انصراف»). کارِ اصلی اول پنجره را می‌بندد، بعد [onConfirm] — همان ترتیبِ برگهٔ گوشی.
 */
@Composable
fun DeskConfirmDialog(
  title: String,
  message: String,
  confirm: String,
  onConfirm: () -> Unit,
  onDismiss: () -> Unit,
  icon: ImageVector? = null,
  danger: Boolean = true,
) {
  DeskDialog(
    title = title,
    onDismiss = onDismiss,
    icon = icon,
    danger = danger,
    confirm = DeskDialogAction(confirm, danger = danger) { onDismiss(); onConfirm() },
    dismiss = DeskDialogAction(StrKit.cancel, onClick = onDismiss),
  ) {
    if (message.isNotEmpty()) Text(message, style = DeskType.body, color = Desk.colors.inkSecondary)
  }
}

/** متنِ بدنهٔ پنجره (کم‌رنگ؛ [strong] ⇒ همان رنگِ متن) */
@Composable
fun DeskDialogText(text: String, modifier: Modifier = Modifier, strong: Boolean = false, color: androidx.compose.ui.graphics.Color? = null) {
  val c = Desk.colors
  Text(text, modifier, style = if (strong) DeskType.bodyStrong else DeskType.body, color = color ?: if (strong) c.ink else c.inkSecondary)
}

/** کادرِ پنجره با برچسبِ کوچکِ بالا و یادداشتِ کم‌رنگِ زیر (همان [DeskLabeledField] با خطا و کلید) */
@Composable
fun DeskDialogField(
  label: String,
  value: String,
  onValueChange: (String) -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  isError: Boolean = false,
  note: String? = null,
  placeholder: String? = null,
  focusRequester: FocusRequester? = null,
  textAlign: androidx.compose.ui.text.style.TextAlign = androidx.compose.ui.text.style.TextAlign.Start,
) {
  val c = Desk.colors
  Column(modifier.fillMaxWidth()) {
    Text(label, Modifier.padding(bottom = DeskSpace.x1), style = DeskType.label, color = c.inkSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    DeskTextField(
      value, onValueChange, Modifier.fillMaxWidth(), placeholder = placeholder, enabled = enabled, isError = isError,
      focusRequester = focusRequester, textAlign = textAlign, description = label,
    )
    if (note != null) Text(note, Modifier.padding(top = DeskSpace.x1), style = DeskType.secondary, color = if (isError) c.negative else c.inkSecondary)
  }
}

/**
 *  پرسشِ «کار با دلیل» ِ کامپیوتر (لغو، باطل): عنوان، پیامد، کادرِ دلیل که از آغاز فوکوس دارد، «انصراف» و کارِ اصلی (سرخ).
 *  [enabled] سنجشِ صفحه (دلیلِ لازم، کارِ در جریان)؛ دلیل همان [reason] ِ صفحه است.
 */
@Composable
fun DeskReasonDialog(
  title: String,
  message: String,
  label: String,
  reason: String,
  onReason: (String) -> Unit,
  confirm: String,
  enabled: Boolean,
  onConfirm: () -> Unit,
  onDismiss: () -> Unit,
  danger: Boolean = true,
) {
  val field = remember { FocusRequester() }
  DeskDialog(
    title = title,
    onDismiss = onDismiss,
    danger = danger,
    confirm = DeskDialogAction(confirm, enabled = enabled, danger = danger, onClick = onConfirm),
    dismiss = DeskDialogAction(StrKit.cancel, onClick = onDismiss),
  ) {
    if (message.isNotEmpty()) DeskDialogText(message)
    DeskDialogField(label, reason, onReason, focusRequester = field)
    LaunchedEffect(Unit) { runCatching { field.requestFocus() } }
  }
}
