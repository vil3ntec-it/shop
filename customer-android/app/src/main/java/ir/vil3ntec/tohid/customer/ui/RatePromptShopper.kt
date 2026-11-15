package ir.vil3ntec.tohid.customer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.rate.RateChoice
import ir.vil3ntec.tohid.core.rate.RateMoment
import ir.vil3ntec.tohid.core.rate.RatePrompt
import ir.vil3ntec.tohid.core.rate.RateState
import ir.vil3ntec.tohid.core.rate.RateTarget
import ir.vil3ntec.tohid.core.shopper.RateText
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 *  RATE-01 — مرزِ سکوی «ستاره بدهید» ِ برنامهٔ خرید ([Platform.rate]): فروشگاهِ همین ساخت و بازکنِ آن. اندروید فقط در ساختِ
 *  `-Pstore=play` (`RateAndroid.hook`)؛ آیفون: `RateTarget.APP_STORE` با `SKStoreReviewController` — میزبانِ آیفون هنوز
 *  نمی‌نشاند (`docs/GOOGLE-PLAY-fa.md` §۹). بی آن (`null`) برگه هرگز ساخته نمی‌شود.
 */
class RateHook(val target: RateTarget, val open: () -> Unit)

/** همین بارِ باز بودنِ برنامه: آغاز، تازه‌ترین سفارشِ آن لحظه و آخرین خطای دیده‌شده — فقط در حافظه */
private object ShopperRateSession {
  var startedAt = 0L
  var newestAtStart: String? = null
  var lastErrorAt = 0L
}

/** حالِ برگه در نگه‌داریِ همین دستگاه (`shopper` ِ اندروید، NSUserDefaults ِ آیفون) — نه سرور، نه حساب */
internal const val RATE_KEY = "shopper.rate.v1"

/**
 *  میزبانِ برگه (`CustomerApp`). قاعده‌ها همه در `core/rate/RatePrompt.kt`: فقط روی «خانه» (هرگز سبد، پرداخت یا پیگیری)،
 *  نه تا نیم ساعت پس از خطای دیده‌شده، نه در آغازِ همین بار (سه دقیقه و یک سفارشِ تازه)، دست‌کم ۷ روز و ۲ سفارش،
 *  حداکثر ۳ بار با ۳۰ روز فاصله؛ «دیگر نپرس» و «ستاره می‌دهم» برای همیشه.
 */
@Composable
internal fun RatePromptShopper(model: CustomerModel, hook: RateHook?) {
  if (hook == null || hook.target == RateTarget.NONE) return
  var state by remember { mutableStateOf(RatePrompt.decode(model.kv.get(RATE_KEY))) }
  fun save(s: RateState) {
    state = s
    model.kv.put(RATE_KEY, RatePrompt.encode(s))
  }
  LaunchedEffect(Unit) {
    if (state.firstSeenAt <= 0) save(RatePrompt.seen(state, model.clock()))
    if (ShopperRateSession.startedAt <= 0) {
      ShopperRateSession.startedAt = model.clock()
      ShopperRateSession.newestAtStart = model.saved.orders.firstOrNull()?.token
    }
  }
  if (state.stop != null) return

  //  شمارِ سفارش‌ها از فهرستِ سقف‌دارِ همین دستگاه؛ «سفارشِ تازه در همین بار» از تازه‌ترین توکن (نه از شمار)
  val orders = model.saved.orders
  val newest = orders.firstOrNull()?.token
  val home = model.route == Route.Home
  val errorNow = model.market is Load.Failed || model.shopList is Load.Failed || model.addressError
  var open by remember { mutableStateOf(false) }
  LaunchedEffect(home, errorNow, orders.size, newest) {
    if (open || !home) return@LaunchedEffect
    delay(RatePrompt.SETTLE_MS)
    val startedAt = ShopperRateSession.startedAt.takeIf { it > 0 } ?: return@LaunchedEffect
    val at = model.clock()
    if (errorNow) ShopperRateSession.lastErrorAt = at
    val m = RateMoment(
      now = at, target = hook.target, usage = orders.size, minUsage = RatePrompt.SHOPPER_MIN_ORDERS, sessionStartedAt = startedAt,
      usedThisSession = newest != null && newest != ShopperRateSession.newestAtStart, busy = !home,
      lastErrorAt = ShopperRateSession.lastErrorAt,
    )
    if (RatePrompt.shouldAsk(state, m)) {
      save(RatePrompt.shown(state, at))
      //  App Store: فقط کارتِ خودِ سیستم، بی برگهٔ ما (قاعدهٔ ۵.۶.۱ ِ اپل) — شمرده می‌شود، ولی «ستاره داد» دانسته نمی‌شود
      if (hook.target.ownSheet) open = true else hook.open()
    }
  }
  if (open) {
    ShopperRateSheet { choice ->
      open = false
      save(RatePrompt.chose(state, choice, model.clock()))
      if (choice == RateChoice.RATE) hook.open()
    }
  }
}

/**
 *  برگهٔ کوچکِ «ستاره بدهید» ِ برنامهٔ خرید (رنگِ ویترین): ستاره، یک جمله و سه دکمه. ⛔ نه پرسش، نه شمارِ ستاره، نه جایزه؛
 *  «ستاره می‌دهم» برای هر کس فروشگاه است. [onChoice] دقیقاً یک بار، پس از رفتنِ برگه؛ کشیدن/بیرون همان «بعداً».
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ShopperRateSheet(onChoice: (RateChoice) -> Unit) {
  val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  val scope = rememberCoroutineScope()
  var chosen by remember { mutableStateOf<RateChoice?>(null) }
  var done by remember { mutableStateOf(false) }
  fun finish() {
    if (done) return
    done = true
    onChoice(chosen ?: RateChoice.LATER)
  }
  fun pick(choice: RateChoice) {
    if (chosen != null) return
    chosen = choice
    scope.launch { sheet.hide() }.invokeOnCompletion { finish() }
  }
  ModalBottomSheet(onDismissRequest = { finish() }, sheetState = sheet) {
    Column(
      Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(56.dp)) {
        Box(contentAlignment = Alignment.Center) {
          Icon(Icons.Filled.Star, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(30.dp))
        }
      }
      Spacer(Modifier.height(12.dp))
      Text(RateText.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
      Spacer(Modifier.height(6.dp))
      Text(
        RateText.body,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
      )
      Spacer(Modifier.height(20.dp))
      Button(onClick = { pick(RateChoice.RATE) }, modifier = Modifier.fillMaxWidth()) { Text(RateText.rate) }
      Spacer(Modifier.height(8.dp))
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { pick(RateChoice.LATER) }, modifier = Modifier.weight(1f)) { Text(RateText.later) }
        TextButton(onClick = { pick(RateChoice.NEVER) }, modifier = Modifier.weight(1f)) { Text(RateText.never) }
      }
    }
  }
}
