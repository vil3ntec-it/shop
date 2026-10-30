package ir.vil3ntec.tohid.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.storefront.MarketListing
import ir.vil3ntec.tohid.core.storefront.MarketListing.Gate
import ir.vil3ntec.tohid.core.storefront.MarketListing.LocationEdit
import ir.vil3ntec.tohid.core.storefront.MarketListingDto
import ir.vil3ntec.tohid.core.storefront.MarketListingRepository
import ir.vil3ntec.tohid.core.storefront.MarketStateDto
import ir.vil3ntec.tohid.core.storefront.StorefrontOwner
import ir.vil3ntec.tohid.data.DeviceLocation
import ir.vil3ntec.tohid.core.storefront.SendOutcome
import ir.vil3ntec.tohid.core.storefront.StorefrontChange
import ir.vil3ntec.tohid.core.storefront.StorefrontOutbox
import ir.vil3ntec.tohid.data.repo.Backend
import ir.vil3ntec.tohid.data.repo.StorefrontQueue
import ir.vil3ntec.tohid.data.repo.StorefrontConfigDto
import ir.vil3ntec.tohid.data.repo.StorefrontSettingsDto
import ir.vil3ntec.tohid.ui.Platform
import ir.vil3ntec.tohid.ui.theme.Shop
import kotlinx.coroutines.launch

private const val MARKET_OFF_NOTE = "بازارچه هنوز برای مشتری باز نیست. وقتی باز شد، همین‌جا می‌توانید دکان را در فهرستش بگذارید."

/**
 *  «نمایش در بازارچه» — برگهٔ صفحهٔ ویترین (`GET/PUT /api/shop/market`).
 *
 *  بازارچه فهرستِ عمومیِ دکان‌ها در سایتِ مشتری است؛ دکان فقط با روشن کردنِ
 *  همین کلید در آن دیده می‌شود (پیش‌فرض خاموش). فقط صاحب دکان یا مدیر
 *  (`settings.write`) با اشتراکِ ویترین می‌تواند عوض کند؛ بقیه فقط می‌بینند.
 *  بازارچهٔ خاموش یا ویترینِ خاموش ⇒ یک یادداشتِ کوتاه، بی دکمهٔ بی‌اثر.
 */
@Composable
internal fun MarketPane(config: StorefrontConfigDto, snackbar: SnackbarHostState) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val repo = remember(context) { MarketListingRepository(Backend.api(context)) }
  var state by remember { mutableStateOf<MarketStateDto?>(null) }
  //  null = در حالِ خواندن
  var gate by remember { mutableStateOf<Gate?>(null) }
  var loadError by remember { mutableStateOf("") }

  suspend fun reload() {
    gate = null
    repo.load()
      .onSuccess { state = it; gate = Gate.READY }
      .onFailure { e -> loadError = e.userMessage; gate = MarketListing.gateOf(e.status, e.code) }
  }
  LaunchedEffect(Unit) { reload() }

  Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
    val s = state
    when {
      gate == null -> TohidLoadingState(rows = 3)
      gate == Gate.ERROR -> TohidErrorState("بخشِ بازارچه باز نشد", loadError.ifBlank { "اتصال را بررسی کنید." }, onRetry = { scope.launch { reload() } })
      gate == Gate.MARKET_OFF || (gate == Gate.READY && s != null && !s.directory) ->
        TohidEmptyState("بازارچه هنوز باز نیست", MARKET_OFF_NOTE, icon = Icons.Filled.Storefront)
      gate == Gate.STOREFRONT_OFF ->
        TohidEmptyState("ویترین برای این دکان روشن نیست", "نمایش در بازارچه پس از روشن شدنِ ویترین در دسترس است.", icon = Icons.Filled.Storefront)
      gate == Gate.NO_PERMISSION ->
        TohidEmptyState("فقط صاحب دکان یا مدیر", "نمایش در بازارچه را صاحب دکان یا مدیر تنظیم می‌کند.", icon = Icons.Filled.Storefront)
      else -> s?.let { st ->
        if (!config.permissions.manage || !config.entitled) MarketReadOnly(st.listing, config)
        else MarketForm(st.listing, config.settings, snackbar) {
          //  بی «در حالِ خواندن»: پیامِ «ثبت شد» ِ فرم سرِ جایش بماند
          scope.launch { repo.load().onSuccess { state = it } }
        }
      }
    }
  }
}

