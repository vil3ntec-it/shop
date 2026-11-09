package ir.vil3ntec.tohid.customer.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.market.PublicProduct
import ir.vil3ntec.tohid.core.shopper.CartRules
import ir.vil3ntec.tohid.core.shopper.ShopperText

/** برگهٔ کالا (WEB-08): عکسِ بزرگ، قیمت، موجودی، شرح، مشخصات، افزودن و دو قفسه. */
@Composable
fun ProductScreen(model: CustomerModel, slug: String, id: String) {
  val cart = model.cartOf(slug)
  Column(Modifier.fillMaxSize()) {
    when (val l = model.product) {
      is Load.Ready -> {
        val page = l.value
        val p = page.product
        TopBar(p.name, onBack = { model.back() })
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
          model.photoPath(slug, p, thumb = false)?.let { path ->
            item {
              val m = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(16.dp))
              LocalPlatform.current.photo(path, m)
            }
          }
          item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
              Text(p.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
              p.priceMinor?.let { Text(ShopperText.perUnit(it, p.unit), style = MaterialTheme.typography.titleMedium) }
              p.stock?.let { Text(ShopperText.stock(it.status, it.prepMinutes), style = MaterialTheme.typography.bodyMedium) }
            }
          }
          item {
            val qty = CartRules.qtyOf(cart, p.id)
            if (qty > 0) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                QtyStepper(ShopperText.qty(qty), p.name, onLess = { model.step(slug, p.id, up = false) }, onMore = { model.add(slug, p) },
                  moreEnabled = !CartRules.atMax(qty, p.maxQtyMilli))
              }
            } else if (CartRules.canOrder(p)) MainButton(ShopperText.add) { model.add(slug, p) }
          }
          //  WEB-11: ♥ (پرچمِ `shop.storefront.favorites`؛ با نشستِ حساب همراهِ حساب)
          item { FavButton(model, slug, p) }
          model.notice?.let { n -> item { Note(n, Tone.WARN) } }
          p.description?.takeIf { it.isNotBlank() }?.let { d -> item { Text(d, style = MaterialTheme.typography.bodyLarge) } }
          if (p.specs.isNotEmpty()) {
            item { SectionTitle(ShopperText.specs) }
            items(p.specs) { s -> MoneyRow(s.name, s.value) }
          }
          if (page.similar.isNotEmpty()) item { Shelf(model, slug, ShopperText.similar, page.similar) }
          if (page.complementary.isNotEmpty()) item { Shelf(model, slug, ShopperText.together, page.complementary) }
        }
      }
      is Load.Failed -> {
        TopBar("", onBack = { model.back() })
        ErrorBox(l.error, onRetry = { model.retryProduct() })
      }
      else -> {
        TopBar("", onBack = { model.back() })
        BusyBox()
      }
    }
    CartBar(CartRules.count(cart), CartRules.subtotal(cart)) { model.go(Route.CartPage(slug)) }
  }
}

@Composable
private fun Shelf(model: CustomerModel, slug: String, title: String, list: List<PublicProduct>) {
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    SectionTitle(title)
    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
      items(list, key = { it.id }) { p ->
        Column(
          Modifier.width(120.dp).clickable(role = Role.Button) { model.go(Route.Product(slug, p.id)) },
          verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          Thumb(model.photoPath(slug, p, thumb = true), 120.dp)
          Text(p.name, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
          p.priceMinor?.let { Text(ShopperText.money(it), style = MaterialTheme.typography.bodySmall) }
        }
      }
    }
  }
}
