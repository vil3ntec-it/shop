package ir.vil3ntec.tohid.ui.v3.money

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.NorthEast
import androidx.compose.material.icons.outlined.SouthWest
import androidx.compose.material3.Icon
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrMoney
import ir.vil3ntec.tohid.ui.screens.v3Text
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.screens.Motion
import ir.vil3ntec.tohid.ui.screens.MotionSpec

/**
 *  ردیفِ یک‌خطیِ حساب (~۴۶dp، دستورِ صاحب محصول در #354): همهٔ ردیف به رنگِ نوعش — برد سرخ،
 *  رسید سبز — و فقط سه چیز: تاریخ، مبلغ با علامت، و ماندهٔ پس از همین ردیف. بقیه در برگهٔ
 *  جزئیات. زدن ⇐ برگه؛ نگه‌داشتن ⇐ کارها؛ کشیدن به سمتِ آغاز ⇐ حذف (با برگرداندن).
 *
 *  پیشنهادِ کیت (`Draft`): `V3ListRow` زمینهٔ رنگیِ ردیف را ندارد؛ کشیدن این‌جا `SwipeToDismissBox`
 *  ِ خودِ Compose است.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun LedgerRow(
  date: String,
  amount: String,
  after: String?,
  tone: LedgerTone,
  /** برد ↗ / رسید ↙ — نامِ نوع برای صفحه‌خوان */
  up: Boolean,
  kindLabel: String,
  onClick: () -> Unit,
  onLongClick: () -> Unit,
  onDelete: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val c = PhoneV3.colors
  val ink = tone.ink()
  val shape = RoundedCornerShape(PhoneV3.tileRadius)
  val haptic = LocalHapticFeedback.current
  //  فشار ۰٫۹۷ مثلِ همهٔ ردیف‌های کیت (`v3Pressable` نگه‌داشتن ندارد)
  val source = remember { MutableInteractionSource() }
  val down by source.collectIsPressedAsState()
  val press by animateFloatAsState(if (down && Motion.enabled) 0.97f else 1f, MotionSpec.press(), label = "ledgerPress")
  @Suppress("DEPRECATION")
  val swipe = rememberSwipeToDismissBoxState(
    confirmValueChange = { value ->
      if (value == SwipeToDismissBoxValue.EndToStart) {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        onDelete()
        true
      } else false
    },
  )
  SwipeToDismissBox(
    state = swipe,
    modifier = modifier,
    enableDismissFromStartToEnd = false,
    backgroundContent = {
      Box(Modifier.fillMaxSize().clip(shape).background(c.tile).padding(horizontal = 16.dp), contentAlignment = Alignment.CenterEnd) {
        Icon(Icons.Outlined.DeleteOutline, contentDescription = StrMoney.delete, tint = LedgerTone.OWE.ink(), modifier = Modifier.size(20.dp))
      }
    },
  ) {
    Row(
      Modifier
        .fillMaxWidth()
        .height(46.dp)
        .graphicsLayer { scaleX = press; scaleY = press }
        .clip(shape)
        .background(tone.wash())
        .combinedClickable(
          interactionSource = source,
          indication = null,
          role = Role.Button,
          onClickLabel = StrMoney.details,
          onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onLongClick() },
          onClick = onClick,
        )
        .padding(horizontal = 12.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(if (up) Icons.Outlined.NorthEast else Icons.Outlined.SouthWest, contentDescription = kindLabel, tint = ink, modifier = Modifier.size(15.dp))
      Spacer(Modifier.width(8.dp))
      Text(date, style = v3Text(12, FontWeight.Normal, 16), color = c.inkMuted, maxLines = 1, modifier = Modifier.width(74.dp))
      Text(amount, style = v3Text(15, FontWeight.Bold, 20), color = ink, maxLines = 1, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
      if (after != null) Text(after, style = v3Text(12, FontWeight.Medium, 16), color = c.inkMuted, maxLines = 1)
    }
  }
}
