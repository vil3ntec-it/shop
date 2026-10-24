package ir.vil3ntec.tohid.ui.v3.account.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.outlined.AlternateEmail
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrAccount
import ir.vil3ntec.tohid.data.PhotoStore
import ir.vil3ntec.tohid.data.repo.Backend
import ir.vil3ntec.tohid.sync.SyncStore
import ir.vil3ntec.tohid.ui.screens.Haptics
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.PhoneV3Roles
import ir.vil3ntec.tohid.ui.theme.V3ButtonKind
import ir.vil3ntec.tohid.ui.theme.V3Tone
import ir.vil3ntec.tohid.ui.v3.V3FieldKind
import ir.vil3ntec.tohid.ui.v3.V3Form
import ir.vil3ntec.tohid.ui.v3.V3ListRow
import ir.vil3ntec.tohid.ui.v3.V3Section
import ir.vil3ntec.tohid.ui.v3.V3TextField
import ir.vil3ntec.tohid.ui.v3.V3Type
import ir.vil3ntec.tohid.ui.v3.account.AccountFormat
import ir.vil3ntec.tohid.ui.v3.nav.RouteScope
import ir.vil3ntec.tohid.ui.v3.rememberV3Stagger
import ir.vil3ntec.tohid.ui.v3.v3Pressable
import ir.vil3ntec.tohid.ui.v3.v3Shake
import ir.vil3ntec.tohid.ui.v3.v3Stagger
import kotlinx.coroutines.launch

/** فیلدهای فرمِ «ویرایش حساب و دکان» */
internal data class ProfileEditUi(
  val accountId: String,
  val avatarVersion: Int,
  val name: String,
  val nameError: String? = null,
  val email: String,
  val phone: String,
  val newPhone: String,
  val phoneError: String? = null,
  val shopName: String,
  val createdAt: Long,
  val busy: Boolean = false,
  val dirty: Boolean = false,
)

/** فرمِ V3: عکس با نشانِ دوربین، گروه‌های حساب/دکان/عضویت، دکمهٔ چسبانِ «ذخیره تغییرات» */
@Composable
internal fun ProfileEditContent(
  ui: ProfileEditUi,
  onName: (String) -> Unit,
  onNewPhone: (String) -> Unit,
  onShopName: (String) -> Unit,
  onPhoto: () -> Unit,
  onSave: () -> Unit,
  onBack: (() -> Unit)?,
) {
  val c = PhoneV3.colors
  V3Form(
    title = StrAccount.menuEdit,
    primary = if (ui.busy) StrAccount.saving else StrAccount.saveChanges,
    onPrimary = onSave,
    onBack = onBack,
    loading = ui.busy,
    dirty = ui.dirty,
  ) {
    //  عکس و گروه‌ها با ورودِ پلکانیِ کیت می‌نشینند
    val stagger = rememberV3Stagger()
    Column(Modifier.fillMaxWidth().padding(top = 12.dp).v3Stagger(stagger, "photo", 0), horizontalAlignment = Alignment.CenterHorizontally) {
      Box(Modifier.v3Pressable(label = StrAccount.changePhoto, scale = 0.96f, onClick = onPhoto)) {
        ProfileAvatar(ui.accountId, ui.avatarVersion, 88.dp)
        val pair = PhoneV3Roles.button(c, V3ButtonKind.PRIMARY)
        Box(
          Modifier.align(Alignment.BottomEnd).size(30.dp).clip(CircleShape).background(c.card).padding(2.dp).clip(CircleShape).background(pair.bg),
          contentAlignment = Alignment.Center,
        ) { Icon(Icons.Outlined.PhotoCamera, contentDescription = null, tint = pair.fg, modifier = Modifier.size(16.dp)) }
      }
      Spacer(Modifier.height(8.dp))
      Text(StrAccount.photoLocal, style = V3Type.footnote, color = c.inkMuted, textAlign = TextAlign.Center)
    }

    V3Section(StrAccount.groupAccount, Modifier.v3Stagger(stagger, "account", 1), padding = PaddingValues(16.dp)) {
      V3TextField(ui.name, onName, StrAccount.fullName, Modifier.v3Shake(ui.nameError), error = ui.nameError, required = true)
      Spacer(Modifier.height(14.dp))
      LockedField(Icons.Outlined.AlternateEmail, StrAccount.email, ui.email.ifBlank { StrAccount.emailNone }, ProfileEditModel.emailNote(ui.email), ltr = ui.email.isNotBlank())
      Spacer(Modifier.height(14.dp))
      if (ProfileEditModel.phoneLocked(ui.phone)) {
        LockedField(Icons.Outlined.Lock, StrAccount.phone, ui.phone, StrAccount.phoneLocked, ltr = true)
      } else {
        V3TextField(
          ui.newPhone, onNewPhone, StrAccount.phone, Modifier.v3Shake(ui.phoneError),
          kind = V3FieldKind.PHONE, placeholder = "07XXXXXXXX",
          helper = StrAccount.phoneOnceHelper, error = ui.phoneError,
        )
      }
    }

    V3Section(StrAccount.groupShop, Modifier.v3Stagger(stagger, "shop", 2), padding = PaddingValues(16.dp)) {
      V3TextField(ui.shopName, onShopName, StrAccount.shopName, helper = StrAccount.shopNameHelper)
    }

    V3Section(StrAccount.groupMembership, Modifier.v3Stagger(stagger, "member", 3)) {
      V3ListRow(
        StrAccount.memberFor,
        icon = Icons.Outlined.Schedule,
        value = AccountFormat.span(ui.createdAt),
        subtitle = if (ui.createdAt > 0) StrAccount.memberSince(AccountFormat.date(ui.createdAt)) else StrAccount.createdUnknown,
        chevron = false,
      )
    }
    Spacer(Modifier.height(24.dp))
  }
}

