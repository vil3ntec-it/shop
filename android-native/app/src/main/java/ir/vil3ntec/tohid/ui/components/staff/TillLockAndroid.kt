package ir.vil3ntec.tohid.ui.components.staff

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.StrNav
import ir.vil3ntec.tohid.core.i18n.StrPos
import ir.vil3ntec.tohid.core.i18n.StrTill
import ir.vil3ntec.tohid.core.model.MemberDto
import ir.vil3ntec.tohid.core.perm.ApproverVault
import ir.vil3ntec.tohid.core.perm.PinGuard
import ir.vil3ntec.tohid.core.staff.TillSwitch
import ir.vil3ntec.tohid.data.CartStore
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.data.StaffPins
import ir.vil3ntec.tohid.data.TillUser
import ir.vil3ntec.tohid.plain
import ir.vil3ntec.tohid.ui.screens.AppLockScreen
import ir.vil3ntec.tohid.ui.screens.SegmentedChoice
import ir.vil3ntec.tohid.ui.theme.Radius
import ir.vil3ntec.tohid.ui.theme.Shop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 *  S46 (STF-04) — صفحهٔ قفلِ صندوق با PIN ِ شخصی، و بخشِ تنظیمِ «تعویضِ کاربر روی صندوق».
 *
 *  هر کس از فهرستِ کاربرانِ PIN‌دارِ همین دستگاه نامش را برمی‌گزیند و PIN ِ خودش را می‌زند؛ سنجش همان
 *  `ApproverVault.verify` (PBKDF2، ۵ خطا ⇒ قفل، شمارِ خطا نوشته می‌شود). «با رمزِ دستگاه» (اگر گذاشته شده)
 *  خودِ حسابِ دستگاه را برمی‌گرداند. باز شدن ⇒ `TillUser.unlock`: سبدها جابه‌جا و ردیفِ سابقه.
 */
@Composable
fun TillLockScreen(cart: CartStore, store: ShopStore, deviceCode: Boolean) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val pins = remember { StaffPins(context) }
  val device = remember { runCatching { ir.vil3ntec.tohid.sync.SyncStore(context).accountId }.getOrDefault("") }
  var users by remember { mutableStateOf(TillSwitch.lockList(TillUser.users(context), device)) }
  var who by remember { mutableStateOf(users.firstOrNull { !it.locked }?.userId ?: users.firstOrNull()?.userId.orEmpty()) }
  var error by remember(who) { mutableStateOf<String?>(null) }
  var busy by remember { mutableStateOf(false) }
  var byCode by remember { mutableStateOf(false) }
  if (byCode) {
    AppLockScreen { busy = true; scope.launch { TillUser.unlock(context, cart, store, null); busy = false } }
    return
  }
  val picked = users.firstOrNull { it.userId == who }
  Box(Modifier.fillMaxSize().background(Shop.colors.surface), contentAlignment = Alignment.Center) {
    Column(
      Modifier.widthIn(max = 420.dp).padding(18.dp).verticalScroll(rememberScrollState()),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Text(StrTill.locked, style = MaterialTheme.typography.titleLarge, color = Shop.colors.text)
      Spacer(Modifier.height(6.dp))
      Text(StrTill.who, style = MaterialTheme.typography.bodyMedium, color = Shop.colors.muted)
      Spacer(Modifier.height(12.dp))
      if (users.size > 1) {
        SegmentedChoice(
          options = users.map { it.userId to it.name.ifBlank { roleName(it.role) } },
          selected = who,
          onSelect = { if (!busy) who = it },
        )
        Spacer(Modifier.height(12.dp))
      }
      if (picked != null) {
        val name = picked.name.ifBlank { roleName(picked.role) }
        PinPad(
          title = StrPos.pinOf(name),
          hint = StrPos.sixDigitPinOf(name),
          error = if (picked.locked) StrTill.lockedUser(name) else error,
          locked = picked.locked,
          busy = busy,
          onComplete = { pin ->
            val r = pins.get(picked.userId) ?: return@PinPad
            busy = true
            scope.launch {
              //  PBKDF2 سنگین است؛ بیرون از رشتهٔ رابط
              val out = withContext(Dispatchers.Default) { ApproverVault.verify(r, pin) }
              pins.put(out.record)
              if (out.ok) {
                val opened = TillUser.user(out.record)
                TillUser.unlock(context, cart, store, opened)
                //  STF-04: سرور هم همان PIN را می‌سنجد و نوبتِ صندوق را باز می‌کند (پرچمِ `shop.till_pin`؛ بهترین‌کوشش)
                TillUser.serverVerify(context, opened, pin)
              } else {
                users = TillSwitch.lockList(TillUser.users(context), device)
                error = StrPos.pinWrong(plain(PinGuard.remaining(out.record.guard)))
              }
              busy = false
            }
          },
        )
      }
      if (deviceCode) {
        Spacer(Modifier.height(10.dp))
        TextButton(onClick = { byCode = true }, enabled = !busy) { Text(StrTill.deviceCode) }
      }
    }
  }
}

