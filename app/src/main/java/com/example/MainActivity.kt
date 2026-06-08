package com.example

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import com.example.data.TallyItem
import com.example.ui.ActiveField
import com.example.ui.TimberMode
import com.example.ui.TimberViewModel
import com.example.ui.theme.MyApplicationTheme
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs
import kotlin.math.floor

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val viewModel = ViewModelProvider(this)[TimberViewModel::class.java]
        setContent {
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    contentWindowInsets = WindowInsets.safeDrawing
                ) { innerPadding ->
                    TimberCalculatorApp(
                        viewModel = viewModel,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun TimberCalculatorApp(
    viewModel: TimberViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val tallyList by viewModel.tallyItems.collectAsState()

    // Intercept back button to collapse the custom keypad if expanded
    BackHandler(enabled = !viewModel.isKeyboardCollapsed) {
        viewModel.isKeyboardCollapsed = true
    }

    // State to toggle between calculator tab and customer bills tab
    var activeTab by remember { mutableStateOf("calculator") }
    var showSaveBillDialog by remember { mutableStateOf(false) }
    var inputCustomerName by remember { mutableStateOf("") }
    var showSaveCompiledDialog by remember { mutableStateOf(false) }
    var inputCompiledCustomerName by remember { mutableStateOf("") }

    // Base totals calculations derived from Room Flow state
    val baseTotalCft = tallyList.sumOf { it.calculatedCft }
    val wastageCft = baseTotalCft * viewModel.wastagePercent
    val grandTotalCft = baseTotalCft + wastageCft
    val rate = viewModel.ratePerCft.toDoubleOrNull() ?: 0.0
    val grandTotalPrice = grandTotalCft * rate

    // AlertDialog to input customer name and save bill
    if (showSaveBillDialog) {
        AlertDialog(
            onDismissRequest = { showSaveBillDialog = false },
            title = {
                Text(
                    text = "Save Customer Bill",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1D1B20)
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Assign a customer name to index and save this bill's logs.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF49454F)
                    )
                    OutlinedTextField(
                        value = inputCustomerName,
                        onValueChange = { inputCustomerName = it },
                        label = { Text("Customer Name") },
                        placeholder = { Text("e.g. John Doe") },
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("save_bill_customer_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF6750A4),
                            focusedLabelColor = Color(0xFF6750A4)
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val name = inputCustomerName.trim()
                        if (name.isNotEmpty()) {
                            viewModel.saveCustomerBill(
                                customerName = name,
                                subtotal = baseTotalCft,
                                wastagePercent = viewModel.wastagePercent,
                                totalCft = grandTotalCft,
                                rate = rate,
                                totalPrice = grandTotalPrice,
                                items = tallyList
                            )
                            Toast.makeText(context, "Bill saved successfully under '$name'!", Toast.LENGTH_SHORT).show()
                            showSaveBillDialog = false
                            inputCustomerName = ""
                        } else {
                            Toast.makeText(context, "Please enter a valid name!", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6750A4))
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveBillDialog = false }) {
                    Text("Cancel", color = Color(0xFF6750A4))
                }
            }
        )
    }

    if (showSaveCompiledDialog) {
        val pendingBillItems = viewModel.pendingBillItems
        val compiledSubtotal = pendingBillItems.sumOf { it.calculatedCft }
        val compiledWastageCft = compiledSubtotal * viewModel.wastagePercent
        val compiledTotalCft = compiledSubtotal + compiledWastageCft
        val compiledTotalPrice = pendingBillItems.sumOf { item ->
            item.calculatedCft * (1.0 + viewModel.wastagePercent) * item.rate
        }
        AlertDialog(
            onDismissRequest = { showSaveCompiledDialog = false },
            title = {
                Text(
                    text = "Save Compiled Multi-Rate Bill",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1D1B20)
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Assign a customer name to index and save this compiled multi-rate bill.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF49454F)
                    )
                    OutlinedTextField(
                        value = inputCompiledCustomerName,
                        onValueChange = { inputCompiledCustomerName = it },
                        label = { Text("Customer Name") },
                        placeholder = { Text("e.g. John Doe") },
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("save_compiled_bill_customer_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF6750A4),
                            focusedLabelColor = Color(0xFF6750A4)
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val name = inputCompiledCustomerName.trim()
                        if (name.isNotEmpty()) {
                            viewModel.saveCompiledCustomerBill(
                                customerName = name,
                                subtotal = compiledSubtotal,
                                wastagePercent = viewModel.wastagePercent,
                                totalCft = compiledTotalCft,
                                totalPrice = compiledTotalPrice
                            )
                            Toast.makeText(context, "Compiled bill saved successfully under '$name'!", Toast.LENGTH_SHORT).show()
                            showSaveCompiledDialog = false
                            inputCompiledCustomerName = ""
                        } else {
                            Toast.makeText(context, "Please enter a valid name!", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6750A4))
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveCompiledDialog = false }) {
                    Text("Cancel", color = Color(0xFF6750A4))
                }
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF7F2FA)) // Classic High Density soft lavender background
    ) {
        // High Density Header Bar sharing tab selection
        HeaderSection(
            activeTab = activeTab,
            onTabChange = { activeTab = it }
        )

        if (activeTab == "bills") {
            CustomerBillsScreen(
                viewModel = viewModel,
                onBackToCalculator = { activeTab = "calculator" },
                modifier = Modifier.weight(1f)
            )
        } else if (activeTab == "template") {
            RecipeTemplateScreen(
                viewModel = viewModel,
                onBackToCalculator = { activeTab = "calculator" },
                modifier = Modifier.weight(1f)
            )
        } else {
            // Main Scrolling Body Container
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Mode Select Toggle Bar
                item {
                    ModeSelectorTabs(
                        selectedMode = viewModel.currentMode,
                        onModeChange = {
                            viewModel.currentMode = it
                            viewModel.activeField = if (it == TimberMode.RECTANGULAR) {
                                ActiveField.RECT_WIDTH
                            } else {
                                ActiveField.ROUND_LENGTH
                            }
                        }
                    )
                }

                // Measurement Input Deck
                item {
                    InputFormCard(viewModel = viewModel)
                }

                // Live Calculated Subtotal Volume Widget
                item {
                    ActiveVolumeDisplay(
                        cftResult = viewModel.currentCalculatedCft,
                        onAddClick = {
                            viewModel.addCurrentToTally()
                            Toast.makeText(context, "Saved log definition!", Toast.LENGTH_SHORT).show()
                        }
                    )
                }

                // Ledger Tally Sheets Card
                item {
                    RecentMeasurementsCard(
                        tallyList = tallyList,
                        onDeleteClick = { viewModel.deleteTallyItem(it) },
                        onRerunClick = { viewModel.rerunCalculation(it) },
                        onClearAll = { viewModel.clearTally() }
                    )
                }

                // Material Rates & Summary Control Deck
                if (tallyList.isNotEmpty()) {
                    item {
                        InvoiceSummaryCard(
                            baseTotalCft = baseTotalCft,
                            wastageCft = wastageCft,
                            grandTotalCft = grandTotalCft,
                            grandTotalPrice = grandTotalPrice,
                            rateString = viewModel.ratePerCft,
                            activeField = viewModel.activeField,
                            onRateFocus = { viewModel.activeField = ActiveField.RATE },
                            selectedWastage = viewModel.wastagePercent,
                            onWastageChange = { viewModel.wastagePercent = it },
                            onShareInvoice = {
                                val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                                val dateStr = sdf.format(Date())
                                val formattedMessage = viewModel.formatReceiptText(
                                    customerName = "",
                                    dateString = dateStr,
                                    itemsList = tallyList,
                                    subtotalCft = baseTotalCft,
                                    wastagePct = viewModel.wastagePercent,
                                    wastageCft = wastageCft,
                                    totalCft = grandTotalCft,
                                    rate = rate,
                                    totalVal = grandTotalPrice
                                )
                                shareReceipt(context, formattedMessage)
                            },
                            onSaveBillClick = {
                                showSaveBillDialog = true
                            },
                            onAddToCompiledBillClick = {
                                viewModel.addTallyToPendingBill(tallyList, rate)
                                Toast.makeText(context, "Added batch of wood to the compiled bill!", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }

                // Dynamic Multi-rate Compiled Bill Card
                val pendingBillItems = viewModel.pendingBillItems
                if (pendingBillItems.isNotEmpty()) {
                    item {
                        CompiledBillCard(
                            viewModel = viewModel,
                            pendingItems = pendingBillItems,
                            onClearBill = {
                                viewModel.clearPendingBill()
                                Toast.makeText(context, "Cleared compiled bill!", Toast.LENGTH_SHORT).show()
                            },
                            onSaveBillClick = {
                                showSaveCompiledDialog = true
                            },
                            onShareBillClick = {
                                val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                                val dateStr = sdf.format(Date())
                                val compiledSubtotal = pendingBillItems.sumOf { it.calculatedCft }
                                val compiledWastageCft = compiledSubtotal * viewModel.wastagePercent
                                val compiledTotalCft = compiledSubtotal + compiledWastageCft
                                val compiledTotalPrice = pendingBillItems.sumOf { item ->
                                    item.calculatedCft * (1.0 + viewModel.wastagePercent) * item.rate
                                }
                                val formattedMessage = viewModel.formatReceiptText(
                                    customerName = "",
                                    dateString = dateStr,
                                    itemsList = pendingBillItems,
                                    subtotalCft = compiledSubtotal,
                                    wastagePct = viewModel.wastagePercent,
                                    wastageCft = compiledWastageCft,
                                    totalCft = compiledTotalCft,
                                    rate = 0.0,
                                    totalVal = compiledTotalPrice
                                )
                                shareReceipt(context, formattedMessage)
                            }
                        )
                    }
                }
            }

            // Tactile Fixed Keypad Deck
            KeypadFooter(viewModel = viewModel)
        }
    }
}

@Composable
fun HeaderSection(
    activeTab: String,
    onTabChange: (String) -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (activeTab == "bills" || activeTab == "template") {
                IconButton(
                    onClick = { onTabChange("calculator") },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back to Calculator",
                        tint = Color(0xFF6750A4)
                    )
                }
            } else {
                // Elegant purple circle avatar
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(Color(0xFF6750A4), RoundedCornerShape(20.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "T",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Column {
                Text(
                    text = when (activeTab) {
                        "bills" -> "Customer Bills"
                        "template" -> "Receipt Outline"
                        else -> "TimberCalc Pro"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1D1B20),
                    lineHeight = 18.sp
                )
                Text(
                    text = when (activeTab) {
                        "bills" -> "Saved Invoices"
                        "template" -> "Configure Share Text"
                        else -> "Merchant Volume Utility"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF49454F)
                )
            }
        }
        
        Box {
            IconButton(
                onClick = { showMenu = true },
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Options Menu",
                    tint = Color(0xFF49454F)
                )
            }
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Timber Calculator") },
                    onClick = {
                        onTabChange("calculator")
                        showMenu = false
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Home,
                            contentDescription = null,
                            tint = Color(0xFF6750A4)
                        )
                    }
                )
                DropdownMenuItem(
                    text = { Text("Customer Bills") },
                    onClick = {
                        onTabChange("bills")
                        showMenu = false
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.List,
                            contentDescription = null,
                            tint = Color(0xFF6750A4)
                        )
                    }
                )
                DropdownMenuItem(
                    text = { Text("Receipt Template") },
                    onClick = {
                        onTabChange("template")
                        showMenu = false
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = null,
                            tint = Color(0xFF6750A4)
                        )
                    }
                )
            }
        }
    }
}

