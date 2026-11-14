package ir.vil3ntec.tohid.customer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.legal.Legal
import ir.vil3ntec.tohid.core.legal.LegalKind

/**
 *  «حریمِ خصوصی» و «شرایط و ضوابط» درونِ برنامهٔ خرید (اندروید و آیفون، همین پرونده) — همان منبعِ یکتای `core/legal` که برنامهٔ
 *  دکان و صفحهٔ عمومیِ سرور (`/legal/*`) نشان می‌دهند؛ بی اینترنت و بی صفحهٔ بیرونی. زبان همان زبانِ برنامه (دری از لایهٔ واژه).
 *  مسیرش در پرونده‌ای جداست چون `CustomerModel.kt` به سقفِ اندازه رسیده (زیرکلاسِ `Route` در همان بسته مجاز است).
 */
data class LegalRoute(val kind: LegalKind) : Route()

/** دو ردیفِ صفحهٔ «تنظیمات» */
@Composable
fun LegalLinks(model: CustomerModel) {
  val lang = model.lang.code
  SectionTitle(Legal.labels(lang).heading)
  LegalKind.entries.forEach { k ->
    OutlinedButton(onClick = { model.go(LegalRoute(k)) }, modifier = Modifier.fillMaxWidth()) { Text(Legal.doc(k, lang).short) }
  }
}

@Composable
fun LegalScreen(model: CustomerModel, kind: LegalKind) {
  val lang = model.lang.code
  val doc = Legal.doc(kind, lang)
  Column(Modifier.fillMaxSize()) {
    TopBar(doc.short, onBack = { model.back() })
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
      item(key = "head") {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
          Text(doc.title, style = MaterialTheme.typography.headlineSmall)
          Text(Legal.subtitle(doc, lang), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
      }
      items(doc.sections, key = { it.id }) { s ->
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Text(s.title, style = MaterialTheme.typography.titleMedium)
          s.paras.forEach { Text(legalText(it), style = MaterialTheme.typography.bodyMedium) }
          s.items.forEach {
            Row(Modifier.fillMaxWidth().padding(top = 2.dp)) {
              Text("•", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(end = 8.dp))
              Text(legalText(it), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            }
          }
        }
      }
    }
  }
}

/** `**پررنگ**` ⇒ پررنگ؛ پیوندِ نسبیِ سرور (`/portal/…`) این‌جا فقط نوشته است */
private fun legalText(s: String): AnnotatedString = buildAnnotatedString {
  for (span in Legal.spans(s)) {
    if (span.bold) withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(span.text) } else append(span.text)
  }
}
