package ir.vil3ntec.tohid.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.window.Dialog
import ir.vil3ntec.tohid.core.model.CatalogEntryDto
import ir.vil3ntec.tohid.core.model.MemberPermsDto
import ir.vil3ntec.tohid.core.perm.ApproverVault
import ir.vil3ntec.tohid.core.perm.PinGuard
import ir.vil3ntec.tohid.data.ShopRole
import ir.vil3ntec.tohid.data.StaffPins
import ir.vil3ntec.tohid.numericInput
import ir.vil3ntec.tohid.parseNumber
import ir.vil3ntec.tohid.qty
import ir.vil3ntec.tohid.ui.components.common.PillTone
import ir.vil3ntec.tohid.ui.components.common.StatusPill
import ir.vil3ntec.tohid.ui.components.staff.PinPad
import ir.vil3ntec.tohid.ui.theme.Radius
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonNull
import ir.vil3ntec.tohid.core.model.ShopDeviceDto
import ir.vil3ntec.tohid.core.model.ShopPermsDto
import ir.vil3ntec.tohid.data.repo.ShopRepository
import ir.vil3ntec.tohid.plain
import ir.vil3ntec.tohid.ui.theme.Shop
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/*
 *  «کارمندان»: مجوزِ هر کارمند (STF-01..03) و دستگاه‌های گم‌شده (STF-10).
 *
 *  ⛔ این صفحه چیزی را خودش تصمیم نمی‌گیرد. فهرستِ کلیدهای قابل‌تغییر و
 *  حالتِ هر کدام از سرور می‌آید (`GET /api/shop/permissions`) و سرور هم
 *  همان را اجرا می‌کند؛ کلیدی که سرور اجرا نمی‌کند این‌جا دیده نمی‌شود —
 *  دکمهٔ بی‌اثر نداریم. تخفیف، لغو و برگشت از ۱۴۰۵/۰۷/۲۷ در همگام‌سازی
 *  اجرا می‌شوند (سقف و «نیازمند تأیید» با برگهٔ امضاشده، STF-02/03).
 */

private fun permLabel(key: String): String = when (key) {
  "settings.write" -> "تنظیماتِ دکان"
  "audit.view" -> "سابقهٔ عملیات"
  "reports.money" -> "دیدنِ عددهای مالی (درآمد، سود)"
  "sales.discount" -> "تخفیف"
  "sales.cancel" -> "لغوِ فاکتور"
  "returns.create" -> "برگشت و بازپرداخت"
  "shift.report" -> ir.vil3ntec.tohid.core.i18n.StrShiftReview.permReports
  "safe.move" -> "گاوصندوق: شمارش و جابه‌جاییِ پول"
  else -> key
}

