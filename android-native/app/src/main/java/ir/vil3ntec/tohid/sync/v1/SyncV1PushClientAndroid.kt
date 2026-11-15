package ir.vil3ntec.tohid.sync.v1

import android.content.Context
import ir.vil3ntec.tohid.core.sync.PushSince
import ir.vil3ntec.tohid.data.repo.Backend

/**
 *  لایهٔ شبکهٔ `SyncV1Engine` برای push و pull: `since` ِ push (#481، `PushSince`) و «فشرده بفرست» ِ سرور (SYN-04،
 *  `SyncV1Client.PushGzip`) — ماندگار روی همان حساب (`SyncStateV1.gzipPush`)، ردِ فشرده تا پایانِ همین اجرا.
 *  [state] حالِ حسابِ همین حالا (با عوض شدنِ حساب عوض می‌شود).
 */
internal class SyncV1PushClientAndroid(private val app: Context, private val state: () -> SyncStateV1) {
  private val gzip = SyncV1Client.PushGzip({ state().gzipPush }, { state().gzipPush = it })

  fun client(): SyncV1Client =
    SyncV1Client(Backend.api(app), { PushSince.since(state().pushSince, state().cursor) }, gzip)
}
