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
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.account.AccountDeletion
import ir.vil3ntec.tohid.core.account.EmailClaimForm
import ir.vil3ntec.tohid.core.i18n.StrAccount
import ir.vil3ntec.tohid.core.i18n.StrShellPages
import ir.vil3ntec.tohid.core.net.ApiFailure
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
import ir.vil3ntec.tohid.ui.v3.V3TextButton
import ir.vil3ntec.tohid.ui.v3.V3TextField
import ir.vil3ntec.tohid.ui.v3.V3Type
import ir.vil3ntec.tohid.ui.v3.account.AccountFormat
import ir.vil3ntec.tohid.ui.v3.account.AccountPaths
import ir.vil3ntec.tohid.ui.v3.nav.RouteScope
import ir.vil3ntec.tohid.ui.v3.rememberV3Stagger
import ir.vil3ntec.tohid.ui.v3.v3Pressable
import ir.vil3ntec.tohid.ui.v3.v3Shake
import ir.vil3ntec.tohid.ui.v3.v3Stagger
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** فیلدهای فرمِ «ویرایش حساب و دکان» */
internal data class ProfileEditUi(
  val accountId: String,
  val avatarVersion: Int,
  val name: String,
  val nameError: String? = null,
  val email: String,
  /** «فقط ایمیل»: ایمیلی که حسابِ بی‌ایمیل می‌افزاید (کادر فقط وقتی `email` خالی است) */
  val newEmail: String = "",
  val emailError: String? = null,
  /** شمارهٔ تماس (راهِ ورود نیست؛ بی کد عوض/پاک) */
  val newPhone: String,
  val phoneError: String? = null,
  val shopName: String,
  val createdAt: Long,
  val busy: Boolean = false,
  val dirty: Boolean = false,
  /** AUTH-08 ⇐ ایمیل: کادرِ «کدِ همان ایمیل» — فقط وقتی سرور کد خواست (`email_code_required`) */
  val codeShown: Boolean = false,
  val code: String = "",
  val codeError: String? = null,
  /** ثانیه‌های ماندهٔ «ارسالِ دوباره» از مهلتِ پاسخِ سرور */
  val resendWait: Int = 0,
)

