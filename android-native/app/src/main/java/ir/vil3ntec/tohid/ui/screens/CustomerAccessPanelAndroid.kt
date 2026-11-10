package ir.vil3ntec.tohid.ui.screens

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.crm.CustomerAccess
import ir.vil3ntec.tohid.core.i18n.StrCustomerAccess
import ir.vil3ntec.tohid.core.perm.PermCore
import ir.vil3ntec.tohid.data.CustomerAccessLog
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopRole
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.data.TillUser
import ir.vil3ntec.tohid.data.repo.Backend
import ir.vil3ntec.tohid.ui.theme.Shop
import ir.vil3ntec.tohid.ui.v3.account.AccountFormat
import kotlinx.coroutines.launch

/*
 *  CRM-15 — مرزِ سکوی «سابقهٔ دسترسی»: ثبتِ دیدنِ پرونده روی دفترِ همین دستگاه ([CustomerAccessNote]) و بخشِ
 *  «چه کسی این پرونده را دید» ([CustomerAccessPanel]) در پروندهٔ مشتریِ گوشیِ نسخهٔ ۳ (`DebtorFileExtras`) و
 *  کامپیوتر (`DeskCustomers`). قاعده‌ها در `core/crm/CustomerAccess` و `data/CustomerAccessLog`.
 */

/** ثبتِ یک دیدن در `auditLog` ِ دفتر — هرگز خطا نمی‌دهد و صفحه را نگه نمی‌دارد */
object CustomerAccessNote {
  /**
   *  `true` نوشته شد؛ `null` چیزی برای نوشتن نبود (تکرارِ ۱۰ دقیقه)؛ `false` ننوشت: حالتِ آموزشی، بازسازیِ دفتر،
   *  خطای دیسک، یا نقشِ بی `auditLog` (حسابدار — سرور ردیفِ دفترش را می‌انداخت) که سرور در دسترس نبود.
   *  نقشِ بی `auditLog` ⇐ همان دیدن به درِ `app` ِ سرور ([CustomerAccess.noteBody]؛ بیننده و دستگاه از توکن، یکی کردنِ
   *  ۱۰ دقیقه با سرور) — بی‌اینترنت ثبت نمی‌شود.
   */
  suspend fun note(context: Context, store: ShopStore, customerId: String, kind: CustomerAccess.Kind): Boolean? = runCatching {
    if (store.training) return@runCatching false
    val role = ShopRole.current(context)
    if (!PermCore.canWrite(role, "auditLog")) {
      val body = CustomerAccess.noteBody(customerId, kind) ?: return@runCatching false
      if (!Backend.isReady(context)) return@runCatching false
      Backend.api(context).post(CustomerAccess.PATH, body)
      return@runCatching true
    }
    if (ir.vil3ntec.tohid.sync.v1.SyncV1Engine.of(context).rebuilding) return@runCatching false
    val who = CustomerAccessLog.Viewer(
      id = TillUser.meId(context), name = TillUser.me(context), role = role,
      device = runCatching { ir.vil3ntec.tohid.sync.SyncStore(context).deviceUid }.getOrDefault(""),
    )
    store.update { d -> CustomerAccessLog.record(d, customerId, kind, who, ir.vil3ntec.tohid.todayIso(), System.currentTimeMillis()) }
  }.getOrDefault(false)
}

/** CRM-15: خروجی/چاپِ یک برگهٔ گزارش (کلیدِ `ReportCsv`) — `AnalyticsRoutes` برگهٔ مشتریان را «خروجیِ همهٔ مشتریان» ثبت می‌کند */
val LocalReportExport = androidx.compose.runtime.staticCompositionLocalOf<(String) -> Unit> { {} }

/** «این دیدن را ثبت کن» برای صفحه‌ها — بی انتظار */
@Composable
fun rememberCustomerAccessNote(store: ShopStore): (String, CustomerAccess.Kind) -> Unit {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  return remember(store, context, scope) { { id, kind -> scope.launch { CustomerAccessNote.note(context, store, id, kind) } } }
}

/**
 *  «چه کسی این پرونده را دید» — فقط صاحب و `audit.view` (همان «سابقهٔ عملیات»)؛ ردیف‌های دفترِ همین دستگاه (همهٔ
 *  دستگاه‌های همگام‌شده) و، اگر وارد است، درهای سرور (`GET /shop/customer-access`). خطای شبکه ⇒ فقط دفتر.
 */
@Composable
fun CustomerAccessPanel(d: ShopData, debtorId: String, modifier: Modifier = Modifier) {
  val context = LocalContext.current
  val version by ShopRole.policyVersion.collectAsState()
  val allowed = remember(version) { ShopRole.allows(context, "audit.view") }
  if (!allowed) return
  val local = remember(d.auditLog) { CustomerAccessLog.views(d) }
  var server by remember(debtorId) { mutableStateOf<List<CustomerAccess.View>>(emptyList()) }
  LaunchedEffect(debtorId) {
    val path = CustomerAccess.path(debtorId) ?: return@LaunchedEffect
    if (!Backend.isReady(context)) return@LaunchedEffect
    runCatching { Backend.api(context).get(path) }.getOrNull()?.let { server = CustomerAccess.parse(it, debtorId) }
  }
  val rows = remember(local, server, debtorId) { CustomerAccess.rows(local, server, debtorId) }
  Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
    Text(StrCustomerAccess.title, style = MaterialTheme.typography.titleSmall, color = Shop.colors.text, modifier = Modifier.semantics { heading() })
    Text(StrCustomerAccess.hint, style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted)
    if (rows.isEmpty()) Text(StrCustomerAccess.empty, style = MaterialTheme.typography.bodySmall, color = Shop.colors.muted)
    for (r in rows) Text(accessLine(r), style = MaterialTheme.typography.bodySmall, color = Shop.colors.text)
  }
}

/** «بیننده · نقش · چه · کی · چند بار · از سرور» */
internal fun accessLine(r: CustomerAccess.View): String = listOfNotNull(
  r.viewerName.ifBlank { StrCustomerAccess.unknownViewer },
  r.role.takeIf { it.isNotBlank() }?.let { AccountFormat.teamRole(it) },
  when (r.kind) {
    CustomerAccess.Kind.DETAIL -> StrCustomerAccess.kindDetail
    CustomerAccess.Kind.PHONE -> StrCustomerAccess.kindPhone
    CustomerAccess.Kind.EXPORT -> if (r.customerId == CustomerAccess.ALL) StrCustomerAccess.kindExportAll else StrCustomerAccess.kindExport
  },
  "${ir.vil3ntec.tohid.displayMillis(r.at)} ${clock(r.at)}".trim(),
  r.count.takeIf { it > 1 }?.let { StrCustomerAccess.times(it.toString()) },
  StrCustomerAccess.fromServer.takeIf { r.server },
).joinToString(" · ")

private fun clock(at: Long): String {
  if (at <= 0) return ""
  val c = java.util.Calendar.getInstance().apply { timeInMillis = at }
  return "${c.get(java.util.Calendar.HOUR_OF_DAY).toString().padStart(2, '0')}:${c.get(java.util.Calendar.MINUTE).toString().padStart(2, '0')}"
}
