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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.shopper.CartLine
import ir.vil3ntec.tohid.core.shopper.CartRules
import ir.vil3ntec.tohid.core.shopper.CheckoutForm
import ir.vil3ntec.tohid.core.shopper.CheckoutRules
import ir.vil3ntec.tohid.core.shopper.ShopperText

/** سبد: «− n +» و «برداشتن» همیشه پیدا، کالای رفته خط‌خورده، برآوردِ ارسال و حداقل، و «ادامه». */
@Composable
fun CartScreen(model: CustomerModel, slug: String) {
  val cart = model.cartOf(slug)
  val page = model.store.valueOrNull()
  Column(Modifier.fillMaxSize()) {
    TopBar(page?.shop?.title?.let { ShopperText.cart + " — " + it } ?: ShopperText.cart, onBack = { model.back() })
    if (cart.lines.isEmpty()) {
      Note(ShopperText.emptyCart, modifier = Modifier.fillMaxWidth().padding(16.dp))
      //  WEB-11 و CHK-01: «فهرستِ خرید» و ورود با سبدِ خالی هم (دستگاهِ تازه سبدِ حساب را همین‌جا می‌گیرد)
      Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        model.notice?.let { Note(it, Tone.WARN) }
        SavedList(model, slug)
        AccountCard(model, slug)
      }
      return@Column
    }
    LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      model.cartNote?.let { n -> item { Note(n, Tone.WARN) } }
      model.notice?.let { n -> item { Note(n, Tone.WARN) } }
      if (CartRules.hasUnavailable(cart)) item { Note(ShopperText.cartBad, Tone.ERROR) }
      items(cart.lines, key = { it.productId }) { l ->
        CartRow(model, slug, l)
        HorizontalDivider()
      }
      item {
        Column(Modifier.padding(top = 8.dp)) {
          MoneyRow(ShopperText.itemsTotal, ShopperText.money(CartRules.subtotal(cart)), strong = true)
          if (page != null) {
            val f = CheckoutRules.fulfillmentOf(page.shop, CheckoutForm())
            val s = CheckoutRules.summary(page, cart, CheckoutForm(), null)
            if (f == CheckoutRules.DELIVERY) {
              val zones = CheckoutRules.zones(page)
              Text(
                if (zones != null) ShopperText.zoneAfter else ShopperText.shipFee(page.shop.deliveryFeeMinor),
                style = MaterialTheme.typography.bodySmall,
              )
            }
            if (s.shortOfMinMinor > 0) Note(ShopperText.minLeft(s.shortOfMinMinor, s.minOrderMinor), Tone.WARN)
          }
        }
      }
      //  WEB-11 و CHK-01: «فهرستِ خرید» و ورود به حساب (سبد، ♥ و فهرست همراهِ حساب)
      item { SavedList(model, slug) }
      item { AccountCard(model, slug) }
    }
    Column(Modifier.padding(16.dp)) {
      MainButton(ShopperText.toCheckout, enabled = !CartRules.hasUnavailable(cart) && page != null) { model.go(Route.Checkout(slug)) }
    }
  }
}

@Composable
private fun CartRow(model: CustomerModel, slug: String, l: CartLine) {
  Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    Column(Modifier.weight(1f)) {
      Text(
        l.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium,
        textDecoration = if (l.unavailable) TextDecoration.LineThrough else null,
      )
      Text(
        if (l.unavailable) ShopperText.lineGone else ShopperText.lineQty(l.qtyMilli, l.unit, l.priceMinor) + " = " + ShopperText.money(CartRules.lineTotal(l)),
        style = MaterialTheme.typography.bodySmall,
      )
    }
    if (!l.unavailable) {
      QtyStepper(ShopperText.qty(l.qtyMilli), l.name, onLess = { model.step(slug, l.productId, up = false) },
        onMore = { model.step(slug, l.productId, up = true) }, moreEnabled = !CartRules.atMax(l.qtyMilli, l.maxQtyMilli))
    }
    IconButton(onClick = { model.remove(slug, l.productId) }) { Icon(Icons.Filled.Delete, contentDescription = ShopperText.remove + " " + l.name) }
  }
}
