package ir.vil3ntec.tohid.ui.v3.modes

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.DeliveryDining
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrKit
import ir.vil3ntec.tohid.core.nav.AppMode
import ir.vil3ntec.tohid.ui.screens.MotionSpec
import ir.vil3ntec.tohid.ui.screens.MotionTokens
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.PhoneV3Palette
import ir.vil3ntec.tohid.ui.theme.PhoneV3Roles
import ir.vil3ntec.tohid.ui.theme.V3ButtonKind
import ir.vil3ntec.tohid.ui.v3.V3Button
import ir.vil3ntec.tohid.ui.v3.V3Hue
import ir.vil3ntec.tohid.ui.v3.V3IconTile
import ir.vil3ntec.tohid.ui.v3.V3Type
import ir.vil3ntec.tohid.ui.v3.rememberV3Stagger
import ir.vil3ntec.tohid.ui.v3.v3Pressable
import ir.vil3ntec.tohid.ui.v3.v3Stagger

/** آیکن، رنگ و دو خطِ هر حالت — یک جا برای انتخاب‌گر و سوییچر */
internal data class ModeLook(val icon: ImageVector, val hue: V3Hue, val title: () -> String, val body: () -> String)

internal fun lookOf(mode: AppMode): ModeLook = when (mode) {
  AppMode.SHOP -> ModeLook(Icons.Outlined.Storefront, V3Hue.BRAND, { StrKit.modeShop }, { StrKit.modeShopBody })
  AppMode.COURIER -> ModeLook(Icons.Outlined.DeliveryDining, V3Hue.BLUE, { StrKit.modeCourier }, { StrKit.modeCourierBody })
}

/**
 *  بارِ اول: «از برنامه چه می‌خواهید؟» — دو کارتِ بزرگ (دکان دارم / پیکم) که
 *  پلکانی می‌آیند، با فشار کوچک می‌شوند و انتخاب‌شده مرزِ برند و تیک
 *  می‌گیرد؛ «ادامه» تا انتخاب نشده غیرفعال است.
 */
@Composable
fun ModeChooser(onPick: (AppMode) -> Unit) {
  val c = PhoneV3.colors
  var picked by rememberSaveable { mutableStateOf<AppMode?>(null) }
  val stagger = rememberV3Stagger()
  Column(
    Modifier
      .fillMaxSize()
      .background(c.bg)
      .windowInsetsPadding(WindowInsets.statusBars)
      .windowInsetsPadding(WindowInsets.navigationBars)
      .verticalScroll(rememberScrollState())
      .padding(horizontal = 20.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Spacer(Modifier.height(48.dp))
    Text(StrKit.modeTitle, style = V3Type.largeTitle, color = c.ink, textAlign = TextAlign.Center, modifier = Modifier.v3Stagger(stagger, "t", 0))
    Spacer(Modifier.height(6.dp))
    Text(StrKit.modeSubtitle, style = V3Type.callout, color = c.inkMuted, textAlign = TextAlign.Center, modifier = Modifier.v3Stagger(stagger, "s", 1))
    Spacer(Modifier.height(28.dp))
    Column(Modifier.widthIn(max = 480.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      AppMode.entries.forEachIndexed { i, mode ->
        ModeCard(mode, selected = picked == mode, modifier = Modifier.v3Stagger(stagger, mode, i + 2)) { picked = mode }
      }
    }
    Spacer(Modifier.height(24.dp))
    V3Button(
      StrKit.continueLabel,
      onClick = { picked?.let(onPick) },
      modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth(),
      enabled = picked != null,
      haptic = true,
    )
    Spacer(Modifier.height(24.dp))
  }
}

@Composable
internal fun ModeCard(mode: AppMode, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
  val c = PhoneV3.colors
  val look = remember(mode) { lookOf(mode) }
  val edge by animateColorAsState(if (selected) PhoneV3Roles.focus(c) else c.hairline, MotionSpec.tweenOf(MotionTokens.SHORT), label = "modeEdge")
  val fill by animateColorAsState(if (selected) PhoneV3Palette.selected(c) else c.card, MotionSpec.tweenOf(MotionTokens.SHORT), label = "modeFill")
  val width by animateDpAsState(if (selected) 2.dp else 1.dp, MotionSpec.indicator(), label = "modeW")
  val shape = RoundedCornerShape(20.dp)
  Row(
    modifier
      .fillMaxWidth()
      .v3Pressable(label = look.title(), role = Role.RadioButton, haptic = true, onClick = onClick)
      .clip(shape)
      .background(fill)
      .border(width, edge, shape)
      .semantics { this.selected = selected }
      .padding(16.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    V3IconTile(look.icon, look.hue, size = 52.dp)
    Spacer(Modifier.width(14.dp))
    Column(Modifier.weight(1f)) {
      Text(look.title(), style = V3Type.headline, color = c.ink)
      Text(look.body(), style = V3Type.footnote, color = c.inkMuted)
    }
    Box(
      Modifier
        .size(26.dp)
        .clip(CircleShape)
        .background(if (selected) PhoneV3Roles.button(c, V3ButtonKind.PRIMARY).bg else c.card)
        .border(1.5.dp, if (selected) PhoneV3Roles.button(c, V3ButtonKind.PRIMARY).bg else PhoneV3Roles.inputBorder(c), CircleShape),
      contentAlignment = Alignment.Center,
    ) {
      if (selected) Icon(Icons.Outlined.Check, contentDescription = null, tint = PhoneV3Roles.button(c, V3ButtonKind.PRIMARY).fg, modifier = Modifier.size(16.dp))
    }
  }
}
