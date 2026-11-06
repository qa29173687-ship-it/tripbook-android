package tw.tripbook.data

import kotlinx.coroutines.flow.Flow
import tw.tripbook.domain.*

class Repository(private val db: AppDatabase) {
    private val trips get() = db.trips()
    private val members get() = db.members()
    private val expenses get() = db.expenses()
    private val cash get() = db.cash()

    fun observeTrips(): Flow<List<TripEntity>> = trips.observeTrips()
    fun observeTrip(id: Long) = trips.observe(id)
    fun observeMembers(tripId: Long) = members.observeForTrip(tripId)
    fun observeExpenses(tripId: Long) = expenses.observeForTrip(tripId)
    fun observeMemberCash(tripId: Long, memberId: Long) = cash.observeForMember(tripId, memberId)
    fun observePoolCash(tripId: Long) = cash.observeForPool(tripId)

    suspend fun getTrip(id: Long) = trips.get(id)
    suspend fun listMembers(tripId: Long) = members.listForTrip(tripId)
    suspend fun listExpenses(tripId: Long) = expenses.listForTrip(tripId)

    suspend fun createTrip(
        name: String, place: String, currency: String,
        startDate: String, endDate: String,
        memberNames: List<String>, meIndex: Int = 0
    ): Long {
        val tripId = trips.insert(
            TripEntity(name = name, place = place, currency = currency, startDate = startDate, endDate = endDate)
        )
        memberNames.forEachIndexed { i, n ->
            members.insert(
                MemberEntity(tripId = tripId, name = n.trim(), isMe = i == meIndex, colorIndex = i % 6)
            )
        }
        return tripId
    }

    suspend fun updateTrip(trip: TripEntity) = trips.update(trip)
    suspend fun deleteTrip(id: Long) = trips.delete(id)

    suspend fun addMember(tripId: Long, name: String, cashCents: Long = 0): Long {
        val count = members.listForTrip(tripId).size
        return members.insert(
            MemberEntity(tripId = tripId, name = name.trim(), cashCents = cashCents, colorIndex = count % 6)
        )
    }

    suspend fun setMemberCashDirect(memberId: Long, newCashCents: Long, note: String = "手動調整") {
        val m = members.get(memberId) ?: return
        val delta = newCashCents - m.cashCents
        if (delta == 0L) return
        members.update(m.copy(cashCents = newCashCents))
        cash.insert(
            CashMovementEntity(
                tripId = m.tripId,
                memberId = memberId,
                direction = if (delta > 0) CashDirection.IN.name else CashDirection.OUT.name,
                kind = CashKind.OTHER.name,
                amountCents = kotlin.math.abs(delta),
                note = note
            )
        )
    }

    suspend fun setPoolCashDirect(tripId: Long, newPoolCents: Long, holderId: Long?, note: String = "手動調整公款") {
        val t = trips.get(tripId) ?: return
        val delta = newPoolCents - t.poolCashCents
        trips.update(t.copy(poolCashCents = newPoolCents, poolHolderMemberId = holderId ?: t.poolHolderMemberId))
        if (delta == 0L) return
        cash.insert(
            CashMovementEntity(
                tripId = tripId,
                memberId = null,
                direction = if (delta > 0) CashDirection.IN.name else CashDirection.OUT.name,
                kind = CashKind.OTHER.name,
                amountCents = kotlin.math.abs(delta),
                note = note
            )
        )
    }

    /** Personal or pool cash IN/OUT that is NOT a shared expense. */
    suspend fun addCashMovement(
        tripId: Long,
        memberId: Long?, // null = pool
        direction: CashDirection,
        kind: CashKind,
        amountCents: Long,
        note: String
    ) {
        require(amountCents > 0)
        if (memberId == null) {
            val t = trips.get(tripId) ?: return
            val next = when (direction) {
                CashDirection.IN -> CashLedger.credit(t.poolCashCents, amountCents)
                CashDirection.OUT -> CashLedger.debit(t.poolCashCents, amountCents)
            }
            trips.update(t.copy(poolCashCents = next))
        } else {
            val m = members.get(memberId) ?: return
            val next = when (direction) {
                CashDirection.IN -> CashLedger.credit(m.cashCents, amountCents)
                CashDirection.OUT -> CashLedger.debit(m.cashCents, amountCents)
            }
            members.update(m.copy(cashCents = next))
        }
        cash.insert(
            CashMovementEntity(
                tripId = tripId,
                memberId = memberId,
                direction = direction.name,
                kind = kind.name,
                amountCents = amountCents,
                note = note
            )
        )
    }

