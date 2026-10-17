package ir.vil3ntec.tohid.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.model.MemberPermsDto
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
 *  همان را اجرا می‌کند؛ پس کلیدی که سرور هنوز اجرا نمی‌کند (تخفیف، لغو)
 *  این‌جا اصلاً دیده نمی‌شود — دکمهٔ بی‌اثر نداریم.
 */

private fun permLabel(key: String): String = when (key) {
  "settings.write" -> "تنظیماتِ دکان"
  "audit.view" -> "سابقهٔ عملیات"
  "reports.money" -> "دیدنِ عددهای مالی (درآمد، سود)"
  else -> key
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
  val staff = p.members.filter { it.role != "owner" }
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
        if (entry.edit == "restrict" && roleDefault != "allow") return@forEach
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

/** لایهٔ فعلیِ کارمند با یک کلیدِ عوض‌شده — بقیه دست نمی‌خورند */
private fun withMode(m: MemberPermsDto, key: String, mode: String): JsonObject = buildJsonObject {
  m.layer.forEach { (k, v) -> if (k != key) put(k, v) }
  put(key, buildJsonObject { put("mode", JsonPrimitive(mode)) })
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
        Text(
          when {
            d.current -> "همین دستگاه"
            disabled -> "از کار افتاده" + if (d.blockedAttempts > 0) " — ${plain(d.blockedAttempts)} تلاشِ ردشده" else ""
            d.status != "active" -> "خارج‌شده"
            else -> "فعال"
          },
          style = MaterialTheme.typography.labelSmall,
          color = if (disabled) Shop.colors.danger else Shop.colors.muted,
        )
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
