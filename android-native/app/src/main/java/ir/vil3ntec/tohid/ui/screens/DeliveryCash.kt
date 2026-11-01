package ir.vil3ntec.tohid.ui.screens

import ir.vil3ntec.tohid.ui.v3.online.delivery.DeskData
import ir.vil3ntec.tohid.ui.v3.online.delivery.jalaliMonth
import ir.vil3ntec.tohid.ui.v3.online.delivery.fieldOf
import ir.vil3ntec.tohid.ui.v3.online.delivery.minorOf
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeliveryDining
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.Jalali
import ir.vil3ntec.tohid.core.delivery.CourierCash
import ir.vil3ntec.tohid.core.delivery.CourierRow
import ir.vil3ntec.tohid.core.delivery.DeliveryText
import ir.vil3ntec.tohid.core.delivery.DeskConfig
import ir.vil3ntec.tohid.core.delivery.ExternalCompany
import ir.vil3ntec.tohid.core.delivery.InvoiceCheck
import ir.vil3ntec.tohid.core.delivery.Settlement
import ir.vil3ntec.tohid.core.delivery.SettlementMath
import ir.vil3ntec.tohid.core.delivery.SettlementPage
import ir.vil3ntec.tohid.core.delivery.SettlementPreview
import ir.vil3ntec.tohid.core.delivery.Statement
import ir.vil3ntec.tohid.data.repo.DeliveryRepository
import ir.vil3ntec.tohid.data.repo.DeliveryWire
import ir.vil3ntec.tohid.isoMillis
import ir.vil3ntec.tohid.ui.components.common.PillTone
import ir.vil3ntec.tohid.ui.components.common.StatusPill
import ir.vil3ntec.tohid.ui.theme.Shop
import kotlinx.coroutines.launch

/* ============================ پیک‌ها و نقد ============================ */

/**
 *  دفترِ نقدِ هر پیک (DLV-11): سقف، در دست (گرفته و تسویه‌نشده)، گرفتنی از
 *  مأموریت‌های زنده، جای خالی و دلیلِ «خودکار نمی‌گیرد». تسویهٔ پایانِ روز
 *  (DLV-12) و دفترِ تسویه‌ها فقط برای صاحب/مدیر (`delivery.settle` در
 *  سرور = تأییدِ مدیر؛ تأییدکننده در سند ثبت می‌شود).
 */
