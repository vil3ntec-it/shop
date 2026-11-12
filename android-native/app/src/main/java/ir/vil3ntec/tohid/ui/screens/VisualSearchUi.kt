package ir.vil3ntec.tohid.ui.screens

// i18n: سه‌زبانه

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.LocaleChoice
import ir.vil3ntec.tohid.LocaleText
import ir.vil3ntec.tohid.core.i18n.StrVisualSearch
import ir.vil3ntec.tohid.core.net.ApiClient
import ir.vil3ntec.tohid.core.net.ApiFailure
import ir.vil3ntec.tohid.core.net.ApiResult
import ir.vil3ntec.tohid.core.product.VisualSearch
import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.repo.VisualSearchRepository
import ir.vil3ntec.tohid.data.repo.VisualSearchSource
import ir.vil3ntec.tohid.ui.components.common.PillTone
import ir.vil3ntec.tohid.ui.components.common.StatusPill
import ir.vil3ntec.tohid.ui.theme.Radius
import ir.vil3ntec.tohid.ui.theme.Shop
import kotlinx.coroutines.launch

/*
 *  AI-11 — «جست‌وجو با عکس» در صندوق و فهرستِ کالاها: کامپیوتر (`DeskSale`، `DeskProducts`) با دکمهٔ [VisualSearchEntry]؛ گوشیِ
 *  نسخهٔ ۳ (صندوق `ui/v3/sales/cart/SaleScreen` با ردیفِ «…»، فروشِ سریع و «کالاها» با دکمهٔ سربرگ) با [rememberVisualSearch] و
 *  [VisualSearchHost]. منطقِ خالص در `core/product/VisualSearch`. عکس از همان برگهٔ دوربین/گالریِ عکسِ کالا (`ParcelPhotoSource` ⇐ `PhotoSourceSheet`)، کوچک
 *  و JPEG، فقط برای همین درخواست. پاسخ فقط فهرستِ «مشابه» با نام، قیمت و عکسِ دفترِ همین دستگاه.
 *  ⛔ دکمه فقط وقتی سرور «روشن» گفت (`VisualSearchLive`، مثلِ `OwnerReportLive`)؛ پرچمِ خاموش ⇒ ۴۰۴ ⇒ هیچ‌چیز رسم نمی‌شود.
 *  ⛔ زدنِ ردیف فقط `onTap` با `VisualSearch.tap` (کادرِ جست‌وجو یا بازکردنِ کالا)؛ این فایل به سبد و فاکتور راه ندارد.
 */

/** «سرور گفت نه» — پرچم، اجازه، نشست یا سرورِ کهنه؛ نه خطای گذرای شبکه */
internal fun visualRefused(f: ApiFailure): Boolean =
  f is ApiFailure.Forbidden || f is ApiFailure.NotFound || f is ApiFailure.Unauthorized ||
    f is ApiFailure.SessionExpired || f is ApiFailure.NotConfigured

/** دکمهٔ «جست‌وجو با عکس» دیده شود؟ — فقط با `enabled: true` ِ سرور */
object VisualSearchLive {
  var visible by mutableStateOf(false)
    private set

  suspend fun load(src: VisualSearchSource) {
    val r = src.status()
    visible = VisualSearch.visibleAfter(visible, r.valueOrNull(), (r as? ApiResult.Failure)?.error?.let(::visualRefused) == true)
  }

  /** پرچم میانِ کار خاموش شد (۴۰۴) */
  internal fun refused() { visible = false }
}

internal fun visualLocal(p: Product?): VisualSearch.Local? =
  p?.let { VisualSearch.Local(it.name, it.salePrice, it.unit, it.photo, it.active) }

@Stable
internal class VisualSearchController(
  private val src: VisualSearchSource,
  private val place: VisualSearch.Place,
  private val local: (String) -> VisualSearch.Local?,
) {
  var sheet by mutableStateOf(VisualSearch.Sheet())
    private set

  /** [jpeg] null ⇒ عکس خوانده نشد */
  suspend fun send(jpeg: ByteArray?) {
    if (jpeg == null) { sheet = VisualSearch.failed(StrVisualSearch.unreadable); return }
    sheet = VisualSearch.sending()
    src.search(jpeg)
      .onSuccess { sheet = VisualSearch.answered(it, place, local) }
      .onFailure { f ->
        if (visualRefused(f)) { VisualSearchLive.refused(); sheet = VisualSearch.Sheet(); return@onFailure }
        sheet = VisualSearch.failed(if (f is ApiFailure.Invalid) StrVisualSearch.unreadable else f.userMessage)
      }
  }

  fun close() { sheet = VisualSearch.Sheet() }

  /** زدنِ ردیف: برگه بسته می‌شود و فقط همان کارِ نتیجهٔ جست‌وجو برمی‌گردد */
  fun tap(s: VisualSearch.Suggestion): VisualSearch.Tap { close(); return VisualSearch.tap(s, place) }
}

/** «جست‌وجو با عکس» ِ یک صفحه: دکمه یا ردیفِ منو فقط [start] را می‌زند؛ برگهٔ عکس و نتیجه با [VisualSearchHost] */
@Stable
class VisualSearchHandle internal constructor(internal val ctl: VisualSearchController) {
  /** برگهٔ دوربین/گالری باز است */
  var picking by mutableStateOf(false)
    internal set

