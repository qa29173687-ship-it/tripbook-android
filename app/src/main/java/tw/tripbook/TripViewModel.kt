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
    val shareStore = tw.tripbook.cloud.ShareStore(app)
    val ownerSync = tw.tripbook.cloud.OwnerSync(repo, shareStore, viewModelScope)
    val joined = MutableStateFlow(shareStore.joined())

    fun cloudIdFor(tripId: Long) = shareStore.ownedCloudId(tripId)
    suspend fun share(tripId: Long): String = ownerSync.share(tripId)
    fun refreshJoined() { joined.value = shareStore.joined() }
    /** Remove a companion-joined trip from this phone's list only; does not touch Firestore. */
    fun leaveShared(cloudId: String) {
        shareStore.removeJoined(cloudId)
        refreshJoined()
    }

    val trips: StateFlow<List<TripEntity>> =
        repo.observeTrips().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        viewModelScope.launch { repo.seedDemoIfEmpty() }
        runCatching { ownerSync.startAll() }
    }

    fun members(tripId: Long) = repo.observeMembers(tripId)
    fun expenses(tripId: Long) = repo.observeExpenses(tripId)
    fun trip(tripId: Long) = repo.observeTrip(tripId)

    suspend fun balances(tripId: Long) = repo.computeBalances(tripId)
    suspend fun transfers(tripId: Long) = repo.computeTransfers(tripId)
    suspend fun timeline(tripId: Long, memberId: Long?) = repo.cashTimeline(tripId, memberId)
    suspend fun getExpense(id: Long) = repo.getExpense(id)
    suspend fun splitsFor(expenseId: Long) = repo.splitsForExpense(expenseId)
    suspend fun listMembers(tripId: Long) = repo.listMembers(tripId)
    suspend fun listExpenses(tripId: Long) = repo.listExpenses(tripId)

    fun createTrip(
        name: String, place: String, currency: String,
        start: String, end: String,
        drafts: List<MemberDraft>,
        onDone: (Long) -> Unit
    ) = viewModelScope.launch {
        val id = repo.createTrip(name, place, currency, start, end, drafts, poolStarting = 0)
        onDone(id)
    }

    fun updateTripMeta(
        tripId: Long, name: String, place: String, currency: String,
        start: String, end: String, onDone: () -> Unit = {}
    ) = viewModelScope.launch {
        val t = repo.getTrip(tripId) ?: return@launch
        repo.updateTrip(
            t.copy(
                name = name.trim(),
                place = place.trim(),
                currency = currency,
                startDate = start,
                endDate = end
            )
        )
        onDone()
    }

    fun addMember(tripId: Long, name: String, startingCash: Long = 0) =
        viewModelScope.launch { repo.addMember(tripId, name, startingCash) }

    fun deleteMember(memberId: Long, onResult: (Boolean) -> Unit = {}) = viewModelScope.launch {
        onResult(repo.tryDeleteMember(memberId))
    }

    fun setStartingCash(memberId: Long, amount: Long) =
        viewModelScope.launch { repo.setStartingCash(memberId, amount) }

    fun addExpense(
        tripId: Long, title: String, amount: Long, category: ExpenseCategory,
        payerId: Long, method: PaymentMethod, participants: List<Long>,
        note: String, receiptPath: String?, occurredAt: Long, onDone: () -> Unit
    ) = viewModelScope.launch {
        repo.addExpense(tripId, title, amount, category, payerId, method, participants, note, receiptPath, occurredAt)
        onDone()
    }

    fun updateExpense(
        expenseId: Long, title: String, amount: Long, category: ExpenseCategory,
        payerId: Long, method: PaymentMethod, participants: List<Long>,
        note: String, receiptPath: String?, occurredAt: Long, onDone: () -> Unit
    ) = viewModelScope.launch {
        repo.updateExpense(expenseId, title, amount, category, payerId, method, participants, note, receiptPath, occurredAt)
        onDone()
    }

    fun deleteExpense(id: Long, onDone: () -> Unit = {}) = viewModelScope.launch {
        repo.deleteExpense(id)
        onDone()
    }

    fun shopping(tripId: Long) = repo.observeShopping(tripId)
    fun addShoppingItem(tripId: Long, name: String, qty: Int, note: String, photoPath: String? = null) =
        viewModelScope.launch { repo.addShoppingItem(tripId, name, qty, note, photoPath) }
    fun updateShoppingItem(id: Long, name: String, qty: Int, note: String, photoPath: String?) =
        viewModelScope.launch { repo.updateShoppingItem(id, name, qty, note, photoPath) }
    fun deleteShoppingItem(id: Long) = viewModelScope.launch { repo.deleteShoppingItem(id) }
    fun markBought(itemId: Long, amount: Long, method: PaymentMethod, onDone: () -> Unit = {}) =
        viewModelScope.launch { repo.markBought(itemId, amount, method); onDone() }
    fun unmarkBought(itemId: Long) = viewModelScope.launch { repo.unmarkBought(itemId) }

    fun markSettled(tripId: Long, settled: Boolean) = viewModelScope.launch { repo.markSettled(tripId, settled) }
    fun deleteTrip(id: Long) = viewModelScope.launch {
        ownerSync.stop(id)
        shareStore.clearOwned(id) // keep the cloud copy; it can be restored later
        repo.deleteTrip(id)
    }

    suspend fun me(tripId: Long): MemberEntity? = repo.listMembers(tripId).firstOrNull { it.isMe }
        ?: repo.listMembers(tripId).firstOrNull()
}
