package ir.vil3ntec.tohid.ui.v3.account

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.ui.screens.MotionSpec
import ir.vil3ntec.tohid.ui.screens.MotionTokens
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.PhoneV3Palette
import ir.vil3ntec.tohid.ui.theme.PhoneV3Roles
import ir.vil3ntec.tohid.ui.theme.V3ButtonKind
import ir.vil3ntec.tohid.ui.theme.V3Space
import ir.vil3ntec.tohid.ui.v3.V3Type
import ir.vil3ntec.tohid.ui.v3.v3Pressable

/**
 *  کارتِ انتخابیِ تک‌گزینه (رادیو) — نقشِ کدِ پیوستن و مدتِ اشتراک. گوشهٔ ۱۲،
 *  مرزِ مویی؛ انتخاب‌شده: سطحِ surfaceSelected، مرزِ ۲dpِ برند و تیکِ گرد در
 *  گوشه — مرز و رنگ با حرکتِ کوتاه عوض می‌شوند. رنگ تنها نیست: تیک و
 *  «انتخاب شد» برای صفحه‌خوان.
 *
 *  `badge`: برچسبِ کوچکِ بالای کارت («پیشنهاد ما»). پیش‌نویسِ گروهِ حساب برای
 *  بردن به کیت (`V3SelectCard`).
 */
@Composable
internal fun DraftSelectCard(
  selected: Boolean,
  label: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  badge: String? = null,
  content: @Composable ColumnScope.() -> Unit,
) {
  val c = PhoneV3.colors
  val shape = RoundedCornerShape(V3Space.cardRadius)
  val edge by animateColorAsState(if (selected) PhoneV3Roles.focus(c) else c.hairline, MotionSpec.tweenOf(MotionTokens.MEDIUM), label = "selEdge")
  val width by animateDpAsState(if (selected) 2.dp else 1.dp, MotionSpec.tweenOf(MotionTokens.MEDIUM), label = "selWidth")
  val fill by animateColorAsState(if (selected) PhoneV3Palette.selected(c) else c.card, MotionSpec.tweenOf(MotionTokens.MEDIUM), label = "selFill")
  Box(
    modifier
      .v3Pressable(label = label, role = Role.RadioButton, haptic = true, onClick = onClick)
      .semantics { this.selected = selected },
  ) {
    Column(
      Modifier
        .fillMaxWidth()
        .fillMaxHeight()
        .clip(shape)
        .background(fill)
        .border(width, edge, shape)
        .padding(horizontal = 12.dp, vertical = 12.dp),
      content = {
        if (badge != null) {
          Text(
            badge,
            style = V3Type.caption,
            color = PhoneV3Roles.brandText(c),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            //  جا برای تیکِ گوشه تا برچسب زیرش نرود
            modifier = Modifier.padding(bottom = 4.dp, end = 22.dp),
          )
        }
        content()
      },
    )
    if (selected) {
      val primary = PhoneV3Roles.button(c, V3ButtonKind.PRIMARY)
      Box(
        Modifier.align(Alignment.TopEnd).padding(8.dp).size(20.dp).clip(CircleShape).background(primary.bg),
        contentAlignment = Alignment.Center,
      ) { Icon(Icons.Outlined.Check, contentDescription = null, tint = primary.fg, modifier = Modifier.size(14.dp)) }
    }
  }
}
