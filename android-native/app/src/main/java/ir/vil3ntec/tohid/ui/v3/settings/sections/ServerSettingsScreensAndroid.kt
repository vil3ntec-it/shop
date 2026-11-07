package ir.vil3ntec.tohid.ui.v3.settings.sections

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.LocaleChoice
import ir.vil3ntec.tohid.LocaleText
import ir.vil3ntec.tohid.core.i18n.StrSettings
import ir.vil3ntec.tohid.core.i18n.StrSettingsPages
import ir.vil3ntec.tohid.core.net.ApiClient
import ir.vil3ntec.tohid.core.report.OwnerReportPlan
import ir.vil3ntec.tohid.core.storefront.EtaViewDto
import ir.vil3ntec.tohid.core.storefront.PromoGuardApi
import ir.vil3ntec.tohid.core.storefront.PromoGuardViewDto
import ir.vil3ntec.tohid.core.storefront.StorefrontEtaApi
import ir.vil3ntec.tohid.data.ShopRole
import ir.vil3ntec.tohid.data.repo.Backend
import ir.vil3ntec.tohid.sync.SyncStore
import ir.vil3ntec.tohid.ui.screens.OwnerReportLive
import ir.vil3ntec.tohid.ui.screens.PromoGuardLive
import ir.vil3ntec.tohid.ui.screens.SettingsLayout
import ir.vil3ntec.tohid.ui.screens.StorefrontEtaLive
import ir.vil3ntec.tohid.ui.screens.ownerReportRow
import ir.vil3ntec.tohid.ui.screens.promoGuardRow
import ir.vil3ntec.tohid.ui.screens.storefrontEtaRow
import ir.vil3ntec.tohid.ui.v3.V3Button
import ir.vil3ntec.tohid.ui.v3.V3Divider
import ir.vil3ntec.tohid.ui.v3.V3FieldKind
import ir.vil3ntec.tohid.ui.v3.V3PickerRow
import ir.vil3ntec.tohid.ui.v3.V3PickerSheet
import ir.vil3ntec.tohid.ui.v3.V3Section
import ir.vil3ntec.tohid.ui.v3.V3Segmented
import ir.vil3ntec.tohid.ui.v3.V3TextField
import ir.vil3ntec.tohid.ui.v3.V3ToggleRow
import ir.vil3ntec.tohid.ui.v3.nav.RouteScope
import ir.vil3ntec.tohid.ui.v3.settings.SettingsBlock
import ir.vil3ntec.tohid.ui.v3.settings.SettingsNote
import ir.vil3ntec.tohid.ui.v3.settings.SettingsPageFrame
import kotlinx.coroutines.launch

/*
 *  سه تنظیمِ سروری که فقط در رابطِ امروز بودند (`OwnerReportSettings`، `StorefrontEtaSettings`، `PromoGuardSettings`
 *  در رابطِ قدیمِ پاک‌شده) — همان درِ سرور (`OwnerReportLive`، `StorefrontEtaLive`، `PromoGuardLive`)، همان
 *  سنجشِ فرم (`StorefrontEtaApi.check`، `PromoGuardApi.check`) و همان پیام‌ها (متن‌ها هم همان، فارسی).
 *  پشتِ پرچمِ سرور: تا پاسخِ روشنِ سرور ردیفی نیست (`SettingsLayout.SERVER_GATED`، `rememberServerSections`) و
 *  صفحه هم جز «خاموش» چیزی نمی‌کشد — مثلِ رابطِ امروز که با پرچمِ خاموش یا نقشِ دیگر هیچ نمی‌کشد.
 */

/**
 *  درِ سرور برای این صفحه‌ها — همان شرطِ رابطِ قدیم: واردشده و سرورِ آماده؛ `ownerOnly` (گزارشِ صاحب) فقط
 *  وقتی نقشِ همین دستگاه صاحبِ دکان است. وگرنه null ⇒ نه ردیفی، نه بخشی.
 */
@Composable
internal fun rememberSettingsApi(ownerOnly: Boolean): ApiClient? {
  val context = LocalContext.current
  return remember(ownerOnly) {
    val signedIn = runCatching { SyncStore(context).accessToken != null }.getOrDefault(false)
    val role = !ownerOnly || ShopRole.current(context) == ShopRole.OWNER
    if (signedIn && role && Backend.isReady(context)) Backend.api(context) else null
  }
}

