package ir.vil3ntec.tohid.sync.v1

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 *  حالِ همگام‌سازی برای صفحه‌ای که با برنامه باز می‌شود (خانهٔ نسخهٔ ۳، نوارِ پایینِ کامپیوتر) — PERF-LEDGER.
 *
 *  ⛔ موتور (`SyncV1Engine.of`) روی رشتهٔ صفحه ساخته نمی‌شود: ساختنش prefs ِ `tohid-sync-v1` و صف را می‌خواند و
 *  پیش از پرچمِ `shop.sync_shadow_file` همان XML کلِ سایه را داشت (نخستین `getString` تا تجزیهٔ کلِ آن صبر می‌کرد).
 *  موتورِ ساخته‌شده همان دم؛ وگرنه روی `Dispatchers.IO` و تا آن وقت `null` (خانه هشداری نمی‌گذارد، نوار «در راه»).
 */
@Composable
fun rememberSyncV1Status(context: Context): SyncStatus? {
  val app = context.applicationContext
  val engine by produceState(SyncV1Engine.peek(), app) {
    if (value == null) value = withContext(Dispatchers.IO) { SyncV1Engine.of(app) }
  }
  return engine?.status?.collectAsState()?.value
}

/** تا ساخته شدنِ موتور (`rememberSyncV1Status` ⇒ `null`): خاکستری، بی شمار و بی خطا */
val SYNC_STARTING = SyncStatus(
  dot = SyncDot.GREY, queued = 0, dropped = 0, cursor = 0, lastOkAt = 0, lastError = "", holding = false,
  upgradeAvailable = false, schemaVersion = LedgerDiff.SCHEMA_VERSION, serverSchema = 0, live = false,
)
