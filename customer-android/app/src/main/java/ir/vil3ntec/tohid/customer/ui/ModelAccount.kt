package ir.vil3ntec.tohid.customer.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ir.vil3ntec.tohid.core.market.PublicProduct
import ir.vil3ntec.tohid.core.shopper.AccountFeatures
import ir.vil3ntec.tohid.core.shopper.AccountPulls
import ir.vil3ntec.tohid.core.shopper.AccountReply
import ir.vil3ntec.tohid.core.shopper.AccountRules
import ir.vil3ntec.tohid.core.shopper.AccountSession
import ir.vil3ntec.tohid.core.shopper.ListItem
import ir.vil3ntec.tohid.core.shopper.ShopperError
import ir.vil3ntec.tohid.core.shopper.ShopperJson
import ir.vil3ntec.tohid.core.shopper.ShopperKeyValue
import ir.vil3ntec.tohid.core.shopper.ShopperMemory
import ir.vil3ntec.tohid.core.shopper.ShopperResult
import ir.vil3ntec.tohid.core.shopper.ShopperText
import ir.vil3ntec.tohid.core.shopper.StorePage
import ir.vil3ntec.tohid.core.text.LocaleFormat

/*
 *  برنامهٔ خرید — حسابِ مشتری، ♥ و «فهرستِ خرید» کنارِ [CustomerModel] (فایلِ جدا تا مدل از سقفِ اندازه نگذرد).
 *  قاعده‌ها و صف‌ها همه در core ([AccountRules]، همان درها و ادغامِ وب)؛ این‌جا فقط ترتیبِ درخواست‌ها و حالِ صفحه.
 */

/**
 *  حالِ حساب در همین اجرا. ⛔ نشست فقط در [secrets] (نگه‌دارِ رمزشدهٔ سکو: Keystore ِ اندروید)؛ null ⇒ فقط در حافظهٔ
 *  همین اجرا (آیفون تا Keychain، و آزمون).
 */
class AccountBox(private val secrets: ShopperKeyValue?) {
  var session: AccountSession? by mutableStateOf(load())
    private set
  var phone: String by mutableStateOf("")
  var code: String by mutableStateOf("")
  var codeSent: Boolean by mutableStateOf(false)
    internal set
  var busy: Boolean by mutableStateOf(false)
    internal set
  var note: String? by mutableStateOf(null)
    internal set
  /** کالاهای امروزِ پاسخ‌ها برای نامِ ♥ و فهرست (کلید: دکان + کالا؛ شناسهٔ کالا فقط درونِ یک دکان یکتاست) */
  var products: Map<String, PublicProduct> by mutableStateOf(emptyMap())
    internal set
  internal var running = false
  internal var pulls = AccountPulls()
  internal var features: Map<String, AccountFeatures> = emptyMap()

  private fun load(): AccountSession? = try {
    secrets?.get(KEY)?.let { ShopperJson.decodeFromString(AccountSession.serializer(), it) }
  } catch (_: Exception) {
    null
  }

  internal fun keep(s: AccountSession?) {
    session = s
    try {
      secrets?.put(KEY, s?.let { ShopperJson.encodeToString(AccountSession.serializer(), it) })
    } catch (_: Exception) {
      //  نگه‌دارِ امن در دسترس نیست ⇒ فقط همین اجرا
    }
  }

  fun live(now: Long): AccountSession? = session?.takeIf { it.expiresAt > now }

  internal fun learn(slug: String, list: List<PublicProduct>) {
    if (list.isNotEmpty()) products = products + list.associateBy { key(slug, it.id) }
  }

  companion object {
    const val KEY = "shopper.session.v1"
    fun key(slug: String, id: String) = slug + "\n" + id
  }
}

private const val MAX_STEPS = 60

fun CustomerModel.signedIn(): AccountSession? = account.live(clock())

