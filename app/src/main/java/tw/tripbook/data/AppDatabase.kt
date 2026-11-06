package tw.tripbook.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        TripEntity::class, MemberEntity::class, ExpenseEntity::class,
        ExpenseSplitEntity::class, CashMovementEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun trips(): TripDao
    abstract fun members(): MemberDao
    abstract fun expenses(): ExpenseDao
    abstract fun cash(): CashDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null
        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext, AppDatabase::class.java, "tripbook.db"
                ).fallbackToDestructiveMigration().build().also { INSTANCE = it }
            }
    }
}
