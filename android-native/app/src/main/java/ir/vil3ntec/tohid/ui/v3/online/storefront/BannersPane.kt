package ir.vil3ntec.tohid.ui.v3.online.storefront

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.calendar.CivilDays
import ir.vil3ntec.tohid.core.i18n.StrKit
import ir.vil3ntec.tohid.core.i18n.StrOnline
import ir.vil3ntec.tohid.core.storefront.StorefrontBanners
import ir.vil3ntec.tohid.core.storefront.StorefrontBanners.Draft
import ir.vil3ntec.tohid.core.storefront.StorefrontBanners.Err
import ir.vil3ntec.tohid.core.storefront.StorefrontOwner
import ir.vil3ntec.tohid.data.repo.StorefrontBannersRepository
import ir.vil3ntec.tohid.data.repo.StorefrontDeliveryRepository
import ir.vil3ntec.tohid.data.repo.StorefrontProductDto
import ir.vil3ntec.tohid.formatDate
import ir.vil3ntec.tohid.todayIso
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.PhoneV3Roles
import ir.vil3ntec.tohid.ui.v3.LocalV3Toasts
import ir.vil3ntec.tohid.ui.v3.V3AddChip
import ir.vil3ntec.tohid.ui.v3.V3Chip
import ir.vil3ntec.tohid.ui.v3.V3Divider
import ir.vil3ntec.tohid.ui.v3.V3EmptyState
import ir.vil3ntec.tohid.ui.v3.V3FieldKind
import ir.vil3ntec.tohid.ui.v3.V3ListRow
import ir.vil3ntec.tohid.ui.v3.V3Section
import ir.vil3ntec.tohid.ui.v3.V3Segmented
import ir.vil3ntec.tohid.ui.v3.V3Skeleton
import ir.vil3ntec.tohid.ui.v3.V3TextField
import ir.vil3ntec.tohid.ui.v3.V3Type

/**
 *  WEB-24 — برگهٔ «بنرها» ِ صفحهٔ ویترین (پرچمِ `shop.storefront.banners` ⇐ `config.banners`): خواندن و نوشتنِ
 *  یک‌جای `GET/PUT /api/shop/storefront/banners`. سنجش همان `core/storefront/StorefrontBanners` (قاعده‌های سرور)؛
 *  روزها به وقتِ دکان (`StorefrontOwner.dayStartMs`). «ثبت شد» فقط با پاسخِ ۲xx و فرم از همان پاسخ از نو پر می‌شود.
 */
