package ir.vil3ntec.tohid.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.net.ApiFailure
import ir.vil3ntec.tohid.core.storefront.BulkPublish
import ir.vil3ntec.tohid.core.storefront.StorefrontOwner
import ir.vil3ntec.tohid.core.storefront.StorefrontSpecs
import ir.vil3ntec.tohid.core.storefront.StorefrontOwner.Tone
import ir.vil3ntec.tohid.data.repo.Backend
import ir.vil3ntec.tohid.data.repo.PresenceStatusDto
import ir.vil3ntec.tohid.data.repo.StorefrontConfigDto
import ir.vil3ntec.tohid.data.repo.StorefrontProductDto
import ir.vil3ntec.tohid.data.repo.StorefrontQueue
import ir.vil3ntec.tohid.data.repo.StorefrontRepository
import ir.vil3ntec.tohid.core.storefront.OutboxEntry
import ir.vil3ntec.tohid.core.storefront.PresenceDeclaration
import ir.vil3ntec.tohid.core.storefront.SendOutcome
import ir.vil3ntec.tohid.core.storefront.StorefrontChange
import ir.vil3ntec.tohid.core.storefront.StorefrontOutbox
import ir.vil3ntec.tohid.core.storefront.StorefrontRules
import ir.vil3ntec.tohid.data.repo.StorefrontSettingsDto
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.ui.components.common.ScreenKind
import ir.vil3ntec.tohid.ui.components.common.ViewMode
import ir.vil3ntec.tohid.ui.components.common.ViewModeToggle
import ir.vil3ntec.tohid.ui.components.common.rememberViewMode
import ir.vil3ntec.tohid.ui.theme.Shape
import ir.vil3ntec.tohid.ui.theme.Shop
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 *  آیا بخشِ «ویترین» دیده شود؟ — فقط وقتی سرور می‌گوید روشن است و کاربر
 *  اجازهٔ تنظیم دارد. بی اینترنت یا بی حساب: پنهان (هیچ دکمهٔ بی‌اثری نیست).
 */
object StorefrontGate {
  val config = mutableStateOf<StorefrontConfigDto?>(null)
  val visible: Boolean get() = config.value?.let { it.flag && it.permissions.manage } == true

  suspend fun refresh(context: android.content.Context): StorefrontConfigDto? {
    if (!Backend.isReady(context)) { config.value = null; return null }
    val r = StorefrontRepository(Backend.api(context)).config().valueOrNull()
    if (r != null) {
      config.value = r
      //  هر ویرایشِ ویترین/بازار که بی اینترنت ماند، با باز شدنِ برنامه دوباره فرستاده می‌شود
      if (r.flag) StorefrontQueue.flush(context)
    }
    return r
  }
}


/**
 *  ویترینِ اینترنتی — صفحهٔ صاحب دکان (WEB-01/03، PRD-27، §۳۱ PRES).
 *
 *  شش برگه: «وضعیت» (همان که مشتری می‌بیند + اعلامِ آنلاین/آفلاین)،
 *  «تنظیمات»، «کالاها» (انتشار، قیمتِ آنلاین، سقفِ تعداد، توضیح و مشخصات، گروهی)،
 *  «بازارچه» (نمایش در فهرستِ عمومیِ دکان‌ها، `StorefrontMarket.kt`)،
 *  «پیوند و QR» (پیوندِ کمپین و QR ِ دیوار/رسید، `StorefrontCampaigns.kt`)
 *  و «ارسال و زمان» (ناحیه‌ها و بازه‌ها — `StorefrontDelivery.kt`).
 *  هر کار فقط پس از پاسخِ سرور «ثبت شد» می‌گوید (PRES-09).
 */
