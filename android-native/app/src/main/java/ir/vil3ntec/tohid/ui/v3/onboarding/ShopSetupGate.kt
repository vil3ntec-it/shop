package ir.vil3ntec.tohid.ui.v3.onboarding

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.StrAuth
import ir.vil3ntec.tohid.core.net.userText
import ir.vil3ntec.tohid.core.onboarding.ShopChoice
import ir.vil3ntec.tohid.core.onboarding.ShopSetupRules
import ir.vil3ntec.tohid.data.LedgerOwner
import ir.vil3ntec.tohid.data.ShopRole
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.data.StaffCode
import ir.vil3ntec.tohid.data.repo.Backend
import ir.vil3ntec.tohid.sync.AutoSync
import ir.vil3ntec.tohid.ui.screens.MotionSpec
import ir.vil3ntec.tohid.ui.screens.MotionTokens
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.PhoneV3Roles
import ir.vil3ntec.tohid.ui.v3.V3Collapse
import ir.vil3ntec.tohid.ui.v3.V3Hue
import ir.vil3ntec.tohid.ui.v3.V3IconTile
import ir.vil3ntec.tohid.ui.v3.V3TextField
import ir.vil3ntec.tohid.ui.v3.V3Type
import ir.vil3ntec.tohid.ui.v3.v3Pressable
import kotlinx.coroutines.launch

/**
 *  `setup/shop` — «دکانت را بسازیم» (فقط وقتی سرور گفته `no_shop`، نه با خطای شبکه؛ آن تصمیم مالِ
 *  پوسته است). دو کارتِ گزینه: صاحبِ دکان یکی می‌سازد و مالکش می‌شود، شاگرد با کدِ صاحبش روی همان
 *  دکان می‌نشیند (دکانِ خودکار نه — شاگرد دیگر نمی‌توانست بپیوندد). پس از هر دو، دفتر به همین دکان
 *  سند می‌خورد و همگام‌سازی همان لحظه راه می‌افتد. «بعداً» همیشه هست؛ دفعهٔ بعد دوباره پرسیده می‌شود.
 */
@Composable
fun ShopSetupGate(store: ShopStore, onDone: () -> Unit) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val shops = remember(context) { Backend.shop(context) }
  //  نامِ دکان اگر از قبل در تنظیمات گذاشته شده، همان پیشنهاد می‌شود
  val savedName = remember {
    context.getSharedPreferences("tohid", android.content.Context.MODE_PRIVATE).getString("store_name", "").orEmpty()
  }
  var choice by rememberSaveable { mutableStateOf(ShopChoice.OWNER) }
  var name by rememberSaveable { mutableStateOf(savedName) }
  var code by rememberSaveable { mutableStateOf("") }
  var busy by remember { mutableStateOf(false) }
  var error by remember { mutableStateOf<String?>(null) }
  var failures by remember { mutableIntStateOf(0) }

  suspend fun settle(shopId: String) {
    runCatching { LedgerOwner.shopChanged(context, store, shopId) }
    AutoSync.now(context, store)
    onDone()
  }
  fun fail(text: String) { error = text; failures++ }

  ShopSetupBody(
    choice = choice, name = name, code = code, busy = busy, error = error, failures = failures,
    onChoice = { choice = it; error = null },
    onName = { name = it; error = null },
    onCode = { code = it.uppercase(); error = null },
    onCreate = {
      busy = true; error = null
      scope.launch {
        shops.create(ShopSetupRules.nameToCreate(name))
          .onSuccess { settle(it.shop?.id.orEmpty()) }
          .onFailure { fail(it.userText(StrAuth.shopNotCreated)) }
        busy = false
      }
    },
    onJoin = {
      val entered = StaffCode.clean(code)
      //  قالبِ کد کارِ سرور است؛ گوشی فقط می‌بیند خالی نباشد
      if (!StaffCode.usable(entered)) {
        fail(Str.codeIncomplete)
      } else {
        busy = true; error = null
        scope.launch {
          //  کاربر از قبل وارد است (این دروازه بعد از ورود می‌آید) ⇒ همان حساب می‌پیوندد
          shops.join(entered)
            .onSuccess {
              it.role?.let { role -> ShopRole.remember(context, role) }
              settle(it.shop?.id.orEmpty())
            }
            .onFailure { fail(it.userText(StrAuth.joinFailed)) }
          busy = false
        }
      }
    },
    onLater = onDone,
  )
}

