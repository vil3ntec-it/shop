package ir.vil3ntec.tohid.ui.v3.account.support

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.SupportAgent
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.StrAccount
import ir.vil3ntec.tohid.core.model.SupportMessageDto
import ir.vil3ntec.tohid.data.repo.Backend
import ir.vil3ntec.tohid.sync.SyncStore
import ir.vil3ntec.tohid.ui.screens.MotionSpec
import ir.vil3ntec.tohid.ui.screens.MotionTokens
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.PhoneV3Palette
import ir.vil3ntec.tohid.ui.theme.PhoneV3Roles
import ir.vil3ntec.tohid.ui.theme.V3ButtonKind
import ir.vil3ntec.tohid.ui.theme.V3Tone
import ir.vil3ntec.tohid.ui.v3.LocalV3SystemBottom
import ir.vil3ntec.tohid.ui.v3.V3EmptyState
import ir.vil3ntec.tohid.ui.v3.V3Message
import ir.vil3ntec.tohid.ui.v3.V3Scaffold
import ir.vil3ntec.tohid.ui.v3.V3Skeleton
import ir.vil3ntec.tohid.ui.v3.V3Type
import ir.vil3ntec.tohid.ui.v3.nav.RouteScope
import ir.vil3ntec.tohid.ui.v3.rememberV3Stagger
import ir.vil3ntec.tohid.ui.v3.v3AnimateItem
import ir.vil3ntec.tohid.ui.v3.v3Pressable
import ir.vil3ntec.tohid.ui.v3.v3Shake
import ir.vil3ntec.tohid.ui.v3.v3Stagger
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** آن‌چه صفحهٔ پشتیبانی نشان می‌دهد */
internal data class SupportUi(
  val items: List<SupportModel.Item>,
  val greeting: String = "",
  val loading: Boolean = false,
  val error: String? = null,
  val draft: String = "",
  val sending: Boolean = false,
)

/**
 *  «پشتیبانی» به شکلِ گفت‌وگو: حباب‌ها (خودم = surfaceSelected، پشتیبانی = سطحِ کارت
 *  با مرز، سامانه = رنگِ هشدار)، جداکنندهٔ روز، حبابِ «در حال فرستادن…»، و نوارِ
 *  نوشتن که بالای صفحه‌کلید می‌ماند — «فرستادن» تا نوشته خالی است خاموش.
 */
@Composable
internal fun SupportContent(ui: SupportUi, onBack: (() -> Unit)?, onDraft: (String) -> Unit, onSend: () -> Unit) {
  val c = PhoneV3.colors
  val list = rememberLazyListState()
  val stagger = rememberV3Stagger()
  LaunchedEffect(ui.items.size) { if (ui.items.isNotEmpty()) list.animateScrollToItem(ui.items.lastIndex) }
  V3Scaffold(
    title = Str.support,
    onBack = onBack,
    largeTitle = false,
    bottomBar = { Box(Modifier.v3Shake(ui.error)) { DraftComposer(ui.draft, onDraft, SupportModel.canSend(ui.draft, ui.sending), onSend) } },
  ) { pad ->
    Column(Modifier.fillMaxSize()) {
      Text(StrAccount.supportSubtitle, style = V3Type.footnote, color = c.inkMuted, modifier = Modifier.fillMaxWidth().padding(top = 6.dp), textAlign = TextAlign.Center)
      ui.error?.let { Box(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) { V3Message(V3Tone.ERROR, it) } }
      when {
        ui.loading && ui.items.isEmpty() -> Box(Modifier.padding(16.dp)) { V3Skeleton(rows = 3) }
        ui.items.isEmpty() -> V3EmptyState(Icons.Outlined.SupportAgent, ui.greeting.ifBlank { StrAccount.supportIntro })
        else -> LazyColumn(
          state = list,
          modifier = Modifier.weight(1f).fillMaxWidth(),
          contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 8.dp, bottom = pad.calculateBottomPadding()),
          verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          itemsIndexed(ui.items, key = { _, it -> it.key }) { i, item ->
            when (item) {
              //  پیامِ تازه با جابه‌جاییِ نرمِ بقیه می‌آید (`v3AnimateItem`)، بارِ اول پلکانی
              is SupportModel.Item.Day -> DaySeparator(item.label, Modifier.v3AnimateItem(this))
              is SupportModel.Item.Bubble -> Bubble(item, Modifier.v3AnimateItem(this).v3Stagger(stagger, item.key, i))
            }
          }
        }
      }
    }
  }
}

@Composable
private fun DaySeparator(label: String, modifier: Modifier) {
  val c = PhoneV3.colors
  Box(modifier.fillMaxWidth().padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
    Text(label, style = V3Type.caption, color = c.inkMuted, modifier = Modifier.clip(CircleShape).background(c.tile).padding(horizontal = 12.dp, vertical = 4.dp))
  }
}