/** بخش‌های پشتِ پرچمِ سرور که همین لحظه دیده شوند — همان `ownerReportRow`، `storefrontEtaRow` و `promoGuardRow` ِ رابطِ امروز */
@Composable
internal fun rememberServerSections(): Set<String> {
  val report = ownerReportRow(rememberSettingsApi(ownerOnly = true))
  val api = rememberSettingsApi(ownerOnly = false)
  val eta = storefrontEtaRow(api)
  val promo = promoGuardRow(api)
  val online = onlineOrdersRow(api)
  return buildSet {
    if (report) add(SettingsLayout.OWNER_REPORT)
    if (eta) add(SettingsLayout.STOREFRONT_ETA)
    if (promo) add(SettingsLayout.PROMO_GUARD)
    if (online) add(SettingsLayout.ONLINE_ORDERS)
    //  نشانی و نقشهٔ دکان (ممیزیِ ویترین): همان درِ «سفارشِ آنلاین» — دکانِ بی ویترین این ردیف را نمی‌بیند
    if (online) add(SettingsLayout.STORE_PLACE)
    //  WEB-09: «کالای ناموجود در ویترین» — همان در؛ پیوند به «ویترین ⇐ تنظیمات»
    if (online) add(SettingsLayout.STORE_STOCK)
  }
}

private val OWNER_STATUS: Map<String, String> get() = mapOf("sent" to StrSettingsPages.ownerSent, "error" to StrSettingsPages.ownerError, "claimed" to StrSettingsPages.ownerClaimed)
private const val PICK_WEEKDAY = "weekday"
private const val PICK_HOUR = "hour"

/**
 *  `settings/sales/owner-report`: گزارشِ خودکار برای صاحب (PAY-20/RPT-12) — روشن/خاموش، روزانه یا هفتگی، ساعتِ
 *  دکان و کانال‌ها؛ فقط صاحبِ دکان و پرچمِ روشنِ `shop.owner_report`. نوشتن همان `OwnerReportLive.set` با نسخه.
 */