@Composable
internal fun ColumnScope.CouriersTab(
  d: DeskData,
  cfg: DeskConfig,
  writable: Boolean,
  repo: DeliveryRepository,
  snackbar: SnackbarHostState,
  onChanged: () -> Unit,
) {
  val c = Shop.colors
  val scope = rememberCoroutineScope()
  var settleFor by remember { mutableStateOf<CourierRow?>(null) }
  var ledgerFor by remember { mutableStateOf<CourierRow?>(null) }
  var capFor by remember { mutableStateOf<CourierRow?>(null) }
  var reportOpen by remember { mutableStateOf(false) }
  var ratingsOpen by remember { mutableStateOf(false) }

  if (d.couriers.isEmpty()) {
    TohidEmptyState(
      title = "پیکی ثبت نشده",
      description = "از «کارمندان» یک کدِ دعوت با نقشِ پیک بسازید؛ پیک با همان کد واردِ برنامهٔ پیک می‌شود.",
      icon = Icons.Filled.DeliveryDining,
    )
    return
  }
  if (!cfg.perms.settle) {
    Text("تسویه و دفترِ تسویه‌ها فقط با صاحب یا مدیرِ دکان است.", style = MaterialTheme.typography.bodySmall, color = c.muted)
    Spacer(Modifier.height(6.dp))
  } else {
    //  RPT-15: گزارشِ نقدِ همهٔ پیک‌ها در یک بازه
    TohidSecondaryButton("گزارشِ نقدِ پیک‌ها", onClick = { reportOpen = true })
    Spacer(Modifier.height(6.dp))
  }
  //  DLV-18: امتیازِ تحویل‌ها — صاحب و مدیر، فقط با پرچمِ دکان
  if (cfg.ratings && cfg.perms.couriers) {
    TohidSecondaryButton(ir.vil3ntec.tohid.core.i18n.StrCourier.ratingsButton, onClick = { ratingsOpen = true })
    Spacer(Modifier.height(6.dp))
  }
  LazyColumn(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    items(d.couriers, key = { it.id }) { cr ->
      TohidCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(cr.name, Modifier.weight(1f), fontWeight = FontWeight.Bold, color = c.text)
          if (!cr.active) StatusPill("غیرفعال", PillTone.NEUTRAL)
          else if (CourierCash.overCap(cr)) StatusPill("بالای سقف", PillTone.DANGER)
          else if (CourierCash.atCap(cr)) StatusPill("به سقف رسیده", PillTone.WARNING)
          else StatusPill("آماده", PillTone.SUCCESS)
        }
        Spacer(Modifier.height(8.dp))
        CapBar(CourierCash.usePercent(cr), CourierCash.atCap(cr))
        Spacer(Modifier.height(8.dp))
        Row {
          Figure("در دست، تسویه‌نشده", afn(cr.cashHeldMinor), Modifier.weight(1f))
          Figure("گرفتنی از ${cr.liveCount} مأموریتِ زنده", afn(cr.liveExpectedMinor), Modifier.weight(1f))
          Figure("سقف · جای خالی", "${afn(cr.cashCapMinor)} · ${plainMoney(CourierCash.headroom(cr))}", Modifier.weight(1f))
        }
        CourierCash.autoBlockReason(cr)?.let {
          Spacer(Modifier.height(4.dp))
          Text(it, style = MaterialTheme.typography.bodySmall, color = c.warning)
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          if (cfg.perms.settle && writable) TohidButton("تسویهٔ پایانِ روز", onClick = { settleFor = cr })
          if (cfg.perms.settle) TohidSecondaryButton("دفترِ نقد", onClick = { ledgerFor = cr })
          if (cfg.perms.couriers && writable) TohidSecondaryButton("سقف و وضعیت", onClick = { capFor = cr })
        }
      }
    }
  }

  settleFor?.let { cr ->
    SettlementSheet(cr, repo, onDismiss = { settleFor = null }, onDone = { s ->
      settleFor = null
      onChanged()
      val gap = when {
        s.shortageMinor > 0 -> " · کسری ${afn(s.shortageMinor)}"
        s.excessMinor > 0 -> " · اضافه ${afn(s.excessMinor)}"
        else -> ""
      }
      scope.launch { snackbar.showSnackbar("تسویهٔ شمارهٔ ${s.settleNo} ثبت شد$gap") }
    })
  }
  ledgerFor?.let { cr -> LedgerSheet(cr, repo) { ledgerFor = null } }
  if (reportOpen) CashReportSheet(repo) { reportOpen = false }
  if (ratingsOpen) RatingsSheet(repo) { ratingsOpen = false }
  capFor?.let { cr ->
    CourierCapDialog(cr, repo, onDismiss = { capFor = null }, onDone = {
      capFor = null
      onChanged()
      scope.launch { snackbar.showSnackbar("${cr.name}: ذخیره شد") }
    })
  }
}

private fun plainMoney(minor: Long) = ir.vil3ntec.tohid.core.orders.OrderText.money(minor)

@Composable
private fun Figure(label: String, value: String, modifier: Modifier = Modifier) {
  val c = Shop.colors
  Column(modifier.padding(end = 6.dp)) {
    Text(label, style = MaterialTheme.typography.labelSmall, color = c.muted, maxLines = 2)
    Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = c.text)
  }
}

/** نوارِ پُر شدنِ سقفِ نقد — درصد کنارش نوشته است، نه فقط رنگ */
@Composable
private fun CapBar(percent: Int, full: Boolean) {
  val c = Shop.colors
  Row(verticalAlignment = Alignment.CenterVertically) {
    Box(Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(50)).background(c.surface2)) {
      Box(
        Modifier.fillMaxHeight().fillMaxWidth(percent.coerceIn(0, 100) / 100f)
          .clip(RoundedCornerShape(50)).background(if (full) c.warning else c.primary),
      )
    }
    Spacer(Modifier.width(8.dp))
    Text("$percent٪ سقف", style = MaterialTheme.typography.labelSmall, color = c.muted)
  }
}

