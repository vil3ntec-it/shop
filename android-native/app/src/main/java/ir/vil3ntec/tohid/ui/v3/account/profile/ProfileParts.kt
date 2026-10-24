package ir.vil3ntec.tohid.ui.v3.account.profile

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.ManageAccounts
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrAccount
import ir.vil3ntec.tohid.data.PhotoStore
import ir.vil3ntec.tohid.ui.screens.isDark
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.v3.V3Type
import ir.vil3ntec.tohid.ui.v3.account.AccountFormat
import ir.vil3ntec.tohid.ui.v3.rememberCountUp
import ir.vil3ntec.tohid.ui.v3.v3Pressable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/*
 *  تکه‌های کارتِ r4 — شکل همان کارتِ تأییدشده، رنگ فقط از توکن‌های VILL3N:
 *  کارتِ شیشه‌ای، قرصِ نقش روی سطحِ وارونه، سه کادرِ آمار که هر کدام درِ بخشِ
 *  خودش است، و نوارِ تیرهٔ اشتراک (سطحِ وارونه #0B1220 ⇐ #162235، قرصِ سفید).
 */

/** کارتِ شیشه‌ای: نام، شماره، نقش، سه کادرِ آمار، نوارِ تیرهٔ اشتراک */
@Composable
internal fun ProfileCard(
  name: String,
  contact: String,
  contactLtr: Boolean,
  role: String?,
  roleKind: AccountFormat.Role,
  doors: List<ProfileHomeModel.Door>,
  banner: ProfileHomeModel.Banner,
  onDoor: (String) -> Unit,
  onBanner: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val c = PhoneV3.colors
  val shape = RoundedCornerShape(PhoneV3.cardRadius)
  Column(
    modifier
      .fillMaxWidth()
      .shadow(18.dp, shape, clip = false, ambientColor = c.ink.copy(alpha = 0.10f), spotColor = c.ink.copy(alpha = 0.12f))
      .clip(shape)
      .background(c.cardGlass.copy(alpha = c.cardGlass.alpha * 0.8f))
      .border(1.dp, c.hairline, shape)
      .padding(start = 24.dp, end = 24.dp, top = 30.dp, bottom = 24.dp),
  ) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
      Column(Modifier.weight(1f)) {
        Text(name, style = V3Type.title, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (contact.isNotBlank()) {
          //  شماره و ایمیل چپ‌به‌راست خوانده می‌شوند، وگرنه `+93` ته خط می‌رود
          CompositionLocalProvider(LocalLayoutDirection provides if (contactLtr) LayoutDirection.Ltr else LocalLayoutDirection.current) {
            Text(contact, style = V3Type.subhead, color = c.inkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
          }
        }
      }
      if (role != null) {
        Spacer(Modifier.width(10.dp))
        RolePill(role, roleKind)
      }
    }
    Spacer(Modifier.height(16.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      doors.forEach { d -> DoorBox(d, Modifier.weight(1f)) { onDoor(d.route) } }
    }
    Spacer(Modifier.height(14.dp))
    PlanBanner(banner, onBanner)
  }
}

private fun roleIcon(kind: AccountFormat.Role): ImageVector = when (kind) {
  AccountFormat.Role.OWNER -> Icons.Outlined.Storefront
  AccountFormat.Role.MANAGER -> Icons.Outlined.ManageAccounts
  AccountFormat.Role.STAFF -> Icons.Outlined.Badge
  AccountFormat.Role.COURIER -> Icons.Outlined.LocalShipping
}

/** قرصِ نقش روی سطحِ وارونه (r4) — آیکنِ معنادارِ نقش + برچسب */
@Composable
private fun RolePill(role: String, kind: AccountFormat.Role) {
  val c = PhoneV3.colors
  Row(
    Modifier
      .clip(RoundedCornerShape(percent = 50))
      .background(c.black)
      .padding(start = 10.dp, end = 14.dp, top = 7.dp, bottom = 7.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(roleIcon(kind), contentDescription = null, tint = c.onBlack, modifier = Modifier.size(16.dp))
    Spacer(Modifier.width(6.dp))
    Text(role, style = V3Type.subhead, color = c.onBlack, maxLines = 1)
  }
}

/** کادرِ آمار — عددِ شمارنده و برچسب؛ زدن ⇒ همان بخش */
@Composable
private fun DoorBox(d: ProfileHomeModel.Door, modifier: Modifier, onClick: () -> Unit) {
  val c = PhoneV3.colors
  val shape = RoundedCornerShape(PhoneV3.groupRadius)
  val shown = rememberCountUp(d.count.toDouble())
  Column(
    modifier
      .v3Pressable(label = d.label, onClick = onClick)
      .height(78.dp)
      .shadow(6.dp, shape, clip = false, ambientColor = c.ink.copy(alpha = 0.08f), spotColor = c.ink.copy(alpha = 0.08f))
      .clip(shape)
      //  سطحِ درونی با مرز: روز سفید روی شیشه، شب `tile` روی کارت (VILL3N §۶)
      .background(if (c.isDark) c.tile else c.card)
      .border(1.dp, c.hairline, shape)
      .semantics(mergeDescendants = true) { contentDescription = "${d.label}: ${d.count}" },
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
  ) {
    Text(shown.toLong().toString(), style = V3Type.figure, color = c.ink, maxLines = 1)
    Text(d.label, style = V3Type.footnote, color = c.inkMuted, maxLines = 1)
  }
}

/** نوارِ تیرهٔ اشتراک: سطحِ وارونه با شیبِ نرم، ستاره، عنوان، زیرعنوان و قرصِ سفید */
@Composable
private fun PlanBanner(b: ProfileHomeModel.Banner, onClick: () -> Unit) {
  val c = PhoneV3.colors
  val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
  val stops = if (rtl) listOf(c.blackRaised, c.black) else listOf(c.black, c.blackRaised)
  Row(
    Modifier
      .fillMaxWidth()
      .heightIn(min = 60.dp)
      .clip(RoundedCornerShape(PhoneV3.groupRadius))
      .background(Brush.horizontalGradient(stops))
      .padding(start = 16.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(Icons.Filled.Star, contentDescription = null, tint = c.onBlack, modifier = Modifier.size(22.dp))
    Spacer(Modifier.width(12.dp))
    Column(Modifier.weight(1f)) {
      Text(b.title, style = V3Type.callout, color = c.onBlack, maxLines = 2, overflow = TextOverflow.Ellipsis)
      Text(b.subtitle, style = V3Type.caption, color = c.onBlackMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
    Spacer(Modifier.width(8.dp))
    Box(
      Modifier
        .v3Pressable(label = b.button, haptic = true, onClick = onClick)
        .heightIn(min = 36.dp)
        .clip(RoundedCornerShape(percent = 50))
        .background(c.onBlack)
        .padding(horizontal = 16.dp, vertical = 8.dp),
      contentAlignment = Alignment.Center,
    ) {
      Text(b.button, style = V3Type.subhead, color = c.black, maxLines = 1)
    }
  }
}

/** عکسِ حساب در حلقهٔ سفید؛ بی‌عکس آدمکِ خطی روی دایرهٔ نرم */
@Composable
internal fun ProfileAvatar(accountId: String, version: Int, size: Dp, modifier: Modifier = Modifier) {
  val context = LocalContext.current
  val c = PhoneV3.colors
  var bitmap by remember(accountId, version) { mutableStateOf<android.graphics.Bitmap?>(null) }
  LaunchedEffect(accountId, version) {
    bitmap = withContext(Dispatchers.IO) { runCatching { PhotoStore.loadAvatar(context, accountId) }.getOrNull() }
  }
  Box(
    modifier
      .size(size)
      .shadow(10.dp, CircleShape, clip = false, ambientColor = c.ink.copy(alpha = 0.12f), spotColor = c.ink.copy(alpha = 0.12f))
      .clip(CircleShape)
      .background(c.card)
      .border(1.dp, c.hairline, CircleShape)
      .padding(4.dp)
      .clip(CircleShape)
      .background(Brush.linearGradient(listOf(c.tile, lerp(c.tile, c.inkMuted, 0.28f)))),
    contentAlignment = Alignment.Center,
  ) {
    val image = bitmap
    if (image != null) {
      Image(image.asImageBitmap(), contentDescription = StrAccount.accountPhoto, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    } else {
      Icon(Icons.Outlined.Person, contentDescription = null, tint = c.inkMuted, modifier = Modifier.size(size * 0.46f))
    }
  }
}
