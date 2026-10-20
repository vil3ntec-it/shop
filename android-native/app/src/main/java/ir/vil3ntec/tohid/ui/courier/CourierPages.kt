package ir.vil3ntec.tohid.ui.courier

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.ReportProblem
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.vil3ntec.tohid.core.courier.CourierBoardModel
import ir.vil3ntec.tohid.core.courier.CourierQueue
import ir.vil3ntec.tohid.core.courier.CourierSummary
import ir.vil3ntec.tohid.core.courier.MissionCard
import ir.vil3ntec.tohid.core.courier.QueueState
import ir.vil3ntec.tohid.fa
import ir.vil3ntec.tohid.formatMillis
import ir.vil3ntec.tohid.ui.screens.Motion
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.PhoneV3Tone
import ir.vil3ntec.tohid.ui.theme.PhoneV3UiDark

/*
 *  برگه‌های حالتِ پیک به شکلِ مرجعِ صاحب محصول (courier-ref.jpg، صفحهٔ چپ):
 *  سربرگ با نام و دکان و زنگ، جست‌وجوی «شمارهٔ سفارش…»، کارتِ تیرهٔ «مأموریتِ
 *  جاری»، میان‌برهای گرد، «کارهای اخیر» و نوارِ قرصیِ تیرهٔ پایین — به‌علاوهٔ
 *  «آمار»، «حساب» و «نقدِ من». همهٔ عددها از سرور یا صفِ همین گوشی‌اند.
 */

private fun lastSyncText(at: Long): String =
  if (at <= 0) "هنوز با دکان همگام نشده است" else "آخرین به‌روزرسانی: ${formatMillis(at)} · ${clock(at)}"

/** فشردنِ کاشی‌ها (بخشِ G) */
@Composable
private fun Modifier.pressable(onClick: () -> Unit, role: Role = Role.Button, enabled: Boolean = true): Modifier {
  val interaction = androidx.compose.runtime.remember { MutableInteractionSource() }
  val down by interaction.collectIsPressedAsState()
  val scale by animateFloatAsState(if (down && Motion.enabled) 0.94f else 1f, label = "tilePress")
  return this.graphicsLayer { scaleX = scale; scaleY = scale }
    .clickable(interactionSource = interaction, indication = LocalIndication.current, enabled = enabled, role = role, onClick = onClick)
}

@Composable
private fun Avatar(name: String, size: Int) {
  val p = PhoneV3.colors
  val ui = PhoneV3.ui
  Box(
    Modifier.size(size.dp).background(ui.success.bg, CircleShape).border(1.dp, p.hairline, CircleShape),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      name.trim().firstOrNull()?.toString() ?: "پ",
      style = v3Style((size * 0.42f).sp, FontWeight.Bold), color = ui.success.fg,
    )
  }
}

/* ============================== خانه ============================== */

