package ir.vil3ntec.tohid.ui.v3.online.delivery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.DeliveryDining
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.delivery.AutoAssign
import ir.vil3ntec.tohid.core.i18n.StrKit
import ir.vil3ntec.tohid.core.i18n.StrOnline
import ir.vil3ntec.tohid.core.model.OrderDto
import ir.vil3ntec.tohid.core.net.ApiResult
import ir.vil3ntec.tohid.data.repo.DeliveryRepository
import ir.vil3ntec.tohid.data.repo.DeliveryWire
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.v3.V3Button
import ir.vil3ntec.tohid.ui.v3.V3Chip
import ir.vil3ntec.tohid.ui.v3.V3Divider
import ir.vil3ntec.tohid.ui.v3.V3FieldKind
import ir.vil3ntec.tohid.ui.v3.V3Hue
import ir.vil3ntec.tohid.ui.v3.V3ListRow
import ir.vil3ntec.tohid.ui.v3.V3Section
import ir.vil3ntec.tohid.ui.v3.V3Sheet
import ir.vil3ntec.tohid.ui.v3.V3SuccessCheck
import ir.vil3ntec.tohid.ui.v3.V3TextField
import ir.vil3ntec.tohid.ui.v3.V3Type
import kotlinx.coroutines.launch

/**
 *  سپردنِ خودکار (`delivery/auto`، DLV-03): پیش‌نمایشِ نقشهٔ `AutoAssign` — چه سفارشی به چه پیکی،
 *  چه سفارشی جا نشد و چرا، کدام پیک خودکار نمی‌گیرد — با سقفِ کارِ هم‌زمان و کرایهٔ هر تحویل، و فقط
 *  با «تأیید» فرستاده می‌شود. اگر سرور میانِ کار «سقف پر شد» گفت، پیک‌ها تازه و باقی دوباره نقشه
 *  می‌شوند (تا سه دور) — همان کارِ پنجرهٔ پیشین.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AutoAssignSheet(orders: List<OrderDto>, d: DeskData, repo: DeliveryRepository, onDismiss: () -> Unit, onFinished: () -> Unit) {
  val c = PhoneV3.colors
  val scope = rememberCoroutineScope()
  val cands = remember(orders) { orders.map { AutoAssign.Candidate(it.id, it.orderNo, DeliveryModel.expected(it), it.createdAt) } }
  var maxLive by remember { mutableStateOf<Int?>(null) }
  var fare by remember { mutableStateOf("") }
  var busy by remember { mutableStateOf(false) }
  var done by remember { mutableStateOf<List<String>?>(null) }
  var failed by remember { mutableStateOf<List<String>>(emptyList()) }
  var left by remember { mutableStateOf<AutoAssign.Plan?>(null) }
  val plan = remember(cands, d.couriers, maxLive) { AutoAssign.plan(cands, d.couriers, maxLive) }
  val fareMinor = minorOf(fare)

  suspend fun run() {
    var couriers = d.couriers
    var remaining = cands
    val ok = ArrayList<String>()
    val bad = ArrayList<String>()
    var rounds = 0
    rounds@ while (remaining.isNotEmpty() && rounds < MAX_ROUNDS) {
      rounds++
      val p = AutoAssign.plan(remaining, couriers, maxLive)
      if (p.picks.isEmpty()) break@rounds
      var replan = false
      picks@ for (pick in p.picks) {
        val body = DeliveryWire.assignJson("shop_courier", courierId = pick.courierId, fareMinor = fareMinor ?: 0L)
        when (val r = repo.assign(pick.orderId, body, DeliveryRepository.newKey())) {
          is ApiResult.Success -> {
            ok += "#${pick.orderNo} ⇐ ${pick.courierName}"
            remaining = remaining.filter { it.orderId != pick.orderId }
          }
          is ApiResult.Failure -> {
            if (r.error.code == "cash_cap_exceeded") { replan = true; break@picks }
            bad += "#${pick.orderNo}: ${r.error.userMessage}"
            remaining = remaining.filter { it.orderId != pick.orderId }
          }
        }
      }
      if (!replan) break@rounds
      couriers = repo.couriers().valueOrNull() ?: break@rounds
    }
    val fresh = repo.couriers().valueOrNull() ?: couriers
    done = ok
    failed = bad
    left = AutoAssign.plan(remaining, fresh, maxLive)
  }

  V3Sheet(onDismiss = { if (done != null) onFinished(); onDismiss() }, title = StrOnline.dlAutoTitle) { closer ->
    val result = done
    if (result == null) {
      Text(StrOnline.dlAutoHelp, style = V3Type.footnote, color = c.inkMuted)
      Spacer(Modifier.height(14.dp))
      Text(StrOnline.dlMaxLive, style = V3Type.subhead, color = c.ink)
      Spacer(Modifier.height(8.dp))
      FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf<Int?>(null, 1, 2, 3, 5).forEach { n -> V3Chip(n?.toString() ?: StrOnline.dlNoLimit, maxLive == n, { maxLive = n }) }
      }
      Spacer(Modifier.height(14.dp))
      V3TextField(
        fare, { fare = it.take(DeliveryForms.NUMBER_MAX) }, StrOnline.dlFareEach, kind = V3FieldKind.MONEY,
        helper = StrOnline.dlFareEachHelp, error = if (fareMinor == null) StrOnline.dlBadNumber else null, modifier = Modifier.fillMaxWidth(),
      )
      PlanView(plan)
      Spacer(Modifier.height(20.dp))
      V3Button(
        if (plan.picks.isEmpty()) StrOnline.dlNothingToAssign else StrOnline.dlConfirmAuto(plan.picks.size),
        { busy = true; scope.launch { run(); busy = false } },
        Modifier.fillMaxWidth(),
        enabled = plan.picks.isNotEmpty() && fareMinor != null && !busy,
        loading = busy,
        haptic = true,
      )
    } else {
      V3SuccessCheck(result.isNotEmpty(), Modifier.align(Alignment.CenterHorizontally))
      Spacer(Modifier.height(8.dp))
      Text(StrOnline.dlAutoDone(result.size), style = V3Type.title, color = c.ink, modifier = Modifier.align(Alignment.CenterHorizontally))
      Lines(null, result.map { it to null }, Icons.Outlined.DeliveryDining, V3Hue.MINT)
      if (failed.isNotEmpty()) Lines(StrOnline.dlAutoFailed, failed.map { it to null }, Icons.Outlined.ErrorOutline, V3Hue.CORAL)
      left?.let { p -> if (p.unplaced.isNotEmpty() || p.blocked.isNotEmpty()) PlanView(p.copy(picks = emptyList())) }
      Spacer(Modifier.height(20.dp))
      V3Button(StrKit.close, { closer.close { onFinished() } }, Modifier.fillMaxWidth())
    }
  }
}

@Composable
private fun PlanView(p: AutoAssign.Plan) {
  if (p.picks.isNotEmpty()) Lines(StrOnline.dlSuggest, p.picks.map { "#${it.orderNo} ⇐ ${it.courierName}" to null }, Icons.Outlined.DeliveryDining, V3Hue.MINT)
  if (p.unplaced.isNotEmpty()) Lines(StrOnline.dlUnplaced, p.unplaced.map { "#${it.orderNo}" to it.reason }, Icons.Outlined.ErrorOutline, V3Hue.AMBER)
  if (p.blocked.isNotEmpty()) Lines(StrOnline.dlBlockedCouriers, p.blocked.map { it.name to it.reason }, Icons.Outlined.Block, V3Hue.CORAL)
}

@Composable
private fun Lines(title: String?, lines: List<Pair<String, String?>>, icon: androidx.compose.ui.graphics.vector.ImageVector, hue: V3Hue) {
  V3Section(title) {
    lines.forEachIndexed { i, (l, sub) ->
      if (i > 0) V3Divider(start = 64.dp)
      V3ListRow(l, subtitle = sub, icon = icon, hue = hue, subtitleLines = 3, chevron = false)
    }
  }
}

private const val MAX_ROUNDS = 3
