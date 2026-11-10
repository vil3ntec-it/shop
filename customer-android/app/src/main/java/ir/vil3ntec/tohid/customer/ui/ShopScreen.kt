package ir.vil3ntec.tohid.customer.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.market.PublicProduct
import ir.vil3ntec.tohid.core.shopper.CartRules
import ir.vil3ntec.tohid.core.shopper.CheckoutRules
import ir.vil3ntec.tohid.core.shopper.ShopperText
import ir.vil3ntec.tohid.core.shopper.StorePage

/** دکان: کارتِ فشرده، جست‌وجو و دسته‌ها بالا، کالاها از همان صفحهٔ اول (یافتهٔ ۲ ِ ممیزی)، نوارِ سبد پایین. */
@Composable
fun ShopScreen(model: CustomerModel, slug: String) {
  val cart = model.cartOf(slug)
  Column(Modifier.fillMaxSize()) {
    when (val s = model.store) {
      is Load.Ready -> {
        val page = s.value
        TopBar(page.shop.title, onBack = { model.back() }) {
          if (page.shop.contactPhone.isNotBlank()) {
            val platform = LocalPlatform.current
            IconButton(onClick = { platform.call(page.shop.contactPhone) }) { Icon(Icons.Filled.Call, contentDescription = ShopperText.callShop) }
          }
        }
        Catalog(model, slug, page, Modifier.weight(1f))
      }
      is Load.Failed -> {
        TopBar(slug, onBack = { model.back() })
        ErrorBox(s.error, onRetry = { model.loadStore(slug) })
      }
      else -> {
        TopBar(slug, onBack = { model.back() })
        BusyBox()
      }
    }
    CartBar(CartRules.count(cart), CartRules.subtotal(cart)) { model.go(Route.CartPage(slug)) }
  }
}

@Composable
private fun Catalog(model: CustomerModel, slug: String, page: StorePage, modifier: Modifier) {
  val shop = page.shop
  LazyColumn(modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    item {
      Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (shop.tagline.isNotBlank()) Text(shop.tagline, style = MaterialTheme.typography.bodyMedium)
        val zones = CheckoutRules.zones(page)
        val meta = buildList {
          add(ShopperText.presence(shop.status?.badge))
          if (shop.fulfillment.delivery) add(if (zones != null) ShopperText.shipFrom(zones.minOf { it.feeMinor }) else ShopperText.shipFee(shop.deliveryFeeMinor))
          if (shop.fulfillment.pickup) add(ShopperText.pickupOnly)
          if (shop.minOrderMinor > 0) add(ShopperText.minOrder(shop.minOrderMinor))
        }.filter { it.isNotBlank() }.joinToString(" · ")
        Text(meta, style = MaterialTheme.typography.bodySmall)
        //  NTF-09: میانگینِ نظرهای تأییدشده (پرچمِ `shop.orders.reviews`)
        model.feedback.shop?.takeIf { page.features.reviews && it.count > 0 && it.avgTenths != null }?.let {
          Text(ShopperText.shopRating(it.avgTenths!!, it.count), style = MaterialTheme.typography.bodySmall)
        }
        //  گفت‌وگو با دکان (پرچمِ `shop.chat`)
        if (page.features.chat) {
          androidx.compose.material3.OutlinedButton(onClick = { model.go(Route.Chat(slug, null)) }, modifier = Modifier.fillMaxWidth()) {
            Text(ShopperText.chatShop)
          }
        }
        if (CheckoutRules.closed(shop)) Note(shop.ordering?.message?.takeIf { it.isNotBlank() } ?: ShopperText.notAccepting, Tone.WARN)
      }
    }
    item {
      OutlinedTextField(
        value = model.query, onValueChange = { model.search(it) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) }, placeholder = { Text(ShopperText.searchProducts) },
      )
    }
    if (shop.categories.isNotEmpty()) {
      item {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          item { Pill(ShopperText.allCategories, model.category.isEmpty()) { model.pickCategory("") } }
          items(shop.categories) { c -> Pill(c, model.category == c) { model.pickCategory(c) } }
        }
      }
    }
    //  WEB-11: دلخواه‌های همین دکان
    item { FavRow(model, slug) }
    //  WEB-13: دیده‌شده‌های اخیر (فقط روی همین دستگاه)
    item { RecentRow(model, slug) }
    model.notice?.let { n -> item { Note(n, Tone.WARN) } }
    val c = model.catalog
    if (model.items.isEmpty()) {
      item {
        when (c) {
          is Load.Failed -> ErrorBox(c.error, onRetry = { model.retryCatalog() })
          is Load.Ready -> Note(if (model.query.isNotBlank()) ShopperText.nothingFound(model.query) else ShopperText.noProducts)
          else -> BusyBox(Modifier.fillMaxWidth())
        }
      }
    }
    items(model.items, key = { it.id }) { p ->
      ProductRow(model, slug, p)
      HorizontalDivider()
    }
    if (model.items.isNotEmpty()) {
      when {
        c is Load.Busy -> item { BusyBox(Modifier.fillMaxWidth()) }
        c is Load.Failed -> item { ErrorBox(c.error, onRetry = { model.retryCatalog() }) }
        model.cursor != null -> item {
          OutlinedButton(onClick = { model.loadMore() }, modifier = Modifier.fillMaxWidth()) { Text(ShopperText.moreProducts) }
        }
      }
    }
  }
}

