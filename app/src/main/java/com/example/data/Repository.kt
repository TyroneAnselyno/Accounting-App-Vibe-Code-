package com.example.data

import android.content.Context
import androidx.room.Room
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class Repository(private val db: AppDatabase) {

    // --- Tenant ---
    val allTenants: Flow<List<Tenant>> = db.tenantDao().getAllTenants()
    suspend fun getTenantById(id: Int) = db.tenantDao().getTenantById(id)
    suspend fun insertTenant(tenant: Tenant): Int = withContext(Dispatchers.IO) {
        db.tenantDao().insertTenant(tenant).toInt()
    }

    // --- Account ---
    fun getAccountsForTenant(tenantId: Int): Flow<List<Account>> = db.accountDao().getAccountsByTenant(tenantId)
    suspend fun getAccountsForTenantSync(tenantId: Int): List<Account> = db.accountDao().getAccountsByTenantSync(tenantId)
    suspend fun insertAccount(account: Account) = db.accountDao().insertAccount(account)
    suspend fun updateAccount(account: Account) = db.accountDao().updateAccount(account)
    suspend fun deleteAccount(account: Account) = db.accountDao().deleteAccount(account)

    // --- Product ---
    fun getProductsForTenant(tenantId: Int): Flow<List<Product>> = db.productDao().getProductsByTenant(tenantId)
    suspend fun getProductsForTenantSync(tenantId: Int): List<Product> = db.productDao().getProductsByTenantSync(tenantId)
    suspend fun getProductById(id: Int) = db.productDao().getProductById(id)
    suspend fun insertProduct(product: Product) = db.productDao().insertProduct(product)
    suspend fun updateProduct(product: Product) = db.productDao().updateProduct(product)
    suspend fun deleteProduct(product: Product) = db.productDao().deleteProduct(product)

    // --- Journal ---
    fun getEntriesForTenant(tenantId: Int): Flow<List<JournalEntry>> = db.journalDao().getEntriesByTenant(tenantId)
    suspend fun getEntriesForTenantSync(tenantId: Int): List<JournalEntry> = db.journalDao().getEntriesByTenantSync(tenantId)
    fun getAllJournalItemsForTenant(tenantId: Int): Flow<List<JournalItem>> = db.journalDao().getAllItemsByTenant(tenantId)
    suspend fun getAllJournalItemsForTenantSync(tenantId: Int): List<JournalItem> = db.journalDao().getAllItemsByTenantSync(tenantId)
    suspend fun insertJournalItems(items: List<JournalItem>) = db.journalDao().insertJournalItems(items)

    suspend fun insertManualJournal(
        tenantId: Int,
        description: String,
        items: List<JournalItem>,
        date: Long = System.currentTimeMillis()
    ): Long = withContext(Dispatchers.IO) {
        val entry = JournalEntry(tenantId = tenantId, description = description, source = "MANUAL", date = date)
        val entryId = db.journalDao().insertEntry(entry).toInt()
        val mappedItems = items.map { it.copy(journalEntryId = entryId) }
        db.journalDao().insertJournalItems(mappedItems)
        entryId.toLong()
    }

    suspend fun deleteJournalEntry(entry: JournalEntry) = withContext(Dispatchers.IO) {
        db.journalDao().deleteItemsByEntryId(entry.id)
        db.journalDao().deleteEntry(entry)
    }

    // --- Chat & Audit Trail ---
    fun getChatMessagesForTenant(tenantId: Int): Flow<List<ChatMessage>> = db.chatDao().getChatMessagesByTenant(tenantId)
    suspend fun insertChatMessage(message: ChatMessage) = db.chatDao().insertChatMessage(message)
    suspend fun clearChatForTenant(tenantId: Int) = db.chatDao().clearChatByTenant(tenantId)

    // --- Subscription ---
    fun getSubscriptionForTenant(tenantId: Int): Flow<Subscription?> = db.subscriptionDao().getSubscriptionByTenant(tenantId)
    suspend fun getSubscriptionForTenantSync(tenantId: Int): Subscription? = db.subscriptionDao().getSubscriptionByTenantSync(tenantId)
    suspend fun updateSubscription(subscription: Subscription) = db.subscriptionDao().insertSubscription(subscription)

    // --- Inventory System ---
    fun getInventoryTransactions(tenantId: Int): Flow<List<InventoryTransaction>> = db.inventoryDao().getTransactionsByTenant(tenantId)

    suspend fun registerPurchase(
        tenantId: Int,
        productId: Int,
        quantity: Int,
        unitPrice: Double,
        date: Long = System.currentTimeMillis()
    ) = withContext(Dispatchers.IO) {
        val product = db.productDao().getProductById(productId) ?: return@withContext
        val oldQty = product.stockQuantity
        val oldPrice = product.averagePurchasePrice
        
        val newQty = oldQty + quantity
        val newAvgPrice = if (newQty > 0) {
            ((oldQty * oldPrice) + (quantity * unitPrice)) / newQty
        } else {
            0.0
        }

        // Update product
        val updatedProduct = product.copy(
            stockQuantity = newQty,
            averagePurchasePrice = newAvgPrice
        )
        db.productDao().insertProduct(updatedProduct)

        // Find standard Cash/Bank and Inventory (Persediaan) Accounts
        val coas = db.accountDao().getAccountsByTenantSync(tenantId)
        val cashCode = coas.find { it.category == "Aset Lancar" && (it.name.contains("Kas", true) || it.name.contains("Cash", true)) }?.code ?: "1101"
        val cashName = coas.find { it.category == "Aset Lancar" && (it.name.contains("Kas", true) || it.name.contains("Cash", true)) }?.name ?: "Kas"
        val inventoryCode = coas.find { it.category == "Aset Lancar" && (it.name.contains("Persediaan", true) || it.name.contains("Inventory", true)) }?.code ?: "1103"
        val inventoryName = coas.find { it.category == "Aset Lancar" && (it.name.contains("Persediaan", true) || it.name.contains("Inventory", true)) }?.name ?: "Persediaan Barang"

        val totalAmount = quantity * unitPrice

        // Create double-entry journal (Debit: Persediaan, Credit: Kas)
        val entryId = db.journalDao().insertEntry(
            JournalEntry(
                tenantId = tenantId,
                description = "Beli ${product.name} (Qty: $quantity @Rp $unitPrice)",
                source = "MANUAL",
                date = date
            )
        ).toInt()

        db.journalDao().insertJournalItems(
            listOf(
                JournalItem(journalEntryId = entryId, accountCode = inventoryCode, accountName = inventoryName, debit = totalAmount, credit = 0.0),
                JournalItem(journalEntryId = entryId, accountCode = cashCode, accountName = cashName, debit = 0.0, credit = totalAmount)
            )
        )

        // Register inventory transaction
        db.inventoryDao().insertTransaction(
            InventoryTransaction(
                tenantId = tenantId,
                productId = productId,
                journalEntryId = entryId,
                date = date,
                type = "IN",
                quantity = quantity,
                unitPrice = unitPrice
            )
        )
    }

    suspend fun registerSale(
        tenantId: Int,
        productId: Int,
        quantity: Int,
        unitSalesPrice: Double,
        date: Long = System.currentTimeMillis()
    ) = withContext(Dispatchers.IO) {
        val product = db.productDao().getProductById(productId) ?: return@withContext
        val oldQty = product.stockQuantity
        val avgCost = product.averagePurchasePrice

        val newQty = maxOf(0, oldQty - quantity)
        
        // Update product stock
        val updatedProduct = product.copy(stockQuantity = newQty)
        db.productDao().insertProduct(updatedProduct)

        // Find standard Cash/Bank, Revenue, HPP, and Persediaan COAs
        val coas = db.accountDao().getAccountsByTenantSync(tenantId)
        val cashCode = coas.find { it.category == "Aset Lancar" && (it.name.contains("Kas", true) || it.name.contains("Cash", true)) }?.code ?: "1101"
        val cashName = coas.find { it.category == "Aset Lancar" && (it.name.contains("Kas", true) || it.name.contains("Cash", true)) }?.name ?: "Kas"
        
        val revenueCode = coas.find { it.category == "Pendapatan" && (it.name.contains("Pendapatan", true) || it.name.contains("Penjualan", true)) }?.code ?: "4101"
        val revenueName = coas.find { it.category == "Pendapatan" && (it.name.contains("Pendapatan", true) || it.name.contains("Penjualan", true)) }?.name ?: "Pendapatan Penjualan"

        val hppCode = coas.find { it.category == "Beban" && (it.name.contains("Harga Pokok", true) || it.name.contains("HPP", true) || it.name.contains("COGS", true)) }?.code ?: "5101"
        val hppName = coas.find { it.category == "Beban" && (it.name.contains("Harga Pokok", true) || it.name.contains("HPP", true) || it.name.contains("COGS", true)) }?.name ?: "Beban HPP (COGS)"

        val inventoryCode = coas.find { it.category == "Aset Lancar" && (it.name.contains("Persediaan", true) || it.name.contains("Inventory", true)) }?.code ?: "1103"
        val inventoryName = coas.find { it.category == "Aset Lancar" && (it.name.contains("Persediaan", true) || it.name.contains("Inventory", true)) }?.name ?: "Persediaan Barang"

        val totalSaleValue = quantity * unitSalesPrice
        val totalCogsValue = quantity * avgCost

        // Insert double-entry journal:
        // 1. Debit Kas, Credit Pendapatan Penjualan (Revenue recognition)
        // 2. Debit HPP, Credit Persediaan (COGS matching)
        val entryId = db.journalDao().insertEntry(
            JournalEntry(
                tenantId = tenantId,
                description = "Jual ${product.name} (Qty: $quantity @Rp $unitSalesPrice)",
                source = "MANUAL",
                date = date
            )
        ).toInt()

        val items = mutableListOf(
            JournalItem(journalEntryId = entryId, accountCode = cashCode, accountName = cashName, debit = totalSaleValue, credit = 0.0),
            JournalItem(journalEntryId = entryId, accountCode = revenueCode, accountName = revenueName, debit = 0.0, credit = totalSaleValue)
        )

        if (totalCogsValue > 0) {
            items.add(JournalItem(journalEntryId = entryId, accountCode = hppCode, accountName = hppName, debit = totalCogsValue, credit = 0.0))
            items.add(JournalItem(journalEntryId = entryId, accountCode = inventoryCode, accountName = inventoryName, debit = 0.0, credit = totalCogsValue))
        }

        db.journalDao().insertJournalItems(items)

        // Register inventory transaction
        db.inventoryDao().insertTransaction(
            InventoryTransaction(
                tenantId = tenantId,
                productId = productId,
                journalEntryId = entryId,
                date = date,
                type = "OUT",
                quantity = quantity,
                unitPrice = unitSalesPrice,
                costCalculatedPercent = totalCogsValue
            )
        )
    }

    // --- Seeding & Niche Templates ---
    suspend fun seedTenantWithTemplate(tenantId: Int, businessType: String) = withContext(Dispatchers.IO) {
        val accounts = when (businessType) {
            "F&B" -> listOf(
                Account(tenantId = tenantId, code = "1101", name = "Kas Toko Kuliner", category = "Aset Lancar", isSystem = true),
                Account(tenantId = tenantId, code = "1102", name = "Bank BCA Bisnis", category = "Aset Lancar", isSystem = true),
                Account(tenantId = tenantId, code = "1103", name = "Persediaan Bahan Baku", category = "Aset Lancar", isSystem = true),
                Account(tenantId = tenantId, code = "1201", name = "Peralatan Dapur Utama", category = "Aset Tetap", isSystem = true),
                Account(tenantId = tenantId, code = "2101", name = "Utang Bahan Kuliner", category = "Kewajiban", isSystem = true),
                Account(tenantId = tenantId, code = "3101", name = "Modal Pemilik Kuliner", category = "Ekuitas", isSystem = true),
                Account(tenantId = tenantId, code = "4101", name = "Pendapatan Makanan & Minuman", category = "Pendapatan", isSystem = true),
                Account(tenantId = tenantId, code = "5101", name = "Harga Pokok Penjualan (HPP)", category = "Beban", isSystem = true),
                Account(tenantId = tenantId, code = "5201", name = "Beban Gaji Karyawan", category = "Beban", isSystem = true),
                Account(tenantId = tenantId, code = "5202", name = "Beban Sewa Ruko", category = "Beban", isSystem = true)
            )
            "Jasa" -> listOf(
                Account(tenantId = tenantId, code = "1101", name = "Kas Jasa", category = "Aset Lancar", isSystem = true),
                Account(tenantId = tenantId, code = "1102", name = "Piutang Layanan Jasa", category = "Aset Lancar", isSystem = true),
                Account(tenantId = tenantId, code = "1201", name = "Mesin & Peralatan Kerja", category = "Aset Tetap", isSystem = true),
                Account(tenantId = tenantId, code = "2101", name = "Utang Sewa Alat", category = "Kewajiban", isSystem = true),
                Account(tenantId = tenantId, code = "3101", name = "Modal Usaha Jasa", category = "Ekuitas", isSystem = true),
                Account(tenantId = tenantId, code = "4101", name = "Pendapatan Jasa Layanan", category = "Pendapatan", isSystem = true),
                Account(tenantId = tenantId, code = "5201", name = "Beban Listrik, Air & Wi-Fi", category = "Beban", isSystem = true),
                Account(tenantId = tenantId, code = "5202", name = "Beban Perlengkapan Operasional", category = "Beban", isSystem = true)
            )
            "Retail" -> listOf(
                Account(tenantId = tenantId, code = "1101", name = "Kas Laci Toko", category = "Aset Lancar", isSystem = true),
                Account(tenantId = tenantId, code = "1102", name = "Bank BRI Bisnis", category = "Aset Lancar", isSystem = true),
                Account(tenantId = tenantId, code = "1103", name = "Persediaan Barang Dagang", category = "Aset Lancar", isSystem = true),
                Account(tenantId = tenantId, code = "1201", name = "Rak & Kulkas Display", category = "Aset Tetap", isSystem = true),
                Account(tenantId = tenantId, code = "2101", name = "Utang Dagang Supplier", category = "Kewajiban", isSystem = true),
                Account(tenantId = tenantId, code = "3101", name = "Modal Usaha Retail", category = "Ekuitas", isSystem = true),
                Account(tenantId = tenantId, code = "4101", name = "Pendapatan Jasa Penjualan Retail", category = "Pendapatan", isSystem = true),
                Account(tenantId = tenantId, code = "5101", name = "Harga Pokok Penjualan (HPP)", category = "Beban", isSystem = true),
                Account(tenantId = tenantId, code = "5201", name = "Beban Listrik Toko", category = "Beban", isSystem = true),
                Account(tenantId = tenantId, code = "5202", name = "Beban Kantong & Kemasan", category = "Beban", isSystem = true)
            )
            else -> listOf(
                Account(tenantId = tenantId, code = "1101", name = "Kas", category = "Aset Lancar", isSystem = true),
                Account(tenantId = tenantId, code = "1102", name = "Bank", category = "Aset Lancar", isSystem = true),
                Account(tenantId = tenantId, code = "1103", name = "Persediaan", category = "Aset Lancar", isSystem = true),
                Account(tenantId = tenantId, code = "3101", name = "Modal Pemilik", category = "Ekuitas", isSystem = true),
                Account(tenantId = tenantId, code = "4101", name = "Pendapatan Penjualan", category = "Pendapatan", isSystem = true),
                Account(tenantId = tenantId, code = "5101", name = "Beban HPP", category = "Beban", isSystem = true)
            )
        }

        db.accountDao().deleteAccountsByTenant(tenantId)
        db.accountDao().insertAccounts(accounts)

        // Seed default products
        if (businessType == "Retail" || businessType == "F&B" || businessType == "Custom") {
            val p1 = Product(tenantId = tenantId, name = "Sofa Klasik", stockQuantity = 5, averagePurchasePrice = 2000000.0, salesPrice = 3500000.0)
            val p2 = Product(tenantId = tenantId, name = "Bakso Sapi Premium", stockQuantity = 50, averagePurchasePrice = 12000.0, salesPrice = 25000.0)
            val p3 = Product(tenantId = tenantId, name = "Teh Manis Dingin", stockQuantity = 100, averagePurchasePrice = 1500.0, salesPrice = 5000.0)
            db.productDao().insertProduct(p1)
            db.productDao().insertProduct(p2)
            db.productDao().insertProduct(p3)
        }

        // Set Subscription to Active initially for demo convenience
        val sub = Subscription(
            tenantId = tenantId,
            status = "Active",
            plan = "Monthly (Rp450.000)",
            expiryDate = System.currentTimeMillis() + 14 * 24 * 60 * 60 * 1000 // 14 days
        )
        db.subscriptionDao().insertSubscription(sub)
    }

    companion object {
        @Volatile
        private var INSTANCE: Repository? = null

        fun getInstance(context: Context): Repository {
            return INSTANCE ?: synchronized(this) {
                val db = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "akunsaku_db"
                ).fallbackToDestructiveMigration().build()
                val repo = Repository(db)
                INSTANCE = repo
                repo
            }
        }
    }
}
