package ir.vil3ntec.tohid.admin.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.admin.net.AdminApi
import ir.vil3ntec.tohid.admin.net.Session
import ir.vil3ntec.tohid.admin.ui.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 *  ══ پمپ‌بنزین‌ها ═══════════════════════════════════════════════════
 *
 *  ⛔ **این بخش تا امروز در اپِ مدیریت نبود.** مسیرهایش از مدت‌ها پیش
 *  روی سرور بودند (`/admin/pump/…`) و فقط پنلِ وب بلدشان بود. یعنی
 *  صاحبِ سامانه با گوشی‌اش می‌توانست به هر دکانی اشتراک بدهد ولی به
 *  هیچ پمپی — و همان‌جا هم که پمپ ثبت می‌شد، هیچ‌جا دیده نمی‌شد.
 *
 *  ── چرا دفترِ جدا ──────────────────────────────────────────────────
 *  اشتراکِ پمپ روی `stations` و `station_subscriptions` می‌نشیند، نه
 *  روی `shops`. قاعدهٔ صریحِ صاحب مخزن: «شاپ و پمپ ربطی به هم نداشته
 *  باشند، حتی یک ذره.» پس این صفحه هیچ‌وقت مسیرهای بخشِ دکان را صدا
 *  نمی‌زند، حتی وقتی شکلشان یکی است.
 *
 *  ⚠️ **پمپ می‌تواند صاحب نداشته باشد.** برنامهٔ کامپیوترِ پمپ با کدِ
 *  شش‌رقمی فعال می‌شود و حساب ندارد؛ گوشیِ صاحب بعداً با کدِ پیوستن
 *  می‌آید. پس هر جایی که نامِ صاحب را نشان می‌دهیم باید خالی بودنش را
 *  هم بلد باشد — وگرنه تازه‌ترین مشتری‌ها «بی‌نام» دیده می‌شوند.
 */
@Composable
fun PumpScreen(session: Session) {
  val c = Admin.colors

  var query by rememberSaveable { mutableStateOf("") }
  var rows by remember { mutableStateOf<JSONArray?>(null) }
  var busy by remember { mutableStateOf(false) }
  var error by remember { mutableStateOf<String?>(null) }
  var open by remember { mutableStateOf<String?>(null) }
  var codes by rememberSaveable { mutableStateOf(false) }
  var reloadKey by remember { mutableIntStateOf(0) }

  LaunchedEffect(query, reloadKey) {
    val token = session.token ?: return@LaunchedEffect
    delay(350)
    busy = true
    runCatching { AdminApi(session.serverUrl).stations(token, query.trim()) }
      .onSuccess { rows = it; error = null }
      .onFailure { error = (it as? AdminApi.ApiError)?.message ?: "فهرست خوانده نشد" }
    busy = false
  }

  if (codes) {
    PumpCodesSheet(session) { codes = false }
    return
  }

  open?.let { id ->
    StationSheet(session, id, onBack = { open = null; reloadKey += 1 })
    return
  }

  Column(Modifier.fillMaxSize().background(c.bg).padding(16.dp)) {
    Field(value = query, onValueChange = { query = it }, label = "جست‌وجو — نام پمپ، کد یا صاحبش")
    Spacer(Modifier.height(10.dp))
    GhostButton(text = "کدهای اشتراکِ پمپ", modifier = Modifier.fillMaxWidth()) { codes = true }
    Spacer(Modifier.height(12.dp))
    ErrorNote(error)

    val list = rows
    if (list == null || list.length() == 0) {
      Panel {
        Text(
          if (busy) "در حال خواندن…" else "پمپی نیست.",
          style = MaterialTheme.typography.bodySmall,
          color = c.muted,
        )
      }
    } else {
      Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
        Panel {
          for (i in 0 until list.length()) {
            val s = list.optJSONObject(i) ?: continue
            //  ⚠️ صاحب می‌تواند نداشته باشد — پمپی که با کدِ شش‌رقمی
            //  فعال شده. آن‌وقت کدِ پمپ تنها چیزی است که می‌شناساندش.
            val owner = s.optString("owner_name").ifBlank { "بی صاحب — با کد فعال شده" }
            ClickRow(
              title = s.optString("name").ifBlank { "پمپ" },
              subtitle = "${s.optString("code")} · $owner".fa(),
              trailing = { SubChip(s.optString("sub_status"), s.optLong("ends_at")) },
            ) { open = s.optString("id") }
            if (i < list.length() - 1) HorizontalDivider(color = c.border)
          }
        }
        Spacer(Modifier.height(24.dp))
      }
    }
  }
}

