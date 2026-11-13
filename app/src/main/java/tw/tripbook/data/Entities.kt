package tw.tripbook.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "trips")
data class TripEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val place: String = "",
    val currency: String = "TWD",
    val startDate: String = "",
    val endDate: String = "",
    val settled: Boolean = false,
    val poolCashCents: Long = 0,
    val poolStartingCashCents: Long = 0,
    val poolHolderMemberId: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "members",
    foreignKeys = [ForeignKey(
        entity = TripEntity::class, parentColumns = ["id"], childColumns = ["tripId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("tripId")]
)
data class MemberEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripId: Long,
    val name: String,
    /** Remaining cash (deducted when paying 現金 expenses). */
    val cashCents: Long = 0,
    /** Brought / starting cash set when creating the trip — shown as 初始現金. */
    val startingCashCents: Long = 0,
    val isMe: Boolean = false,
    val colorIndex: Int = 0
)

@Entity(
    tableName = "expenses",
    foreignKeys = [ForeignKey(
        entity = TripEntity::class, parentColumns = ["id"], childColumns = ["tripId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("tripId")]
)
data class ExpenseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripId: Long,
    val title: String,
    val amountCents: Long,
    val category: String,
    val payerMemberId: Long,
    val paymentMethod: String,
    val note: String = "",
    val receiptPath: String? = null,
    val occurredAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "expense_splits",
    primaryKeys = ["expenseId", "memberId"],
    foreignKeys = [ForeignKey(
        entity = ExpenseEntity::class, parentColumns = ["id"], childColumns = ["expenseId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("expenseId"), Index("memberId")]
)
data class ExpenseSplitEntity(
    val expenseId: Long,
    val memberId: Long,
    val shareCents: Long
)

@Entity(
    tableName = "cash_movements",
    foreignKeys = [ForeignKey(
        entity = TripEntity::class, parentColumns = ["id"], childColumns = ["tripId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("tripId"), Index("memberId")]
)
data class CashMovementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripId: Long,
    val memberId: Long?,
    val direction: String,
    val kind: String,
    val amountCents: Long,
    val note: String = "",
    val relatedExpenseId: Long? = null,
    val occurredAt: Long = System.currentTimeMillis()
)

/** Draft used when creating a trip. */
data class MemberDraft(val name: String, val startingCash: Long = 0)

/** 我要買：personal pre-trip shopping list item (no price until bought). */
@Entity(
    tableName = "shopping_items",
    foreignKeys = [ForeignKey(
        entity = TripEntity::class, parentColumns = ["id"], childColumns = ["tripId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("tripId")]
)
data class ShoppingItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripId: Long,
    val name: String,
    val qty: Int = 1,
    val note: String = "",
    val done: Boolean = false,
    val expenseId: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)
