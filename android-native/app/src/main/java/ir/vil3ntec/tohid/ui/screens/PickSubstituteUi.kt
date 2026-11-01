package ir.vil3ntec.tohid.ui.screens

// i18n: سه‌زبانه

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.LocaleChoice
import ir.vil3ntec.tohid.LocaleText
import ir.vil3ntec.tohid.core.i18n.StrPick
import ir.vil3ntec.tohid.core.model.PickCustomerLinkDto
import ir.vil3ntec.tohid.core.model.PickLineDto
import ir.vil3ntec.tohid.core.model.PickSubstitutionDto
import ir.vil3ntec.tohid.core.model.PickSuggestionDto
import ir.vil3ntec.tohid.core.model.PickSuggestionsDto
import ir.vil3ntec.tohid.core.pick.PickSubstitute
import ir.vil3ntec.tohid.data.repo.PickingRepository
import ir.vil3ntec.tohid.ui.components.common.PillTone
import ir.vil3ntec.tohid.ui.components.common.StatusPill
import ir.vil3ntec.tohid.ui.theme.Shop
import kotlinx.coroutines.launch

/*
 *  جایگزینِ امن برای قلمِ «پیدا نشد» (PCK-05) و حلالِ کالای اصل (PCK-06) در برگهٔ جمع‌آور — پشتِ همان پرچمِ
 *  خاموشِ `shop.orders.picking`. پیشنهادها، قیمتِ کمتر و همهٔ قاعده‌ها از سرور است (`lib/pick-substitutes.js`):
 *  این‌جا فقط نشان دادن، اسکنِ بارکدِ جایگزین، پیوند/واتس‌اپ برای مشتری، «مشتری گفت …» پس از تماس، و حلال.
 */

private fun money(minor: Long): String = LocaleText.afghani(minor / 100.0, LocaleChoice.digits, LocaleChoice.symbol)

private fun stateText(sub: PickSubstitutionDto): String = when (PickSubstitute.shownState(sub.status, sub.expired, sub.willApply)) {
  "accepted" -> StrPick.subAccepted
  "rejected" -> StrPick.subRejected
  "withdrawn" -> StrPick.subWithdrawn
  "will_accept" -> StrPick.subWillAccept
  "will_reject" -> StrPick.subWillReject
  else -> StrPick.subPending
}

private fun halalText(v: String): String = when (PickSubstitute.halalOf(v)) {
  "halal" -> StrPick.halal
  "non_halal" -> StrPick.nonHalal
  else -> StrPick.halalUnknown
}

