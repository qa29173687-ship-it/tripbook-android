package tw.tripbook.cloud

import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import tw.tripbook.data.Repository
import tw.tripbook.domain.ExpenseCategory
import tw.tripbook.domain.PaymentMethod
import tw.tripbook.domain.SharedView
import tw.tripbook.domain.ShoppingText
import tw.tripbook.util.Money

/**
 * Runs on the owner's (主帳號) phone: mirrors the local trip to the cloud so companions can read it,
 * and records companions' purchases as expenses (they can be edited afterwards by the owner).
 */
@OptIn(FlowPreview::class)
class OwnerSync(private val repo: Repository, private val store: ShareStore, private val scope: CoroutineScope) {
    private val jobs = mutableMapOf<Long, Job>()
    private val ingestLock = Mutex()
    private val handled = mutableSetOf<String>()

    /** Latest "小美 剛買到…已記帳" notice per local trip id. */
    val notices = MutableStateFlow<Map<Long, String>>(emptyMap())

    fun startAll() {
        if (Cloud.user == null) return
        store.ownedAll().forEach { (local, cloud) -> start(local, cloud) }
    }

    fun start(localId: Long, cloudId: String) {
        if (jobs[localId]?.isActive == true) return
        jobs[localId] = scope.launch {
            launch {
                combine(repo.observeTrip(localId), repo.observeMembers(localId), repo.observeExpenses(localId)) { t, m, e -> Triple(t, m, e) }
                    .debounce(700)
                    .collect { runCatching { push(localId, cloudId) } }
            }
            launch {
                Cloud.inboxFlow(cloudId).collect { list ->
                    list.sortedBy { it.at }.forEach { runCatching { ingest(localId, cloudId, it) } }
                }
            }
        }
    }

    /** Create the cloud copy + invite code. Moves this trip's 我要買 items to the shared list. */
    suspend fun share(localId: Long): String {
        store.ownedCloudId(localId)?.let { start(localId, it); return it }
        val user = Cloud.user?.takeIf { Cloud.isGoogle } ?: error("請先用 Google 登入")
        val t = repo.getTrip(localId) ?: error("找不到行程")
        val me = repo.listMembers(localId).let { ms -> ms.firstOrNull { it.isMe } ?: ms.first() }
        val code = SharedView.newInviteCode()
        val ref = Cloud.trips().document()
        ref.set(
            mapOf(
                "ownerUid" to user.uid, "ownerName" to me.name, "inviteCode" to code,
                "name" to t.name, "currency" to t.currency, "startDate" to t.startDate, "endDate" to t.endDate,
                "members" to emptyList<Any>(), "updatedAt" to System.currentTimeMillis()
            )
        ).await()
        Cloud.db.collection("invites").document(code).set(mapOf("tripId" to ref.id, "ownerUid" to user.uid)).await()
        store.setOwned(localId, ref.id)
        // move personal 我要買 into the shared list
        val exps = repo.listExpenses(localId).associateBy { it.id }
        for (it in repo.listShopping(localId)) {
            val e = it.expenseId?.let { id -> exps[id] }
            ref.collection("shopping").add(
                mapOf(
                    "ownerUid" to user.uid, "memberId" to cloudMemberId(localId, me.id), "memberName" to me.name,
                    "name" to it.name, "qty" to it.qty, "note" to it.note,
                    "photo" to (it.photoPath?.let { p -> Cloud.photoToBase64(p) } ?: ""),
                    "done" to it.done, "amount" to (e?.amountCents ?: 0L), "method" to (e?.paymentMethod ?: "CASH"),
                    "expenseId" to e?.id?.toString(), "helpers" to emptyMap<String, Any>(),
                    "createdAt" to it.createdAt, "updatedAt" to System.currentTimeMillis()
                )
            ).await()
            repo.deleteShoppingItemKeepExpense(it.id)
        }
        push(localId, ref.id)
        start(localId, ref.id)
        return ref.id
    }