private enum class SaveState { IDLE, SAVING, SAVED, FAILED }

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MarketForm(
  serverListing: MarketListingDto,
  storefront: StorefrontSettingsDto?,
  snackbar: SnackbarHostState,
  onConfirmed: () -> Unit,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  //  ویرایشِ نرسیده (صفِ آفلاین) روی دادهٔ سرور — کاربر کارِ خودش را می‌بیند
  val pendingEntry = StorefrontQueue.entries.value.let { StorefrontQueue.pending(context, StorefrontChange.MARKET) }
  val listing = StorefrontQueue.marketWithPending(serverListing, pendingEntry)
  val cats = remember(serverListing) { MarketListing.categoriesOf(serverListing) }
  //  پس از ثبت `updatedAt` عوض می‌شود و فرم از پاسخِ سرور از نو پر می‌شود
  val key = serverListing.updatedAt
  var listed by remember(key) { mutableStateOf(listing.listed) }
  var category by remember(key) { mutableStateOf(listing.category) }
  var area by remember(key) { mutableStateOf(listing.area) }
  var locEdit by remember(key) { mutableStateOf<LocationEdit>(LocationEdit.Keep) }
  var categoryError by remember { mutableStateOf<String?>(null) }
  var save by remember { mutableStateOf(SaveState.IDLE) }
  var saveError by remember { mutableStateOf("") }
  var savedText by remember { mutableStateOf("") }
  //  «ثبت شد» فقط با پاسخِ ۲xx ِ سرور (گزارشِ صف)، نه هنگامِ گذاشتن در صف (§۳۱.۲ بندِ ۲)
  LaunchedEffect(Unit) {
    StorefrontQueue.reports.collect { r ->
      if (r.change != StorefrontChange.MARKET) return@collect
      when (r.outcome) {
        SendOutcome.CONFIRMED -> { save = SaveState.SAVED; savedText = r.message; onConfirmed() }
        SendOutcome.REJECTED -> { save = SaveState.FAILED; saveError = r.message }
        SendOutcome.HOLD -> save = SaveState.IDLE
      }
    }
  }
  var locating by remember { mutableStateOf(false) }
  var locNote by remember { mutableStateOf<String?>(null) }

  fun touched() { if (save != SaveState.SAVING) save = SaveState.IDLE }

  val busy = save == SaveState.SAVING && StorefrontQueue.sending.value
  val shownLocation = when (val l = locEdit) {
    LocationEdit.Keep -> listing.location
    LocationEdit.Clear -> null
    is LocationEdit.Set -> l.location
  }

  /** فقط درشت نگه داشته می‌شود — نقطهٔ دقیقِ دستگاه همین‌جا گرد می‌شود */
  fun locate() {
    locating = true
    locNote = null
    scope.launch {
      val fix = DeviceLocation.current(context)
      locating = false
      val coarse = fix?.let { MarketListing.coarseOf(it.lat, it.lng) }
      if (coarse == null) locNote = "موقعیت پیدا نشد؛ مکان‌یابی را روشن کنید و دوباره بزنید."
      else { locEdit = LocationEdit.Set(coarse); touched() }
    }
  }
  val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
    if (granted.values.any { it }) locate()
    else locNote = "اجازهٔ مکان‌یابی داده نشد. بی موقعیت هم دکان در بازارچه دیده می‌شود."
  }

  val areaError = MarketListing.areaError(area)

  Panel(Modifier.fillMaxWidth()) { Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text("نمایش در بازارچه", style = MaterialTheme.typography.titleSmall)
    Text("بازارچه فهرستِ عمومیِ دکان‌ها در سایتِ مشتری است؛ با روشن کردنِ این کلید، مشتریِ تازه دکان و ویترینتان را پیدا می‌کند.",
      style = MaterialTheme.typography.bodySmall, color = Shop.colors.muted)
    SwitchRow("در بازارچه نشان بده", listed) { listed = it; categoryError = null; touched() }
    MarketListing.hiddenReason(listed, storefront?.enabled == true, storefront != null)?.let {
      Text(it, style = MaterialTheme.typography.bodySmall, color = Shop.colors.warning)
    }
    TohidDropdown(
      label = "دستهٔ دکان",
      options = cats.map { it.label },
      selected = MarketListing.labelOf(category, cats),
      onSelect = { label -> cats.firstOrNull { it.label == label }?.let { category = it.key; categoryError = null; touched() } },
      modifier = Modifier.fillMaxWidth(),
    )
    categoryError?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = Shop.colors.danger) }
    TohidTextField(
      area, { area = it; touched() }, "ناحیه یا گذر (اختیاری)",
      placeholder = "مثلاً کارتهٔ سه",
      supporting = StorefrontOwner.toFa("${MarketListing.cleanArea(area).length}/${MarketListing.AREA_MAX}") + " — مشتری این را کنارِ نامِ دکان می‌بیند",
      error = areaError,
    )
  } }

  Panel(Modifier.fillMaxWidth()) { Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text("موقعیتِ تقریبیِ دکان (اختیاری)", style = MaterialTheme.typography.titleSmall)
    Text("فقط حدودِ یک کیلومتر نگه داشته می‌شود، نه نشانیِ دقیق؛ برای «نزدیکِ من» ِ مشتری. مشتری خودِ عدد را نمی‌بیند.",
      style = MaterialTheme.typography.bodySmall, color = Shop.colors.muted)
    Text(MarketListing.locationText(shownLocation), style = MaterialTheme.typography.bodyMedium, color = Shop.colors.text)
    if (Platform.DESKTOP) {
      Text("این دستگاه مکان‌یاب ندارد؛ موقعیت را از برنامهٔ تلفنِ همراه بگذارید.",
        style = MaterialTheme.typography.bodySmall, color = Shop.colors.muted)
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      if (!Platform.DESKTOP) {
        TohidSecondaryButton(
          if (locating) "در حالِ یافتنِ موقعیت…" else "گذاشتنِ موقعیتِ همین‌جا",
          onClick = { if (DeviceLocation.granted(context)) locate() else ask.launch(DeviceLocation.PERMISSIONS) },
          enabled = !locating && !busy,
        )
      }
      if (shownLocation != null) {
        TohidSecondaryButton("برداشتنِ موقعیت", onClick = { locEdit = LocationEdit.Clear; locNote = null; touched() },
          enabled = !busy)
      }
    }
    locNote?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Shop.colors.warning) }
  } }

  TohidButton(if (busy) "در حالِ فرستادن…" else "ذخیرهٔ بازارچه", onClick = {
    val form = MarketListing.Form(listed, category, area, locEdit)
    val errors = MarketListing.errors(form, cats)
    categoryError = errors["category"]
    if (errors.isNotEmpty()) return@TohidButton
    //  اول روی دیسک (صف)، بعد سرور
    val e = StorefrontQueue.enqueue(context, StorefrontChange.MARKET, MarketListing.body(form).toString(),
      pendingEntry?.base ?: StorefrontQueue.marketBase(serverListing))
    if (e == null) { saveError = "دکانِ این نشست معلوم نیست؛ یک بار از حساب بیرون و دوباره وارد شوید."; save = SaveState.FAILED; return@TohidButton }
    save = SaveState.SAVING
    scope.launch { StorefrontQueue.flush(context, force = true) }
  }, enabled = !busy && !locating, busy = busy, modifier = Modifier.fillMaxWidth())

  when (save) {
    SaveState.SAVED -> Text(savedText, style = MaterialTheme.typography.bodySmall, color = Shop.colors.success)
    SaveState.FAILED -> Text("ذخیره نشد: $saveError", style = MaterialTheme.typography.bodySmall, color = Shop.colors.danger)
    else -> pendingEntry?.let { Text(StorefrontOutbox.statusLine(it), style = MaterialTheme.typography.bodySmall, color = Shop.colors.warning) }
  }
}

