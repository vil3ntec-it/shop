package ir.vil3ntec.tohid.customer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.shopper.ShopperError
import ir.vil3ntec.tohid.core.shopper.ShopperText

/** نوارِ بالا: «برگشت» (آینه‌ای در راست‌چین)، سرخط و کارهای سمتِ دیگر. */
@Composable
fun TopBar(title: String, onBack: (() -> Unit)?, actions: @Composable RowScope.() -> Unit = {}) {
  Row(
    Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 4.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    if (onBack != null) {
      IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = ShopperText.back) }
    } else Spacer(Modifier.width(12.dp))
    Text(
      title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
      modifier = Modifier.weight(1f),
    )
    actions()
  }
}

@Composable
fun BusyBox(modifier: Modifier = Modifier.fillMaxSize()) {
  Box(modifier.padding(24.dp), contentAlignment = Alignment.Center) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
      CircularProgressIndicator()
      Text(ShopperText.loading, style = MaterialTheme.typography.bodyMedium)
    }
  }
}

/** خطای روشن با «دوباره بزنید» — هرگز صفحهٔ سفیدِ بی‌متن */
@Composable
fun ErrorBox(error: ShopperError, onRetry: (() -> Unit)?, modifier: Modifier = Modifier.fillMaxWidth()) {
  Column(modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Text(ShopperText.error(error), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyLarge)
    if (onRetry != null) OutlinedButton(onClick = onRetry) { Text(ShopperText.retry) }
  }
}

enum class Tone { INFO, WARN, ERROR, OK }

@Composable
fun Note(text: String, tone: Tone = Tone.INFO, modifier: Modifier = Modifier.fillMaxWidth()) {
  val c = MaterialTheme.colorScheme
  val (bg, fg) = when (tone) {
    Tone.ERROR -> c.errorContainer to c.onErrorContainer
    Tone.WARN -> c.tertiaryContainer to c.onTertiaryContainer
    Tone.OK -> c.primaryContainer to c.onPrimaryContainer
    Tone.INFO -> c.surfaceVariant to c.onSurfaceVariant
  }
  Surface(modifier, color = bg, contentColor = fg, shape = RoundedCornerShape(12.dp)) {
    Text(text, Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
  }
}

@Composable
fun SectionTitle(text: String) {
  Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
}

/** «برچسب ………… مبلغ» */
@Composable
fun MoneyRow(label: String, value: String, strong: Boolean = false) {
  Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
    val style = if (strong) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium
    Text(label, style = style, fontWeight = if (strong) FontWeight.Bold else null)
    Text(value, style = style, fontWeight = if (strong) FontWeight.Bold else null)
  }
}

/** دکمهٔ اصلیِ تمام‌عرض (هدفِ لمسِ ≥ ۴۸dp) */
@Composable
fun MainButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
  Button(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
    Text(text, style = MaterialTheme.typography.titleMedium)
  }
}

/** «− مقدار +» با نامِ دسترس‌پذیرِ هر دکمه (همان `qty.less`/`qty.more` ِ وب) */
@Composable
fun QtyStepper(qty: String, name: String, onLess: () -> Unit, onMore: () -> Unit, moreEnabled: Boolean = true) {
  Row(verticalAlignment = Alignment.CenterVertically) {
    OutlinedButton(
      onClick = onLess, modifier = Modifier.size(48.dp).semantics { contentDescription = ShopperText.less(name) },
      contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
    ) { Text("−", style = MaterialTheme.typography.titleLarge) }
    Text(qty, Modifier.padding(horizontal = 12.dp), style = MaterialTheme.typography.titleMedium)
    OutlinedButton(
      onClick = onMore, enabled = moreEnabled, modifier = Modifier.size(48.dp).semantics { contentDescription = ShopperText.more(name) },
      contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
    ) { Icon(Icons.Filled.Add, contentDescription = null) }
  }
}

/** عکسِ کوچکِ کالا یا جای خالیِ هم‌رنگ */
@Composable
fun Thumb(path: String?, size: Dp) {
  val m = Modifier.size(size).clip(RoundedCornerShape(10.dp))
  if (path == null) Box(m.background(MaterialTheme.colorScheme.surfaceVariant))
  else LocalPlatform.current.photo(path, m)
}

/** نوارِ پایینِ سبد: شمار و جمع، همیشه پیدا (یافتهٔ ۸ ِ ممیزی) */
@Composable
fun CartBar(count: Int, sum: Long, onClick: () -> Unit) {
  if (count <= 0) return
  Surface(
    color = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary,
    modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick),
  ) {
    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
      Icon(Icons.Filled.ShoppingCart, contentDescription = null)
      Spacer(Modifier.width(12.dp))
      Text(ShopperText.cartBar(count, sum), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
    }
  }
}

/** دکمهٔ کوچکِ «برچسب» (دسته‌ها) — برگزیده پُررنگ */
@Composable
fun Pill(text: String, selected: Boolean, onClick: () -> Unit) {
  val c = MaterialTheme.colorScheme
  Surface(
    color = if (selected) c.primary else c.surfaceVariant,
    contentColor = if (selected) c.onPrimary else c.onSurfaceVariant,
    shape = RoundedCornerShape(50),
    modifier = Modifier.heightIn(min = 40.dp).clip(RoundedCornerShape(50)).clickable(role = Role.Tab, onClick = onClick),
  ) {
    Box(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
      Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
  }
}