/**
 *  پروندهٔ یک پمپ: اشتراک، اعضا، پوشه، و پشتیبان‌هایش.
 */
@Composable
private fun StationSheet(session: Session, stationId: String, onBack: () -> Unit) {
  val c = Admin.colors
  val scope = rememberCoroutineScope()

  var data by remember { mutableStateOf<JSONObject?>(null) }
  var plans by remember { mutableStateOf<JSONArray?>(null) }
  var busy by remember { mutableStateOf(false) }
  var error by remember { mutableStateOf<String?>(null) }
  var done by remember { mutableStateOf<String?>(null) }
  var granting by remember { mutableStateOf(false) }
  //  خبرهایی که خودِ برنامه فرستاده — «خبر نگرفتم» را همین‌جا می‌شود سنجید
  var events by remember { mutableStateOf<JSONArray?>(null) }

  fun load() {
    val token = session.token ?: return
    busy = true
    scope.launch {
      val api = AdminApi(session.serverUrl)
      runCatching { api.station(token, stationId) }
        .onSuccess { data = it; error = null }
        .onFailure { error = (it as? AdminApi.ApiError)?.message ?: "خوانده نشد" }
      //  ⚠️ پلن‌های **پمپ**، نه دکان. جدول یکی است و ستونِ `app` جدایشان
      //  می‌کند؛ بی آن، قیمتِ دکان به یک پمپ داده می‌شد.
      plans = runCatching { api.pumpPlans(token) }.getOrNull()
      //  ⚠️ نبودنش صفحه را نمی‌شکند: سرورِ به‌روزنشده این مسیر را ندارد
      events = runCatching { api.stationEvents(token, stationId) }.getOrNull()
      busy = false
    }
  }
  LaunchedEffect(stationId) { load() }

  val d = data
  val sub = d?.optJSONObject("subscription")

  if (granting) {
    GrantSheet(
      plans = plans,
      current = sub,
      busy = busy,
      noun = "پمپ",
      onBack = { granting = false },
      onGrant = { plan, days, note ->
        val token = session.token ?: return@GrantSheet
        busy = true
        scope.launch {
          runCatching { AdminApi(session.serverUrl).grantStation(token, stationId, plan, days, note) }
            .onSuccess { granting = false; done = "اشتراک ثبت شد"; load() }
            .onFailure { error = (it as? AdminApi.ApiError)?.message ?: "ثبت نشد"; busy = false }
        }
      },
    )
    return
  }

  Column(
    Modifier.fillMaxSize().background(c.bg).verticalScroll(rememberScrollState()).padding(16.dp)
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      IconButton(onClick = onBack) {
        Icon(Icons.Filled.ArrowForward, contentDescription = "برگشت", tint = c.text)
      }
      Text("پروندهٔ پمپ", style = MaterialTheme.typography.titleMedium, color = c.text, fontWeight = FontWeight.Bold)
    }
    Spacer(Modifier.height(10.dp))
    ErrorNote(error)
    done?.let {
      Text(it, style = MaterialTheme.typography.bodySmall, color = c.success, modifier = Modifier.padding(bottom = 8.dp))
    }

    if (d == null) {
      Panel { Text(if (busy) "در حال خواندن…" else "چیزی نیست.", style = MaterialTheme.typography.bodySmall, color = c.muted) }
      return@Column
    }

    val st = d.optJSONObject("station") ?: JSONObject()
    val owner = d.optJSONObject("owner")

    Panel {
      Text(
        st.optString("name").ifBlank { "پمپ" },
        style = MaterialTheme.typography.titleMedium, color = c.text, fontWeight = FontWeight.Bold,
      )
      Spacer(Modifier.height(8.dp))
      Row2("کدِ پمپ", st.optString("code").fa())
      Row2("صاحب", owner?.optString("name")?.ifBlank { null } ?: "هنوز کسی وصل نشده")
      owner?.optString("email")?.takeIf { it.isNotBlank() }?.let { Row2("ایمیل", it) }
      //  کدِ اپِ کارمندان — تا مدیر بتواند به صاحبِ پمپی که گمش کرده بگوید
      d.optString("accessCode").takeIf { it.isNotBlank() }?.let { Row2("کدِ اپِ کارمندان", it.fa()) }
      Row2("ساخته شده", jalali(st.optLong("createdAt")))
    }

    Spacer(Modifier.height(14.dp))
    SectionTitle("اشتراک")
    Panel {
      if (sub == null || !sub.optBoolean("active", sub.optString("status") == "active")) {
        Text(
          sub?.optString("status")?.takeIf { it.isNotBlank() && it != "none" }
            ?.let { "وضعیت: ${statusFa(it)}" }
            ?: "این پمپ اشتراکی ندارد.",
          style = MaterialTheme.typography.bodySmall,
          color = c.muted,
        )
      }
      if (sub != null && sub.optLong("endsAt") > 0) {
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
          Text(
            planName(sub.optString("plan")),
            style = MaterialTheme.typography.titleSmall, color = c.text,
            fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f),
          )
          SubChip(sub.optString("status"), sub.optLong("endsAt"))
        }
        Spacer(Modifier.height(6.dp))
        Row2("پایان", jalali(sub.optLong("endsAt")))
        Row2("روزِ مانده", sub.optInt("daysLeft").fa())
      }
    }

    Spacer(Modifier.height(12.dp))
    PrimaryButton(
      text = if (sub == null || sub.optLong("endsAt") <= 0) "فعال کردن اشتراک" else "تمدید یا تغییر",
      modifier = Modifier.fillMaxWidth(),
      enabled = !busy,
    ) { done = null; granting = true }

    val subId = sub?.optString("id").orEmpty()
    //  ⚠️ `sub != null` هم شرط است و نه فقط `subId`: بی آن، کاتلین
    //  داخلِ بلوک `sub` را هنوز nullable می‌بیند و کد کامپایل نمی‌شود.
    if (sub != null && subId.isNotBlank()) {
      Spacer(Modifier.height(8.dp))
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val suspended = sub.optString("status") == "suspended"
        GhostButton(
          text = if (suspended) "برگرداندن" else "معلق کردن",
          modifier = Modifier.weight(1f),
          enabled = !busy,
        ) {
          val token = session.token ?: return@GhostButton
          busy = true
          scope.launch {
            runCatching {
              AdminApi(session.serverUrl)
                .setStationSubStatus(token, subId, if (suspended) "active" else "suspended")
            }
              .onSuccess { done = "وضعیت عوض شد"; load() }
              .onFailure { error = (it as? AdminApi.ApiError)?.message ?: "نشد"; busy = false }
          }
        }
        GhostButton(
          text = "لغو",
          modifier = Modifier.weight(1f),
          enabled = !busy,
          tint = c.danger,
        ) {
          val token = session.token ?: return@GhostButton
          busy = true
          scope.launch {
            runCatching { AdminApi(session.serverUrl).setStationSubStatus(token, subId, "cancelled") }
              .onSuccess { done = "اشتراک لغو شد"; load() }
              .onFailure { error = (it as? AdminApi.ApiError)?.message ?: "نشد"; busy = false }
          }
        }
      }
    }

    //  ⛔ روشن و خاموش کردنِ خودِ پمپ — تا امروز فقط در پنلِ وب بود.
    //  پمپی که خاموش شود، برنامه‌اش دیگر روی پوشهٔ ابری نمی‌نویسد.
    Spacer(Modifier.height(12.dp))
    val stationOff = st.optString("status") == "disabled"
    GhostButton(
      text = if (stationOff) "روشن کردنِ پمپ" else "خاموش کردنِ پمپ",
      modifier = Modifier.fillMaxWidth(),
      enabled = !busy,
      tint = if (stationOff) c.success else c.danger,
    ) {
      val token = session.token ?: return@GhostButton
      busy = true
      scope.launch {
        runCatching {
          AdminApi(session.serverUrl)
            .setStationStatus(token, stationId, if (stationOff) "active" else "disabled")
        }
          .onSuccess { done = if (stationOff) "پمپ روشن شد" else "پمپ خاموش شد"; load() }
          .onFailure { error = (it as? AdminApi.ApiError)?.message ?: "نشد"; busy = false }
      }
    }

    val members = d.optJSONArray("members")
    if (members != null && members.length() > 0) {
      Spacer(Modifier.height(14.dp))
      SectionTitle("اعضا")
      Panel {
        for (i in 0 until members.length()) {
          val m = members.optJSONObject(i) ?: continue
          Row2(m.optString("name").ifBlank { "—" }, roleFa(m.optString("role")))
        }
      }
    }

    /*
     *  خبرهای همین پمپ.
     *
     *  ⛔ تا امروز هیچ‌جا دیده نمی‌شدند — چون اصلاً به ابر نمی‌رفتند.
     *  «اضافه برد» و «کم مانده» فقط روی سرورِ خانگیِ خودِ پمپ می‌ماندند،
     *  پس وقتی صاحبِ پمپ می‌گفت «خبر نگرفتم»، هیچ راهی نبود که معلوم شود
     *  خبر ساخته شده بود یا نه.
     */
    val evs = events
    if (evs != null && evs.length() > 0) {
      Spacer(Modifier.height(14.dp))
      SectionTitle("خبرهای این پمپ")
      Panel {
        for (i in 0 until minOf(evs.length(), 20)) {
          val e = evs.optJSONObject(i) ?: continue
          Row2(
            e.optString("title").ifBlank { eventFa(e.optString("kind")) },
            jalali(e.optLong("at")),
          )
        }
      }
    }

    Spacer(Modifier.height(14.dp))
    AccountBackupsPanel(session, "pump", stationId)

    Spacer(Modifier.height(30.dp))
  }
}

