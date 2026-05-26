package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TenantDao {
    @Query("SELECT * FROM tenants ORDER BY id ASC")
    fun getAllTenants(): Flow<List<Tenant>>

    @Query("SELECT * FROM tenants WHERE id = :id LIMIT 1")
    suspend fun getTenantById(id: Int): Tenant?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTenant(tenant: Tenant): Long

    @Delete
    suspend fun deleteTenant(tenant: Tenant)
}

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts WHERE tenantId = :tenantId ORDER BY code ASC")
    fun getAccountsByTenant(tenantId: Int): Flow<List<Account>>

    @Query("SELECT * FROM accounts WHERE tenantId = :tenantId ORDER BY code ASC")
    suspend fun getAccountsByTenantSync(tenantId: Int): List<Account>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAccount(account: Account): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAccounts(accounts: List<Account>)

    @Update
    suspend fun updateAccount(account: Account)

    @Delete
    suspend fun deleteAccount(account: Account)

    @Query("DELETE FROM accounts WHERE tenantId = :tenantId")
    suspend fun deleteAccountsByTenant(tenantId: Int)
}

@Dao
interface ProductDao {
    @Query("SELECT * FROM products WHERE tenantId = :tenantId ORDER BY name ASC")
    fun getProductsByTenant(tenantId: Int): Flow<List<Product>>

    @Query("SELECT * FROM products WHERE tenantId = :tenantId ORDER BY name ASC")
    suspend fun getProductsByTenantSync(tenantId: Int): List<Product>

    @Query("SELECT * FROM products WHERE id = :id LIMIT 1")
    suspend fun getProductById(id: Int): Product?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProduct(product: Product): Long

    @Update
    suspend fun updateProduct(product: Product)

    @Delete
    suspend fun deleteProduct(product: Product)
}

@Dao
interface JournalDao {
    @Query("SELECT * FROM journal_entries WHERE tenantId = :tenantId ORDER BY date DESC, id DESC")
    fun getEntriesByTenant(tenantId: Int): Flow<List<JournalEntry>>

    @Query("SELECT * FROM journal_entries WHERE tenantId = :tenantId ORDER BY date DESC, id DESC")
    suspend fun getEntriesByTenantSync(tenantId: Int): List<JournalEntry>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntry(entry: JournalEntry): Long

    @Delete
    suspend fun deleteEntry(entry: JournalEntry)

    @Query("SELECT * FROM journal_items WHERE journalEntryId = :entryId")
    suspend fun getItemsByEntryId(entryId: Int): List<JournalItem>

    @Query("SELECT * FROM journal_items WHERE journalEntryId IN (SELECT id FROM journal_entries WHERE tenantId = :tenantId)")
    fun getAllItemsByTenant(tenantId: Int): Flow<List<JournalItem>>

    @Query("SELECT * FROM journal_items WHERE journalEntryId IN (SELECT id FROM journal_entries WHERE tenantId = :tenantId)")
    suspend fun getAllItemsByTenantSync(tenantId: Int): List<JournalItem>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertJournalItem(item: JournalItem)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertJournalItems(items: List<JournalItem>)

    @Query("DELETE FROM journal_items WHERE journalEntryId = :entryId")
    suspend fun deleteItemsByEntryId(entryId: Int)
}

@Dao
interface InventoryDao {
    @Query("SELECT * FROM inventory_transactions WHERE tenantId = :tenantId ORDER BY date DESC")
    fun getTransactionsByTenant(tenantId: Int): Flow<List<InventoryTransaction>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: InventoryTransaction): Long
}

@Dao
interface ChatDao {
    @Query("SELECT * FROM chat_messages WHERE tenantId = :tenantId ORDER BY timestamp ASC")
    fun getChatMessagesByTenant(tenantId: Int): Flow<List<ChatMessage>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChatMessage(message: ChatMessage): Long

    @Query("DELETE FROM chat_messages WHERE tenantId = :tenantId")
    suspend fun clearChatByTenant(tenantId: Int)
}

@Dao
interface SubscriptionDao {
    @Query("SELECT * FROM subscriptions WHERE tenantId = :tenantId LIMIT 1")
    fun getSubscriptionByTenant(tenantId: Int): Flow<Subscription?>

    @Query("SELECT * FROM subscriptions WHERE tenantId = :tenantId LIMIT 1")
    suspend fun getSubscriptionByTenantSync(tenantId: Int): Subscription?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSubscription(subscription: Subscription)
}