/** ورود ۱: کدِ واقعیِ سرور به همان شماره (`/verify/start`)؛ سرورِ بی راهِ پیامک ⇒ `verification_unavailable` (D-09) */
fun CustomerModel.accountSendCode(slug: String) {
  val phone = LocaleFormat.normalizePhone(account.phone) ?: run { account.note = ShopperText.error(badPhone()); return }
  account.busy = true
  request({ api.verifyStart(slug, phone) }) { r ->
    account.busy = false
    account.codeSent = r is ShopperResult.Ok
    account.note = r.errorOrNull()?.let { ShopperText.error(it) } ?: ShopperText.codeSent
  }
}

/** ورود ۲: کد ⇒ بلیتِ تازه ⇒ نشست (`POST /:slug/account`)؛ سپس دورِ کاملِ هم‌گامی (ادغامِ نخستینِ همین نشست) */
fun CustomerModel.accountSignIn(slug: String) {
  val phone = LocaleFormat.normalizePhone(account.phone) ?: return
  val digits = LocaleFormat.asciiDigits(account.code).filter { it in '0'..'9' }
  account.busy = true
  request({ api.verifyCheck(slug, phone, digits) }) { r ->
    val ticket = r.valueOrNull()
    if (ticket == null) {
      account.busy = false
      account.note = r.errorOrNull()?.let { ShopperText.error(it) }
      return@request
    }
    persist(saved.copy(ticket = ticket))
    request({ api.signIn(slug, ticket.ticket, saved.name) }) { s ->
      account.busy = false
      val session = s.valueOrNull()
      if (session == null) {
        account.note = s.errorOrNull()?.let { ShopperText.error(it) }
        return@request
      }
      account.keep(session)
      account.code = ""
      account.codeSent = false
      account.note = ShopperText.signedIn
      syncAccount(slug, AccountPulls.ALL)
    }
  }
}

/** خروج: نشست در سرور بسته (بهترین تلاش) و صف‌ها پاک؛ سبد، ♥ و فهرست روی همین دستگاه می‌مانند */
fun CustomerModel.accountSignOut() {
  val s = account.session ?: return
  val slug = storeSlug.ifBlank { saved.recent.firstOrNull()?.slug.orEmpty() }
  if (slug.isNotBlank()) request({ api.signOut(slug, s.session) }) { }
  account.keep(null)
  persist(AccountRules.signedOut(saved))
  account.note = null
}

/** دکان باز شد: درهای روشنِ همین دکان، نامِ ♥ و قلم‌های فهرست از کاتالوگِ امروز، و دورِ کاملِ هم‌گامی */
fun CustomerModel.enteredStore(slug: String, page: StorePage) {
  val f = page.features
  account.features = account.features + (slug to AccountFeatures(favorites = f.favorites, cart = f.webAccount && f.accountCart))
  if (f.favorites) {
    val need = (AccountRules.favsOf(saved, slug) + AccountRules.listFor(saved, slug).map { it.id })
      .distinct().filter { AccountBox.key(slug, it) !in account.products }
    need.chunked(100).forEach { ids ->
      request({ api.productsByIds(slug, ids) }) { r -> r.valueOrNull()?.let { account.learn(slug, it.items) } }
    }
  }
  syncAccount(slug, AccountPulls.ALL)
}

/** دورِ هم‌گامیِ [slug]: یک درخواست در هر بار و حالِ **اکنون** پس از هر پاسخ (کارِ تازه در میانه گم نمی‌شود) */
fun CustomerModel.syncAccount(slug: String, pulls: AccountPulls = AccountPulls()) {
  account.pulls = account.pulls + pulls
  if (account.running || slug.isBlank()) return
  accountStep(slug, 0)
}