    private suspend fun push(localId: Long, cloudId: String) {
        val t = repo.getTrip(localId) ?: return
        val ms = repo.listMembers(localId)
        val es = repo.listExpenses(localId)
        val splits = repo.splitsForTrip(localId).groupBy { it.expenseId }
        val ref = Cloud.trip(cloudId)
        val mm = store.memberMap(localId)
        fun cid(id: Long) = mm[id] ?: id.toString()
        ref.set(
            mapOf(
                "name" to t.name, "currency" to t.currency, "startDate" to t.startDate, "endDate" to t.endDate,
                "ownerName" to (ms.firstOrNull { it.isMe }?.name ?: ""),
                "members" to ms.map {
                    mapOf("id" to cid(it.id), "name" to it.name, "start" to it.startingCashCents, "cash" to it.cashCents, "owner" to it.isMe)
                },
                "updatedAt" to System.currentTimeMillis()
            ),
            SetOptions.merge()
        ).await()
        val col = ref.collection("expenses")
        val existing = col.get().await().documents.map { it.id }.toSet()
        val now = es.map { it.id.toString() }.toSet()
        val batch = Cloud.db.batch()
        es.forEach { e ->
            batch.set(
                col.document(e.id.toString()),
                mapOf(
                    "title" to e.title, "amount" to e.amountCents, "payerId" to cid(e.payerMemberId), "category" to e.category,
                    "method" to e.paymentMethod, "at" to e.occurredAt,
                    "shares" to (splits[e.id].orEmpty().associate { cid(it.memberId) to it.shareCents })
                )
            )
        }
        (existing - now).forEach { batch.delete(col.document(it)) }
        batch.commit().await()
    }

    private suspend fun ingest(localId: Long, cloudId: String, m: CInbox) = ingestLock.withLock {
        if (!handled.add(m.id)) return@withLock
        val itemRef = Cloud.trip(cloudId).collection("shopping").document(m.itemId)
        val item = runCatching { Cloud.parseItem(itemRef.get().await()) }.getOrNull()
        val oldExp = item?.expenseId?.toLongOrNull()
        val memberId = localMemberId(localId, m.memberId)
        val forId = localMemberId(localId, m.forMemberId) ?: memberId
        val mems = repo.listMembers(localId)
        if (m.type == "buy" && memberId != null && forId != null && mems.any { it.id == memberId } && mems.any { it.id == forId }) {
            oldExp?.let { repo.deleteExpense(it) }
            val method = runCatching { PaymentMethod.valueOf(m.method) }.getOrDefault(PaymentMethod.CASH)
            val eid = repo.addExpense(localId, m.title, m.amountCents, ExpenseCategory.SHOP, memberId, method, listOf(forId))
            if (item != null) itemRef.update(
                mapOf("expenseId" to eid.toString(), "done" to true, "amount" to m.amountCents, "method" to m.method)
            ).await()
            val cur = repo.getTrip(localId)?.currency ?: "TWD"
            val forName = mems.firstOrNull { it.id == forId }?.name.orEmpty()
            val who = if (forId == memberId) "${m.memberName} 剛買到" else "${m.memberName} 幫 $forName 買到"
            notices.update {
                it + (localId to "$who ${m.title} ${Money.format(m.amountCents, cur)}" +
                    "${if (method == PaymentMethod.CASH) " 現金" else " 信用卡"}，已記帳")
            }
            // If 已當場還錢 was already marked on the item, settle now so a later repay inbox is a no-op.
            val fresh = runCatching { Cloud.parseItem(itemRef.get().await()) }.getOrNull()
            if (fresh?.paidBy?.repaid == true) applyRepay(localId, fresh)
        } else if (m.type == "unbuy") {
            oldExp?.let { repo.deleteExpense(it) }
            if (item != null) itemRef.update(mapOf("expenseId" to null, "done" to false, "amount" to 0L)).await()
        } else if (m.type == "repay") {
            val fresh = item ?: runCatching { Cloud.parseItem(itemRef.get().await()) }.getOrNull()
            if (applyRepay(localId, fresh)) {
                val cur = repo.getTrip(localId)?.currency ?: "TWD"
                val pb = fresh?.paidBy
                val a = pb?.name ?: m.memberName
                val b = fresh?.memberName ?: mems.firstOrNull { it.id == forId }?.name.orEmpty()
                val how = if (m.method == "CASH") "現金" else "轉帳"
                notices.update {
                    it + (localId to "$b 已當場還錢給 $a（${m.title} ${Money.format(m.amountCents, cur)} · $how）")
                }
            }
        } else if (m.type == "unrepay") {
            val fresh = item ?: runCatching { Cloud.parseItem(itemRef.get().await()) }.getOrNull()
            if (applyUnrepay(localId, fresh)) {
                notices.update { it + (localId to "已取消「已當場還錢」（${m.title}）") }
            }
        }
        Cloud.trip(cloudId).collection("inbox").document(m.id).delete().await()
    }

    /** Convert a recorded helper-pay expense into B-bought-it-themselves after 已當場還錢. */
    private suspend fun applyRepay(localId: Long, item: CItem?): Boolean {
        val pb = item?.paidBy ?: return false
        val expId = item.expenseId?.toLongOrNull() ?: return false
        val forId = localMemberId(localId, item.memberId) ?: return false
        val repay = runCatching { PaymentMethod.valueOf(pb.repaidMethod.ifBlank { "CASH" }) }
            .getOrDefault(PaymentMethod.CASH)
            .let { if (it == PaymentMethod.CASH) it else PaymentMethod.TRANSFER }
        return repo.settleHelperRepay(expId, forId, repay, pb.name, item.memberName)
    }