@Composable
internal fun CourierHomeTab(
  name: String,
  summary: CourierSummary?,
  live: List<MissionCard>,
  finished: List<MissionCard>,
  queue: QueueState,
  query: String,
  onQuery: (String) -> Unit,
  seen: MutableSet<String>,
  onBell: () -> Unit,
  onOpen: (MissionCard) -> Unit,
  onMissions: () -> Unit,
  onDelivered: () -> Unit,
  onCash: () -> Unit,
  onCallShop: () -> Unit,
) {
  val p = PhoneV3.colors
  val bell = CourierBoardModel.bell(live, queue)
  val current = CourierBoardModel.current(live)
  val recent = CourierBoardModel.recent(live, finished)
  val results = CourierBoardModel.search(live + finished, query)
  LazyColumn(
    Modifier.fillMaxSize(),
    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = NavClearance),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    item(key = "head") {
      Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Avatar(name, 48)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
          Text(name.ifBlank { "پیک" }, style = v3Style(18.sp, FontWeight.Bold), color = p.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Storefront, contentDescription = null, tint = p.icon, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(4.dp))
            Text(
              summary?.shopName?.ifBlank { null } ?: "دکان",
              style = v3Style(13.sp), color = p.inkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
          }
        }
        RoundIconButton(Icons.Outlined.NotificationsNone, "پیام‌ها", onBell, dot = bell.total > 0)
      }
    }
    item(key = "search") {
      Column {
        Text("پیگیریِ سفارش", style = v3Style(16.sp, FontWeight.Bold), color = p.ink)
        Spacer(Modifier.height(8.dp))
        SearchPill(query, onQuery, "شمارهٔ سفارش…", Icons.Outlined.Search)
      }
    }
    if (query.isNotBlank()) {
      if (results.isEmpty()) {
        item(key = "none") {
          Text("سفارشی با این شماره یا نشانه پیدا نشد.", style = v3Style(14.sp), color = p.inkMuted, modifier = Modifier.padding(4.dp))
        }
      } else {
        itemsIndexed(results, key = { _, c -> "s-" + c.id }) { _, c ->
          RecentRow(c, CourierQueue.pendingFor(queue, c.id).isNotEmpty(), Modifier.animateItem()) { onOpen(c) }
        }
      }
    } else {
      item(key = "cur-h") { SectionHead("مأموریتِ جاری", if (live.size > 1) "همه" else null, onMissions) }
      item(key = "cur") {
        CurrentMissionCard(current, current?.let { CourierQueue.pendingFor(queue, it.id).isNotEmpty() } == true) {
          current?.let(onOpen)
        }
      }
      item(key = "short-h") { SectionHead("میان‌برها") }
      item(key = "short") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
          Shortcut(Icons.Outlined.Inventory2, "مأموریت‌ها", onMissions)
          Shortcut(Icons.Outlined.Payments, "نقدِ من", onCash)
          Shortcut(Icons.Outlined.Call, "تماس با دکان", onCallShop)
          Shortcut(Icons.Outlined.TaskAlt, "تحویل‌شده‌ها", onDelivered)
        }
      }
      item(key = "recent-h") { SectionHead("کارهای اخیر", if (recent.isNotEmpty()) "همه" else null, onMissions) }
      if (recent.isEmpty()) {
        item(key = "recent-empty") {
          Text("هنوز کاری ثبت نشده است.", style = v3Style(14.sp), color = p.inkMuted, modifier = Modifier.padding(4.dp))
        }
      } else {
        itemsIndexed(recent, key = { _, c -> "r-" + c.id }) { index, c ->
          val appear = rememberAppear("r-" + c.id, index, seen)
          RecentRow(
            c, CourierQueue.pendingFor(queue, c.id).isNotEmpty(),
            Modifier.animateItem().graphicsLayer { alpha = appear.value; translationY = (1f - appear.value) * 20.dp.toPx() },
          ) { onOpen(c) }
        }
      }
    }
  }
}

/** کارتِ تیرهٔ «مأموریتِ جاری» (مرجع: Current Tracking) — بی نقشهٔ ساختگی؛ نوارِ چهارپله */
@Composable
private fun CurrentMissionCard(c: MissionCard?, waiting: Boolean, onOpen: () -> Unit) {
  val p = PhoneV3.colors
  val dark = PhoneV3UiDark
  Column(
    Modifier.fillMaxWidth().v3Dark(p).clip(CardShape)
      .then(if (c != null) Modifier.pressable(onOpen) else Modifier)
      .padding(18.dp),
  ) {
    if (c == null) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(44.dp).background(p.blackRaised, CircleShape), contentAlignment = Alignment.Center) {
          Icon(Icons.Outlined.LocalShipping, contentDescription = null, tint = dark.primaryOnDark, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column {
          Text("مأموریتِ زنده‌ای ندارید", style = v3Style(16.sp, FontWeight.Bold), color = p.onBlack)
          Text("دکان که بسپارد، همین‌جا می‌آید.", style = v3Style(13.sp), color = p.onBlackMuted)
        }
      }
    } else {
      CurrentMissionBody(c, waiting)
    }
  }
}