    suspend fun addExpense(
        tripId: Long,
        title: String,
        amountCents: Long,
        category: ExpenseCategory,
        payerMemberId: Long, // -1L for pool payer
        method: PaymentMethod,
        participantIds: List<Long>,
        note: String = "",
        receiptPath: String? = null,
        occurredAt: Long = System.currentTimeMillis()
    ): Long {
        require(amountCents >= 0)
        require(participantIds.isNotEmpty()) { "至少一位分攤" }
        if (method == PaymentMethod.POOL) require(payerMemberId == -1L || true)
        val effectiveMethod = if (payerMemberId == -1L) PaymentMethod.POOL else method
        val splits = SplitMath.equalSplit(amountCents, participantIds)
        val id = expenses.insert(
            ExpenseEntity(
                tripId = tripId,
                title = title,
                amountCents = amountCents,
                category = category.name,
                payerMemberId = if (effectiveMethod == PaymentMethod.POOL) -1L else payerMemberId,
                paymentMethod = effectiveMethod.name,
                note = note,
                receiptPath = receiptPath,
                occurredAt = occurredAt
            )
        )
        expenses.insertSplits(splits.map { ExpenseSplitEntity(id, it.memberId, it.amountCents) })
        applyCashForExpense(tripId, id, if (effectiveMethod == PaymentMethod.POOL) -1L else payerMemberId, effectiveMethod, amountCents, title)
        return id
    }

    private suspend fun applyCashForExpense(
        tripId: Long, expenseId: Long, payerMemberId: Long,
        method: PaymentMethod, amountCents: Long, title: String
    ) {
        when (method) {
            PaymentMethod.CASH -> {
                val m = members.get(payerMemberId) ?: return
                val (next, _) = CashLedger.applyExpensePayment(m.cashCents, 0, amountCents, PaymentMethod.CASH)
                members.update(m.copy(cashCents = next))
                cash.insert(
                    CashMovementEntity(
                        tripId = tripId, memberId = payerMemberId,
                        direction = CashDirection.OUT.name, kind = CashKind.EXPENSE.name,
                        amountCents = amountCents, note = "分帳：$title", relatedExpenseId = expenseId
                    )
                )
            }
            PaymentMethod.POOL -> {
                val t = trips.get(tripId) ?: return
                val (_, nextPool) = CashLedger.applyExpensePayment(0, t.poolCashCents, amountCents, PaymentMethod.POOL)
                trips.update(t.copy(poolCashCents = nextPool))
                cash.insert(
                    CashMovementEntity(
                        tripId = tripId, memberId = null,
                        direction = CashDirection.OUT.name, kind = CashKind.EXPENSE.name,
                        amountCents = amountCents, note = "公款分帳：$title", relatedExpenseId = expenseId
                    )
                )
            }
            else -> Unit
        }
    }

    suspend fun deleteExpense(expenseId: Long) {
        val e = expenses.get(expenseId) ?: return
        val method = PaymentMethod.valueOf(e.paymentMethod)
        when (method) {
            PaymentMethod.CASH -> {
                val m = members.get(e.payerMemberId) ?: return
                val (next, _) = CashLedger.reverseExpensePayment(m.cashCents, 0, e.amountCents, PaymentMethod.CASH)
                members.update(m.copy(cashCents = next))
            }
            PaymentMethod.POOL -> {
                val t = trips.get(e.tripId) ?: return
                val (_, next) = CashLedger.reverseExpensePayment(0, t.poolCashCents, e.amountCents, PaymentMethod.POOL)
                trips.update(t.copy(poolCashCents = next))
            }
            else -> Unit
        }
        cash.deleteForExpense(expenseId)
        expenses.deleteSplits(expenseId)
        expenses.delete(expenseId)
    }

    suspend fun computeBalances(tripId: Long): List<MemberBalance> {
        val mems = members.listForTrip(tripId)
        val exps = expenses.listForTrip(tripId)
        val splits = expenses.splitsForTrip(tripId)
        val paid = mutableMapOf<Long, Long>()
        val share = mutableMapOf<Long, Long>()
        mems.forEach { paid[it.id] = 0; share[it.id] = 0 }
        exps.forEach { e ->
            if (e.payerMemberId > 0) paid[e.payerMemberId] = (paid[e.payerMemberId] ?: 0) + e.amountCents
        }
        splits.forEach { s ->
            share[s.memberId] = (share[s.memberId] ?: 0) + s.shareCents
        }
        return SplitMath.nets(paid, share, mems.map { it.id })
    }

    suspend fun computeTransfers(tripId: Long): List<Transfer> =
        SplitMath.minimizeTransfers(computeBalances(tripId))

    suspend fun markSettled(tripId: Long, settled: Boolean) {
        val t = trips.get(tripId) ?: return
        trips.update(t.copy(settled = settled))
    }