/** نشانِ قرصی‌شکلِ وضعیت: نقطه + متن، هر دو به رنگِ وضعیت */
@Composable
private fun StatePill(text: String, tint: Color) {
  Row(
    Modifier
      .clip(RoundedCornerShape(999.dp))
      .background(tint.copy(alpha = 0.12f))
      .padding(horizontal = 10.dp, vertical = 4.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(Modifier.size(6.dp).clip(CircleShape).background(tint))
    Spacer(Modifier.width(6.dp))
    Text(text, style = MaterialTheme.typography.labelSmall, color = tint)
  }
}

/** مجوزهای هر کارمند — فقط صاحب دکان تغییر می‌دهد */
@Composable
fun MemberPermsSection(shops: ShopRepository, canEdit: Boolean, snackbar: SnackbarHostState) {
  val scope = rememberCoroutineScope()
  var page by remember { mutableStateOf<ShopPermsDto?>(null) }
  var busy by remember { mutableStateOf(false) }

  suspend fun load() { shops.permissions().onSuccess { page = it.shop } }
  LaunchedEffect(Unit) { load() }

  val p = page ?: return
  val editable = p.catalog.filter { it.edit != "none" }
  //  پیک (DLV-01) لایهٔ مجوز ندارد: توکنش جز درِ پیک جایی را باز نمی‌کند
  val staff = p.members.filter { it.role != "owner" && it.role != "courier" }
  if (editable.isEmpty() || staff.isEmpty()) return

  SectionTitle("مجوزِ کارمندان")
  Panel {
    Text(
      "هر کلید فقط می‌تواند کمتر از نقش بدهد؛ «عددهای مالی» فقط نمایش است و داده روی دستگاه هست.",
      style = MaterialTheme.typography.labelSmall,
      color = Shop.colors.muted2,
    )
    staff.forEachIndexed { i, m ->
      if (i > 0) HorizontalDivider(Modifier.padding(vertical = 8.dp), color = Shop.colors.muted2.copy(alpha = 0.2f))
      Text(m.name.ifBlank { "کارمند" }, style = MaterialTheme.typography.titleSmall, color = Shop.colors.text)
      editable.forEach { entry ->
        //  کلیدِ restrict که نقش اصلاً ندارد، چیزی برای تغییر ندارد
        val roleDefault = entry.defaults[m.role] ?: "deny"
        //  «نیازمند تأیید» حتی برای نقشی که پیش‌فرضش «ممنوع» است معنا دارد
        if (entry.edit == "restrict" && roleDefault != "allow" && !entry.approvable) return@forEach
        if (entry.approvable) {
          ApprovableRow(m, entry, roleDefault, enabled = canEdit && !busy) { layer ->
            busy = true
            scope.launch {
              shops.setMemberPerms(m.id, layer)
                .onSuccess { load() }
                .onFailure { snackbar.showSnackbar(it.userMessage) }
              busy = false
            }
          }
          return@forEach
        }
        val on = m.effective[entry.key]?.mode == "allow"
        Row(
          Modifier.fillMaxWidth().padding(vertical = 2.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(permLabel(entry.key), style = MaterialTheme.typography.bodyMedium, color = Shop.colors.text, modifier = Modifier.weight(1f))
          Switch(
            checked = on,
            enabled = canEdit && !busy,
            onCheckedChange = { want ->
              busy = true
              scope.launch {
                shops.setMemberPerms(m.id, withMode(m, entry.key, if (want) "allow" else "deny"))
                  .onSuccess { load() }
                  .onFailure { snackbar.showSnackbar(it.userMessage) }
                busy = false
              }
            },
          )
        }
      }
    }
  }
  Spacer(Modifier.height(18.dp))
}

/** لایهٔ فعلیِ کارمند با یک کلیدِ عوض‌شده — بقیه (و سقف‌های همین کلید) دست نمی‌خورند */
private fun withMode(m: MemberPermsDto, key: String, mode: String): JsonObject = buildJsonObject {
  m.layer.forEach { (k, v) -> if (k != key) put(k, v) }
  put(key, buildJsonObject {
    put("mode", JsonPrimitive(mode))
    (m.layer[key] as? JsonObject)?.get("caps")?.let { put("caps", it) }
  })
}

/** همان، با سقف‌های تازه (`null` ⇐ برداشتنِ سقف) */
private fun withCaps(m: MemberPermsDto, key: String, caps: Map<String, Double?>): JsonObject = buildJsonObject {
  m.layer.forEach { (k, v) -> if (k != key) put(k, v) }
  put(key, buildJsonObject {
    (m.layer[key] as? JsonObject)?.get("mode")?.let { put("mode", it) }
    put("caps", buildJsonObject { caps.forEach { (n, v) -> put(n, if (v == null) JsonNull else JsonPrimitive(v)) } })
  })
}

/**
 *  کلیدِ تأییدپذیر (تخفیف، لغو، برگشت): سه حالت — «مجاز» (فقط اگر نقش دارد)،
 *  «با تأیید» و «ممنوع» — و سقفِ عددی برای «مجاز» (STF-02/03).
 */
@Composable
private fun ApprovableRow(
  m: MemberPermsDto,
  entry: CatalogEntryDto,
  roleDefault: String,
  enabled: Boolean,
  onSave: (JsonObject) -> Unit,
) {
  val state = m.effective[entry.key]
  val mode = state?.mode ?: roleDefault
  var editCaps by remember { mutableStateOf(false) }
  Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
    Text(permLabel(entry.key), style = MaterialTheme.typography.bodyMedium, color = Shop.colors.text)
    Spacer(Modifier.height(4.dp))
    val options = buildList {
      if (roleDefault == "allow") add("allow" to "مجاز")
      add("approve" to "با تأییدِ مدیر")
      add("deny" to "ممنوع")
    }
    SegmentedChoice(options = options, selected = mode, onSelect = { if (enabled && it != mode) onSave(withMode(m, entry.key, it)) })
    if (mode == "allow" && entry.caps.isNotEmpty()) {
      Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        val caps = state?.caps.orEmpty()
        Text(
          if (caps.isEmpty()) "بی سقف" else caps.entries.joinToString(" · ") { (n, v) ->
            if (n == "maxPct") "حداکثر ${qty(v)}٪" else "حداکثر ${qty(v / 100.0)} ؋"
          } + " — بیشتر از آن با تأییدِ مدیر",
          style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted, modifier = Modifier.weight(1f),
        )
        TextButton(onClick = { editCaps = true }, enabled = enabled) { Text("سقف") }
      }
    }
  }
  if (editCaps) {
    CapsDialog(entry, state?.caps.orEmpty(), onDismiss = { editCaps = false }) { caps ->
      editCaps = false
      onSave(withCaps(m, entry.key, caps))
    }
  }
}

