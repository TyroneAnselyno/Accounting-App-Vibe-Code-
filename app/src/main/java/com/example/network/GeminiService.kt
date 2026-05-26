package com.example.network

import com.example.BuildConfig
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class Part(
    @Json(name = "text") val text: String? = null
)

@JsonClass(generateAdapter = true)
data class Content(
    @Json(name = "parts") val parts: List<Part>
)

@JsonClass(generateAdapter = true)
data class GenerateContentRequest(
    @Json(name = "contents") val contents: List<Content>
)

@JsonClass(generateAdapter = true)
data class Candidate(
    @Json(name = "content") val content: Content
)

@JsonClass(generateAdapter = true)
data class GenerateContentResponse(
    @Json(name = "candidates") val candidates: List<Candidate>? = null
)

interface GeminiApi {
    @POST("v1beta/models/gemini-3.5-flash:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body request: GenerateContentRequest
    ): GenerateContentResponse
}

object GeminiApiClient {
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    val apiService: GeminiApi by lazy {
        retrofit.create(GeminiApi::class.java)
    }

    /**
     * Parse raw Indonesian business chat text into journal commands using Gemini (Option B).
     */
    suspend fun parseIndonesianChat(
        message: String,
        coaDescription: String,
        productsDescription: String
    ): String {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            return "ERROR_API_KEY_MISSING"
        }

        val prompt = """
            You are AkunSaku AI, an Indonesian Accounting expert bot for Indonesian UMKMs (Micro/Small/Medium Business).
            The user runs a business that uses double-entry bookkeeping (SAK EMKM).
            
            You must parse the following Indonesian WhatsApp chat message of a business transaction into a structured JSON.
            
            Here is the current Chart of Accounts (COA) of the business:
            $coaDescription
            
            Here are the current trackable products (for sales or purchases):
            $productsDescription
            
            CRITICAL RULES:
            - Map accounts in the transaction exactly based on the Provided Chart of Accounts (COA) above!
              For example, if the user mentioned Cash, find the appropriate Cash/Bank account in the COA (e.g., "1101 Kas Laci Toko"). If they mentions spending/buying or paying rent, map to the proper "Beban..." or asset account.
              Double Entry Rule: Debits must EXACTLY match Credits.
            
            - Special Inventory Rule:
              If the user sells trackable inventory items (e.g. "Jual 2 Sofa", "Jual bakso 5 piring"), check the products list.
              If a product matches (e.g., "Sofa" / "Sofa Klasik", "Bakso Sapi Premium"), determine the quantity sold, and set the `inventoryAction` field.
              The `inventoryAction` should contain: "productId", "type": "OUT", "quantity", and "unitSalesPrice" (if specified in the message, else use the salesPrice from the product description).
              For inventory sales, you MUST record the Revenue recognition journal:
                - Debit: Cash or Bank account for quantity * unitSalesPrice
                - Credit: Revenue (Pendapatan) account for quantity * unitSalesPrice
              Do NOT write the HPP / COGS journal entries in this JSON yourself; our repository engine calculates FIFO/Average COGS automatically and writes HPP - Persediaan journals behind the scenes! Just focus on the cash/sale amount journal.
              
              If they buy inventory items (e.g. "Beli 5 Sofa @1.5jt"), the `inventoryAction` should contain: "productId", "type": "IN", "quantity", and "unitPurchasePrice" (the price they bought at, e.g. 1500000.0).
              For inventory purchases, you MUST record the Purchase recognition:
                - Debit: Persediaan (Inventory) account for quantity * unitPurchasePrice
                - Credit: Cash or Bank account for quantity * unitPurchasePrice

            - For non-inventory transactions (e.g. "Bayar listrik 200rb", "Setor modal 10jt"), format a typical double entry:
              e.g., "Bayar listrik 200rb" -> Debit: "Beban Listrik..." for 200000.0, Credit: "Kas..." for 200000.0.
            
            - Output format MUST be a single raw JSON object strictly complying with this schema (no surrounding markdown ticks, no extra text):
            {
              "description": "Short Indonesian description of the transaction (e.g., 'Bayar sewa ruko')",
              "journalItems": [
                {
                  "accountCode": "1101",
                  "accountName": "Kas Laci Toko",
                  "debit": 0.0,
                  "credit": 200000.0
                },
                {
                  "accountCode": "5201",
                  "accountName": "Beban Listrik Toko",
                  "debit": 200000.0,
                  "credit": 0.0
                }
              ],
              "inventoryAction": {
                "productId": 1,
                "type": "OUT" | "IN",
                "quantity": 2,
                "price": 3500000.0
              } // Set to null if there is no inventory product buy/sell
            }
            
            If you cannot understand the transaction or it is invalid, return:
            {
              "error": "Deskripsi error dalam Bahasa Indonesia (e.g. 'Transaksi tidak jelas')"
            }
            
            Analyze this user message: "${message.replace("\"", "\\\"")}"
        """.trimIndent()

        val request = GenerateContentRequest(
            contents = listOf(Content(parts = listOf(Part(text = prompt))))
        )

        return try {
            val response = apiService.generateContent(apiKey, request)
            val rawText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            cleanJsonResponse(rawText ?: "")
        } catch (e: Exception) {
            "Error: ${e.localizedMessage}"
        }
    }

    private fun cleanJsonResponse(raw: String): String {
        return raw.trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
    }
}
