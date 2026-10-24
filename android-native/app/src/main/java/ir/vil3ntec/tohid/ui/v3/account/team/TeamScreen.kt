package ir.vil3ntec.tohid.ui.v3.account.team

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.PersonRemove
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.StrAccount
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.PhoneV3Palette
import ir.vil3ntec.tohid.ui.theme.PhoneV3Roles
import ir.vil3ntec.tohid.ui.theme.V3ButtonKind
import ir.vil3ntec.tohid.ui.theme.V3Status
import ir.vil3ntec.tohid.ui.theme.V3Tone
import ir.vil3ntec.tohid.ui.v3.V3Button
import ir.vil3ntec.tohid.ui.v3.V3Collapse
import ir.vil3ntec.tohid.ui.v3.V3Divider
import ir.vil3ntec.tohid.ui.v3.V3EmptyState
import ir.vil3ntec.tohid.ui.v3.V3Fab
import ir.vil3ntec.tohid.ui.v3.V3Hue
import ir.vil3ntec.tohid.ui.v3.V3IconButton
import ir.vil3ntec.tohid.ui.v3.V3ListRow
import ir.vil3ntec.tohid.ui.v3.V3Message
import ir.vil3ntec.tohid.ui.v3.V3Scaffold
import ir.vil3ntec.tohid.ui.v3.V3Section
import ir.vil3ntec.tohid.ui.v3.V3Skeleton
import ir.vil3ntec.tohid.ui.v3.V3StatusPill
import ir.vil3ntec.tohid.ui.v3.V3SwipeAction
import ir.vil3ntec.tohid.ui.v3.V3Type
import ir.vil3ntec.tohid.ui.v3.account.AccountFormat
import ir.vil3ntec.tohid.ui.v3.account.DraftCodeReveal
import ir.vil3ntec.tohid.ui.v3.rememberV3Stagger
import ir.vil3ntec.tohid.ui.v3.v3Stagger

/** کارهای صفحهٔ کارمندان (مسیرها و فراخوان‌ها در `TeamRoute`) */
internal class TeamActions(
  val back: (() -> Unit)?,
  val signIn: () -> Unit,
  val copy: (String) -> Unit,
  val rotate: () -> Unit,
  val disable: () -> Unit,
  val member: (String) -> Unit,
  val remove: (String) -> Unit,
  val revoke: (String) -> Unit,
  val newCode: () -> Unit,
  val closeFresh: () -> Unit,
)

/**
 *  «کارمندان»: کدِ ثابتِ دکان (رونوشت، کدِ تازه، خاموش)، کارمندان (هر ردیف ⇐ صفحهٔ
 *  همان کارمند؛ کشیدن ⇐ «برداشتن»)، کدهای پیوستن (کشیدن ⇐ «باطل کن») و دکمهٔ
 *  شناورِ «کدِ پیوستنِ تازه».
 */
