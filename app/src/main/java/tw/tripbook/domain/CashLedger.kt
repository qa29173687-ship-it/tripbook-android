package tw.tripbook.domain

/**
 * Pure cash-on-hand ledger helpers.
 * Personal cash and shared pool are separate balances.
 * Shared trip expenses paid by CASH/POOL adjust those balances;
 * personal IN/OUT movements never become split expenses.
 */
object CashLedger {
    fun applyExpensePayment(
        personalCash: Long,
        poolCash: Long,
        amountCents: Long,
        method: PaymentMethod
    ): Pair<Long, Long> {
        require(amountCents >= 0)
        return when (method) {
            PaymentMethod.CASH -> (personalCash - amountCents) to poolCash
            PaymentMethod.POOL -> personalCash to (poolCash - amountCents)
            PaymentMethod.CARD, PaymentMethod.TRANSFER -> personalCash to poolCash
        }
    }

    fun reverseExpensePayment(
        personalCash: Long,
        poolCash: Long,
        amountCents: Long,
        method: PaymentMethod
    ): Pair<Long, Long> {
        require(amountCents >= 0)
        return when (method) {
            PaymentMethod.CASH -> (personalCash + amountCents) to poolCash
            PaymentMethod.POOL -> personalCash to (poolCash + amountCents)
            PaymentMethod.CARD, PaymentMethod.TRANSFER -> personalCash to poolCash
        }
    }

    fun credit(balance: Long, amountCents: Long): Long {
        require(amountCents >= 0)
        return balance + amountCents
    }

    fun debit(balance: Long, amountCents: Long): Long {
        require(amountCents >= 0)
        return balance - amountCents
    }

    fun timeline(
        startingBalance: Long,
        movements: List<Triple<CashDirection, Long, CashKind>>
    ): List<Long> {
        var bal = startingBalance
        return movements.map { (dir, amt, _) ->
            bal = when (dir) {
                CashDirection.IN -> credit(bal, amt)
                CashDirection.OUT -> debit(bal, amt)
            }
            bal
        }
    }

    fun replaceExpensePayment(
        personalCash: Long,
        poolCash: Long,
        oldAmount: Long,
        oldMethod: PaymentMethod,
        newAmount: Long,
        newMethod: PaymentMethod
    ): Pair<Long, Long> {
        val (p1, pool1) = reverseExpensePayment(personalCash, poolCash, oldAmount, oldMethod)
        return applyExpensePayment(p1, pool1, newAmount, newMethod)
    }
}
