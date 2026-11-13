package tw.tripbook.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TripDao {
    @Query("SELECT * FROM trips ORDER BY createdAt DESC")
    fun observeTrips(): Flow<List<TripEntity>>

    @Query("SELECT * FROM trips ORDER BY createdAt DESC")
    suspend fun listTrips(): List<TripEntity>

    @Query("SELECT * FROM trips WHERE id = :id")
    suspend fun get(id: Long): TripEntity?

    @Query("SELECT * FROM trips WHERE id = :id")
    fun observe(id: Long): Flow<TripEntity?>

    @Insert suspend fun insert(trip: TripEntity): Long
    @Update suspend fun update(trip: TripEntity)
    @Query("DELETE FROM trips WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface MemberDao {
    @Query("SELECT * FROM members WHERE tripId = :tripId ORDER BY id ASC")
    fun observeForTrip(tripId: Long): Flow<List<MemberEntity>>

    @Query("SELECT * FROM members WHERE tripId = :tripId ORDER BY id ASC")
    suspend fun listForTrip(tripId: Long): List<MemberEntity>

    @Query("SELECT * FROM members WHERE id = :id")
    suspend fun get(id: Long): MemberEntity?

    @Insert suspend fun insert(member: MemberEntity): Long
    @Update suspend fun update(member: MemberEntity)
    @Query("DELETE FROM members WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface ExpenseDao {
    @Query("SELECT * FROM expenses WHERE tripId = :tripId ORDER BY occurredAt DESC, id DESC")
    fun observeForTrip(tripId: Long): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expenses WHERE tripId = :tripId ORDER BY occurredAt ASC, id ASC")
    suspend fun listForTrip(tripId: Long): List<ExpenseEntity>

    @Query("SELECT * FROM expenses WHERE id = :id")
    suspend fun get(id: Long): ExpenseEntity?

    @Insert suspend fun insert(expense: ExpenseEntity): Long
    @Update suspend fun update(expense: ExpenseEntity)
    @Query("DELETE FROM expenses WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM expense_splits WHERE expenseId = :expenseId")
    suspend fun splitsFor(expenseId: Long): List<ExpenseSplitEntity>

    @Query("SELECT * FROM expense_splits WHERE expenseId IN (SELECT id FROM expenses WHERE tripId = :tripId)")
    suspend fun splitsForTrip(tripId: Long): List<ExpenseSplitEntity>

    @Insert suspend fun insertSplits(splits: List<ExpenseSplitEntity>)
    @Query("DELETE FROM expense_splits WHERE expenseId = :expenseId")
    suspend fun deleteSplits(expenseId: Long)
}

@Dao
interface CashDao {
    @Query("SELECT * FROM cash_movements WHERE tripId = :tripId AND memberId = :memberId ORDER BY occurredAt ASC, id ASC")
    suspend fun listForMember(tripId: Long, memberId: Long): List<CashMovementEntity>

    @Query("SELECT * FROM cash_movements WHERE tripId = :tripId AND memberId IS NULL ORDER BY occurredAt ASC, id ASC")
    suspend fun listForPool(tripId: Long): List<CashMovementEntity>

    @Query("SELECT * FROM cash_movements WHERE tripId = :tripId AND memberId = :memberId ORDER BY occurredAt ASC, id ASC")
    fun observeForMember(tripId: Long, memberId: Long): Flow<List<CashMovementEntity>>

    @Query("SELECT * FROM cash_movements WHERE tripId = :tripId AND memberId IS NULL ORDER BY occurredAt ASC, id ASC")
    fun observeForPool(tripId: Long): Flow<List<CashMovementEntity>>

    @Insert suspend fun insert(m: CashMovementEntity): Long
    @Query("DELETE FROM cash_movements WHERE relatedExpenseId = :expenseId")
    suspend fun deleteForExpense(expenseId: Long)
}

@Dao
interface ShoppingDao {
    @Query("SELECT * FROM shopping_items WHERE tripId = :tripId ORDER BY done ASC, createdAt ASC, id ASC")
    fun observeForTrip(tripId: Long): Flow<List<ShoppingItemEntity>>

    @Query("SELECT * FROM shopping_items WHERE tripId = :tripId ORDER BY createdAt")
    suspend fun listForTrip(tripId: Long): List<ShoppingItemEntity>

    @Query("SELECT * FROM shopping_items WHERE id = :id")
    suspend fun get(id: Long): ShoppingItemEntity?

    @Insert suspend fun insert(item: ShoppingItemEntity): Long
    @Update suspend fun update(item: ShoppingItemEntity)
    @Query("DELETE FROM shopping_items WHERE id = :id")
    suspend fun delete(id: Long)
    @Query("UPDATE shopping_items SET done = 0, expenseId = NULL WHERE expenseId = :expenseId")
    suspend fun unlinkExpense(expenseId: Long)
}
