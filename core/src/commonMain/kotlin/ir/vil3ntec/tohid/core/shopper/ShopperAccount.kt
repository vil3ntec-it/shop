package ir.vil3ntec.tohid.core.shopper

import ir.vil3ntec.tohid.core.market.PublicProduct
import ir.vil3ntec.tohid.core.market.StockBadge
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/*
 *  برنامهٔ خرید — حسابِ مشتری: سبد، ♥ و «فهرستِ خرید» همراهِ حساب (WEB-11، CHK-03) با **همان درها و همان قاعده‌های
 *  ادغامِ وب** (`server/public/store/catalog.js` و `acct.js`):
 *    سبد ── ماشینِ [CartSync] (CHK-03): نخستین بارِ هر نشست ادغامِ سبدِ مهمان (`/account/cart/merge`، بیشینه؛ سبدِ خالی ⇒
 *           فقط خواندن)، سپس حساب جای سبدِ دستگاه؛ تغییرهای دستگاه پس از سکوت یک جایگزینی با نسخهٔ دیده‌شده (۴۰۹ ⇒ همان
 *           تغییرِ دستگاه روی سبدِ تازه، یک بار)؛ پر ⇒ سبدِ دستگاه؛ خروج ⇒ سبدِ حساب از دستگاه پاک.
 *    ♥ ──── نخستین بارِ هر نشست همهٔ ♥ ِ دستگاه (کهنه‌ترین اول) در صف؛ هر ♥ یک کار؛ صف خالی ⇒ حساب حرفِ آخر.
 *    فهرست ─ ادغام، سپس هر تغییر یک کار («همه» یا «برداشتنِ یکی») روی نسخهٔ دیده‌شده؛ ۴۰۹ ⇒ همان کار روی فهرستِ تازه؛
 *           صف خالی ⇒ حساب حرفِ آخر؛ پر ⇒ فهرستِ دستگاه جای حساب؛ `too_many_lists` ⇒ فقط روی دستگاه.
 *  ⛔ قیمت، نام و موجودی همیشه از پاسخِ امروزِ سرور؛ فهرست فقط شناسه و مقدار. بی نشست ⇒ همه فقط روی دستگاه.
 *  ⛔ ورود فقط با بلیتِ تازهٔ کدِ واقعیِ سرور به **ایمیل** (`POST /:slug/account`؛ «فقط ایمیل»)؛ سرورِ بی راهِ ایمیل ⇒ ۵۰۳،
 *     هرگز کدِ ساختگی. شمارهٔ حساب فقط تماسِ پیک است.
 */

@Serializable
data class AccountCustomer(val id: String = "", val email: String? = null, val phone: String? = null, val name: String = "")

/** نشستِ حساب (`sc1.…`) — پاسخِ `POST /:slug/account`. ⛔ فقط در نگه‌دارِ رمزشدهٔ سکو، هرگز در [ShopperState]. */
@Serializable
data class AccountSession(val session: String, val expiresAt: Long, val customer: AccountCustomer = AccountCustomer())

/** قلمِ نوشتنی (سبد با یادداشت، فهرست بی یادداشت) */
@Serializable
data class AccountItem(val productId: String, val qtyMilli: Long, val note: String? = null)

@Serializable
internal data class AccountWrite(val version: Long? = null, val items: List<AccountItem>)

@Serializable
data class AccountCartLine(
  val productId: String,
  val qtyMilli: Long,
  val note: String = "",
  val available: Boolean = false,
  val name: String? = null,
  val unit: String? = null,
  val priceMinor: Long? = null,
  val maxQtyMilli: Long? = null,
  /** نشانِ موجودیِ امروز (`publicStock`)؛ «ناموجود»/«قابلِ سفارش نیست» ⇒ قلمِ دستگاه خط‌خورده ([CartSync.orderable]) */
  val stock: StockBadge? = null,
)

@Serializable
data class AccountCart(val version: Long = 0, val lines: List<AccountCartLine> = emptyList())