@Composable
private fun SumLine(label: String, value: String, strong: Boolean = false, tone: androidx.compose.ui.graphics.Color? = null) {
  val c = Shop.colors
  Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
    Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = c.muted)
    Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = if (strong) FontWeight.Bold else FontWeight.Normal, color = tone ?: c.text)
  }
}

/**
 *  تسویهٔ پایانِ روز (DLV-12، S40، DEL-02): فهرستِ تحویل‌های تسویه‌نشده،
 *  شمارشِ مدیر، مقایسه با بدهیِ پیک، کسری/اضافه و اختلافِ مشتری جدا — با
 *  دلیل اگر اختلافی هست. سند با یک کلیدِ یکتا: زدنِ دوباره همان سند است.
 */
@Composable
private fun SettlementSheet(cr: CourierRow, repo: DeliveryRepository, onDismiss: () -> Unit, onDone: (Settlement) -> Unit) {
  val c = Shop.colors
  val scope = rememberCoroutineScope()
  var preview by remember { mutableStateOf<SettlementPreview?>(null) }
  var loadErr by remember { mutableStateOf<String?>(null) }
  var reload by remember { mutableStateOf(0) }
  var key by remember { mutableStateOf(DeliveryRepository.newKey()) }
  var farePaid by remember { mutableStateOf(false) }
  var handed by remember { mutableStateOf("") }
  var received by remember { mutableStateOf("") }
  var reason by remember { mutableStateOf("") }
  var note by remember { mutableStateOf("") }
  var busy by remember { mutableStateOf(false) }
  var err by remember { mutableStateOf<String?>(null) }

  LaunchedEffect(reload) {
    repo.settlementPreview(cr.id)
      .onSuccess { preview = it; loadErr = null; key = DeliveryRepository.newKey() }
      .onFailure { loadErr = it.userMessage }
  }

  TohidBottomSheet(title = "تسویهٔ پایانِ روز — ${cr.name}", onDismiss = onDismiss) {
    SheetBody {
      val p = preview
      if (p == null) {
        if (loadErr != null) TohidErrorState(title = "پیش‌نمایش نیامد", description = loadErr ?: "", onRetry = { reload++ })
        else TohidLoadingState(rows = 3)
        return@SheetBody
      }
      if (p.rows.isEmpty()) {
        Text("تحویلِ تسویه‌نشده‌ای نیست.", color = c.muted)
        return@SheetBody
      }
      val handedMinor = if (handed.isBlank()) null else minorOf(handed)
      val receivedMinor = if (received.isBlank()) null else minorOf(received)
      val t = SettlementMath.totals(p.rows, handedMinor ?: 0L, receivedMinor ?: 0L, farePaid)

      DeskSection("${p.rows.size} تحویل")
      p.rows.forEach { r ->
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
          Text("#${r.orderNo}", Modifier.width(64.dp), fontWeight = FontWeight.SemiBold, color = c.text)
          Text(deskStamp(r.deliveredAt), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = c.muted)
          Text(afn(r.cashCollectedMinor), style = MaterialTheme.typography.bodySmall, color = c.text)
          if (r.collectionDiffMinor != 0L) {
            Spacer(Modifier.width(6.dp))
            StatusPill("اختلاف ${plainMoney(r.collectionDiffMinor)}", PillTone.WARNING)
          }
        }
      }
      HorizontalDivider(color = c.border.copy(alpha = 0.5f))
      SumLine("موردِ انتظار از مشتری‌ها", afn(t.expectedCashMinor))
      SumLine("پیک گرفت", afn(t.collectedCashMinor))
      if (t.collectionDiffMinor != 0L) {
        SumLine("اختلافِ مشتری (جدا بررسی می‌شود)", afn(t.collectionDiffMinor), tone = c.warning)
      }
      SumLine("کرایه‌ها", afn(t.fareTotalMinor))
      Row(verticalAlignment = Alignment.CenterVertically) {
        Switch(checked = farePaid, onCheckedChange = { farePaid = it })
        Spacer(Modifier.width(8.dp))
        Text("کرایه را پیک از همین نقد برداشت", style = MaterialTheme.typography.bodySmall, color = c.text)
      }
      SumLine("پیک باید تحویل دهد", afn(t.handoverDueMinor), strong = true)
      TohidTextField(
        handed, { handed = it.take(14) }, label = "پیک می‌گوید تحویل داد (افغانی)",
        error = if (handed.isNotBlank() && handedMinor == null) "عدد درست نیست" else null,
        keyboardOptions = MONEY_KEYS,
      )
      TohidTextField(
        received, { received = it.take(14) }, label = "شمارشِ مدیر (افغانی) — لازم",
        error = if (received.isNotBlank() && receivedMinor == null) "عدد درست نیست" else null,
        keyboardOptions = MONEY_KEYS,
      )
      if (receivedMinor != null) {
        when {
          t.shortageMinor > 0 -> SumLine("کسری", afn(t.shortageMinor), strong = true, tone = c.danger)
          t.excessMinor > 0 -> SumLine("اضافه", afn(t.excessMinor), strong = true, tone = c.warning)
          else -> SumLine("اختلاف", "ندارد", strong = true, tone = c.success)
        }
        if (handedMinor != null && t.handedOverMismatch) {
          Text("گفتهٔ پیک با شمارشِ مدیر یکی نیست.", style = MaterialTheme.typography.bodySmall, color = c.warning)
        }
      }
      val needReason = receivedMinor != null && t.reasonRequired
      //  دلیل در «نقدِ من» ِ برنامهٔ پیک دیده می‌شود (`/api/courier/summary`)؛ یادداشت نه
      TohidTextField(
        reason, { reason = it.take(200) },
        label = if (needReason) "دلیلِ اختلاف (لازم)" else "دلیل (اختیاری)",
        supporting = "پیک این دلیل را در «نقدِ من» می‌بیند.",
      )
      TohidTextField(
        note, { note = it.take(300) }, label = "یادداشت (اختیاری)", singleLine = false,
        supporting = "فقط دکان می‌بیند.",
      )
      ErrorLine(err)
      val valid = receivedMinor != null && (handed.isBlank() || handedMinor != null) && (!needReason || reason.isNotBlank())
      TohidButton(
        "تأییدِ مدیر و بستنِ تسویه",
        busy = busy,
        enabled = valid && !busy,
        modifier = Modifier.fillMaxWidth(),
        onClick = {
          busy = true
          err = null
          scope.launch {
            val body = DeliveryWire.settleJson(
              clientKey = key, assignmentIds = p.rows.map { it.assignmentId },
              handedOverMinor = handedMinor ?: (receivedMinor ?: 0L), receivedMinor = receivedMinor ?: 0L,
              farePaidFromCash = farePaid, reason = reason, note = note,
            )
            repo.settle(cr.id, body)
              .onSuccess { onDone(it.settlement) }
              .onFailure { f ->
                err = f.userMessage
                //  فهرست عوض شده (تحویلِ تازه یا تسویه از دستگاهِ دیگر) ⇒ پیش‌نمایشِ تازه، کلیدِ تازه
                if (f.code == "settlement_changed" || f.code == "idempotency_key_reused") reload++
              }
            busy = false
          }
        },
      )
    }
  }
}

