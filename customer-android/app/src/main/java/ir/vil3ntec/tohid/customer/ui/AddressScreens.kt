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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.shopper.AddressRules
import ir.vil3ntec.tohid.core.shopper.GeoPin
import ir.vil3ntec.tohid.core.shopper.MapPinFeature
import ir.vil3ntec.tohid.core.shopper.PlaceText
import ir.vil3ntec.tohid.core.shopper.PinText
import ir.vil3ntec.tohid.core.shopper.SavedAddress
import ir.vil3ntec.tohid.core.shopper.ShopperText

/** «نشانی‌های من» (CHK-09): نام‌دار، با پین، حداکثر ۱۰؛ بی حساب فقط روی همین گوشی، با حساب همراهِ دفترِ حساب (CRM-09). */
@Composable
fun AddressesScreen(model: CustomerModel) {
  val list = model.saved.addresses
  //  CRM-09: با حساب، دفترِ حساب از نو (نشانیِ تازهٔ وب یا گوشیِ دیگر)
  LaunchedEffect(Unit) { model.syncAddresses() }
  Column(Modifier.fillMaxSize()) {
    TopBar(ShopperText.myAddresses, onBack = { model.back() }) {
      if (list.size < AddressRules.MAX) {
        IconButton(onClick = { model.go(Route.AddressEdit(null)) }) { Icon(Icons.Filled.Add, contentDescription = ShopperText.addAddress) }
      }
    }
    LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      model.notice?.let { n -> item { Note(n, Tone.OK) } }
      if (list.isEmpty()) item { Note(ShopperText.noAddresses) }
      items(list, key = { it.id }) { a -> AddressRow(a, onOpen = { model.go(Route.AddressEdit(a.id)) }, onDelete = { model.deleteAddress(a.id) }) }
      model.account.places.note?.let { n -> item { Note(n, Tone.WARN) } }
      item { Text(if (model.signedIn() != null) PlaceText.addressesAccount else ShopperText.addressesLocal, style = MaterialTheme.typography.bodySmall) }
    }
  }
}

@Composable
private fun AddressRow(a: SavedAddress, onOpen: () -> Unit, onDelete: () -> Unit) {
  Card(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onOpen)) {
    Row(Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
      Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(a.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        listOf(a.text, a.landmark).filter { it.isNotBlank() }.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
        a.pin?.let { Text(ShopperText.pinSet(PinText.short(it)), style = MaterialTheme.typography.bodySmall) }
        a.media?.let { m ->
          if (m.gatePhoto) Text(PlaceText.amPhoto + ": " + PlaceText.amSaved, style = MaterialTheme.typography.bodySmall)
          if (m.voiceNote) Text(PlaceText.amVoice + ": " + PlaceText.amSaved, style = MaterialTheme.typography.bodySmall)
        }
      }
      IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = ShopperText.deleteAddress(a.label)) }
    }
  }
}

/**
 *  برگهٔ نشانی: نام، نشانی، نشانه و پین (موقعیتِ من، پیوند/مختصاتِ برنامهٔ نقشه، یا «انتخاب روی نقشه»)؛ ذخیره با قاعده‌های
 *  سرور. CRM-09: نشانیِ حساب در دکانی با پرچم ⇒ عکسِ دروازه و یادداشتِ صوتی ([AddressMediaCard]).
 */
