package tw.tripbook

import org.junit.Assert.*
import org.junit.Test
import tw.tripbook.domain.*
import tw.tripbook.util.Dates

class LogicTest {
    @Test fun equalSplitThree() {
        val s = SplitMath.equalSplit(1200, listOf(1, 2, 3))
        assertEquals(listOf(400L, 400L, 400L), s.map { it.amountCents })
    }

    @Test fun equalSplitRemainder() {
        val s = SplitMath.equalSplit(1000, listOf(1, 2, 3))
        assertEquals(1000L, s.sumOf { it.amountCents })
        assertEquals(listOf(334L, 333L, 333L), s.map { it.amountCents })
    }

    @Test fun equalSplitExclude() {
        val s = SplitMath.equalSplit(1800, listOf(1, 2))
        assertEquals(listOf(900L, 900L), s.map { it.amountCents })
    }

    @Test fun perPersonShareFloor() {
        assertEquals(400L, SplitMath.perPersonShare(1200, 3))
        assertEquals(333L, SplitMath.perPersonShare(1000, 3))
        assertEquals(0L, SplitMath.perPersonShare(1000, 0))
    }

    @Test fun tokyoDemoNetsAndSettlement() {
        val paid = mapOf(1L to 11100L, 2L to 3900L, 3L to 3600L)
        val share = mapOf(1L to 6500L, 2L to 6500L, 3L to 5600L)
        val bals = SplitMath.nets(paid, share, listOf(1, 2, 3))
        assertEquals(4600L, bals.first { it.memberId == 1L }.netCents)
        assertEquals(-2600L, bals.first { it.memberId == 2L }.netCents)
        assertEquals(-2000L, bals.first { it.memberId == 3L }.netCents)
        val t = SplitMath.minimizeTransfers(bals)
        assertEquals(2, t.size)
        assertTrue(t.any { it.fromMemberId == 2L && it.toMemberId == 1L && it.amountCents == 2600L })
        assertTrue(t.any { it.fromMemberId == 3L && it.toMemberId == 1L && it.amountCents == 2000L })
        assertEquals(0L, bals.sumOf { it.netCents })
    }

    @Test fun minimizeTransfersThreeWay() {
        val bals = listOf(
            MemberBalance(1, 0, 0, 500),
            MemberBalance(2, 0, 0, -200),
            MemberBalance(3, 0, 0, -300)
        )
        val t = SplitMath.minimizeTransfers(bals)
        assertEquals(2, t.size)
        assertEquals(500L, t.sumOf { it.amountCents })
    }

    @Test fun cashExpenseDeductAndReverse() {
        val (p1, pool1) = CashLedger.applyExpensePayment(5000, 3000, 1200, PaymentMethod.CASH)
        assertEquals(3800L, p1)
        assertEquals(3000L, pool1)
        val (p2, pool2) = CashLedger.reverseExpensePayment(p1, pool1, 1200, PaymentMethod.CASH)
        assertEquals(5000L, p2)
        assertEquals(3000L, pool2)
    }

    @Test fun poolExpenseDeductAndReverse() {
        val (p1, pool1) = CashLedger.applyExpensePayment(5000, 3000, 800, PaymentMethod.POOL)
        assertEquals(5000L, p1)
        assertEquals(2200L, pool1)
        val (_, pool2) = CashLedger.reverseExpensePayment(p1, pool1, 800, PaymentMethod.POOL)
        assertEquals(3000L, pool2)
    }

    @Test fun cardDoesNotTouchCash() {
        val (p, pool) = CashLedger.applyExpensePayment(5000, 3000, 6000, PaymentMethod.CARD)
        assertEquals(5000L, p)
        assertEquals(3000L, pool)
    }

    @Test fun cashCreditDebit() {
        assertEquals(8000L, CashLedger.credit(0, 8000))
        assertEquals(7500L, CashLedger.debit(8000, 500))
        assertEquals(8500L, CashLedger.credit(7500, 1000))
    }

    @Test fun cashTimelineRunningBalance() {
        val after = CashLedger.timeline(
            0,
            listOf(
                Triple(CashDirection.IN, 8000L, CashKind.ATM),
                Triple(CashDirection.OUT, 600L, CashKind.EXPENSE),
                Triple(CashDirection.OUT, 500L, CashKind.TOP_UP)
            )
        )
        assertEquals(listOf(8000L, 7400L, 6900L), after)
    }

    @Test fun replaceExpensePaymentMethod() {
        val (p, pool) = CashLedger.replaceExpensePayment(3800, 3000, 1200, PaymentMethod.CASH, 1200, PaymentMethod.CARD)
        assertEquals(5000L, p)
        assertEquals(3000L, pool)
    }