/** دفترِ نقدِ یک پیک: گرفته، تحویل‌داده، مانده، کسری — و تسویه‌های اخیر */
@Composable
private fun LedgerSheet(cr: CourierRow, repo: DeliveryRepository, onDismiss: () -> Unit) {
  val c = Shop.colors
  var page by remember { mutableStateOf<SettlementPage?>(null) }
  var err by remember { mutableStateOf<String?>(null) }
  var reload by remember { mutableStateOf(0) }
  LaunchedEffect(reload) {
    repo.settlements(cr.id, 30).onSuccess { page = it; err = null }.onFailure { err = it.userMessage }
  }
  TohidBottomSheet(title = "دفترِ نقدِ ${cr.name}", onDismiss = onDismiss) {
    SheetBody {
      val p = page
      if (p == null) {
        if (err != null) TohidErrorState(title = "دفتر نیامد", description = err ?: "", onRetry = { reload++ })
        else TohidLoadingState(rows = 3)
        return@SheetBody
      }
      val l = CourierCash.ledger(cr, p.sums)
      SumLine("نقدِ گرفته از مشتری (همه)", afn(l.collectedMinor))
      SumLine("تحویلِ دکان شد (شمارشِ مدیر)", afn(l.handedBackMinor))
      SumLine("مانده در دستِ پیک", afn(l.outstandingMinor), strong = true)
      SumLine("کرایه‌ها", afn(l.faresMinor))
      if (l.shortageMinor > 0) SumLine("کسریِ تسویه‌ها", afn(l.shortageMinor), tone = c.danger)
      if (l.excessMinor > 0) SumLine("اضافهٔ تسویه‌ها", afn(l.excessMinor), tone = c.warning)
      DeskSection("تسویه‌های اخیر")
      if (p.items.isEmpty()) Text("هنوز تسویه‌ای نیست.", style = MaterialTheme.typography.bodySmall, color = c.muted)
      p.items.forEach { s ->
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.surface2).padding(10.dp)) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text("شمارهٔ ${s.settleNo}", Modifier.weight(1f), fontWeight = FontWeight.SemiBold, color = c.text)
            Text(deskStamp(s.createdAt), style = MaterialTheme.typography.bodySmall, color = c.muted)
          }
          Text(
            "${s.assignmentsCount} تحویل · بدهی ${afn(s.handoverDueMinor)} · شمرده ${afn(s.receivedMinor)}",
            style = MaterialTheme.typography.bodySmall, color = c.text,
          )
          if (s.shortageMinor > 0) Text("کسری ${afn(s.shortageMinor)} — ${s.reason}", style = MaterialTheme.typography.bodySmall, color = c.danger)
          if (s.excessMinor > 0) Text("اضافه ${afn(s.excessMinor)} — ${s.reason}", style = MaterialTheme.typography.bodySmall, color = c.warning)
          if (s.collectionDiffMinor != 0L) {
            Text("اختلافِ مشتری ${afn(s.collectionDiffMinor)}", style = MaterialTheme.typography.bodySmall, color = c.warning)
          }
        }
      }
    }
  }
}

