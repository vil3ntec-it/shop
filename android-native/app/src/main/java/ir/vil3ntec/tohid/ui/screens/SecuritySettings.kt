package ir.vil3ntec.tohid.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
  var askOn by remember { mutableStateOf(false) }
  var askRebuild by remember { mutableStateOf(false) }

  /** نوشتنِ تنظیم و کوچِ همان لحظه */
  fun setEncryption(want: Boolean) {
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
  }

  SettingsSection(
    icon = Icons.Filled.Security,
    title = SECURITY_SECTION_TITLE,
    subtitle = if (on) "رمزگذاریِ دفتر: روشن" else "رمزگذاریِ دفتر: خاموش",
    tint = if (unreadable != null) colors.danger else if (on) colors.success else colors.muted,
  ) {
    Text(
      "دفترِ دکان، دفترِ حساب‌های دیگرِ همین دستگاه، بایگانی، نسخهٔ ایمنیِ پیش از بازیابی و نسخه‌های خودکار " +
        "روی همین دستگاه رمز می‌شوند؛ کسی که فقط این فایل‌ها را بردارد، بی کلیدِ همین دستگاه چیزی نمی‌خواند.",
      style = MaterialTheme.typography.bodySmall,
      color = colors.muted,
    )
    Spacer(Modifier.height(6.dp))
    Text(
      PLAIN_IN_PHASE_1,
      style = MaterialTheme.typography.labelSmall,
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
        //  روشن کردن پس از پرسشِ روشن (بازبینیِ #331)؛ خاموش کردن همان لحظه — راهِ برگشت است
        onCheckedChange = { want -> if (want) askOn = true else setEncryption(false) },
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
      AtRestKeys.note() + " فایل‌های پشتیبانی که بیرون می‌روند (فایل، پوشه، ابر) رمزِ این دستگاه را ندارند.",
      style = MaterialTheme.typography.labelSmall,
      color = colors.muted,
    )

    //  فایل‌های کنارگذاشته — هیچ‌وقت خودکار پاک نمی‌شوند؛ فقط گفته می‌شود که هستند
    val left = s?.leftovers.orEmpty()
    if (left.isNotEmpty()) {
      Spacer(Modifier.height(8.dp))
      Text(
        "${left.size} فایلِ کنارگذاشته روی دستگاه مانده (پاک نشده‌اند): " + left.take(4).joinToString("، ") +
          if (left.size > 4) "، …" else "",
        style = MaterialTheme.typography.labelSmall,
        color = colors.muted,
      )
    }

    //  دفترِ رمزدارِ خوانده‌نشده: نوشتن بسته است؛ راهِ برگشت همین‌جا
    if (unreadable != null) {
      Spacer(Modifier.height(12.dp))
      Text(unreadableText(unreadable), style = MaterialTheme.typography.bodySmall, color = colors.danger)
      Spacer(Modifier.height(8.dp))
      TohidButton(
        text = REBUILD,
        enabled = !busy,
        busy = busy,
        onClick = { askRebuild = true },
        modifier = Modifier.fillMaxWidth(),
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

  if (askOn) {
    AlertDialog(
      onDismissRequest = { askOn = false },
      containerColor = colors.bg,
      title = { Text("رمزگذاریِ دفتر روشن شود؟", color = colors.text) },
      text = { Text(enableWarning(), color = colors.muted) },
      confirmButton = {
        TextButton(onClick = { askOn = false; setEncryption(true) }) { Text("روشن کن", color = colors.primary) }
      },
      dismissButton = { TextButton(onClick = { askOn = false }) { Text("نه", color = colors.muted) } },
    )
  }

  if (askRebuild) {
    RebuildDialog(
      onDismiss = { askRebuild = false },
      onConfirm = {
        askRebuild = false
        busy = true
        scope.launch {
          val r = store.rebuildFromServer()
          busy = false
          tick++
          toast(r.message)
        }
      },
    )
  }
}

/** نامِ راهِ برگشت — همان در صفحهٔ امنیت و پنجرهٔ «دفتر باز نشد» */
const val REBUILD = "بازسازی از سرور"

/** چه چیزی در فاز ۱ ساده می‌ماند — بی تعارف (بازبینیِ #331؛ شرح سرِ `AtRestScope`) */
const val PLAIN_IN_PHASE_1 =
  "ساده می‌ماند (فاز ۱): صفِ تغییرهای همگام‌سازی، ژورنال‌ها و سایهٔ همگام‌سازی (نسخه‌ای از ردیف‌های همگام‌شده)، " +
    "خلاصهٔ دفتر (ledger-summary.json) و سبدِ نیمه‌کاره (sale-draft.json). توکن‌های ورود از پیش جدا رمزند."

private fun unreadableText(kind: AtRestException.Kind?): String = when (kind) {
  AtRestException.Kind.KEY_UNAVAILABLE ->
    "دفترِ رمزدار همین حالا باز نشد (کلید در دسترس نبود). دوباره امتحان کنید؛ اگر نشد، «$REBUILD» دفتر را از سرور می‌گیرد."
  AtRestException.Kind.KEY_MISSING ->
    "دفترِ این دستگاه رمزدار است ولی کلیدش روی این دستگاه نیست (نصبِ دوباره، انتقال به " +
      "${if (Platform.DESKTOP) "کامپیوترِ" else "گوشیِ"} تازه یا از کار افتادنِ کلید). هیچ چیزی پاک نشده. " +
      "«$REBUILD» اول تغییرهای نرفتهٔ این دستگاه را می‌فرستد و بعد دفتر را از سرور می‌سازد؛ " +
      "بازیابیِ پشتیبان هم از همین راه است و فقط ردیف‌هایی را می‌افزاید که هرگز به سرور نرفته بودند؛ ردیفِ حذف‌شده زنده نمی‌شود."
  AtRestException.Kind.IO ->
    "بازسازیِ قبلی نیمه‌کاره ماند و دفترِ تازه جا نیفتاد؛ برای اینکه دفترِ قدیم کنارِ همگام‌سازیِ تازه ذخیره نشود، ثبت بسته است. " +
      "«دوباره امتحان» همان جابه‌جایی را تمام می‌کند؛ اگر نشد، «$REBUILD» را دوباره بزنید. هیچ چیزی پاک نشده."
  else ->
    "فایلِ دفترِ رمزدار خراب است و باز نشد. هیچ چیزی پاک نشده؛ «$REBUILD» دفتر را از سرور می‌سازد."
}

private fun enableWarning(): String = buildString {
  append("کلید فقط روی همین ${if (Platform.DESKTOP) "کامپیوتر" else "گوشی"} است. ")
  append(AtRestKeys.note())
  append(" اگر کلید از دست برود، دفتر با «$REBUILD» برمی‌گردد — پس پیش از روشن کردن مطمئن شوید همگام‌سازی کار می‌کند.")
  if (Platform.DESKTOP) {
    append(
      "\n\n⚠️ پیش از نصبِ نسخهٔ قدیمی‌ترِ برنامه این را خاموش کنید. نسخه‌های پیش از این، دفترِ رمزدار را نمی‌شناسند: " +
        "دفتر را خالی نشان می‌دهند و نخستین ذخیره‌شان دفترِ رمزدار را رونویسی می‌کند و همهٔ ردیف‌ها را از سرور هم پاک می‌کند.",
    )
  }
}

/** پرسشِ پیش از «بازسازی از سرور» */
@Composable
private fun RebuildDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
  val colors = Shop.colors
  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = colors.bg,
    title = { Text("$REBUILD؟", color = colors.text) },
    text = {
      Text(
        "اول تغییرهایی که هنوز از این دستگاه نرفته‌اند فرستاده می‌شوند؛ اگر نرسیدند، هیچ چیزی عوض نمی‌شود. " +
          "بعد دفتر از روی سرور ساخته می‌شود. دفترِ رمزدارِ فعلی پاک نمی‌شود، کنار می‌رود. اینترنت لازم است.",
        color = colors.muted,
      )
    },
    confirmButton = { TextButton(onClick = onConfirm) { Text(REBUILD, color = colors.primary) } },
    dismissButton = { TextButton(onClick = onDismiss) { Text("نه", color = colors.muted) } },
  )
}

