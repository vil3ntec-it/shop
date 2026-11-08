package ir.vil3ntec.tohid.sync.v1

import android.content.Context
import ir.vil3ntec.tohid.core.sync.SyncReplica
import ir.vil3ntec.tohid.sync.SyncStore

/**
 *  `device_id` ِ همین حساب روی همین دستگاه برای سرور (push، pull، سریِ سند، سوکتِ زنده) — شرح سرِ
 *  `core/sync/SyncReplica.kt`. نخستین حساب (یا هر حسابی که پیش‌تر از این دستگاه push کرده) همان شناسهٔ خام؛ حسابِ
 *  دیگری که تازه روی همین گوشی شروع می‌کند (شاگرد با کدِ شاگرد پس از خروجِ صاحب) شناسهٔ خودش — یک بار انتخاب و در
 *  `SyncStateV1.replica` ِ همان حساب نگه داشته (`SyncV1Keys.move` با حالِ حساب جابه‌جایش می‌کند). جدا از
 *  `SyncV1Engine.kt` تا آن پرونده از سقفِ اندازه نگذرد.
 */
internal object V1Replica {

  fun id(lock: Any, app: Context, store: SyncStore, state: SyncStateV1): String = synchronized(lock) {
    val kept = state.replica
    if (kept.isNotBlank()) return@synchronized kept
    val device = store.deviceUid
    val account = SyncStateV1.currentAccount(app).trim()
    if (account.isEmpty()) return@synchronized device
    //  «تازه» = این حساب از این دستگاه هرگز push نکرده (Snapshot و صفِ نرفته بی‌خطرند: هیچ op ِ خودش با شناسهٔ
    //  خام پس از cursor روی سرور نیست که با شناسهٔ تازه «دیگری» دیده و دوباره نشانده شود)
    val id = SyncReplica.choose(device, account, store.replicaOwner, fresh = state.lastPushAt == 0L)
    if (id == device && store.replicaOwner.isBlank()) store.replicaOwner = account
    state.replica = id
    id
  }
}