/** سقفِ نقد و فعال/غیرفعالِ پیک — فقط با دسترسیِ «پیک‌ها» (صاحب/مدیر) */
@Composable
private fun CourierCapDialog(cr: CourierRow, repo: DeliveryRepository, onDismiss: () -> Unit, onDone: () -> Unit) {
  val c = Shop.colors
  val scope = rememberCoroutineScope()
  var cap by remember { mutableStateOf(fieldOf(cr.cashCapMinor)) }
  var active by remember { mutableStateOf(cr.active) }
  var maxLive by remember { mutableStateOf(cr.maxLive?.toString().orEmpty()) }
  var busy by remember { mutableStateOf(false) }
  var err by remember { mutableStateOf<String?>(null) }
  val capMinor = if (cap.isBlank()) null else minorOf(cap)
  //  DLV-03: خالی = بی‌سقف؛ وگرنه ۱ تا ۵۰ (همان CHECKِ سرور)
  val liveCap = maxLive.trim().toIntOrNull()?.takeIf { it in 1..50 }
  val liveCapOk = maxLive.isBlank() || liveCap != null
  TohidBottomSheet(title = "سقف و وضعیتِ ${cr.name}", onDismiss = onDismiss) {
    SheetBody {
      TohidTextField(
        cap, { cap = it.take(14) }, label = "سقفِ نقد (افغانی)",
        supporting = "بیش از این نقد در دستِ پیک باشد، سفارشِ خودکار نمی‌گیرد",
        error = if (capMinor == null) "عدد لازم است" else null,
        keyboardOptions = MONEY_KEYS,
      )
      TohidTextField(
        maxLive, { maxLive = it.filter { ch -> ch.isDigit() }.take(2) }, label = "سقفِ کارِ هم‌زمان (اختیاری)",
        supporting = "این‌قدر مأموریتِ زنده داشته باشد، کارِ تازه به او سپرده نمی‌شود؛ خالی = بی‌سقف",
        error = if (!liveCapOk) "عددِ ۱ تا ۵۰" else null,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
      )
      Row(verticalAlignment = Alignment.CenterVertically) {
        Switch(checked = active, onCheckedChange = { active = it })
        Spacer(Modifier.width(8.dp))
        Text(if (active) "فعال — کارِ تازه می‌گیرد" else "غیرفعال — کارِ تازه نمی‌گیرد", color = c.text)
      }
      ErrorLine(err)
      TohidButton("ذخیره", busy = busy, enabled = capMinor != null && liveCapOk && !busy, modifier = Modifier.fillMaxWidth(), onClick = {
        busy = true
        err = null
        scope.launch {
          val body = DeliveryWire.courierUpdateJson(
            cashCapMinor = capMinor, active = active, maxLive = liveCap, clearMaxLive = liveCap == null && cr.maxLive != null,
          )
          repo.updateCourier(cr.id, body)
            .onSuccess { onDone() }
            .onFailure { err = it.userMessage }
          busy = false
        }
      })
    }
  }
}