/** چیزی که هست ولی عوض نمی‌شود: سطحِ درونی، قفل، و راهنمای زیرش */
@Composable
private fun LockedField(icon: ImageVector, label: String, value: String, note: String, ltr: Boolean) {
  val c = PhoneV3.colors
  val shape = RoundedCornerShape(10.dp)
  Column {
    Text(label, style = V3Type.subhead, color = c.inkMuted, modifier = Modifier.padding(start = 2.dp, bottom = 6.dp))
    Row(
      Modifier.fillMaxWidth().clip(shape).background(c.tile).border(1.dp, c.hairline, shape).padding(horizontal = 14.dp, vertical = 13.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(icon, contentDescription = null, tint = c.icon, modifier = Modifier.size(18.dp))
      Spacer(Modifier.width(10.dp))
      CompositionLocalProvider(LocalLayoutDirection provides if (ltr) LayoutDirection.Ltr else LocalLayoutDirection.current) {
        Text(value, style = V3Type.body, color = c.ink, modifier = Modifier.weight(1f))
      }
      Icon(Icons.Outlined.Lock, contentDescription = StrAccount.notEditable, tint = c.inkMuted, modifier = Modifier.size(15.dp))
    }
    Text(note, style = V3Type.footnote, color = c.inkMuted, modifier = Modifier.padding(start = 2.dp, top = 6.dp))
  }
}

/**
 *  مسیرِ `profile/edit`: نام و نامِ دکان همان لحظه روی گوشی (`SyncStore`،
 *  `store_name`)، و اگر آنتن بود `updateProfile` روی سرور؛ عکس فقط روی گوشی.
 */
@Composable
internal fun RouteScope.ProfileEditRoute() {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val state = remember { SyncStore(context) }
  val prefs = remember { context.getSharedPreferences("tohid", android.content.Context.MODE_PRIVATE) }
  var savedName by rememberSaveable { mutableStateOf(state.accountName) }
  var savedShop by rememberSaveable { mutableStateOf(prefs.getString("store_name", "").orEmpty()) }
  var name by rememberSaveable { mutableStateOf(savedName) }
  var shop by rememberSaveable { mutableStateOf(savedShop) }
  var claim by rememberSaveable { mutableStateOf("") }
  var phone by remember { mutableStateOf(state.accountPhone) }
  var email by remember { mutableStateOf(state.accountEmail) }
  var nameError by remember { mutableStateOf<String?>(null) }
  var phoneError by remember { mutableStateOf<String?>(null) }
  var busy by remember { mutableStateOf(false) }
  var version by remember { mutableIntStateOf(0) }
  var sheet by remember { mutableStateOf(false) }
  val accountId = state.accountId.ifBlank { "local" }

  val picker = rememberAvatarPicker { uri ->
    PhotoStore.saveAvatar(context, accountId, uri)
      .onSuccess { version++; toasts.show(StrAccount.photoSaved, V3Tone.SUCCESS) }
      .onFailure { toasts.show(StrAccount.photoFailed, V3Tone.ERROR) }
  }

  fun save() {
    val clean = name.trim()
    val step = ProfileEditModel.step(clean, claim.trim(), Backend.isReady(context), Backend.isOnline(context))
    if (step == ProfileEditModel.Step.NAME_MISSING) { nameError = StrAccount.nameRequired; return }
    //  نام و نامِ دکان همان لحظه روی گوشی — سربرگِ فاکتور و رسیدها همین را دارند
    prefs.edit().putString("store_name", shop.trim()).apply()
    state.accountName = clean
    savedName = clean; savedShop = shop.trim(); name = clean
    when (step) {
      ProfileEditModel.Step.LOCAL_ONLY -> { toasts.show(StrAccount.nameSavedOffline, V3Tone.INFO); back() }
      ProfileEditModel.Step.PHONE_NEEDS_INTERNET -> phoneError = StrAccount.phoneNeedsInternet
      ProfileEditModel.Step.SERVER -> {
        val wanted = claim.trim()
        busy = true
        scope.launch {
          Backend.account(context).updateProfile(clean, wanted)
            .onSuccess { done ->
              state.accountName = done.user.name
              state.accountEmail = done.user.email.orEmpty()
              state.accountPhone = done.user.phone.orEmpty()
              name = done.user.name; savedName = done.user.name
              email = done.user.email.orEmpty(); phone = done.user.phone.orEmpty(); claim = ""
              Haptics.success(context)
              toasts.show(ProfileEditModel.savedMessage(wanted), V3Tone.SUCCESS)
              busy = false
              back()
            }
            .onFailure { toasts.show(it.userMessage, V3Tone.ERROR); busy = false }
        }
      }
      ProfileEditModel.Step.NAME_MISSING -> Unit
    }
  }

  ProfileEditContent(
    ui = ProfileEditUi(
      accountId = accountId, avatarVersion = version,
      name = name, nameError = nameError, email = email, phone = phone,
      newPhone = claim, phoneError = phoneError, shopName = shop, createdAt = state.accountCreatedAt,
      busy = busy, dirty = ProfileEditModel.dirty(name, savedName, shop, savedShop, claim),
    ),
    onName = { name = it; nameError = null },
    onNewPhone = { claim = it; phoneError = null },
    onShopName = { shop = it },
    onPhoto = { sheet = true },
    onSave = ::save,
    onBack = backOrNull,
  )
  if (sheet) AvatarSourceSheet(picker) { sheet = false }
}
