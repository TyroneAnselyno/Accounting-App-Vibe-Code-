package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import com.example.network.GeminiApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = Repository.getInstance(application)

    // --- Active Tenant States ---
    private val _activeTenantId = MutableStateFlow(1)
    val activeTenantId: StateFlow<Int> = _activeTenantId.asStateFlow()

    val tenants: StateFlow<List<Tenant>> = repository.allTenants
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeTenant: StateFlow<Tenant?> = combine(tenants, _activeTenantId) { list, id ->
        list.find { it.id == id }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // --- Subscription State ---
    val subscription: StateFlow<Subscription?> = _activeTenantId
        .flatMapLatest { id -> repository.getSubscriptionForTenant(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // --- Accounts State ---
    val accounts: StateFlow<List<Account>> = _activeTenantId
        .flatMapLatest { id -> repository.getAccountsForTenant(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- Products State ---
    val products: StateFlow<List<Product>> = _activeTenantId
        .flatMapLatest { id -> repository.getProductsForTenant(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- Journal Entries with Items ---
    val journalEntries: StateFlow<List<JournalEntry>> = _activeTenantId
        .flatMapLatest { id -> repository.getEntriesForTenant(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val journalItems: StateFlow<List<JournalItem>> = _activeTenantId
        .flatMapLatest { id -> repository.getAllJournalItemsForTenant(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- Chat Messages Status ---
    val chatHistory: StateFlow<List<ChatMessage>> = _activeTenantId
        .flatMapLatest { id -> repository.getChatMessagesForTenant(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isBotLoading = MutableStateFlow(false)
    val isBotLoading: StateFlow<Boolean> = _isBotLoading.asStateFlow()

    // --- Tax Settings ---
    private val _isIndividualOwnerPajak = MutableStateFlow(true) // Orang Pribadi vs Badan
    val isIndividualOwnerPajak: StateFlow<Boolean> = _isIndividualOwnerPajak.asStateFlow()

    private val _forcePreviousGrossRevenue = MutableStateFlow(0.0) // Manual input threshold booster
    val forcePreviousGrossRevenue: StateFlow<Double> = _forcePreviousGrossRevenue.asStateFlow()

    init {
        // Prepare initial mockup tenants to make the SaaS fully functional upon first load
        viewModelScope.launch(Dispatchers.IO) {
            repository.allTenants.first().let { currentTenants ->
                if (currentTenants.isEmpty()) {
                    val t1 = repository.insertTenant(Tenant(name = "Bakso Maju Sejahtera (F&B)", businessType = "F&B"))
                    val t2 = repository.insertTenant(Tenant(name = "Bersih Rapi Laundry (Jasa)", businessType = "Jasa"))
                    val t3 = repository.insertTenant(Tenant(name = "Saka Furniture Retail", businessType = "Retail"))

                    repository.seedTenantWithTemplate(t1, "F&B")
                    repository.seedTenantWithTemplate(t2, "Jasa")
                    repository.seedTenantWithTemplate(t3, "Retail")

                    _activeTenantId.value = t1
                } else {
                    _activeTenantId.value = currentTenants.first().id
                }
            }
        }
    }

    fun selectTenant(tenantId: Int) {
        _activeTenantId.value = tenantId
    }

    fun toggleIndividualOwner(isIndividual: Boolean) {
        _isIndividualOwnerPajak.value = isIndividual
    }

    fun updatePreviousGrossRevenue(amount: Double) {
        _forcePreviousGrossRevenue.value = amount
    }

    // --- COA Management ---
    fun createAccount(code: String, name: String, category: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val account = Account(tenantId = _activeTenantId.value, code = code, name = name, category = category)
            repository.insertAccount(account)
        }
    }

    fun updateAccount(account: Account) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateAccount(account)
        }
    }

    fun deleteAccount(account: Account) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteAccount(account)
        }
    }

    // --- Product Management ---
    fun createProduct(name: String, qty: Int, purchasePrice: Double, salesPrice: Double) {
        viewModelScope.launch(Dispatchers.IO) {
            val product = Product(
                tenantId = _activeTenantId.value,
                name = name,
                stockQuantity = qty,
                averagePurchasePrice = purchasePrice,
                salesPrice = salesPrice
            )
            repository.insertProduct(product)
        }
    }

    fun deleteProduct(product: Product) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteProduct(product)
        }
    }

    // --- Direct Manual Transaction Entry API ---
    fun createManualJournal(description: String, debitAccount: String, debitName: String, creditAccount: String, creditName: String, amount: Double) {
        viewModelScope.launch(Dispatchers.IO) {
            val item1 = JournalItem(journalEntryId = 0, accountCode = debitAccount, accountName = debitName, debit = amount, credit = 0.0)
            val item2 = JournalItem(journalEntryId = 0, accountCode = creditAccount, accountName = creditName, debit = 0.0, credit = amount)
            repository.insertManualJournal(_activeTenantId.value, description, listOf(item1, item2))
        }
    }

    // Delete single journal
    fun deleteJournal(entry: JournalEntry) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteJournalEntry(entry)
        }
    }

    // --- Chat Trigger NLP ---
    fun sendWhatsAppMessage(message: String) {
        if (message.isBlank()) return

        val currentTenantId = _activeTenantId.value
        viewModelScope.launch {
            // Check locked out status
            val currentSub = repository.getSubscriptionForTenantSync(currentTenantId)
            val isLocked = currentSub == null || currentSub.status == "Expired" || currentSub.status == "Unpaid"

            if (isLocked) {
                val botReply = "⚠️ *[RENEWAL REQUIRED]* Langganan AkunSaku Anda telah berakhir atau belum dibayar. Bot WhatsApp tidak dapat memproses jurnal saat ini.\n\nSilakan lakukan transfer atau scan QRIS di Dashboard untuk memperpanjang langganan Anda."
                repository.insertChatMessage(
                    ChatMessage(
                        tenantId = currentTenantId,
                        userMessage = message,
                        botReply = botReply,
                        status = "RENEWAL_REQUIRED"
                    )
                )
                return@launch
            }

            _isBotLoading.value = true

            // Gather structural COA and Product Context
            val coaList = repository.getAccountsForTenantSync(currentTenantId)
            val productsList = repository.getProductsForTenantSync(currentTenantId)

            val coasDesc = coaList.joinToString("\n") { "- Code: ${it.code}, Name: ${it.name}, Category: ${it.category}" }
            val productsDesc = productsList.joinToString("\n") { "- ID: ${it.id}, Name: ${it.name}, Base Stock: ${it.stockQuantity}, Buy Cost: ${it.averagePurchasePrice}, Sell Price: ${it.salesPrice}" }

            val response = GeminiApiClient.parseIndonesianChat(message, coasDesc, productsDesc)

            _isBotLoading.value = false

            if (response == "ERROR_API_KEY_MISSING") {
                val botReply = "⚠️ *Kunci API Gemini Belum Dikonfigurasi!*\n\nMasukkan API Key Anda di panel *Secrets* Google AI Studio dengan nama *GEMINI_API_KEY* untuk memulai pemrosesan WhatsApp NLP."
                repository.insertChatMessage(
                    ChatMessage(
                        tenantId = currentTenantId,
                        userMessage = message,
                        botReply = botReply,
                        status = "ERROR"
                    )
                )
                return@launch
            }

            try {
                val json = JSONObject(response)

                if (json.has("error")) {
                    val errorMsg = json.getString("error")
                    val botReply = "❌ *Gagal Mencatat Jurnal*:\n$errorMsg\n\nCoba deskripsikan transaksi dengan lebih detail, contoh:\n- \"Beli kopi sachet 100rb dari kas\"\n- \"Jual 2 Sofa Klasik tunai\""
                    repository.insertChatMessage(
                        ChatMessage(tenantId = currentTenantId, userMessage = message, botReply = botReply, status = "ERROR")
                    )
                } else {
                    val description = json.getString("description")
                    val inventoryAction = json.optJSONObject("inventoryAction")

                    if (inventoryAction != null) {
                        // Product stock movement flow
                        val productId = inventoryAction.getInt("productId")
                        val actionType = inventoryAction.getString("type") // IN or OUT
                        val quantity = inventoryAction.getInt("quantity")
                        val price = inventoryAction.getDouble("price")

                        if (actionType == "IN") {
                            repository.registerPurchase(currentTenantId, productId, quantity, price)
                        } else {
                            repository.registerSale(currentTenantId, productId, quantity, price)
                        }

                        val product = repository.getProductById(productId)
                        val botReply = "✅ *Jurnal Inventaris Otomatis Berhasil!*\n\n" +
                                "*Transaksi:* $description\n" +
                                "*Gerakan Stok:* ${product?.name ?: ""} (Qty: $quantity - ${if (actionType == "IN") "Masuk" else "Keluar"})\n" +
                                "*Nilai Satuan:* Rp " + String.format("%,.0f", price) + "\n\n" +
                                "Buku Jurnal SAK EMKM dan Laporan Keuangan Anda serta HPP telah disesuaikan secara real-time."

                        repository.insertChatMessage(
                            ChatMessage(tenantId = currentTenantId, userMessage = message, botReply = botReply, status = "SUCCESS")
                        )
                    } else {
                        // Standard Non-Inventory Double-Entry
                        val journalItemsArray = json.getJSONArray("journalItems")
                        val listItems = mutableListOf<JournalItem>()
                        for (i in 0 until journalItemsArray.length()) {
                            val jobj = journalItemsArray.getJSONObject(i)
                            listItems.add(
                                JournalItem(
                                    journalEntryId = 0,
                                    accountCode = jobj.getString("accountCode"),
                                    accountName = jobj.getString("accountName"),
                                    debit = jobj.optDouble("debit", 0.0),
                                    credit = jobj.optDouble("credit", 0.0)
                                )
                            )
                        }

                        val entryId = repository.insertManualJournal(currentTenantId, description, listItems)

                        val details = StringBuilder()
                        for (item in listItems) {
                            if (item.debit > 0) {
                                details.append("- [Dr] ${item.accountName} (${item.accountCode}): Rp ${String.format("%,.0f", item.debit)}\n")
                            } else {
                                details.append("- [Cr]   ${item.accountName} (${item.accountCode}): Rp ${String.format("%,.0f", item.credit)}\n")
                            }
                        }

                        val botReply = "✅ *Pencatatan Jurnal Berhasil!*\n\n" +
                                "*Deskripsi:* $description\n" +
                                "*Double Entry Jurnal:*\n$details\n" +
                                "Laporan keuangan Anda telah terupdate otomatis."

                        repository.insertChatMessage(
                            ChatMessage(tenantId = currentTenantId, userMessage = message, botReply = botReply, status = "SUCCESS")
                        )
                    }
                }
            } catch (e: Exception) {
                val botReply = "⚠️ *Gagal Membaca Respons AI.*\n\nRespons tidak sesuai format JSON yang kami harapkan. Silakan coba lagi dengan kalimat yang lebih eksplisit.\n\n_Detail: ${e.localizedMessage}_"
                repository.insertChatMessage(
                    ChatMessage(tenantId = currentTenantId, userMessage = message, botReply = botReply, status = "ERROR")
                )
            }
        }
    }

    fun clearChat() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.clearChatForTenant(_activeTenantId.value)
        }
    }

    // --- Create a New Business Tenant ---
    fun createTenant(name: String, niche: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val tenantId = repository.insertTenant(Tenant(name = name, businessType = niche))
            repository.seedTenantWithTemplate(tenantId, niche)
            _activeTenantId.value = tenantId
        }
    }

    // --- Subscription Purchase Simulation (Midtrans/Xendit) ---
    fun paySubscription(plan: String, method: String) {
        val currentTenantId = _activeTenantId.value
        viewModelScope.launch(Dispatchers.IO) {
            val dummyVa = when (method) {
                "BCA" -> "11288" + (100000..999999).random().toString()
                "Mandiri" -> "88012" + (100000..999999).random().toString()
                "BRI" -> "11802" + (100000..999999).random().toString()
                else -> "QRIS_SCANNER_ACTIVE"
            }

            // Put status Unpaid initially to trigger invoice simulation
            val initialSub = Subscription(
                tenantId = currentTenantId,
                status = "Unpaid",
                plan = plan,
                expiryDate = System.currentTimeMillis() + if (plan.contains("Yearly")) 365L*24*60*60*1000 else 30L*24*60*60*1000,
                lastPaymentMethod = method,
                lastPaymentVa = dummyVa
            )
            repository.updateSubscription(initialSub)
        }
    }

    fun confirmPayment() {
        val currentTenantId = _activeTenantId.value
        viewModelScope.launch(Dispatchers.IO) {
            val sub = repository.getSubscriptionForTenantSync(currentTenantId)
            if (sub != null) {
                val updated = sub.copy(status = "Active")
                repository.updateSubscription(updated)
            }
        }
    }

    fun setSubscriptionStatusManual(status: String) {
        val currentTenantId = _activeTenantId.value
        viewModelScope.launch(Dispatchers.IO) {
            val sub = repository.getSubscriptionForTenantSync(currentTenantId)
            if (sub != null) {
                val updated = sub.copy(status = status)
                repository.updateSubscription(updated)
            }
        }
    }

    // --- Real-time SAK EMKM Financial Computers ---
    fun calcIncomeStatement(): IncomeStatementReport {
        val items = journalItems.value
        
        var revenue = 0.0
        var hpp = 0.0
        val expensesMap = mutableMapOf<String, Double>()

        for (item in items) {
            if (item.accountCode.startsWith("4")) {
                // Revenue: Normal Credit balance
                revenue += (item.credit - item.debit)
            } else if (item.accountCode.startsWith("5")) {
                // Beban: Normal Debit balance
                if (item.accountName.contains("Harga Pokok", true) || item.accountName.contains("HPP", true)) {
                    hpp += (item.debit - item.credit)
                } else {
                    val prev = expensesMap.getOrDefault(item.accountName, 0.0)
                    expensesMap[item.accountName] = prev + (item.debit - item.credit)
                }
            }
        }

        val grossProfit = revenue - hpp
        val totalOperatingExpenses = expensesMap.values.sum()
        val netProfitBeforeTax = grossProfit - totalOperatingExpenses

        // Indon tax logic
        val taxThreshold = 500_000_000.0 // Rp500 Juta
        val previousOmzet = _forcePreviousGrossRevenue.value
        val totalOmzetThisPeriod = revenue
        val totalOmzetCombined = previousOmzet + totalOmzetThisPeriod

        var PPhFinal05 = 0.0
        var taxableOmzet = 0.0

        if (_isIndividualOwnerPajak.value) {
            // Individual MSME gets Rp500 mil free thresh
            if (totalOmzetCombined > taxThreshold) {
                // Determine how much of the CURRENT omzet is above the threshold
                val freeBufferRemaining = maxOf(0.0, taxThreshold - previousOmzet)
                taxableOmzet = maxOf(0.0, totalOmzetThisPeriod - freeBufferRemaining)
                PPhFinal05 = taxableOmzet * 0.005
            } else {
                PPhFinal05 = 0.0
            }
        } else {
            // Corporate MSME has no tax-free threshold, taxed on all revenue
            taxableOmzet = totalOmzetThisPeriod
            PPhFinal05 = taxableOmzet * 0.005
        }

        val netProfitAfterTax = netProfitBeforeTax - PPhFinal05

        return IncomeStatementReport(
            revenue = revenue,
            hpp = hpp,
            grossProfit = grossProfit,
            operatingExpenses = expensesMap,
            totalOperatingExpenses = totalOperatingExpenses,
            netProfitBeforeTax = netProfitBeforeTax,
            taxFreeThresholdRemaining = maxOf(0.0, taxThreshold - totalOmzetCombined),
            taxableRevenue = taxableOmzet,
            pphFinal = PPhFinal05,
            netProfitAfterTax = netProfitAfterTax
        )
    }

    fun calcBalanceSheet(netProfit: Double): BalanceSheetReport {
        val items = journalItems.value

        var cashAndBank = 0.0
        var receivables = 0.0
        var inventoryVal = 0.0
        var otherAssets = 0.0

        var fixedAssets = 0.0
        var liabilities = 0.0
        var equity = 0.0

        for (item in items) {
            val code = item.accountCode
            if (code.startsWith("1")) {
                // Assets: Normal debit balance
                val balance = item.debit - item.credit
                if (item.accountName.contains("Kas", true) || item.accountName.contains("Cash", true) || item.accountName.contains("Bank", true)) {
                    cashAndBank += balance
                } else if (item.accountName.contains("Piutang", true) || item.accountName.contains("Receivable", true)) {
                    receivables += balance
                } else if (item.accountName.contains("Persediaan", true) || item.accountName.contains("Inventory", true)) {
                    inventoryVal += balance
                } else {
                    if (code.startsWith("11")) { // Other current assets
                        otherAssets += balance
                    } else if (code.startsWith("12")) { // Fixed assets
                        fixedAssets += balance
                    }
                }
            } else if (code.startsWith("2")) {
                // Liabilities: Normal credit balance
                val balance = item.credit - item.debit
                liabilities += balance
            } else if (code.startsWith("3")) {
                // Equity: Normal credit balance
                val balance = item.credit - item.debit
                equity += balance
            }
        }

        // Laba tahun berjalan included in ending equity
        val totalEquity = equity + netProfit
        val totalAssets = cashAndBank + receivables + inventoryVal + otherAssets + fixedAssets
        val totalLiabilitiesAndEquity = liabilities + totalEquity

        return BalanceSheetReport(
            cashAndBank = cashAndBank,
            receivables = receivables,
            inventoryVal = inventoryVal,
            otherCurrentAssets = otherAssets,
            fixedAssets = fixedAssets,
            totalAssets = totalAssets,
            liabilities = liabilities,
            capital = equity,
            retainedEarnings = netProfit,
            totalEquity = totalEquity,
            totalLiabilitiesAndEquity = totalLiabilitiesAndEquity
        )
    }
}

// --- Report Data Structures ---
data class IncomeStatementReport(
    val revenue: Double,
    val hpp: Double,
    val grossProfit: Double,
    val operatingExpenses: Map<String, Double>,
    val totalOperatingExpenses: Double,
    val netProfitBeforeTax: Double,
    val taxFreeThresholdRemaining: Double,
    val taxableRevenue: Double,
    val pphFinal: Double,
    val netProfitAfterTax: Double
)

data class BalanceSheetReport(
    val cashAndBank: Double,
    val receivables: Double,
    val inventoryVal: Double,
    val otherCurrentAssets: Double,
    val fixedAssets: Double,
    val totalAssets: Double,
    val liabilities: Double,
    val capital: Double,
    val retainedEarnings: Double,
    val totalEquity: Double,
    val totalLiabilitiesAndEquity: Double
)
