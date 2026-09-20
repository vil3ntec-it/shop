package ir.vil3ntec.tohid.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.net.ApiEndpoints
import ir.vil3ntec.tohid.core.net.ApiJson
import ir.vil3ntec.tohid.data.repo.Backend
import ir.vil3ntec.tohid.sync.v1.HeartbeatCache
import ir.vil3ntec.tohid.sync.v1.SoftLock
import ir.vil3ntec.tohid.fa
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

/**
 *  «اشتراکِ من» — بندِ ۲۱٫۵.
 *
 *  پلن، آغاز و پایان، روزهای مانده با نوارِ رنگی، «دائمی ✓» بی شمارش،
 *  دستگاه‌های فعال، پرداخت‌ها و پیام‌های مدیر.
 *
 *  ⛔ **هیچ عددِ قیمتی این‌جا ساخته نمی‌شود.** هرچه دیده می‌شود از سرور
 *  آمده — همان قاعدهٔ همیشگیِ مخزن.
 *  ⛔ **رنگ و روزِ مانده هم از سرور می‌آید** (`subscriptionView` در
 *  `routes/portal.js`). قاعدهٔ جدا این‌جا نوشته نمی‌شود، وگرنه روزی
 *  مشتری در برنامه سبز می‌بیند و در پورتال سرخ.
 *  ⚠️ داده از **تپشِ کش‌شده** می‌آید، پس این صفحه آفلاین هم چیزی برای
 *  نشان دادن دارد.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MySubscriptionScreen() {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val cache = remember { HeartbeatCache(context) }

  var beat by remember { mutableStateOf(cache.read()) }
  var devices by remember { mutableStateOf<List<JsonObject>>(emptyList()) }
  var payments by remember { mutableStateOf<List<JsonObject>>(emptyList()) }
  var notices by remember { mutableStateOf<List<JsonObject>>(emptyList()) }
  var note by remember { mutableStateOf("") }
  var busy by remember { mutableStateOf(false) }

  var loginOpen by remember { mutableStateOf(false) }

  suspend fun refresh() {
    if (!Backend.isReady(context)) { note = "برای دیدنِ اشتراک، اول با ایمیل وارد شوید."; return }
    busy = true
    Backend.codeLogin(context).heartbeat()
      .onSuccess { body ->
        cache.write(body)
        beat = body
        note = ""
        //  قفلِ نرم از همین تپش تصمیم می‌گیرد، نه از ساعتِ گوشی
        SoftLock.refresh(context)
      }
      .onFailure { note = it.userMessage }
    val api = Backend.api(context)
    runCatching { devices = (api.get(ApiEndpoints.Me.DEVICES)["devices"] as? JsonArray)?.mapNotNull { it as? JsonObject } ?: emptyList() }
    runCatching { payments = (api.get(ApiEndpoints.Me.PAYMENTS)["payments"] as? JsonArray)?.mapNotNull { it as? JsonObject } ?: emptyList() }
    runCatching { notices = (api.get(ApiEndpoints.Me.NOTICES)["notices"] as? JsonArray)?.mapNotNull { it as? JsonObject } ?: emptyList() }
    busy = false
  }

  LaunchedEffect(Unit) { refresh() }

  Column(
    Modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState())
      .padding(horizontal = 16.dp, vertical = 12.dp),
  ) {
    val sub = beat?.get("subscription") as? JsonObject
    if (sub == null) {
      EmptyNote(
        if (note.isNotBlank()) note
        else "هنوز چیزی از سرور نیامده. اینترنت که وصل شد، همین‌جا پر می‌شود.",
      )
    } else {
      val permanent = ApiJson.bool(sub, "permanent")
      val color = ApiJson.text(sub, "color").ifBlank { "grey" }
      val days = ApiJson.int(sub, "daysLeft")
      val progress = ApiJson.int(sub, "progress").coerceIn(0, 100)
      val tint = when (color) {
        "green" -> Shop.colors.success
        "yellow" -> Shop.colors.warning
        "red" -> Shop.colors.danger
        else -> Shop.colors.muted
      }

      Panel {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
          Text("پلن", style = MaterialTheme.typography.labelMedium, color = Shop.colors.muted)
          Text(
            ApiJson.text(sub, "plan").ifBlank { "—" },
            style = MaterialTheme.typography.labelLarge,
            color = Shop.colors.primary,
            fontWeight = FontWeight.Bold,
          )
        }
        Spacer(Modifier.height(10.dp))
        Text(
          //  ⚠️ «دائمی ✓» شمارش ندارد — نه صفر، نه «۰ روز مانده»
          if (permanent) "دائمی ✓" else ApiJson.text(sub, "label").ifBlank { "${days.fa()} روز مانده" },
          style = MaterialTheme.typography.titleMedium,
          color = tint,
          fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        Box(
          Modifier
            .fillMaxWidth()
            .height(10.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(Shop.colors.fieldBorder),
        ) {
          Box(
            Modifier
              .fillMaxWidth(if (permanent) 1f else progress / 100f)
              .fillMaxHeight()
              .clip(RoundedCornerShape(999.dp))
              .background(tint),
          )
        }
        Spacer(Modifier.height(10.dp))
        PlanRow("آغاز", stamp(ApiJson.long(sub, "startsAt")))
        PlanRow("پایان", if (permanent) "دائمی ✓" else stamp(ApiJson.long(sub, "endsAt")))
        PlanRow("وضعیت", ApiJson.text(sub, "status"))
      }

      //  ⚠️ بنرِ هفت‌روزه — همان قاعدهٔ قفلِ نرم
      if (!permanent && ApiJson.bool(sub, "active") && days in 1..7) {
        Spacer(Modifier.height(12.dp))
        Panel {
          Text(
            "${days.fa()} روز تا پایانِ اشتراک. پس از آن برنامه فقط‌خواندنی می‌شود — " +
              "هیچ داده‌ای پاک نمی‌شود و خروجی و چاپ هم کار می‌کند.",
            style = MaterialTheme.typography.bodySmall,
            color = Shop.colors.warning,
          )
        }
      }
      if (!ApiJson.bool(sub, "active") && ApiJson.text(sub, "status") != "none") {
        Spacer(Modifier.height(12.dp))
        Panel {
          Text(
            "اشتراک تمام شده. برنامه فقط‌خواندنی است — دفترتان دست‌نخورده سرِ جایش " +
              "است و خروجی و چاپ هم کار می‌کند.",
            style = MaterialTheme.typography.bodySmall,
            color = Shop.colors.danger,
          )
        }
      }
    }

    Spacer(Modifier.height(18.dp))
    SectionTitle("دستگاه‌های فعال")
    Panel {
      if (devices.isEmpty()) EmptyNote("دستگاهی ثبت نشده.")
      else devices.forEach { d ->
        PlanRow(
          ApiJson.text(d, "name").ifBlank { ApiJson.text(d, "device_uid") },
          stamp(ApiJson.long(d, "last_seen_at")),
        )
      }
    }

    Spacer(Modifier.height(18.dp))
    SectionTitle("پرداخت‌ها")
    Panel {
      if (payments.isEmpty()) EmptyNote("پرداختی ثبت نشده.")
      //  ⛔ عدد از سرور می‌آید؛ این‌جا هیچ قیمتی ساخته نمی‌شود
      else payments.forEach { p ->
        PlanRow(
          ApiJson.text(p, "planTitle").ifBlank { ApiJson.text(p, "plan") },
          ApiJson.text(p, "amountLabel").ifBlank { ApiJson.text(p, "amount") },
        )
      }
    }

    Spacer(Modifier.height(18.dp))
    SectionTitle("پیام‌های مدیر")
    Panel {
      if (notices.isEmpty()) EmptyNote("پیامی نیست.")
      else notices.take(10).forEach { n ->
        PlanRow(ApiJson.text(n, "title"), stamp(ApiJson.long(n, "createdAt")))
      }
    }

    Spacer(Modifier.height(16.dp))
    Button(
      onClick = { scope.launch { refresh() } },
      enabled = !busy,
      modifier = Modifier.fillMaxWidth().height(46.dp),
    ) { Text(if (busy) "در حالِ خواندن…" else "تازه کردن از سرور") }

    if (!Backend.isReady(context)) {
      Spacer(Modifier.height(10.dp))
      OutlinedButton(
        onClick = { loginOpen = true },
        modifier = Modifier.fillMaxWidth().height(46.dp),
      ) { Text("ورود با ایمیل و کدِ شش‌رقمی") }
    }

    if (note.isNotBlank()) {
      Spacer(Modifier.height(8.dp))
      Text(note, style = MaterialTheme.typography.labelMedium, color = Shop.colors.danger)
    }
    Spacer(Modifier.height(24.dp))
  }

  if (loginOpen) {
    ModalBottomSheet(onDismissRequest = { loginOpen = false }, containerColor = Shop.colors.bg) {
      CodeLoginSheet(
        onClose = { loginOpen = false },
        onSignedIn = {
          loginOpen = false
          scope.launch { refresh() }
        },
      )
    }
  }
}

@Composable
private fun PlanRow(label: String, value: String) {
  Row(
    Modifier.fillMaxWidth().padding(vertical = 6.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(label, style = MaterialTheme.typography.bodySmall, color = Shop.colors.muted)
    Text(
      value.ifBlank { "—" },
      style = MaterialTheme.typography.bodySmall,
      color = Shop.colors.text,
      fontWeight = FontWeight.Bold,
    )
  }
}

private fun stamp(ms: Long): String {
  if (ms <= 0) return "—"
  //  سرور گاهی ثانیه می‌دهد و گاهی میلی‌ثانیه؛ هر دو باید درست خوانده شوند
  val millis = if (ms < 100_000_000_000L) ms * 1000 else ms
  return ir.vil3ntec.tohid.formatMillis(millis)
}

/**
 *  بنرِ اشتراک، بالای هر صفحه — بندِ ۲۱٫۸.
 *
 *  ⛔ متنش صریح می‌گوید **داده پاک نمی‌شود** و خروجی و چاپ کار می‌کنند.
 *  بنری که فقط بگوید «تمام شد» کاربر را می‌ترساند و تماسِ عصبانی
 *  می‌سازد.
 *  ⚠️ چیزی نمی‌پرسد و هیچ درخواستی نمی‌زند: فقط تپشِ کش‌شده را می‌خواند.
 */