/** کاربرِ بی اجازهٔ تنظیم یا دکانِ بی اشتراکِ ویترین: فقط دیدن، بی کلید و دکمه */
@Composable
private fun MarketReadOnly(listing: MarketListingDto, config: StorefrontConfigDto) {
  val cats = MarketListing.categoriesOf(listing)
  Panel(Modifier.fillMaxWidth()) { Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Text("نمایش در بازارچه", style = MaterialTheme.typography.titleSmall)
    Text(if (listing.listed) "دکان در بازارچه نشان داده می‌شود." else "دکان در بازارچه نشان داده نمی‌شود.",
      style = MaterialTheme.typography.bodyMedium, color = if (listing.listed) Shop.colors.success else Shop.colors.muted)
    Text("دسته: " + MarketListing.labelOf(listing.category, cats).ifBlank { "—" }, style = MaterialTheme.typography.bodySmall, color = Shop.colors.text)
    Text("ناحیه: " + listing.area.ifBlank { "—" }, style = MaterialTheme.typography.bodySmall, color = Shop.colors.text)
    Text("موقعیتِ تقریبی: " + MarketListing.locationText(listing.location), style = MaterialTheme.typography.bodySmall, color = Shop.colors.text)
    Text(
      if (!config.permissions.manage) "تغییرِ این بخش کارِ صاحب دکان یا مدیر است."
      else "اشتراکِ این دکان ویترین را در بر ندارد؛ تغییرِ بازارچه ذخیره نمی‌شود.",
      style = MaterialTheme.typography.bodySmall, color = Shop.colors.warning,
    )
  } }
}