@Composable
internal fun TeamContent(team: TeamData, signedIn: Boolean, isOwner: Boolean, rotating: Boolean, actions: TeamActions) {
  val c = PhoneV3.colors
  val stagger = rememberV3Stagger(team.loaded)
  V3Scaffold(
    title = Str.staff,
    onBack = actions.back,
    fab = if (signedIn) ({ expanded -> V3Fab(Icons.Outlined.PersonAdd, StrAccount.newJoinCode, expanded, actions.newCode) }) else null,
  ) { pad ->
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
      Column(
        Modifier.widthIn(max = 640.dp).fillMaxWidth().verticalScroll(rememberScrollState())
          .padding(start = 16.dp, end = 16.dp, bottom = pad.calculateBottomPadding() + 72.dp),
      ) {
        Text(StrAccount.teamIntro, style = V3Type.footnote, color = c.inkMuted, modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp))
        when {
          !signedIn -> V3EmptyState(Icons.Outlined.Groups, StrAccount.signInFirst, body = StrAccount.teamSignInBody, action = Str.signIn, onAction = actions.signIn)
          !team.loaded -> { Spacer(Modifier.height(16.dp)); V3Skeleton(rows = 4) }
          else -> {
            team.error?.let { Spacer(Modifier.height(12.dp)); V3Message(V3Tone.ERROR, it) }
            team.fresh?.let { FreshCode(it, actions) }
            ShopCode(team, isOwner, rotating, actions)
            V3Section(TeamModel.staffTitle(team.members.size, team.maxMembers)) {
              if (team.members.isEmpty()) Note(StrAccount.noMembers)
              team.members.map(TeamModel::memberRow).forEachIndexed { i, m ->
                V3Collapse(visible = m.id !in team.leaving) {
                  Column {
                    if (i > 0) V3Divider(start = 64.dp)
                    MemberRowView(m, Modifier.v3Stagger(stagger, "m" + m.id, i), actions)
                  }
                }
              }
            }
            V3Section(StrAccount.joinCodes) {
              val rows = TeamModel.codeRows(team.codes)
              if (rows.isEmpty()) Note(StrAccount.noCodes)
              rows.forEachIndexed { i, code ->
                if (i > 0) V3Divider(start = 64.dp)
                V3ListRow(
                  title = code.hint + if (code.dead) " " + StrAccount.revokedMark else "",
                  subtitle = "${code.role} · ${code.uses} · ${code.expiry}",
                  icon = Icons.Outlined.Key,
                  hue = if (code.dead) null else V3Hue.BRAND,
                  chevron = false,
                  modifier = Modifier.v3Stagger(stagger, "c" + code.id, i + team.members.size),
                  endActions = if (code.dead) emptyList() else listOf(V3SwipeAction(Icons.Outlined.Block, StrAccount.revoke, V3ButtonKind.DANGER) { actions.revoke(code.id) }),
                )
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun MemberRowView(m: TeamModel.MemberRow, modifier: Modifier, actions: TeamActions) {
  val c = PhoneV3.colors
  V3ListRow(
    title = m.name,
    subtitle = m.contact.ifBlank { null },
    modifier = modifier,
    leading = {
      Box(Modifier.size(40.dp).clip(CircleShape).background(if (m.roleKind == AccountFormat.Role.OWNER) PhoneV3Palette.selected(c) else c.tile), contentAlignment = Alignment.Center) {
        Text(m.initial, style = V3Type.headline, color = if (m.active) PhoneV3Roles.brandText(c) else c.inkMuted)
      }
    },
    trailing = {
      Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        RolePill(m.role)
        V3StatusPill(if (m.active) V3Status.DELIVERED else V3Status.INACTIVE, if (m.active) StrAccount.stateActive else StrAccount.stateClosed)
      }
    },
    endActions = if (m.removable) listOf(V3SwipeAction(Icons.Outlined.PersonRemove, StrAccount.remove, V3ButtonKind.DANGER) { actions.remove(m.id) }) else emptyList(),
    onClick = { actions.member(m.id) },
  )
}

/** قرصِ آرامِ نقش (سطحِ درونی) — کنارِ قرصِ وضعیت */
@Composable
internal fun RolePill(text: String) {
  val c = PhoneV3.colors
  Text(
    text,
    style = V3Type.caption,
    color = c.inkMuted,
    maxLines = 1,
    modifier = Modifier.clip(CircleShape).background(c.tile).padding(horizontal = 9.dp, vertical = 4.dp),
  )
}

@Composable
private fun Note(text: String) {
  Text(text, style = V3Type.callout, color = PhoneV3.colors.inkMuted, modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp))
}

/** کدِ ثابتِ دکان: کدِ درشتِ تک‌پهنا با رونوشت، «کد تازه» و (صاحب دکان) «خاموش کردنِ کد» */
@Composable
private fun ShopCode(team: TeamData, isOwner: Boolean, rotating: Boolean, actions: TeamActions) {
  val c = PhoneV3.colors
  if (team.standingOff) {
    V3Section(StrAccount.shopCode, padding = PaddingValues(16.dp)) {
      Text(StrAccount.codeOffBody, style = V3Type.callout, color = c.inkMuted)
      Spacer(Modifier.height(12.dp))
      V3Button(StrAccount.turnOnNewCode, actions.rotate, Modifier.fillMaxWidth(), icon = Icons.Outlined.Refresh, loading = rotating)
    }
    return
  }
  if (team.standing.isBlank()) return
  V3Section(StrAccount.shopCode, footer = StrAccount.shopCodeHelper, padding = PaddingValues(16.dp)) {
    Row(
      Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(PhoneV3Palette.selected(c)).padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Text(
          team.standing,
          style = V3Type.figure.copy(fontFamily = FontFamily.Monospace),
          color = PhoneV3Roles.brandText(c),
          textAlign = TextAlign.Start,
          modifier = Modifier.weight(1f),
        )
      }
      V3IconButton(Icons.Outlined.ContentCopy, StrAccount.copyCode) { actions.copy(team.standing) }
    }
    Spacer(Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      V3Button(StrAccount.newShopCode, actions.rotate, Modifier.weight(1f), kind = V3ButtonKind.SECONDARY, icon = Icons.Outlined.Refresh, loading = rotating)
      if (isOwner) V3Button(StrAccount.disableCode, actions.disable, Modifier.weight(1f), kind = V3ButtonKind.NEUTRAL)
    }
  }
}

/** کدِ تازه‌ای که برگه‌اش پیش از «بستن» رفته — همین‌جا می‌ماند تا گم نشود */
@Composable
private fun FreshCode(code: String, actions: TeamActions) {
  V3Section(StrAccount.revealTitle, footer = StrAccount.revealBody, padding = PaddingValues(16.dp)) {
    DraftCodeReveal(code)
    Spacer(Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      V3Button(StrAccount.copyAction, { actions.copy(code) }, Modifier.weight(1f), icon = Icons.Outlined.ContentCopy, haptic = true)
      V3Button(Str.close, actions.closeFresh, Modifier.weight(1f), kind = V3ButtonKind.NEUTRAL)
    }
  }
}