@Serializable
data class AccountListLine(val productId: String, val qtyMilli: Long, val available: Boolean = false)

/** «فهرستِ خرید» ِ حساب: قلم‌ها و کالاهای همین حالا در ویترین (قیمتِ امروز) */
@Serializable
data class AccountList(val version: Long = 0, val lines: List<AccountListLine> = emptyList(), val items: List<PublicProduct> = emptyList())

@Serializable
data class FavoritesPage(val items: List<PublicProduct> = emptyList(), val max: Int = 200)

/** قلمِ «فهرستِ خرید» روی دستگاه — فقط شناسه و مقدار (همان `{id, q}` ِ وب)، هرگز قیمت */
@Serializable
data class ListItem(val id: String, val q: Long)

@Serializable
data class FavOp(val id: String, val add: Boolean)

/** کارِ صفِ فهرست: «همه» ([all]) یا «برداشتنِ یکی» ([rm]) — همان `['all', …]`/`['rm', id]` ِ وب */
@Serializable
data class ListOp(val all: List<ListItem>? = null, val rm: String? = null)

/** هم‌گامیِ یک دکان در یک نشست ([t] = `expiresAt` ِ همان نشست؛ نشستِ تازه ⇒ ادغامِ تازه) */
@Serializable
data class AccountSync(
  val t: Long = 0,
  val favOps: List<FavOp> = emptyList(),
  val listV: Long = 0,
  val listS: List<ListItem> = emptyList(),
  val listM: Boolean = false,
  val listOps: List<ListOp> = emptyList(),
  val listOff: Boolean = false,
  val cartV: Long = 0,
  val cartK: String = "",
  val cartM: Boolean = false,
  /** یادداشتِ قلم‌های سبدِ حساب (از وب) تا نوشتنِ برنامه آن را پاک نکند */
  val cartNotes: Map<String, String> = emptyMap(),
  /** CHK-03: قلم‌های سبدِ حساب در آخرین پاسخ — پایهٔ [CartSync.rebase] پس از ۴۰۹ */
  val cartB: List<AccountItem> = emptyList(),
  /** ۴۰۹ ِ `cart_changed` ِ پشتِ‌هم (سقف [CartSync.CONFLICT_RETRIES] در یک دور) */
  val cartC: Int = 0,
  /** شکستِ گذرای پشتِ‌هم و «نه پیش از» ِ دورِ بعد ([CartSync.backoffMs]) */
  val fails: Int = 0,
  val retryAt: Long = 0,
)

/** درهای روشنِ همین دکان (`features` ِ `GET /:slug`) */
data class AccountFeatures(
  val favorites: Boolean = false,
  val cart: Boolean = false,
  /** CRM-09: دفترِ نشانیِ حساب ([AddressSync]) و رسانهٔ آن در همین دکان */
  val addresses: Boolean = false,
  val addressMedia: Boolean = false,
)

/** چه چیزی هنوز در این دور از سرور تازه خوانده نشده (ورود به دکان، برگشتِ برنامه ⇒ هر سه) */
data class AccountPulls(val cart: Boolean = false, val fav: Boolean = false, val list: Boolean = false) {
  operator fun plus(o: AccountPulls) = AccountPulls(cart || o.cart, fav || o.fav, list || o.list)

  companion object {
    val ALL = AccountPulls(cart = true, fav = true, list = true)
  }
}

sealed class AccountStep {
  data class CartMerge(val items: List<AccountItem>) : AccountStep()
  data class CartPut(val version: Long, val items: List<AccountItem>) : AccountStep()
  data class CartPull(val mark: String) : AccountStep()
  data class Fav(val op: FavOp) : AccountStep()
  object FavPull : AccountStep()
  data class ListMerge(val items: List<ListItem>) : AccountStep()
  data class ListPut(val op: ListOp, val version: Long, val items: List<ListItem>) : AccountStep()
  object ListPull : AccountStep()
}

