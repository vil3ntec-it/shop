package ir.vil3ntec.tohid.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.storefront.StorefrontDelivery
import ir.vil3ntec.tohid.core.storefront.StorefrontDelivery.RuleDraft
import ir.vil3ntec.tohid.core.storefront.StorefrontDelivery.ZoneDraft
import ir.vil3ntec.tohid.core.storefront.StorefrontOwner
import ir.vil3ntec.tohid.data.repo.Backend
import ir.vil3ntec.tohid.data.repo.StorefrontDeliveryDto
import ir.vil3ntec.tohid.data.repo.StorefrontDeliveryRepository
import ir.vil3ntec.tohid.ui.theme.Shop
import kotlinx.coroutines.launch

/**
 *  برگهٔ «ارسال و زمان» ِ ویترین (CHK-06/07/10/12) — ناحیه‌های ارسال
 *  (چندضلعی یا دایره، هزینه، حداقل، «رایگان از…»، هزینهٔ سبدِ کوچک) و بازه‌های
 *  زمان‌بندی با ظرفیت و هزینهٔ اضافهٔ بازه. همان قاعده‌های وب و سرور (`core/storefront/StorefrontDelivery.kt`)؛
 *  «ذخیره شد» فقط پس از پاسخِ سرور. بی نقشهٔ پولی: نقطه‌ها «عرض، طول» نوشته
 *  می‌شوند و مرکزِ دایره را موقعیتِ فعلیِ کاربر هم می‌تواند بدهد.
 */
@Composable
fun StorefrontDeliveryPane(snackbar: SnackbarHostState) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val repo = remember(context) { StorefrontDeliveryRepository(Backend.api(context)) }
  var loaded by remember { mutableStateOf<StorefrontDeliveryDto?>(null) }
  var loadError by remember { mutableStateOf<String?>(null) }
  var version by remember { mutableIntStateOf(0) }

  suspend fun load() {
    loadError = null
    repo.load().onSuccess { loaded = it; version++ }.onFailure { loadError = it.userMessage }
  }
  LaunchedEffect(Unit) { load() }

  val d = loaded
  when {
    d == null && loadError == null -> TohidLoadingState(rows = 3)
    d == null -> TohidErrorState("ارسال و زمان باز نشد", loadError ?: "اتصال را بررسی کنید.", onRetry = { scope.launch { load() } })
    else -> DeliveryEditor(d, version, snackbar, onSave = { checked, asap ->
      repo.save(checked, asap).onSuccess { loaded = it; version++; snackbar.showSnackbar("ارسال و زمان ذخیره شد") }
        .onFailure { snackbar.showSnackbar(it.userMessage) }
    })
  }
}

@Composable
private fun DeliveryEditor(
  d: StorefrontDeliveryDto,
  version: Int,
  snackbar: SnackbarHostState,
  onSave: suspend (StorefrontDelivery.Checked, Boolean) -> Unit,
) {
  val scope = rememberCoroutineScope()
  val zones = remember(version) { mutableStateListOf<ZoneDraft>().apply { addAll(d.zones.map { it.draft() }) } }
  val rules = remember(version) { mutableStateListOf<RuleDraft>().apply { addAll(d.slotRules.map { it.draft() }) } }
  var asap by remember(version) { mutableStateOf(d.allowAsap) }
  var slotDays by remember(version) { mutableStateOf(d.slotDays.toString()) }
  val errors = remember(version) { mutableStateMapOf<String, String>() }
  var saving by remember { mutableStateOf(false) }

  Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Panel {
      Text("ناحیه‌های ارسال", style = MaterialTheme.typography.titleSmall)
      Text("بی ناحیه، همان هزینه و حداقلِ «تنظیمات» برای همه‌جا. با ناحیه، پینِ مشتری خودکار سنجیده می‌شود؛ بیرون از ناحیه‌ها ارسال پذیرفته نمی‌شود و «دریافت از دکان» پیشنهاد می‌شود.",
        style = MaterialTheme.typography.bodySmall, color = Shop.colors.muted)
      if (zones.isEmpty()) Text("هنوز ناحیه‌ای نیست.", style = MaterialTheme.typography.bodySmall, color = Shop.colors.muted)
      zones.forEachIndexed { i, z -> ZoneCard(i, z, errors, onChange = { zones[i] = it }, onRemove = { zones.removeAt(i); errors.clear() }) }
      TohidSecondaryButton("افزودنِ ناحیه", onClick = { zones.add(ZoneDraft()) })
    }
    Panel {
      Text("زمان‌بندی", style = MaterialTheme.typography.titleSmall)
      Text("بازه‌ها به ساعتِ دکان و بی روزهای تعطیل نشان داده می‌شوند؛ پر شدنِ ظرفیت ⇒ «پر است». مشتری تعدادِ سفارش‌ها را نمی‌بیند.",
        style = MaterialTheme.typography.bodySmall, color = Shop.colors.muted)
      DeliverySwitch("سفارشِ «هرچه زودتر» هم پذیرفته شود", asap) { asap = it }
      TohidTextField(slotDays, { slotDays = it }, "چند روزِ پیشِ رو نشان داده شود (۱ تا ۱۴)", error = errors["slotDays"],
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
      if (rules.isEmpty()) Text("هنوز بازه‌ای نیست؛ همه «هرچه زودتر».", style = MaterialTheme.typography.bodySmall, color = Shop.colors.muted)
      rules.forEachIndexed { i, r -> RuleCard(i, r, errors, onChange = { rules[i] = it }, onRemove = { rules.removeAt(i); errors.clear() }) }
      TohidSecondaryButton("افزودنِ بازه", onClick = { rules.add(RuleDraft()) })
    }
    TohidButton(if (saving) "در حالِ ذخیره…" else "ذخیرهٔ ارسال و زمان", onClick = {
      val c = StorefrontDelivery.check(zones.toList(), rules.toList(), slotDays)
      errors.clear(); errors.putAll(c.errors)
      if (c.errors.isNotEmpty()) { scope.launch { snackbar.showSnackbar("چند کادر درست نیست؛ پیامِ کنارِ هر کادر را ببینید") }; return@TohidButton }
      saving = true
      scope.launch { onSave(c, asap); saving = false }
    }, enabled = !saving, busy = saving, modifier = Modifier.fillMaxWidth())
  }
}

