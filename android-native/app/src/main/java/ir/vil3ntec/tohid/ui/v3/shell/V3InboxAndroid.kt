package ir.vil3ntec.tohid.ui.v3.shell

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SyncProblem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalContext
import ir.vil3ntec.tohid.core.i18n.StrV3Inbox
import ir.vil3ntec.tohid.data.V3InboxOutboxAndroid
import ir.vil3ntec.tohid.ui.AppNav
import ir.vil3ntec.tohid.ui.theme.V3Tone
import ir.vil3ntec.tohid.ui.v3.V3EmptyState
import ir.vil3ntec.tohid.ui.v3.V3Scaffold
import ir.vil3ntec.tohid.ui.v3.nav.LocalV3Nav
import ir.vil3ntec.tohid.ui.v3.nav.RouteScope
import kotlinx.coroutines.delay

/**
 *  SYN-07 / S28 — مرزِ سکوی صندوقِ تعارضِ v3 در پوسته (گوشیِ نسخهٔ ۳ و کامپیوتر): یک [V3InboxModel] برای هر حساب، نوارِ
 *  «N تعارضِ همگام‌سازی…» ([V3InboxNotice]) و صفحهٔ `sync-conflicts` ([V3InboxRoute]).
 *
 *  ⛔ پیش‌فرض خاموش در سه لایه: (۱) دستگاهِ Sync v1 (پیش‌فرض) یا نقشِ غیرِ صاحب/مدیر ⇒ هیچ درخواستی
 *     (`V3InboxOutboxAndroid.eligible`)؛ (۲) سرورِ بی `SYNC_V3_ENABLED` (۴۰۴) یا (۳) پرچمِ دکانِ `shop.sync_v3_inbox`
 *     خاموش (پاسخ بی `inbox`) ⇒ هیچ نوار و هیچ ردیفی، و تا ده دقیقهٔ بعد پرسیده نمی‌شود.
 */
object V3InboxLive {
  const val LIST_MS = 60_000L

  private val model = mutableStateOf<V3InboxModel?>(null)
  @Volatile private var key = ""

  /** مدلِ همین حساب برای صفحه‌ها (بیرون از ترکیب، با [ensure] ساخته می‌شود) */
  val current: State<V3InboxModel?> get() = model

  /** مدلِ همین حساب؛ دستگاهِ نامجاز ⇒ null (و مدلِ پیشین کنار) */
  fun ensure(context: Context): V3InboxModel? {
    if (!V3InboxOutboxAndroid.eligible(context)) { model.value = null; key = ""; return null }
    val k = V3InboxOutboxAndroid.key(context)
    model.value?.takeIf { k == key }?.let { return it }
    val client = V3InboxOutboxAndroid.client(context) ?: return null
    return V3InboxModel(client, V3InboxOutboxAndroid.me(context), V3InboxOutboxAndroid.lock, { System.currentTimeMillis() })
      .also { model.value = it; key = k }
  }
}

/** نوارِ «N تعارضِ همگام‌سازی منتظرِ تصمیمِ شماست» — فقط با صندوقِ روشن و تعارضِ بازِ بی‌تصمیم؛ زدن ⇒ صفحه */
@Composable
fun V3InboxNotice() {
  val context = LocalContext.current
  val nav = LocalV3Nav.current
  LaunchedEffect(Unit) {
    while (true) {
      runCatching { V3InboxLive.ensure(context)?.refresh() }
      delay(V3InboxLive.LIST_MS)
    }
  }
  val m = V3InboxLive.current.value ?: return
  val count = m.waiting()
  if (!m.on.value || count <= 0) return
  if (nav?.current?.let(ir.vil3ntec.tohid.core.nav.RoutePaths::base) == ShellPaths.SYNC_CONFLICTS) return
  val go = { if (nav != null) nav.open(ShellPaths.SYNC_CONFLICTS) else AppNav.go(ShellPaths.SYNC_CONFLICTS) }
  DraftNoticeBar(
    tone = V3Tone.WARNING,
    text = StrV3Inbox.pending(count),
    icon = Icons.Outlined.SyncProblem,
    action = StrV3Inbox.open,
    onAction = go,
    onClick = go,
  )
}

/** `sync-conflicts` — دستگاهِ نامجاز (v1، نقشِ دیگر) ⇒ همان صفحه با «تعارضی نمانده است» */
@Composable
fun RouteScope.V3InboxRoute() {
  val context = LocalContext.current
  LaunchedEffect(Unit) { runCatching { V3InboxLive.ensure(context) } }
  val m = V3InboxLive.current.value
  if (m == null) {
    V3Scaffold(title = StrV3Inbox.title, onBack = backOrNull) { _ -> V3EmptyState(Icons.Outlined.SyncProblem, StrV3Inbox.empty) }
    return
  }
  V3InboxPage(m, data, backOrNull)
}