@Composable
fun SubscriptionBanner(onOpen: () -> Unit) {
  val context = LocalContext.current
  val locked by SoftLock.locked.collectAsState()
  LaunchedEffect(Unit) { SoftLock.refresh(context) }
  val warn = remember(locked) { SoftLock.warnDays(context) }

  if (!locked && warn == null) return
  val tint = if (locked) Shop.colors.danger else Shop.colors.warning
  Row(
    Modifier
      .fillMaxWidth()
      .padding(horizontal = 14.dp, vertical = 8.dp)
      .clip(RoundedCornerShape(14.dp))
      .background(if (locked) Shop.colors.dangerTint else Shop.colors.warningTint)
      .padding(horizontal = 12.dp, vertical = 10.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      if (locked)
        "اشتراک تمام شده — برنامه فقط‌خواندنی است. دفترتان دست‌نخورده سرِ جایش است و خروجی و چاپ کار می‌کند."
      else
        "${(warn ?: 0).fa()} روز تا پایانِ اشتراک. پس از آن برنامه فقط‌خواندنی می‌شود؛ هیچ داده‌ای پاک نمی‌شود.",
      style = MaterialTheme.typography.labelMedium,
      color = tint,
      modifier = Modifier.weight(1f),
    )
    TextButton(onClick = onOpen) { Text("اشتراکِ من", color = tint) }
  }
}
