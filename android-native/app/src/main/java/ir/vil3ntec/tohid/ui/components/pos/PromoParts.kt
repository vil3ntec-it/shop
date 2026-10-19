package ir.vil3ntec.tohid.ui.components.pos

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.promo.PromoCore
import ir.vil3ntec.tohid.data.PromoEngine
import ir.vil3ntec.tohid.data.SalesEngine
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.ui.theme.Shape
import ir.vil3ntec.tohid.ui.theme.Shop
import ir.vil3ntec.tohid.ui.theme.Space

/*
 *  تخفیف‌های خودکار در صندوق (PRM-18): هر قاعدهٔ اعمال‌شده یک ردیف با نامش در
 *  سبد، ردیف‌های خلاصهٔ پرداخت، و کادرِ «کدِ تخفیف». فقط نمایش‌اند؛ حساب
 *  `PromoEngine` است و حال `PromoLive`.
 */

object PromoSummary {
  /**
   *  خلاصهٔ پرداخت با تخفیف‌های خودکار: «جمع» پیش از آن‌ها، هر قاعده یک ردیفِ
   *  منفی، بعد تخفیفِ دستی. بی تخفیف ⇒ همان `PosSummary.rows`.
   */
  fun rows(t: SalesEngine.Totals, o: PromoEngine.Outcome?): List<PosSummaryRow> =
    if (o == null || o.applied.isEmpty()) PosSummary.rows(t) else buildList {
      add(PosSummaryRow("جمع", o.gross))
      o.applied.forEach { add(PosSummaryRow(it.name, it.discount, negative = true)) }
      if (t.discount > 0) add(PosSummaryRow("تخفیف", t.discount, negative = true))
    }
}

/** ردیف‌های «تخفیف» با نامِ هر قاعده — تهِ فهرستِ سبد */
@Composable
fun PromoLines(applied: List<PromoEngine.Applied>, modifier: Modifier = Modifier) {
  if (applied.isEmpty()) return
  val c = Shop.colors
  Column(modifier.fillMaxWidth().padding(vertical = Space.xxs), verticalArrangement = Arrangement.spacedBy(Space.xxs)) {
    applied.forEach { a ->
      Row(
        Modifier
          .fillMaxWidth()
          .background(c.successTint, Shape.card)
          .padding(horizontal = Space.sm, vertical = Space.xs)
          .semantics { contentDescription = "تخفیفِ ${a.name}: ${money(a.discount)} افغانی" },
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Icon(Icons.Filled.LocalOffer, contentDescription = null, tint = c.success, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(Space.xs))
        Text(
          a.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
          color = c.success, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        Text("−${money(a.discount)} ؋", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = c.success)
      }
    }
  }
}

/**
 *  کادرِ «کدِ تخفیف» — فقط وقتی قاعدهٔ کددار هست. هر کدِ زده‌شده با وضعیتش
 *  (اعمال شد / پیدا نشد / …) و دکمهٔ برداشتن.
 */
@Composable
fun PromoCodeField(
  codes: List<PromoCore.CodeStatus>,
  entered: List<String>,
  onAdd: (String) -> Unit,
  onRemove: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val c = Shop.colors
  var text by remember { mutableStateOf("") }
  fun submit() {
    if (text.isNotBlank()) onAdd(text.trim())
    text = ""
  }
  Column(modifier.fillMaxWidth()) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        label = { Text("کدِ تخفیف") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { submit() }),
        modifier = Modifier.weight(1f),
      )
      Spacer(Modifier.width(Space.xs))
      OutlinedButton(onClick = { submit() }, enabled = text.isNotBlank(), modifier = Modifier.heightIn(min = 48.dp)) { Text("اعمال") }
    }
    entered.forEach { code ->
      val st = codes.firstOrNull { it.code == code }?.status ?: "unknown"
      val ok = st == "applied"
      Row(
        Modifier.fillMaxWidth().padding(top = Space.xxs)
          .background(if (ok) c.successTint else c.dangerTint, Shape.chip)
          .padding(start = Space.sm),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          "$code — ${PromoEngine.codeText(st)}", Modifier.weight(1f),
          style = MaterialTheme.typography.bodySmall, color = if (ok) c.success else c.danger,
        )
        IconButton(onClick = { onRemove(code) }) {
          Icon(Icons.Filled.Close, contentDescription = "برداشتنِ کدِ $code", tint = c.muted, modifier = Modifier.size(16.dp))
        }
      }
    }
  }
}