@Composable
private fun ZoneCard(i: Int, z: ZoneDraft, errors: Map<String, String>, onChange: (ZoneDraft) -> Unit, onRemove: () -> Unit) {
  val k = "z$i"
  val num = KeyboardOptions(keyboardType = KeyboardType.Decimal)
  TohidCard {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Text("ناحیهٔ " + StorefrontOwner.toFa((i + 1).toString()), style = MaterialTheme.typography.labelLarge)
      TohidTextField(z.name, { onChange(z.copy(name = it)) }, "نامِ ناحیه", error = errors["$k.name"])
      TohidFilterRow(listOf("polygon" to "چندضلعی", "radius" to "دایره دورِ یک نقطه"), z.kind, { onChange(z.copy(kind = it)) })
      if (z.kind == "radius") {
        TohidTextField(z.center, { onChange(z.copy(center = it)) }, "مرکز (عرض، طول)", placeholder = "34.53, 69.17", error = errors["$k.center"])
        TohidTextField(z.radiusKm, { onChange(z.copy(radiusKm = it)) }, "شعاع (کیلومتر)", error = errors["$k.radius"], keyboardOptions = num)
      } else {
        TohidTextField(z.polygon, { onChange(z.copy(polygon = it)) }, "نقطه‌ها — هر خط «عرض، طول» (دست‌کم ۳)",
          placeholder = "34.50, 69.12", error = errors["$k.polygon"], singleLine = false)
      }
      TohidTextField(z.fee, { onChange(z.copy(fee = it)) }, "هزینهٔ ارسال (افغانی)", error = errors["$k.fee"], keyboardOptions = num)
      TohidTextField(z.min, { onChange(z.copy(min = it)) }, "حداقلِ سفارش (افغانی)", error = errors["$k.min"], keyboardOptions = num)
      TohidTextField(z.free, { onChange(z.copy(free = it)) }, "ارسالِ رایگان از (اختیاری)", error = errors["$k.free"], keyboardOptions = num)
      //  CHK-06: به‌جای ردِ سبدِ زیرِ حداقل
      TohidTextField(z.small, { onChange(z.copy(small = it)) }, "هزینهٔ سبدِ کوچک (اختیاری؛ زیرِ حداقل به‌جای رد)",
        error = errors["$k.small"], keyboardOptions = num)
      DeliverySwitch("این ناحیه فعال باشد", z.active) { onChange(z.copy(active = it)) }
      TohidSecondaryButton("حذفِ ناحیه", onClick = onRemove)
    }
  }
}

@Composable
private fun RuleCard(i: Int, r: RuleDraft, errors: Map<String, String>, onChange: (RuleDraft) -> Unit, onRemove: () -> Unit) {
  val k = "r$i"
  val num = KeyboardOptions(keyboardType = KeyboardType.Number)
  TohidCard {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Text("بازهٔ " + StorefrontOwner.toFa((i + 1).toString()), style = MaterialTheme.typography.labelLarge)
      TohidFilterRow(listOf("delivery" to "ارسال", "pickup" to "دریافت از دکان"), r.fulfillment, { onChange(r.copy(fulfillment = it)) })
      Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (day in StorefrontOwner.DISPLAY_DAYS) {
          val on = day in r.weekdays
          TohidFilterChip(StorefrontOwner.DAY_NAMES[day], on, onClick = { onChange(r.copy(weekdays = if (on) r.weekdays - day else r.weekdays + day)) })
        }
      }
      errors["$k.days"]?.let { Text(it, color = Shop.colors.danger, style = MaterialTheme.typography.bodySmall) }
      TohidTextField(r.range, { onChange(r.copy(range = it)) }, "ساعت (مثلِ 10:00-12:00)", error = errors["$k.range"])
      TohidTextField(r.capacity, { onChange(r.copy(capacity = it)) }, "ظرفیت (چند سفارش)", error = errors["$k.capacity"], keyboardOptions = num)
      TohidTextField(r.cutoff, { onChange(r.copy(cutoff = it)) }, "مهلتِ سفارش (دقیقه پیش از آغاز)", error = errors["$k.cutoff"], keyboardOptions = num)
      //  CHK-07: هزینهٔ زمانِ تحویل — فقط ارسال
      if (r.fulfillment != "pickup") {
        TohidTextField(r.fee, { onChange(r.copy(fee = it)) }, "هزینهٔ اضافهٔ این بازه (افغانی؛ ۰ = بی هزینه)", error = errors["$k.fee"],
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
      }
      DeliverySwitch("این بازه فعال باشد", r.active) { onChange(r.copy(active = it)) }
      TohidSecondaryButton("حذفِ بازه", onClick = onRemove)
    }
  }
}

@Composable
private fun DeliverySwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
  Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { onChange(!checked) }, verticalAlignment = Alignment.CenterVertically) {
    Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
    Switch(checked = checked, onCheckedChange = onChange)
  }
}