@Composable
private fun CurrentMissionBody(c: MissionCard, waiting: Boolean) {
  val p = PhoneV3.colors
  val dark = PhoneV3UiDark
  Column {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Column(Modifier.weight(1f)) {
        Text("سفارش", style = v3Style(12.sp), color = p.onBlackMuted)
        Text("#${c.orderNo.fa()}", style = v3Style(24.sp, FontWeight.Bold), color = p.onBlack)
      }
      Box(Modifier.size(44.dp).background(dark.primaryOnDark, CircleShape), contentAlignment = Alignment.Center) {
        Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = "جزئیات", tint = dark.onPrimaryOnDark, modifier = Modifier.size(22.dp))
      }
    }
    Spacer(Modifier.height(12.dp))
    Text("مقصد", style = v3Style(12.sp), color = p.onBlackMuted)
    Row(verticalAlignment = Alignment.CenterVertically) {
      Icon(Icons.Outlined.Place, contentDescription = null, tint = dark.primaryOnDark, modifier = Modifier.size(18.dp))
      Spacer(Modifier.width(6.dp))
      Text(c.landmark.ifBlank { c.addressText.ifBlank { "—" } }, style = v3Style(15.sp, FontWeight.Medium), color = p.onBlack, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    Spacer(Modifier.height(10.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text("وضعیت", style = v3Style(12.sp), color = p.onBlackMuted)
      Spacer(Modifier.width(8.dp))
      TonePill(STATUS_LABEL[c.status] ?: c.status, statusTone(c.status, dark))
      if (waiting) { Spacer(Modifier.width(6.dp)); TonePill("در صف", dark.awaiting) }
    }
    if (reached(c.status) >= 0) {
      Spacer(Modifier.height(14.dp))
      StepTrack(c.status, onDark = true)
    }
  }
}

@Composable
private fun Shortcut(icon: ImageVector, label: String, onClick: () -> Unit) {
  val p = PhoneV3.colors
  val ui = PhoneV3.ui
  Column(
    Modifier.width(80.dp).clip(RoundedCornerShape(16.dp)).pressable(onClick).padding(vertical = 4.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Box(Modifier.size(56.dp).v3Card(p, CircleShape, elevation = 2.dp), contentAlignment = Alignment.Center) {
      Icon(icon, contentDescription = null, tint = ui.iconActive, modifier = Modifier.size(24.dp))
    }
    Spacer(Modifier.height(6.dp))
    Text(label, style = v3Style(12.sp, FontWeight.Medium), color = p.ink, textAlign = TextAlign.Center, maxLines = 1)
  }
}

/** ردیفِ «کارهای اخیر» (مرجع: Recent Activities) */
@Composable
internal fun RecentRow(c: MissionCard, waiting: Boolean, modifier: Modifier = Modifier, onOpen: () -> Unit) {
  val p = PhoneV3.colors
  val ui = PhoneV3.ui
  val done = c.status == "delivered"
  Row(
    modifier.fillMaxWidth().v3Card(p, GroupShape).clip(GroupShape).clickable(onClick = onOpen).padding(12.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(Modifier.size(48.dp).background(p.tile, RoundedCornerShape(14.dp)).border(1.dp, p.hairline, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
      Icon(if (done) Icons.Outlined.TaskAlt else Icons.Outlined.LocalShipping, contentDescription = null, tint = ui.iconActive, modifier = Modifier.size(24.dp))
    }
    Spacer(Modifier.width(12.dp))
    Column(Modifier.weight(1f)) {
      Text("#${c.orderNo.fa()}", style = v3Style(15.sp, FontWeight.Bold), color = p.ink)
      Text(
        c.landmark.ifBlank { if (ir.vil3ntec.tohid.core.courier.CourierFlow.isLive(c.status)) "—" else "پایان یافت · نشانی پاک شد" },
        style = v3Style(13.sp), color = p.inkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis,
      )
    }
    Spacer(Modifier.width(8.dp))
    Column(horizontalAlignment = Alignment.End) {
      TonePill(if (waiting) "در صف" else STATUS_LABEL[c.status] ?: c.status, if (waiting) ui.awaiting else statusTone(c.status, ui))
      val amount = c.cashCollectedMinor ?: c.expectedCollectMinor
      if (amount > 0) {
        Spacer(Modifier.height(4.dp))
        Text(afn(amount), style = v3Style(12.sp, FontWeight.Medium), color = p.inkMuted)
      }
    }
  }
}

/* ============================== نوارِ قرصیِ پایین ============================== */

private data class NavItem(val id: String, val label: String, val icon: ImageVector)

private val NAV = listOf(
  NavItem("home", "خانه", Icons.Outlined.Home),
  NavItem("route", "مسیر", Icons.Outlined.Route),
  NavItem("stats", "آمار", Icons.Outlined.BarChart),
  NavItem("account", "حساب", Icons.Outlined.Person),
)

/** نوارِ تیرهٔ شناور (مرجع): برگهٔ فعال = دایرهٔ سبزآبی + نام که با فنر باز می‌شود */
@Composable
internal fun CourierNavBar(current: String, onPick: (String) -> Unit, modifier: Modifier = Modifier) {
  val p = PhoneV3.colors
  val dark = PhoneV3UiDark
  Row(
    modifier.navigationBarsPadding().padding(bottom = 16.dp)
      .shadow(14.dp, PillShape, clip = false, ambientColor = p.black.copy(alpha = 0.25f), spotColor = p.black.copy(alpha = 0.25f))
      .background(p.black, PillShape).border(1.dp, p.blackRaised, PillShape)
      .height(64.dp).padding(horizontal = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    NAV.forEach { item ->
      val sel = item.id == current
      val capsule by animateColorAsState(if (sel) p.blackRaised else Color.Transparent, tween(motionMs(220)), label = "navCap")
      val circle by animateColorAsState(if (sel) dark.primaryOnDark else Color.Transparent, tween(motionMs(220)), label = "navCircle")
      Row(
        Modifier.height(48.dp).clip(PillShape).background(capsule)
          .pressable({ onPick(item.id) }, role = Role.Tab)
          .padding(start = 4.dp, end = if (sel) 14.dp else 4.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Box(Modifier.size(40.dp).background(circle, CircleShape), contentAlignment = Alignment.Center) {
          Icon(item.icon, contentDescription = item.label, tint = if (sel) dark.onPrimaryOnDark else p.onBlackMuted, modifier = Modifier.size(22.dp))
        }
        AnimatedVisibility(
          visible = sel,
          enter = if (Motion.enabled) expandHorizontally() + fadeIn() else fadeIn(snap()),
          exit = if (Motion.enabled) shrinkHorizontally() + fadeOut() else fadeOut(snap()),
          label = "navLabel",
        ) {
          Row {
            Spacer(Modifier.width(8.dp))
            Text(item.label, style = v3Style(14.sp, FontWeight.Bold), color = p.onBlack, maxLines = 1)
          }
        }
      }
    }
  }
}

/* ============================== آمار ============================== */

@Composable
internal fun CourierStatsTab(
  summary: CourierSummary?,
  week: Boolean,
  onWeek: (Boolean) -> Unit,
  lastSyncAt: Long,
  syncing: Boolean,
  onRefresh: () -> Unit,
  onCash: () -> Unit,
) {
  val p = PhoneV3.colors
  Column(
    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = 16.dp, bottom = NavClearance),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text("آمار", style = v3Style(28.sp, FontWeight.Bold), color = p.ink, modifier = Modifier.weight(1f))
      RoundIconButton(Icons.Outlined.Refresh, "به‌روز کردن", onRefresh, enabled = !syncing, spin = syncing)
    }
    TwoTabs("امروز" to null, "این هفته" to null, firstSelected = !week, onSelect = { onWeek(!it) })
    if (summary == null) {
      EmptyCard(
        "آمار هنوز از دکان نیامده",
        "به اینترنت که وصل شوید، شمارِ تحویل‌ها و نقدِ گرفته‌شده این‌جا می‌آید.",
        if (syncing) null else onRefresh,
      )
    } else {
      StatsBody(summary, week, lastSyncAt, onCash)
    }
  }
}

@Composable
private fun StatsBody(summary: CourierSummary, week: Boolean, lastSyncAt: Long, onCash: () -> Unit) {
  val p = PhoneV3.colors
  Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
    val s = if (week) summary.week else summary.today
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
      StatTile("تحویل‌شده", s.delivered.fa(), Icons.Outlined.TaskAlt, Modifier.weight(1f))
      StatTile("ناموفق / برگشتی", s.failed.fa(), Icons.Outlined.ReportProblem, Modifier.weight(1f))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
      StatTile("نقدِ گرفته‌شده", afn(s.collectedCashMinor), Icons.Outlined.Payments, Modifier.weight(1f))
      StatTile("کرایهٔ من", afn(s.fareMinor), Icons.Outlined.AccountBalanceWallet, Modifier.weight(1f))
    }
    Column(Modifier.fillMaxWidth().v3Dark(p).padding(18.dp)) {
      Text("نقدِ در دستِ شما", style = v3Style(13.sp), color = p.onBlackMuted)
      Text(afn(summary.cashInHandMinor), style = v3Style(26.sp, FontWeight.Bold), color = p.onBlack)
      Spacer(Modifier.height(12.dp))
      V3PrimaryButton("دیدنِ نقدِ من", onCash, Modifier.fillMaxWidth(), onDark = true, icon = Icons.Outlined.Payments)
    }
    Text(
      (if (week) "از شنبهٔ همین هفته" else "از آغازِ امروز") + " · " + lastSyncText(lastSyncAt),
      style = v3Style(12.sp), color = p.inkMuted, modifier = Modifier.padding(horizontal = 4.dp),
    )
  }
}

@Composable
private fun StatTile(label: String, value: String, icon: ImageVector, modifier: Modifier) {
  val p = PhoneV3.colors
  val ui = PhoneV3.ui
  Column(modifier.v3Card(p).padding(16.dp)) {
    Box(Modifier.size(40.dp).background(ui.success.bg, CircleShape), contentAlignment = Alignment.Center) {
      Icon(icon, contentDescription = null, tint = ui.iconActive, modifier = Modifier.size(22.dp))
    }
    Spacer(Modifier.height(10.dp))
    Crossfade(targetState = value, animationSpec = tween(motionMs(260)), label = "statValue") { v ->
      Text(v, style = v3Style(20.sp, FontWeight.Bold), color = p.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    Text(label, style = v3Style(13.sp), color = p.inkMuted, maxLines = 1)
  }
}

/* ============================== حساب ============================== */

@Composable
internal fun CourierAccountTab(
  name: String,
  summary: CourierSummary?,
  syncing: Boolean,
  waiting: Int,
  lastError: String?,
  lastSyncAt: Long,
  onRefresh: () -> Unit,
  onCallShop: () -> Unit,
  onCash: () -> Unit,
  onSignOut: () -> Unit,
) {
  val p = PhoneV3.colors
  val ui = PhoneV3.ui
  Column(
    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = 16.dp, bottom = NavClearance),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    Text("حساب", style = v3Style(28.sp, FontWeight.Bold), color = p.ink)
    Column(Modifier.fillMaxWidth().v3Card(p).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
      Avatar(name, 72)
      Spacer(Modifier.height(10.dp))
      Text(name.ifBlank { "پیک" }, style = v3Style(20.sp, FontWeight.Bold), color = p.ink)
      Text(summary?.shopName?.ifBlank { null } ?: "دکان", style = v3Style(14.sp), color = p.inkMuted)
      Spacer(Modifier.height(8.dp))
      TonePill("پیک", ui.preparing)
    }
    Column(Modifier.fillMaxWidth().v3Card(p).padding(horizontal = 16.dp, vertical = 4.dp)) {
      val phone = summary?.shopPhone.orEmpty()
      AccountRow(Icons.Outlined.Call, "تماس با دکان", phone.ifBlank { "ثبت نشده" }, ltr = phone.isNotBlank(), onClick = onCallShop)
      AccountRow(Icons.Outlined.Payments, "نقدِ من", summary?.let { afn(it.cashInHandMinor) } ?: "—", onClick = onCash)
      val syncText = when {
        syncing -> "در حالِ فرستادن…"
        waiting > 0 -> "${waiting.fa()} کار در صف"
        lastError != null -> "وصل نیست"
        else -> "همه رسید"
      }
      AccountRow(Icons.Outlined.Sync, "همگام‌سازی", syncText, onClick = onRefresh, divider = false)
    }
    Text(lastSyncText(lastSyncAt), style = v3Style(12.sp), color = p.inkMuted, modifier = Modifier.padding(horizontal = 4.dp))
    V3SecondaryButton(
      "خروج از حسابِ پیک", onSignOut, Modifier.fillMaxWidth(),
      icon = Icons.AutoMirrored.Outlined.Logout, tone = ButtonTone.NEGATIVE,
    )
  }
}

@Composable
private fun AccountRow(icon: ImageVector, label: String, value: String, ltr: Boolean = false, divider: Boolean = true, onClick: () -> Unit) {
  val p = PhoneV3.colors
  val ui = PhoneV3.ui
  Row(
    Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(GroupShape).clickable(onClick = onClick).padding(vertical = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(icon, contentDescription = null, tint = ui.iconActive, modifier = Modifier.size(22.dp))
    Spacer(Modifier.width(12.dp))
    Text(label, style = v3Style(15.sp, FontWeight.Medium), color = p.ink, modifier = Modifier.weight(1f))
    Text(
      value,
      style = v3Style(14.sp).copy(textDirection = if (ltr) TextDirection.Ltr else TextDirection.Content),
      color = p.inkMuted, maxLines = 1,
    )
    Spacer(Modifier.width(4.dp))
    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = p.chevron, modifier = Modifier.size(20.dp))
  }
  if (divider) Box(Modifier.fillMaxWidth().padding(start = 34.dp).height(1.dp).background(p.hairline))
}

/* ============================== «نقدِ من» ============================== */

@Composable
internal fun CourierCashPage(summary: CourierSummary?, lastSyncAt: Long, syncing: Boolean, onBack: () -> Unit, onRefresh: () -> Unit) {
  val p = PhoneV3.colors
  val ui = PhoneV3.ui
  Column(Modifier.fillMaxSize()) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
      RoundIconButton(Icons.AutoMirrored.Outlined.ArrowBack, "بازگشت", onBack)
      Text("نقدِ من", style = v3Style(18.sp, FontWeight.Bold), color = p.ink, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
      RoundIconButton(Icons.Outlined.Refresh, "به‌روز کردن", onRefresh, enabled = !syncing, spin = syncing)
    }
    if (summary == null) {
      Box(Modifier.padding(16.dp)) {
        EmptyCard("هنوز از دکان نیامده", "به اینترنت که وصل شوید، نقدِ در دست و تسویه‌ها این‌جا می‌آید.", if (syncing) null else onRefresh)
      }
    } else {
      CashList(summary, lastSyncAt)
    }
  }
}

@Composable
private fun CashList(summary: CourierSummary, lastSyncAt: Long) {
  val p = PhoneV3.colors
  val ui = PhoneV3.ui
    LazyColumn(
      Modifier.fillMaxSize(),
      contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 28.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      item(key = "hero") { CashHero(summary) }
      item(key = "note") {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
          Icon(Icons.Outlined.Info, contentDescription = null, tint = p.icon, modifier = Modifier.size(16.dp))
          Spacer(Modifier.width(6.dp))
          Text("تسویه را دکان ثبت می‌کند؛ این‌جا فقط دیدن است. " + lastSyncText(lastSyncAt), style = v3Style(12.sp), color = p.inkMuted)
        }
      }
      item(key = "un-h") { SectionHead("تحویل‌شده، هنوز تسویه‌نشده") }
      if (summary.unsettled.isEmpty()) {
        item(key = "un-empty") { CalmNote("نقدِ تسویه‌نشده‌ای نزدِ شما نیست.") }
      } else {
        item(key = "un") {
          Column(Modifier.fillMaxWidth().v3Card(p, GroupShape).padding(horizontal = 16.dp, vertical = 4.dp)) {
            summary.unsettled.forEachIndexed { i, u ->
              Row(Modifier.fillMaxWidth().heightIn(min = 54.dp).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("#${u.orderNo.fa()}", style = v3Style(14.sp, FontWeight.Bold), color = p.ink, modifier = Modifier.width(64.dp))
                Text("${formatMillis(u.deliveredAt)} · ${clock(u.deliveredAt)}", style = v3Style(12.sp), color = p.inkMuted, modifier = Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End) {
                  Text(afn(u.cashCollectedMinor), style = v3Style(14.sp, FontWeight.Bold), color = p.ink)
                  if (u.cashCollectedMinor != u.expectedCollectMinor) {
                    Text("از ${afn(u.expectedCollectMinor)}", style = v3Style(11.sp), color = ui.awaiting.fg)
                  }
                }
              }
              if (i < summary.unsettled.lastIndex) Box(Modifier.fillMaxWidth().height(1.dp).background(p.hairline))
            }
          }
        }
      }
      item(key = "st-h") { SectionHead("تاریخچهٔ تسویه") }
      if (summary.settlements.isEmpty()) {
        item(key = "st-empty") { CalmNote("هنوز تسویه‌ای ثبت نشده است.") }
      } else {
        itemsIndexed(summary.settlements, key = { _, s -> "st-${s.settleNo}" }) { _, s ->
          Column(Modifier.fillMaxWidth().v3Card(p, GroupShape).padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Text("تسویهٔ #${s.settleNo.fa()}", style = v3Style(15.sp, FontWeight.Bold), color = p.ink, modifier = Modifier.weight(1f))
              Text(formatMillis(s.at), style = v3Style(12.sp), color = p.inkMuted)
            }
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
              Text("${s.assignmentsCount.fa()} سفارش · دکان گرفت", style = v3Style(13.sp), color = p.inkMuted, modifier = Modifier.weight(1f))
              Text(afn(s.receivedMinor), style = v3Style(15.sp, FontWeight.Bold), color = p.ink)
            }
            Spacer(Modifier.height(8.dp))
            Row {
              when {
                s.shortageMinor > 0 -> TonePill("کسری: ${afn(s.shortageMinor)}", ui.cancelled)
                s.excessMinor > 0 -> TonePill("اضافه: ${afn(s.excessMinor)}", ui.awaiting)
                else -> TonePill("بی اختلاف", ui.success)
              }
            }
          }
        }
      }
    }
  }