@Stable
internal class BannersController(
  private val repo: StorefrontBannersRepository?,
  private val zonesRepo: StorefrontDeliveryRepository?,
) {
  var loaded by mutableStateOf(false)
    private set
  var loadError by mutableStateOf<String?>(null)
    private set
  var drafts by mutableStateOf<List<Draft>>(emptyList())
    private set
  private var base by mutableStateOf<List<Draft>>(emptyList())
  var max by mutableStateOf(StorefrontBanners.MAX)
    private set
  /** ناحیه‌های «ارسال و زمان» (شناسه ⇐ نام)؛ null = ناحیه‌ها خوانده نشد ⇒ شناسه‌های بنرها ناشناخته شمرده نمی‌شوند */
  var zones by mutableStateOf<List<Pair<String, String>>?>(emptyList())
    private set
  var errors by mutableStateOf<Map<String, Err>>(emptyMap())
    private set
  var tried by mutableStateOf(false)
    private set
  var saving by mutableStateOf(false)
    private set

  val dirty: Boolean get() = loaded && drafts != base

  private fun known(): Set<String> = zones?.map { it.first }?.toSet() ?: drafts.flatMap { it.zoneIds }.toSet()

  fun check(tz: String): StorefrontBanners.Checked = StorefrontBanners.check(drafts, known()) { StorefrontOwner.dayStartMs(it, tz) }

  private fun show(list: List<StorefrontBanners.Banner>, tz: String) {
    drafts = list.map { b -> StorefrontBanners.draftOf(b) { StorefrontOwner.dayOfMs(it, tz) } }
    base = drafts
    errors = emptyMap()
    tried = false
    loaded = true
  }

  suspend fun load(tz: String, withZones: Boolean) {
    val r = repo ?: return
    loadError = null
    if (withZones && zonesRepo != null) {
      zonesRepo.load().onSuccess { d -> zones = d.zones.map { it.id to it.name } }.onFailure { zones = null }
    } else zones = emptyList()
    r.load().onSuccess { d -> max = d.max; show(d.banners.map { it.banner() }, tz) }.onFailure { loadError = it.userMessage }
  }

  fun edit(i: Int, d: Draft, tz: String) {
    drafts = drafts.toMutableList().also { it[i] = d }
    if (tried) errors = check(tz).errors
  }

  fun add(today: String) { drafts = drafts + StorefrontBanners.fresh(today) }

  fun remove(i: Int, tz: String) {
    drafts = drafts.filterIndexed { j, _ -> j != i }
    if (tried) errors = check(tz).errors
  }

  fun restore(list: List<Draft>) { drafts = list }

  /** @return null = ذخیره شد؛ وگرنه پیامِ کوتاه (خطای کادرها یا پیامِ سرور) */
  suspend fun save(tz: String): String? {
    val r = repo ?: return null
    tried = true
    val c = check(tz)
    errors = c.errors
    val list = c.banners ?: return c.errors["list"]?.let(BannersText::error) ?: StrOnline.fixFields
    saving = true
    var out: String? = null
    r.save(list).onSuccess { d -> max = d.max; show(d.banners.map { it.banner() }, tz) }.onFailure { out = it.userMessage }
    saving = false
    return out
  }
}

/** متنِ خطا و نشانِ هر بنر — خالص (آزمون: `StorefrontBannersUiTest`) */
internal object BannersText {
  fun error(e: Err): String = when (e) {
    Err.TOO_MANY -> StrOnline.bnErrTooMany(StorefrontBanners.MAX)
    Err.TITLE_REQUIRED -> StrOnline.bnErrTitle
    Err.TITLE_LONG -> StrOnline.bnErrTooLong(StorefrontBanners.TITLE_MAX)
    Err.BODY_LONG -> StrOnline.bnErrTooLong(StorefrontBanners.BODY_MAX)
    Err.START_BAD, Err.END_BAD -> StrOnline.bnErrDate
    Err.END_BEFORE_START -> StrOnline.bnErrEndBefore
    Err.SPAN_TOO_LONG -> StrOnline.bnErrSpan(StorefrontBanners.MAX_DAYS)
    Err.PRODUCT_REQUIRED -> StrOnline.bnErrProduct
    Err.CATEGORY_REQUIRED -> StrOnline.bnErrCategory
    Err.CATEGORY_LONG -> StrOnline.bnErrTooLong(StorefrontBanners.CATEGORY_MAX)
    Err.SORT_BAD -> StrOnline.bnErrSort(StorefrontBanners.SORT_MAX)
    Err.ZONES_GONE -> StrOnline.bnErrZonesGone
  }

  fun whenLabel(w: StorefrontBanners.When): String = when (w) {
    StorefrontBanners.When.LIVE -> StrOnline.bnLive
    StorefrontBanners.When.LATER -> StrOnline.bnLater
    StorefrontBanners.When.ENDED -> StrOnline.bnEnded
  }

  /** کالاهای فروختنیِ ویترین که نامشان [query] را دارد — حداکثر [limit] */
  fun productMatches(products: List<StorefrontProductDto>, query: String, limit: Int = 6): List<StorefrontProductDto> {
    val q = query.trim()
    return products.filter { it.published && it.active && (q.isEmpty() || it.name.contains(q, ignoreCase = true)) }.take(limit)
  }