@Composable
fun StorefrontScreen(snackbar: SnackbarHostState) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val repo = remember(context) { StorefrontRepository(Backend.api(context)) }
  val cfg = StorefrontGate.config.value
  var loading by remember { mutableStateOf(cfg == null) }
  var error by remember { mutableStateOf<String?>(null) }
  var tab by remember { mutableStateOf("status") }

  suspend fun reload() {
    loading = true
    repo.config()
      .onSuccess { StorefrontGate.config.value = it; error = null }
      .onFailure { error = it.userMessage }
    loading = false
  }
  LaunchedEffect(Unit) { reload() }
  //  صفِ آفلاین: هر ۱۵ ثانیه تا صفحه باز است (پس‌کشیِ خودِ صف جلوی تکرارِ بی‌جا را می‌گیرد)
  LaunchedEffect(Unit) {
    while (true) {
      runCatching { StorefrontQueue.flush(context) }
      kotlinx.coroutines.delay(15_000)
    }
  }
  //  «ثبت شد» فقط از پاسخِ سرور (§۳۱.۲ بندِ ۲)
  LaunchedEffect(Unit) {
    StorefrontQueue.reports.collect { r -> if (r.outcome != SendOutcome.HOLD || r.message == StorefrontOutbox.CONFLICT_NOTE) snackbar.showSnackbar(r.message) }
  }

  val c = StorefrontGate.config.value
  Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Text("ویترینِ اینترنتیِ دکان — نشانی، ساعتِ کاری، کالاهای آنلاین و وضعیتی که مشتری می‌بیند",
      style = MaterialTheme.typography.bodySmall, color = Shop.colors.muted)
    when {
      c == null && loading -> TohidLoadingState(rows = 4)
      c == null -> TohidErrorState("ویترین باز نشد", error ?: "اتصال را بررسی کنید.", onRetry = { scope.launch { reload() } })
      !c.flag -> TohidEmptyState("ویترین برای این دکان روشن نیست", "این بخش پس از روشن شدن از طرفِ سامانه دیده می‌شود.", icon = Icons.Filled.Storefront)
      !c.permissions.manage -> TohidEmptyState("فقط صاحب دکان یا مدیر", "تنظیمِ ویترین کارِ صاحب دکان یا مدیر است.", icon = Icons.Filled.Storefront)
      else -> {
        if (!c.entitled) {
          Text("اشتراکِ این دکان ویترین را در بر ندارد؛ تغییرها ذخیره نمی‌شوند.", color = Shop.colors.warning, style = MaterialTheme.typography.bodySmall)
        }
        TohidFilterRow(
          //  «ارسال و زمان» فقط وقتی سرور پرچمِ جدای `shop.storefront.zones` را روشن گفته
          options = listOf("status" to "وضعیت", "settings" to "تنظیمات", "products" to "کالاها", "market" to "بازارچه", "campaigns" to "پیوند و QR") +
            (if (c.zones) listOf("delivery" to "ارسال و زمان") else emptyList()),
          selected = tab,
          onSelect = { tab = it },
        )
        OutboxBanner()
        val settings = c.settings
        when (tab) {
          "status" -> PresencePane(settings, repo, snackbar) { tab = "settings" }
          "settings" -> SettingsPane(settings, repo, snackbar)
          "delivery" -> if (!c.zones) {
            TohidEmptyState("ناحیه و زمان‌بندی روشن نیست", "این بخش پس از روشن شدن از طرفِ سامانه دیده می‌شود.", icon = Icons.Filled.Storefront)
          } else if (settings == null) {
            TohidEmptyState("اول ویترین را بسازید", "در «تنظیمات» نشانی و نام را بنویسید و ذخیره کنید.", actionText = "رفتن به تنظیمات", onAction = { tab = "settings" })
          } else StorefrontDeliveryPane(snackbar)
          //  «نمایش در بازارچه» — دادهٔ جدا و ذخیرهٔ جدا (`StorefrontMarket.kt`)
          "market" -> MarketPane(c, snackbar)
          //  «پیوند و QR» (MKT-10/11) — پرچمِ جدا و دادهٔ جدا (`StorefrontCampaigns.kt`)
          "campaigns" -> CampaignsPane(settings?.title.orEmpty(), snackbar)
          else -> if (settings == null) {
            TohidEmptyState("اول ویترین را بسازید", "در «تنظیمات» نشانی و نام را بنویسید و ذخیره کنید.", actionText = "رفتن به تنظیمات", onAction = { tab = "settings" })
          } else ProductsPane(repo, snackbar, bulkOn = c.bulk)
        }
      }
    }
  }
}

/* ══════════════════════════════════════════════════════════════════
   وضعیت و اعلام (§۳۱.۱، PRES-01..09)
   ══════════════════════════════════════════════════════════════════ */