/**
 *  کدهای اشتراکِ پمپ.
 *
 *  ⚠️ کدِ خام فقط **همان یک بار** در پاسخ می‌آید؛ بعد از آن حتی خودِ
 *  سرور هم نمی‌تواند نشانش بدهد (فقط HMACش ذخیره می‌شود). پس یا همان
 *  لحظه برداشته می‌شود یا ایمیل کارش را می‌کند.
 */
@Composable
private fun PumpCodesSheet(session: Session, onBack: () -> Unit) {
  val c = Admin.colors
  val scope = rememberCoroutineScope()

  var codes by remember { mutableStateOf<JSONArray?>(null) }
  var plans by remember { mutableStateOf<JSONArray?>(null) }
  var picked by rememberSaveable { mutableStateOf("") }
  var days by rememberSaveable { mutableStateOf("") }
  var email by rememberSaveable { mutableStateOf("") }
  var phone by rememberSaveable { mutableStateOf("") }
  var note by rememberSaveable { mutableStateOf("") }
  var made by remember { mutableStateOf<String?>(null) }
  var error by remember { mutableStateOf<String?>(null) }
  var busy by remember { mutableStateOf(false) }
  var reload by remember { mutableIntStateOf(0) }

  LaunchedEffect(reload) {
    val token = session.token ?: return@LaunchedEffect
    val api = AdminApi(session.serverUrl)
    runCatching { api.pumpVipCodes(token) }.onSuccess { codes = it }
    plans = runCatching { api.pumpPlans(token) }.getOrNull()
    //  پلنِ پیش‌فرض همان اولی است، تا کادر هیچ‌وقت بی‌انتخاب نماند
    if (picked.isBlank()) picked = plans?.optJSONObject(0)?.optString("code").orEmpty()
  }

  Column(
    Modifier.fillMaxSize().background(c.bg).verticalScroll(rememberScrollState()).imePadding().padding(16.dp)
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      IconButton(onClick = onBack) {
        Icon(Icons.Filled.ArrowForward, contentDescription = "برگشت", tint = c.text)
      }
      Text("کدهای اشتراکِ پمپ", style = MaterialTheme.typography.titleMedium, color = c.text, fontWeight = FontWeight.Bold)
    }
    Spacer(Modifier.height(10.dp))
    ErrorNote(error)
    made?.let {
      Panel {
        Text(
          "کد: $it",
          style = MaterialTheme.typography.titleMedium, color = c.success, fontWeight = FontWeight.Bold,
        )
        Text(
          "همین حالا برش دارید — دیگر نشان داده نمی‌شود.",
          style = MaterialTheme.typography.labelSmall, color = c.muted,
        )
      }
      Spacer(Modifier.height(12.dp))
    }

    SectionTitle("کدِ تازه")
    /*
     *  ⛔ انتخابِ پلن اجباری شد، و این یک اصلاحِ واقعی است نه آرایش.
     *
     *  تا دیروز این صفحه هیچ کادرِ پلنی نداشت: اگر روزی نوشته می‌شد
     *  پلن `custom` می‌رفت، و پلنِ `custom` هیچ فهرستِ قابلیتی ندارد —
     *  و فهرستِ خالی روی سرور یعنی «پلنِ کامل». پس هر کدی که با روزِ
     *  دستی ساخته می‌شد، عملاً **وی‌آی‌پی** بود، حتی وقتی صاحب سامانه
     *  می‌خواست «استاندارد» بفروشد. مستقیم روی پول.
     *
     *  حالا پلن انتخاب می‌شود و «روز» فقط مدت را جلو می‌برد، نه مرزِ
     *  قابلیت‌ها را.
     */
    Panel {
      if (plans == null || plans?.length() == 0) {
        Text("پلنی از سرور خوانده نشد.", style = MaterialTheme.typography.bodySmall, color = c.muted)
      } else {
        val list = plans
        if (list != null) {
          for (i in 0 until list.length()) {
            val p = list.optJSONObject(i) ?: continue
            val code = p.optString("code")
            PlanRow(
              title = p.optString("title").ifBlank { planName(code) },
              subtitle = periodText(p.optInt("amount"), p.optString("unit")),
              selected = picked == code,
            ) { picked = code }
          }
        }
      }
      PlanRow(
        title = "مدت دلخواه — همهٔ قابلیت‌ها",
        subtitle = "بی مرزِ پلن؛ فقط وقتی که خودتان می‌خواهید",
        selected = picked == "custom",
      ) { picked = "custom" }
      Spacer(Modifier.height(10.dp))
      Field(
        value = days,
        onValueChange = { days = it.filter { ch -> ch.isDigit() }.take(4) },
        label = "چند روز (خالی = مدتِ پلن)",
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
      )
      Spacer(Modifier.height(10.dp))
      Field(
        value = email, onValueChange = { email = it },
        label = "ایمیلِ گیرنده (اختیاری) — سرور خودش می‌فرستد",
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
      )
      Spacer(Modifier.height(10.dp))
      Field(
        value = phone, onValueChange = { phone = it },
        label = "موبایلِ گیرنده (اختیاری) — کد پیامک می‌شود",
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
      )
      Spacer(Modifier.height(10.dp))
      Field(value = note, onValueChange = { note = it }, label = "یادداشت — برای چه کسی و چرا")
      Spacer(Modifier.height(12.dp))
      PrimaryButton(text = "ساختِ کد", modifier = Modifier.fillMaxWidth(), busy = busy) {
        val token = session.token ?: return@PrimaryButton
        busy = true
        error = null
        scope.launch {
          runCatching {
            //  ⚠️ پلنِ **انتخاب‌شده** می‌رود، نه «دلخواه». مرزِ
            //  قابلیت‌ها از همان پلن می‌آید و روزِ دستی فقط مدت است.
            val firstPlan = plans?.optJSONObject(0)?.optString("code").orEmpty()
            AdminApi(session.serverUrl).createPumpVipCode(
              token,
              plan = picked.ifBlank { firstPlan.ifBlank { "custom" } },
              days = days.toIntOrNull(),
              email = email.trim(),
              phone = phone.trim(),
              note = note.trim(),
            )
          }
            .onSuccess { out ->
              made = out.optString("code")
              //  نتیجهٔ ایمیل و پیامک را همان‌جا می‌گوییم — «ساخته شد»
              //  در حالی که چیزی بیرون نرفته، بدترین پیامِ ممکن است
              val mail = out.optString("emailStatus")
              val sms = out.optString("smsStatus")
              error = buildString {
                if (mail == "failed") append("ایمیل نرفت: ${out.optString("emailError")}. ")
                if (sms == "failed") append("پیامک نرفت: ${out.optString("smsError")}.")
              }.ifBlank { null }
              email = ""; phone = ""; note = ""
              reload += 1
            }
            .onFailure { error = (it as? AdminApi.ApiError)?.message ?: "ساخته نشد" }
          busy = false
        }
      }
    }

    Spacer(Modifier.height(14.dp))
    SectionTitle("کدهای ساخته‌شده")
    Panel {
      val list = codes
      if (list == null || list.length() == 0) {
        Text("هنوز کدی ساخته نشده است.", style = MaterialTheme.typography.bodySmall, color = c.muted)
      } else {
        for (i in 0 until list.length()) {
          val k = list.optJSONObject(i) ?: continue
          Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
              //  فقط دو رقمِ آخر — خودِ کد هیچ‌جا ذخیره نشده
              Text(
                "••••${k.optString("hint")}".fa(),
                style = MaterialTheme.typography.bodyMedium, color = c.text, fontWeight = FontWeight.Medium,
              )
              Text(
                buildString {
                  append(planName(k.optString("plan")))
                  if (!k.isNull("days")) append(" · ${k.optInt("days").fa()} روز")
                  k.optString("email").takeIf { it.isNotBlank() }?.let { append(" · $it") }
                },
                style = MaterialTheme.typography.labelSmall, color = c.muted,
              )
            }
            if (k.optString("status") == "active") {
              TextButton(onClick = {
                val token = session.token ?: return@TextButton
                scope.launch {
                  runCatching { AdminApi(session.serverUrl).revokePumpVipCode(token, k.optString("id")) }
                    .onSuccess { reload += 1 }
                    .onFailure { error = (it as? AdminApi.ApiError)?.message ?: "باطل نشد" }
                }
              }) { Text("باطل", color = c.danger, style = MaterialTheme.typography.labelMedium) }
            } else {
              StatusChip(if (k.optString("status") == "used") "مصرف‌شده" else "باطل", c.muted)
            }
          }
          if (i < list.length() - 1) HorizontalDivider(color = c.border)
        }
      }
    }
    Spacer(Modifier.height(30.dp))
  }
}

/** نامِ فارسیِ نوعِ خبر — همان شش نوعی که سرور می‌شناسد. */
private fun eventFa(s: String): String = when (s) {
  "sale" -> "فروش"
  "stock_out" -> "تمام شد"
  "low_stock" -> "کم مانده"
  "expense" -> "مصرف"
  "debt" -> "قرض"
  "note" -> "یادداشت"
  else -> s
}

private fun statusFa(s: String): String = when (s) {
  "active" -> "فعال"
  "suspended" -> "معلق"
  "expired" -> "تمام‌شده"
  "cancelled" -> "لغوشده"
  "none" -> "بدون اشتراک"
  else -> s
}

private fun roleFa(s: String): String = when (s) {
  "owner" -> "صاحب"
  "manager" -> "مدیر"
  "staff" -> "کارمند"
  else -> s
}