/* ============================ شرکت‌های بیرونی ============================ */

/**
 *  شرکت‌های پیکِ بیرونی (DLV-19): از خودِ سپردن‌ها — نام و «کرایهٔ هر
 *  سفارش» (کرایهٔ آخرین سپردن). تطبیقِ صورت‌حساب با تحویل‌های همان شرکت در
 *  همان بازه، فقط برای صاحب/مدیر.
 */
@Composable
internal fun ColumnScope.CompaniesTab(d: DeskData, cfg: DeskConfig, repo: DeliveryRepository) {
  val c = Shop.colors
  var checkFor by remember { mutableStateOf<ExternalCompany?>(null) }
  if (d.companies.isEmpty()) {
    TohidEmptyState(
      title = "هنوز به شرکتِ بیرونی نسپرده‌اید",
      description = "در «سپردن» روشِ «شرکتِ بیرونی» را بزنید؛ نامِ شرکت و کرایهٔ هر سفارش همان‌جا ثبت می‌شود و این‌جا می‌آید.",
      icon = Icons.Filled.LocalShipping,
    )
    return
  }
  LazyColumn(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    items(d.companies, key = { it.name }) { co ->
      TohidCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(co.name, Modifier.weight(1f), fontWeight = FontWeight.Bold, color = c.text)
          if (co.live > 0) StatusPill("${co.live} زنده", PillTone.NEUTRAL)
        }
        Spacer(Modifier.height(4.dp))
        Text(
          "کرایهٔ هر سفارش (آخرین): ${afn(co.lastFareMinor)} · تحویل‌شده ${co.delivered} از ${co.assignments} · آخرین بار ${deskStamp(co.lastUsedAt)}",
          style = MaterialTheme.typography.bodySmall, color = c.muted,
        )
        if (cfg.perms.settle) {
          Spacer(Modifier.height(8.dp))
          TohidSecondaryButton("تطبیقِ صورت‌حساب", onClick = { checkFor = co })
        }
      }
    }
  }
  checkFor?.let { co -> InvoiceSheet(co, repo) { checkFor = null } }
}

private val KIND_LABEL = mapOf(
  InvoiceCheck.Kind.MATCHED to "درست",
  InvoiceCheck.Kind.AMOUNT_DIFF to "مبلغ فرق دارد",
  InvoiceCheck.Kind.NOT_DELIVERED to "در دفترِ ما تحویل نشده",
  InvoiceCheck.Kind.UNKNOWN to "در دفترِ ما نیست",
  InvoiceCheck.Kind.DUPLICATE to "دو بار آمده",
  InvoiceCheck.Kind.NOT_BILLED to "تحویل شده ولی در صورت‌حساب نیست",
)

/**
 *  خالصِ حساب با شرکت در یک جهتِ روشن: مثبت ⇒ دکان به شرکت می‌دهد؛ منفی ⇒
 *  شرکت (که نقدِ COD را گرفته) به دکان می‌دهد.
 */