@Composable
fun PresenceBadge(status: PresenceStatusDto) {
  val colors = Shop.colors
  val (tint, fill) = when (StorefrontOwner.badgeTone(status.badge)) {
    Tone.OK -> colors.success to colors.successTint
    Tone.WARN -> colors.warning to colors.warningTint
    Tone.OFF -> colors.danger to colors.dangerTint
  }
  //  «متن + رنگ + نقطه»، هرگز رنگِ تنها (VISUAL-DIRECTION §۱ بندِ ۳)
  Row(
    Modifier.clip(Shape.badge).background(fill).padding(horizontal = 10.dp, vertical = 4.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    Box(Modifier.size(8.dp).clip(Shape.badge).background(tint))
    Text(StorefrontOwner.badgeLabel(status.badge, status.declaredUntil, status.timezone),
      color = tint, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
  }
}

@Composable
private fun PresencePane(
  settings: StorefrontSettingsDto?,
  repo: StorefrontRepository,
  snackbar: SnackbarHostState,
  onSettings: () -> Unit,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  //  اعلامِ نرسیده در صفِ پایدارِ ویترین (core/StorefrontOutbox) — همان eventId دوباره می‌رود (PRES-08/09)
  val pendingEntry = StorefrontQueue.entries.value.let { StorefrontQueue.pending(context, StorefrontChange.PRESENCE) }
  val pending = pendingEntry?.let { PresenceDeclaration.decode(it.body) }
  val sending = StorefrontQueue.sending.value
  var notice by remember { mutableStateOf<String?>(null) }
  var untilText by remember { mutableStateOf("") }
  var untilError by remember { mutableStateOf<String?>(null) }

  LaunchedEffect(Unit) {
    StorefrontQueue.reports.collect { r -> if (r.change == StorefrontChange.PRESENCE && r.outcome != SendOutcome.HOLD) notice = r.message }
  }

  if (settings == null) {
    TohidEmptyState("هنوز ویترینی ساخته نشده", "نشانی و نامِ ویترین را در «تنظیمات» بنویسید.", icon = Icons.Filled.Storefront,
      actionText = "رفتن به تنظیمات", onAction = onSettings)
    return
  }
  val tz = settings.timezone

  suspend fun flush() { StorefrontQueue.flush(context, force = true) }

  fun declare(mode: String, until: Long? = null) {
    val p = PresenceDeclaration.create(mode, until, System.currentTimeMillis(), kotlin.random.Random.nextLong())
    if (StorefrontQueue.enqueue(context, StorefrontChange.PRESENCE, PresenceDeclaration.encode(p), "", id = p.eventId) == null) {
      notice = "دکانِ این نشست معلوم نیست؛ یک بار از حساب بیرون و دوباره وارد شوید."
      return
    }
    notice = null
    scope.launch { flush() }
  }


  val st = settings.status
  Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Panel {
      Text("مشتری الان این را می‌بیند", style = MaterialTheme.typography.titleSmall, color = Shop.colors.text)
      Spacer(Modifier.height(8.dp))
      if (st == null) {
        Text("وضعیت هنوز از سرور نیامده.", color = Shop.colors.muted)
      } else {
        PresenceBadge(st)
        Spacer(Modifier.height(8.dp))
        Text(StorefrontOwner.connectionLabel(st.lastConfirmedAt, tz), color = Shop.colors.muted, style = MaterialTheme.typography.bodySmall)
        Text("ساعتِ دکان: ${StorefrontOwner.toFa(st.localTime)} · " +
          when {
            st.holiday -> "امروز تعطیلِ استثنایی"
            st.today.isNotEmpty() -> "امروز " + StorefrontOwner.toFa(StorefrontOwner.formatDay(st.today.map { it[0] to it[1] }))
            st.hoursSet -> "امروز تعطیل"
            else -> "ساعتِ کاری تنظیم نشده"
          },
          color = Shop.colors.muted, style = MaterialTheme.typography.bodySmall)
        Text(StorefrontOwner.acceptanceLabel(st.orderAcceptance), style = MaterialTheme.typography.bodySmall, color = Shop.colors.text)
        Text("اعلامِ فعلیِ شما: ${StorefrontOwner.modeLabel(st.mode)}", style = MaterialTheme.typography.bodySmall, color = Shop.colors.muted)
      }
      Spacer(Modifier.height(6.dp))
      Text("نشانی: ${settings.slug}.vill3n.top" + if (!settings.enabled) " — ویترین خاموش است" else "",
        style = MaterialTheme.typography.bodySmall, color = if (settings.enabled) Shop.colors.muted else Shop.colors.warning)
    }

    if (pending != null) {
      Panel {
        Text(StorefrontOwner.NOT_CONFIRMED, color = Shop.colors.warning, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(8.dp))
        TohidSecondaryButton(if (sending) "در حالِ فرستادن…" else "دوباره بفرست", onClick = { scope.launch { flush() } }, enabled = !sending)
      }
    }
    notice?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Shop.colors.muted) }

    Panel {
      Text("اعلامِ شما به مشتری", style = MaterialTheme.typography.titleSmall)
      Spacer(Modifier.height(8.dp))
      Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TohidTextField(
          value = untilText, onValueChange = { untilText = it; untilError = null }, label = "آنلاین تا ساعتِ (مثلاً 20:30)",
          modifier = Modifier.weight(1f), error = untilError,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        TohidButton("ثبت", onClick = {
          val m = StorefrontOwner.parseHm(untilText)
          if (m == null || m >= 1440) untilError = "ساعت را مثلِ 20:30 بنویسید"
          else declare("online_until", StorefrontOwner.declaredUntilFor(m, System.currentTimeMillis(), tz))
        }, enabled = !sending && pending == null)
      }
      Spacer(Modifier.height(10.dp))
      Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for ((mode, label) in listOf("online" to "آنلاین بی زمانِ پایان", "offline" to "آفلاین", "closed" to "تعطیل/بسته", "auto" to "طبقِ ساعتِ کاری")) {
          TohidSecondaryButton(label, onClick = { declare(mode) }, enabled = !sending && pending == null)
        }
      }
      Spacer(Modifier.height(6.dp))
      Text("«ثبت شد» فقط وقتی دیده می‌شود که سرور گرفته باشد. بی اینترنت، تغییر این‌جا می‌ماند و برای مشتری عوض نمی‌شود.",
        style = MaterialTheme.typography.bodySmall, color = Shop.colors.muted)
    }
  }
}

/* ══════════════════════════════════════════════════════════════════
   تنظیمات
   ══════════════════════════════════════════════════════════════════ */