@Composable
internal fun RouteScope.OwnerReportScreen(onBack: (() -> Unit)?) {
  val api = rememberSettingsApi(ownerOnly = true)
  var view by remember { mutableStateOf<OwnerReportPlan.View?>(null) }
  var draft by remember { mutableStateOf(OwnerReportPlan.Settings()) }
  var busy by remember { mutableStateOf(false) }
  var pick by remember { mutableStateOf<String?>(null) }
  val scope = rememberCoroutineScope()
  LaunchedEffect(api) { if (api != null) OwnerReportLive.load(api).onSuccess { v -> view = v; draft = v.settings } }
  val v = view?.takeIf { api != null && it.enabled && it.owner }
  SettingsPageFrame(
    title = SettingsLayout.labelOf(SettingsLayout.OWNER_REPORT),
    subtitle = if (v == null) StrSettings.off else if (v.settings.active) OwnerReportPlan.coverage(v.settings) else StrSettingsPages.off,
    onBack = onBack,
  ) {
    if (v == null || api == null) {
      item(key = "off") { SettingsNote(StrSettings.off) }
      return@SettingsPageFrame
    }
    item(key = "intro") {
      SettingsNote(
        StrSettingsPages.ownerIntro(if (v.timezone.isNotBlank()) " (${v.timezone})" else ""),
      )
      SettingsBlock { V3Segmented(listOf(StrSettingsPages.off, StrSettingsPages.on), if (draft.active) 1 else 0, { draft = draft.copy(active = it == 1) }) }
    }
    if (draft.active) {
      item(key = "plan") {
        SettingsBlock {
          V3Segmented(
            listOf(StrSettingsPages.daily, StrSettingsPages.weekly),
            if (draft.frequency == OwnerReportPlan.WEEKLY) 1 else 0,
            { draft = draft.copy(frequency = if (it == 1) OwnerReportPlan.WEEKLY else OwnerReportPlan.DAILY) },
          )
        }
        V3Section(null) {
          if (draft.frequency == OwnerReportPlan.WEEKLY) {
            V3PickerRow(StrSettingsPages.weekday, OwnerReportPlan.weekdayName(draft.weekday), onClick = { pick = PICK_WEEKDAY })
            V3Divider(start = 16.dp)
          }
          V3PickerRow(StrSettingsPages.hour, OwnerReportPlan.hourLabel(draft.hour), onClick = { pick = PICK_HOUR })
        }
        SettingsNote(OwnerReportPlan.coverage(draft))
      }
      item(key = "channels") {
        V3Section(null) {
          listOf(OwnerReportPlan.INAPP to StrSettingsPages.inApp, OwnerReportPlan.PUSH to StrSettingsPages.push, OwnerReportPlan.EMAIL to StrSettingsPages.email).forEachIndexed { i, (ch, label) ->
            if (i > 0) V3Divider(start = 16.dp)
            val usable = ch != OwnerReportPlan.EMAIL || v.emailAvailable
            V3ToggleRow(
              if (usable) label else StrSettingsPages.noEmail(label),
              ch in draft.channels,
              { draft = draft.copy(channels = OwnerReportPlan.toggleChannel(draft.channels, ch)) },
              enabled = usable,
            )
          }
        }
      }
    }
    item(key = "save") {
      V3Button(
        text = StrSettingsPages.save,
        onClick = {
          busy = true
          scope.launch {
            OwnerReportLive.set(api, draft, v.settings.version)
              .onSuccess { s -> view = v.copy(settings = s); draft = s; toasts.show(StrSettingsPages.saved) }
              .onFailure { f -> toasts.show(if (f.code == "version_conflict") StrSettingsPages.versionConflict else f.userMessage) }
            busy = false
          }
        },
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        enabled = !busy && draft != v.settings,
        loading = busy,
      )
      for (r in v.reports.take(5)) {
        val net = r.netSalesMinor?.let { StrSettingsPages.reportNet(ir.vil3ntec.tohid.money(it / 100.0)) }.orEmpty()
        SettingsNote("${r.reportDate} · ${OWNER_STATUS[r.status] ?: r.status}$net")
      }
    }
  }
  when (pick) {
    PICK_WEEKDAY -> {
      val names = OwnerReportPlan.WEEK_ORDER.map(OwnerReportPlan::weekdayName)
      V3PickerSheet(
        title = StrSettingsPages.weekday,
        options = names,
        selected = OwnerReportPlan.weekdayName(draft.weekday),
        onPick = { n -> names.indexOf(n).takeIf { it >= 0 }?.let { draft = draft.copy(weekday = OwnerReportPlan.WEEK_ORDER[it]) }; pick = null },
        onDismiss = { pick = null },
        allowNew = false,
      )
    }
    PICK_HOUR -> {
      val hours = (0..23).map(OwnerReportPlan::hourLabel)
      V3PickerSheet(
        title = StrSettingsPages.hour,
        options = hours,
        selected = OwnerReportPlan.hourLabel(draft.hour),
        onPick = { h -> hours.indexOf(h).takeIf { it >= 0 }?.let { draft = draft.copy(hour = it) }; pick = null },
        onDismiss = { pick = null },
        allowNew = false,
      )
    }
  }
}

private fun afghani(minor: Long): String = LocaleText.afghani(minor / 100.0, LocaleChoice.screenDigits, LocaleChoice.symbol)

@Composable
private fun NumberField(value: String, onChange: (String) -> Unit, label: String, error: String?) {
  V3TextField(
    value = value,
    onValueChange = onChange,
    label = label,
    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
    kind = V3FieldKind.NUMBER,
    error = error,
  )
}

/**
 *  `settings/sales/storefront-eta`: زمانِ تقریبیِ تحویلِ ویترین (WEB-04) — نمایش، دقیقهٔ آماده‌سازی و راه، سفارشِ
 *  هم‌زمان و راهِ هر ناحیه؛ پرچمِ `shop.storefront.eta` و دسترسیِ تنظیم (سرور می‌سنجد). همان `StorefrontEtaLive.set`.
 */
