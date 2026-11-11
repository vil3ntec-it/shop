package ir.vil3ntec.tohid.ui.courier

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import ir.vil3ntec.tohid.core.courier.LiveTrack
import ir.vil3ntec.tohid.core.courier.LocationConsent
import ir.vil3ntec.tohid.core.courier.MissionCard
import ir.vil3ntec.tohid.core.courier.QueueState
import ir.vil3ntec.tohid.core.net.ApiResult
import ir.vil3ntec.tohid.data.CourierStore
import ir.vil3ntec.tohid.data.DeviceLocation
import ir.vil3ntec.tohid.data.repo.Backend
import kotlinx.coroutines.delay

/*
 *  DLV-16 — «موقعیتِ پیک فقط در زمانِ مأموریت» (§۳.۱۱). بی وابستگیِ سکو: سرویسِ پیش‌زمینهٔ اندروید از نوعِ `location`
 *  (`CourierTrackAndroid.kt` ‏`CourierTrackService`) فقط [CourierTrack.loop] را می‌چرخاند؛ کامپیوتر GPS ندارد و همتایش
 *  (`desktop/…/ui/courier/CourierTrackDesk.kt`) هیچ نمی‌کند؛ آیفون سرویسِ پیش‌رو ندارد (shim ِ `startForegroundService`
 *  هیچ است) — موقعیتِ پس‌زمینهٔ آیفون حسابِ اپل و `UIBackgroundModes` می‌خواهد (`docs/IOS-PLAN-fa.md`).
 *
 *  قاعده‌ها همه در `core/…/courier/LiveTrack.kt`: فقط کارتی که سرور `tracking.location` داد و روی گوشی هنوز «در راه»
 *  است؛ هر نیم دقیقه یک نقطهٔ تازه (کهنه‌تر از یک دقیقه نه)؛ نقطهٔ نرسیده دور ریخته می‌شود، هیچ صفی نیست، و نقطه روی
 *  گوشی نوشته نمی‌شود (`DeviceLocation.current(keep = false)`). سرور داور است (`delivery-live.js` ‏`putLocation`).
 *
 *  S94: با پرچمِ رضایتِ دکان (`LocationConsent.required`) فقط پس از «بله» ِ خودِ پیک (`LocationConsent.mayShare`، کلیدِ
 *  «اشتراکِ موقعیت» در برگهٔ «حساب»)؛ «نه» یا ۴۰۳ ِ `location_consent_required` ِ سرور ⇒ همان دور بسته و سرویس خاموش.
 */
internal object CourierTrack {

  /** مأموریت‌هایی که اکنون موقعیت می‌خواهند — از کارت‌ها و صفِ نگه‌داشتهٔ گوشی (سرویس پس از بسته شدنِ صفحه هم همین را می‌خواند) */
  fun targets(context: Context): List<LiveTrack.Target> {
    val store = CourierStore(context)
    return LiveTrack.targets(store.cards("active"), store.queue())
  }

  /** سرویس روشن بماند؟ اجازهٔ موقعیت و رضایتِ پیک (S94) هست و دست‌کم یک مأموریت «در راه» موقعیت می‌خواهد */
  fun wanted(context: Context): Boolean =
    DeviceLocation.granted(context) && CourierStore(context).locationConsent.mayShare && targets(context).isNotEmpty()

  /** پاسخِ درِ سرور ⇒ نتیجهٔ یک فرستادن (`LiveTrack.Sent`)؛ کدِ خطا همان کدِ سرور/شبکه */
  fun sentOf(r: ApiResult<Boolean>): LiveTrack.Sent = when (r) {
    is ApiResult.Success -> LiveTrack.Sent(ok = true)
    is ApiResult.Failure -> LiveTrack.Sent(ok = false, code = r.error.code)
  }

  /** نقطهٔ دستگاه ⇒ نقطهٔ سرور */
  fun fixOf(f: ir.vil3ntec.tohid.data.LocationFix): LiveTrack.Fix = LiveTrack.Fix(f.lat, f.lng, LiveTrack.accuracyOf(f.accuracy), f.at)