/** پاسخِ یک کار — فقط یکی از سه پر است */
class AccountReply(
  val cart: ShopperResult<AccountCart>? = null,
  val favs: ShopperResult<FavoritesPage>? = null,
  val fav: ShopperResult<Boolean>? = null,
  val list: ShopperResult<AccountList>? = null,
) {
  val error: ShopperError? get() = cart?.errorOrNull() ?: favs?.errorOrNull() ?: fav?.errorOrNull() ?: list?.errorOrNull()
}

/**
 *  حالِ تازه؛ [go] ⇒ کارِ بعد؛ [products] کالاهای امروزِ پاسخ (برای نمایش)؛ [signedOut] ⇒ نشست دیگر کار نمی‌کند (۴۰۱)؛
 *  [cartOff] ⇒ درِ سبد در این دکان نیست ([CartSync.doorClosed]): فراخوان سبدِ همین دکان را خاموش می‌کند و اگر بخواهد ادامه می‌دهد؛
 *  [repriced]/[gone] ⇒ شمارِ قلم‌های سبدِ دستگاه با قیمتِ تازه یا دیگر-نیست پس از پاسخِ سرور (برای پیام).
 */
data class AccountOutcome(
  val state: ShopperState,
  val go: Boolean,
  val products: List<PublicProduct> = emptyList(),
  val signedOut: Boolean = false,
  val cartOff: Boolean = false,
  val repriced: Int = 0,
  val gone: Int = 0,
)

object AccountRules {
  const val MAX_FAVS = 200
  const val MAX_LIST = CartRules.MAX_LINES
  const val MAX_SHOPS = 30
  private val ID = Regex("^[A-Za-z0-9_.:-]{1,80}$")

  /** شمارهٔ خودِ مشتری، پوشیده (CRM-15): `+93791114888` ⇒ `0791•••888` */
  fun maskPhone(e164: String): String {
    val local = if (e164.startsWith("+93")) "0" + e164.removePrefix("+93") else e164
    return if (local.length <= 7) local else local.take(4) + "•••" + local.takeLast(3)
  }

  /** ایمیلِ خودِ مشتری، پوشیده (CRM-15): `ali.r@x.af` ⇒ `a••••@x.af` (همان `customer-contact.maskPhone` ِ سرور برای ایمیل) */
  fun maskEmail(email: String): String {
    val at = email.indexOf('@')
    return if (at <= 0) email else email.take(1) + "•".repeat(maxOf(1, at - 1)) + email.substring(at)
  }

  fun cleanFavs(ids: List<String>): List<String> = ids.filter { ID.matches(it) }.distinct().take(MAX_FAVS)

  fun cleanList(xs: List<ListItem>): List<ListItem> =
    xs.filter { ID.matches(it.id) && it.q > 0 && it.q <= CartRules.MAX_QTY_MILLI }.distinctBy { it.id }.take(MAX_LIST)

  fun favsOf(s: ShopperState, slug: String): List<String> = s.favs[slug].orEmpty()

  fun listFor(s: ShopperState, slug: String): List<ListItem> = s.lists[slug].orEmpty()

  private fun <T> withKey(m: Map<String, List<T>>, slug: String, v: List<T>): Map<String, List<T>> =
    if (v.isEmpty()) m - slug else (mapOf(slug to v) + (m - slug)).entries.take(MAX_SHOPS).associate { it.key to it.value }

  /**
   *  نشستِ تازه (یا نخستین دورِ این دکان) ⇒ ♥ ِ دستگاه، کهنه‌ترین اول، در صف؛ فهرست و سبد «هنوز ادغام نشده». سبدِ دستگاهی که
   *  هنوز آینهٔ نشستِ پیشین است (شاید حسابِ دیگر) ادغام نمی‌شود: کنار می‌رود (CHK-03).
   */
  fun begin(s: ShopperState, slug: String, expiresAt: Long): ShopperState {
    val st = s.sync[slug]
    if (st != null && st.t == expiresAt) return s
    val base = if (st != null && st.cartM) s.copy(carts = s.carts.filter { it.slug != slug }) else s
    val fresh = AccountSync(t = expiresAt, favOps = favsOf(base, slug).reversed().map { FavOp(it, true) })
    return base.copy(sync = base.sync + (slug to fresh))
  }