@Composable
fun ModeSelectorTabs(
    selectedMode: TimberMode,
    onModeChange: (TimberMode) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xFFE7E0EC))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Sawn Tab
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(20.dp))
                .background(
                    if (selectedMode == TimberMode.RECTANGULAR) Color(0xFF6750A4)
                    else Color.Transparent
                )
                .clickable { onModeChange(TimberMode.RECTANGULAR) }
                .padding(vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Rectangular",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = if (selectedMode == TimberMode.RECTANGULAR) Color.White else Color(0xFF49454F)
            )
        }

        // Round Log Tab
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(20.dp))
                .background(
                    if (selectedMode == TimberMode.ROUND) Color(0xFF6750A4)
                    else Color.Transparent
                )
                .clickable { onModeChange(TimberMode.ROUND) }
                .padding(vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Round Log",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = if (selectedMode == TimberMode.ROUND) Color.White else Color(0xFF49454F)
            )
        }
    }
}

@Composable
fun InputFormCard(
    viewModel: TimberViewModel
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (viewModel.currentMode == TimberMode.RECTANGULAR) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    DimensionInputElement(
                        label = "Width (in)",
                        value = viewModel.rectWidth,
                        isActive = viewModel.activeField == ActiveField.RECT_WIDTH,
                        onFocus = { viewModel.activeField = ActiveField.RECT_WIDTH },
                        placeholder = "Width"
                    )
                }
                Box(modifier = Modifier.weight(1f)) {
                    DimensionInputElement(
                        label = "Thickness (in)",
                        value = viewModel.rectThickness,
                        isActive = viewModel.activeField == ActiveField.RECT_THICKNESS,
                        onFocus = { viewModel.activeField = ActiveField.RECT_THICKNESS },
                        placeholder = "Thick"
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    DimensionInputElement(
                        label = "Length (ft)",
                        value = viewModel.rectLength,
                        isActive = viewModel.activeField == ActiveField.RECT_LENGTH,
                        onFocus = { viewModel.activeField = ActiveField.RECT_LENGTH },
                        placeholder = "Length"
                    )
                }
                Box(modifier = Modifier.weight(1f)) {
                    DimensionInputElement(
                        label = "Units / Pieces",
                        value = viewModel.rectUnits,
                        isActive = viewModel.activeField == ActiveField.RECT_UNITS,
                        onFocus = { viewModel.activeField = ActiveField.RECT_UNITS },
                        placeholder = "1"
                    )
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    DimensionInputElement(
                        label = "Length (ft)",
                        value = viewModel.roundLength,
                        isActive = viewModel.activeField == ActiveField.ROUND_LENGTH,
                        onFocus = { viewModel.activeField = ActiveField.ROUND_LENGTH },
                        placeholder = "Length"
                    )
                }
                Box(modifier = Modifier.weight(1f)) {
                    DimensionInputElement(
                        label = "Girth / Circ (in)",
                        value = viewModel.roundGirth,
                        isActive = viewModel.activeField == ActiveField.ROUND_GIRTH,
                        onFocus = { viewModel.activeField = ActiveField.ROUND_GIRTH },
                        placeholder = "Circum"
                    )
                }
            }

            // Calculation Standard / Divisor Select Tab inside Input Deck
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFE7E0EC))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (viewModel.useHoppusRule) Color(0xFF6750A4) else Color.Transparent)
                        .clickable { viewModel.useHoppusRule = true }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Hoppus (2304)",
                        color = if (viewModel.useHoppusRule) Color.White else Color(0xFF49454F),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (!viewModel.useHoppusRule) Color(0xFF6750A4) else Color.Transparent)
                        .clickable { viewModel.useHoppusRule = false }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Cylinder (1810)",
                        color = if (!viewModel.useHoppusRule) Color.White else Color(0xFF49454F),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun DimensionInputElement(
    label: String,
    value: String,
    isActive: Boolean,
    onFocus: () -> Unit,
    placeholder: String
) {
    val fractionLabel = formatDecimalToFraction(value)
    
    // Fraction isolated symbol from value, e.g. "¼" if it ends in ".25"
    val showFractionSymbol = when {
        value.endsWith(".25") -> "¼"
        value.endsWith(".5") || value.endsWith(".50") -> "½"
        value.endsWith(".75") -> "¾"
        else -> ""
    }
    
    // Display value without the trailing decimal fraction symbol if we want,
    // or just display the whole number part
    val wholeText = if (showFractionSymbol.isNotEmpty()) {
        val dotIdx = value.indexOf('.')
        if (dotIdx != -1) value.substring(0, dotIdx) else value
    } else {
        value
    }

    Column(
        modifier = Modifier.clickable { onFocus() }
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = if (isActive) Color(0xFF6750A4) else Color(0xFF49454F),
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (isActive) Color.White else Color(0xFFE7E0EC))
                .border(
                    width = if (isActive) 2.dp else 0.dp,
                    color = if (isActive) Color(0xFF6750A4) else Color.Transparent,
                    shape = RoundedCornerShape(12.dp)
                )
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Main value number text + blinking cursor
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                if (wholeText.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color(0xFF1D1B20).copy(alpha = 0.35f)
                    )
                } else {
                    Text(
                        text = wholeText,
                        style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp),
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1D1B20)
                    )
                }
                
                // Flashing cursor visual implementation
                if (isActive) {
                    val infiniteTransition = rememberInfiniteTransition(label = "cursor")
                    val alpha by infiniteTransition.animateFloat(
                        initialValue = 1f,
                        targetValue = 0f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(durationMillis = 500, easing = LinearEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "cursor_alpha"
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Box(
                        modifier = Modifier
                            .size(width = 2.dp, height = 22.dp)
                            .background(Color(0xFF6750A4).copy(alpha = alpha))
                    )
                }
            }

            // Fraction symbol on the right (¼, ½, ¾)
            if (showFractionSymbol.isNotEmpty()) {
                Text(
                    text = showFractionSymbol,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFF6750A4),
                    modifier = Modifier.padding(end = 4.dp)
                )
            } else if (fractionLabel.isNotEmpty() && fractionLabel != value) {
                Text(
                    text = fractionLabel.substringAfter(" ", ""),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFF6750A4),
                    modifier = Modifier.padding(end = 4.dp)
                )
            }
        }
    }
}