  /**
   *  یک دور: کارت‌ها و صف ⇐ نوبت‌رسیده‌ها ⇐ یک نقطه ⇐ فرستادن. [fix] و [send] بیرون از این تابع‌اند تا بی گوشی و
   *  بی شبکه آزموده شود (`CourierLiveTrackTest`). ⇒ شمارِ هدف‌های زندهٔ پس از این دور.
   */
  suspend fun tick(
    cards: List<MissionCard>,
    queue: QueueState,
    pacer: LiveTrack.Pacer,
    now: Long,
    fix: suspend () -> LiveTrack.Fix?,
    send: suspend (LiveTrack.Target, LiveTrack.Fix) -> LiveTrack.Sent,
  ): Int = LiveTrack.tick(cards, queue, pacer, now, { fix() }, { t, f -> send(t, f) })

  /**
   *  حلقهٔ سرویس: تا هدفی هست هر [LiveTrack.PERIOD_MS] یک دور؛ هیچ هدفی نماند (رسیدم، پایان، پس‌گرفته، «دیگر نه» ِ
   *  سرور، بی اجازه) ⇒ برمی‌گردد و سرویس خودش را می‌بندد.
   */
  suspend fun loop(context: Context, pacer: LiveTrack.Pacer = LiveTrack.Pacer()) {
    val repo = Backend.courier(context)
    while (true) {
      if (!DeviceLocation.granted(context)) return
      val store = CourierStore(context)
      if (!store.locationConsent.mayShare) return   //  S94: «نه» ِ پیک ⇒ همین دور هیچ نقطه
      val left = tick(
        store.cards("active"), store.queue(), pacer, System.currentTimeMillis(),
        fix = { DeviceLocation.current(context, force = true, maxAgeMs = LiveTrack.MAX_FIX_AGE_MS, keep = false)?.let(::fixOf) },
        send = { t, f ->
          val r = repo.location(t.assignmentId, f)
          //  S94: سرور رضایت می‌خواهد و ندارد ⇒ گوشی هم دیگر نمی‌فرستد تا «بله» ِ پیک
          if (r is ApiResult.Failure && r.error.code == LocationConsent.REQUIRED_CODE) store.locationConsent = store.locationConsent.refusedByServer()
          sentOf(r)
        },
      )
      if (left == 0 || !store.locationConsent.mayShare) return
      delay(LiveTrack.PERIOD_MS)
    }
  }
}

/**
 *  صفحهٔ پیک سرویس را با هر تغییرِ کارت‌ها یا صف روشن/خاموش می‌کند (`CourierHome`). اجازهٔ موقعیت فقط وقتی پرسیده
 *  می‌شود که مأموریتی واقعاً «در راه» موقعیت می‌خواهد، و در هر بارِ باز بودنِ صفحه یک بار؛ «نه» ⇒ بی سرویس
 *  (کارِ پیک هیچ‌جا بسته نمی‌شود — مشتری فقط نقطه را نمی‌بیند).
 */
@Composable
internal fun CourierLiveTrack(active: List<MissionCard>, queue: QueueState) {
  val context = LocalContext.current
  val targets = remember(active, queue) { LiveTrack.targets(active, queue) }
  var asked by rememberSaveable { mutableStateOf(false) }
  val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
    CourierTrackPlatform.sync(context, granted.values.any { it } && CourierTrack.wanted(context))
  }
  //  S94: «نه» ِ پیک (یا هنوز «بله» نگفته با پرچمِ رضایت) ⇒ سرویس خاموش و اجازهٔ دستگاه هم پرسیده نمی‌شود؛
  //  `CourierHome` با هر تغییرِ رضایت همین را از نو می‌سازد (`key(consent.mayShare)`)
  LaunchedEffect(targets) {
    when {
      targets.isEmpty() || !CourierStore(context).locationConsent.mayShare -> CourierTrackPlatform.sync(context, false)
      DeviceLocation.granted(context) -> CourierTrackPlatform.sync(context, CourierTrack.wanted(context))
      !asked -> { asked = true; launcher.launch(DeviceLocation.PERMISSIONS) }
    }
  }
}