@Composable
internal fun RouteScope.StorefrontEtaScreen(onBack: (() -> Unit)?) {
  val api = rememberSettingsApi(ownerOnly = false)
  var view by remember { mutableStateOf<EtaViewDto?>(null) }
  var form by remember { mutableStateOf(StorefrontEtaApi.Form()) }
  var errors by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
  var busy by remember { mutableStateOf(false) }
  val scope = rememberCoroutineScope()
  LaunchedEffect(api) { if (api != null) StorefrontEtaLive.load(api).onSuccess { v -> view = v; form = StorefrontEtaApi.formOf(v) } }
  val v = view?.takeIf { api != null && StorefrontEtaApi.visible(it) }
  SettingsPageFrame(
    title = SettingsLayout.labelOf(SettingsLayout.STOREFRONT_ETA),
    subtitle = if (v == null) StrSettings.off else if (v.settings.show) StrSettingsPages.etaOn else StrSettingsPages.off,
    onBack = onBack,
  ) {
    if (v == null || api == null) {
      item(key = "off") { SettingsNote(StrSettings.off) }
      return@SettingsPageFrame
    }
    item(key = "show") {
      SettingsNote(
        StrSettingsPages.etaIntro,
      )
      SettingsBlock { V3Segmented(listOf(StrSettingsPages.etaHide, StrSettingsPages.etaShow), if (form.show) 1 else 0, { form = form.copy(show = it == 1) }) }
    }
    item(key = "minutes") {
      SettingsBlock {
        NumberField(form.prep, { form = form.copy(prep = it) }, StrSettingsPages.etaPrep, errors["prep"])
        NumberField(form.travel, { form = form.copy(travel = it) }, StrSettingsPages.etaTravel, errors["travel"])
        NumberField(form.parallel, { form = form.copy(parallel = it) }, StrSettingsPages.etaParallel, errors["parallel"])
      }
    }
    if (v.zones.isNotEmpty()) {
      item(key = "zones") {
        SettingsNote(StrSettingsPages.etaZones)
        SettingsBlock {
          for (z in v.zones) {
            NumberField(
              form.zones[z.id].orEmpty(), { form = form.copy(zones = form.zones + (z.id to it)) },
              if (z.active) z.name else StrSettingsPages.inactiveZone(z.name), errors["zone:${z.id}"],
            )
          }
        }
      }
    }
    item(key = "save") {
      errors["zones"]?.let { SettingsNote(it) }
      V3Button(
        text = StrSettingsPages.save,
        onClick = {
          val c = StorefrontEtaApi.check(form, v.zones.map { it.id }, v.settings.version)
          errors = c.errors
          val body = c.body
          if (body == null) {
            toasts.show(StrSettingsPages.fixFields)
          } else {
            busy = true
            scope.launch {
              StorefrontEtaLive.set(api, body)
                .onSuccess {
                  toasts.show(StrSettingsPages.saved)
                  StorefrontEtaLive.load(api).onSuccess { n -> view = n; form = StorefrontEtaApi.formOf(n) }
                }
                .onFailure { f -> toasts.show(StorefrontEtaApi.errorText(f.code, f.userMessage)) }
              busy = false
            }
          }
        },
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        enabled = !busy,
        loading = busy,
      )
      SettingsNote(StorefrontEtaApi.signalLine(v.signals))
      for (line in StorefrontEtaApi.previewLines(v.preview, v.zones)) SettingsNote(line)
    }
  }
}

/**
 *  `settings/sales/promo-guard`: تخفیفِ اولین خرید و سدِ سوءاستفاده (MKT-02، RSK-09) — درصد یا مبلغ، سقف، کفِ سبد،
 *  ترکیب/انحصاری؛ سقفِ کد برای هر شماره و دستگاه و سقفِ تخفیفِ هر سفارش؛ و فهرستِ «مشکوک». پرچمِ
 *  `shop.storefront.promo_guard` (پاسخِ ۴۰۳ ⇒ پنهان). همان `PromoGuardLive.set`.
 */
