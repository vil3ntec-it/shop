package ir.vil3ntec.tohid.ui.v3.online.storefront

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrKit
import ir.vil3ntec.tohid.core.i18n.StrOnline
import ir.vil3ntec.tohid.core.storefront.MarketListingRepository
import ir.vil3ntec.tohid.core.storefront.StorefrontDelivery
import ir.vil3ntec.tohid.data.DeviceLocation
import ir.vil3ntec.tohid.data.repo.Backend
import ir.vil3ntec.tohid.data.repo.StorefrontDeliveryDto
import ir.vil3ntec.tohid.data.repo.StorefrontDeliveryRepository
import ir.vil3ntec.tohid.data.repo.StorefrontRepository
import ir.vil3ntec.tohid.ui.Platform
import ir.vil3ntec.tohid.ui.screens.Haptics
import ir.vil3ntec.tohid.ui.screens.StorefrontGate
import ir.vil3ntec.tohid.ui.v3.V3ConfirmSheet
import ir.vil3ntec.tohid.ui.v3.V3EmptyState
import ir.vil3ntec.tohid.ui.v3.V3FormBar
import ir.vil3ntec.tohid.ui.v3.V3Skeleton
import ir.vil3ntec.tohid.ui.v3.nav.RouteScope
import ir.vil3ntec.tohid.ui.v3.online.OnlinePaths
import kotlinx.coroutines.launch

/*
 *  پیوندِ مسیرهای ویترین به برگه‌های بی‌حالت: بارِ پیکربندی (`StorefrontGate`)، صفِ اعلام، فرمِ
 *  تنظیمات، کالاها (`StorefrontHub`)، بازارچه (`MarketController`) و «ارسال و زمان» — هر کدام با همان
 *  مخزنِ امروز. «ذخیره» ِ هر برگه نوارِ چسبانِ پایینِ همان صفحه است.
 */