/** WEB-13: «دیده‌شده‌های اخیر» ِ همین دکان با «پاک کردن» (همان `#recent` ِ وب؛ فقط با `recent` ِ سرور) */
@Composable
private fun RecentRow(model: CustomerModel, slug: String) {
  val seen = model.recentItems(slug)
  if (seen.isEmpty()) return
  Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Box(Modifier.weight(1f)) { SectionTitle(ShopperText.recentViewed) }
      TextButton(onClick = { model.clearRecent(slug) }) { Text(ShopperText.recentClear) }
    }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      items(seen, key = { "seen-" + it.id }) { p ->
        Column(
          Modifier.width(112.dp).clickable(role = Role.Button) { model.go(Route.Product(slug, p.id)) }.padding(vertical = 4.dp),
          verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
          Thumb(model.photoPath(slug, p, thumb = true), 104.dp)
          Text(p.name, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
          p.priceMinor?.let { Text(ShopperText.perUnit(it, p.unit), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
      }
    }
  }
}

/** یک کالا: عکس، نام، قیمت/واحد، موجودی و «+» یا «− n +» (هرگز زیرِ چیزِ دیگر، یافتهٔ ۱ ِ ممیزی) */
@Composable
private fun ProductRow(model: CustomerModel, slug: String, p: PublicProduct) {
  val qty = CartRules.qtyOf(model.cartOf(slug), p.id)
  Row(
    Modifier.fillMaxWidth().clickable(role = Role.Button) { model.go(Route.Product(slug, p.id)) }.padding(vertical = 6.dp),
    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Thumb(model.photoPath(slug, p, thumb = true), 64.dp)
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
      Text(p.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
      p.priceMinor?.let { Text(ShopperText.perUnit(it, p.unit), style = MaterialTheme.typography.bodyMedium) }
      p.stock?.let { Text(ShopperText.stock(it.status, it.prepMinutes), style = MaterialTheme.typography.bodySmall) }
    }
    if (qty > 0) {
      QtyStepper(ShopperText.qty(qty), p.name, onLess = { model.step(slug, p.id, up = false) }, onMore = { model.add(slug, p) },
        moreEnabled = !CartRules.atMax(qty, p.maxQtyMilli))
    } else if (CartRules.canOrder(p)) {
      IconButton(onClick = { model.add(slug, p) }, modifier = Modifier.semantics { contentDescription = ShopperText.addOne(p.name) }) {
        Icon(Icons.Filled.Add, contentDescription = null)
      }
    }
  }
}
