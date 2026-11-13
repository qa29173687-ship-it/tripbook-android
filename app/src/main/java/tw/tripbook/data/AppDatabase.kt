package tw.tripbook.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        TripEntity::class, MemberEntity::class, ExpenseEntity::class,
        ExpenseSplitEntity::class, CashMovementEntity::class, ShoppingItemEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun trips(): TripDao
    abstract fun members(): MemberDao
    abstract fun expenses(): ExpenseDao
    abstract fun cash(): CashDao
    abstract fun shopping(): ShoppingDao

    companion object {
        /** Keeps existing trips/expenses; only adds the 我要買 table. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `shopping_items` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `tripId` INTEGER NOT NULL, " +
                        "`name` TEXT NOT NULL, `qty` INTEGER NOT NULL, `note` TEXT NOT NULL, " +
                        "`done` INTEGER NOT NULL, `expenseId` INTEGER, `createdAt` INTEGER NOT NULL, " +
                        "FOREIGN KEY(`tripId`) REFERENCES `trips`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_shopping_items_tripId` ON `shopping_items` (`tripId`)")
            }
        }

        @Volatile private var INSTANCE: AppDatabase? = null
        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext, AppDatabase::class.java, "tripbook.db"
                ).addMigrations(MIGRATION_2_3).fallbackToDestructiveMigration().build().also { INSTANCE = it }
            }
    }
}