@Composable
fun ActiveVolumeDisplay(
    cftResult: Double,
    onAddClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFFEADDFF))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(
                text = "SUBTOTAL",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF21005D),
                letterSpacing = 1.sp
            )
            Row(
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = String.format(java.util.Locale.US, "%.3f", cftResult),
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 32.sp),
                    fontWeight = FontWeight.Black,
                    color = Color(0xFF21005D)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "CFT",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Normal,
                    color = Color(0xFF21005D),
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
        }

        Button(
            onClick = onAddClick,
            enabled = cftResult > 0.0,
            modifier = Modifier.testTag("add_to_tally_button"),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF6750A4),
                contentColor = Color.White,
                disabledContainerColor = Color(0xFF6750A4).copy(alpha = 0.35f),
                disabledContentColor = Color.White.copy(alpha = 0.5f)
            ),
            shape = RoundedCornerShape(24.dp),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Text(
                "+ ADD TO BILL",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

@Composable
fun RecentMeasurementsCard(
    tallyList: List<TallyItem>,
    onDeleteClick: (Int) -> Unit,
    onRerunClick: (TallyItem) -> Unit,
    onClearAll: () -> Unit
) {
    val context = LocalContext.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFCAC4D0))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "RECENT MEASUREMENTS & HISTORY",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF49454F),
                    letterSpacing = 0.5.sp
                )
                if (tallyList.isNotEmpty()) {
                    Text(
                        text = "CLEAR TALLY",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        fontWeight = FontWeight.Black,
                        modifier = Modifier
                            .clickable { onClearAll() }
                            .padding(4.dp)
                    )
                }
            }

            if (tallyList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No saved logs yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF49454F).copy(alpha = 0.5f)
                    )
                }
            } else {
                tallyList.forEachIndexed { index, item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                onRerunClick(item)
                                Toast.makeText(context, "Loaded calculation into fields!", Toast.LENGTH_SHORT).show()
                            }
                            .padding(vertical = 4.dp, horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            val dimString = if (item.type == "RECTANGULAR") {
                                val pcsString = if (item.units > 1) " × ${item.units} pcs" else ""
                                "${item.width}\" × ${item.thickness}\" × ${item.length}′$pcsString"
                            } else {
                                "${item.girth}\" circ × ${item.length}′ (${if (item.useHoppusRule) "Hoppus" else "Cylinder"})"
                            }
                            Text(
                                text = dimString,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF1D1B20),
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = String.format(java.util.Locale.US, "%.3f CFT", item.calculatedCft),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF1D1B20)
                            )
                            IconButton(
                                onClick = {
                                    onRerunClick(item)
                                    Toast.makeText(context, "Loaded calculation into fields!", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Re-run calculation",
                                    tint = Color(0xFF6750A4),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            IconButton(
                                onClick = { onDeleteClick(item.id) },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Delete",
                                    tint = Color.Red.copy(alpha = 0.6f),
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                    if (index < tallyList.size - 1) {
                        HorizontalDivider(color = Color(0xFFE7E0EC), thickness = 1.dp)
                    }
                }
            }
        }
    }
}