@Composable
private fun CashHero(s: CourierSummary) {
  val p = PhoneV3.colors
  val dark = PhoneV3UiDark
  val use = CourierBoardModel.capUse(s)
  val fill by animateFloatAsState(use, if (Motion.enabled) tween(600, delayMillis = 120) else snap(), label = "capUse")
  Column(Modifier.fillMaxWidth().v3Dark(p).padding(18.dp)) {
    Text("نقدِ در دستِ شما", style = v3Style(13.sp), color = p.onBlackMuted)
    Text(afn(s.cashInHandMinor), style = v3Style(30.sp, FontWeight.Bold), color = p.onBlack)
    Spacer(Modifier.height(14.dp))
    Box(Modifier.fillMaxWidth().height(8.dp).background(p.blackRaised, PillShape)) {
      Box(
        Modifier.fillMaxHeight().fillMaxWidth(fill)
          .background(if (use >= 0.85f) dark.awaiting.fg else dark.primaryOnDark, PillShape),
      )
    }
    Spacer(Modifier.height(6.dp))
    Text(
      if (s.cashCapMinor > 0) "پُریِ سقف (در دست + در راه): ${(use * 100).toInt().fa()}٪" else "سقفی برای شما تعیین نشده است",
      style = v3Style(12.sp), color = p.onBlackMuted,
    )
    Spacer(Modifier.height(14.dp))
    Row(Modifier.fillMaxWidth()) {
      CashFigure("سقف", afn(s.cashCapMinor), Modifier.weight(1f))
      CashFigure("در راه", afn(s.liveExpectedMinor), Modifier.weight(1f))
      CashFigure("جای خالی", afn(s.headroomMinor), Modifier.weight(1f))
    }
  }
}

