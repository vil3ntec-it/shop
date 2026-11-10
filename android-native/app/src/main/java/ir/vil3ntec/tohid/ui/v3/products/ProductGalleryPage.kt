package ir.vil3ntec.tohid.ui.v3.products

// i18n: سه‌زبانه

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrProductGallery
import ir.vil3ntec.tohid.core.image.ImageQuality
import ir.vil3ntec.tohid.core.image.ProductGallery
import ir.vil3ntec.tohid.core.net.ApiResult
import ir.vil3ntec.tohid.data.repo.ProductGallerySource
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.V3ButtonKind
import ir.vil3ntec.tohid.ui.theme.V3Tone
import ir.vil3ntec.tohid.ui.v3.V3Button
import ir.vil3ntec.tohid.ui.v3.V3EmptyState
import ir.vil3ntec.tohid.ui.v3.V3ListRow
import ir.vil3ntec.tohid.ui.v3.V3Section
import ir.vil3ntec.tohid.ui.v3.V3Segmented
import ir.vil3ntec.tohid.ui.v3.V3Type
import ir.vil3ntec.tohid.ui.v3.nav.RouteScope
import kotlinx.coroutines.launch

/*
 *  PRD-17 — عکس‌های تکمیلیِ کالا و PRD-27 — عکسِ جدای ویترین (`products/{id}/photo/gallery`)، روی همان قراردادِ بانکِ
 *  تصاویرِ دکان (`core/image/ProductGallery`، `server/test/image-bank-extras.test.js`). راهش در «…» ِ عکسِ کالا فقط با
 *  بانکِ روشن (`rememberPhotoMore`)؛ نشانیِ مستقیم با پرچمِ خاموش، سرورِ کهنه یا بیرون از حساب فقط «روشن نیست» می‌گوید.
 *  ⛔ نوشتن فقط با نقشِ «نوشتنِ کالا» (همان `canWrite(role, 'products')` ِ سرور)؛ بقیه فقط می‌بینند.
 *  ⛔ هیچ کارِ این صفحه عکسِ روی دستگاه (صندوق و فهرست) را عوض نمی‌کند.
 */

/** حالِ صفحه — بی سرور و بی Compose ِ دیدنی آزموده می‌شود (`ProductGalleryStateTest`) */
@Stable
class ProductGalleryState(private val src: ProductGallerySource, val productId: String) {
  sealed interface Outcome {
    data object Done : Outcome
    /** سقفِ تکمیلی‌ها پر */
    data object Full : Outcome
    /** کارِ دیگری در راه است */
    data object Busy : Outcome
    /** پیامِ خودِ سرور (سقفِ دکان، نوعِ فایل) یا «نرفت» */
    data class Failed(val message: String) : Outcome
  }

  var items by mutableStateOf<List<ProductGallery.Item>?>(null)
    private set
  var shown by mutableStateOf<ImageQuality.Shown?>(null)
    private set
  var off by mutableStateOf(false)
    private set
  var failed by mutableStateOf(false)
    private set
  var busy by mutableStateOf(false)
    private set
  /** نسخهٔ کوچکِ هر عکسِ دیدنی (شناسه ⇒ بایت) */
  val thumbs = mutableStateMapOf<String, ByteArray>()

  val extras: List<ProductGallery.Item> get() = ProductGallery.extras(items.orEmpty())

  val storefront: ProductGallery.Storefront? get() = shown?.let { ProductGallery.storefrontOf(it, items.orEmpty()) }

  /** عکسِ جدای ویترین، اگر همین حالا نشان داده می‌شود */
  val storefrontItem: ProductGallery.Item? get() {
    val s = shown ?: return null
    if (storefront != ProductGallery.Storefront.OWN) return null
    return items.orEmpty().find { it.id == s.imageId }
  }

  suspend fun load() {
    failed = false
    when (val r = src.items(productId)) {
      is ApiResult.Success -> { items = r.value; off = false }
      is ApiResult.Failure -> { if (ProductGallery.off(r.error.status)) off = true else failed = true; return }
    }
    when (val r = src.shown(productId)) {
      is ApiResult.Success -> shown = r.value
      is ApiResult.Failure -> if (ProductGallery.off(r.error.status)) off = true else failed = true
    }
    for (x in extras + listOfNotNull(storefrontItem)) {
      if (thumbs.containsKey(x.id)) continue
      val url = x.thumbUrl ?: x.displayUrl ?: continue
      val b = src.bytes(url)
      if (b is ApiResult.Success) thumbs[x.id] = b.value
    }
  }