@Composable
fun InvoiceSummaryCard(
    baseTotalCft: Double,
    wastageCft: Double,
    grandTotalCft: Double,
    grandTotalPrice: Double,
    rateString: String,
    activeField: ActiveField,
    onRateFocus: () -> Unit,
    selectedWastage: Double,
    onWastageChange: (Double) -> Unit,
    onShareInvoice: () -> Unit,
    onSaveBillClick: () -> Unit,
    onAddToCompiledBillClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFCAC4D0))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Tally Sheet Summary & Pricing",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF6750A4)
            )

            // Subtotal Base Volume row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Subtotal (Base Volume)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF49454F)
                )
                Text(
                    text = String.format(java.util.Locale.US, "%.3f CFT", baseTotalCft),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1D1B20)
                )
            }

            // High Density Wastage selector pills
            Column {
                Text(
                    text = "Wastage Allowance (Shrinkage Loss)",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF49454F),
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val wastageOptions = listOf(0.0 to "None", 0.10 to "+10%", 0.15 to "+15%", 0.20 to "+20%")
                    wastageOptions.forEach { (pct, label) ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (selectedWastage == pct) Color(0xFF6750A4)
                                    else Color(0xFFE7E0EC)
                                )
                                .clickable { onWastageChange(pct) }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (selectedWastage == pct) Color.White else Color(0xFF49454F)
                            )
                        }
                    }
                }
            }

            if (selectedWastage > 0.0) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Wastage Volume Loss",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF49454F)
                    )
                    Text(
                        text = String.format(java.util.Locale.US, "+%.3f CFT", wastageCft),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF6750A4),
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Custom Rate Input Element using the same keypad pattern
            Column {
                Text(
                    text = "Rate per CFT ($)",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (activeField == ActiveField.RATE) Color(0xFF6750A4) else Color(0xFF49454F)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (activeField == ActiveField.RATE) Color.White else Color(0xFFE7E0EC))
                        .border(
                            width = if (activeField == ActiveField.RATE) 2.dp else 0.dp,
                            color = if (activeField == ActiveField.RATE) Color(0xFF6750A4) else Color.Transparent,
                            shape = RoundedCornerShape(10.dp)
                        )
                        .clickable { onRateFocus() }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (rateString.isEmpty()) "Enter Rate" else "$ $rateString",
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 16.sp),
                        fontWeight = FontWeight.Bold,
                        color = if (rateString.isEmpty()) Color(0xFF1D1B20).copy(alpha = 0.35f) else Color(0xFF1D1B20)
                    )
                    if (activeField == ActiveField.RATE) {
                        val infiniteTransition = rememberInfiniteTransition(label = "rateCursor")
                        val alpha by infiniteTransition.animateFloat(
                            initialValue = 1f,
                            targetValue = 0f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(durationMillis = 500, easing = LinearEasing),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "rate_cursor_alpha"
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Box(
                            modifier = Modifier
                                .size(width = 2.dp, height = 16.dp)
                                .background(Color(0xFF6750A4).copy(alpha = alpha))
                        )
                    }
                }
            }

            HorizontalDivider(color = Color(0xFFE7E0EC), thickness = 1.dp)

            // Final Bill Summary total volumes banner
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFEADDFF).copy(alpha = 0.4f))
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "TOTAL NET VOLUME",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF21005D)
                    )
                    Row(
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Text(
                            text = String.format(java.util.Locale.US, "%.3f", grandTotalCft),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF21005D)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "CFT",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF21005D),
                            modifier = Modifier.padding(bottom = 2.dp)
                        )
                    }
                }

                if (grandTotalPrice > 0.0) {
                    Column(
                        horizontalAlignment = Alignment.End
                    ) {
                        Text(
                            text = "BILL PRICE EST",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF6750A4)
                        )
                        Text(
                            text = String.format(java.util.Locale.US, "$ %.2f", grandTotalPrice),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF6750A4)
                        )
                    }
                }
            }

            // Add to Compiled Bill Button
            Button(
                onClick = onAddToCompiledBillClick,
                enabled = baseTotalCft > 0.0 && rateString.isNotEmpty() && (rateString.toDoubleOrNull() ?: 0.0) > 0.0,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("add_to_compiled_bill_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF6750A4),
                    contentColor = Color.White,
                    disabledContainerColor = Color(0xFF6750A4).copy(alpha = 0.35f),
                    disabledContentColor = Color.White.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    "ADD TO BILL",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Share Digital Receipt Button
                Button(
                    onClick = onShareInvoice,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF6750A4)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "SHARE",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Save Bill Button
                Button(
                    onClick = onSaveBillClick,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF006874),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "SAVE BILL",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun CompiledBillCard(
    viewModel: TimberViewModel,
    pendingItems: List<TallyItem>,
    onClearBill: () -> Unit,
    onSaveBillClick: () -> Unit,
    onShareBillClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("compiled_bill_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFDF8F5)), // warm subtle wood tone
        border = BorderStroke(1.5.dp, Color(0xFFEADDFF))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically, 
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle, 
                        contentDescription = null,
                        tint = Color(0xFF6750A4),
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "COMPILED BILL (MULTI-RATE)",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF6750A4),
                        letterSpacing = 0.5.sp
                    )
                }
                
                Text(
                    text = "CLEAR BILL",
                    color = Color(0xFFB3261E),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                    fontWeight = FontWeight.Black,
                    modifier = Modifier
                        .clickable { onClearBill() }
                        .padding(4.dp)
                        .testTag("clear_compiled_bill_button")
                )
            }

            // List of added items in compiled bill
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                pendingItems.forEachIndexed { index, item ->
                    val pcsString = if (item.units > 1) " × ${item.units} pcs" else ""
                    val dimString = if (item.type == "RECTANGULAR") {
                        "${item.width}\" × ${item.thickness}\" × ${item.length}′$pcsString"
                    } else {
                        "${item.girth}\" circ × ${item.length}′ (${if (item.useHoppusRule) "Hoppus" else "Cylinder"})"
                    }
                    val itemWastagePct = viewModel.wastagePercent
                    val itemBaseCft = item.calculatedCft
                    val itemPrice = itemBaseCft * (1.0 + itemWastagePct) * item.rate

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.White, RoundedCornerShape(8.dp))
                            .border(BorderStroke(0.5.dp, Color(0xFFE7E0EC)), RoundedCornerShape(8.dp))
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${index + 1}. $dimString",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF1D1B20)
                            )
                            Text(
                                text = String.format(java.util.Locale.US, "%.3f CFT @ $ %.2f / CFT", itemBaseCft, item.rate),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF49454F)
                            )
                        }
                        Text(
                            text = String.format(java.util.Locale.US, "$ %.2f", itemPrice),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1D1B20)
                        )
                    }
                }
            }

            HorizontalDivider(color = Color(0xFFE7E0EC), thickness = 1.dp)

            // Compiled totals calculation
            val compiledSubtotal = pendingItems.sumOf { it.calculatedCft }
            val compiledWastageCft = compiledSubtotal * viewModel.wastagePercent
            val compiledTotalCft = compiledSubtotal + compiledWastageCft
            val compiledTotalPrice = pendingItems.sumOf { item ->
                item.calculatedCft * (1.0 + viewModel.wastagePercent) * item.rate
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFFFEDD5).copy(alpha = 0.5f))
                    .border(BorderStroke(0.5.dp, Color(0xFFFED7AA)), RoundedCornerShape(12.dp))
                    .padding(10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "COMPILED VOLUME",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF7C2D12)
                    )
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = String.format(java.util.Locale.US, "%.3f", compiledTotalCft),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF431407)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "CFT",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF431407)
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "ESTIMATED MULTI-RATE PRICE",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF7C2D12)
                    )
                    Text(
                        text = String.format(java.util.Locale.US, "$ %.2f", compiledTotalPrice),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        color = Color(0xFF7C2D12)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Share Multi-Rate Digital Receipt Button
                Button(
                    onClick = onShareBillClick,
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6750A4)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(imageVector = Icons.Default.Share, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("SHARE", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }

                // Save Multi-Rate Bill Button
                Button(
                    onClick = onSaveBillClick,
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF006874)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("SAVE BILL", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun KeypadFooter(
    viewModel: TimberViewModel
) {
    if (viewModel.isKeyboardCollapsed) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFE7E0EC))
                .clickable { viewModel.isKeyboardCollapsed = false }
                .padding(vertical = 12.dp)
                .navigationBarsPadding()
                .testTag("expand_keypad_bar"),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowUp,
                contentDescription = "Expand Keypad",
                tint = Color(0xFF6750A4),
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "EXPAND KEYPAD",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF6750A4)
            )
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFE7E0EC))
                .navigationBarsPadding()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Collapsible header handle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.isKeyboardCollapsed = true }
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Collapse Keypad",
                    tint = Color(0xFF49454F).copy(alpha = 0.6f),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "COLLAPSE KEYPAD",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF49454F).copy(alpha = 0.6f),
                    fontWeight = FontWeight.Bold
                )
            }

            val keysList = listOf(
                listOf("7", "8", "9", "¼"),
                listOf("4", "5", "6", "½"),
                listOf("1", "2", "3", "¾"),
                listOf("0", ".", "BACK", "NEXT")
            )

            keysList.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    row.forEach { key ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    when (key) {
                                        "¼", "½", "¾" -> Color(0xFFD0BCFF)
                                        "NEXT" -> Color(0xFF6750A4)
                                        else -> Color.White
                                    }
                                )
                                .clickable {
                                    when (key) {
                                        "¼" -> viewModel.appendFraction(0.25)
                                        "½" -> viewModel.appendFraction(0.50)
                                        "¾" -> viewModel.appendFraction(0.75)
                                        "NEXT" -> viewModel.moveToNextField()
                                        "BACK" -> viewModel.backspaceActiveField()
                                        else -> viewModel.appendChar(key)
                                    }
                                }
                                .testTag("keypad_btn_$key"),
                            contentAlignment = Alignment.Center
                        ) {
                            if (key == "BACK") {
                                Text(
                                    text = "⌫",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    ),
                                    color = Color(0xFF21005D)
                                )
                            } else {
                                Text(
                                    text = key,
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontSize = if (key.length > 2) 14.sp else 18.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    ),
                                    color = when (key) {
                                        "¼", "½", "¾" -> Color(0xFF21005D)
                                        "NEXT" -> Color.White
                                        else -> Color(0xFF1D1B20)
                                    }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(2.dp))
            // High density visual slide anchor bar decoration
            Box(
                modifier = Modifier
                    .width(100.dp)
                    .height(5.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color(0xFF49454F).copy(alpha = 0.2f))
            )
        }
    }
}