    @Test fun editExpenseMovesCashBetweenPayers() {
        // A paid 1200 cash → remaining 3800; edit to B paid cash → A restored, B deducted
        var a = 5000L
        var b = 5000L
        a = CashLedger.applyExpensePayment(a, 0, 1200, PaymentMethod.CASH).first
        assertEquals(3800L, a)
        val (a2, _) = CashLedger.replaceExpensePayment(a, 0, 1200, PaymentMethod.CASH, 1200, PaymentMethod.CARD)
        a = a2
        assertEquals(5000L, a)
        b = CashLedger.applyExpensePayment(b, 0, 1200, PaymentMethod.CASH).first
        assertEquals(3800L, b)
        assertEquals(5000L, a)
    }

    @Test fun editExpenseAmountAdjustsRemaining() {
        var rem = 10_000L
        rem = CashLedger.applyExpensePayment(rem, 0, 3000, PaymentMethod.CASH).first
        assertEquals(7000L, rem)
        rem = CashLedger.replaceExpensePayment(rem, 0, 3000, PaymentMethod.CASH, 1500, PaymentMethod.CASH).first
        assertEquals(8500L, rem)
    }

    @Test fun startingCashUnchangedWhenExpenseDeductsRemaining() {
        val starting = 8000L
        var remaining = starting
        remaining = CashLedger.applyExpensePayment(remaining, 0, 600, PaymentMethod.CASH).first
        assertEquals(8000L, starting)
        assertEquals(7400L, remaining)
    }

    @Test fun adjustStartingKeepsSpentGap() {
        val oldStart = 8000L
        var remaining = 7400L
        val spent = oldStart - remaining
        val newStart = 10000L
        remaining = (newStart - spent).coerceAtLeast(0)
        assertEquals(600L, spent)
        assertEquals(9400L, remaining)
    }

    @Test fun remainingAfterCashExpenseAndDelete() {
        val starting = 100_000L
        var remaining = starting
        remaining = CashLedger.applyExpensePayment(remaining, 0, 3000, PaymentMethod.CASH).first
        assertEquals(97_000L, remaining)
        assertEquals(100_000L, starting)
        remaining = CashLedger.reverseExpensePayment(remaining, 0, 3000, PaymentMethod.CASH).first
        assertEquals(100_000L, remaining)
    }

    @Test fun companionsCashIndependent() {
        var me = 100_000L
        var friend = 50_000L
        me = CashLedger.applyExpensePayment(me, 0, 3000, PaymentMethod.CASH).first
        assertEquals(97_000L, me)
        assertEquals(50_000L, friend)
        friend = CashLedger.applyExpensePayment(friend, 0, 1200, PaymentMethod.CASH).first
        assertEquals(97_000L, me)
        assertEquals(48_800L, friend)
    }

    @Test fun cardDoesNotChangeRemaining() {
        val starting = 100_000L
        var remaining = starting
        remaining = CashLedger.applyExpensePayment(remaining, 0, 3000, PaymentMethod.CARD).first
        assertEquals(starting, remaining)
        remaining = CashLedger.applyExpensePayment(remaining, 0, 3000, PaymentMethod.TRANSFER).first
        assertEquals(starting, remaining)
    }

    @Test fun defaultExpenseIsoInsideTripIsToday() {
        val today = Dates.todayIso()
        assertEquals(today, Dates.defaultExpenseIso(today, today))
    }

    @Test fun defaultExpenseIsoAfterTripIsEnd() {
        assertEquals("2026-01-05", Dates.defaultExpenseIso("2026-01-01", "2026-01-05"))
    }

    @Test fun clampExpenseDateToTrip() {
        assertEquals("2026-11-01", Dates.clampIsoToRange("2026-10-01", "2026-11-01", "2026-11-03"))
        assertEquals("2026-11-03", Dates.clampIsoToRange("2026-12-01", "2026-11-01", "2026-11-03"))
        assertEquals("2026-11-02", Dates.clampIsoToRange("2026-11-02", "2026-11-01", "2026-11-03"))
    }


    @Test fun blankItemTitleBecomesExpense() {
        fun displayTitle(raw: String) = raw.trim().ifBlank { "支出" }
        assertEquals("一蘭拉麵", displayTitle("一蘭拉麵"))
        assertEquals("支出", displayTitle(""))
        assertEquals("支出", displayTitle("   "))
    }

    @Test fun settleCopyHasNoRemainingCashLabel() {
        val chat = buildString {
            appendLine("【東京三日遊 結算】")
            appendLine("小美 轉給 宏威 NT$2,600")
            appendLine("—")
            appendLine("總消費明細：")
            appendLine("· 新宿飯店 NT$6,000｜宏威付｜宏威、小美、阿明｜刷卡")
        }
        assertFalse(chat.contains("剩餘現金"))
        assertTrue(chat.contains("總消費明細"))
        assertTrue(chat.contains("小美 轉給 宏威"))
    }
}