/** سقفِ درصد و مبلغ — خالی یعنی بی سقف. مبلغ به افغانی گرفته و Minor فرستاده می‌شود. */
@Composable
private fun CapsDialog(
  entry: CatalogEntryDto,
  current: Map<String, Double>,
  onDismiss: () -> Unit,
  onSave: (Map<String, Double?>) -> Unit,
) {
  var pct by remember { mutableStateOf(current["maxPct"]?.let { qty(it) }.orEmpty()) }
  var amount by remember { mutableStateOf(current["maxAmount"]?.let { qty(it / 100.0) }.orEmpty()) }
  val pctVal = parseNumber(pct)
  val amountVal = parseNumber(amount)
  val pctBad = pct.isNotBlank() && (pctVal == null || pctVal < 0 || pctVal > 100)
  val amountBad = amount.isNotBlank() && (amountVal == null || amountVal < 0)
  Dialog(onDismissRequest = onDismiss) {
    Surface(color = Shop.colors.surfaceSolid, shape = RoundedCornerShape(Radius.lg), modifier = Modifier.fillMaxWidth()) {
      Column(Modifier.padding(18.dp)) {
        Text("سقفِ ${permLabel(entry.key)}", style = MaterialTheme.typography.titleMedium, color = Shop.colors.text)
        Spacer(Modifier.height(10.dp))
        if ("maxPct" in entry.caps) {
          OutlinedTextField(
            value = pct, onValueChange = { pct = numericInput(it) }, singleLine = true,
            label = { Text("حداکثر درصد (خالی = بی سقف)") }, isError = pctBad,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(),
          )
          Spacer(Modifier.height(8.dp))
        }
        if ("maxAmount" in entry.caps) {
          OutlinedTextField(
            value = amount, onValueChange = { amount = numericInput(it) }, singleLine = true,
            label = { Text("حداکثر مبلغ به افغانی (خالی = بی سقف)") }, isError = amountBad,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(),
          )
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
          TextButton(onClick = onDismiss) { Text("انصراف") }
          TextButton(
            enabled = !pctBad && !amountBad,
            onClick = {
              val out = LinkedHashMap<String, Double?>()
              if ("maxPct" in entry.caps) out["maxPct"] = pctVal?.takeIf { pct.isNotBlank() }
              if ("maxAmount" in entry.caps) out["maxAmount"] = amountVal?.takeIf { amount.isNotBlank() }?.let { Math.round(it * 100).toDouble() }
              onSave(out)
            },
          ) { Text("ذخیره") }
        }
      }
    }
  }
}

/**
 *  PIN و کلیدِ تأییدِ خودم روی همین دستگاه — STF-02 و STF-04 (بخشِ ۱۰.۱ و ۱۰.۲).
 *
 *  صاحب دکان و مدیر یک PINِ شش‌رقمی می‌گذارند؛ همان لحظه جفت‌کلیدِ ES256 ساخته
 *  می‌شود، کلیدِ خصوصی با همان PIN بسته روی دستگاه می‌ماند و کلیدِ عمومی یک بار
 *  آنلاین در سرور ثبت می‌شود. PINِ قفل‌شده (۵ خطا) فقط با PINِ تازه از همین
 *  صفحه (که ورودِ آنلاین می‌خواهد) باز می‌شود.
 */
