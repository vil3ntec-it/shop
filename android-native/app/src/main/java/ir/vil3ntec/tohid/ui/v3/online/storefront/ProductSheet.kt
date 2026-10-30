package ir.vil3ntec.tohid.ui.v3.online.storefront

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrKit
import ir.vil3ntec.tohid.core.i18n.StrOnline
import ir.vil3ntec.tohid.core.storefront.StorefrontOwner
import ir.vil3ntec.tohid.core.storefront.StorefrontSpecs
import ir.vil3ntec.tohid.data.repo.StorefrontProductDto
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.V3Tone
import ir.vil3ntec.tohid.ui.v3.V3Button
import ir.vil3ntec.tohid.ui.v3.V3FieldKind
import ir.vil3ntec.tohid.ui.v3.V3Message
import ir.vil3ntec.tohid.ui.v3.V3ProductThumb
import ir.vil3ntec.tohid.ui.v3.V3Sheet
import ir.vil3ntec.tohid.ui.v3.V3TextField
import ir.vil3ntec.tohid.ui.v3.V3Type
import ir.vil3ntec.tohid.ui.v3.v3Shake

/** آنچه برگهٔ کالا ذخیره می‌کند (همان چهار مقدارِ پنجرهٔ پیشین) */
data class ProductEdit(val online: Long?, val max: Long?, val description: String, val specs: List<Pair<String, String>>)

/**
 *  برگهٔ یک کالای ویترین (`storefront/products/{id}`): قیمتِ دفتر برای مقایسه، قیمتِ آنلاین (خالی =
 *  قیمتِ دفتر)، سقفِ هر سفارش، توضیح و مشخصاتِ عمومی (WEB-08). همان سنجه‌های `StorefrontOwner` و
 *  `StorefrontSpecs`؛ خطای سرور درجا با لرزش، و برگه فقط پس از «ذخیره شد» بسته می‌شود.
 */
@Composable
fun ProductSheet(
  p: StorefrontProductDto,
  busy: Boolean,
  failure: String?,
  onSave: (ProductEdit) -> Unit,
  onDismiss: () -> Unit,
) {
  val c = PhoneV3.colors
  var online by remember { mutableStateOf(p.onlinePriceMinor?.let { StorefrontOwner.formatMinor(it) } ?: "") }
  var max by remember { mutableStateOf(p.maxQtyMilli?.let { StorefrontOwner.formatQtyMilli(it) } ?: "") }
  var description by remember { mutableStateOf(p.description) }
  var specs by remember { mutableStateOf(StorefrontSpecs.format(p.specs.map { it.name to it.value })) }
  var errors by remember { mutableStateOf(emptyMap<String, String>()) }
  var tried by remember { mutableIntStateOf(0) }
  V3Sheet(onDismiss = onDismiss, title = p.name) { _ ->
    Row(verticalAlignment = Alignment.CenterVertically) {
      V3ProductThumb(p.id, p.name, size = 48.dp)
      Column(Modifier.padding(start = 12.dp)) {
        Text(StrOnline.ledgerPrice(StorefrontModel.ledgerPrice(p)), style = V3Type.callout, color = c.ink)
        Text(StorefrontModel.stateLabel(p), style = V3Type.footnote, color = c.inkMuted)
      }
    }
    Spacer(Modifier.height(16.dp))
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
      V3TextField(online, { online = it; errors = errors - "online" }, StrOnline.sfOnlinePrice, kind = V3FieldKind.MONEY, error = errors["online"], modifier = Modifier.fillMaxWidth())
      V3TextField(max, { max = it; errors = errors - "max" }, StrOnline.sfMaxQty(p.unit), kind = V3FieldKind.NUMBER, error = errors["max"], modifier = Modifier.fillMaxWidth())
      V3TextField(
        description, { description = it.take(StorefrontSpecs.MAX_DESCRIPTION) }, StrOnline.sfDescription,
        helper = StrOnline.sfProductHelp, singleLine = false, modifier = Modifier.fillMaxWidth(),
      )
      V3TextField(
        specs, { specs = it; errors = errors - "specs" }, StrOnline.sfSpecs, placeholder = StrOnline.sfSpecsPlaceholder,
        error = errors["specs"], singleLine = false, modifier = Modifier.fillMaxWidth(),
      )
    }
    if (failure != null) {
      Spacer(Modifier.height(12.dp))
      V3Message(V3Tone.ERROR, failure, Modifier.v3Shake(failure))
    }
    Spacer(Modifier.height(20.dp))
    V3Button(
      StrKit.save,
      {
        val o = runCatching { StorefrontOwner.parseMinor(online) }
        val m = runCatching { StorefrontOwner.parseQtyMilli(max) }
        val (sp, spErr) = StorefrontSpecs.parse(specs)
        val e = buildMap {
          o.exceptionOrNull()?.let { put("online", it.message ?: StrOnline.badAmount) }
          m.exceptionOrNull()?.let { put("max", it.message ?: StrOnline.badAmount) }
          spErr?.let { put("specs", it) }
        }
        errors = e
        if (e.isEmpty()) onSave(ProductEdit(o.getOrNull(), m.getOrNull(), description.trim(), sp.orEmpty())) else tried++
      },
      Modifier.fillMaxWidth().v3Shake(tried.takeIf { it > 0 }),
      loading = busy,
      haptic = true,
    )
  }
}