  private suspend fun send(role: String, display: ByteArray, thumb: ByteArray?, select: Boolean): Outcome {
    if (busy) return Outcome.Busy
    busy = true
    val r = try { src.upload(productId, role, display, thumb, select) } finally { busy = false }
    return when (r) {
      is ApiResult.Success -> { load(); Outcome.Done }
      is ApiResult.Failure -> Outcome.Failed(r.error.userMessage)
    }
  }

  /** عکسِ تکمیلی با نقشِ [role] (نسخه‌های آماده‌شده)؛ عکسِ اصلی و ویترین دست نمی‌خورد */
  suspend fun add(role: String, display: ByteArray, thumb: ByteArray?): Outcome {
    if (!ProductGallery.canAdd(extras.size)) return Outcome.Full
    return send(role, display, thumb, select = false)
  }

  /** عکسِ جدای ویترین: بارگذاری با نقشِ ویترین و انتخاب */
  suspend fun setStorefront(display: ByteArray, thumb: ByteArray?): Outcome =
    send(ProductGallery.STOREFRONT_ROLE, display, thumb, select = true)

  /** برداشتن از کالا (اصل روی سرور می‌ماند). @return null ⇒ کارِ دیگری در راه است */
  suspend fun detach(imageId: String): ProductGallery.Detach? {
    if (busy) return null
    busy = true
    val r = try { src.detach(imageId) } finally { busy = false }
    val d = when (r) {
      is ApiResult.Success -> ProductGallery.detachOutcome(true, 200, "")
      is ApiResult.Failure -> ProductGallery.detachOutcome(false, r.error.status, r.error.code)
    }
    if (d == ProductGallery.Detach.DONE || d == ProductGallery.Detach.GONE) { thumbs.remove(imageId); load() }
    return d
  }
}

private fun roleLabel(role: String): String = when (role) {
  "back" -> StrProductGallery.kindBack
  "label" -> StrProductGallery.kindLabel
  "variant" -> StrProductGallery.kindVariant
  else -> StrProductGallery.kindDetail
}

private fun storefrontLine(s: ProductGallery.Storefront?): String = when (s) {
  ProductGallery.Storefront.OWN -> StrProductGallery.shownOwn
  ProductGallery.Storefront.CENTRAL -> StrProductGallery.shownCentral
  ProductGallery.Storefront.NONE -> StrProductGallery.shownNone
  ProductGallery.Storefront.DEVICE, null -> StrProductGallery.shownDevice
}

@Composable
private fun Thumb(bytes: ByteArray?) {
  val bmp = remember(bytes) { bytes?.let { decodeVersion(it) } }
  Box(Modifier.size(48.dp).background(PhoneV3.colors.card)) {
    if (bmp != null) Image(bmp, null, Modifier.size(48.dp), contentScale = ContentScale.Crop)
  }
}