@Composable
fun ApproverSetupSection(shops: ShopRepository, snackbar: SnackbarHostState) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val store = remember { StaffPins(context) }
  val sync = remember { runCatching { ir.vil3ntec.tohid.sync.SyncStore(context) }.getOrNull() } ?: return
  val userId = sync.accountId
  val role = remember { ShopRole.current(context) }
  if (userId.isBlank() || role !in ApproverVault.APPROVER_ROLES) return
  var rec by remember { mutableStateOf(store.get(userId)) }
  var setting by remember { mutableStateOf(false) }
  var busy by remember { mutableStateOf(false) }
  val label = (sync.accountName.ifBlank { "دستگاه" } + " · " + if (ir.vil3ntec.tohid.ui.Platform.DESKTOP) "کامپیوتر" else "گوشی").take(60)

  suspend fun register(r: ApproverVault.Record) {
    shops.registerApproverKey(r.spki, label, sync.deviceUid)
      .onSuccess { id ->
        val next = r.copy(registered = id == r.keyId)
        store.put(next)
        rec = next
        snackbar.showSnackbar(if (next.registered) "کلیدِ تأیید ثبت شد" else "پاسخِ سرور با این کلید نخواند")
      }
      .onFailure { snackbar.showSnackbar(it.userMessage) }
  }

  SectionTitle("PIN و کلیدِ تأیید")
  Panel {
    Text(
      "با این PIN روی همین دستگاه تخفیف، لغو یا برگشتِ بیش از سقفِ کارمند را تأیید می‌کنید. " +
        "PIN و کلیدِ خصوصی از این دستگاه بیرون نمی‌روند.",
      style = MaterialTheme.typography.labelSmall,
      color = Shop.colors.muted2,
    )
    Spacer(Modifier.height(8.dp))
    val r = rec
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      when {
        r == null -> StatusPill("PIN ندارد", PillTone.NEUTRAL)
        r.locked -> StatusPill("قفل پس از ${plain(PinGuard.MAX_FAILURES)} خطا", PillTone.DANGER)
        r.registered -> StatusPill("آمادهٔ تأیید", PillTone.SUCCESS)
        else -> StatusPill("کلید هنوز ثبت نشده", PillTone.WARNING)
      }
      Spacer(Modifier.weight(1f))
      if (r != null && !r.registered && !r.locked) {
        TohidSecondaryButton(
          text = "ثبتِ کلید",
          enabled = !busy,
          onClick = { busy = true; scope.launch { register(r); busy = false } },
        )
      }
      TohidSecondaryButton(text = if (r == null) "گذاشتنِ PIN" else "PINِ تازه", enabled = !busy, onClick = { setting = true })
    }
  }
  Spacer(Modifier.height(18.dp))

  if (setting) {
    PinSetupDialog(onDismiss = { setting = false }) { pin ->
      setting = false
      busy = true
      scope.launch {
        val old = rec
        val next = withContext(Dispatchers.Default) { ApproverVault.create(userId, sync.accountName, role, pin) }
        store.put(next)
        rec = next
        //  کلیدِ قبلیِ همین دستگاه دیگر باز نمی‌شود؛ در سرور هم باطل شود
        if (old != null && old.registered && old.keyId != next.keyId) shops.revokeApproverKey(old.keyId)
        register(next)
        busy = false
      }
    }
  }
}

