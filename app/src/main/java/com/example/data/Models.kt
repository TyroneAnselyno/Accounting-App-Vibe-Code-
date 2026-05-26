package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tenants")
data class Tenant(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val businessType: String // "F&B", "Jasa", "Retail", "Custom"
)

@Entity(tableName = "accounts")
data class Account(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val tenantId: Int,
    val code: String,
    val name: String,
    val category: String, // "Aset Lancar", "Aset Tetap", "Kewajiban", "Ekuitas", "Pendapatan", "Beban"
    val isSystem: Boolean = false
) {
    override fun toString(): String {
        return "[$code] $name ($category)"
    }
}

@Entity(tableName = "products")
data class Product(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val tenantId: Int,
    val name: String,
    val stockQuantity: Int = 0,
    val averagePurchasePrice: Double = 0.0,
    val salesPrice: Double = 0.0
)

@Entity(tableName = "journal_entries")
data class JournalEntry(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val tenantId: Int,
    val date: Long = System.currentTimeMillis(),
    val description: String,
    val source: String, // "WA" or "MANUAL"
    val rawMessage: String? = null
)

@Entity(tableName = "journal_items")
data class JournalItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val journalEntryId: Int,
    val accountCode: String,
    val accountName: String,
    val debit: Double = 0.0,
    val credit: Double = 0.0
)

@Entity(tableName = "inventory_transactions")
data class InventoryTransaction(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val tenantId: Int,
    val productId: Int,
    val journalEntryId: Int?,
    val date: Long = System.currentTimeMillis(),
    val type: String, // "IN" (buy) or "OUT" (sell)
    val quantity: Int,
    val unitPrice: Double,
    val costCalculatedPercent: Double = 0.0 // COGS related calculation helper
)

@Entity(tableName = "chat_messages")
data class ChatMessage(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val tenantId: Int,
    val userMessage: String,
    val botReply: String,
    val timestamp: Long = System.currentTimeMillis(),
    val status: String // "SUCCESS", "ERROR", "RENEWAL_REQUIRED"
)

@Entity(tableName = "subscriptions")
data class Subscription(
    @PrimaryKey val tenantId: Int,
    val status: String, // "Active", "Expired", "Unpaid"
    val plan: String, // "Monthly (Rp450.000)", "Yearly (Rp4.200.000)"
    val expiryDate: Long = System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1000,
    val lastPaymentMethod: String? = null,
    val lastPaymentVa: String? = null
)
