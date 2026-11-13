package ir.vil3ntec.tohid.customer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.market.PublicProduct
import ir.vil3ntec.tohid.core.shopper.AccountRules
import ir.vil3ntec.tohid.core.shopper.ShopperText

/*
 *  برنامهٔ خرید — ♥، «فهرستِ خرید» و حسابِ مشتری (WEB-11، CHK-01). هر بخش فقط با درِ روشنِ همان دکان
 *  (`features.favorites`، `features.webAccount`) دیده می‌شود؛ متن‌ها همه از [ShopperText].
 */

/** ♥ ِ برگهٔ کالا */
@Composable
fun FavButton(model: CustomerModel, slug: String, p: PublicProduct) {
  if (model.store.valueOrNull()?.features?.favorites != true) return
  OutlinedButton(onClick = { model.toggleFav(slug, p) }, modifier = Modifier.fillMaxWidth()) {
    Text(if (model.isFav(slug, p.id)) ShopperText.favOn else ShopperText.favAdd)
  }
}

/** ردیفِ «دلخواه‌ها» ِ دکان */
@Composable
fun FavRow(model: CustomerModel, slug: String) {
  if (model.store.valueOrNull()?.features?.favorites != true) return
  val favs = model.favItems(slug)
  if (favs.isEmpty()) return
  Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
    SectionTitle(ShopperText.favorites)
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      items(favs, key = { it.id }) { p -> Pill(p.name, false) { model.go(Route.Product(slug, p.id)) } }
    }
  }
}

/** «فهرستِ خرید» زیرِ سبد: نگه‌داشتنِ سبد، برداشتنِ قلم، «همه به سبد» با قیمتِ امروز، پاک کردن */
@Composable
fun SavedList(model: CustomerModel, slug: String) {
  if (model.store.valueOrNull()?.features?.favorites != true) return
  val list = model.listLines(slug)
  val cart = model.cartOf(slug)
  if (list.isEmpty() && cart.lines.isEmpty()) return
  Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    SectionTitle(ShopperText.shoppingList)
    if (list.isEmpty()) Text(ShopperText.listEmpty, style = MaterialTheme.typography.bodySmall)
    list.forEach { (x, name, unit) ->
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Text(ShopperText.qty(x.q) + (if (unit.isBlank()) "" else " " + unit), style = MaterialTheme.typography.bodyMedium)
        IconButton(onClick = { model.removeFromList(slug, x.id) }) {
          Icon(Icons.Filled.Delete, contentDescription = ShopperText.remove + " " + name)
        }
      }
    }
    if (cart.lines.isNotEmpty()) {
      OutlinedButton(onClick = { model.saveCartAsList(slug) }, modifier = Modifier.fillMaxWidth()) { Text(ShopperText.listSave) }
    }
    if (list.isNotEmpty()) {
      OutlinedButton(onClick = { model.listToCart(slug) }, modifier = Modifier.fillMaxWidth()) { Text(ShopperText.listAdd) }
      TextButton(onClick = { model.clearList(slug) }, modifier = Modifier.fillMaxWidth()) { Text(ShopperText.listClear) }
    }
  }
}

/** ورود به حساب با کدِ واقعیِ سرور (بی راهِ پیامک ⇒ همان پیامِ «تأیید راه‌اندازی نشده»، D-09) */
@Composable
fun AccountCard(model: CustomerModel, slug: String) {
  val page = model.store.valueOrNull() ?: return
  if (!page.features.webAccount || model.signedIn() != null) return
  val a = model.account
  Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    SectionTitle(ShopperText.accountTitle)
    Text(ShopperText.accountWhy, style = MaterialTheme.typography.bodySmall)
    if (!page.verificationAvailable) {
      Note(ShopperText.verifyOff, Tone.WARN)
      return@Column
    }
    OutlinedTextField(
      value = a.email, onValueChange = { a.email = it.take(190) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
      label = { Text(ShopperText.email) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
    )
    if (a.codeSent) {
      OutlinedTextField(
        value = a.code, onValueChange = { a.code = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        label = { Text(ShopperText.code) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
      )
      MainButton(ShopperText.signIn, enabled = !a.busy) { model.accountSignIn(slug) }
    } else {
      OutlinedButton(onClick = { model.accountSendCode(slug) }, enabled = !a.busy, modifier = Modifier.fillMaxWidth()) { Text(ShopperText.sendCode) }
    }
    a.note?.let { Note(it) }
  }
}

/** تنظیمات: واردشده با ایمیلِ پوشیده (CRM-15؛ حسابِ کهنهٔ بی‌ایمیل: شمارهٔ پوشیده) و «خروج» */
@Composable
fun AccountSettings(model: CustomerModel) {
  SectionTitle(ShopperText.accountTitle)
  val s = model.signedIn()
  if (s == null) {
    Text(ShopperText.notSignedIn, style = MaterialTheme.typography.bodyMedium)
    return
  }
  val who = s.customer.email?.let { AccountRules.maskEmail(it) } ?: AccountRules.maskPhone(s.customer.phone.orEmpty())
  Text(ShopperText.signedInAs(who), style = MaterialTheme.typography.bodyMedium)
  OutlinedButton(onClick = { model.accountSignOut() }, modifier = Modifier.fillMaxWidth()) { Text(ShopperText.signOut) }
}