  /**
   *  خروج ⇒ همهٔ صف‌ها و نسخه‌ها پاک؛ سبدی که آینهٔ حساب بود هم ([CartSync.forget]) و نشانی‌های حساب ([AddressSync.signedOut])؛
   *  ♥، فهرست، سبدِ مهمان و نشانی‌های فقط‌دستگاهی روی دستگاه می‌مانند
   */
  fun signedOut(s: ShopperState): ShopperState = AddressSync.signedOut(CartSync.forget(s).copy(sync = emptyMap()))

  // ── کارهای دستگاه ──

  fun toggleFav(s: ShopperState, slug: String, id: String): ShopperState {
    if (!ID.matches(id)) return s
    val cur = favsOf(s, slug)
    val add = id !in cur
    val next = if (add) (listOf(id) + cur).take(MAX_FAVS) else cur - id
    val st = s.sync[slug]
    val sync = if (st == null) s.sync else s.sync + (slug to st.copy(favOps = st.favOps.filter { it.id != id } + FavOp(id, add)))
    return s.copy(favs = withKey(s.favs, slug, next), sync = sync)
  }

  private fun setList(s: ShopperState, slug: String, items: List<ListItem>, op: ListOp): ShopperState {
    val st = s.sync[slug]
    val sync = if (st == null || st.listOff) s.sync
      else s.sync + (slug to st.copy(listOps = if (op.all != null) listOf(op) else st.listOps + op))
    return s.copy(lists = withKey(s.lists, slug, items), sync = sync)
  }

  /** «نگه‌داشتنِ سبد» ⇒ فهرست = همان شناسه‌ها و مقدارها (بی قیمت) */
  fun saveCart(s: ShopperState, slug: String, cart: Cart): ShopperState {
    val items = cleanList(cart.lines.map { ListItem(it.productId, it.qtyMilli) })
    return setList(s, slug, items, ListOp(all = items))
  }

  fun removeFromList(s: ShopperState, slug: String, id: String): ShopperState =
    setList(s, slug, listFor(s, slug).filter { it.id != id }, ListOp(rm = id))

  fun clearList(s: ShopperState, slug: String): ShopperState = setList(s, slug, emptyList(), ListOp(all = emptyList()))

  /** «همه به سبد»: فقط کالاهای امروزِ سرور ([items] ِ پاسخِ `?ids=`) با همان مقدار تا سقف؛ قلمِ موجودِ سبد دست نمی‌خورد. @return (سبد، جامانده) */
  fun listToCart(cart: Cart, list: List<ListItem>, items: List<PublicProduct>, at: Long): Pair<Cart, Int> {
    var c = cart
    var gone = 0
    for (x in list) {
      val p = items.firstOrNull { it.id == x.id }
      if (p == null || !CartRules.canOrder(p)) { gone++; continue }
      if (CartRules.qtyOf(c, p.id) > 0) continue
      val added = CartRules.add(c, p, at)
      if (added === c) { gone++; continue }
      c = CartRules.setQty(added, p.id, x.q.coerceAtLeast(CartRules.STEP_MILLI), at)
    }
    return c to gone
  }

  // ── سبد: همان `items()`/`mark()` ِ acct.js ──

  fun cartItems(cart: Cart, notes: Map<String, String>): List<AccountItem> =
    cart.lines.sortedBy { it.productId }.map { AccountItem(it.productId, it.qtyMilli, notes[it.productId]?.takeIf { n -> n.isNotBlank() }) }

  fun mark(items: List<AccountItem>): String =
    items.sortedBy { it.productId }.joinToString("|") { it.productId + ":" + it.qtyMilli + ":" + (it.note ?: "").trim() }

  private fun serverItems(c: AccountCart): List<AccountItem> = c.lines.map { AccountItem(it.productId, it.qtyMilli, it.note.takeIf { n -> n.isNotBlank() }) }

