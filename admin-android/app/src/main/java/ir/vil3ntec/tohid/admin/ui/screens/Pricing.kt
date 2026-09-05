package ir.vil3ntec.tohid.admin.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.admin.net.AdminApi
import ir.vil3ntec.tohid.admin.net.Session
import ir.vil3ntec.tohid.admin.ui.*
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 *  نرخ‌ها و تخفیف‌ها.
 *
 *  ── چه چیزی اینجا درست شد ──────────────────────────────────────────
 *  تا امروز نرخ‌های وی‌آی‌پی فقط با دست بردن در دیتابیس عوض می‌شدند و
 *  تخفیف اصلاً وجود نداشت. یعنی برای یک جشنوارهٔ سه‌روزه هم باید کسی
 *  به سرور SSH می‌زد.
 *
 *  ── تخفیف چطور کار می‌کند ──────────────────────────────────────────
 *  قیمتِ اصلی دست نمی‌خورد؛ تخفیف کنارش می‌نشیند. پس وقتی مهلتش تمام
 *  شد، قیمت خودش برمی‌گردد و لازم نیست عددِ قبلی را جایی یادداشت کنید.
 *  در برنامه و سایت، قیمتِ قبلی خط‌خورده کنارِ قیمتِ تازه دیده می‌شود.
 */
@Composable
fun PricingScreen(session: Session) {
  val c = Admin.colors
  val scope = rememberCoroutineScope()

  var plans by remember { mutableStateOf<JSONArray?>(null) }
  var busy by remember { mutableStateOf(false) }
  var error by remember { mutableStateOf<String?>(null) }
  var editing by remember { mutableStateOf<JSONObject?>(null) }
  var discounting by remember { mutableStateOf<JSONObject?>(null) }
  var showCodes by remember { mutableStateOf(false) }

  fun load() {
    val token = session.token ?: return
    busy = true
    scope.launch {
      runCatching { AdminApi(session.serverUrl).plans(token) }
        .onSuccess { plans = it; error = null }
        .onFailure { error = (it as? AdminApi.ApiError)?.message ?: "خوانده نشد" }
      busy = false
    }
  }
  LaunchedEffect(Unit) { load() }

  editing?.let { p ->
    PlanSheet(session, p) { changed -> editing = null; if (changed) load() }
    return
  }
  discounting?.let { p ->
    DiscountSheet(session, p) { changed -> discounting = null; if (changed) load() }
    return
  }
  if (showCodes) {
    VipCodesScreen(session) { showCodes = false }
    return
  }

  Column(Modifier.fillMaxSize().background(c.bg).verticalScroll(rememberScrollState()).padding(16.dp)) {
    Text("نرخ‌ها و تخفیف", style = MaterialTheme.typography.titleMedium, color = c.text, fontWeight = FontWeight.Bold)
    Text(
      "همین عددها در برنامه و سایت دیده می‌شوند.",
      style = MaterialTheme.typography.labelSmall, color = c.muted,
    )

    Spacer(Modifier.height(14.dp))
    ErrorNote(error)

    val list = plans
    if (list == null || list.length() == 0) {
      Panel {
        Text(
          if (busy) "در حال خواندن…" else "پلنی نیست.",
          style = MaterialTheme.typography.bodySmall, color = c.muted,
        )
      }
    } else {
      for (i in 0 until list.length()) {
        val p = list.optJSONObject(i) ?: continue
        PlanCard(
          plan = p,
          onEdit = { editing = p },
          onDiscount = { discounting = p },
        )
        Spacer(Modifier.height(10.dp))
      }
    }

    Spacer(Modifier.height(8.dp))
    Panel {
      SectionTitle("کد اشتراک")
      Text(
        "یک کد شش‌رقمی بسازید و ایمیل طرف را بنویسید — سرور خودش کد را برایش می‌فرستد. " +
          "او همان شش رقم را در برنامه یا سایت می‌زند و اشتراکش فعال می‌شود.",
        style = MaterialTheme.typography.bodySmall, color = c.muted,
      )
      Spacer(Modifier.height(10.dp))
      PrimaryButton("کدهای اشتراک", Modifier.fillMaxWidth()) { showCodes = true }
    }

    Spacer(Modifier.height(14.dp))
    GhostButton("تازه کردن", Modifier.fillMaxWidth(), enabled = !busy) { load() }
    Spacer(Modifier.height(30.dp))
  }
}