/** `products/{id}/photo/gallery` */
@Composable
internal fun RouteScope.ProductGalleryPage(productId: String) {
  val hasPhoto = data.products.find { it.id == productId }?.photo == true
  val port = rememberGalleryPort(productId, hasPhoto)
  val state = remember(port) { port?.let { ProductGalleryState(it.src, productId) } }
  var tick by remember { mutableIntStateOf(0) }
  if (state != null) LaunchedEffect(state, tick) { state.load() }
  val scope = rememberCoroutineScope()
  //  نقشِ عکسی که برگهٔ دوربین/گالری برایش باز شد (`STOREFRONT_ROLE` ⇒ عکسِ ویترین) — با بستنِ برگه پاک نمی‌شود
  var pickFor by remember { mutableStateOf(ProductGallery.EXTRA_ROLES.first()) }
  var role by remember { mutableIntStateOf(0) }
  var sheet by remember { mutableStateOf(false) }
  val c = PhoneV3.colors

  fun shout(o: ProductGalleryState.Outcome, ok: String) = when (o) {
    ProductGalleryState.Outcome.Done -> toasts.show(ok, V3Tone.SUCCESS)
    ProductGalleryState.Outcome.Full -> toasts.show(StrProductGallery.full, V3Tone.WARNING)
    ProductGalleryState.Outcome.Busy -> Unit
    is ProductGalleryState.Outcome.Failed -> toasts.show(o.message.ifBlank { StrProductGallery.notSent }, V3Tone.ERROR)
  }

  Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
    when {
      state == null || port == null || state.off -> V3EmptyState(Icons.Outlined.PhotoLibrary, StrProductGallery.off)
      state.failed && state.items == null -> V3EmptyState(Icons.Outlined.CloudOff, StrProductGallery.failed, action = StrProductGallery.retry, onAction = { tick++ })
      state.items == null -> Text(StrProductGallery.loading, style = V3Type.callout, color = c.inkMuted, modifier = Modifier.padding(top = 16.dp))
      else -> {
        if (!port.canWrite) Text(StrProductGallery.readOnly, style = V3Type.footnote, color = c.inkMuted)
        //  PRD-27: عکسِ ویترین
        V3Section(StrProductGallery.storefront, footer = StrProductGallery.storefrontNote) {
          val own = state.storefrontItem
          V3ListRow(
            title = storefrontLine(state.storefront),
            leading = if (own == null) null else { { Thumb(state.thumbs[own.id]) } },
            chevron = false,
          )
          if (port.canWrite) {
            V3Button(
              StrProductGallery.setStorefront,
              { pickFor = ProductGallery.STOREFRONT_ROLE; sheet = true },
              Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
              kind = V3ButtonKind.SECONDARY, enabled = !state.busy,
            )
            if (own != null && port.hasDevicePhoto) {
              V3Button(
                StrProductGallery.useProductPhoto,
                {
                  scope.launch {
                    if (port.useDevicePhoto()) { toasts.show(StrProductGallery.productPhotoBack, V3Tone.SUCCESS); tick++ }
                    else toasts.show(StrProductGallery.notSent, V3Tone.ERROR)
                  }
                },
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                kind = V3ButtonKind.NEUTRAL, enabled = !state.busy,
              )
            }
          }
        }
        //  PRD-17: عکس‌های تکمیلی
        V3Section(StrProductGallery.extras, footer = StrProductGallery.extrasFooter(ProductGallery.MAX_EXTRAS)) {
          val extras = state.extras
          if (extras.isEmpty()) {
            Text(StrProductGallery.noExtras, style = V3Type.callout, color = c.inkMuted, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
          }
          for (x in extras) {
            V3ListRow(
              title = roleLabel(x.role),
              leading = { Thumb(state.thumbs[x.id]) },
              chevron = false,
              trailing = if (!port.canWrite) null else {
                {
                  V3Button(
                    StrProductGallery.remove,
                    {
                      scope.launch {
                        when (state.detach(x.id)) {
                          ProductGallery.Detach.DONE, ProductGallery.Detach.GONE -> toasts.show(StrProductGallery.removed, V3Tone.SUCCESS)
                          ProductGallery.Detach.SELECTED -> toasts.show(StrProductGallery.selected, V3Tone.WARNING)
                          ProductGallery.Detach.DENIED -> toasts.show(StrProductGallery.off, V3Tone.WARNING)
                          ProductGallery.Detach.FAILED -> toasts.show(StrProductGallery.notSent, V3Tone.ERROR)
                          null -> Unit
                        }
                      }
                    },
                    kind = V3ButtonKind.SECONDARY, enabled = !state.busy, height = 36.dp,
                  )
                }
              },
            )
          }
          if (port.canWrite) {
            val pad = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)
            V3Segmented(ProductGallery.EXTRA_ROLES.map(::roleLabel), role, { role = it }, pad)
            V3Button(
              if (state.busy) StrProductGallery.uploading else StrProductGallery.add,
              {
                if (!ProductGallery.canAdd(extras.size)) toasts.show(StrProductGallery.full, V3Tone.WARNING)
                else { pickFor = ProductGallery.EXTRA_ROLES[role.coerceIn(0, ProductGallery.EXTRA_ROLES.size - 1)]; sheet = true }
              },
              pad, enabled = !state.busy, loading = state.busy,
            )
          }
        }
      }
    }
  }

  if (state != null && port != null && port.canWrite) {
    GalleryPickSheet(open = sheet, onDismiss = { sheet = false }) { raw ->
      val target = pickFor
      scope.launch {
        val prep = raw?.let { port.prepare(it) }
        if (prep == null) { toasts.show(StrProductGallery.unreadable, V3Tone.WARNING); return@launch }
        if (target == ProductGallery.STOREFRONT_ROLE) shout(state.setStorefront(prep.first, prep.second), StrProductGallery.storefrontSaved)
        else shout(state.add(target, prep.first, prep.second), StrProductGallery.saved)
      }
    }
  }
}