@Composable
private fun SettingsPane(serverSettings: StorefrontSettingsDto?, repo: StorefrontRepository, snackbar: SnackbarHostState) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  //  ویرایشِ نرسیده (صفِ آفلاین) روی دادهٔ سرور نشان داده می‌شود تا کاربر کارِ خودش را ببیند، نه مقدارِ کهنه
  val pendingEntry = StorefrontQueue.entries.value.let { StorefrontQueue.pending(context, StorefrontChange.SETTINGS) }
  val settings = StorefrontQueue.settingsWithPending(serverSettings, pendingEntry)
  val s = settings ?: StorefrontSettingsDto()
  //  پس از ردِ سرور هم فرم همان نوشتهٔ کاربر را نگه می‌دارد؛ فقط «ثبت شد» (updatedAt ِ تازه) از نو پرش می‌کند
  val formKey = s.updatedAt
  var slug by remember(formKey) { mutableStateOf(s.slug) }
  var title by remember(formKey) { mutableStateOf(s.title) }
  var tagline by remember(formKey) { mutableStateOf(s.tagline) }
  var phone by remember(formKey) { mutableStateOf(s.contactPhone.replace(Regex("^\\+93"), "0")) }
  var accent by remember(formKey) { mutableStateOf(s.accent) }
  var notice by remember(formKey) { mutableStateOf(s.notice) }
  var enabled by remember(formKey) { mutableStateOf(s.enabled) }
  var delivery by remember(formKey) { mutableStateOf(s.fulfillment.delivery) }
  var pickup by remember(formKey) { mutableStateOf(s.fulfillment.pickup) }
  var fee by remember(formKey) { mutableStateOf(if (settings == null) "" else StorefrontOwner.formatMinor(s.deliveryFeeMinor)) }
  var minOrder by remember(formKey) { mutableStateOf(if (settings == null) "" else StorefrontOwner.formatMinor(s.minOrderMinor)) }
  var tz by remember(formKey) { mutableStateOf(s.timezone) }
  val days = remember(formKey) {
    mutableStateMapOf<Int, String>().apply {
      for (d in 0..6) put(d, StorefrontOwner.formatDay(s.weeklyHours[d.toString()].orEmpty().filter { it.size == 2 }.map { it[0] to it[1] }))
    }
  }
  var closed by remember(formKey) { mutableStateOf(s.closedDates.joinToString("، ")) }
  val errors = remember { mutableStateMapOf<String, String>() }
  val saving = StorefrontQueue.sending.value

  /** سنجش در core (`StorefrontRules.checkSettings`) — همان قاعده‌های سرور؛ نادرست ⇒ null و خطا کنارِ کادر */
  fun build(): kotlinx.serialization.json.JsonObject? {
    val draft = StorefrontRules.SettingsDraft(
      slug = slug, title = title, tagline = tagline, phone = phone, accent = accent, notice = notice, enabled = enabled,
      delivery = delivery, pickup = pickup, fee = fee, minOrder = minOrder, timezone = tz,
      days = (0..6).associateWith { days[it].orEmpty() }, closed = closed,
    )
    errors.clear()
    errors.putAll(StorefrontRules.checkSettings(draft).errors)
    return if (errors.isEmpty()) StorefrontQueue.settingsBody(draft) else null
  }

  Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Panel {
      Text("نشانی و نام", style = MaterialTheme.typography.titleSmall)
      TohidTextField(slug, { slug = it }, "نشانی (زیرِ vill3n.top)", placeholder = "my-shop", error = errors["slug"],
        supporting = "نشانیِ مشتری: ${slug.ifBlank { "…" }}.vill3n.top")
      TohidTextField(title, { title = it }, "نامِ ویترین (نامِ کسب‌وکار، نه نامِ شخص)", error = errors["title"])
      TohidTextField(tagline, { tagline = it }, "یک جملهٔ کوتاه (اختیاری)", error = errors["tagline"])
      TohidTextField(phone, { phone = it }, "شمارهٔ تماسِ دکان", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
      TohidTextField(accent, { accent = it }, "رنگِ ویترین (اختیاری)", placeholder = "#2c5ce6", error = errors["accent"])
      TohidTextField(notice, { notice = it }, "پیامِ کوتاه کنارِ وضعیت (اختیاری)", error = errors["notice"])
      SwitchRow("ویترین روشن باشد", enabled) { enabled = it }
    }
    Panel {
      Text("تحویل و هزینه", style = MaterialTheme.typography.titleSmall)
      SwitchRow("ارسال به نشانیِ مشتری", delivery) { delivery = it }
      SwitchRow("دریافت از دکان", pickup) { pickup = it }
      errors["fulfillment"]?.let { Text(it, color = Shop.colors.danger, style = MaterialTheme.typography.bodySmall) }
      TohidTextField(fee, { fee = it }, "هزینهٔ ارسال (افغانی)", error = errors["fee"], keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
      TohidTextField(minOrder, { minOrder = it }, "حداقلِ سفارش (افغانی)", error = errors["min"], keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
    }
    Panel {
      Text("ساعتِ کاری", style = MaterialTheme.typography.titleSmall)
      Text("هر روز مثلِ 08:00-12:00، 14:00-20:00؛ خالی = تعطیل. شبِ پس از ۲۴ را در روزِ بعد بنویسید. همه خالی = ساعت تنظیم نشده.",
        style = MaterialTheme.typography.bodySmall, color = Shop.colors.muted)
      TohidTextField(tz, { tz = it }, "منطقهٔ زمانیِ دکان", placeholder = "Asia/Kabul", error = errors["timezone"])
      for (d in StorefrontOwner.DISPLAY_DAYS) {
        TohidTextField(days[d].orEmpty(), { days[d] = it }, StorefrontOwner.DAY_NAMES[d], error = errors["day$d"])
      }
      TohidTextField(closed, { closed = it }, "تعطیلیِ استثنایی (تاریخ‌ها مثلِ 2026-10-20)", error = errors["closed"], singleLine = false)
    }
    if (pendingEntry != null) {
      Text(StorefrontOutbox.statusLine(pendingEntry), color = Shop.colors.warning, style = MaterialTheme.typography.bodySmall)
    }
    TohidButton(if (saving) "در حالِ فرستادن…" else "ذخیرهٔ ویترین", onClick = {
      val body = build()
      if (body == null) { scope.launch { snackbar.showSnackbar("چند کادر درست نیست؛ پیامِ کنارِ هر کادر را ببینید") }; return@TohidButton }
      //  اول روی دیسک (صف)، بعد سرور؛ «ثبت شد» فقط از پاسخِ سرور می‌آید (گزارشِ صف ⇒ snackbar ِ صفحه)
      val e = StorefrontQueue.enqueue(context, StorefrontChange.SETTINGS, body.toString(),
        pendingEntry?.base ?: StorefrontQueue.settingsBase(serverSettings))
      if (e == null) { scope.launch { snackbar.showSnackbar("دکانِ این نشست معلوم نیست؛ یک بار از حساب بیرون و دوباره وارد شوید.") }; return@TohidButton }
      scope.launch { StorefrontQueue.flush(context, force = true) }
    }, enabled = !saving, busy = saving, modifier = Modifier.fillMaxWidth())
  }
}

@Composable
internal fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
  Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { onChange(!checked) }, verticalAlignment = Alignment.CenterVertically) {
    Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
    Switch(checked = checked, onCheckedChange = onChange)
  }
}