@Composable
private fun PlanCard(plan: JSONObject, onEdit: () -> Unit, onDiscount: () -> Unit) {
  val c = Admin.colors
  val discount = plan.optJSONObject("discount")
  val full = plan.optInt("fullPrice", plan.optInt("price"))
  val price = plan.optInt("price")

  Panel {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
      Column(Modifier.weight(1f)) {
        Text(
          plan.optString("title"),
          style = MaterialTheme.typography.titleSmall, color = c.text, fontWeight = FontWeight.Bold,
        )
        Text(
          "${plan.optInt("amount").fa()} ${unitName(plan.optString("unit"))} · ${plan.optInt("days").fa()} روز",
          style = MaterialTheme.typography.labelSmall, color = c.muted,
        )
      }
      if (!plan.optBoolean("active", true)) StatusChip("خاموش", c.muted)
      else if (discount != null) StatusChip("٪${discount.optInt("percent").fa()} تخفیف", c.warn)
    }

    Spacer(Modifier.height(10.dp))
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      Text(
        price.fa(),
        style = MaterialTheme.typography.headlineSmall,
        color = if (discount != null) c.success else c.text,
        fontWeight = FontWeight.Bold,
      )
      //  قیمتِ قبلی، خط‌خورده — همان چیزی که کاربر در برنامه می‌بیند
      if (discount != null) {
        Text(
          full.fa(),
          style = MaterialTheme.typography.bodyMedium,
          color = c.muted,
          textDecoration = TextDecoration.LineThrough,
          modifier = Modifier.padding(bottom = 3.dp),
        )
      }
      Text("افغانی", style = MaterialTheme.typography.labelSmall, color = c.muted, modifier = Modifier.padding(bottom = 5.dp))
    }

    if (discount != null) {
      val until = discount.optLong("until", 0L)
      Text(
        buildString {
          append("تخفیف: ${discount.optInt("savings").fa()} افغانی کمتر")
          if (discount.optString("label").isNotBlank()) append(" · ${discount.optString("label")}")
          if (until > 0) append(" · تا ${jalali(until)}")
        },
        style = MaterialTheme.typography.labelSmall, color = c.warn,
        modifier = Modifier.padding(top = 4.dp),
      )
    }

    Spacer(Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      GhostButton("نرخ", Modifier.weight(1f)) { onEdit() }
      GhostButton(
        if (discount != null) "تخفیف" else "افزودن تخفیف",
        Modifier.weight(1f),
        tint = c.warn,
      ) { onDiscount() }
    }
  }
}