@Composable
internal fun RouteScope.PromoGuardScreen(onBack: (() -> Unit)?) {
  val api = rememberSettingsApi(ownerOnly = false)
  var view by remember { mutableStateOf<PromoGuardViewDto?>(null) }
  var form by remember { mutableStateOf(PromoGuardApi.Form()) }
  var errors by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
  var busy by remember { mutableStateOf(false) }
  val scope = rememberCoroutineScope()
  LaunchedEffect(api) { if (api != null) PromoGuardLive.load(api).onSuccess { v -> view = v; form = PromoGuardApi.formOf(v.settings) } }
  val v = view?.takeIf { api != null }
  val suspects = v?.let { PromoGuardApi.suspectRows(it.suspicious) }.orEmpty()
  SettingsPageFrame(
    title = SettingsLayout.labelOf(SettingsLayout.PROMO_GUARD),
    subtitle = v?.settings?.firstOrder?.let { f0 ->
      buildString {
        append(
          when {
            !f0.enabled -> StrSettingsPages.firstOff
            f0.kind == PromoGuardApi.AMOUNT -> StrSettingsPages.firstAmount(afghani(f0.amountMinor))
            else -> StrSettingsPages.firstPercent(PromoGuardApi.percentText(f0.bp))
          },
        )
        if (suspects.isNotEmpty()) append(StrSettingsPages.suspects(ir.vil3ntec.tohid.core.storefront.StorefrontOwner.toFa(suspects.size.toString())))
      }
    } ?: StrSettings.off,
    onBack = onBack,
  ) {
    if (v == null || api == null) {
      item(key = "off") { SettingsNote(StrSettings.off) }
      return@SettingsPageFrame
    }
    item(key = "first") {
      SettingsNote(StrSettingsPages.firstNote)
      SettingsBlock { V3Segmented(listOf(StrSettingsPages.firstOff, StrSettingsPages.firstOn), if (form.enabled) 1 else 0, { form = form.copy(enabled = it == 1) }) }
    }
    if (form.enabled) {
      item(key = "offer") {
        SettingsBlock {
          V3Segmented(
            listOf(StrSettingsPages.percent, StrSettingsPages.amount),
            if (form.kind == PromoGuardApi.AMOUNT) 1 else 0,
            { form = form.copy(kind = if (it == 1) PromoGuardApi.AMOUNT else PromoGuardApi.PERCENT) },
          )
          if (form.kind == PromoGuardApi.PERCENT) {
            NumberField(form.percent, { form = form.copy(percent = it) }, StrSettingsPages.percentField, errors["percent"])
            NumberField(form.max, { form = form.copy(max = it) }, StrSettingsPages.maxField, errors["max"])
          } else {
            NumberField(form.amount, { form = form.copy(amount = it) }, StrSettingsPages.amountField, errors["amount"])
          }
          NumberField(form.minSubtotal, { form = form.copy(minSubtotal = it) }, StrSettingsPages.minSubtotalField, errors["minSubtotal"])
        }
        SettingsBlock {
          V3Segmented(
            listOf(StrSettingsPages.stackCombine, StrSettingsPages.stackExclusive),
            if (form.stack == PromoGuardApi.EXCLUSIVE) 1 else 0,
            { form = form.copy(stack = if (it == 1) PromoGuardApi.EXCLUSIVE else PromoGuardApi.COMBINE) },
          )
        }
      }
    }
    item(key = "guard") {
      SettingsNote(StrSettingsPages.guardNote)
      SettingsBlock {
        NumberField(form.codePerPhone, { form = form.copy(codePerPhone = it) }, StrSettingsPages.codePerPhone, errors["codePerPhone"])
        NumberField(form.codePerDevice, { form = form.copy(codePerDevice = it) }, StrSettingsPages.codePerDevice, errors["codePerDevice"])
        NumberField(form.maxDiscount, { form = form.copy(maxDiscount = it) }, StrSettingsPages.maxDiscount, errors["maxDiscount"])
      }
    }
    item(key = "save") {
      V3Button(
        text = StrSettingsPages.save,
        onClick = {
          val c = PromoGuardApi.check(form)
          errors = c.errors
          val next = c.settings
          if (next == null) {
            toasts.show(StrSettingsPages.fixFields)
          } else {
            busy = true
            scope.launch {
              PromoGuardLive.set(api, next)
                .onSuccess { s -> view = v.copy(settings = s); form = PromoGuardApi.formOf(s); toasts.show(StrSettingsPages.saved) }
                .onFailure { f -> toasts.show(PromoGuardApi.errorText(f.code, f.userMessage)) }
              busy = false
            }
          }
        },
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        enabled = !busy,
        loading = busy,
      )
    }
    item(key = "suspects") {
      SettingsNote(PromoGuardApi.suspectSummary(v.suspicious))
      for (r in suspects) {
        SettingsNote(r.title)
        SettingsNote(r.detail)
      }
      if (v.recent.isNotEmpty()) {
        SettingsNote(StrSettingsPages.recentDiscounts)
        for (c in v.recent.take(5)) {
          val code = if (c.code.isNotBlank()) " ${c.code}" else ""
          SettingsNote("${PromoGuardApi.kindLabel(c.kind)}$code · ${c.phone} · ${afghani(c.discountMinor)}")
        }
      }
    }
  }
}