    private suspend fun applyUnrepay(localId: Long, item: CItem?): Boolean {
        val pb = item?.paidBy ?: return false
        val expId = item.expenseId?.toLongOrNull() ?: return false
        val forId = localMemberId(localId, item.memberId) ?: return false
        val payerId = localMemberId(localId, pb.memberId) ?: return false
        val method = runCatching { PaymentMethod.valueOf(pb.method) }.getOrDefault(PaymentMethod.CASH)
        val title = ShoppingText.expenseTitle(item.name, item.qty)
        return repo.undoHelperRepay(expId, payerId, method, forId, title)
    }

    /** Cloud id used for a local member (identity unless this trip was restored on a new phone). */
    fun cloudMemberId(localTripId: Long, memberId: Long) = store.memberMap(localTripId)[memberId] ?: memberId.toString()
    private fun localMemberId(localTripId: Long, cloudId: String): Long? =
        store.memberMap(localTripId).entries.firstOrNull { it.value == cloudId }?.key ?: cloudId.toLongOrNull()

    /** Shared trips this Google account owns that are not on this phone yet. */
    suspend fun restorable(): List<CTrip> {
        val u = Cloud.user?.takeIf { Cloud.isGoogle } ?: return emptyList()
        val have = store.ownedAll().values.toSet()
        return Cloud.trips().whereEqualTo("ownerUid", u.uid).get().await().documents
            .mapNotNull { Cloud.parseTrip(it) }.filter { it.id !in have }
    }

    /** Rebuild a shared trip from the cloud on a new phone (members, cash, expenses), keep companions bound. */
    suspend fun restore(c: CTrip): Long {
        val ref = Cloud.trip(c.id)
        val exps = ref.collection("expenses").get().await().documents.map { it to Cloud.parseExpense(it) }
        val ownerIdx = c.members.indexOfFirst { it.isOwner }.coerceAtLeast(0)
        val localId = repo.createTrip(
            c.name, "", c.currency, c.startDate, c.endDate,
            c.members.map { tw.tripbook.data.MemberDraft(it.name, it.startCents) }, meIndex = ownerIdx
        )
        val locals = repo.listMembers(localId).sortedBy { it.id }
        val map = c.members.indices.associate { i -> locals[i].id to c.members[i].id }
        val rev = map.entries.associate { (k, v) -> v to k }
        store.setMemberMap(localId, map)
        val expMap = mutableMapOf<String, Long>()
        exps.sortedBy { it.second.at }.forEach { (doc, e) ->
            val payer = rev[e.payerId] ?: return@forEach
            val parts = e.shares.keys.mapNotNull { rev[it] }.ifEmpty { listOf(payer) }
            val cat = runCatching { ExpenseCategory.valueOf(doc.getString("category") ?: "OTHER") }.getOrDefault(ExpenseCategory.OTHER)
            val method = runCatching { PaymentMethod.valueOf(e.method) }.getOrDefault(PaymentMethod.CARD)
            expMap[e.id] = repo.addExpense(localId, e.title, e.amountCents, cat, payer, method, parts, occurredAt = e.at)
        }
        ref.collection("shopping").get().await().documents.forEach { d ->
            val old = d.getString("expenseId") ?: return@forEach
            d.reference.update("expenseId", expMap[old]?.toString()).await()
        }
        store.setOwned(localId, c.id)
        push(localId, c.id)
        start(localId, c.id)
        return localId
    }

    fun clearNotice(localId: Long) = notices.update { it - localId }

    // ---------- owner's own items ----------
    suspend fun ownerBuy(localId: Long, cloudId: String, item: CItem, amount: Long, method: PaymentMethod) {
        val me = repo.listMembers(localId).let { ms -> ms.firstOrNull { it.isMe } ?: ms.first() }
        item.expenseId?.toLongOrNull()?.let { repo.deleteExpense(it) }
        val eid = repo.addExpense(
            localId, ShoppingText.expenseTitle(item.name, item.qty), amount, ExpenseCategory.SHOP, me.id, method, listOf(me.id)
        )
        Cloud.updateItem(cloudId, item.id, mapOf("done" to true, "amount" to amount, "method" to method.name, "expenseId" to eid.toString()))
    }

    suspend fun ownerUnbuy(cloudId: String, item: CItem) {
        item.expenseId?.toLongOrNull()?.let { repo.deleteExpense(it) }
        Cloud.updateItem(cloudId, item.id, mapOf("done" to false, "amount" to 0L, "expenseId" to null))
    }
}