/** عوض کردنِ خودِ نرخ. */
@Composable
private fun PlanSheet(session: Session, plan: JSONObject, onDone: (Boolean) -> Unit) {
  val c = Admin.colors
  val scope = rememberCoroutineScope()
  val code = plan.optString("code")

  var title by rememberSaveable { mutableStateOf(plan.optString("title")) }
  var price by rememberSaveable { mutableStateOf(plan.optInt("fullPrice", plan.optInt("price")).toString()) }
  var amount by rememberSaveable { mutableStateOf(plan.optInt("amount").toString()) }
  var unit by rememberSaveable { mutableStateOf(plan.optString("unit").ifBlank { "month" }) }
  var badge by rememberSaveable { mutableStateOf(plan.optString("badge")) }
  var active by rememberSaveable { mutableStateOf(plan.optBoolean("active", true)) }
  var busy by remember { mutableStateOf(false) }
  var error by remember { mutableStateOf<String?>(null) }

  BackHandler { onDone(false) }

  Column(Modifier.fillMaxSize().background(c.bg).verticalScroll(rememberScrollState()).padding(16.dp)) {
    Header("نرخِ ${plan.optString("title")}") { onDone(false) }

    Spacer(Modifier.height(12.dp))
    Field(value = title, onValueChange = { title = it }, label = "عنوان")
    Spacer(Modifier.height(10.dp))
    Field(
      value = price, onValueChange = { price = it.filter { ch -> ch.isDigit() } },
      label = "قیمت (افغانی)",
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    )
    Spacer(Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      Field(
        value = amount, onValueChange = { amount = it.filter { ch -> ch.isDigit() } },
        label = "مدت", modifier = Modifier.weight(1f),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
      )
    }
    Spacer(Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      Chip("روز", unit == "day") { unit = "day" }
      Chip("هفته", unit == "week") { unit = "week" }
      Chip("ماه", unit == "month") { unit = "month" }
      Chip("سال", unit == "year") { unit = "year" }
    }
    Spacer(Modifier.height(10.dp))
    Field(value = badge, onValueChange = { badge = it }, label = "نشان (مثلاً «پیشنهاد ما»)")

    Spacer(Modifier.height(12.dp))
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
      Text("نشان دادن در برنامه", style = MaterialTheme.typography.bodyMedium, color = c.text, modifier = Modifier.weight(1f))
      Switch(checked = active, onCheckedChange = { active = it })
    }

    Spacer(Modifier.height(14.dp))
    ErrorNote(error)
    PrimaryButton("ذخیره", Modifier.fillMaxWidth(), busy = busy) {
      val token = session.token ?: return@PrimaryButton
      busy = true
      scope.launch {
        runCatching {
          AdminApi(session.serverUrl).savePlan(
            token, code, title.trim(), price.toIntOrNull() ?: 0,
            amount.toIntOrNull() ?: 1, unit, badge.trim(), active,
          )
        }
          .onSuccess { onDone(true) }
          .onFailure { error = (it as? AdminApi.ApiError)?.message ?: "ذخیره نشد"; busy = false }
      }
    }
    Text(
      "قیمتِ اینجا قیمتِ اصلی است. اگر تخفیف گذاشته باشید، همان تخفیف روی این عدد حساب می‌شود.",
      style = MaterialTheme.typography.labelSmall, color = c.muted,
      modifier = Modifier.padding(top = 8.dp),
    )
    Spacer(Modifier.height(30.dp))
  }
}

