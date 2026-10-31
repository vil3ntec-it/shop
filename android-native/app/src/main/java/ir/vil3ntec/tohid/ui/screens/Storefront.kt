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
import ir.vil3ntec.tohid.core.storefront.StorefrontOwner
import ir.vil3ntec.tohid.core.storefront.StorefrontSpecs
import ir.vil3ntec.tohid.core.storefront.StorefrontOwner.Tone
import ir.vil3ntec.tohid.data.repo.Backend
import ir.vil3ntec.tohid.data.repo.PresenceStatusDto
import ir.vil3ntec.tohid.data.repo.StorefrontConfigDto
import ir.vil3ntec.tohid.data.repo.StorefrontProductDto
import ir.vil3ntec.tohid.data.repo.StorefrontRepository
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
    if (r != null) config.value = r
    return r
  }
}

private const val PREFS = "tohid-storefront"
private const val PENDING_KEY = "pending-presence"

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
          } else ProductsPane(repo, snackbar)
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
  val prefs = remember(context) { context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE) }
  var pending by remember { mutableStateOf(StorefrontOwner.decode(prefs.getString(PENDING_KEY, null))) }
  var sending by remember { mutableStateOf(false) }
  var notice by remember { mutableStateOf<String?>(null) }
  var untilText by remember { mutableStateOf("") }
  var untilError by remember { mutableStateOf<String?>(null) }

  if (settings == null) {
    TohidEmptyState("هنوز ویترینی ساخته نشده", "نشانی و نامِ ویترین را در «تنظیمات» بنویسید.", icon = Icons.Filled.Storefront,
      actionText = "رفتن به تنظیمات", onAction = onSettings)
    return
  }
  val tz = settings.timezone

  /** فرستادنِ اعلامِ در صف — همان شناسه و همان زمانِ دستگاه (PRES-02/08/09) */
  suspend fun flush() {
    val p = pending ?: return
    sending = true
    repo.declare(p, "")
      .onSuccess { r ->
        prefs.edit().remove(PENDING_KEY).apply()
        pending = null
        StorefrontGate.config.value = StorefrontGate.config.value?.let { c -> c.copy(settings = c.settings?.copy(status = r.status, presenceMode = r.status.mode, declaredUntil = r.status.declaredUntil)) }
        notice = when (r.applied) {
          false -> "ثبت شد، ولی وضعیتِ تازه‌تری از دستگاهِ دیگر جلوتر است؛ همان می‌ماند."
          else -> "ثبت شد — زمانِ سرور: ${StorefrontOwner.toFa(StorefrontOwner.clock(r.serverReceivedAt, tz))}"
        }
        snackbar.showSnackbar("وضعیتِ ویترین روی سرور ثبت شد")
      }
      .onFailure { e ->
        if (!e.retryable) {
          //  سرور رد کرد — در صف نمی‌ماند، ولی هیچ «ثبت شد» ی هم نیست
          prefs.edit().remove(PENDING_KEY).apply()
          pending = null
          notice = e.userMessage
        } else notice = StorefrontOwner.NOT_CONFIRMED
      }
    sending = false
  }

  fun declare(mode: String, until: Long? = null) {
    val p = StorefrontOwner.pending(mode, until, System.currentTimeMillis(), kotlin.random.Random.nextLong())
    prefs.edit().putString(PENDING_KEY, StorefrontOwner.encode(p)).apply()
    pending = p
    scope.launch { flush() }
  }

  LaunchedEffect(Unit) { if (pending != null) flush() }

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
private fun SettingsPane(settings: StorefrontSettingsDto?, repo: StorefrontRepository, snackbar: SnackbarHostState) {
  val scope = rememberCoroutineScope()
  val s = settings ?: StorefrontSettingsDto()
  var slug by remember(s.updatedAt) { mutableStateOf(s.slug) }
  var title by remember(s.updatedAt) { mutableStateOf(s.title) }
  var tagline by remember(s.updatedAt) { mutableStateOf(s.tagline) }
  var phone by remember(s.updatedAt) { mutableStateOf(s.contactPhone.replace(Regex("^\\+93"), "0")) }
  var accent by remember(s.updatedAt) { mutableStateOf(s.accent) }
  var notice by remember(s.updatedAt) { mutableStateOf(s.notice) }
  var enabled by remember(s.updatedAt) { mutableStateOf(s.enabled) }
  var delivery by remember(s.updatedAt) { mutableStateOf(s.fulfillment.delivery) }
  var pickup by remember(s.updatedAt) { mutableStateOf(s.fulfillment.pickup) }
  var fee by remember(s.updatedAt) { mutableStateOf(if (settings == null) "" else StorefrontOwner.formatMinor(s.deliveryFeeMinor)) }
  var minOrder by remember(s.updatedAt) { mutableStateOf(if (settings == null) "" else StorefrontOwner.formatMinor(s.minOrderMinor)) }
  var tz by remember(s.updatedAt) { mutableStateOf(s.timezone) }
  val days = remember(s.updatedAt) {
    mutableStateMapOf<Int, String>().apply {
      for (d in 0..6) put(d, StorefrontOwner.formatDay(s.weeklyHours[d.toString()].orEmpty().map { it[0] to it[1] }))
    }
  }
  var closed by remember(s.updatedAt) { mutableStateOf(s.closedDates.joinToString("، ")) }
  val errors = remember { mutableStateMapOf<String, String>() }
  var saving by remember { mutableStateOf(false) }

  fun build(): kotlinx.serialization.json.JsonObject? {
    errors.clear()
    StorefrontOwner.slugError(slug)?.let { errors["slug"] = it }
    if (enabled && title.isBlank()) errors["title"] = "برای روشن کردن، نامِ ویترین لازم است"
    if (accent.isNotBlank() && !Regex("^#[0-9a-fA-F]{6}$").matches(accent.trim())) errors["accent"] = "مثلِ #2c5ce6"
    if (!delivery && !pickup) errors["fulfillment"] = "دست‌کم یکی از ارسال یا دریافت از دکان"
    val feeMinor = runCatching { StorefrontOwner.parseMinor(fee, StorefrontOwner.MAX_FEE_MINOR) ?: 0L }.getOrElse { errors["fee"] = it.message ?: "مبلغ درست نیست"; 0L }
    val minMinor = runCatching { StorefrontOwner.parseMinor(minOrder) ?: 0L }.getOrElse { errors["min"] = it.message ?: "مبلغ درست نیست"; 0L }
    val hours = mutableMapOf<Int, List<Pair<Int, Int>>>()
    for (d in 0..6) {
      val r = StorefrontOwner.parseDay(days[d].orEmpty())
      if (r.error != null) errors["day$d"] = r.error else hours[d] = r.ranges
    }
    val (closedList, closedErr) = StorefrontOwner.parseClosedDates(closed)
    closedErr?.let { errors["closed"] = it }
    if (errors.isNotEmpty()) return null
    return buildJsonObject {
      put("slug", slug.trim().lowercase())
      put("enabled", enabled)
      put("title", title.trim())
      put("tagline", tagline.trim())
      put("contactPhone", phone.trim())
      put("accent", accent.trim().lowercase())
      put("notice", notice.trim())
      put("timezone", tz.trim())
      put("weeklyHours", StorefrontRepository.weeklyHoursJson(hours))
      put("closedDates", JsonArray(closedList.map { JsonPrimitive(it) }))
      put("acceptDelivery", delivery)
      put("acceptPickup", pickup)
      put("deliveryFeeMinor", feeMinor)
      put("minOrderMinor", minMinor)
    }
  }

  Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Panel {
      Text("نشانی و نام", style = MaterialTheme.typography.titleSmall)
      TohidTextField(slug, { slug = it }, "نشانی (زیرِ vill3n.top)", placeholder = "my-shop", error = errors["slug"],
        supporting = "نشانیِ مشتری: ${slug.ifBlank { "…" }}.vill3n.top")
      TohidTextField(title, { title = it }, "نامِ ویترین (نامِ کسب‌وکار، نه نامِ شخص)", error = errors["title"])
      TohidTextField(tagline, { tagline = it }, "یک جملهٔ کوتاه (اختیاری)")
      TohidTextField(phone, { phone = it }, "شمارهٔ تماسِ دکان", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
      TohidTextField(accent, { accent = it }, "رنگِ ویترین (اختیاری)", placeholder = "#2c5ce6", error = errors["accent"])
      TohidTextField(notice, { notice = it }, "پیامِ کوتاه کنارِ وضعیت (اختیاری)")
      SwitchRow("ویترین روشن باشد", enabled) { enabled = it }
    }
    //  M3: لوگو و تصویرِ سر — پرچمِ جدای `shop.storefront.media`؛ ذخیرهٔ جدا (`StorefrontMediaPanel.kt`)
    if (settings != null && StorefrontGate.config.value?.media == true) StorefrontMediaPanel(snackbar)
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
      TohidTextField(tz, { tz = it }, "منطقهٔ زمانیِ دکان", placeholder = "Asia/Kabul")
      for (d in StorefrontOwner.DISPLAY_DAYS) {
        TohidTextField(days[d].orEmpty(), { days[d] = it }, StorefrontOwner.DAY_NAMES[d], error = errors["day$d"])
      }
      TohidTextField(closed, { closed = it }, "تعطیلیِ استثنایی (تاریخ‌ها مثلِ 2026-10-20)", error = errors["closed"], singleLine = false)
    }
    TohidButton(if (saving) "در حالِ ذخیره…" else "ذخیرهٔ ویترین", onClick = {
      val body = build() ?: return@TohidButton
      saving = true
      scope.launch {
        repo.save(body)
          .onSuccess { saved ->
            StorefrontGate.config.value = StorefrontGate.config.value?.copy(settings = saved)
            snackbar.showSnackbar("ویترین ذخیره شد")
          }
          .onFailure { snackbar.showSnackbar(it.userMessage) }
        saving = false
      }
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
private fun ProductsPane(repo: StorefrontRepository, snackbar: SnackbarHostState) {
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

  fun bulk(publish: Boolean) {
    val list = items.orEmpty().filter { it.id in selected }
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
            TextButton(onClick = { selected.clear() }) { Text("×") }
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
    }
    Text(priceText(p), Modifier.weight(1.4f), style = MaterialTheme.typography.bodySmall)
    Text(p.maxQtyMilli?.let { StorefrontOwner.toFa(StorefrontOwner.formatQtyMilli(it)) } ?: "—", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
    Box(Modifier.weight(1.2f)) { StatusPill(p.published, p.active) }
    Switch(checked = p.published, onCheckedChange = onPublish, modifier = Modifier.semantics { contentDescription = "در ویترین: ${p.name}" })
    TextButton(onClick = onEdit) { Text("قیمت و سقف") }
  }
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