  private fun notesOf(c: AccountCart): Map<String, String> = c.lines.filter { it.note.isNotBlank() }.associate { it.productId to it.note }

  // ── گام‌ها ──

  /** کارِ بعدی برای [slug] — یا null (همه رسید). همیشه یک درخواست در هر بار، مثلِ وب. */
  fun next(s: ShopperState, slug: String, f: AccountFeatures, pulls: AccountPulls): AccountStep? {
    val st = s.sync[slug] ?: return null
    if (f.cart) {
      val items = cartItems(ShopperMemory.cartOf(s, slug), st.cartNotes)
      //  ادغام فقط وقتی سبدِ مهمان هست؛ سبدِ خالی ⇒ همان یک خواندن (S140)
      if (!st.cartM) return if (items.isEmpty()) AccountStep.CartPull(mark(items)) else AccountStep.CartMerge(items)
      if (mark(items) != st.cartK) return AccountStep.CartPut(st.cartV, items)
    }
    if (f.favorites) {
      st.favOps.firstOrNull()?.let { return AccountStep.Fav(it) }
      if (!st.listOff) {
        if (!st.listM) return AccountStep.ListMerge(listFor(s, slug))
        st.listOps.firstOrNull()?.let { op -> return AccountStep.ListPut(op, st.listV, op.all ?: st.listS.filter { it.id != op.rm }) }
      }
    }
    if (f.cart && pulls.cart) return AccountStep.CartPull(mark(cartItems(ShopperMemory.cartOf(s, slug), st.cartNotes)))
    if (f.favorites && pulls.fav) return AccountStep.FavPull
    if (f.favorites && pulls.list && !st.listOff) return AccountStep.ListPull
    return null
  }

  /** پس از پاسخِ خواندنِ موفق، همان خواندن از فهرستِ این دور بیرون می‌رود (پاسخِ ادغام خودش سبدِ امروز است) */
  fun after(p: AccountPulls, step: AccountStep, reply: AccountReply): AccountPulls {
    if (reply.error != null) return p
    return when (step) {
      is AccountStep.CartPull, is AccountStep.CartMerge -> p.copy(cart = false)
      AccountStep.FavPull -> p.copy(fav = false)
      AccountStep.ListPull -> p.copy(list = false)
      else -> p
    }
  }

  private fun put(s: ShopperState, slug: String, st: AccountSync): ShopperState = s.copy(sync = s.sync + (slug to st))

  private fun drop(ops: List<FavOp>, op: FavOp): List<FavOp> {
    val i = ops.indexOf(op)
    return if (i < 0) ops else ops.filterIndexed { j, _ -> j != i }
  }

  private fun dropList(ops: List<ListOp>, op: ListOp): List<ListOp> {
    val i = ops.indexOf(op)
    return if (i < 0) ops else ops.filterIndexed { j, _ -> j != i }
  }

  private fun listItems(l: AccountList): List<ListItem> = l.lines.map { ListItem(it.productId, it.qtyMilli) }

  /**
   *  حالِ تازه پس از پاسخِ [step] — حالِ [s] همان حالِ **اکنون** است (کارِ تازه در میانهٔ درخواست گم نمی‌شود). شکستِ گذرا ⇒
   *  درنگِ دورِ بعد ([CartSync.paced]).
   */
  fun apply(s: ShopperState, slug: String, step: AccountStep, reply: AccountReply, at: Long): AccountOutcome =
    CartSync.paced(applyStep(s, slug, step, reply, at), slug, reply.error, at)