private fun CustomerModel.accountStep(slug: String, n: Int) {
  val s = account.live(clock())
  val f = account.features[slug]
  if (s == null || f == null || n >= MAX_STEPS) {
    account.running = false
    return
  }
  val begun = AccountRules.begin(saved, slug, s.expiresAt)
  if (begun !== saved) persist(begun)
  val step = AccountRules.next(saved, slug, f, account.pulls)
  if (step == null) {
    accountIdle(slug)
    return
  }
  account.running = true
  request({ ShopperResult.Ok(AccountRules.send(api, s.session, slug, step)) }) { r ->
    val reply = r.valueOrNull() ?: AccountReply()
    val out = AccountRules.apply(saved, slug, step, reply, clock())
    persist(out.state)
    account.pulls = AccountRules.after(account.pulls, step, reply)
    account.learn(slug, out.products)
    if (out.signedOut) account.keep(null)
    if (out.go) accountStep(slug, n + 1) else accountIdle(slug)
  }
}

/** دورِ این دکان تمام شد؛ اگر در میانه دکانِ دیگری باز شد، نوبتِ آن */
private fun CustomerModel.accountIdle(slug: String) {
  account.running = false
  if (storeSlug.isNotBlank() && storeSlug != slug) syncAccount(storeSlug)
}

// ── ♥ ──

fun CustomerModel.isFav(slug: String, id: String): Boolean = id in AccountRules.favsOf(saved, slug)

fun CustomerModel.toggleFav(slug: String, p: PublicProduct) {
  val was = isFav(slug, p.id)
  account.learn(slug, listOf(p))
  persist(AccountRules.toggleFav(saved, slug, p.id))
  notice = if (was) ShopperText.favRemoved(p.name) else ShopperText.favAdded(p.name)
  syncAccount(slug)
}

/** ♥ ِ همین دکان که کالایش امروز در ویترین دیده شده (کالای پنهان نیست ولی ♥ اش می‌ماند، مثلِ وب) */
fun CustomerModel.favItems(slug: String): List<PublicProduct> {
  val here = items.associateBy { it.id }
  return AccountRules.favsOf(saved, slug).mapNotNull { here[it] ?: account.products[AccountBox.key(slug, it)] }
}

// ── «فهرستِ خرید» ──

/** قلم‌های فهرست با نام و واحدِ امروز؛ کالای نبوده دیده نمی‌شود ولی در فهرست می‌ماند */
fun CustomerModel.listLines(slug: String): List<Triple<ListItem, String, String>> {
  val here = items.associateBy { it.id }
  val cart = cartOf(slug).lines.associateBy { it.productId }
  return AccountRules.listFor(saved, slug).mapNotNull { x ->
    val p = here[x.id] ?: account.products[AccountBox.key(slug, x.id)]
    when {
      p != null -> Triple(x, p.name, p.unit)
      cart[x.id] != null -> Triple(x, cart.getValue(x.id).name, cart.getValue(x.id).unit)
      else -> null
    }
  }
}

fun CustomerModel.saveCartAsList(slug: String) {
  persist(AccountRules.saveCart(saved, slug, cartOf(slug)))
  notice = ShopperText.listSaved
  syncAccount(slug)
}

fun CustomerModel.removeFromList(slug: String, id: String) {
  persist(AccountRules.removeFromList(saved, slug, id))
  syncAccount(slug)
}

fun CustomerModel.clearList(slug: String) {
  persist(AccountRules.clearList(saved, slug))
  notice = ShopperText.listCleared
  syncAccount(slug)
}

/** «همه به سبد»: قیمت، سقف و موجودی فقط از پاسخِ امروزِ `?ids=` (هرگز از فهرست) */
fun CustomerModel.listToCart(slug: String) {
  val list = AccountRules.listFor(saved, slug)
  if (list.isEmpty()) return
  request({ api.productsByIds(slug, list.map { it.id }) }) { r ->
    val page = r.valueOrNull()
    if (page == null) {
      notice = r.errorOrNull()?.let { ShopperText.error(it) }
      return@request
    }
    account.learn(slug, page.items)
    val (c, gone) = AccountRules.listToCart(cartOf(slug), list, page.items, clock())
    persist(ShopperMemory.withCart(saved, c))
    syncAccount(slug)
    if (gone > 0) notice = ShopperText.repeatGone(gone)
  }
}

private fun badPhone() = ShopperError(400, "bad_phone")