// Share text receipt sender helper
private fun shareReceipt(
    context: android.content.Context,
    message: String
) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, message)
    }
    context.startActivity(Intent.createChooser(intent, "Share Timber Receipt"))
}

fun formatDecimalToFraction(valueStr: String): String {
    val d = valueStr.toDoubleOrNull() ?: return ""
    val whole = floor(d).toInt()
    val fraction = d - whole
    val fracSymbol = when {
        abs(fraction - 0.25) < 0.05 -> "¼"
        abs(fraction - 0.50) < 0.05 -> "½"
        abs(fraction - 0.75) < 0.05 -> "¾"
        else -> ""
    }
    return if (whole == 0 && fracSymbol.isNotEmpty()) {
        fracSymbol
    } else if (fracSymbol.isNotEmpty()) {
        "$whole $fracSymbol"
    } else {
        valueStr
    }
}

// -----------------------------------------------------------------------------
// RECEIPT CUSTOMIZATION OUTLINE CONFIGURATION SCREEN
// -----------------------------------------------------------------------------

@Composable
fun RecipeTemplateScreen(
    viewModel: TimberViewModel,
    onBackToCalculator: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    var localTemplate by remember { mutableStateOf(viewModel.receiptTemplate) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF7F2FA))
            .verticalScroll(scrollState)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Explanatory Info Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFEADDFF)),
            border = BorderStroke(0.5.dp, Color(0xFF6750A4))
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "Info",
                    tint = Color(0xFF21005D),
                    modifier = Modifier.size(24.dp)
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Customize Share Outline",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF21005D)
                    )
                    Text(
                        text = "Design your own custom text layout for shared receipts! Use bracket tags like {customer} or {total_price} below, and the app will automatically fill in the values when sharing.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF21005D).copy(alpha = 0.8f)
                    )
                }
            }
        }

        // Editor Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Color(0xFFCAC4D0))
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "OUTLINE EDITOR",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF49454F)
                    )
                    
                    // Reset to Defaults
                    TextButton(
                        onClick = {
                            viewModel.resetReceiptTemplate()
                            localTemplate = viewModel.receiptTemplate
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Reset Template",
                            tint = Color(0xFFB3261E),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "RESET DEFAULT",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFB3261E)
                        )
                    }
                }

                // Text Field Editor
                OutlinedTextField(
                    value = localTemplate,
                    onValueChange = { newVal ->
                        localTemplate = newVal
                        viewModel.saveReceiptTemplate(newVal)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .testTag("receipt_template_text_editor"),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        color = Color(0xFF1D1B20)
                    ),
                    placeholder = {
                        Text(
                            text = "Enter customized share receipt template outline...",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF6750A4),
                        unfocusedBorderColor = Color(0xFFCAC4D0)
                    ),
                    shape = RoundedCornerShape(8.dp)
                )

                // Quick Tag Chooser Header
                Text(
                    text = "Tap tag below to insert:",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF49454F),
                    fontWeight = FontWeight.Bold
                )

                // Tags horizontal scroll row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val tags = listOf(
                        Pair("{customer}", "Customer"),
                        Pair("{date}", "Date"),
                        Pair("{items}", "Items List"),
                        Pair("{subtotal}", "Subtotal"),
                        Pair("{wastage}", "Wastage"),
                        Pair("{total_cft}", "Net CFT"),
                        Pair("{rate}", "Rate"),
                        Pair("{total_price}", "Total Price")
                    )

                    tags.forEach { tagPair ->
                        SuggestionChip(
                            onClick = {
                                localTemplate = localTemplate + " " + tagPair.first
                                viewModel.saveReceiptTemplate(localTemplate)
                            },
                            label = {
                                Text(
                                    text = tagPair.first,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF21005D)
                                )
                            },
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = Color(0xFFEADDFF).copy(alpha = 0.5f),
                                labelColor = Color(0xFF21005D)
                            )
                        )
                    }
                }
            }
        }

        // Live Formatting Preview Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Color(0xFFCAC4D0))
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "RECEIPT LIVE PREVIEW",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF49454F)
                )

                // Simulated receipts scroll paper tape container
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFFFFBEB), RoundedCornerShape(8.dp))
                        .border(BorderStroke(1.dp, Color(0xFFE7E0EC)), RoundedCornerShape(8.dp))
                        .padding(12.dp)
                ) {
                    val sampleItems = listOf(
                        TallyItem(id = 1, type = "RECTANGULAR", width = 6.0, thickness = 3.0, length = 12.0, units = 5, calculatedCft = 15.0, useHoppusRule = true),
                        TallyItem(id = 2, type = "ROUND", width = 0.0, thickness = 0.0, length = 10.0, units = 1, calculatedCft = 10.0, girth = 48.0, useHoppusRule = true)
                    )
                    
                    val formattedPreview = viewModel.formatReceiptText(
                        customerName = "Ram Timber Traders Ltd",
                        dateString = "2026-06-05 14:15",
                        itemsList = sampleItems,
                        subtotalCft = 25.0,
                        wastagePct = 0.10,
                        wastageCft = 2.5,
                        totalCft = 27.5,
                        rate = 18.0,
                        totalVal = 495.0
                    )

                    Text(
                        text = formattedPreview,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = Color(0xFF1D1B20),
                        lineHeight = 16.sp
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(30.dp))
    }
}