  private fun applyStep(s: ShopperState, slug: String, step: AccountStep, reply: AccountReply, at: Long): AccountOutcome {
    val st = s.sync[slug] ?: return AccountOutcome(s, go = false)
    val e = reply.error
    val cartStep = step is AccountStep.CartMerge || step is AccountStep.CartPut || step is AccountStep.CartPull
    //  پرچمِ سبد خاموش شد (درِ ناشناخته ⇒ ۴۰۱ بی `session_required`) یا ویترین بسته ⇒ فقط سبدِ دستگاه، هرگز خروج
    if (e != null && cartStep && CartSync.doorClosed(e)) return AccountOutcome(s, go = false, cartOff = true)
    if (e != null && e.status == 401) return AccountOutcome(signedOut(s), go = false, signedOut = true)
    return when (step) {
      is AccountStep.CartMerge, is AccountStep.CartPull, is AccountStep.CartPut -> applyCart(s, slug, st, step, reply, at)
      is AccountStep.Fav -> when {
        e == null || e.status == 400 || e.status == 404 || e.status == 409 ->
          AccountOutcome(put(s, slug, st.copy(favOps = drop(st.favOps, step.op))), go = true)
        else -> AccountOutcome(s, go = false)
      }
      AccountStep.FavPull -> {
        val page = reply.favs?.valueOrNull() ?: return AccountOutcome(s, go = false)
        if (st.favOps.isNotEmpty()) return AccountOutcome(s, go = true, products = page.items)
        AccountOutcome(s.copy(favs = withKey(s.favs, slug, cleanFavs(page.items.map { it.id }))), go = true, products = page.items)
      }
      is AccountStep.ListMerge, is AccountStep.ListPut, AccountStep.ListPull -> applyList(s, slug, st, step, reply)
    }
  }

  /** CHK-03: پاسخِ گام‌های سبد — [CartSync.rebase] و [CartSync.adopt]؛ ۴۰۹ ِ نوشتن فقط [CartSync.CONFLICT_RETRIES] بار پشتِ‌هم دوباره */
  private fun applyCart(s: ShopperState, slug: String, st: AccountSync, step: AccountStep, reply: AccountReply, at: Long): AccountOutcome {
    val local = cartItems(ShopperMemory.cartOf(s, slug), st.cartNotes)
    val c = reply.cart?.valueOrNull()
    if (c != null) {
      //  پایهٔ تغییرِ در صف: آن‌چه فرستاده شد (ادغام/نوشتن) یا سبدِ دیده‌شده (خواندن)؛ تغییرِ میانهٔ درخواست می‌ماند
      val base = when (step) {
        is AccountStep.CartMerge -> step.items
        is AccountStep.CartPut -> step.items
        is AccountStep.CartPull -> if (mark(local) == step.mark) local else st.cartB
        else -> st.cartB
      }
      return cartSeen(s, slug, st, c, CartSync.rebase(base, local, serverItems(c)), conflicts = 0, go = true, at = at)
    }
    val e = reply.error ?: return AccountOutcome(s, go = false)
    val d = cartIn(e) ?: return AccountOutcome(s, go = false)
    return when {
      //  دستگاهِ دیگر سبد را عوض کرد ⇒ همان تغییرِ این دستگاه روی سبدِ تازه؛ دومین ۴۰۹ ِ پشتِ‌هم ⇒ دورِ بعد با درنگ (بی حلقه)
      e.code == CartSync.CART_CHANGED && step is AccountStep.CartPut ->
        cartSeen(s, slug, st, d, CartSync.rebase(st.cartB, local, serverItems(d)), conflicts = st.cartC + 1, go = st.cartC < CartSync.CONFLICT_RETRIES, at = at)
      //  بیش از ۱۰۰ قلم ⇒ سبدِ دستگاه جای سبدِ حساب (هیچ چیز بی‌صدا گم نمی‌شود)
      e.code == CartSync.CART_FULL && step !is AccountStep.CartPut ->
        AccountOutcome(put(s, slug, st.copy(cartV = d.version, cartK = "", cartB = emptyList(), cartM = true, cartC = 0)), go = true)
      else -> AccountOutcome(s, go = false)
    }
  }