  fun start() { picking = true }
}

/**
 *  [api] null (بی حساب) یا پرچمِ خاموش (سرور «روشن» نگفته، `VisualSearchLive`) ⇒ null: نه دکمه، نه ردیف، نه برگه.
 *  [products] دفترِ همین دستگاه (نام، قیمت و عکسِ ردیف‌ها).
 */
@Composable
fun rememberVisualSearch(api: ApiClient?, products: List<Product>, place: VisualSearch.Place): VisualSearchHandle? {
  if (api == null) return null
  val src = remember(api) { VisualSearchRepository(api) }
  LaunchedEffect(src) { VisualSearchLive.load(src) }
  val byId = rememberUpdatedState(remember(products) { products.associateBy { it.id } })
  val handle = remember(src, place) { VisualSearchHandle(VisualSearchController(src, place) { id -> visualLocal(byId.value[id]) }) }
  return if (VisualSearchLive.visible) handle else null
}

/** برگهٔ عکس و برگهٔ «مشابه» ِ [handle]؛ [onTap] همان کارِ نتیجهٔ جست‌وجوی معمولیِ همان صفحه (هرگز سبد) */
@Composable
fun VisualSearchHost(handle: VisualSearchHandle, onTap: (VisualSearch.Tap) -> Unit) {
  val ctl = handle.ctl
  val scope = rememberCoroutineScope()
  ParcelPhotoSource(open = handle.picking, note = StrVisualSearch.photoNote, onDismiss = { handle.picking = false }) { bytes -> scope.launch { ctl.send(bytes) } }
  if (ctl.sheet.open) VisualSearchSheet(ctl, onAgain = handle::start, onTap = { s -> onTap(ctl.tap(s)) })
}

/**
 *  دکمه و برگهٔ «جست‌وجو با عکس» (کامپیوتر). [api] null (بی حساب) یا پرچمِ خاموش ⇒ هیچ‌چیز. [products] دفترِ همین دستگاه؛
 *  [onTap] همان کارِ نتیجهٔ جست‌وجوی معمولیِ همان صفحه.
 */
@Composable
fun VisualSearchEntry(
  api: ApiClient?,
  products: List<Product>,
  place: VisualSearch.Place,
  modifier: Modifier = Modifier,
  onTap: (VisualSearch.Tap) -> Unit,
) {
  val h = rememberVisualSearch(api, products, place) ?: return
  TohidSecondaryButton(StrVisualSearch.button, onClick = h::start, modifier = modifier, icon = Icons.Filled.PhotoCamera)
  VisualSearchHost(h, onTap)
}

@Composable
private fun VisualSearchSheet(ctl: VisualSearchController, onAgain: () -> Unit, onTap: (VisualSearch.Suggestion) -> Unit) {
  val c = Shop.colors
  val s = ctl.sheet
  TohidBottomSheet(title = StrVisualSearch.title, onDismiss = { ctl.close() }) {
    SheetBody {
      when (s.phase) {
        VisualSearch.Phase.SENDING -> Row(verticalAlignment = Alignment.CenterVertically) {
          CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
          Spacer(Modifier.width(8.dp))
          Text(StrVisualSearch.busy, style = MaterialTheme.typography.bodyMedium, color = c.muted)
        }
        VisualSearch.Phase.FAILED -> Text(s.error.orEmpty(), style = MaterialTheme.typography.bodyMedium, color = c.danger)
        VisualSearch.Phase.DONE -> {
          val note = when {
            s.rows.isNotEmpty() -> StrVisualSearch.notice
            s.indexed == 0 -> StrVisualSearch.notIndexed
            else -> StrVisualSearch.none
          }
          Text(note, style = MaterialTheme.typography.labelSmall, color = c.muted2)
          for (row in s.rows) SuggestionRow(row) { onTap(row) }
        }
        VisualSearch.Phase.IDLE -> Unit
      }
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        TohidSecondaryButton(StrVisualSearch.again, onClick = onAgain, enabled = s.phase != VisualSearch.Phase.SENDING, icon = Icons.Filled.PhotoCamera)
        TextButton(onClick = { ctl.close() }) { Text(StrVisualSearch.close) }
      }
    }
  }
}

@Composable
private fun SuggestionRow(s: VisualSearch.Suggestion, onClick: () -> Unit) {
  val c = Shop.colors
  val shape = RoundedCornerShape(Radius.md)
  Row(
    Modifier.fillMaxWidth().clip(shape).border(1.dp, c.border, shape).clickable(onClick = onClick).padding(10.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    if (s.photo) {
      ProductPhoto(productId = s.productId, size = 44.dp)
      Spacer(Modifier.width(10.dp))
    }
    Column(Modifier.weight(1f)) {
      Text(s.name, style = MaterialTheme.typography.bodyMedium, color = c.text, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
      Text(
        LocaleText.afghani(s.price, LocaleChoice.digits, LocaleChoice.symbol) + if (s.unit.isNotBlank()) " / ${s.unit}" else "",
        style = MaterialTheme.typography.labelMedium, color = c.primary, maxLines = 1,
      )
    }
    Spacer(Modifier.width(8.dp))
    StatusPill(StrVisualSearch.similar, PillTone.INFO)
  }
}