/** نمایشِ «ساختِ دکان»، بی حالتِ خودش (عکس‌ها همین را با هر گزینه و خطا می‌کشند) */
@Composable
internal fun ShopSetupBody(
  choice: ShopChoice,
  name: String,
  code: String,
  busy: Boolean,
  error: String?,
  failures: Int,
  onChoice: (ShopChoice) -> Unit,
  onName: (String) -> Unit,
  onCode: (String) -> Unit,
  onCreate: () -> Unit,
  onJoin: () -> Unit,
  onLater: () -> Unit,
) {
  GateBackdrop {
    GateFrame(
      title = StrAuth.setupShop,
      subtitle = StrAuth.setupNote,
      onBack = null,
      bare = true,
      footer = { GateLink(StrAuth.laterThisDevice, onLater, enabled = !busy) },
    ) {
      ChoiceCard(Icons.Outlined.Storefront, V3Hue.BRAND, StrAuth.areYouOwner, StrAuth.ownerChoiceSub, choice == ShopChoice.OWNER, { onChoice(ShopChoice.OWNER) }) {
        V3TextField(name, onName, StrAuth.shopName, Modifier.fillMaxWidth(), placeholder = StrAuth.shopNameExample, imeAction = ImeAction.Done, onImeAction = onCreate)
        if (choice == ShopChoice.OWNER) GateMessages(error, null, failures)
        Spacer(Modifier.height(14.dp))
        SubmitButton(StrAuth.createMyShop, enabled = !busy, loading = busy, done = false, onClick = onCreate)
      }
      Spacer(Modifier.height(12.dp))
      ChoiceCard(Icons.Outlined.Badge, V3Hue.VIOLET, StrAuth.areYouStaff, StrAuth.staffChoiceSub, choice == ShopChoice.STAFF, { onChoice(ShopChoice.STAFF) }) {
        V3TextField(code, onCode, Str.joinCode, Modifier.fillMaxWidth(), placeholder = StrAuth.ownerCodeHint, imeAction = ImeAction.Go, onImeAction = onJoin)
        if (choice == ShopChoice.STAFF) GateMessages(error, null, failures)
        Spacer(Modifier.height(14.dp))
        SubmitButton(StrAuth.joinShop, enabled = !busy && code.isNotBlank(), loading = busy, done = false, onClick = onJoin)
      }
    }
  }
}

/** کارتِ گزینه (رادیو): انتخاب‌شده مرزِ ۲dpِ برند می‌گیرد و کادرهایش نرم باز می‌شوند */
@Composable
private fun ChoiceCard(
  icon: ImageVector,
  hue: V3Hue,
  title: String,
  subtitle: String,
  selected: Boolean,
  onSelect: () -> Unit,
  content: @Composable () -> Unit,
) {
  val c = PhoneV3.colors
  val shape = RoundedCornerShape(GATE_CARD_RADIUS)
  val edge by animateColorAsState(if (selected) PhoneV3Roles.focus(c) else c.hairline, MotionSpec.tweenOf(MotionTokens.SHORT), label = "choiceEdge")
  val width by animateDpAsState(if (selected) 2.dp else 1.dp, MotionSpec.tweenOf(MotionTokens.SHORT), label = "choiceW")
  Column(Modifier.fillMaxWidth().clip(shape).background(c.card).border(width, edge, shape)) {
    Row(
      Modifier
        .fillMaxWidth()
        .semantics { this.selected = selected }
        .v3Pressable(label = title, role = Role.RadioButton, onClick = onSelect)
        .padding(16.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      V3IconTile(icon, hue)
      Spacer(Modifier.width(12.dp))
      Column(Modifier.weight(1f)) {
        Text(title, style = V3Type.headline, color = c.ink)
        Text(subtitle, style = V3Type.footnote, color = c.inkMuted)
      }
      Spacer(Modifier.width(8.dp))
      RadioDot(selected)
    }
    V3Collapse(selected) {
      Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) { content() }
    }
  }
}

@Composable
private fun RadioDot(on: Boolean) {
  val c = PhoneV3.colors
  val ring by animateColorAsState(if (on) PhoneV3Roles.focus(c) else PhoneV3Roles.inputBorder(c), MotionSpec.tweenOf(MotionTokens.SHORT), label = "radio")
  Box(Modifier.size(22.dp).clip(CircleShape).border(2.dp, ring, CircleShape), contentAlignment = Alignment.Center) {
    V3Collapse(on) { Box(Modifier.size(11.dp).clip(CircleShape).background(ring)) }
  }
}
