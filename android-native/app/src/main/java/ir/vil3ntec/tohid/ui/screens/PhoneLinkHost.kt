package ir.vil3ntec.tohid.ui.screens

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.StrDevices
import ir.vil3ntec.tohid.core.i18n.StrPick
import ir.vil3ntec.tohid.core.i18n.StrProducts
import ir.vil3ntec.tohid.core.i18n.StrScanPages
import ir.vil3ntec.tohid.core.i18n.StrSettings
import ir.vil3ntec.tohid.data.PhotoStore
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.sync.PhoneScanner
import ir.vil3ntec.tohid.sync.PhotoSync
import ir.vil3ntec.tohid.ui.theme.Shop
import kotlinx.coroutines.launch

/**
 *  دوربینِ گوشی برای کامپیوتر — هر دو سرِ کار، در همهٔ صفحه‌ها.
 *
 *  **گوشی** (فرستنده): کامپیوتر عکسِ کالایی را خواست ⇒ پنجرهٔ «عکس بگیر»
 *  ⇒ دوربین ⇒ عکس به صندوقِ سرور (`/photo-inbox`) می‌رود.
 *  **کامپیوتر** (گیرنده): عکسی برای یک کالا رسید ⇒ همان لحظه استودیوی
 *  عکس با آن باز می‌شود؛ «ذخیره» عکس را روی کالا می‌گذارد و از صندوق
 *  برمی‌دارد. عکسِ بی‌کالا در «دستگاه‌های جانبی ⇐ عکس‌های رسیده» می‌ماند.
 */
@Composable
fun PhoneLinkHost(store: ShopStore, data: ShopData, snackbar: SnackbarHostState) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  fun toast(t: String) { scope.launch { snackbar.showSnackbar(t) } }

  LaunchedEffect(PhoneScanner.listenCode.value) { PhoneScanner.refreshInbox(context) }

  /* ---------------------------- گوشی ---------------------------- */
  val request = PhoneScanner.photoRequest.value
  var pickFor by remember { mutableStateOf<Pair<String, String>?>(null) }
  if (request != null) {
    AlertDialog(
      onDismissRequest = { PhoneScanner.photoRequest.value = null },
      containerColor = Shop.colors.surfaceSolid,
      title = { Text(StrScanPages.pcWantsPhoto, color = Shop.colors.text) },
      text = { Text(StrScanPages.takePhotoOf(request.second.ifBlank { Str.product }), color = Shop.colors.muted) },
      confirmButton = { TextButton(onClick = { pickFor = request; PhoneScanner.photoRequest.value = null }) { Text(StrPick.photoTake) } },
      dismissButton = { TextButton(onClick = { PhoneScanner.photoRequest.value = null }) { Text(StrDevices.later) } },
    )
  }
  PhotoSourceSheet(
    open = pickFor != null,
    onDismiss = { pickFor = null },
    note = StrSettings.photoForPc,
  ) { uri ->
    val target = pickFor ?: return@PhotoSourceSheet
    toast(StrScanPages.sendingToPc)
    scope.launch { toast(PhoneScanner.sendPhoto(context, uri, target.first) ?: StrSettings.photoArrived) }
  }

  /* -------------------------- کامپیوتر -------------------------- */
  val item = PhoneScanner.openNow.value
  var uri by remember(item?.id) { mutableStateOf<android.net.Uri?>(null) }
  LaunchedEffect(item?.id) {
    if (item != null) {
      uri = PhoneScanner.download(context, item)
      if (uri == null) { toast(StrScanPages.phonePhotoNotReceived); PhoneScanner.openNow.value = null }
    }
  }
  val u = uri
  if (item != null && u != null) {
    val product = data.products.firstOrNull { it.id == item.productId }
    PhotoStudioDialog(source = u, onDismiss = { PhoneScanner.openNow.value = null }) { bitmap ->
      PhoneScanner.openNow.value = null
      if (product == null) { toast(StrProducts.missingTitle); return@PhotoStudioDialog }
      PhotoStore.saveBitmap(context, product.id, bitmap).onSuccess {
        scope.launch {
          store.save(data, data.copy(products = data.products.map { if (it.id == product.id) it.copy(photo = true) else it }))
          PhoneScanner.discard(context, item)
        }
        PhotoSync.changed(context, product.id)
        PhotoSync.version.value = PhotoSync.version.value + 1
        toast(StrScanPages.phonePhotoSet(product.name))
      }.onFailure { toast(StrProducts.photoFailed) }
    }
  }
}