/** گذاشتن یا برداشتنِ تخفیف. */
@Composable
private fun DiscountSheet(session: Session, plan: JSONObject, onDone: (Boolean) -> Unit) {
  val c = Admin.colors
  val scope = rememberCoroutineScope()
  val code = plan.optString("code")
  val full = plan.optInt("fullPrice", plan.optInt("price"))
  val existing = plan.optJSONObject("discount")

  var mode by rememberSaveable { mutableStateOf("percent") }
  var percent by rememberSaveable { mutableStateOf(existing?.optInt("percent")?.toString() ?: "20") }
  var fixed by rememberSaveable { mutableStateOf("") }
  var label by rememberSaveable { mutableStateOf(existing?.optString("label") ?: "") }
  var days by rememberSaveable { mutableStateOf("") }
  var busy by remember { mutableStateOf(false) }
  var error by remember { mutableStateOf<String?>(null) }

  BackHandler { onDone(false) }

  //  پیش‌نمایشِ همان چیزی که کاربر خواهد دید
  val preview = when {
    mode == "price" -> fixed.toIntOrNull() ?: full
    else -> {
      val pct = percent.toIntOrNull() ?: 0
      if (pct in 1..95) Math.round(full * (100 - pct) / 100.0).toInt() else full
    }
  }

  Column(Modifier.fillMaxSize().background(c.bg).verticalScroll(rememberScrollState()).padding(16.dp)) {
    Header("تخفیفِ ${plan.optString("title")}") { onDone(false) }

    Spacer(Modifier.height(12.dp))
    Panel {
      Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
          preview.fa(),
          style = MaterialTheme.typography.headlineMedium,
          color = if (preview < full) c.success else c.text,
          fontWeight = FontWeight.Bold,
        )
        if (preview < full) {
          Text(
            full.fa(), style = MaterialTheme.typography.titleSmall, color = c.muted,
            textDecoration = TextDecoration.LineThrough, modifier = Modifier.padding(bottom = 4.dp),
          )
        }
        Text("افغانی", style = MaterialTheme.typography.labelSmall, color = c.muted, modifier = Modifier.padding(bottom = 6.dp))
      }
      Text(
        if (preview < full) "کاربر همین را می‌بیند." else "هنوز تخفیفی نیست.",
        style = MaterialTheme.typography.labelSmall, color = c.muted,
      )
    }

    Spacer(Modifier.height(14.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      Chip("درصدی", mode == "percent") { mode = "percent" }
      Chip("قیمت ثابت", mode == "price") { mode = "price" }
    }

    Spacer(Modifier.height(10.dp))
    if (mode == "percent") {
      Field(
        value = percent, onValueChange = { percent = it.filter { ch -> ch.isDigit() }.take(2) },
        label = "درصد تخفیف (۱ تا ۹۵)",
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
      )
    } else {
      Field(
        value = fixed, onValueChange = { fixed = it.filter { ch -> ch.isDigit() } },
        label = "قیمت با تخفیف (کمتر از ${full.fa()})",
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
      )
    }

    Spacer(Modifier.height(10.dp))
    Field(value = label, onValueChange = { label = it }, label = "نامِ تخفیف (مثلاً «جشنوارهٔ عید»)")

    Spacer(Modifier.height(10.dp))
    Field(
      value = days, onValueChange = { days = it.filter { ch -> ch.isDigit() }.take(3) },
      label = "چند روز؟ (خالی یعنی بی‌مهلت)",
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    )

    Spacer(Modifier.height(14.dp))
    ErrorNote(error)

    PrimaryButton("گذاشتن تخفیف", Modifier.fillMaxWidth(), busy = busy) {
      val token = session.token ?: return@PrimaryButton
      busy = true
      scope.launch {
        val until = days.toIntOrNull()?.takeIf { it > 0 }
          ?.let { System.currentTimeMillis() + it * 24L * 3600 * 1000 }
        runCatching {
          AdminApi(session.serverUrl).setDiscount(
            token, code,
            percent = if (mode == "percent") percent.toIntOrNull() else null,
            price = if (mode == "price") fixed.toIntOrNull() else null,
            label = label.trim(),
            until = until,
          )
        }
          .onSuccess { onDone(true) }
          .onFailure { error = (it as? AdminApi.ApiError)?.message ?: "انجام نشد"; busy = false }
      }
    }

    if (existing != null) {
      Spacer(Modifier.height(10.dp))
      GhostButton("برداشتنِ تخفیف", Modifier.fillMaxWidth(), tint = c.danger) {
        val token = session.token ?: return@GhostButton
        busy = true
        scope.launch {
          runCatching { AdminApi(session.serverUrl).clearDiscount(token, code) }
            .onSuccess { onDone(true) }
            .onFailure { error = (it as? AdminApi.ApiError)?.message ?: "انجام نشد"; busy = false }
        }
      }
    }

    Text(
      "قیمتِ اصلی دست نمی‌خورد. وقتی مهلت تمام شود، قیمت خودش برمی‌گردد — لازم نیست یادتان باشد عددِ قبلی چه بود.",
      style = MaterialTheme.typography.labelSmall, color = c.muted,
      modifier = Modifier.padding(top = 10.dp),
    )
    Spacer(Modifier.height(30.dp))
  }
}