// -----------------------------------------------------------------------------
// CUSTOMER BILLS MODULE SCREEN & INVOICE MANAGEMENT
// -----------------------------------------------------------------------------

@Composable
fun CustomerBillsScreen(
    viewModel: TimberViewModel,
    onBackToCalculator: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val billsList by viewModel.customerBills.collectAsState()
    
    // Group bills by customer name for standard index list
    val groupedBills = remember(billsList) {
        billsList.groupBy { it.customerName }
    }

    // Keep track of which customer cards are expanded
    var expandedCustomer by remember { mutableStateOf<String?>(null) }
    // Inside each customer, we can click to expand particular bills details
    var expandedBillId by remember { mutableStateOf<Int?>(null) }

    // Dialog confirmation states
    var showDeleteConfirmationDialog by remember { mutableStateOf(false) }
    var billToDelete by remember { mutableStateOf<com.example.data.CustomerBill?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF7F2FA))
    ) {
        if (billsList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.List,
                        contentDescription = "No Bills",
                        modifier = Modifier.size(64.dp),
                        tint = Color(0xFF6750A4).copy(alpha = 0.4f)
                    )
                    Text(
                        text = "No saved customer bills yet.",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF49454F)
                    )
                    Text(
                        text = "Add wood logs to your tally sheet on the main tab, then select 'Save Bill' to archive the summaries here.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = Color(0xFF49454F).copy(alpha = 0.7f)
                    )
                    Button(
                        onClick = onBackToCalculator,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6750A4))
                    ) {
                        Text("Fresh Draft Tally Sheet")
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                groupedBills.forEach { (customerName, bills) ->
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            border = BorderStroke(1.dp, Color(0xFFCAC4D0))
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                // Header row of customer group
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            expandedCustomer = if (expandedCustomer == customerName) null else customerName
                                        }
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .background(Color(0xFFEADDFF), RoundedCornerShape(18.dp)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = customerName.firstOrNull()?.uppercase() ?: "C",
                                                color = Color(0xFF21005D),
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.titleSmall
                                            )
                                        }
                                        Column {
                                            Text(
                                                text = customerName,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF1D1B20)
                                            )
                                            val count = bills.size
                                            Text(
                                                text = if (count == 1) "1 Saved Invoice" else "$count Saved Invoices",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Color(0xFF49454F)
                                            )
                                        }
                                    }
                                    
                                    Icon(
                                        imageVector = if (expandedCustomer == customerName) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                        contentDescription = "Expand/Collapse",
                                        tint = Color(0xFF49454F)
                                    )
                                }

                                if (expandedCustomer == customerName) {
                                    HorizontalDivider(
                                        color = Color(0xFFE7E0EC),
                                        thickness = 1.dp,
                                        modifier = Modifier.padding(vertical = 10.dp)
                                    )
                                    
                                    // List Bills for this Customer
                                    Column(
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        bills.forEachIndexed { billIndex, bill ->
                                            val isBillDetailed = expandedBillId == bill.id
                                            val sdf = SimpleDateFormat("dd MMM yyyy, h:mm a", Locale.getDefault())
                                            val billDate = sdf.format(Date(bill.timestamp))
                                            
                                            // Individual Bill Row Card
                                            Card(
                                                modifier = Modifier.fillMaxWidth(),
                                                shape = RoundedCornerShape(10.dp),
                                                colors = CardDefaults.cardColors(containerColor = Color(0xFFF7F2FA).copy(alpha = 0.5f)),
                                                border = BorderStroke(0.5.dp, Color(0xFFCAC4D0))
                                            ) {
                                                Column(modifier = Modifier.padding(10.dp)) {
                                                    // Top Row click to expand items list, now with a separate direct Delete action button
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        // Tap details to expand/collapse
                                                        Row(
                                                            modifier = Modifier
                                                                .weight(1f)
                                                                .clickable {
                                                                    expandedBillId = if (isBillDetailed) null else bill.id
                                                                }
                                                                .padding(vertical = 4.dp),
                                                            horizontalArrangement = Arrangement.SpaceBetween,
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Column(modifier = Modifier.weight(1.0f)) {
                                                                Text(
                                                                    text = "Invoice on $billDate",
                                                                    style = MaterialTheme.typography.bodyMedium,
                                                                    fontWeight = FontWeight.Bold,
                                                                    color = Color(0xFF1D1B20)
                                                                )
                                                                val decodedCount = com.example.data.CustomerBill.deserializeItems(bill.itemsJson).size
                                                                Text(
                                                                    text = "$decodedCount Timber Wood Item" + if (decodedCount != 1) "s" else "",
                                                                    style = MaterialTheme.typography.bodySmall,
                                                                    color = Color(0xFF49454F)
                                                                )
                                                            }
                                                            Column(
                                                                horizontalAlignment = Alignment.End,
                                                                modifier = Modifier.padding(end = 8.dp)
                                                            ) {
                                                                Text(
                                                                    text = String.format(Locale.US, "%.3f CFT", bill.totalCft),
                                                                    style = MaterialTheme.typography.bodyMedium,
                                                                    fontWeight = FontWeight.ExtraBold,
                                                                    color = Color(0xFF6750A4)
                                                                )
                                                                if (bill.totalPrice > 0.0) {
                                                                    Text(
                                                                        text = String.format(Locale.US, "$ %.2f", bill.totalPrice),
                                                                        style = MaterialTheme.typography.titleSmall,
                                                                        fontWeight = FontWeight.Black,
                                                                        color = Color(0xFF006874)
                                                                    )
                                                                }
                                                            }
                                                        }

                                                        // Quick individual bill delete icon button
                                                        IconButton(
                                                            onClick = {
                                                                billToDelete = bill
                                                                showDeleteConfirmationDialog = true
                                                            },
                                                            modifier = Modifier
                                                                .size(40.dp)
                                                                .testTag("delete_bill_btn_${bill.id}")
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Default.Delete,
                                                                contentDescription = "Delete Bill",
                                                                tint = Color.Red.copy(alpha = 0.7f),
                                                                modifier = Modifier.size(20.dp)
                                                            )
                                                        }
                                                    }

                                                    // Expanded items details & actions
                                                    if (isBillDetailed) {
                                                        HorizontalDivider(
                                                            color = Color(0xFFE7E0EC),
                                                            thickness = 0.5.dp,
                                                            modifier = Modifier.padding(vertical = 8.dp)
                                                        )

                                                        val billItems = com.example.data.CustomerBill.deserializeItems(bill.itemsJson)
                                                        Column(
                                                            modifier = Modifier.padding(horizontal = 4.dp),
                                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                                        ) {
                                                            billItems.forEachIndexed { itemIdx, item ->
                                                                Row(
                                                                    modifier = Modifier.fillMaxWidth(),
                                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                                    verticalAlignment = Alignment.CenterVertically
                                                                ) {
                                                                    val nameIndex = itemIdx + 1
                                                                    val itemLabel = if (item.type == "RECTANGULAR") {
                                                                        val pcs = if (item.units > 1) " × ${item.units} pcs" else ""
                                                                        "$nameIndex. ${item.width}\" × ${item.thickness}\" × ${item.length}′$pcs${if (item.rate > 0.0) String.format(Locale.US, " (@ $%.2f)", item.rate) else ""}"
                                                                    } else {
                                                                        val rule = if (item.useHoppusRule) "Hoppus" else "Cylinder"
                                                                        "$nameIndex. Log ($rule): G: ${item.girth}\" × L: ${item.length}′${if (item.rate > 0.0) String.format(Locale.US, " (@ $%.2f)", item.rate) else ""}"
                                                                    }
                                                                    Text(
                                                                        text = itemLabel,
                                                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                                                        color = Color(0xFF49454F),
                                                                        modifier = Modifier.weight(1.0f)
                                                                    )
                                                                    Text(
                                                                        text = String.format(Locale.US, "%.3f CFT", item.calculatedCft),
                                                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                                                        fontWeight = FontWeight.Bold,
                                                                        color = Color(0xFF1D1B20)
                                                                    )
                                                                }
                                                            }
                                                            
                                                            HorizontalDivider(
                                                                color = Color(0xFFE7E0EC),
                                                                thickness = 0.5.dp,
                                                                modifier = Modifier.padding(vertical = 6.dp)
                                                            )

                                                            // Metadata subtotal wastage rows
                                                            Row(
                                                                modifier = Modifier.fillMaxWidth(),
                                                                horizontalArrangement = Arrangement.SpaceBetween
                                                            ) {
                                                                Text(
                                                                    text = "Subtotal: ${String.format(Locale.US, "%.3f CFT", bill.subtotalCft)}" +
                                                                            if (bill.wastagePercent > 0.0) " | Wastage: +${(bill.wastagePercent * 100).toInt()}%" else "",
                                                                    style = MaterialTheme.typography.labelSmall,
                                                                    color = Color(0xFF49454F)
                                                                )
                                                                if (bill.ratePerCft > 0.0) {
                                                                    Text(
                                                                        text = "Rate: $ ${String.format(Locale.US, "%.2f", bill.ratePerCft)}",
                                                                        style = MaterialTheme.typography.labelSmall,
                                                                        color = Color(0xFF49454F)
                                                                    )
                                                                } else {
                                                                    val hasAnyRates = billItems.any { it.rate > 0.0 }
                                                                    if (hasAnyRates) {
                                                                        Text(
                                                                            text = "Multi-Rate Bill",
                                                                            style = MaterialTheme.typography.labelSmall,
                                                                            color = Color(0xFF6750A4),
                                                                            fontWeight = FontWeight.Bold
                                                                        )
                                                                    }
                                                                }
                                                            }

                                                            Spacer(modifier = Modifier.height(10.dp))

                                                            // Action Bar triggers
                                                            Row(
                                                                modifier = Modifier.fillMaxWidth(),
                                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                            ) {
                                                                // Restore draft to workspace
                                                                OutlinedButton(
                                                                    onClick = {
                                                                        viewModel.restoreBill(bill)
                                                                        Toast.makeText(context, "Restored bill draft to active workspace!", Toast.LENGTH_SHORT).show()
                                                                        onBackToCalculator()
                                                                    },
                                                                    modifier = Modifier
                                                                        .weight(1.0f)
                                                                        .height(36.dp),
                                                                    shape = RoundedCornerShape(8.dp),
                                                                    contentPadding = PaddingValues(0.dp),
                                                                    colors = ButtonDefaults.outlinedButtonColors(
                                                                        contentColor = Color(0xFF6750A4)
                                                                    ),
                                                                    border = BorderStroke(1.dp, Color(0xFF6750A4))
                                                                ) {
                                                                    Icon(
                                                                        imageVector = Icons.Default.Refresh,
                                                                        contentDescription = null,
                                                                        modifier = Modifier.size(14.dp)
                                                                    )
                                                                    Spacer(modifier = Modifier.width(4.dp))
                                                                    Text(
                                                                        text = "RESTORE DRAFT",
                                                                        style = MaterialTheme.typography.labelSmall,
                                                                        fontWeight = FontWeight.Bold
                                                                    )
                                                                }

                                                                // Share Bill text
                                                                Button(
                                                                    onClick = {
                                                                        val sdfDetail = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                                                                         val dateStrDetail = sdfDetail.format(Date(bill.timestamp))
                                                                         val itemsDetail = com.example.data.CustomerBill.deserializeItems(bill.itemsJson)
                                                                         val textBody = viewModel.formatReceiptText(
                                                                             customerName = customerName,
                                                                             dateString = dateStrDetail,
                                                                             itemsList = itemsDetail,
                                                                             subtotalCft = bill.subtotalCft,
                                                                             wastagePct = bill.wastagePercent,
                                                                             wastageCft = bill.subtotalCft * bill.wastagePercent,
                                                                             totalCft = bill.totalCft,
                                                                             rate = bill.ratePerCft,
                                                                             totalVal = bill.totalPrice
                                                                         )
                                                                         shareReceipt(context, textBody)
                                                                    },
                                                                    modifier = Modifier
                                                                        .weight(1.0f)
                                                                        .height(36.dp),
                                                                    shape = RoundedCornerShape(8.dp),
                                                                    contentPadding = PaddingValues(0.dp),
                                                                    colors = ButtonDefaults.buttonColors(
                                                                        containerColor = Color(0xFF6750A4),
                                                                        contentColor = Color.White
                                                                    )
                                                                ) {
                                                                    Icon(
                                                                        imageVector = Icons.Default.Share,
                                                                        contentDescription = null,
                                                                        modifier = Modifier.size(14.dp)
                                                                    )
                                                                    Spacer(modifier = Modifier.width(4.dp))
                                                                    Text(
                                                                        text = "SHARE",
                                                                        style = MaterialTheme.typography.labelSmall,
                                                                        fontWeight = FontWeight.Bold
                                                                    )
                                                                }

                                                                // Delete Bill inside expanded detail view
                                                                IconButton(
                                                                    onClick = {
                                                                        billToDelete = bill
                                                                        showDeleteConfirmationDialog = true
                                                                    },
                                                                    modifier = Modifier
                                                                        .size(36.dp)
                                                                        .border(1.dp, Color.Red.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                                                        .testTag("detail_delete_bill_btn_${bill.id}"),
                                                                    colors = IconButtonDefaults.iconButtonColors(
                                                                        contentColor = Color.Red
                                                                    )
                                                                ) {
                                                                    Icon(
                                                                        imageVector = Icons.Default.Delete,
                                                                        contentDescription = "Delete Bill",
                                                                        modifier = Modifier.size(16.dp)
                                                                    )
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Delete Confirmation Dialog for accidental click protection
        if (showDeleteConfirmationDialog && billToDelete != null) {
            val bill = billToDelete!!
            val sdf = SimpleDateFormat("dd MMM yyyy, h:mm a", Locale.getDefault())
            val billDate = sdf.format(Date(bill.timestamp))
            AlertDialog(
                onDismissRequest = {
                    showDeleteConfirmationDialog = false
                    billToDelete = null
                },
                title = {
                    Text(
                        text = "Delete Saved Bill",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1D1B20)
                    )
                },
                text = {
                    Text(
                        text = "Are you sure you want to permanently delete the saved bill for '${bill.customerName}' created on $billDate?\n\nThis will remove the archived log records permanently.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF49454F)
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.deleteCustomerBill(bill.id)
                            Toast.makeText(context, "Deleted saved bill for '${bill.customerName}'", Toast.LENGTH_SHORT).show()
                            showDeleteConfirmationDialog = false
                            billToDelete = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                        modifier = Modifier.testTag("confirm_delete_bill_btn")
                    ) {
                        Text("Delete", color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showDeleteConfirmationDialog = false
                            billToDelete = null
                        },
                        modifier = Modifier.testTag("cancel_delete_bill_btn")
                    ) {
                        Text("Cancel", color = Color(0xFF6750A4))
                    }
                }
            )
        }
    }
}