/** PINِ تازه: دو بار، شش رقم، نه ساده (۱۱۱۱۱۱ / ۱۲۳۴۵۶) */
@Composable
private fun PinSetupDialog(onDismiss: () -> Unit, onDone: (String) -> Unit) {
  var first by remember { mutableStateOf<String?>(null) }
  var error by remember { mutableStateOf<String?>(null) }
  Dialog(onDismissRequest = onDismiss) {
    Surface(color = Shop.colors.surfaceSolid, shape = RoundedCornerShape(Radius.lg), modifier = Modifier.fillMaxWidth()) {
      Column(Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        PinPad(
          title = if (first == null) "PINِ تازه" else "دوباره همان PIN",
          hint = "شش رقم؛ نه همه یکی و نه پشتِ سرِ هم",
          error = error,
          onComplete = { pin ->
            val f = first
            when {
              f == null && PinGuard.newPinProblem(pin) != null -> { error = "این PIN ساده است؛ شش رقمِ دیگری بزنید" }
              f == null -> { first = pin; error = null }
              f != pin -> { first = null; error = "دو PIN یکی نبودند؛ از نو" }
              else -> { onDone(pin) }
            }
          },
        )
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onDismiss) { Text("انصراف") }
      }
    }
  }
}

/** دستگاه‌های اعضا — صاحب دکان دستگاهِ گم‌شده را از کار می‌اندازد (S44) */
@Composable
fun DevicesSection(shops: ShopRepository, canEdit: Boolean, snackbar: SnackbarHostState) {
  val scope = rememberCoroutineScope()
  var devices by remember { mutableStateOf<List<ShopDeviceDto>?>(null) }
  var confirm by remember { mutableStateOf<ShopDeviceDto?>(null) }

  suspend fun load() { shops.devices().onSuccess { devices = it } }
  LaunchedEffect(Unit) { load() }

  val list = devices ?: return
  SectionTitle("دستگاه‌ها")
  Panel {
    if (list.isEmpty()) EmptyNote("دستگاهی ثبت نشده است.")
    list.forEachIndexed { i, d ->
      if (i > 0) HorizontalDivider(Modifier.padding(vertical = 8.dp), color = Shop.colors.muted2.copy(alpha = 0.2f))
      val disabled = d.disabledAt != null
      Column(Modifier.fillMaxWidth()) {
        Text(
          "${d.member.name.ifBlank { "کارمند" }} · ${d.name.ifBlank { d.platform.ifBlank { "دستگاه" } }}",
          style = MaterialTheme.typography.bodyMedium,
          color = Shop.colors.text,
        )
        Spacer(Modifier.height(4.dp))
        //  وضعیت همیشه «متن + رنگ + نقطه» (VISUAL-DIRECTION ۱.۳)
        when {
          d.current -> StatePill("همین دستگاه", Shop.colors.primary)
          disabled -> StatePill(
            "از کار افتاده" + if (d.blockedAttempts > 0) " · ${plain(d.blockedAttempts)} تلاشِ ردشده" else "",
            Shop.colors.danger,
          )
          d.status != "active" -> StatePill("خارج‌شده", Shop.colors.muted)
          else -> StatePill("فعال", Shop.colors.success)
        }
        if (canEdit && !d.current && (disabled || d.status == "active")) {
          Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
            TohidSecondaryButton(
              text = if (disabled) "برگرداندن" else "از کار انداختن",
              onClick = {
                if (disabled) {
                  scope.launch {
                    shops.enableDevice(d.id)
                      .onSuccess { snackbar.showSnackbar("دستگاه برگشت؛ کارمند باید دوباره وارد شود"); load() }
                      .onFailure { snackbar.showSnackbar(it.userMessage) }
                  }
                } else {
                  confirm = d
                }
              },
            )
          }
        }
      }
    }
  }
  Spacer(Modifier.height(18.dp))

  confirm?.let { d ->
    TohidConfirmDialog(
      title = "از کار انداختنِ این دستگاه",
      message = "همهٔ نشست‌های این دستگاه بسته می‌شود و از این پس هیچ چیزی از آن پذیرفته نمی‌شود. " +
        "فروش‌هایی که هنوز بالا نرفته‌اند روی خودِ دستگاه و در پشتیبانِ آن می‌مانند.",
      confirmText = "از کار بینداز",
      danger = true,
      onDismiss = { confirm = null },
      onConfirm = {
        confirm = null
        scope.launch {
          shops.disableDevice(d.id)
            .onSuccess { snackbar.showSnackbar("دستگاه از کار افتاد"); load() }
            .onFailure { snackbar.showSnackbar(it.userMessage) }
        }
      },
    )
  }
}
