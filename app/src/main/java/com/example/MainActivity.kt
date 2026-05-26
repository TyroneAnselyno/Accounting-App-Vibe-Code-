package com.example

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.*
import com.example.ui.*
import com.example.ui.theme.MyApplicationTheme

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {
    private val viewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                MainAppScreen(viewModel)
            }
        }
    }
}

enum class AppTab(val title: String, val icon: String) {
    DASHBOARD("Dasbod", "📊"),
    WA_BOT("WhatsApp", "💬"),
    COA("Akun", "📂"),
    INVENTORY("Stok", "📦"),
    REPORTS("Laporan", "📝"),
    BILLING("Billing", "💳")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScreen(viewModel: AppViewModel) {
    var selectedTab by remember { mutableStateOf(AppTab.DASHBOARD) }
    
    val activeTenant by viewModel.activeTenant.collectAsState()
    val tenants by viewModel.tenants.collectAsState()
    val subscription by viewModel.subscription.collectAsState()
    
    var showTenantDropdown by remember { mutableStateOf(false) }
    var showCreateTenantDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current

    // Check if subscription blocks dashboard features
    val isBlocked = subscription == null || subscription?.status == "Expired" || subscription?.status == "Unpaid"

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                ),
                title = {
                    Row(
                        modifier = Modifier.clickable { showTenantDropdown = !showTenantDropdown },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = activeTenant?.name ?: "Pilih Bisnis",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            modifier = Modifier.testTag("tenant_selector_title")
                        )
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = "Ganti Bisnis",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                },
                actions = {
                    // Subscription Pill Indicator
                    val statusText = subscription?.status ?: "Unpaid"
                    val pillBg = if (statusText == "Active") Color(0xFFD1FAE5) else Color(0xFFFEE2E2)
                    val pillTxt = if (statusText == "Active") Color(0xFF065F46) else Color(0xFF991B1B)
                    
                    TextButton(
                        onClick = { selectedTab = AppTab.BILLING },
                        modifier = Modifier.testTag("subscription_pill")
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(30))
                                .background(pillBg)
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "langganan:" + statusText,
                                color = pillTxt,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = Color.White,
                tonalElevation = 8.dp
            ) {
                AppTab.values().forEach { tab ->
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        icon = {
                            Text(text = tab.icon, fontSize = 22.sp)
                        },
                        label = {
                            Text(
                                text = tab.title,
                                fontSize = 11.sp,
                                fontWeight = if (selectedTab == tab) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            unselectedIconColor = Color.Gray,
                            unselectedTextColor = Color.Gray,
                            indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                        ),
                        modifier = Modifier.testTag("tab_" + tab.name.lowercase())
                    )
                }
            }
        },
        contentWindowInsets = WindowInsets.safeDrawing
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF8FAFC))
                .padding(innerPadding)
        ) {
            // Dropdown menu showing current businesses/tenants
            if (showTenantDropdown) {
                Dialog(onDismissRequest = { showTenantDropdown = false }) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Pilih Profil Bisnis (UMKM)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )
                            Divider(modifier = Modifier.padding(bottom = 8.dp))
                            LazyColumn(modifier = Modifier.heightIn(max = 240.dp)) {
                                items(tenants) { tenant ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable {
                                                viewModel.selectTenant(tenant.id)
                                                showTenantDropdown = false
                                                Toast
                                                    .makeText(
                                                        context,
                                                        "Profil beralih ke ${tenant.name}",
                                                        Toast.LENGTH_SHORT
                                                    )
                                                    .show()
                                            }
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "🏢",
                                            fontSize = 20.sp,
                                            modifier = Modifier.padding(end = 12.dp)
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = tenant.name,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 14.sp
                                            )
                                            Text(
                                                text = "Niche: ${tenant.businessType}",
                                                color = Color.Gray,
                                                fontSize = 11.sp
                                            )
                                        }
                                        if (tenant.id == activeTenant?.id) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = "Aktif",
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }
                            }
                            Divider(modifier = Modifier.padding(vertical = 12.dp))
                            Button(
                                onClick = {
                                    showTenantDropdown = false
                                    showCreateTenantDialog = true
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("add_tenant_button"),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Tambah Profil Bisnis Baru")
                            }
                        }
                    }
                }
            }

            // Create Profile Dialog
            if (showCreateTenantDialog) {
                var newName by remember { mutableStateOf("") }
                var selectedNiche by remember { mutableStateOf("F&B") }
                val niches = listOf("F&B", "Jasa", "Retail", "Custom")

                Dialog(onDismissRequest = { showCreateTenantDialog = false }) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text("Tambah Bisnis UMKM Baru", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            
                            OutlinedTextField(
                                value = newName,
                                onValueChange = { newName = it },
                                label = { Text("Nama Bisnis UMKM") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("new_tenant_name_input"),
                                singleLine = true
                            )

                            Text("Template Akuntansi Niche:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                niches.forEach { niche ->
                                    val isSelected = selectedNiche == niche
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (isSelected) MaterialTheme.colorScheme.primary else Color(0xFFF1F5F9))
                                            .clickable { selectedNiche = niche }
                                            .padding(vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = niche,
                                            color = if (isSelected) Color.White else Color.Black,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { showCreateTenantDialog = false },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Batal")
                                }
                                Button(
                                    onClick = {
                                        if (newName.isNotBlank()) {
                                            viewModel.createTenant(newName, selectedNiche)
                                            showCreateTenantDialog = false
                                            Toast.makeText(context, "Berhasil membuat $newName!", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("submit_new_tenant_button"),
                                    enabled = newName.isNotBlank()
                                ) {
                                    Text("Buat")
                                }
                            }
                        }
                    }
                }
            }

            // Subscription Guard Blocker Middleware Look-up
            val billingIsActiveTab = selectedTab == AppTab.BILLING
            if (isBlocked && !billingIsActiveTab) {
                // Subscription Blocker overlay screen
                SubscriptionLobbyBlockedView(viewModel) {
                    selectedTab = AppTab.BILLING
                }
            } else {
                // Happy active path renders tabs correctly
                AnimatedContent(
                    targetState = selectedTab,
                    transitionSpec = {
                        fadeIn() togetherWith fadeOut()
                    },
                    label = "tab_fade"
                ) { targetTab ->
                    when (targetTab) {
                        AppTab.DASHBOARD -> DashboardScreen(viewModel)
                        AppTab.WA_BOT -> WhatsAppBotScreen(viewModel)
                        AppTab.COA -> ChartOfAccountsScreen(viewModel)
                        AppTab.INVENTORY -> InventoryScreen(viewModel)
                        AppTab.REPORTS -> ReportsScreen(viewModel)
                        AppTab.BILLING -> BillingScreen(viewModel)
                    }
                }
            }
        }
    }
}

