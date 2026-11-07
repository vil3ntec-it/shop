package ir.vil3ntec.tohid.customer.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.shopper.CartRules
import ir.vil3ntec.tohid.core.shopper.ShopCard
import ir.vil3ntec.tohid.core.shopper.ShopperText
import ir.vil3ntec.tohid.core.text.TextNormalizer

/**
 *  خانه: بازکردنِ دکان با نشانی/پیوند، سبدهای همین دستگاه، سفارش‌های من، دکان‌های اخیر و (اگر بازارچه باز
 *  است، پرچمِ `market.directory`) جست‌وجوی دکان‌های بازارچه. همه روی یک صفحه، بی منوی پنهان.
 */
@Composable
fun HomeScreen(model: CustomerModel) {
  var address by remember { mutableStateOf("") }
  val saved = model.saved
  val q = model.shopQuery
  //  جست‌وجوی محلی در دکان‌های اخیر با همان نرمال‌سازیِ فارسیِ هسته (ي/ی، ك/ک، نیم‌فاصله، رقم)
  val recent = saved.recent.filter { q.isBlank() || TextNormalizer.matches(it.title, q) || TextNormalizer.matches(it.slug, q) }
  LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
    item {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
          Text(ShopperText.homeTitle, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
          Text(ShopperText.homeSub, style = MaterialTheme.typography.bodyMedium)
        }
        IconButton(onClick = { model.go(Route.Addresses) }) { Icon(Icons.Filled.LocationOn, contentDescription = ShopperText.myAddresses) }
        IconButton(onClick = { model.go(Route.Settings) }) { Icon(Icons.Filled.Settings, contentDescription = ShopperText.settings) }
      }
    }
    model.linkNote?.let { n -> item { Note(n, Tone.WARN) } }
    //  MKT-08: سفارش‌های تکراریِ رسیده به موعد (یادآوری درونِ برنامه؛ ثبت با خودِ مشتری)
    items(model.duePlans(), key = { "due-" + it.token }) { p -> DuePlanCard(model, p) }
    item {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
          value = address, onValueChange = { address = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
          label = { Text(ShopperText.shopAddress) }, placeholder = { Text(ShopperText.shopAddressHint) },
          isError = model.addressError,
          supportingText = if (model.addressError) ({ Text(ShopperText.badAddress) }) else null,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
          keyboardActions = KeyboardActions(onGo = { model.openAddress(address) }),
        )
        OutlinedButton(onClick = { model.openAddress(address) }, modifier = Modifier.fillMaxWidth()) { Text(ShopperText.open) }
      }
    }
    if (saved.carts.isNotEmpty()) {
      item { SectionTitle(ShopperText.myCarts) }
      items(saved.carts, key = { "cart-" + it.slug }) { c ->
        val title = saved.recent.firstOrNull { it.slug == c.slug }?.title ?: c.slug
        Row(
          Modifier.fillMaxWidth().clickable(role = Role.Button) { model.go(Route.CartPage(c.slug)) }.padding(vertical = 8.dp),
        ) { Text(ShopperText.cartOf(title, CartRules.count(c), CartRules.subtotal(c)), style = MaterialTheme.typography.bodyLarge) }
      }
    }
    if (saved.orders.isNotEmpty()) {
      item { SectionTitle(ShopperText.myOrders) }
      items(saved.orders.take(10), key = { "order-" + it.token }) { o ->
        Column(Modifier.fillMaxWidth().clickable(role = Role.Button) { model.openOrder(o) }.padding(vertical = 8.dp)) {
          Text(ShopperText.orderNo(o.orderNo) + " — " + o.storeTitle, style = MaterialTheme.typography.bodyLarge)
          Text(ShopperText.money(o.totalMinor), style = MaterialTheme.typography.bodySmall)
        }
      }
    }
    item {
      OutlinedTextField(
        value = q, onValueChange = { model.searchShops(it) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) }, placeholder = { Text(ShopperText.searchShops) },
      )
    }
    if (recent.isNotEmpty()) {
      item { SectionTitle(ShopperText.recentShops) }
      items(recent, key = { "recent-" + it.slug }) { r ->
        Text(
          r.title, style = MaterialTheme.typography.bodyLarge,
          modifier = Modifier.fillMaxWidth().clickable(role = Role.Button) { model.go(Route.Shop(r.slug)) }.padding(vertical = 10.dp),
        )
      }
    }
    when (val m = model.market) {
      is Load.Failed -> item {
        if (m.error.code == "market_not_found") Note(ShopperText.marketOff) else ErrorBox(m.error, onRetry = { model.retryHome() })
      }
      Load.Busy, Load.Idle -> item { BusyBox(Modifier.fillMaxWidth()) }
      is Load.Ready -> {
        item { SectionTitle(ShopperText.marketShops) }
        when (val list = model.shopList) {
          is Load.Ready -> if (list.value.shops.isEmpty()) item { Note(ShopperText.noShops) }
            else items(list.value.shops, key = { "shop-" + it.slug }) { s -> ShopRow(s) { model.go(Route.Shop(s.slug)) } }
          is Load.Failed -> item { ErrorBox(list.error, onRetry = { model.searchShops(q) }) }
          else -> item { BusyBox(Modifier.fillMaxWidth()) }
        }
      }
    }
  }
}

@Composable
private fun ShopRow(s: ShopCard, onClick: () -> Unit) {
  Card(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick)) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
      Text(s.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
      val meta = listOf(s.categoryLabel, s.area, ShopperText.presence(s.status?.badge)).filter { it.isNotBlank() }.joinToString(" · ")
      if (meta.isNotEmpty()) Text(meta, style = MaterialTheme.typography.bodySmall)
      val ship = buildList {
        if (s.fulfillment.delivery) add(ShopperText.shipFee(s.deliveryFeeMinor))
        if (s.fulfillment.pickup) add(ShopperText.pickupOnly)
        if (s.minOrderMinor > 0) add(ShopperText.minOrder(s.minOrderMinor))
      }.joinToString(" · ")
      if (ship.isNotEmpty()) Text(ship, style = MaterialTheme.typography.bodySmall)
    }
  }
}
