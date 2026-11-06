package tw.tripbook

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import tw.tripbook.data.*
import tw.tripbook.domain.*

class TripViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = Repository(AppDatabase.get(app))

    val trips: StateFlow<List<TripEntity>> =
        repo.observeTrips().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        viewModelScope.launch { repo.seedDemoIfEmpty() }
    }

    fun members(tripId: Long) = repo.observeMembers(tripId)
    fun expenses(tripId: Long) = repo.observeExpenses(tripId)
    fun trip(tripId: Long) = repo.observeTrip(tripId)

    suspend fun balances(tripId: Long) = repo.computeBalances(tripId)
    suspend fun transfers(tripId: Long) = repo.computeTransfers(tripId)
    suspend fun timeline(tripId: Long, memberId: Long?) = repo.cashTimeline(tripId, memberId)

    fun createTrip(
        name: String, place: String, currency: String,
        start: String, end: String, memberNames: List<String>, onDone: (Long) -> Unit
    ) = viewModelScope.launch {
        val id = repo.createTrip(name, place, currency, start, end, memberNames)
        onDone(id)
    }

    fun addMember(tripId: Long, name: String) = viewModelScope.launch { repo.addMember(tripId, name) }
    fun deleteMember(memberId: Long, onResult: (Boolean) -> Unit = {}) = viewModelScope.launch {
        onResult(repo.tryDeleteMember(memberId))
    }

    fun setMemberCash(memberId: Long, amount: Long, note: String = "手動調整") =
        viewModelScope.launch { repo.setMemberCashDirect(memberId, amount, note) }

    fun setPool(tripId: Long, amount: Long, holderId: Long?) =
        viewModelScope.launch { repo.setPoolCashDirect(tripId, amount, holderId) }

    fun addCashMovement(
        tripId: Long, memberId: Long?, direction: CashDirection,
        kind: CashKind, amount: Long, note: String, onDone: () -> Unit = {}
    ) = viewModelScope.launch {
        repo.addCashMovement(tripId, memberId, direction, kind, amount, note)
        onDone()
    }

    fun addExpense(
        tripId: Long, title: String, amount: Long, category: ExpenseCategory,
        payerId: Long, method: PaymentMethod, participants: List<Long>,
        note: String, receiptPath: String?, onDone: () -> Unit
    ) = viewModelScope.launch {
        repo.addExpense(tripId, title, amount, category, payerId, method, participants, note, receiptPath)
        onDone()
    }

    fun deleteExpense(id: Long) = viewModelScope.launch { repo.deleteExpense(id) }
    fun markSettled(tripId: Long, settled: Boolean) = viewModelScope.launch { repo.markSettled(tripId, settled) }
    fun deleteTrip(id: Long) = viewModelScope.launch { repo.deleteTrip(id) }
    fun updateTrip(trip: TripEntity) = viewModelScope.launch { repo.updateTrip(trip) }

    suspend fun me(tripId: Long): MemberEntity? = repo.listMembers(tripId).firstOrNull { it.isMe }
        ?: repo.listMembers(tripId).firstOrNull()
}