/**
 * Midtrans simulated subscription barrier card that locking out user.
 */
@Composable
fun SubscriptionLobbyBlockedView(viewModel: AppViewModel, onGoToBilling: () -> Unit) {
    val sub by viewModel.subscription.collectAsState()
    Card(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "🔒",
                fontSize = 58.sp,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            Text(
                text = "Dashboard Dikunci",
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Masa aktif langganan AkunSaku SaaS Anda saat ini ${if (sub?.status == "Expired") "Telah Berakhir (Expired)" else "Belum Dibayar (Unpaid)"}.",
                textAlign = TextAlign.Center,
                color = Color.Gray,
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "WhatsApp bot juga akan membalas pesan penulisan dengan 'Renewal Required' jika masa aktif habis.",
                textAlign = TextAlign.Center,
                color = Color.Gray,
                fontSize = 12.sp
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onGoToBilling,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("locked_go_to_billing_button"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Buka Billing & Bayar Sekarang (Simulasi)")
            }
        }
    }
}

// --- TAB 1: DASHBOARD ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(viewModel: AppViewModel) {
    // Collect data points
    val accounts by viewModel.accounts.collectAsState()
    val products by viewModel.products.collectAsState()
    val report = viewModel.calcIncomeStatement()
    val neraca = viewModel.calcBalanceSheet(report.netProfitBeforeTax)

    var showQuickEntry by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .testTag("dashboard_scroll"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            // Main Stat Cards Grid
            Text(
                text = "Dashboard Finansial",
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        item {
            // First Row: Cash on hand & profit
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Card 1: Cash on Hand
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .height(110.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("💵 Kas & Bank", color = Color.White.copy(alpha = 0.8f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text(
                            text = "Rp " + String.format("%,.0f", neraca.cashAndBank),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Text("Lancar", color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp)
                    }
                }

                // Card 2: Net Profit
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .height(110.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondary)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("📈 Laba Bersih", color = Color.White.copy(alpha = 0.8f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text(
                            text = "Rp " + String.format("%,.0f", report.netProfitBeforeTax),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        val colorPercent = if (report.netProfitBeforeTax >= 0) "Profit" else "Loss"
                        Text(colorPercent, color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp)
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Card 3: Receivables
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .height(100.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("🤝 Piutang Usaha", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text(
                            text = "Rp " + String.format("%,.0f", neraca.receivables),
                            color = MaterialTheme.colorScheme.onBackground,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Text("Aset Lancar", color = Color.Gray, fontSize = 10.sp)
                    }
                }

                // Card 4: Inventory Values
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .height(100.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("📦 Nilai Persediaan", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text(
                            text = "Rp " + String.format("%,.0f", neraca.inventoryVal),
                            color = MaterialTheme.colorScheme.onBackground,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Text("Stok Aktif", color = Color.Gray, fontSize = 10.sp)
                    }
                }
            }
        }

        // Low stock Alerts
        val lowStockProducts = products.filter { it.stockQuantity < 10 }
        if (lowStockProducts.isNotEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF2F2))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("⚠️", fontSize = 20.sp, modifier = Modifier.padding(end = 8.dp))
                            Text(
                                text = "Peringatan Stok Rendah (< 10 unit)",
                                color = Color(0xFF991B1B),
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        lowStockProducts.forEach { prod ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(prod.name, fontSize = 12.sp, color = Color(0xFF7F1D1D))
                                Text("Sisa: ${prod.stockQuantity} unit", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF991B1B))
                            }
                        }
                    }
                }
            }
        }

        // Quick Entry triggers
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                onClick = { showQuickEntry = !showQuickEntry }
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("✏️", fontSize = 24.sp, modifier = Modifier.padding(end = 12.dp))
                        Column {
                            Text("Quick Entry Manual Form", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("Catat transaksi double-entry instan", color = Color.Gray, fontSize = 11.sp)
                        }
                    }
                    Icon(
                        imageVector = if (showQuickEntry) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = null
                    )
                }
                
                if (showQuickEntry) {
                    Box(modifier = Modifier.padding(16.dp)) {
                        QuickEntryForm(viewModel, accounts)
                    }
                }
            }
        }

        // Static Tips SAK EMKM
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF6FF))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("💡 Tips Pajak & SAK EMKM UMKM", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Menurut PP 55 Tahun 2022, UMKM Orang Pribadi dengan peredaran bruto s.d Rp500.000.000 per tahun dibebaskan dari pajak final PPh 0,5%. AI kami secara cerdas menghitung sisa batas threshold ini dan memperhitungkan kewajiban perpajakan Anda secara otomatis.",
                        fontSize = 11.sp,
                        color = Color(0xFF1E3A8A),
                        lineHeight = 15.sp
                    )
                }
            }
        }
    }
}