@Composable
internal fun PickSubstituteSheet(
  orderId: String,
  line: PickLineDto,
  subs: List<PickSubstitutionDto>,
  repo: PickingRepository,
  buzz: (Boolean) -> Unit,
  onClose: () -> Unit,
  onChanged: () -> Unit,
) {
  val c = Shop.colors
  val scope = rememberCoroutineScope()
  val uri = LocalUriHandler.current
  var data by remember(line.lineNo) { mutableStateOf<PickSuggestionsDto?>(null) }
  var error by remember(line.lineNo) { mutableStateOf<String?>(null) }
  var note by remember(line.lineNo) { mutableStateOf<Pair<Boolean, String>?>(null) }
  var busy by remember(line.lineNo) { mutableStateOf(false) }
  var scanFor by remember(line.lineNo) { mutableStateOf<String?>(null) }
  var scan by remember(line.lineNo) { mutableStateOf("") }
  var links by remember(line.lineNo) { mutableStateOf<PickCustomerLinkDto?>(null) }
  var reload by remember(line.lineNo) { mutableIntStateOf(0) }
  //  یک شناسهٔ رویداد برای هر کالا: پاسخِ گم‌شده ⇒ همان پیشنهاد، نه دومی
  val keys = remember(line.lineNo) { mutableStateMapOf<String, String>() }

  LaunchedEffect(line.lineNo, reload) {
    repo.suggestions(orderId, line.lineNo).onSuccess { data = it; error = null }.onFailure { error = it.userMessage }
  }

  fun propose(s: PickSuggestionDto, barcode: String) {
    if (busy) return
    busy = true
    note = null
    val id = keys.getOrPut(s.productId) { PickingRepository.newEventId() }
    scope.launch {
      repo.propose(orderId, id, line.lineNo, s.productId, barcode)
        .onSuccess { out -> buzz(true); links = out.customer; scanFor = null; scan = ""; note = true to StrPick.subSent; onChanged() }
        .onFailure { buzz(false); note = false to it.userMessage }
      busy = false
    }
  }

  fun decide(sub: PickSubstitutionDto, decision: String) {
    if (busy) return
    busy = true
    scope.launch {
      repo.decide(orderId, sub.id, decision).onSuccess { onChanged() }.onFailure { note = false to it.userMessage }
      busy = false
    }
  }

  TohidBottomSheet(title = StrPick.subTitle(line.name), onDismiss = onClose) {
    SheetBody {
      Text(StrPick.subHint, style = MaterialTheme.typography.bodySmall, color = c.muted)
      note?.let { (ok, text) -> Text(text, style = MaterialTheme.typography.bodySmall, color = if (ok) c.success else c.danger) }

      //  پیشنهادهای همین قلم و «مشتری گفت …» پس از تماس/واتس‌اپ
      for (sub in subs.filter { it.lineNo == line.lineNo && it.status != "withdrawn" }) {
        TohidCard {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text(StrPick.subLine(sub.name, money(sub.chargedUnitPriceMinor), money(sub.totalMinor)), Modifier.weight(1f), color = c.text)
            StatusPill(stateText(sub), if (sub.status == "accepted") PillTone.SUCCESS else PillTone.NEUTRAL)
          }
          if (sub.status == "pending" && !sub.expired) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              TohidButton(StrPick.subAccept, onClick = { decide(sub, "accept") }, enabled = !busy)
              TohidSecondaryButton(StrPick.subReject, onClick = { decide(sub, "reject") }, enabled = !busy)
            }
          }
        }
      }
      links?.let { l ->
        if (l.link.isNotBlank()) Text(l.link, style = MaterialTheme.typography.labelSmall, color = c.muted)
        if (l.whatsappUrl.isNotBlank()) TohidSecondaryButton(StrPick.subWhatsapp, onClick = { runCatching { uri.openUri(l.whatsappUrl) } })
      }

      val d = data
      when {
        d == null -> if (error != null) {
          ErrorLine(error)
          TohidSecondaryButton(StrPick.refresh, onClick = { reload++ })
        } else {
          TohidLoadingState(rows = 2)
        }
        !d.proposable -> Text(StrPick.subNone, style = MaterialTheme.typography.bodyMedium, color = c.muted)
        d.reason == "category_unknown" -> Text(StrPick.subNoCategory, style = MaterialTheme.typography.bodyMedium, color = c.muted)
        d.suggestions.isEmpty() -> Text(StrPick.subEmpty, style = MaterialTheme.typography.bodyMedium, color = c.muted)
        else -> for (s in d.suggestions) {
          TohidCard {
            Text(StrPick.subLine(s.name, money(s.chargedUnitPriceMinor), money(s.totalMinor)), fontWeight = FontWeight.SemiBold, color = c.text)
            Text(listOf(s.brand, halalText(s.halal)).filter { it.isNotBlank() }.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = c.muted)
            if (scanFor == s.productId) {
              fun send() { if (scan.isNotBlank()) propose(s, scan) }
              Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                  value = scan,
                  onValueChange = { scan = it.take(64) },
                  label = { Text(StrPick.subScan) },
                  singleLine = true,
                  keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                  keyboardActions = KeyboardActions(onSend = { send() }, onDone = { send() }, onGo = { send() }),
                  modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                TohidButton(StrPick.subPropose, onClick = { send() }, enabled = !busy && scan.isNotBlank())
              }
            } else {
              TohidSecondaryButton(StrPick.subPropose, enabled = !busy, onClick = {
                if (s.needsScan) { scanFor = s.productId; scan = "" } else propose(s, "")
              })
            }
          }
        }
      }

      //  PCK-06: حلالِ کالای اصل — انباردار/مدیر (`stock.adjust`؛ سرور بی اجازه ۴۰۳ می‌دهد)
      if (line.productId.isNotBlank()) {
        Text(StrPick.halalOf, style = MaterialTheme.typography.labelLarge, color = c.text)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          for (v in PickSubstitute.HALAL_CHOICES) {
            TohidFilterChip(label = halalText(v), selected = false, onClick = {
              scope.launch {
                repo.setHalal(line.productId, v)
                  .onSuccess { note = true to StrPick.halalSaved; reload++ }
                  .onFailure { note = false to it.userMessage }
              }
            })
          }
        }
      }
    }
  }
}