  /** سبدِ سرور [c] دیده شد: نسخه و پایهٔ تازه، و سبدِ دستگاه = [items] با نام، قیمت، موجودی و سقفِ امروزِ سرور */
  private fun cartSeen(s: ShopperState, slug: String, st: AccountSync, c: AccountCart, items: List<AccountItem>, conflicts: Int, go: Boolean, at: Long): AccountOutcome {
    val fresh = serverItems(c)
    val next = st.copy(cartV = c.version, cartK = mark(fresh), cartB = fresh, cartM = true, cartNotes = notesOf(c), cartC = conflicts)
    val a = CartSync.adopt(ShopperMemory.cartOf(s, slug), items, c, at)
    return AccountOutcome(ShopperMemory.withCart(put(s, slug, next), a.cart), go = go, repriced = a.repriced, gone = a.gone)
  }

  private fun applyList(s: ShopperState, slug: String, st: AccountSync, step: AccountStep, reply: AccountReply): AccountOutcome {
    val l = reply.list?.valueOrNull()
    val e = reply.error
    val op = (step as? AccountStep.ListPut)?.op
    if (l != null) {
      val ops = if (op != null) dropList(st.listOps, op) else st.listOps
      val synced = st.copy(listV = l.version, listS = listItems(l), listM = true, listOps = ops)
      val out = put(s, slug, synced)
      //  صف خالی ⇒ فهرستِ حساب جای فهرستِ دستگاه
      return AccountOutcome(if (ops.isEmpty()) out.copy(lists = withKey(out.lists, slug, cleanList(listItems(l)))) else out, go = true, products = l.items)
    }
    val d = e?.let { listIn(it) }
    return when {
      e?.code == "list_changed" && d != null -> AccountOutcome(put(s, slug, st.copy(listV = d.version, listS = listItems(d))), go = true)
      //  ادغام جا نشد ⇒ فهرستِ دستگاه جای فهرستِ حساب
      e?.code == "list_full" && d != null ->
        AccountOutcome(put(s, slug, st.copy(listV = d.version, listS = listItems(d), listM = true, listOps = listOf(ListOp(all = listFor(s, slug))) + st.listOps)), go = true)
      e?.code == "too_many_lists" -> AccountOutcome(put(s, slug, st.copy(listOff = true)), go = true)
      op != null && e != null && (e.status == 400 || e.status == 404 || e.status == 409) ->
        AccountOutcome(put(s, slug, st.copy(listOps = dropList(st.listOps, op))), go = true)
      else -> AccountOutcome(s, go = false)
    }
  }

  /** سبد/فهرستِ تازهٔ درونِ ۴۰۹ (`cart_changed`/`cart_full`، `list_changed`/`list_full`) */
  fun cartIn(e: ShopperError): AccountCart? = e.data?.get("cart")?.let { decode(AccountCart.serializer(), it) }

  fun listIn(e: ShopperError): AccountList? = e.data?.get("list")?.let { decode(AccountList.serializer(), it) }

  private fun <T> decode(s: DeserializationStrategy<T>, el: JsonElement): T? =
    try { ShopperJson.decodeFromJsonElement(s, el) } catch (_: Exception) { null }

  /** یک کار روی سرور (هم‌گام؛ از نخِ پس‌زمینه) */
  fun send(api: ShopperApi, session: String, slug: String, step: AccountStep): AccountReply = when (step) {
    is AccountStep.CartMerge -> AccountReply(cart = api.mergeAccountCart(slug, session, step.items))
    is AccountStep.CartPut -> AccountReply(cart = api.putAccountCart(slug, session, step.version, step.items))
    is AccountStep.CartPull -> AccountReply(cart = api.accountCart(slug, session))
    is AccountStep.Fav -> AccountReply(fav = api.setFavorite(slug, session, step.op.id, step.op.add))
    AccountStep.FavPull -> AccountReply(favs = api.favorites(slug, session))
    is AccountStep.ListMerge -> AccountReply(list = api.mergeShoppingList(slug, session, step.items))
    is AccountStep.ListPut -> AccountReply(list = api.putShoppingList(slug, session, step.version, step.items))
    AccountStep.ListPull -> AccountReply(list = api.shoppingList(slug, session))
  }
}