/* ══════════════════════════════════════════════════════════════════
   کالاها — انتشار، قیمتِ آنلاین، سقف، گروهی (کارت/جدول: ViewModePref)
   ══════════════════════════════════════════════════════════════════ */

@Composable
private fun ProductsPane(repo: StorefrontRepository, snackbar: SnackbarHostState, bulkOn: Boolean = false) {
  val scope = rememberCoroutineScope()
  val view = rememberViewMode("storefront-products", ScreenKind.MANAGEMENT)
  var items by remember { mutableStateOf<List<StorefrontProductDto>?>(null) }
  var error by remember { mutableStateOf<String?>(null) }
  var query by remember { mutableStateOf("") }
  var filter by remember { mutableStateOf("all") }
  val selected = remember { mutableStateListOf<String>() }
  var editing by remember { mutableStateOf<StorefrontProductDto?>(null) }
  var bulkBusy by remember { mutableStateOf(false) }
  var bulkReport by remember { mutableStateOf<StorefrontOwner.BulkReport?>(null) }
  //  M2 (پرچمِ `bulk`): یک درخواست برای همه، با کلیدِ پایدارِ هر بخش؛ بخشِ نرسیده می‌ماند تا «دوباره بفرست»
  var queue by remember { mutableStateOf<BulkPublish.Queue?>(null) }
  var bulkEditing by remember { mutableStateOf(false) }

  suspend fun load() {
    val all = mutableListOf<StorefrontProductDto>()
    var cursor: String? = null
    var fail: ApiFailure? = null
    for (page in 0 until 100) {
      val r = repo.products(cursor)
      val v = r.valueOrNull()
      if (v == null) { r.onFailure { fail = it }; break }
      all += v.items
      cursor = v.nextCursor ?: break
    }
    if (fail != null && all.isEmpty()) error = fail!!.userMessage else { items = all; error = null }
  }
  LaunchedEffect(Unit) { load() }

  suspend fun saveOne(
    p: StorefrontProductDto, published: Boolean, online: Long?, max: Long?,
    description: String? = null, specs: List<Pair<String, String>>? = null,
  ): String? {
    var err: String? = null
    repo.setProduct(p.id, published, online, max, description, specs).onFailure { err = it.userMessage }
    if (err == null) items = items?.map {
      if (it.id != p.id) it
      else it.copy(
        published = published, onlinePriceMinor = online, maxQtyMilli = max,
        description = description ?: it.description,
        specs = specs?.map { s -> ir.vil3ntec.tohid.data.repo.StorefrontSpecDto(s.first, s.second) } ?: it.specs,
      )
    }
    return err
  }

  fun drain(start: BulkPublish.Queue) {
    bulkBusy = true
    scope.launch {
      var q = start
      while (true) {
        val part = q.next ?: break
        val outcome = repo.setProducts(part)
        q = q.after(part, outcome)
        queue = q
        if (outcome is BulkPublish.Outcome.Retry) break
      }
      if (q.done) selected.clear()
      bulkBusy = false
      load()
      snackbar.showSnackbar(BulkPublish.summarize(q).text + (q.lastError?.let { " ($it)" } ?: ""))
    }
  }

  fun sendBulk(changes: List<BulkPublish.Change>) {
    val (q, invalid) = BulkPublish.queueOf(ir.vil3ntec.tohid.sync.v1.Ulid.next(), changes)
    queue = q
    bulkReport = null
    if (invalid.isNotEmpty()) scope.launch { snackbar.showSnackbar(invalid.first().second) }
    if (!q.done) drain(q)
  }

  fun bulk(publish: Boolean) {
    val list = items.orEmpty().filter { it.id in selected }
    if (bulkOn) { sendBulk(list.map { BulkPublish.Change(it.id, published = publish) }); return }
    bulkBusy = true
    scope.launch {
      val results = list.map { p -> p.id to saveOne(p, publish, p.onlinePriceMinor, p.maxQtyMilli) }
      bulkReport = StorefrontOwner.bulkReport(results)
      selected.clear()
      bulkBusy = false
      snackbar.showSnackbar(bulkReport!!.summary)
    }
  }

  val list = items
  Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      TohidSearchBar(query, { query = it }, "جست‌وجوی نامِ کالا", Modifier.weight(1f))
      ViewModeToggle(view)
    }
    TohidFilterRow(listOf("all" to "همه", "on" to "در ویترین", "off" to "بیرون از ویترین"), filter, { filter = it })
    when {
      list == null && error != null -> TohidErrorState("فهرست نیامد", error!!, onRetry = { scope.launch { load() } })
      list == null -> TohidLoadingState(rows = 5)
      list.isEmpty() -> TohidEmptyState("هنوز کالایی همگام نشده", "کالاها پس از همگام‌سازی با سرور این‌جا دیده می‌شوند.")
      else -> {
        val q = query.trim()
        val shown = list.filter {
          (q.isEmpty() || it.name.contains(q, ignoreCase = true)) &&
            when (filter) { "on" -> it.published; "off" -> !it.published; else -> true }
        }
        if (selected.isNotEmpty()) {
          Row(
            Modifier.fillMaxWidth().clip(Shape.card).background(Shop.colors.primaryTint).padding(8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Text("${StorefrontOwner.toFa(selected.size.toString())} انتخاب‌شده", Modifier.weight(1f), fontWeight = FontWeight.Bold)
            TohidButton("انتشار", onClick = { bulk(true) }, enabled = !bulkBusy)
            TohidSecondaryButton("برداشتن", onClick = { bulk(false) }, enabled = !bulkBusy)
            if (bulkOn) TextButton(onClick = { bulkEditing = true }, enabled = !bulkBusy) { Text("قیمت و سقف") }
            TextButton(onClick = { selected.clear() }) { Text("×") }
          }
        }
        queue?.let { q ->
          val sum = BulkPublish.summarize(q)
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
              if (bulkBusy) "در حالِ فرستادن… " + sum.text else sum.text,
              Modifier.weight(1f), style = MaterialTheme.typography.bodySmall,
              color = if (sum.waiting + sum.failed + sum.notFound + sum.notLive == 0) Shop.colors.success else Shop.colors.warning,
            )
            if (!q.done && !bulkBusy) TohidSecondaryButton("دوباره بفرست", onClick = { drain(q) })
          }
        }
        bulkReport?.let { r ->
          Text(r.summary + r.failed.joinToString("") { (id, e) -> "\n• " + (list.firstOrNull { it.id == id }?.name ?: id) + ": " + e },
            style = MaterialTheme.typography.bodySmall, color = if (r.failed.isEmpty()) Shop.colors.success else Shop.colors.warning)
        }
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          if (shown.isEmpty()) EmptyNote("کالایی با این جست‌وجو نیست")
          if (view.mode == ViewMode.TABLE) {
            ProductTableHeader(allSelected = shown.isNotEmpty() && shown.all { it.id in selected }) { on ->
              if (on) shown.forEach { if (it.id !in selected) selected += it.id } else selected.removeAll(shown.map { it.id }.toSet())
            }
          }
          for (p in shown) {
            val sel = p.id in selected
            val toggleSel = { if (sel) selected.remove(p.id) else selected.add(p.id); Unit }
            val publish = { on: Boolean ->
              scope.launch { saveOne(p, on, p.onlinePriceMinor, p.maxQtyMilli)?.let { snackbar.showSnackbar(it) } }; Unit
            }
            if (view.mode == ViewMode.TABLE) ProductRow(p, sel, toggleSel, publish) { editing = p }
            else ProductCard(p, sel, toggleSel, publish) { editing = p }
          }
        }
      }
    }
  }

  if (bulkEditing) {
    BulkEditDialog(selected.size, onDismiss = { bulkEditing = false }) { price, max, description ->
      bulkEditing = false
      sendBulk(selected.toList().map { id ->
        BulkPublish.Change(
          id, setOnlinePrice = price != null, onlinePriceMinor = price?.getOrNull(),
          setMaxQty = max != null, maxQtyMilli = max?.getOrNull(), description = description,
        )
      })
    }
  }

  editing?.let { p ->
    ProductEditDialog(p, onDismiss = { editing = null }) { online, max, description, specs ->
      scope.launch {
        val err = saveOne(p, p.published, online, max, description, specs)
        if (err == null) { editing = null; snackbar.showSnackbar("ذخیره شد") } else snackbar.showSnackbar(err)
      }
    }
  }
}