@Composable
internal fun RouteScope.StorefrontRoute() {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val repo = remember(context) { StorefrontRepository(Backend.api(context)) }
  var loading by remember { mutableStateOf(StorefrontGate.config.value == null) }
  var error by remember { mutableStateOf<String?>(null) }
  suspend fun reload() {
    loading = true
    repo.config().onSuccess { StorefrontGate.config.value = it; error = null }.onFailure { error = it.userMessage }
    loading = false
  }
  LaunchedEffect(Unit) { reload() }
  val cfg = StorefrontGate.config.value
  var tab by rememberSaveable { mutableStateOf(StorefrontModel.initialTab(args.query("tab"), cfg)) }
  LaunchedEffect(cfg?.zones) { if (cfg != null && tab !in StorefrontModel.tabs(cfg)) tab = StorefrontModel.STATUS }
  val settings = cfg?.settings
  val tz = settings?.timezone ?: "Asia/Kabul"

  //  وضعیت: صفِ اعلامِ نرسیده همان لحظهٔ باز شدن دوباره فرستاده می‌شود (PRES-08)
  val sender = remember(context) { PresenceSender(context, repo) }
  LaunchedEffect(Unit) { if (sender.pending != null) sender.flush(tz) }

  //  تنظیمات: فرم بیرون از برگه تا با عوضِ برگه گم نشود؛ نوشتهٔ ذخیره‌نشده با برگشت می‌پرسد
  var form by remember(settings?.updatedAt) { mutableStateOf(StorefrontSettingsModel.from(settings)) }
  var formErrors by remember(settings?.updatedAt) { mutableStateOf(emptyMap<String, String>()) }
  var saving by remember { mutableStateOf(false) }
  var askDiscard by remember { mutableStateOf(false) }
  val dirty = cfg != null && form != StorefrontSettingsModel.from(settings)
  BackHandler(enabled = dirty) { askDiscard = true }

  //  کالاها
  var bulkBusy by remember { mutableStateOf(false) }
  var bulkReport by remember { mutableStateOf<String?>(null) }
  var bulkOk by remember { mutableStateOf(true) }
  LaunchedEffect(tab) { if (tab == StorefrontModel.PRODUCTS && StorefrontHub.products.value == null) StorefrontHub.load(repo) }

  //  بازارچه
  val market = remember(context) { MarketController(MarketListingRepository(Backend.api(context))) }
  LaunchedEffect(tab) { if (tab == StorefrontModel.MARKET && market.state == null) market.reload() }
  val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
    if (granted.values.any { it }) scope.launch { market.locate(context) } else market.denied()
  }

  //  ارسال و زمان
  val zonesRepo = remember(context) { StorefrontDeliveryRepository(Backend.api(context)) }
  var zones by remember { mutableStateOf<StorefrontDeliveryDto?>(null) }
  var zonesError by remember { mutableStateOf<String?>(null) }
  var zonesVersion by remember { mutableIntStateOf(0) }
  var zonesForm by remember(zonesVersion) { mutableStateOf(zones?.let(::zonesFormOf) ?: ZonesForm()) }
  var zonesErrors by remember(zonesVersion) { mutableStateOf(emptyMap<String, String>()) }
  var zonesSaving by remember { mutableStateOf(false) }
  suspend fun loadZones() {
    zonesError = null
    zonesRepo.load().onSuccess { zones = it; zonesVersion++ }.onFailure { zonesError = it.userMessage }
  }
  LaunchedEffect(tab) { if (tab == StorefrontModel.DELIVERY && zones == null) loadZones() }

  val bottomBar: (@Composable () -> Unit)? = when (tab) {
    StorefrontModel.SETTINGS -> ({
      V3FormBar(StrOnline.sfSave, {
        val r = StorefrontSettingsModel.build(form)
        formErrors = r.errors
        val body = r.body
        if (body == null) toasts.show(StrOnline.fixFields)
        else if (!saving) {
          saving = true
          scope.launch {
            repo.save(body)
              .onSuccess { saved ->
                StorefrontGate.config.value = StorefrontGate.config.value?.copy(settings = saved)
                Haptics.success(context)
                toasts.show(StrOnline.sfSaved)
              }
              .onFailure { toasts.show(it.userMessage) }
            saving = false
          }
        }
      }, loading = saving)
    })
    StorefrontModel.MARKET -> if (cfg != null && marketEditable(market, cfg)) ({
      V3FormBar(StrOnline.mkSave, {
        scope.launch { if (market.submit()) { Haptics.success(context); toasts.show(StrOnline.saved) } }
      }, primaryEnabled = !market.locating, loading = market.save == MarketController.Save.SAVING)
    }) else null
    StorefrontModel.DELIVERY -> if (zones != null) ({
      V3FormBar(StrOnline.dzSave, {
        val c = StorefrontDelivery.check(zonesForm.zones, zonesForm.rules, zonesForm.slotDays)
        zonesErrors = c.errors
        if (c.errors.isNotEmpty()) toasts.show(StrOnline.fixFields)
        else if (!zonesSaving) {
          zonesSaving = true
          scope.launch {
            zonesRepo.save(c, zonesForm.asap)
              .onSuccess { zones = it; zonesVersion++; Haptics.success(context); toasts.show(StrOnline.dzSaved) }
              .onFailure { toasts.show(it.userMessage) }
            zonesSaving = false
          }
        }
      }, loading = zonesSaving)
    }) else null
    else -> null
  }

  StorefrontScreen(
    config = cfg,
    loading = loading,
    error = error,
    tab = tab,
    onTab = { tab = it },
    onRetry = { scope.launch { reload() } },
    onBack = backOrNull?.let { b -> { if (dirty) askDiscard = true else b() } },
    bottomBar = bottomBar,
  ) { t, pad ->
    val c = cfg ?: return@StorefrontScreen
    when (t) {
      StorefrontModel.STATUS -> settings?.let { s ->
        StatusPane(
          settings = s,
          pending = sender.pending != null,
          sending = sender.sending,
          notice = sender.notice,
          confirmed = sender.confirmed,
          onDeclare = { mode, minutes ->
            scope.launch {
              val until = minutes?.let { ir.vil3ntec.tohid.core.storefront.StorefrontOwner.declaredUntilFor(it, System.currentTimeMillis(), s.timezone) }
              sender.declare(mode, until, s.timezone)
              if (sender.pending == null && sender.confirmed > 0) Haptics.success(context)
            }
          },
          onResend = { scope.launch { sender.flush(s.timezone) } },
          pad = pad,
        )
      }
      StorefrontModel.SETTINGS -> SettingsPane(form, formErrors, { form = it }, pad)
      StorefrontModel.PRODUCTS -> ProductsPane(
        products = StorefrontHub.products.value,
        error = StorefrontHub.error.value,
        bulkBusy = bulkBusy,
        bulkReport = bulkReport,
        bulkOk = bulkOk,
        actions = ProductsActions(
          onOpen = { p -> nav.open(OnlinePaths.storefrontProduct(p.id)) },
          onPublish = { p, on ->
            scope.launch { StorefrontHub.save(repo, p, on, p.onlinePriceMinor, p.maxQtyMilli)?.let { toasts.show(it) } }
          },
          onBulk = { ids, publish ->
            bulkBusy = true
            scope.launch {
              val r = StorefrontHub.bulk(repo, ids, publish)
              val names = StorefrontHub.products.value.orEmpty().associate { it.id to it.name }
              bulkReport = StrOnline.bulkDone(r.ok, r.failed.size) + r.failed.joinToString("") { (id, e) -> "\n• " + (names[id] ?: id) + ": " + e }
              bulkOk = r.failed.isEmpty()
              bulkBusy = false
              toasts.show(StrOnline.bulkDone(r.ok, r.failed.size))
            }
          },
          onRetry = { scope.launch { StorefrontHub.load(repo) } },
        ),
        pad = pad,
      )
      StorefrontModel.MARKET -> MarketPane(
        ctl = market,
        config = c,
        hasLocator = !Platform.DESKTOP,
        onLocate = { if (DeviceLocation.granted(context)) scope.launch { market.locate(context) } else ask.launch(DeviceLocation.PERMISSIONS) },
        onRetry = { scope.launch { market.reload() } },
        pad = pad,
      )
      StorefrontModel.DELIVERY -> when {
        zones == null && zonesError == null -> Column(Modifier.fillMaxSize().padding(16.dp)) { V3Skeleton(rows = 3) }
        zones == null -> V3EmptyState(
          Icons.Outlined.CloudOff, StrOnline.dzLoadFailed,
          body = zonesError ?: StrOnline.checkConnection, action = StrKit.retry, onAction = { scope.launch { loadZones() } },
        )
        else -> ZonesPane(zonesForm, zonesErrors, { zonesForm = it }, pad)
      }
    }
  }

  if (askDiscard) {
    V3ConfirmSheet(
      title = StrKit.discardTitle,
      message = StrKit.discardBody,
      confirm = StrKit.discard,
      onConfirm = { form = StorefrontSettingsModel.from(settings); back() },
      onDismiss = { askDiscard = false },
    )
  }
}