  /** دسته‌های کالاهای ویترین (همان نام‌هایی که مشتری می‌بیند) */
  fun categories(products: List<StorefrontProductDto>): List<String> =
    products.filter { it.published && it.active }.map { it.category.trim() }.filter { it.isNotEmpty() }.distinct().sorted()
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BannersPane(ctl: BannersController, products: List<StorefrontProductDto>?, tz: String, onRetry: () -> Unit, pad: PaddingValues) {
  val toasts = LocalV3Toasts.current
  when {
    !ctl.loaded && ctl.loadError == null -> { Column(Modifier.fillMaxSize().padding(16.dp)) { V3Skeleton(rows = 3) }; return }
    !ctl.loaded -> {
      V3EmptyState(Icons.Outlined.CloudOff, StrOnline.bnLoadFailed, body = ctl.loadError, action = StrKit.retry, onAction = onRetry)
      return
    }
  }
  val now = System.currentTimeMillis()
  Column(
    Modifier.fillMaxSize().verticalScroll(rememberScrollState())
      .padding(start = 16.dp, end = 16.dp, bottom = pad.calculateBottomPadding()),
  ) {
    V3Section(StrOnline.bnSection, footer = StrOnline.bnHelp(ctl.max)) {
      if (ctl.drafts.isEmpty()) V3ListRow(StrOnline.bnEmpty, tile = false, chevron = false)
      ctl.drafts.forEachIndexed { i, d ->
        if (i > 0) V3Divider()
        BannerCard(i, d, ctl, products.orEmpty(), tz, now) {
          val before = ctl.drafts
          ctl.remove(i, tz)
          toasts?.undo(StrOnline.bnRemoved) { ctl.restore(before) }
        }
      }
      ctl.errors["list"]?.let { Text(BannersText.error(it), style = V3Type.footnote, color = PhoneV3Roles.errorText(PhoneV3.colors), modifier = Modifier.padding(12.dp)) }
      if (ctl.drafts.size < ctl.max) {
        V3Divider()
        FlowRow(Modifier.padding(12.dp)) { V3AddChip(StrOnline.bnAdd) { ctl.add(todayIso()) } }
      }
    }
    Spacer(Modifier.height(24.dp))
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BannerCard(
  i: Int,
  d: Draft,
  ctl: BannersController,
  products: List<StorefrontProductDto>,
  tz: String,
  now: Long,
  onRemove: () -> Unit,
) {
  val k = "b$i"
  val err = { key: String -> ctl.errors["$k.$key"]?.let(BannersText::error) }
  val set = { n: Draft -> ctl.edit(i, n, tz) }
  //  نشانِ «اکنون / هنوز نرسیده / تمام شده» فقط برای کارتِ درست (همان بازهٔ سرور)
  val status = remember(d, tz) {
    StorefrontBanners.check(listOf(d.copy(zoneIds = emptySet())), emptySet()) { StorefrontOwner.dayStartMs(it, tz) }.banners?.single()
  }
  Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Row {
      Text(StrOnline.bnN(i + 1), style = V3Type.subhead, color = PhoneV3.colors.ink, modifier = Modifier.weight(1f))
      status?.let { Text(BannersText.whenLabel(StorefrontBanners.whenOf(it, now)), style = V3Type.footnote, color = PhoneV3.colors.inkMuted) }
    }
    V3TextField(d.title, { set(d.copy(title = it)) }, StrOnline.bnTitle, required = true, error = err("title"), modifier = Modifier.fillMaxWidth())
    V3TextField(d.body, { set(d.copy(body = it)) }, StrOnline.bnBody, error = err("body"), singleLine = false, modifier = Modifier.fillMaxWidth())
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      V3TextField(
        d.startDay, { set(d.copy(startDay = it.trim())) }, StrOnline.bnFrom, kind = V3FieldKind.DATE, placeholder = todayIso(),
        helper = d.startDay.takeIf { it.length >= 8 }?.let(::formatDate), error = err("start"), modifier = Modifier.weight(1f),
      )
      V3TextField(
        d.endDay, { set(d.copy(endDay = it.trim())) }, StrOnline.bnTo, kind = V3FieldKind.DATE, placeholder = todayIso(),
        helper = d.endDay.takeIf { it.length >= 8 }?.let(::formatDate), error = err("end"), modifier = Modifier.weight(1f),
      )
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      val today = todayIso()
      V3Chip(ir.vil3ntec.tohid.core.i18n.Str.today, d.startDay == today, { set(d.copy(startDay = today)) })
      listOf(6 to StrOnline.bnWeek, 29 to StrOnline.bnMonth).forEach { (plus, label) ->
        val end = CivilDays.addDays(d.startDay, plus)
        if (end != null) V3Chip(label, d.endDay == end, { set(d.copy(endDay = end)) })
      }
    }

    Text(StrOnline.bnLink, style = V3Type.subhead, color = PhoneV3.colors.inkMuted)
    val links = StorefrontBanners.LINKS
    V3Segmented(listOf(StrOnline.bnLinkNone, StrOnline.bnLinkProduct, StrOnline.bnLinkCategory), links.indexOf(d.link).coerceAtLeast(0), { set(d.copy(link = links[it])) })
    when (d.link) {
      StorefrontBanners.PRODUCT -> {
        var q by rememberSaveable(i) { mutableStateOf("") }
        val picked = products.firstOrNull { it.id == d.productId }
        if (d.productId.isNotBlank()) Text(StrOnline.bnLinkedTo(picked?.name ?: d.productId), style = V3Type.callout, color = PhoneV3.colors.ink)
        V3TextField(q, { q = it }, StrOnline.bnFindProduct, error = err("link"), modifier = Modifier.fillMaxWidth())
        val hits = BannersText.productMatches(products, q)
        if (hits.isEmpty()) Text(StrOnline.bnNoProducts, style = V3Type.footnote, color = PhoneV3.colors.inkMuted)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          hits.forEach { p -> V3Chip(p.name, p.id == d.productId, { set(d.copy(productId = p.id)) }) }
        }
      }
      StorefrontBanners.CATEGORY -> {
        V3TextField(d.category, { set(d.copy(category = it)) }, StrOnline.bnCategory, error = err("link"), modifier = Modifier.fillMaxWidth())
        val cats = BannersText.categories(products)
        if (cats.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          cats.forEach { c -> V3Chip(c, d.category.trim() == c, { set(d.copy(category = c)) }) }
        }
      }
      else -> Unit
    }

    val zones = ctl.zones.orEmpty()
    if (zones.isNotEmpty() || d.zoneIds.isNotEmpty()) {
      Text(StrOnline.bnZones, style = V3Type.subhead, color = PhoneV3.colors.inkMuted)
      val known = zones.map { it.first }.toSet()
      val gone = if (ctl.zones == null) emptySet() else d.zoneIds - known
      FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        V3Chip(StrOnline.bnEverywhere, d.zoneIds.isEmpty(), { set(d.copy(zoneIds = emptySet())) })
        zones.forEach { (id, name) ->
          val on = id in d.zoneIds
          V3Chip(name, on, { set(d.copy(zoneIds = if (on) d.zoneIds - id else d.zoneIds + id)) })
        }
        //  ناحیهٔ برداشته‌شده: دیده می‌شود تا بی‌صدا «همه‌جا» نشود؛ لمس ⇒ برداشتن
        if (gone.isNotEmpty()) V3Chip(StrOnline.bnZoneGone, true, { set(d.copy(zoneIds = d.zoneIds - gone)) }, count = gone.size)
      }
      err("zones")?.let { Text(it, style = V3Type.footnote, color = PhoneV3Roles.errorText(PhoneV3.colors)) }
    }
    V3TextField(d.sort, { set(d.copy(sort = it)) }, StrOnline.bnSort(StorefrontBanners.SORT_MAX), kind = V3FieldKind.NUMBER, error = err("sort"), modifier = Modifier.fillMaxWidth())
  }
  V3ListRow(StrOnline.bnRemove, icon = Icons.Outlined.DeleteOutline, danger = true, chevron = false, onClick = onRemove)
}