    suspend fun cashTimeline(tripId: Long, memberId: Long?): List<CashTimelineEntry> {
        val rows = if (memberId == null) cash.listForPool(tripId) else cash.listForMember(tripId, memberId)
        // Reconstruct starting balance: current - sum(IN) + sum(OUT) for chronological after
        var start = if (memberId == null) {
            trips.get(tripId)?.poolCashCents ?: 0L
        } else {
            members.get(memberId)?.cashCents ?: 0L
        }
        // Walk backward to find starting
        for (r in rows.asReversed()) {
            start = when (CashDirection.valueOf(r.direction)) {
                CashDirection.IN -> start - r.amountCents
                CashDirection.OUT -> start + r.amountCents
            }
        }
        var bal = start
        return rows.map { r ->
            bal = when (CashDirection.valueOf(r.direction)) {
                CashDirection.IN -> CashLedger.credit(bal, r.amountCents)
                CashDirection.OUT -> CashLedger.debit(bal, r.amountCents)
            }
            CashTimelineEntry(
                id = r.id,
                direction = CashDirection.valueOf(r.direction),
                kind = CashKind.valueOf(r.kind),
                amountCents = r.amountCents,
                note = r.note,
                occurredAt = r.occurredAt,
                balanceAfterCents = bal,
                relatedExpenseId = r.relatedExpenseId
            )
        }.asReversed() // newest first for UI
    }

    /** Amounts are whole TWD (NT$1 = 1). Field names keep *Cents for Room stability. */
    suspend fun seedDemoIfEmpty() {
        if (trips.listTrips().isNotEmpty()) return
        val tripId = createTrip(
            name = "東京三日遊", place = "日本東京", currency = "TWD",
            startDate = "2026-11-01", endDate = "2026-11-03",
            memberNames = listOf("宏威", "小美", "阿明"), meIndex = 0
        )
        val mems = members.listForTrip(tripId)
        val hw = mems[0].id; val xm = mems[1].id; val am = mems[2].id
        addCashMovement(tripId, hw, CashDirection.IN, CashKind.ATM, 8000, "出發前提款")
        addCashMovement(tripId, xm, CashDirection.IN, CashKind.ATM, 5000, "出發前提款")
        addCashMovement(tripId, am, CashDirection.IN, CashKind.ATM, 6000, "出發前提款")
        addCashMovement(tripId, null, CashDirection.IN, CashKind.EXCHANGE, 3000, "機場換匯放入公款")
        val t = trips.get(tripId)!!
        trips.update(t.copy(poolHolderMemberId = hw))

        fun day(d: Int, h: Int = 12) = java.time.LocalDateTime.of(2026, 11, d, h, 0)
            .atZone(java.time.ZoneId.of("Asia/Taipei")).toInstant().toEpochMilli()

        addExpense(tripId, "新宿飯店", 6000, ExpenseCategory.STAY, hw, PaymentMethod.CARD, listOf(hw, xm, am), occurredAt = day(1, 15))
        addExpense(tripId, "一蘭拉麵", 1200, ExpenseCategory.FOOD, xm, PaymentMethod.CASH, listOf(hw, xm, am), occurredAt = day(1, 19))
        addExpense(tripId, "機場快線", 900, ExpenseCategory.TRANSIT, am, PaymentMethod.CASH, listOf(hw, xm, am), occurredAt = day(1, 10))
        addExpense(tripId, "晴空塔門票", 2400, ExpenseCategory.TICKET, hw, PaymentMethod.CARD, listOf(hw, xm, am), occurredAt = day(2, 11))
        addExpense(tripId, "築地午餐", 1500, ExpenseCategory.FOOD, am, PaymentMethod.CASH, listOf(hw, xm, am), occurredAt = day(2, 13))
        addExpense(tripId, "伴手禮", 1800, ExpenseCategory.SHOP, xm, PaymentMethod.CASH, listOf(hw, xm), occurredAt = day(2, 16))
        addExpense(tripId, "地鐵一日券", 600, ExpenseCategory.TRANSIT, hw, PaymentMethod.CASH, listOf(hw, xm, am), occurredAt = day(2, 9))
        addExpense(tripId, "早餐咖啡", 900, ExpenseCategory.FOOD, xm, PaymentMethod.CASH, listOf(hw, xm, am), occurredAt = day(3, 8))
        addExpense(tripId, "機場接送", 1200, ExpenseCategory.TRANSIT, am, PaymentMethod.TRANSFER, listOf(hw, xm, am), occurredAt = day(3, 14))
        addExpense(tripId, "藥妝採購", 2100, ExpenseCategory.SHOP, hw, PaymentMethod.CARD, listOf(hw, xm, am), occurredAt = day(3, 12))

    }
}
