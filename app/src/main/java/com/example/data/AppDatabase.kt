package com.example.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        Tenant::class,
        Account::class,
        Product::class,
        JournalEntry::class,
        JournalItem::class,
        InventoryTransaction::class,
        ChatMessage::class,
        Subscription::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun tenantDao(): TenantDao
    abstract fun accountDao(): AccountDao
    abstract fun productDao(): ProductDao
    abstract fun journalDao(): JournalDao
    abstract fun inventoryDao(): InventoryDao
    abstract fun chatDao(): ChatDao
    abstract fun subscriptionDao(): SubscriptionDao
}