private fun zonesFormOf(d: StorefrontDeliveryDto): ZonesForm =
  ZonesForm(d.zones.map { it.draft() }, d.slotRules.map { it.draft() }, d.allowAsap, d.slotDays.toString())

/** `storefront/products/{id}` — برگهٔ قیمت، سقف، توضیح و مشخصاتِ یک کالا */
@Composable
internal fun RouteScope.StorefrontProductRoute() {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val repo = remember(context) { StorefrontRepository(Backend.api(context)) }
  val id = args["id"].orEmpty()
  LaunchedEffect(id) { if (StorefrontHub.products.value == null) StorefrontHub.load(repo) }
  val p = StorefrontHub.products.value?.firstOrNull { it.id == id }
  LaunchedEffect(StorefrontHub.products.value, id) { if (StorefrontHub.products.value != null && p == null) back() }
  var busy by remember { mutableStateOf(false) }
  var failure by remember { mutableStateOf<String?>(null) }
  if (p == null) return
  ProductSheet(
    p = p,
    busy = busy,
    failure = failure,
    onDismiss = { back() },
    onSave = { e ->
      busy = true
      failure = null
      scope.launch {
        val err = StorefrontHub.save(repo, p, p.published, e.online, e.max, e.description, e.specs)
        busy = false
        if (err == null) { Haptics.success(context); toasts.show(StrOnline.saved); back() } else failure = err
      }
    },
  )
}