@Composable
fun AddressEditScreen(model: CustomerModel, id: String?) {
  val b = model.book
  //  شناسهٔ همان نشانی (پس از رسیدن به دفترِ حساب شناسهٔ سرور می‌گیرد: `AddressModel.follow`)
  val editing = b.editingId
  DisposableEffect(editing) {
    model.openAddressMedia(editing)
    onDispose { model.closeAddressMedia() }
  }
  Column(Modifier.fillMaxSize()) {
    TopBar(if (id == null) ShopperText.addAddress else ShopperText.editAddress, onBack = { model.back() })
    Column(
      Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      TextBox(ShopperText.addressLabel, b.label) { b.label = it.take(AddressRules.LABEL_MAX) }
      TextBox(ShopperText.address, b.text, ShopperText.addressHint) { b.text = it.take(AddressRules.TEXT_MAX) }
      TextBox(ShopperText.landmark, b.landmark, ShopperText.landmarkHint) { b.landmark = it.take(AddressRules.LANDMARK_MAX) }
      PinBlock(
        pin = b.pin, busy = b.locating, note = b.note,
        onMine = { b.useMyLocation() }, onPaste = { text -> b.paste = text; b.pastePin() }, onClear = { b.clearPin() },
        map = model.mapFeature(), onMap = { b.mapPin(it) },
      )
      AddressMediaCard(model, editing)
    }
    Column(Modifier.padding(16.dp)) { MainButton(ShopperText.save) { model.saveAddress() } }
  }
}

/**
 *  پینِ موقعیت (CHK-08): «موقعیتِ فعلیِ من» (اجازه را سکو می‌پرسد)، چسباندنِ پیوند/مختصات از برنامهٔ نقشه، «نمایش روی
 *  نقشه» با پیوندِ `geo:` (فقط مختصات) و برداشتن. «انتخاب روی نقشه» فقط با [map] (پرچمِ `shop.storefront.map_pin` +
 *  سرورِ تکه‌های همان دکان، هرگز `tile.openstreetmap.org` ِ پیش‌فرض — سیاستِ OSMF) و سکویی که تکه می‌کشد.
 */
@Composable
fun PinBlock(
  pin: GeoPin?, busy: Boolean, note: String?, onMine: () -> Unit, onPaste: (String) -> Boolean, onClear: () -> Unit,
  map: MapPinFeature? = null, onMap: (GeoPin) -> Unit = {},
) {
  var paste by remember { mutableStateOf("") }
  var mapOpen by remember { mutableStateOf(false) }
  val platform = LocalPlatform.current
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    SectionTitle(ShopperText.pinTitle)
    if (pin != null) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f).padding(start = 8.dp)) {
          Text(ShopperText.pinSet(PinText.short(pin)), style = MaterialTheme.typography.bodyLarge)
          pin.accuracyM?.let { Text(ShopperText.pinAccuracy(it), style = MaterialTheme.typography.bodySmall) }
        }
      }
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { platform.openMap(PinText.geoUri(pin)) }) { Text(ShopperText.pinShow) }
        TextButton(onClick = onClear) { Text(ShopperText.pinClear) }
      }
    }
    OutlinedButton(onClick = onMine, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
      Text(if (busy) ShopperText.pinWait else ShopperText.pinMine)
    }
    if (map != null && platform.tile != null) {
      if (!mapOpen) {
        OutlinedButton(onClick = { mapOpen = true }, modifier = Modifier.fillMaxWidth()) { Text(PlaceText.mapOpen) }
      } else {
        PinMapBox(map, pin, onPick = { onMap(it); mapOpen = false }, onClose = { mapOpen = false })
      }
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      OutlinedTextField(
        value = paste, onValueChange = { paste = it.take(2000) }, singleLine = true, modifier = Modifier.weight(1f),
        label = { Text(ShopperText.pinPaste) }, placeholder = { Text(ShopperText.pinPasteHint) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
      )
      OutlinedButton(onClick = { if (onPaste(paste)) paste = "" }, enabled = paste.isNotBlank()) { Text(ShopperText.pinUse) }
    }
    note?.let { Note(it, Tone.WARN) }
  }
}

@Composable
private fun TextBox(label: String, value: String, hint: String? = null, onChange: (String) -> Unit) {
  OutlinedTextField(
    value = value, onValueChange = onChange, label = { Text(label) }, modifier = Modifier.fillMaxWidth(), singleLine = true,
    placeholder = if (hint != null) { { Text(hint) } } else null,
  )
}