/**
 *  پنجرهٔ «دفتر باز نشد» — `AppRoot` وقتی `ShopStore.unreadable` پر شد نشانش می‌دهد (به‌جای
 *  پیامِ تنها): راهِ برگشت همین‌جا، بی گشتن در تنظیمات.
 */
@Composable
fun UnreadableLedgerDialog(
  store: ShopStore,
  onOpenSecurity: () -> Unit,
  onDismiss: () -> Unit,
  toast: (String) -> Unit,
) {
  val colors = Shop.colors
  val scope = rememberCoroutineScope()
  val kind by store.unreadable.collectAsState()
  var busy by remember { mutableStateOf(false) }
  var ask by remember { mutableStateOf(false) }
  AlertDialog(
    onDismissRequest = { if (!busy) onDismiss() },
    containerColor = colors.bg,
    title = { Text("دفترِ رمزدار باز نشد", color = colors.text) },
    text = {
      Text(
        "برای اینکه دفترِ واقعی رونویسی نشود، ثبت بسته است. " + unreadableText(kind),
        color = colors.muted,
      )
    },
    confirmButton = {
      TextButton(enabled = !busy, onClick = { ask = true }) {
        Text(if (busy) "در حالِ بازسازی…" else REBUILD, color = colors.primary)
      }
    },
    dismissButton = {
      Row {
        TextButton(enabled = !busy, onClick = onOpenSecurity) { Text(SECURITY_SECTION_TITLE, color = colors.muted) }
        TextButton(enabled = !busy, onClick = onDismiss) { Text("بعداً", color = colors.muted) }
      }
    },
  )
  if (ask) {
    RebuildDialog(
      onDismiss = { ask = false },
      onConfirm = {
        ask = false
        busy = true
        scope.launch {
          val r = store.rebuildFromServer()
          busy = false
          toast(r.message)
          if (r.ok) onDismiss()
        }
      },
    )
  }
}
