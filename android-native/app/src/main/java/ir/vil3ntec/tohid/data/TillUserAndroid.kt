package ir.vil3ntec.tohid.data

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import ir.vil3ntec.tohid.core.perm.ApproverVault
import ir.vil3ntec.tohid.core.staff.TillSwitch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 *  کاربرِ صندوق — S46، STF-04 (قاعده‌ها در `core/staff/TillSwitch`). فایلِ مرزیِ پلتفرم: تنظیم، کاربرِ فعلی،
 *  پرونده‌های سبدِ کنارگذاشته و ردیفِ سابقه.
 *
 *  ⛔ پشتِ تنظیمِ همین دستگاه (`tohid-ui:till_switch`)، پیش‌فرض **خاموش**: خاموش ⇒ هیچ کاربرِ صندوقی نیست،
 *  [role] همان نقشِ حساب، [me] همان `SyncStore.accountName` و Ctrl+L همان قفلِ امروز (رمزِ دستگاه).
 *
 *  PIN همان `ApproverVault` ِ `StaffPins` است (PBKDF2، نمک، ۵ خطا ⇒ قفل) — هیچ PIN ِ خامی جایی نوشته نمی‌شود.
 *  هر کاربر سبدِ خودش را دارد: سبدِ کاربرِ پیشین در `files/till-carts` کنار می‌رود و سبدِ کاربرِ تازه برمی‌گردد.
 */
object TillUser {
  private const val PREFS = "tohid-ui"
  private const val KEY = "till_switch"
  private const val ACTIVE = "till_active"
  private const val DIR = "till-carts"

  val on = mutableStateOf(false)

  /** کاربرِ صندوقِ فعلی؛ `null` = خودِ حسابِ دستگاه */
  val active = mutableStateOf<TillSwitch.User?>(null)

  /** صندوق با PIN ِ شخصی قفل است (Ctrl+L) */
  val locked = mutableStateOf(false)

  @Volatile private var loaded = false

  fun load(context: Context) {
    if (loaded) return
    loaded = true
    val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    on.value = p.getBoolean(KEY, false)
    //  کاربرِ صندوق پس از بازکردنِ دوبارهٔ برنامه همان می‌ماند — بستنِ برنامه اختیارِ حسابِ دستگاه را برنمی‌گرداند
    active.value = p.getString(ACTIVE, null)?.let { id -> StaffPins(context).get(id)?.let(::user) }
  }

  fun set(context: Context, value: Boolean) {
    load(context)
    on.value = value
    if (!value) locked.value = false
    context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY, value).apply()
    ShopRole.policyVersion.value++
  }

  fun user(r: ApproverVault.Record): TillSwitch.User = TillSwitch.User(r.userId, r.name, r.role, r.locked)

  /** کاربرِ صندوق، فقط با تنظیمِ روشن */
  fun current(context: Context): TillSwitch.User? { load(context); return if (on.value) active.value else null }

  /** نقشی که برنامه به‌کار می‌برد (`ShopRole.current`) — کاربرِ صندوق هرگز بالاتر از حسابِ دستگاه */
  fun role(context: Context, account: String): String = TillSwitch.effectiveRole(account, current(context)?.role)

  /** نامِ فروشنده و نویسندهٔ سابقه */
  fun me(context: Context): String =
    TillSwitch.seller(runCatching { ir.vil3ntec.tohid.sync.SyncStore(context).accountName }.getOrDefault(""), current(context))

  /** شناسهٔ همان کس — درخواست‌کنندهٔ برگهٔ تأیید */
  fun meId(context: Context): String =
    current(context)?.userId ?: runCatching { ir.vil3ntec.tohid.sync.SyncStore(context).accountId }.getOrDefault("")

  fun users(context: Context): List<TillSwitch.User> = StaffPins(context).all().map(::user)

  /** Ctrl+L: `true` ⇒ قفلِ PIN ِ شخصی افتاد؛ `false` ⇒ همان قفلِ امروز */
  fun lock(context: Context, deviceCode: Boolean): Boolean {
    load(context)
    val device = runCatching { ir.vil3ntec.tohid.sync.SyncStore(context).accountId }.getOrDefault("")
    if (!TillSwitch.canLock(on.value, users(context), device, deviceCode)) return false
    locked.value = true
    return true
  }

  /**
   *  صندوق با PIN ِ [who] باز شد (`null` = رمزِ دستگاه ⇒ خودِ حسابِ دستگاه): سبدها جابه‌جا، کاربرِ تازه
   *  نوشته و یک ردیفِ سابقه با شناسه و نامِ همان کس.
   */
  suspend fun unlock(context: Context, cart: HeldCart, store: ShopStore, who: TillSwitch.User?) {
    load(context)
    val device = runCatching { ir.vil3ntec.tohid.sync.SyncStore(context).accountId }.getOrDefault("")
    val next = TillSwitch.activeAfter(device, who?.userId)
    TillSwitch.swap(active.value?.userId, next)?.let { swapCarts(context, cart, it) }
    active.value = if (next == null) null else who
    context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
      .apply { if (next == null) remove(ACTIVE) else putString(ACTIVE, next) }.commit()
    locked.value = false
    ShopRole.policyVersion.value++
    val name = me(context)
    val id = who?.userId ?: device
    runCatching {
      store.update { d ->
        d.copy(auditLog = d.auditLog + AuditEntry(
          id = auditId(), type = AUDIT_TYPE, date = ir.vil3ntec.tohid.todayIso(), refId = id,
          notes = "صندوق با PIN ِ «$name» باز شد", createdAt = System.currentTimeMillis(), by = name,
        ))
      }
    }
  }

  const val AUDIT_TYPE = "till_user"

  private suspend fun swapCarts(context: Context, cart: HeldCart, s: TillSwitch.Swap) = withContext(Dispatchers.IO) {
    val dir = File(TrainingLedger.filesDir(context), DIR).apply { mkdirs() }
    val mine = cart.snapshot()
    val out = File(dir, TillSwitch.fileName(s.stash))
    if (mine.lines.isNotEmpty()) DiskWrite.text(out, SaleDraftCodec.encode(mine)) else out.delete()
    val back = File(dir, TillSwitch.fileName(s.restore))
    val theirs = if (back.isFile) SaleDraftCodec.decode(runCatching { back.readText(Charsets.UTF_8) }.getOrNull()) else null
    cart.freeze(true)
    try { cart.replace(theirs) } finally { cart.freeze(false) }
    back.delete()
  }
}