/**
 *  کدهای اشتراک.
 *
 *  ── چرا این‌طور ────────────────────────────────────────────────────
 *  تا امروز برای دادنِ اشتراک به کسی باید دکانش را پیدا می‌کردید و دستی
 *  تمدید می‌زدید — یعنی طرف باید اول ثبت‌نام می‌کرد و شما هم باید همان
 *  لحظه پشت پنل بودید.
 *
 *  حالا کد می‌سازید و ایمیلش را می‌نویسید؛ **سرور** ایمیل را می‌فرستد.
 *  دیگر لازم نیست خودتان کد را به کسی برسانید.
 */
@Composable
private fun VipCodesScreen(session: Session, onBack: () -> Unit) {
  val c = Admin.colors
  val scope = rememberCoroutineScope()

  var codes by remember { mutableStateOf<JSONArray?>(null) }
  var plansList by remember { mutableStateOf<JSONArray?>(null) }
  var busy by remember { mutableStateOf(false) }
  var error by remember { mutableStateOf<String?>(null) }

  var plan by rememberSaveable { mutableStateOf("") }
  var days by rememberSaveable { mutableStateOf("") }
  var email by rememberSaveable { mutableStateOf("") }
  var note by rememberSaveable { mutableStateOf("") }
  var made by remember { mutableStateOf<JSONObject?>(null) }

  BackHandler { onBack() }

  fun load() {
    val token = session.token ?: return
    busy = true
    scope.launch {
      val api = AdminApi(session.serverUrl)
      runCatching { api.vipCodes(token) }.onSuccess { codes = it }
      runCatching { api.plans(token) }.onSuccess {
        plansList = it
        if (plan.isBlank() && it.length() > 0) plan = it.optJSONObject(0)?.optString("code") ?: ""
      }
      busy = false
    }
  }
  LaunchedEffect(Unit) { load() }

  Column(Modifier.fillMaxSize().background(c.bg).verticalScroll(rememberScrollState()).padding(16.dp)) {
    Header("کدهای اشتراک") { onBack() }

    Spacer(Modifier.height(12.dp))
    Panel {
      SectionTitle("کد تازه")

      val pl = plansList
      if (pl != null && pl.length() > 0) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          for (i in 0 until pl.length()) {
            val p = pl.optJSONObject(i) ?: continue
            Chip(p.optString("title"), plan == p.optString("code")) { plan = p.optString("code") }
          }
        }
        Spacer(Modifier.height(10.dp))
      }

      Field(
        value = days, onValueChange = { days = it.filter { ch -> ch.isDigit() }.take(4) },
        label = "چند روز؟ (خالی = مدتِ همان پلن)",
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
      )
      Spacer(Modifier.height(10.dp))
      Field(
        value = email, onValueChange = { email = it.trim() },
        label = "ایمیل گیرنده — سرور خودش می‌فرستد",
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
      )
      Spacer(Modifier.height(10.dp))
      Field(value = note, onValueChange = { note = it }, label = "یادداشت (اختیاری)")

      Spacer(Modifier.height(12.dp))
      ErrorNote(error)

      PrimaryButton("ساختن و فرستادن", Modifier.fillMaxWidth(), busy = busy, enabled = plan.isNotBlank()) {
        val token = session.token ?: return@PrimaryButton
        busy = true
        scope.launch {
          runCatching {
            AdminApi(session.serverUrl).createVipCode(
              token, plan, days.toIntOrNull(), email.trim(), note.trim(), null, 30,
            )
          }
            .onSuccess { made = it; email = ""; note = ""; error = null; load() }
            .onFailure { error = (it as? AdminApi.ApiError)?.message ?: "ساخته نشد"; busy = false }
        }
      }
    }

    //  کدِ خام فقط همین یک بار — بعد از این حتی سرور هم نشانش نمی‌دهد
    made?.let { m ->
      Spacer(Modifier.height(12.dp))
      Panel {
        Text("کد ساخته شد", style = MaterialTheme.typography.titleSmall, color = c.success, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        Box(
          Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(c.surface2).padding(vertical = 16.dp),
          contentAlignment = Alignment.Center,
        ) {
          Text(
            m.optString("code").fa(),
            style = MaterialTheme.typography.headlineMedium,
            color = c.text, fontWeight = FontWeight.Bold,
          )
        }
        Spacer(Modifier.height(8.dp))
        when (m.optString("emailStatus")) {
          "sent" -> Text("به ایمیل فرستاده شد.", style = MaterialTheme.typography.bodySmall, color = c.success)
          "failed" -> Text(
            "ایمیل نرفت: ${m.optString("emailError")}. کد را خودتان برسانید یا تنظیمات ایمیل را درست کنید.",
            style = MaterialTheme.typography.bodySmall, color = c.danger,
          )
          else -> Text(
            "ایمیلی داده نشده بود — این کد را خودتان به طرف بدهید.",
            style = MaterialTheme.typography.bodySmall, color = c.muted,
          )
        }
        Spacer(Modifier.height(6.dp))
        Text(
          "این کد دیگر جایی نشان داده نمی‌شود. اگر لازمش دارید، همین حالا برش دارید.",
          style = MaterialTheme.typography.labelSmall, color = c.warn,
        )
        Spacer(Modifier.height(10.dp))
        GhostButton("باشد", Modifier.fillMaxWidth()) { made = null }
      }
    }

    Spacer(Modifier.height(16.dp))
    SectionTitle("کدهای ساخته‌شده")
    val list = codes
    Panel {
      if (list == null || list.length() == 0) {
        Text(
          if (busy) "در حال خواندن…" else "هنوز کدی نساخته‌اید.",
          style = MaterialTheme.typography.bodySmall, color = c.muted,
        )
      } else {
        for (i in 0 until list.length()) {
          val v = list.optJSONObject(i) ?: continue
          Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
              Text(
                "••••${v.optString("hint").fa()} · ${v.optInt("days").fa()} روز",
                style = MaterialTheme.typography.bodyMedium, color = c.text, fontWeight = FontWeight.Medium,
              )
              Text(
                listOfNotNull(
                  v.optString("email").ifBlank { null },
                  v.optString("note").ifBlank { null },
                  jalali(v.optLong("createdAt")),
                ).joinToString(" · "),
                style = MaterialTheme.typography.labelSmall, color = c.muted,
              )
              if (v.optString("emailStatus") == "failed") {
                Text("ایمیل نرفت", style = MaterialTheme.typography.labelSmall, color = c.danger)
              }
            }
            when (v.optString("status")) {
              "active" -> {
                StatusChip("زنده", c.success)
                Spacer(Modifier.width(6.dp))
                TextButton(onClick = {
                  val token = session.token ?: return@TextButton
                  scope.launch {
                    runCatching { AdminApi(session.serverUrl).revokeVipCode(token, v.optString("id")) }
                      .onSuccess { load() }
                      .onFailure { error = (it as? AdminApi.ApiError)?.message ?: "انجام نشد" }
                  }
                }) { Text("باطل", style = MaterialTheme.typography.labelSmall, color = c.danger) }
              }
              "used" -> StatusChip("خرج شد", c.muted)
              "revoked" -> StatusChip("باطل", c.danger)
              else -> StatusChip("گذشته", c.muted)
            }
          }
          if (i < list.length() - 1) HorizontalDivider(color = c.border)
        }
      }
    }
    Spacer(Modifier.height(30.dp))
  }
}

@Composable
fun Header(title: String, onBack: () -> Unit) {
  val c = Admin.colors
  Row(verticalAlignment = Alignment.CenterVertically) {
    IconButton(onClick = onBack) {
      Icon(Icons.Filled.ArrowForward, contentDescription = "برگشت", tint = c.text)
    }
    Text(title, style = MaterialTheme.typography.titleMedium, color = c.text, fontWeight = FontWeight.Bold)
  }
}

fun unitName(unit: String): String = when (unit) {
  "day" -> "روزه"
  "week" -> "هفته‌ای"
  "month" -> "ماهه"
  "year" -> "ساله"
  else -> unit
}