@Composable
private fun Bubble(b: SupportModel.Item.Bubble, modifier: Modifier) {
  val c = PhoneV3.colors
  val warn = PhoneV3Roles.tone(c, V3Tone.WARNING)
  val shape = RoundedCornerShape(18.dp)
  val (bg, edge) = when (b.kind) {
    SupportModel.Kind.MINE -> PhoneV3Palette.selected(c) to PhoneV3Palette.selected(c)
    SupportModel.Kind.SYSTEM -> warn.bg to warn.border
    SupportModel.Kind.THEIRS -> c.card to c.hairline
  }
  Row(modifier.fillMaxWidth(), horizontalArrangement = if (b.kind == SupportModel.Kind.MINE) Arrangement.End else Arrangement.Start) {
    Column(Modifier.widthIn(max = 300.dp).clip(shape).background(bg).border(1.dp, edge, shape).padding(horizontal = 12.dp, vertical = 9.dp)) {
      b.sender?.let {
        Text(it, style = V3Type.caption, color = if (b.kind == SupportModel.Kind.SYSTEM) warn.fg else PhoneV3Roles.brandText(c))
        Spacer(Modifier.height(2.dp))
      }
      Text(b.body, style = V3Type.body, color = c.ink)
      Spacer(Modifier.height(2.dp))
      Text(b.time, style = V3Type.caption, color = c.inkMuted, modifier = Modifier.align(Alignment.End))
    }
  }
}

/**
 *  نوارِ نوشتنِ پیام (پیش‌نویسِ گروهِ حساب برای کیت: `V3Composer`): کادرِ قرصیِ
 *  چندخطی (تا ۴ خط) و دکمهٔ گردِ برندِ «فرستادن»؛ بالای صفحه‌کلید و نوارِ سیستم.
 */
@Composable
internal fun DraftComposer(value: String, onValueChange: (String) -> Unit, canSend: Boolean, onSend: () -> Unit) {
  val c = PhoneV3.colors
  val pair = if (canSend) PhoneV3Roles.button(c, V3ButtonKind.PRIMARY) else PhoneV3Roles.disabled(c)
  val sendBg by animateColorAsState(pair.bg, MotionSpec.tweenOf(MotionTokens.SHORT), label = "sendBg")
  val insets = if (LocalV3SystemBottom.current != null) WindowInsets.ime.exclude(WindowInsets.navigationBars) else WindowInsets.ime.union(WindowInsets.navigationBars)
  Column(Modifier.fillMaxWidth().background(c.bg).windowInsetsPadding(insets)) {
    Box(Modifier.fillMaxWidth().height(1.dp).background(c.hairline))
    Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.Bottom) {
      val shape = RoundedCornerShape(22.dp)
      BasicTextField(
        value = value,
        onValueChange = onValueChange,
        maxLines = 4,
        textStyle = V3Type.body.copy(color = c.ink),
        cursorBrush = SolidColor(PhoneV3Roles.focus(c)),
        modifier = Modifier.weight(1f).semantics { contentDescription = StrAccount.messageHint },
        decorationBox = { inner ->
          Box(
            Modifier.fillMaxWidth().heightIn(min = 44.dp).clip(shape).background(PhoneV3Roles.inputFill(c)).border(1.dp, PhoneV3Roles.inputBorder(c), shape).padding(horizontal = 16.dp, vertical = 11.dp),
            contentAlignment = Alignment.CenterStart,
          ) {
            if (value.isEmpty()) Text(StrAccount.messageHint, style = V3Type.body, color = c.inkMuted)
            inner()
          }
        },
      )
      Spacer(Modifier.width(8.dp))
      Box(
        Modifier.v3Pressable(enabled = canSend, label = StrAccount.send, scale = 0.92f, haptic = true, onClick = onSend).size(44.dp).clip(CircleShape).background(sendBg),
        contentAlignment = Alignment.Center,
      ) { Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = StrAccount.send, tint = pair.fg, modifier = Modifier.size(20.dp)) }
    }
  }
}

/**
 *  مسیرِ `support`: گفت‌وگو به شناسهٔ همین دستگاه (بی‌حساب هم)، هر ۸ ثانیه فقط پیام‌های
 *  تازه، باز شدنِ صفحه یعنی «دیدم» (`markRead`) — همان فراخوان‌های صفحهٔ پیشین.
 */
@Composable
internal fun RouteScope.SupportRoute() {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val state = remember { SyncStore(context) }
  var messages by remember { mutableStateOf<List<SupportMessageDto>>(emptyList()) }
  var greeting by remember { mutableStateOf("") }
  var draft by rememberSaveable { mutableStateOf("") }
  var pending by remember { mutableStateOf<String?>(null) }
  var loading by remember { mutableStateOf(true) }
  var error by remember { mutableStateOf<String?>(null) }
  var lastAt by remember { mutableLongStateOf(0L) }

  suspend fun load(after: Long) {
    Backend.support(context).thread(state.deviceUid, after)
      .onSuccess { view ->
        messages = if (after == 0L) view.messages else messages + view.messages
        if (view.messages.isNotEmpty()) lastAt = view.messages.last().createdAt
        if (view.greeting.isNotBlank()) greeting = view.greeting
        error = null
      }
      .onFailure { error = it.userMessage }
    loading = false
  }
  LaunchedEffect(Unit) {
    load(0)
    Backend.support(context).markRead(state.deviceUid)
  }
  LaunchedEffect(lastAt) {
    while (true) { delay(8_000); load(lastAt) }
  }

  SupportContent(
    SupportUi(SupportModel.items(messages, System.currentTimeMillis(), pending), greeting, loading, error, draft, pending != null),
    onBack = if (nav.canBack) ({ back() }) else null,
    onDraft = { draft = it },
    onSend = {
      val body = draft.trim()
      if (body.isNotEmpty()) {
        pending = body
        draft = ""
        scope.launch {
          Backend.support(context).sendMessage(state.deviceUid, state.accountName, body)
            .onSuccess { load(lastAt) }
            .onFailure { error = it.userMessage; draft = body }
          pending = null
        }
      }
    },
  )
}