@Composable
private fun StatusPill(published: Boolean, active: Boolean) {
  //  همان نشانِ مشترک و هفت توکنِ وضعیتِ VILL3N: در ویترین = موفق، بقیه = غیرفعال
  val (text, tone) = when {
    !active -> "غیرفعال در دفتر" to ir.vil3ntec.tohid.ui.components.common.PillTone.NEUTRAL
    published -> "در ویترین" to ir.vil3ntec.tohid.ui.components.common.PillTone.SUCCESS
    else -> "بیرون از ویترین" to ir.vil3ntec.tohid.ui.components.common.PillTone.NEUTRAL
  }
  ir.vil3ntec.tohid.ui.components.common.StatusPill(text, tone)
}

private fun priceText(p: StorefrontProductDto): String {
  val online = p.onlinePriceMinor
  val base = p.priceMinor
  return when {
    online != null -> "${money(online / 100.0)} ؋ (آنلاین)"
    base != null -> "${money(base / 100.0)} ؋"
    else -> "بی‌قیمت — در ویترین دیده نمی‌شود"
  }
}

@Composable
private fun ProductCard(p: StorefrontProductDto, selected: Boolean, onSelect: () -> Unit, onPublish: (Boolean) -> Unit, onEdit: () -> Unit) {
  Panel {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Checkbox(checked = selected, onCheckedChange = { onSelect() }, modifier = Modifier.semantics { contentDescription = "انتخابِ ${p.name}" })
      Column(Modifier.weight(1f)) {
        Text(p.name, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(listOf(p.category, p.unit).filter { it.isNotBlank() }.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = Shop.colors.muted)
        NotLiveLine(p)
      }
      Switch(checked = p.published, onCheckedChange = onPublish, modifier = Modifier.semantics { contentDescription = "در ویترین: ${p.name}" })
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      StatusPill(p.published, p.active)
      Text(priceText(p), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
      TextButton(onClick = onEdit) { Text("قیمت و سقف") }
    }
  }
}

@Composable
private fun ProductTableHeader(allSelected: Boolean, onAll: (Boolean) -> Unit) {
  Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
    Checkbox(checked = allSelected, onCheckedChange = onAll, modifier = Modifier.semantics { contentDescription = "انتخابِ همه" })
    Text("کالا", Modifier.weight(2.2f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
    Text("قیمت", Modifier.weight(1.4f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
    Text("سقفِ هر سفارش", Modifier.weight(1f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
    Text("وضعیت", Modifier.weight(1.2f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
    Spacer(Modifier.width(150.dp))
  }
}

@Composable
private fun ProductRow(p: StorefrontProductDto, selected: Boolean, onSelect: () -> Unit, onPublish: (Boolean) -> Unit, onEdit: () -> Unit) {
  Row(
    Modifier.fillMaxWidth().clip(Shape.card).background(Shop.colors.surfaceSolid).border(1.dp, Shop.colors.border, Shape.card).padding(horizontal = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Checkbox(checked = selected, onCheckedChange = { onSelect() }, modifier = Modifier.semantics { contentDescription = "انتخابِ ${p.name}" })
    Column(Modifier.weight(2.2f)) {
      Text(p.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
      Text(p.category.ifBlank { "—" }, style = MaterialTheme.typography.bodySmall, color = Shop.colors.muted)
      NotLiveLine(p)
    }
    Text(priceText(p), Modifier.weight(1.4f), style = MaterialTheme.typography.bodySmall)
    Text(p.maxQtyMilli?.let { StorefrontOwner.toFa(StorefrontOwner.formatQtyMilli(it)) } ?: "—", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
    Box(Modifier.weight(1.2f)) { StatusPill(p.published, p.active) }
    Switch(checked = p.published, onCheckedChange = onPublish, modifier = Modifier.semantics { contentDescription = "در ویترین: ${p.name}" })
    TextButton(onClick = onEdit) { Text("قیمت و سقف") }
  }
}

/** M2: «در بازار نمی‌آید: …» — فقط وقتی سرور دلیل فرستاده (پرچمِ گروهی) */
@Composable
private fun NotLiveLine(p: StorefrontProductDto) {
  val line = p.notLive?.let { BulkPublish.reasonLine(it) } ?: return
  Text(line, style = MaterialTheme.typography.bodySmall, color = Shop.colors.warning, maxLines = 2, overflow = TextOverflow.Ellipsis)
}

/**
 *  قیمت، سقف و توضیحِ یکسان برای همهٔ انتخاب‌شده‌ها. هر بخش جدا «عوض شود» دارد؛
 *  بی آن همان قبلیِ هر کالا می‌ماند. `Result` ِ تهی = «قیمتِ دفتر» / «بی سقف».
 */
@Composable
private fun BulkEditDialog(
  count: Int,
  onDismiss: () -> Unit,
  onSave: (Result<Long?>?, Result<Long?>?, String?) -> Unit,
) {
  var setPrice by remember { mutableStateOf(false) }
  var price by remember { mutableStateOf("") }
  var setMax by remember { mutableStateOf(false) }
  var max by remember { mutableStateOf("") }
  var setDesc by remember { mutableStateOf(false) }
  var description by remember { mutableStateOf("") }
  var err by remember { mutableStateOf<String?>(null) }
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("${StorefrontOwner.toFa(count.toString())} کالا") },
    text = {
      Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SwitchRow("قیمتِ آنلاین را عوض کن", setPrice) { setPrice = it; err = null }
        if (setPrice) TohidTextField(price, { price = it; err = null }, "قیمتِ آنلاین (خالی = قیمتِ دفتر)", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
        SwitchRow("سقفِ هر سفارش را عوض کن", setMax) { setMax = it; err = null }
        if (setMax) TohidTextField(max, { max = it; err = null }, "سقفِ هر سفارش (خالی = بی سقف)", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
        SwitchRow("توضیح را عوض کن", setDesc) { setDesc = it; err = null }
        if (setDesc) TohidTextField(description, { description = it.take(BulkPublish.MAX_DESCRIPTION); err = null }, "توضیح برای مشتری", singleLine = false)
        err?.let { Text(it, color = Shop.colors.danger, style = MaterialTheme.typography.bodySmall) }
      }
    },
    confirmButton = {
      TextButton(onClick = {
        val o = if (setPrice) runCatching { StorefrontOwner.parseMinor(price) } else null
        val m = if (setMax) runCatching { StorefrontOwner.parseQtyMilli(max) } else null
        err = o?.exceptionOrNull()?.message ?: m?.exceptionOrNull()?.message
          ?: if (!setPrice && !setMax && !setDesc) "یکی را برای عوض کردن روشن کنید" else null
        if (err == null) onSave(o, m, if (setDesc) description.trim() else null)
      }) { Text("ذخیره برای همه") }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
  )
}

@Composable
private fun ProductEditDialog(
  p: StorefrontProductDto,
  onDismiss: () -> Unit,
  onSave: (Long?, Long?, String, List<Pair<String, String>>) -> Unit,
) {
  var online by remember { mutableStateOf(p.onlinePriceMinor?.let { StorefrontOwner.formatMinor(it) } ?: "") }
  var max by remember { mutableStateOf(p.maxQtyMilli?.let { StorefrontOwner.formatQtyMilli(it) } ?: "") }
  //  WEB-08: توضیح و مشخصاتِ عمومی — یادداشتِ داخلیِ دفتر هرگز به ویترین نمی‌رود
  var description by remember { mutableStateOf(p.description) }
  var specs by remember { mutableStateOf(StorefrontSpecs.format(p.specs.map { it.name to it.value })) }
  var err by remember { mutableStateOf<String?>(null) }
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(p.name) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("قیمتِ دفتر: " + (p.priceMinor?.let { "${money(it / 100.0)} ؋" } ?: "ندارد"), style = MaterialTheme.typography.bodySmall)
        TohidTextField(online, { online = it; err = null }, "قیمتِ آنلاین (خالی = قیمتِ دفتر)", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
        TohidTextField(max, { max = it; err = null }, "سقفِ هر سفارش (${p.unit.ifBlank { "واحد" }}، خالی = بی سقف)", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
        TohidTextField(description, { description = it.take(StorefrontSpecs.MAX_DESCRIPTION); err = null }, "توضیح برای مشتری (اختیاری)", singleLine = false)
        TohidTextField(specs, { specs = it; err = null }, "مشخصات — هر خط «نام: مقدار» (اختیاری)", placeholder = "وزن: ۱ کیلو", singleLine = false)
        err?.let { Text(it, color = Shop.colors.danger, style = MaterialTheme.typography.bodySmall) }
      }
    },
    confirmButton = {
      TextButton(onClick = {
        val o = runCatching { StorefrontOwner.parseMinor(online) }
        val m = runCatching { StorefrontOwner.parseQtyMilli(max) }
        val (sp, spErr) = StorefrontSpecs.parse(specs)
        err = o.exceptionOrNull()?.message ?: m.exceptionOrNull()?.message ?: spErr
        if (err == null) onSave(o.getOrNull(), m.getOrNull(), description.trim(), sp.orEmpty())
      }) { Text("ذخیره") }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
  )
}

/* ══════════════════════════════════════════════════════════════════
   صفِ آفلاین (M1، §۳۱.۲ بندِ ۲) — آنچه هنوز به سرور نرسیده
   ══════════════════════════════════════════════════════════════════ */

/**
 *  بالای صفحه: هر ویرایشِ نرسیده با حالِ راستش (هرگز «ثبت شد»)، «دوباره بفرست»، و برای
 *  ناسازگاری با دستگاهِ دیگر دو راه: «بفرست و جایگزین کن» یا «کنار بگذار» (با تأیید).
 */
@Composable
internal fun OutboxBanner() {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val items: List<OutboxEntry> = StorefrontQueue.entries.value.let { StorefrontQueue.pendingAll(context) }
  val sending = StorefrontQueue.sending.value
  var discarding by remember { mutableStateOf<OutboxEntry?>(null) }
  if (items.isEmpty()) return

  Panel {
    Text(StorefrontOutbox.NOT_CONFIRMED, color = Shop.colors.warning, style = MaterialTheme.typography.bodyMedium)
    Spacer(Modifier.height(6.dp))
    for (e in items) {
      Text(StorefrontOutbox.statusLine(e), style = MaterialTheme.typography.bodySmall, color = Shop.colors.text)
      if (e.conflict) {
        Text(StorefrontOutbox.CONFLICT_NOTE, style = MaterialTheme.typography.bodySmall, color = Shop.colors.muted)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          TohidSecondaryButton("بفرست و جایگزین کن", onClick = {
            StorefrontQueue.keepMine(context, e.id)
            scope.launch { StorefrontQueue.flush(context, force = true) }
          }, enabled = !sending)
          TohidSecondaryButton("کنار بگذار", onClick = { discarding = e }, enabled = !sending)
        }
      }
      Spacer(Modifier.height(4.dp))
    }
    if (items.any { !it.conflict }) {
      TohidSecondaryButton(if (sending) "در حالِ فرستادن…" else "دوباره بفرست",
        onClick = { scope.launch { StorefrontQueue.flush(context, force = true) } }, enabled = !sending)
    }
  }

  discarding?.let { e ->
    TohidConfirmDialog(
      title = "کنار گذاشتنِ «${e.change.label}»؟",
      message = "این ویرایش هرگز به سرور نرسیده و پس از کنار گذاشتن برنمی‌گردد. آنچه روی سرور است می‌ماند.",
      confirmText = "کنار بگذار", danger = true,
      onConfirm = { StorefrontQueue.discard(context, e.id); discarding = null },
      onDismiss = { discarding = null },
    )
  }
}