@Composable
private fun NetLine(label: String, netToCompanyMinor: Long) {
  val c = Shop.colors
  when {
    netToCompanyMinor > 0 -> SumLine("$label: دکان به شرکت می‌دهد", afn(netToCompanyMinor), strong = true)
    netToCompanyMinor < 0 -> SumLine("$label: شرکت به دکان می‌دهد", afn(-netToCompanyMinor), strong = true, tone = c.warning)
    else -> SumLine(label, "بی‌حساب", strong = true, tone = c.success)
  }
}

@Composable
private fun InvoiceSheet(co: ExternalCompany, repo: DeliveryRepository, onDismiss: () -> Unit) {
  val c = Shop.colors
  var period by remember { mutableStateOf("this") }
  var stmt by remember { mutableStateOf<Statement?>(null) }
  var err by remember { mutableStateOf<String?>(null) }
  var total by remember { mutableStateOf("") }
  var count by remember { mutableStateOf("") }
  var lines by remember { mutableStateOf("") }
  var result by remember { mutableStateOf<InvoiceCheck.Result?>(null) }
  var parseErrors by remember { mutableStateOf<List<String>>(emptyList()) }

  LaunchedEffect(period) {
    stmt = null
    result = null
    val (from, to) = when (period) {
      "prev" -> jalaliMonth(-1)
      "30" -> (System.currentTimeMillis() - 30L * 86_400_000L) to (System.currentTimeMillis() + 60_000L)
      else -> jalaliMonth(0)
    }
    repo.statement(co.name, from, to).onSuccess { stmt = it; err = null }.onFailure { err = it.userMessage }
  }

  TohidBottomSheet(title = "تطبیقِ صورت‌حسابِ ${co.name}", onDismiss = onDismiss) {
    SheetBody {
      Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        TohidFilterChip("این ماه", selected = period == "this", onClick = { period = "this" })
        TohidFilterChip("ماهِ پیش", selected = period == "prev", onClick = { period = "prev" })
        TohidFilterChip("۳۰ روزِ اخیر", selected = period == "30", onClick = { period = "30" })
      }
      val s = stmt
      if (s == null) {
        if (err != null) Text(err ?: "", color = c.danger) else TohidLoadingState(rows = 2)
        return@SheetBody
      }
      val delivered = s.rows.filter { it.status == "delivered" }
      SumLine("سپرده به این شرکت", "${s.rows.size} سفارش")
      SumLine("تحویل‌شده در دفترِ ما", "${delivered.size} سفارش")
      SumLine("کرایهٔ تحویل‌شده‌ها (دکان به شرکت بدهکار)", afn(s.deliveredFareMinor), strong = true)
      //  نقدِ «پرداخت هنگامِ تحویل» که شرکت از مشتری گرفت — مالِ دکان است
      SumLine(
        "نقدی که شرکت از مشتری‌ها گرفت (شرکت به دکان بدهکار)", afn(s.deliveredCashMinor), strong = true,
        tone = if (s.deliveredCashMinor > 0) c.warning else null,
      )
      NetLine("خالص طبقِ دفترِ ما", s.deliveredFareMinor - s.deliveredCashMinor)
      val notDone = s.rows.count { it.status != "delivered" }
      if (notDone > 0) SumLine("ناموفق، برگشت یا هنوز در راه", "$notDone سفارش", tone = c.warning)
      if (s.truncated) Text("بیش از ۱۰۰۰ سفارش — بازهٔ کوتاه‌تری بگیرید.", style = MaterialTheme.typography.bodySmall, color = c.warning)
      if (s.rows.isNotEmpty()) {
        DeskSection("سفارش‌های سپرده به ${co.name}")
        s.rows.forEach { r ->
          Text(
            "#${r.orderNo}" + (if (r.externalRef.isNotEmpty()) " · ${r.externalRef}" else "") + " · ${DeliveryText.status(r.status)}" +
              " · کرایه ${afn(r.fareMinor)}" +
              (r.cashCollectedMinor?.takeIf { it > 0 }?.let { cash -> " · نقدِ گرفته ${afn(cash)}" } ?: "") +
              (if (r.status == "delivered" && r.expectedCollectMinor > 0 && (r.cashCollectedMinor ?: 0L) != r.expectedCollectMinor)
                " · موردِ انتظار ${afn(r.expectedCollectMinor)}" else ""),
            style = MaterialTheme.typography.bodySmall,
            color = if (r.status == "delivered") c.text else c.muted,
          )
        }
      }

      DeskSection("صورت‌حسابِ شرکت")
      val totalMinor = if (total.isBlank()) null else minorOf(total)
      val countN = count.trim().let { if (it.isEmpty()) null else ir.vil3ntec.tohid.core.orders.OrderDraft.latin(it).toIntOrNull() }
      TohidTextField(total, { total = it.take(16); result = null }, label = "مبلغِ کلِ صورت‌حساب (افغانی) — لازم",
        error = if (total.isNotBlank() && totalMinor == null) "عدد درست نیست" else null, keyboardOptions = MONEY_KEYS)
      TohidTextField(count, { count = it.take(6); result = null }, label = "شمارِ سفارش‌ها در صورت‌حساب (اختیاری)",
        error = if (count.isNotBlank() && countN == null) "عدد درست نیست" else null, keyboardOptions = MONEY_KEYS)
      TohidTextField(
        lines, { lines = it.take(20_000); result = null }, label = "ریزِ صورت‌حساب (اختیاری)",
        supporting = "هر خط: شمارهٔ رهگیری یا #شمارهٔ سفارش، و مبلغ — مثلاً «TRK-12 150»",
        singleLine = false,
      )
      parseErrors.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = c.danger) }
      TohidButton("بسنج", enabled = totalMinor != null && (count.isBlank() || countN != null), modifier = Modifier.fillMaxWidth(), onClick = {
        val parsed = InvoiceCheck.parse(lines)
        parseErrors = parsed.errors
        if (parsed.errors.isEmpty()) result = InvoiceCheck.check(s.rows, totalMinor ?: 0L, countN, parsed.lines)
      })

      result?.let { r ->
        HorizontalDivider(color = c.border.copy(alpha = 0.5f))
        when {
          r.ok -> StatusPill("صورت‌حساب با دفترِ ما یکی است", PillTone.SUCCESS)
          //  ⛔ کرایه درست است ولی نقدِ دکان نزدِ شرکت است ⇒ «همه را بپرداز» نیست
          r.invoiceMatches -> StatusPill("کرایه‌ها درست است — ولی نقدِ دکان نزدِ شرکت است؛ فقط خالص حساب شود", PillTone.WARNING)
          else -> StatusPill("اختلاف هست — پیش از پرداخت بررسی کنید", PillTone.WARNING)
        }
        NetLine("خالص طبقِ صورت‌حساب", r.netToCompanyByInvoiceMinor)
        SumLine(
          "مبلغ: صورت‌حساب − دفترِ ما", afn(r.amountDiffMinor), strong = true,
          tone = if (r.amountDiffMinor == 0L) c.success else c.danger,
        )
        r.countDiff?.let { SumLine("شمار: صورت‌حساب − دفترِ ما", it.toString(), tone = if (it == 0) c.success else c.warning) }
        r.items.forEach { item ->
          val tone = if (item.kind == InvoiceCheck.Kind.MATCHED) c.muted else c.warning
          Text(
            item.key + (item.orderNo?.let { n -> " (#$n)" } ?: "") + " — ${KIND_LABEL[item.kind]}" +
              (item.billedMinor?.let { b -> " · صورت‌حساب ${afn(b)}" } ?: "") +
              (item.ourFareMinor?.let { f -> " · دفترِ ما ${afn(f)}" } ?: "") +
              (item.cashMinor?.takeIf { it > 0 }?.let { cash -> " · نقدِ گرفته ${afn(cash)}" } ?: "") +
              (if (item.status.isNotEmpty() && item.status != "delivered") " · ${DeliveryText.status(item.status)}" else ""),
            style = MaterialTheme.typography.bodySmall, color = tone,
          )
        }
      }
    }
  }
}