/**
 * Double-entry visual builder for quick journaling on the web dashboard.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickEntryForm(viewModel: AppViewModel, accounts: List<Account>) {
    var description by remember { mutableStateOf("") }
    var debitAccount by remember { mutableStateOf<Account?>(null) }
    var creditAccount by remember { mutableStateOf<Account?>(null) }
    var rawAmount by remember { mutableStateOf("") }

    val context = LocalContext.current

    var debitExpanded by remember { mutableStateOf(false) }
    var creditExpanded by remember { mutableStateOf(false) }

    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        OutlinedTextField(
            value = description,
            onValueChange = { description = it },
            label = { Text("Deskripsi Transaksi") },
            placeholder = { Text("contoh: Setor Modal Pemilik") },
            modifier = Modifier.fillMaxWidth().testTag("quick_desc_input"),
            singleLine = true
        )

        // Debit drop menu
        ExposedDropdownMenuBox(
            expanded = debitExpanded,
            onExpandedChange = { debitExpanded = it }
        ) {
            OutlinedTextField(
                value = debitAccount?.let { "[${it.code}] ${it.name}" } ?: "Pilih Akun Debit (Kiri)",
                onValueChange = {},
                readOnly = true,
                label = { Text("Akun Debit (Dr)") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = debitExpanded) },
                modifier = Modifier.fillMaxWidth().menuAnchor().testTag("debit_selection")
            )
            ExposedDropdownMenu(
                expanded = debitExpanded,
                onDismissRequest = { debitExpanded = false }
            ) {
                accounts.forEach { acc ->
                    DropdownMenuItem(
                        text = { Text("[${acc.code}] ${acc.name} (${acc.category})") },
                        onClick = {
                            debitAccount = acc
                            debitExpanded = false
                        }
                    )
                }
            }
        }

        // Credit drop menu
        ExposedDropdownMenuBox(
            expanded = creditExpanded,
            onExpandedChange = { creditExpanded = it }
        ) {
            OutlinedTextField(
                value = creditAccount?.let { "[${it.code}] ${it.name}" } ?: "Pilih Akun Kredit (Kanan)",
                onValueChange = {},
                readOnly = true,
                label = { Text("Akun Kredit (Cr)") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = creditExpanded) },
                modifier = Modifier.fillMaxWidth().menuAnchor().testTag("credit_selection")
            )
            ExposedDropdownMenu(
                expanded = creditExpanded,
                onDismissRequest = { creditExpanded = false }
            ) {
                accounts.forEach { acc ->
                    DropdownMenuItem(
                        text = { Text("[${acc.code}] ${acc.name} (${acc.category})") },
                        onClick = {
                            creditAccount = acc
                            creditExpanded = false
                        }
                    )
                }
            }
        }

        OutlinedTextField(
            value = rawAmount,
            onValueChange = { rawAmount = it },
            label = { Text("Jumlah (Rp)") },
            placeholder = { Text("contoh: 250000") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth().testTag("quick_amount_input"),
            singleLine = true
        )

        Button(
            onClick = {
                val amount = rawAmount.toDoubleOrNull()
                if (description.isBlank() || debitAccount == null || creditAccount == null || amount == null || amount <= 0) {
                    Toast.makeText(context, "Mohon lengkapi semua isian formulir!", Toast.LENGTH_SHORT).show()
                } else {
                    viewModel.createManualJournal(
                        description = description,
                        debitAccount = debitAccount!!.code,
                        debitName = debitAccount!!.name,
                        creditAccount = creditAccount!!.code,
                        creditName = creditAccount!!.name,
                        amount = amount
                    )
                    Toast.makeText(context, "✅ Jurnal berhasil dicatat!", Toast.LENGTH_SHORT).show()
                    description = ""
                    rawAmount = ""
                    debitAccount = null
                    creditAccount = null
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("quick_submit_button"),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Catat Double-Entry")
        }
    }
}

// --- TAB 2: WHATSAPP BOT SANDBOX ---
@Composable
fun WhatsAppBotScreen(viewModel: AppViewModel) {
    val chatHistory by viewModel.chatHistory.collectAsState()
    val isBotLoading by viewModel.isBotLoading.collectAsState()

    var chatInput by remember { mutableStateOf("") }

    val promptSuggestions = listOf(
        "Beli laptop kantor seharga 8jt",
        "Jual Sofa Klasik 1 unit",
        "Bayar sewa air PAM ruko 200rb",
        "Setor modal kas utama 10jt"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // WhatsApp Top Branding
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF075E54)) // WA Green color
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("🤖", fontSize = 24.sp)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text("AkunSaku WhatsApp Bot", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
                    Text("Online • NLP Journaling Engine", color = Color.White.copy(alpha = 0.8f), fontSize = 10.sp)
                }
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = { viewModel.clearChat() }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Clear Chat", tint = Color.White)
                }
            }
        }

        // Chat Timeline
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(vertical = 12.dp)
                .testTag("chat_history_list"),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (chatHistory.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Ketik deskripsi transaksi usaha Anda dalam Bahasa Indonesia.\n\nAI akan menerjemahkannya ke dalam jurnal double-entry otomatis sesuai standar SAK EMKM dan COA Bisnis Anda.",
                            color = Color.Gray,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                items(chatHistory) { msg ->
                    // User message bubble (sender)
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.End
                    ) {
                        Box(
                            modifier = Modifier
                                .widthIn(max = 280.dp)
                                .clip(RoundedCornerShape(12.dp, 0.dp, 12.dp, 12.dp))
                                .background(Color(0xFFDCF8C6)) // WA User green bubble
                                .padding(12.dp)
                        ) {
                            Text(msg.userMessage, fontSize = 13.sp, color = Color.Black)
                        }
                        Text(
                            text = "Anda • 09:45 Z",
                            fontSize = 9.sp,
                            color = Color.Gray,
                            modifier = Modifier.padding(end = 4.dp, top = 2.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Bot message bubble
                    val isError = msg.status == "ERROR"
                    val isLocked = msg.status == "RENEWAL_REQUIRED"
                    val bubbleBg = if (isError) Color(0xFFFEE2E2) else if (isLocked) Color(0xFFFFEDD5) else Color.White
                    val bubbleBorder = if (isError) Color(0xFFFCA5A5) else if (isLocked) Color(0xFFFED7AA) else Color(0xFFE2E8F0)

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.Start
                    ) {
                        Box(
                            modifier = Modifier
                                .widthIn(max = 300.dp)
                                .clip(RoundedCornerShape(0.dp, 12.dp, 12.dp, 12.dp))
                                .background(bubbleBg)
                                .border(1.dp, bubbleBorder, RoundedCornerShape(0.dp, 12.dp, 12.dp, 12.dp))
                                .padding(12.dp)
                        ) {
                            Text(msg.botReply, fontSize = 13.sp, color = Color.DarkGray)
                        }
                        Text(
                            text = "WhatsApp Bot • 09:45 Z",
                            fontSize = 9.sp,
                            color = Color.Gray,
                            modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                        )
                    }
                }
            }

            if (isBotLoading) {
                item {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White)
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Gemini AI sedang menyusun jurnal double-entry...", fontSize = 11.sp, color = Color.Gray)
                    }
                }
            }
        }

        // Suggestions Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            promptSuggestions.take(3).forEach { suggest ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(20))
                        .background(Color(0xFFEFF6FF))
                        .clickable { chatInput = suggest }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = suggest,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // Chat Input Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = chatInput,
                onValueChange = { chatInput = it },
                placeholder = { Text("Ketik pesan transaksi di sini...") },
                modifier = Modifier
                    .weight(1f)
                    .testTag("chat_input_text"),
                singleLine = true,
                trailingIcon = {
                    if (chatInput.isNotEmpty()) {
                        IconButton(onClick = { chatInput = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = null)
                        }
                    }
                }
            )

            FloatingActionButton(
                onClick = {
                    if (chatInput.isNotBlank()) {
                        viewModel.sendWhatsAppMessage(chatInput)
                        chatInput = ""
                    }
                },
                modifier = Modifier.size(48.dp).testTag("chat_send_button"),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White,
                shape = CircleShape
            ) {
                Text("➡️", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// --- TAB 3: CHART OF ACCOUNTS (COA) ENGINE ---
@Composable
fun ChartOfAccountsScreen(viewModel: AppViewModel) {
    val accounts by viewModel.accounts.collectAsState()
    
    var showAddDialog by remember { mutableStateOf(false) }
    var accountCode by remember { mutableStateOf("") }
    var accountName by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("Aset Lancar") }

    val categories = listOf("Aset Lancar", "Aset Tetap", "Kewajiban", "Ekuitas", "Pendapatan", "Beban")
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Engine Chart of Accounts (COA)", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            IconButton(
                onClick = { showAddDialog = true },
                modifier = Modifier.testTag("add_account_btn")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add COA Account", tint = MaterialTheme.colorScheme.primary)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Card(
            modifier = Modifier.fillMaxWidth().weight(1f),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                categories.forEach { cat ->
                    val filtered = accounts.filter { it.category == cat }
                    if (filtered.isNotEmpty()) {
                        item {
                            // Category Header
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xFFF1F5F9))
                                    .padding(8.dp)
                            ) {
                                Text(cat, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.DarkGray)
                            }
                        }
                        items(filtered) { acc ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp, horizontal = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = acc.code,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.width(60.dp)
                                    )
                                    Text(acc.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                }

                                if (!acc.isSystem) {
                                    IconButton(
                                        onClick = { viewModel.deleteAccount(acc) },
                                        modifier = Modifier.size(24.dp).testTag("delete_acc_" + acc.code)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red, modifier = Modifier.size(16.dp))
                                    }
                                } else {
                                    Text("Sistem", fontSize = 10.sp, color = Color.LightGray)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        Dialog(onDismissRequest = { showAddDialog = false }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Tambah Akun COA Baru", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    
                    OutlinedTextField(
                        value = accountCode,
                        onValueChange = { accountCode = it },
                        label = { Text("Kode Akun (e.g. 1104)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth().testTag("new_coa_code"),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = accountName,
                        onValueChange = { accountName = it },
                        label = { Text("Nama Akun (e.g. Kas Tambahan)") },
                        modifier = Modifier.fillMaxWidth().testTag("new_coa_name"),
                        singleLine = true
                    )

                    // Category Selector Selector
                    Text("Kategori Standar Laporan:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    LazyColumn(modifier = Modifier.height(100.dp)) {
                        items(categories) { cat ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedCategory = cat }
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(selected = selectedCategory == cat, onClick = { selectedCategory = cat })
                                Text(cat, fontSize = 13.sp)
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(onClick = { showAddDialog = false }, modifier = Modifier.weight(1f)) {
                            Text("Batal")
                        }
                        Button(
                            onClick = {
                                if (accountCode.isNotBlank() && accountName.isNotBlank()) {
                                    viewModel.createAccount(accountCode, accountName, selectedCategory)
                                    showAddDialog = false
                                    accountCode = ""
                                    accountName = ""
                                } else {
                                    Toast.makeText(context, "Lengkapi semua data!", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.weight(1f).testTag("submit_coa_btn")
                        ) {
                            Text("Simpan")
                        }
                    }
                }
            }
        }
    }
}

// --- TAB 4: INVENTORIS & COGS ---
@Composable
fun InventoryScreen(viewModel: AppViewModel) {
    val products by viewModel.products.collectAsState()
    
    var showAddDialog by remember { mutableStateOf(false) }
    var showActionDialog by remember { mutableStateOf<Product?>(null) }
    var isBuyAction by remember { mutableStateOf(true) } // Buy (IN) or Sell (OUT)

    var name by remember { mutableStateOf("") }
    var qtyInitial by remember { mutableStateOf("10") }
    var purchasePriceInitial by remember { mutableStateOf("1500000") }
    var salesPriceInitial by remember { mutableStateOf("2500000") }

    // Action dialog states
    var actQty by remember { mutableStateOf("") }
    var actUnitPrice by remember { mutableStateOf("") }

    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Inventaris Barang & COGS (HPP)", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("Kalkulator HPP Nilai Rerata Bergerak", color = Color.Gray, fontSize = 11.sp)
            }
            IconButton(
                onClick = { showAddDialog = true },
                modifier = Modifier.testTag("add_product_btn")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Product", tint = MaterialTheme.colorScheme.primary)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // UI Grid List
        Card(
            modifier = Modifier.fillMaxWidth().weight(1f),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(products) { prod ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(prod.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                IconButton(
                                    onClick = { viewModel.deleteProduct(prod) },
                                    modifier = Modifier.size(24.dp).testTag("delete_prod_" + prod.id)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Hapus", tint = Color.Red, modifier = Modifier.size(16.dp))
                                }
                            }
                            Divider(modifier = Modifier.padding(vertical = 6.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column {
                                    Text("Stok Tersedia:", color = Color.Gray, fontSize = 10.sp)
                                    val isLow = prod.stockQuantity < 10
                                    Text(
                                        text = "${prod.stockQuantity} Unit",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = if (isLow) Color.Red else Color.Black
                                    )
                                }
                                Column {
                                    Text("HPP Rerata (Buy):", color = Color.Gray, fontSize = 10.sp)
                                    Text("Rp " + String.format("%,.0f", prod.averagePurchasePrice), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                }
                                Column {
                                    Text("Harga Jual (Sell):", color = Color.Gray, fontSize = 10.sp)
                                    Text("Rp " + String.format("%,.0f", prod.salesPrice), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                }
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            // Action Controls
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        showActionDialog = prod
                                        isBuyAction = true
                                        actQty = "5"
                                        actUnitPrice = prod.averagePurchasePrice.toInt().toString()
                                    },
                                    modifier = Modifier.weight(1f).height(36.dp).testTag("buy_stock_prod_" + prod.id),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Beli (Stok In)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                                Button(
                                    onClick = {
                                        showActionDialog = prod
                                        isBuyAction = false
                                        actQty = "2"
                                        actUnitPrice = prod.salesPrice.toInt().toString()
                                    },
                                    modifier = Modifier.weight(1f).height(36.dp).testTag("sell_stock_prod_" + prod.id),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Jual (Stok Out)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Add Product Dialog
    if (showAddDialog) {
        Dialog(onDismissRequest = { showAddDialog = false }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Tambah Item Inventaris Baru", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Nama Item (e.g. Sofa Minimalis)") },
                        modifier = Modifier.fillMaxWidth().testTag("new_prod_name"),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = qtyInitial,
                        onValueChange = { qtyInitial = it },
                        label = { Text("Stok Pengambilan Awal") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth().testTag("new_prod_qty"),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = purchasePriceInitial,
                        onValueChange = { purchasePriceInitial = it },
                        label = { Text("Harga Beli Terakhir (HPP)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth().testTag("new_prod_buy"),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = salesPriceInitial,
                        onValueChange = { salesPriceInitial = it },
                        label = { Text("Harga Jual Standar") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth().testTag("new_prod_sell"),
                        singleLine = true
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(onClick = { showAddDialog = false }, modifier = Modifier.weight(1f)) {
                            Text("Batal")
                        }
                        Button(
                            onClick = {
                                val qtyVal = qtyInitial.toIntOrNull() ?: 0
                                val buyVal = purchasePriceInitial.toDoubleOrNull() ?: 0.0
                                val sellVal = salesPriceInitial.toDoubleOrNull() ?: 0.0

                                if (name.isNotBlank() && qtyVal >= 0 && buyVal >= 0 && sellVal >= 0) {
                                    viewModel.createProduct(name, qtyVal, buyVal, sellVal)
                                    showAddDialog = false
                                    name = ""
                                } else {
                                    Toast.makeText(context, "Input tidak valid!", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.weight(1f).testTag("submit_product_btn")
                        ) {
                            Text("Tambah")
                        }
                    }
                }
            }
        }
    }

    // Action (Buy or Sell) Dialog
    showActionDialog?.let { prod ->
        Dialog(onDismissRequest = { showActionDialog = null }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = if (isBuyAction) "Simulasikan Pembelian Stok" else "Simulasikan Penjualan Stok",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Text("Item: ${prod.name}", fontSize = 13.sp)

                    OutlinedTextField(
                        value = actQty,
                        onValueChange = { actQty = it },
                        label = { Text("Kuantitas (Qty Unit)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth().testTag("action_qty_input")
                    )

                    OutlinedTextField(
                        value = actUnitPrice,
                        onValueChange = { actUnitPrice = it },
                        label = { Text(if (isBuyAction) "Harga Beli Satuan (Rp)" else "Harga Jual Satuan (Rp)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth().testTag("action_price_input")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(onClick = { showActionDialog = null }, modifier = Modifier.weight(1f)) {
                            Text("Batal")
                        }
                        Button(
                            onClick = {
                                val qty = actQty.toIntOrNull()
                                val price = actUnitPrice.toDoubleOrNull()

                                if (qty != null && qty > 0 && price != null && price >= 0) {
                                    if (isBuyAction) {
                                        viewModel.createManualJournal(
                                            description = "Beli ${prod.name} (Qty: $qty @Rp $price)",
                                            debitAccount = "1103", // Persediaan
                                            debitName = "Persediaan Barang",
                                            creditAccount = "1101", // Kas
                                            creditName = "Kas Toko",
                                            amount = qty * price
                                        )
                                        // Trigger updating in inventory transaction logic
                                        viewModel.createProduct(
                                            prod.name,
                                            prod.stockQuantity + qty,
                                            ((prod.stockQuantity * prod.averagePurchasePrice) + (qty * price)) / (prod.stockQuantity + qty),
                                            prod.salesPrice
                                        )
                                        Toast.makeText(context, "Pembelian dicatat & COGS disesuaikan!", Toast.LENGTH_SHORT).show()
                                    } else {
                                        // Reduce inventory & record sales
                                        val saleValue = qty * price
                                        val cogsValue = qty * prod.averagePurchasePrice
                                        
                                        // Sales journal: Debit Kas, Credit revenue
                                        viewModel.createManualJournal(
                                            description = "Jual ${prod.name} (Qty: $qty @Rp $price)",
                                            debitAccount = "1101", // Kas
                                            debitName = "Kas Toko",
                                            creditAccount = "4101", // Pendapatan
                                            creditName = "Pendapatan Penjualan",
                                            amount = saleValue
                                        )

                                        // COGS integration
                                        if (cogsValue > 0) {
                                            viewModel.createManualJournal(
                                                description = "HPP Penjualan ${prod.name} (Qty: $qty)",
                                                debitAccount = "5101", // HPP
                                                debitName = "Harga Pokok Penjualan",
                                                creditAccount = "1103", // Persediaan
                                                creditName = "Persediaan Barang",
                                                amount = cogsValue
                                            )
                                        }

                                        // Reduction
                                        viewModel.createProduct(
                                            prod.name,
                                            maxOf(0, prod.stockQuantity - qty),
                                            prod.averagePurchasePrice,
                                            prod.salesPrice
                                        )
                                        Toast.makeText(context, "Siklus Penjualan & COGS otomatis sukses!", Toast.LENGTH_SHORT).show()
                                    }
                                    showActionDialog = null
                                } else {
                                    Toast.makeText(context, "Input numerik salah!", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.weight(1f).testTag("submit_action_btn")
                        ) {
                            Text("Konfirmasi")
                        }
                    }
                }
            }
        }
    }
}

// --- TAB 5: FINANCIAL & TAX REPORTING (SAK EMKM) ---
@Composable
fun ReportsScreen(viewModel: AppViewModel) {
    val report = viewModel.calcIncomeStatement()
    val neraca = viewModel.calcBalanceSheet(report.netProfitBeforeTax)

    val isIndividualPajak by viewModel.isIndividualOwnerPajak.collectAsState()
    val previousGrossRevenue by viewModel.forcePreviousGrossRevenue.collectAsState()

    var activeReportTab by remember { mutableStateOf(0) } // 0: Laba Rugi, 1: Neraca, 2: Perubahan Ekuitas, 3: Arus Kas
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Laporan Keuangan & Pajak", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("Kepatuhan Standar SAK EMKM & DJP", color = Color.Gray, fontSize = 11.sp)
            }
            // Export button
            Button(
                onClick = {
                    val printout = buildString {
                        append("=== LAPORAN KEUANGAN SAK EMKM ===\n")
                        append("Jenis Laporan: Laba Rugi & Neraca\n")
                        append("Total Pendapatan: Rp ${String.format("%,.0f", report.revenue)}\n")
                        append("HPP / COGS: Rp ${String.format("%,.0f", report.hpp)}\n")
                        append("Pajak PPh Final 0.5% Terutang: Rp ${String.format("%,.0f", report.pphFinal)}\n")
                        append("Laba Bersih Setelah Pajak: Rp ${String.format("%,.0f", report.netProfitAfterTax)}\n")
                        append("================================")
                    }
                    clipboardManager.setText(AnnotatedString(printout))
                    Toast.makeText(context, "Laporan disalin! Bagikan ke WhatsApp / Email.", Toast.LENGTH_LONG).show()

                    // System Share Action
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, printprint(report, neraca))
                    }
                    context.startActivity(Intent.createChooser(intent, "Kirim Laporan Pajak"))
                },
                modifier = Modifier.testTag("export_reports_btn")
            ) {
                Text("Export", fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Tax Adjuster Widgets panel
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("⚙️ Pengaturan Perpajakan (PPh Final 0.5%)", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Wajib Pajak Orang Pribadi (Threshold Rp500jt Bebas Pajak)", fontSize = 11.sp)
                    Switch(
                        checked = isIndividualPajak,
                        onCheckedChange = { viewModel.toggleIndividualOwner(it) },
                        modifier = Modifier.testTag("tax_mode_switch")
                    )
                }

                if (isIndividualPajak) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Masukkan Akumulasi Omzet Sebelumnya (Rp):",
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                    var prevStr by remember { mutableStateOf(previousGrossRevenue.toInt().toString()) }
                    OutlinedTextField(
                        value = prevStr,
                        onValueChange = {
                            prevStr = it
                            viewModel.updatePreviousGrossRevenue(it.toDoubleOrNull() ?: 0.0)
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("prev_omzet_input"),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Sisa batas bebas pajak tahunan Anda: Rp " + String.format("%,.0f", report.taxFreeThresholdRemaining),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Document tab controller
        TabRow(
            selectedTabIndex = activeReportTab,
            containerColor = Color.White,
            contentColor = MaterialTheme.colorScheme.primary
        ) {
            Tab(selected = activeReportTab == 0, onClick = { activeReportTab = 0 }) { Text("Laba Rugi", fontSize = 11.sp, modifier = Modifier.padding(12.dp)) }
            Tab(selected = activeReportTab == 1, onClick = { activeReportTab = 1 }) { Text("Neraca", fontSize = 11.sp, modifier = Modifier.padding(12.dp)) }
            Tab(selected = activeReportTab == 2, onClick = { activeReportTab = 2 }) { Text("Modal", fontSize = 11.sp, modifier = Modifier.padding(12.dp)) }
            Tab(selected = activeReportTab == 3, onClick = { activeReportTab = 3 }) { Text("Arus Kas", fontSize = 11.sp, modifier = Modifier.padding(12.dp)) }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Card(
            modifier = Modifier.fillMaxWidth().weight(1f),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Box(modifier = Modifier.fillMaxSize().padding(14.dp)) {
                when (activeReportTab) {
                    0 -> LabaRugiReportView(report, isIndividualPajak)
                    1 -> NeracaReportView(neraca)
                    2 -> PerubahanEkuitasView(neraca)
                    3 -> ArusKasView(neraca, report)
                }
            }
        }
    }
}

@Composable
fun LabaRugiReportView(report: IncomeStatementReport, isIndividual: Boolean) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Text("LAPORAN LABA RUGI (SAK EMKM)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text("Untuk Periode Berakhir Mei 2026", color = Color.Gray, fontSize = 11.sp)
            Spacer(modifier = Modifier.height(8.dp))
        }

        item {
            ReportRow("Pendapatan Usaha (Revenue)", report.revenue)
            ReportRow("Harga Pokok Penjualan (HPP / COGS)", -report.hpp)
            Divider(modifier = Modifier.padding(vertical = 4.dp))
            ReportRow("LABA KOTOR (Gross Profit)", report.grossProfit, isBold = true)
            Spacer(modifier = Modifier.height(8.dp))
        }

        item {
            Column {
                Text("Beban Operasional:", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color.Gray)
                report.operatingExpenses.forEach { (name, amount) ->
                    ReportRow("  - $name", -amount)
                }
                Divider(modifier = Modifier.padding(vertical = 4.dp))
                ReportRow("TOTAL BEBAN OPERASIONAL", -report.totalOperatingExpenses, isBold = true)
                Spacer(modifier = Modifier.height(10.dp))
            }
        }

        item {
            ReportRow("LABA SEBELUM PAJAK", report.netProfitBeforeTax, isBold = true)
            
            val exemptText = if (isIndividual) "Pajak Final PPh 0.5% (Eks. Thresh 500jt)" else "Pajak Final PPh 0.5% (Badan)"
            ReportRow(exemptText, -report.pphFinal, color = Color.Red)
            
            Divider(modifier = Modifier.padding(vertical = 6.dp), color = Color.Black)
            ReportRow("LABAS BERSIH SETELAH PAJAK", report.netProfitAfterTax, isBold = true, primaryBg = true)
        }
    }
}

@Composable
fun NeracaReportView(neraca: BalanceSheetReport) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Text("LAPORAN POSISI KEUANGAN (NERACA)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text("Per Tanggal 26 Mei 2026", color = Color.Gray, fontSize = 11.sp)
            Spacer(modifier = Modifier.height(8.dp))
        }

        item {
            Text("ASET (AKTIVA)", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
            ReportRow("  Kas & Setara Kas", neraca.cashAndBank)
            ReportRow("  Piutang Usaha", neraca.receivables)
            ReportRow("  Persediaan Barang", neraca.inventoryVal)
            ReportRow("  Aset Lancar Lainnya", neraca.otherCurrentAssets)
            ReportRow("  Aset Tetap (Peralatan)", neraca.fixedAssets)
            Divider(modifier = Modifier.padding(vertical = 4.dp))
            ReportRow("TOTAL ASET", neraca.totalAssets, isBold = true, primaryBg = true)
            Spacer(modifier = Modifier.height(12.dp))
        }

        item {
            Text("KEWAJIBAN & EKUITAS (PASIVA)", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
            ReportRow("  Utang Usaha / Kewajiban", neraca.liabilities)
            ReportRow("  Modal Pemilik", neraca.capital)
            ReportRow("  Laba Ditahan / Berjalan", neraca.retainedEarnings)
            Divider(modifier = Modifier.padding(vertical = 4.dp))
            ReportRow("TOTAL KEWAJIBAN & EKUITAS", neraca.totalLiabilitiesAndEquity, isBold = true, primaryBg = true)
            
            // Balance checker dot
            Spacer(modifier = Modifier.height(8.dp))
            val isBalanced = Math.abs(neraca.totalAssets - neraca.totalLiabilitiesAndEquity) < 1.0
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(if (isBalanced) Color.Green else Color.Red))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isBalanced) "Neraca Seimbang (Balanced) ✓" else "Neraca Tidak Seimbang ✗",
                    fontSize = 11.sp,
                    color = if (isBalanced) Color.Green else Color.Red,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun PerubahanEkuitasView(neraca: BalanceSheetReport) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Text("LAPORAN PERUBAHAN EKUITAS", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text("Mei 2026", color = Color.Gray, fontSize = 11.sp)
            Spacer(modifier = Modifier.height(8.dp))
        }

        item {
            ReportRow("Saldo Modal Awal", neraca.capital)
            ReportRow("Tambahan Laba Bersih Tahun Berjalan", neraca.retainedEarnings)
            Divider(modifier = Modifier.padding(vertical = 4.dp))
            ReportRow("Saldo Ekuitas Akhir", neraca.totalEquity, isBold = true, primaryBg = true)
        }
    }
}

@Composable
fun ArusKasView(neraca: BalanceSheetReport, report: IncomeStatementReport) {
    val netOps = (report.revenue - neraca.receivables) - report.hpp - report.totalOperatingExpenses
    val netChange = netOps - neraca.fixedAssets + neraca.capital

    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Text("LAPORAN ARUS KAS (METODE LANGSUNG)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text("Mei 2026", color = Color.Gray, fontSize = 11.sp)
            Spacer(modifier = Modifier.height(8.dp))
        }

        item {
            Text("Arus Kas Aktivitas Operasional:", fontWeight = FontWeight.Bold, fontSize = 11.sp)
            ReportRow("  Penerimaan Kas dari Pelanggan", report.revenue - neraca.receivables)
            ReportRow("  Pengeluaran Kas ke Supplier / HPP", -report.hpp)
            ReportRow("  Pembayaran Beban Operasional", -report.totalOperatingExpenses)
            Divider(modifier = Modifier.padding(vertical = 4.dp))
            ReportRow("Kas Bersih dari Aktivitas Operasional", netOps, isBold = true)
            Spacer(modifier = Modifier.height(10.dp))
        }

        item {
            Text("Arus Kas Aktivitas Investasi:", fontWeight = FontWeight.Bold, fontSize = 11.sp)
            ReportRow("  Pembelian Aset Tetap (Peralatan)", -neraca.fixedAssets)
            Spacer(modifier = Modifier.height(10.dp))
        }

        item {
            Text("Arus Kas Aktivitas Pendanaan:", fontWeight = FontWeight.Bold, fontSize = 11.sp)
            ReportRow("  Setor Modal Pemilik", neraca.capital)
            Divider(modifier = Modifier.padding(vertical = 4.dp))
            
            ReportRow("Perubahan Kas Bersih Periode Ini", netChange, isBold = true)
            ReportRow("Saldo Kas Awal", 0.0)
            Divider(modifier = Modifier.padding(vertical = 4.dp))
            ReportRow("Saldo Kas Akhir (Sesuai Neraca)", netChange, isBold = true, primaryBg = true)
        }
    }
}

@Composable
fun ReportRow(title: String, amount: Double, isBold: Boolean = false, primaryBg: Boolean = false, color: Color? = null) {
    val bg = if (primaryBg) Color(0xFFEFF6FF) else Color.Transparent
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bg)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
            fontSize = 12.sp
        )
        val formatted = if (amount < 0) {
            "-Rp " + String.format("%,.0f", Math.abs(amount))
        } else {
            "Rp " + String.format("%,.0f", amount)
        }
        Text(
            text = formatted,
            fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
            color = color ?: if (amount < 0) Color(0xFF991B1B) else Color.Black,
            fontSize = 12.sp
        )
    }
}

private fun printprint(rep: IncomeStatementReport, ner: BalanceSheetReport): String {
    return """
        === LAPORAN PAJAK SAK EMKM AKUNSAKU ===
        Peredaran Bruto Swasta: Rp ${String.format("%,.0f", rep.revenue)}
        HPP / Beban Pokok: Rp ${String.format("%,.0f", rep.hpp)}
        Pajak Terutang (0.5%): Rp ${String.format("%,.0f", rep.pphFinal)}
        Laba Setelah Pajak: Rp ${String.format("%,.0f", rep.netProfitAfterTax)}
        
        Kas di Tangan: Rp ${String.format("%,.0f", ner.cashAndBank)}
        Persediaan: Rp ${String.format("%,.0f", ner.inventoryVal)}
        Total Aset Pasiva: Rp ${String.format("%,.0f", ner.totalAssets)}
        ========================================
        Generated automatically by AkunSaku SaaS
    """.trimIndent()
}

// --- TAB 6: BILLING & PAYMENT CHECKOUT (Xendit/Midtrans VA and QRIS) ---
@Composable
fun BillingScreen(viewModel: AppViewModel) {
    val sub by viewModel.subscription.collectAsState()
    
    var selectedPlan by remember { mutableStateOf("Monthly (Rp450.000)") }
    var selectedMethod by remember { mutableStateOf("BCA") } // BCA, Mandiri, BRI, QRIS

    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("Pengaturan Billing & Pembayaran", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text("Layanan SaaS Subscription AkunSaku", color = Color.Gray, fontSize = 11.sp)
        }

        item {
            // Status Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("📦 Status Layanan Aktif", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.Gray)
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            val statusText = sub?.status ?: "Unpaid"
                            Text(
                                text = if (statusText == "Active") "Paket Premium Aktif" else "Layanan Non-Aktif / Terkunci",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = if (statusText == "Active") Color(0xFF047857) else Color(0xFFB91C1C)
                            )
                            Text("Model Paket: ${sub?.plan ?: "Tidak Ada"}", color = Color.Gray, fontSize = 11.sp)
                        }
                        
                        // Action manual triggers to play with subscription guard lockout demo
                        Button(
                            onClick = {
                                val nextStatus = if (sub?.status == "Active") "Expired" else "Active"
                                viewModel.setSubscriptionStatusManual(nextStatus)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(if (sub?.status == "Active") "Simulasi Habis" else "Buka Paksa", fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        item {
            // Option details
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("💎 Pilih Paket Berlangganan (UMKM)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .border(2.dp, if (selectedPlan.contains("Monthly")) MaterialTheme.colorScheme.primary else Color.Transparent, RoundedCornerShape(8.dp))
                                .background(Color(0xFFEFF6FF))
                                .clickable { selectedPlan = "Monthly (Rp450.000)" }
                                .padding(12.dp)
                        ) {
                            Column {
                                Text("Bulanan", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                Text("Rp450.000", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
                                Text("Per Bulan", color = Color.Gray, fontSize = 10.sp)
                            }
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .border(2.dp, if (selectedPlan.contains("Yearly")) MaterialTheme.colorScheme.primary else Color.Transparent, RoundedCornerShape(8.dp))
                                .background(Color(0xFFECFDF5))
                                .clickable { selectedPlan = "Yearly (Rp4.200.000)" }
                                .padding(12.dp)
                        ) {
                            Column {
                                Text("Tahunan (Hemat!)", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                Text("Rp4.200.000", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.secondary)
                                Text("Per Tahun", color = Color.Gray, fontSize = 10.sp)
                            }
                        }
                    }

                    Text("Pilih Metode Pembayaran VA / QRIS:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    val methods = listOf("BCA", "Mandiri", "BRI", "QRIS")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        methods.forEach { met ->
                            val isSelected = selectedMethod == met
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) MaterialTheme.colorScheme.primary else Color(0xFFF1F5F9))
                                    .clickable { selectedMethod = met }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = met,
                                    color = if (isSelected) Color.White else Color.Black,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }

                    Button(
                        onClick = {
                            viewModel.paySubscription(selectedPlan, selectedMethod)
                            Toast.makeText(context, "Invoice Pembayaran VA Diterbitkan!", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("pay_billing_action_btn"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Dapatkan Kode Bayar")
                    }
                }
            }
        }

        // Midtrans Sandbox Interactive Portal screen
        sub?.let { currentSub ->
            if (currentSub.status == "Unpaid") {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF6FF)),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                "🛡️ Gerbang Pembayaran Simulator (Midtrans / Xendit)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text("Invoice: ${currentSub.plan}", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            
                            if (currentSub.lastPaymentMethod == "QRIS") {
                                // Draw mock QRIS
                                Text("SCAN QRIS CODES UNTUK TRANSFER:", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                Box(
                                    modifier = Modifier
                                        .size(140.dp)
                                        .background(Color.White)
                                        .border(2.dp, Color.Black)
                                        .padding(10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Canvas(modifier = Modifier.fillMaxSize()) {
                                        // Simple representation of QR bits
                                        drawRect(color = Color.Black, size = this.size)
                                        drawRect(
                                            color = Color.White,
                                            size = Size(this.size.width / 1.2f, this.size.height / 1.2f),
                                            topLeft = Offset(this.size.width / 10f, this.size.height / 10f)
                                        )
                                        drawRect(
                                            color = Color.Black,
                                            size = Size(this.size.width / 3f, this.size.height / 3f),
                                            topLeft = Offset(this.size.width / 10f, this.size.height / 10f)
                                        )
                                        drawRect(
                                            color = Color.Black,
                                            size = Size(this.size.width / 3f, this.size.height / 3f),
                                            topLeft = Offset(this.size.width / 10f + (this.size.width * 0.5f), this.size.height / 10f)
                                        )
                                    }
                                }
                            } else {
                                Text("NOMOR VIRTUAL ACCOUNT (${currentSub.lastPaymentMethod}):", fontSize = 10.sp, color = Color.Gray)
                                Text(
                                    text = currentSub.lastPaymentVa ?: "881290318491209",
                                    fontSize = 20.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.DarkGray
                                )
                            }
                            
                            Button(
                                onClick = {
                                    viewModel.confirmPayment()
                                    Toast.makeText(context, "Pembayaran Diterima! Akun Premium Berhasil Diaktifkan.", Toast.LENGTH_LONG).show()
                                },
                                modifier = Modifier.fillMaxWidth().height(44.dp).testTag("confirm_payment_sim"),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Simulasikan Transfer Sukses", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
