package ir.vil3ntec.tohid.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.atrest.AtRestException
import ir.vil3ntec.tohid.core.atrest.AtRestKeyring
import ir.vil3ntec.tohid.data.AtRest
import ir.vil3ntec.tohid.data.AtRestKeys
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.ui.Platform
import ir.vil3ntec.tohid.ui.theme.Shop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** عنوانِ بخش — همان که فهرستِ بخش‌های تنظیماتِ کامپیوتر (`AppRoot`) نشان می‌دهد */
const val SECURITY_SECTION_TITLE = "امنیت"

/**
 *  تنظیمات ← امنیت: «رمزگذاریِ دفتر روی همین دستگاه» (SEC-01، فاز ۱).
 *
 *  یک ردیفِ کمینه، خودبسنده و بی‌وابستگی به چیدمانِ تنظیمات تا چیدمانِ تازهٔ
 *  تنظیمات هر جا خواست بگذاردش. پیش‌فرض **خاموش**؛ روشن/خاموش کردن همان لحظه
 *  فایل‌ها را کوچ می‌دهد (`ShopStore.convergeAtRest`) و نتیجه را راست می‌گوید.
 */
@Composable
fun SecuritySection(store: ShopStore, toast: (String) -> Unit) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val colors = Shop.colors
  val unreadable by store.unreadable.collectAsState()

  var status by remember { mutableStateOf<AtRest.Status?>(null) }
  var busy by remember { mutableStateOf(false) }
  //  هر بار که `tick` جلو برود، وضعیت از روی دیسک دوباره خوانده می‌شود (فقط سرآیندها)
  var tick by remember { mutableIntStateOf(0) }
  LaunchedEffect(tick) {
    status = withContext(Dispatchers.IO) { runCatching { AtRest.status(context) }.getOrNull() }
  }

  val on = status?.wanted ?: false

  SettingsSection(
    icon = Icons.Filled.Security,
    title = SECURITY_SECTION_TITLE,
    subtitle = if (on) "رمزگذاریِ دفتر: روشن" else "رمزگذاریِ دفتر: خاموش",
    tint = if (unreadable != null) colors.danger else if (on) colors.success else colors.muted,
  ) {
    Text(
      "دفتر، بایگانی و نسخه‌های خودکار روی همین دستگاه رمز می‌شوند؛ کسی که فایل‌های برنامه را بردارد، بی کلیدِ همین دستگاه چیزی نمی‌خواند.",
      style = MaterialTheme.typography.bodySmall,
      color = colors.muted,
    )
    Spacer(Modifier.height(12.dp))
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
      Column(Modifier.weight(1f)) {
        Text("رمزگذاریِ دفتر روی این دستگاه", style = MaterialTheme.typography.labelLarge, color = colors.text)
        Text(
          when {
            busy -> "در حالِ کوچِ فایل‌ها…"
            status == null -> "…"
            else -> "${status!!.sealed} فایلِ رمزدار، ${status!!.plain} فایلِ ساده"
          },
          style = MaterialTheme.typography.labelSmall,
          color = colors.muted,
        )
      }
      Switch(
        checked = on,
        enabled = !busy && status != null,
        modifier = Modifier.semantics { contentDescription = "رمزگذاریِ دفتر روی این دستگاه" },
        onCheckedChange = { want ->
          busy = true
          scope.launch {
            val saved = withContext(Dispatchers.IO) { AtRest.setWanted(context, want) }
            val r = if (saved) store.convergeAtRest() else null
            busy = false
            tick++
            toast(
              when {
                r == null -> "تنظیم ذخیره نشد"
                !r.ok -> "${r.failed.size} فایل کوچ نشد و دست‌نخورده ماند؛ دوباره امتحان کنید"
                want -> "رمزگذاری روشن شد (${r.converted} فایل رمز شد)"
                else -> "رمزگذاری خاموش شد (${r.converted} فایل ساده شد)"
              },
            )
          }
        },
      )
    }

    //  روشن است ولی کلید ساخته/باز نشد ⇒ دفتر ساده نوشته می‌شود؛ راستش گفته می‌شود
    val s = status
    if (s != null && s.wanted) {
      val keyLine = when (val k = s.key) {
        is AtRestKeyring.State.Ready -> null
        is AtRestKeyring.State.Unavailable -> "کلیدِ رمز همین حالا در دسترس نیست (${k.reason}) — دفتر فعلاً ساده نوشته می‌شود."
        is AtRestKeyring.State.Lost -> "کلیدِ رمزِ قبلی روی این دستگاه نیست (${k.reason})."
        AtRestKeyring.State.None -> "کلیدِ رمز هنوز ساخته نشده — دفتر فعلاً ساده نوشته می‌شود."
      } ?: s.sealError?.let { "آخرین ذخیره رمز نشد: $it" }
      if (keyLine != null) {
        Spacer(Modifier.height(8.dp))
        Text(keyLine, style = MaterialTheme.typography.labelSmall, color = colors.warning)
      }
    }

    Spacer(Modifier.height(8.dp))
    Text(
      if (AtRestKeys.HARDWARE_BACKED)
        "کلید در ${AtRestKeys.WHERE} است و با پشتیبانِ خودکار یا انتقالِ اندروید به گوشیِ تازه نمی‌رود؛ گوشیِ تازه دفتر را از سرور یا فایلِ پشتیبان می‌گیرد. فایل‌های پشتیبانی که بیرون می‌روند رمزِ این دستگاه را ندارند."
      else
        "کلید فعلاً در ${AtRestKeys.WHERE} است؛ از بیرون رفتنِ تنهای فایلِ دفتر محافظت می‌کند، ولی کسی که کلِ پوشهٔ داده را دارد کلید را هم دارد. نگه‌داشتنِ کلید در خودِ ویندوز در مرحلهٔ بعد می‌آید.",
      style = MaterialTheme.typography.labelSmall,
      color = colors.muted,
    )

    //  دفترِ رمزدارِ خوانده‌نشده: نوشتن بسته است؛ راهِ برگشت همین‌جا گفته می‌شود
    if (unreadable != null) {
      Spacer(Modifier.height(12.dp))
      Text(
        when (unreadable) {
          AtRestException.Kind.KEY_UNAVAILABLE -> "دفترِ رمزدار همین حالا باز نشد (کلید در دسترس نبود). دوباره امتحان کنید."
          AtRestException.Kind.KEY_MISSING ->
            "دفترِ این دستگاه رمزدار است ولی کلیدش روی این دستگاه نیست (نصبِ دوباره یا انتقال به ${if (Platform.DESKTOP) "کامپیوترِ" else "گوشیِ"} تازه). " +
              "هیچ چیزی پاک نشده؛ از «داده‌ها» پشتیبانِ ابری، نسخهٔ پوشه یا فایلِ پشتیبان را بازیابی کنید."
          else -> "فایلِ دفترِ رمزدار خراب است و باز نشد. هیچ چیزی پاک نشده؛ از «داده‌ها» یک پشتیبان را بازیابی کنید."
        },
        style = MaterialTheme.typography.bodySmall,
        color = colors.danger,
      )
      Spacer(Modifier.height(8.dp))
      TohidSecondaryButton(
        text = "دوباره امتحان",
        enabled = !busy,
        onClick = {
          busy = true
          scope.launch {
            store.loadAndSummarize()
            busy = false
            tick++
            toast(if (store.unreadable.value == null) "دفتر باز شد" else "هنوز باز نشد")
          }
        },
        modifier = Modifier.fillMaxWidth(),
      )
    }
  }
}