/** فرمِ V3: عکس با نشانِ دوربین، گروه‌های حساب/دکان/عضویت، دکمهٔ چسبانِ «ذخیره تغییرات» */
@Composable
internal fun ProfileEditContent(
  ui: ProfileEditUi,
  onName: (String) -> Unit,
  onNewPhone: (String) -> Unit,
  onNewEmail: (String) -> Unit = {},
  onShopName: (String) -> Unit,
  onPhoto: () -> Unit,
  onSave: () -> Unit,
  onBack: (() -> Unit)?,
  onPrivacy: () -> Unit = {},
  onDelete: () -> Unit = {},
  onCode: (String) -> Unit = {},
  onResend: () -> Unit = {},
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
      if (ProfileEditModel.emailClaimable(ui.email)) {
        //  «فقط ایمیل»: حسابِ کهنهٔ بی‌ایمیل ایمیل را با کدِ همان ایمیل می‌افزاید (AUTH-08 ⇐ ایمیل)
        V3TextField(
          ui.newEmail, onNewEmail, StrAccount.email, Modifier.v3Shake(ui.emailError),
          kind = V3FieldKind.EMAIL, placeholder = "name@example.com",
          helper = ProfileEditModel.emailNote(ui.email), error = ui.emailError,
        )
        //  کدی که سرور به همین ایمیل فرستاد؛ ایمیل عوض شود ⇒ کادر بسته
        if (ui.codeShown) {
          Spacer(Modifier.height(14.dp))
          V3TextField(
            ui.code, onCode, StrShellPages.smsCodeLabel, Modifier.v3Shake(ui.codeError),
            kind = V3FieldKind.NUMBER, error = ui.codeError, imeAction = ImeAction.Done, onImeAction = onSave,
          )
          V3TextButton(
            ProfileEditModel.resendLabel(ui.resendWait), onResend, Modifier.padding(top = 4.dp),
            enabled = !ui.busy && ui.resendWait == 0,
          )
        }
      } else {
        LockedField(Icons.Outlined.AlternateEmail, StrAccount.email, ui.email, ProfileEditModel.emailNote(ui.email), ltr = true)
      }
      Spacer(Modifier.height(14.dp))
      //  شمارهٔ تماس: راهِ ورود نیست ⇒ همیشه آزاد، بی کد
      V3TextField(
        ui.newPhone, onNewPhone, StrAccount.phone, Modifier.v3Shake(ui.phoneError),
        kind = V3FieldKind.PHONE, placeholder = "07XXXXXXXX",
        helper = StrAccount.phoneOnceHelper, error = ui.phoneError,
      )
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

    //  قاعدهٔ Google Play: حریمِ خصوصی و «حذفِ حساب» در خودِ برنامه، پیدا و یک لمس دورتر
    V3Section(StrAccount.privacyPolicy, Modifier.v3Stagger(stagger, "privacy", 4)) {
      V3ListRow(StrAccount.privacyPolicy, subtitle = StrAccount.privacyLine, icon = Icons.Outlined.PrivacyTip, onClick = onPrivacy)
      V3ListRow(StrAccount.deleteAccount, subtitle = StrAccount.deleteAccountLine, icon = Icons.Outlined.DeleteForever, danger = true, onClick = onDelete)
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
 *
 *  «فقط ایمیل» (۱۴۰۵/۰۸/۲۳): شمارهٔ تماس بی کد با همان ذخیره می‌رود. AUTH-08 ⇐ ایمیل — حسابِ بی‌ایمیل ایمیل را با کدِ همان
 *  ایمیل می‌افزاید: `PUT /api/me` ⇐ `email_code_required` ⇐ `POST /api/me/email/code` ⇐ کادرِ کد ⇐ `PUT` با `emailCode`.
 *  گامِ بعد فقط از `core/account/EmailClaimForm`؛ سقفِ تلاش، فاصلهٔ ارسالِ دوباره و سقفِ روزانه همان سرور است.
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
  var typedPhone by rememberSaveable { mutableStateOf(state.accountPhone) }
  var email by remember { mutableStateOf(state.accountEmail) }
  var nameError by remember { mutableStateOf<String?>(null) }
  var phoneError by remember { mutableStateOf<String?>(null) }
  var emailError by remember { mutableStateOf<String?>(null) }
  var busy by remember { mutableStateOf(false) }
  var form by rememberSaveable(stateSaver = EmailFormSaver) { mutableStateOf(EmailClaimForm()) }
  var resendWait by remember { mutableIntStateOf(0) }
  //  شمارشِ «ارسالِ دوباره» از مهلتِ پاسخِ سرور، با هر کدِ تازه از نو
  LaunchedEffect(form.sent) {
    resendWait = form.resendSeconds
    while (resendWait > 0) { delay(1000); resendWait-- }
  }
  var version by remember { mutableIntStateOf(0) }
  var sheet by remember { mutableStateOf(false) }
  val accountId = state.accountId.ifBlank { "local" }
  val uri = LocalUriHandler.current

  val picker = rememberAvatarPicker { uri ->
    PhotoStore.saveAvatar(context, accountId, uri)
      .onSuccess { version++; toasts.show(StrAccount.photoSaved, V3Tone.SUCCESS) }
      .onFailure { toasts.show(StrAccount.photoFailed, V3Tone.ERROR) }
  }

  /** پاسخِ ناموفقی که پیامش را خودِ سرور داده */
  fun tell(what: EmailClaimForm.Next, f: ApiFailure, tone: V3Tone) {
    when (what) {
      EmailClaimForm.Next.SERVER_MESSAGE -> toasts.show(f.userMessage, tone)
      EmailClaimForm.Next.REQUEST_CODE, EmailClaimForm.Next.NONE -> Unit
    }
  }

  /** کدِ همان ایمیل (`POST /api/me/email/code`)؛ کادرِ کد باز می‌شود یا پیامِ سرور */
  fun requestCode(address: String) {
    busy = true
    scope.launch {
      val account = Backend.account(context)
      account.requestEmailCode(address)
        .onSuccess { seconds -> form = form.sent(address, seconds); toasts.show(StrShellPages.smsCodeSent, V3Tone.INFO) }
        .onFailure { f ->
          val (next, what) = form.sendFailed(f.code, address)
          form = next
          tell(what, f, V3Tone.WARNING)
        }
      busy = false
    }
  }

  fun save() {
    val clean = name.trim()
    val wanted = claim.trim()
    val phoneOut = if (ProfileEditModel.phoneChanged(typedPhone, phone)) typedPhone.trim() else null
    val step = ProfileEditModel.step(clean, wanted + (phoneOut?.let { "#$it" } ?: ""), Backend.isReady(context), Backend.isOnline(context))
    if (step == ProfileEditModel.Step.NAME_MISSING) { nameError = StrAccount.nameRequired; return }
    //  نام و نامِ دکان همان لحظه روی گوشی — سربرگِ فاکتور و رسیدها همین را دارند
    prefs.edit().putString("store_name", shop.trim()).apply()
    state.accountName = clean
    savedName = clean; savedShop = shop.trim(); name = clean
    when (step) {
      ProfileEditModel.Step.LOCAL_ONLY -> { toasts.show(StrAccount.nameSavedOffline, V3Tone.INFO); back() }
      ProfileEditModel.Step.NEEDS_INTERNET -> if (wanted.isNotBlank()) emailError = StrAccount.phoneNeedsInternet else phoneError = StrAccount.phoneNeedsInternet
      ProfileEditModel.Step.SERVER -> {
        //  کادرِ کد باز و کدِ نادرست ⇒ هیچ درخواستی، فقط خطای درجا
        val emailCode = form.codeToSend() ?: run { form = form.badCode(); return }
        busy = true
        scope.launch {
          val account = Backend.account(context)
          account.updateProfile(clean, phoneOut, wanted, emailCode)
            .onSuccess { done ->
              state.accountName = done.user.name
              state.accountEmail = done.user.email.orEmpty()
              state.accountPhone = done.user.phone.orEmpty()
              name = done.user.name; savedName = done.user.name
              email = done.user.email.orEmpty(); phone = done.user.phone.orEmpty(); typedPhone = phone
              claim = ""; form = EmailClaimForm()
              Haptics.success(context)
              toasts.show(ProfileEditModel.savedMessage(wanted), V3Tone.SUCCESS)
              busy = false
              back()
            }
            .onFailure { f ->
              busy = false
              val (next, what) = form.afterSave(f.code, wanted)
              form = next
              when (what) {
                EmailClaimForm.Next.REQUEST_CODE -> requestCode(wanted)
                else -> tell(what, f, V3Tone.ERROR)
              }
            }
        }
      }
      ProfileEditModel.Step.NAME_MISSING -> Unit
    }
  }

  ProfileEditContent(
    ui = ProfileEditUi(
      accountId = accountId, avatarVersion = version,
      name = name, nameError = nameError, email = email,
      newEmail = claim, emailError = emailError,
      newPhone = typedPhone, phoneError = phoneError, shopName = shop, createdAt = state.accountCreatedAt,
      busy = busy,
      dirty = ProfileEditModel.dirty(name, savedName, shop, savedShop, claim + if (ProfileEditModel.phoneChanged(typedPhone, phone)) "#" else ""),
      codeShown = form.shown, code = form.code, codeError = ProfileEditModel.codeError(form.problem), resendWait = resendWait,
    ),
    onName = { name = it; nameError = null },
    onNewPhone = { typedPhone = it; phoneError = null },
    onNewEmail = { claim = it; emailError = null; form = form.email(it) },
    onShopName = { shop = it },
    onPhoto = { sheet = true },
    onSave = ::save,
    onBack = backOrNull,
    onPrivacy = { runCatching { uri.openUri(AccountDeletion.PRIVACY_URL) } },
    onDelete = { nav.open(AccountPaths.DELETE) },
    onCode = { form = form.code(it) },
    onResend = { if (!busy && form.shown) requestCode(form.codeFor) },
  )
  if (sheet) AvatarSourceSheet(picker) { sheet = false }
}

/** حالِ کادرِ کد پس از چرخاندن: ایمیل و کدِ تایپ‌شده (مهلتِ ارسالِ دوباره از نو؛ سرور خودش می‌سنجد) */
private val EmailFormSaver = listSaver<EmailClaimForm, String>(
  save = { listOf(it.codeFor, it.code) },
  restore = { EmailClaimForm(codeFor = it[0], code = it[1]) },
)