@Composable
private fun CashFigure(label: String, value: String, modifier: Modifier) {
  val p = PhoneV3.colors
  Column(modifier) {
    Text(label, style = v3Style(12.sp), color = p.onBlackMuted)
    Text(value, style = v3Style(13.sp, FontWeight.Bold), color = p.onBlack, maxLines = 1, overflow = TextOverflow.Ellipsis)
  }
}

@Composable
private fun CalmNote(text: String) {
  val p = PhoneV3.colors
  Text(
    text, style = v3Style(14.sp), color = p.inkMuted,
    modifier = Modifier.fillMaxWidth().v3Card(p, GroupShape).padding(16.dp),
  )
}

/* ============================== زنگ ============================== */

/** پیام‌های واقعیِ همین گوشی: کارِ تازه، پس‌خواستنِ دکان، کارِ ثبت‌نشده */
@Composable
internal fun CourierBellSheet(
  live: List<MissionCard>,
  queue: QueueState,
  onDismiss: () -> Unit,
  onNewMissions: () -> Unit,
  onOpen: (MissionCard) -> Unit,
  onRejected: () -> Unit,
) {
  val p = PhoneV3.colors
  val ui = PhoneV3.ui
  val b = CourierBoardModel.bell(live, queue)
  V3Sheet(onDismiss) {
    SheetTitle("پیام‌ها")
    Spacer(Modifier.height(12.dp))
    if (b.total == 0) {
      Text("پیامِ تازه‌ای نیست.", style = v3Style(15.sp), color = p.inkMuted)
    }
    if (b.newMissions > 0) {
      BellRow(Icons.Outlined.Inventory2, "${b.newMissions.fa()} کارِ تازه — قبول یا رد", ui.awaiting, onNewMissions)
    }
    live.filter { it.recallRequested }.forEach { c ->
      BellRow(Icons.Outlined.Info, "دکان سفارشِ #${c.orderNo.fa()} را پس خواسته است", ui.cancelled) { onOpen(c) }
    }
    if (b.rejectedEvents > 0) {
      BellRow(Icons.Outlined.ReportProblem, "${b.rejectedEvents.fa()} کار ثبت نشد — ببینید", ui.cancelled, onRejected)
    }
    Spacer(Modifier.height(16.dp))
    V3SecondaryButton("بستن", onDismiss, Modifier.fillMaxWidth())
  }
}

@Composable
private fun BellRow(icon: ImageVector, text: String, tone: PhoneV3Tone, onClick: () -> Unit) {
  val p = PhoneV3.colors
  Row(
    Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(GroupShape).background(p.tile).border(1.dp, p.hairline, GroupShape)
      .clickable(onClick = onClick).padding(12.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(Modifier.size(36.dp).background(tone.bg, CircleShape), contentAlignment = Alignment.Center) {
      Icon(icon, contentDescription = null, tint = tone.fg, modifier = Modifier.size(20.dp))
    }
    Spacer(Modifier.width(12.dp))
    Text(text, style = v3Style(15.sp, FontWeight.Medium), color = p.ink, modifier = Modifier.weight(1f))
    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = p.chevron, modifier = Modifier.size(20.dp))
  }
}
