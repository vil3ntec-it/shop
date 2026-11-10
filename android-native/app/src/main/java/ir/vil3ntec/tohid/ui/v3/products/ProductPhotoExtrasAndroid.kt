package ir.vil3ntec.tohid.ui.v3.products

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CenterFocusStrong
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.PhotoSizeSelectLarge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrImageChanges
import ir.vil3ntec.tohid.core.i18n.StrProductGallery
import ir.vil3ntec.tohid.core.i18n.StrReach
import ir.vil3ntec.tohid.data.ImageBank
import ir.vil3ntec.tohid.data.PhotoStore
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.scan.GuidedCapture
import ir.vil3ntec.tohid.scan.GuidedCaptureDialog
import ir.vil3ntec.tohid.ui.screens.BankPhotoSource
import ir.vil3ntec.tohid.ui.screens.ImageBankPicker
import ir.vil3ntec.tohid.ui.v3.V3MenuItem
import ir.vil3ntec.tohid.ui.v3.nav.RouteScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 *  مرزِ پلتفرمِ «بانکِ تصاویر» (۳۶.۶، IMG-BANK) و «دوربینِ راهنما» (۳۷.۵) برای عکسِ کالا — همان پنجره‌ها و
 *  نوشتنِ صفحهٔ کالای پیشین؛ عکسِ دوربینِ راهنما به همان استودیوی نسخهٔ ۳ می‌رود (بی تأیید چیزی جایگزین نمی‌شود).
 */

/** نسخهٔ عکس‌های گرفته‌شده از بانک — `BankPhotoSource` با آن دوباره می‌پرسد */
private val bankSaves = mutableIntStateOf(0)

/** عکسی که روی سرور برای این کالا ثبت شد (انتخاب از بانک، بازگشت یا نسخهٔ دیگرِ مرکزی) ⇒ همین دستگاه */
private fun saveFromBank(context: android.content.Context, store: ShopStore, scope: CoroutineScope, id: String, bytes: ByteArray) {
  PhotoStore.saveBytes(context, id, bytes).onSuccess {
    scope.launch {
      val cur = store.data.value
      store.save(cur, cur.copy(products = cur.products.map { if (it.id == id) it.copy(photo = true) else it }))
    }
    //  عکسِ مرکزی: فقط همگام‌سازیِ امروز؛ عکسِ قدیمیِ در صفِ بانک دیگر فرستاده نمی‌شود
    ImageBank.photoSaved(context, id, own = false)
    bankSaves.intValue++
  }
}

/** راه‌های دیگرِ برگهٔ «عکسِ کالا»: بانکِ تصاویر (فقط روشن) و دوربینِ راهنما */
@Composable
internal fun RouteScope.rememberPhotoMore(id: String): List<V3MenuItem> {
  val context = LocalContext.current
  LaunchedEffect(Unit) { if (ImageBank.enabled.value == null) ImageBank.refresh(context) }
  val bankOn = ImageBank.enabled.value == true
  return buildList {
    if (bankOn) add(V3MenuItem(Icons.Outlined.Collections, StrReach.photoBank) { nav.replace(ProductsPaths.photoBank(id)) })
    //  IMG-PRO-16: نسخهٔ کوچک و بزرگِ عکسِ بانک (ویترین) و سنجهٔ فشرده‌سازی
    if (bankOn) add(V3MenuItem(Icons.Outlined.PhotoSizeSelectLarge, StrImageChanges.versionsTitle) { nav.replace(ProductsPaths.photoVersions(id)) })
    //  PRD-17/27: عکس‌های تکمیلی و عکسِ جدای ویترین
    if (bankOn) add(V3MenuItem(Icons.Outlined.PhotoLibrary, StrProductGallery.title) { nav.replace(ProductsPaths.photoGallery(id)) })
    if (GuidedCapture.AVAILABLE) add(V3MenuItem(Icons.Outlined.CenterFocusStrong, StrReach.photoGuided) { nav.replace(ProductsPaths.photoGuided(id)) })
  }
}

/** زیرِ عکسِ کالا: کدام عکس در ویترین است، نسخهٔ تازهٔ مرکزی، بازگشت و پیشنهاد به بانک (IMG-BANK-08/09/16) */
@Composable
internal fun RouteScope.BankPhotoSlot(id: String) {
  val product = data.products.find { it.id == id } ?: return
  if (ImageBank.enabled.value != true) return
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  Box(Modifier.padding(vertical = 4.dp)) {
    BankPhotoSource(
      productId = id, name = product.name, barcode = product.barcodes.firstOrNull().orEmpty(),
      refresh = bankSaves.intValue, onPhoto = { saveFromBank(context, store, scope, id, it) },
    )
  }
}

/** `products/{id}/photo/bank` — بی نتیجه ⇒ «عکسِ خودم» همان برگهٔ دوربین/گالری (IMG-BANK-10) */
@Composable
internal fun RouteScope.PhotoBankRoute(id: String) {
  val product = data.products.find { it.id == id }
  if (product == null) { LaunchedEffect(id) { back() }; return }
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  ImageBankPicker(
    productId = id, name = product.name, barcode = product.barcodes.firstOrNull().orEmpty(),
    hasPhoto = product.photo, onDismiss = { back() },
    onUploadOwn = { nav.replace(ProductsPaths.photo(id)) },
  ) { bytes ->
    saveFromBank(context, store, scope, id, bytes)
    back()
  }
}

/** `products/{id}/photo/guided` — عکسِ پذیرفته ⇒ همان استودیوی عکسِ نسخهٔ ۳ */
@Composable
internal fun RouteScope.PhotoGuidedRoute(id: String) {
  GuidedCaptureDialog(onDismiss = { back() }) { uri -> nav.replace(ProductsPaths.studio(id, uri.toString())) }
}