private fun roleName(role: String): String = when (role) {
  "owner" -> StrNav.roleOwner
  "manager" -> StrNav.roleManager
  else -> StrNav.roleStaff
}

/** «پشتِ صندوق: …» — فقط وقتی کسی جز حسابِ دستگاه پشتِ صندوق است */
@Composable
fun TillUserChip() {
  val context = LocalContext.current
  val u = TillUser.current(context) ?: return
  Text(
    StrTill.atTill(u.name.ifBlank { roleName(u.role) }),
    style = MaterialTheme.typography.labelMedium, color = Shop.colors.text,
    modifier = Modifier.background(Shop.colors.primaryTint, RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 2.dp),
  )
}

/**
 *  کاربرانِ PIN‌دارِ همین دستگاه، و «افزودن»: عضوی از فهرستِ اعضای دکان (از سرور، پس نام و نقش واقعی‌اند)
 *  که هنوز PIN ندارد، خودش PIN ِ تازه را دو بار می‌زند (`ApproverVault.create`، همان قاعدهٔ PIN ِ ساده‌نبودن).
 *  فقط کسی که تنظیمات را باز می‌کند (صاحب/مدیر) این‌جاست.
 */
@Composable
fun TillUsersBlock() {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val pins = remember { StaffPins(context) }
  var users by remember { mutableStateOf(TillUser.users(context)) }
  var members by remember { mutableStateOf<List<MemberDto>?>(null) }
  var picking by remember { mutableStateOf(false) }
  var enrolling by remember { mutableStateOf<MemberDto?>(null) }
  var note by remember { mutableStateOf<String?>(null) }
  val device = remember { runCatching { ir.vil3ntec.tohid.sync.SyncStore(context).accountId }.getOrDefault("") }
  val code = remember { runCatching { ir.vil3ntec.tohid.data.LockStore(context).enabled }.getOrDefault(false) }

  Text(StrTill.users, style = MaterialTheme.typography.labelMedium, color = Shop.colors.muted)
  if (users.isEmpty()) Text(StrTill.noUsers, style = MaterialTheme.typography.bodySmall, color = Shop.colors.muted)
  users.forEach { u ->
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
      Text(u.name.ifBlank { roleName(u.role) } + " · " + roleName(u.role), color = Shop.colors.text, modifier = Modifier.weight(1f))
      //  کلیدِ تأییدِ مدیر/صاحب از «کارمندان» برداشته می‌شود، نه این‌جا
      if (u.role !in ApproverVault.APPROVER_ROLES) TextButton(onClick = { pins.remove(u.userId); users = TillUser.users(context) }) { Text(StrTill.remove) }
    }
  }
  if (!TillSwitch.canLock(true, users, device, code) && users.isNotEmpty()) {
    Text(StrTill.needDeviceWay, style = MaterialTheme.typography.bodySmall, color = Shop.colors.danger)
  }
  note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Shop.colors.danger) }
  TextButton(onClick = {
    note = null
    scope.launch {
      ir.vil3ntec.tohid.data.repo.Backend.shop(context).members()
        .onSuccess { page -> members = page.members.filter { m -> m.status == "active" && m.userId.isNotBlank() && pins.get(m.userId) == null }; picking = true }
        .onFailure { note = StrTill.membersOffline }
    }
  }) { Text(StrTill.addUser) }

  val list = members
  if (picking && list != null) {
    Dialog(onDismissRequest = { picking = false }) {
      Surface(color = Shop.colors.surfaceSolid, shape = RoundedCornerShape(Radius.lg), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp)) {
          Text(StrTill.pickMember, style = MaterialTheme.typography.bodyMedium, color = Shop.colors.text)
          Spacer(Modifier.height(8.dp))
          list.forEach { m ->
            TextButton(onClick = { picking = false; enrolling = m }) { Text(m.name.ifBlank { m.contact } + " · " + roleName(m.role)) }
          }
          Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { TextButton(onClick = { picking = false }) { Text(Str.cancel) } }
        }
      }
    }
  }
  enrolling?.let { m ->
    ir.vil3ntec.tohid.ui.screens.PinSetupDialog(onDismiss = { enrolling = null }) { pin ->
      enrolling = null
      scope.launch {
        //  PIN فقط به اثرِ PBKDF2 تبدیل و نگه داشته می‌شود؛ خودش هیچ‌جا نوشته نمی‌شود
        val rec = withContext(Dispatchers.Default) { ApproverVault.create(m.userId, m.name.ifBlank { m.contact }, m.role, pin) }
        pins.put(rec)
        users = TillUser.users(context)
        //  STF-04: همان PIN در سرور هم (پرچمِ `shop.till_pin`) تا سرور بداند چه کسی پشتِ صندوق است
        if (!TillUser.serverEnroll(context, m.id, pin)) note = StrTill.serverEnrollFailed
      }
    }
  }
}
