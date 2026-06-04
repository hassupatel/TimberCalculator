package com.example

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
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

    // Base totals calculations derived from Room Flow state
    val baseTotalCft = tallyList.sumOf { it.calculatedCft }
    val wastageCft = baseTotalCft * viewModel.wastagePercent
    val grandTotalCft = baseTotalCft + wastageCft
    val rate = viewModel.ratePerCft.toDoubleOrNull() ?: 0.0
    val grandTotalPrice = grandTotalCft * rate

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF7F2FA)) // Classic High Density soft lavender background
    ) {
        // High Density Header Bar
        HeaderSection()

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
                            shareTallyReceipt(
                                context = context,
                                items = tallyList,
                                subtotal = baseTotalCft,
                                wastagePct = viewModel.wastagePercent,
                                wastageCft = wastageCft,
                                totalCft = grandTotalCft,
                                rate = rate,
                                totalVal = grandTotalPrice
                            )
                        }
                    )
                }
            }
        }

        // Tactile Fixed Keypad Deck
        KeypadFooter(viewModel = viewModel)
    }
}

@Composable
fun HeaderSection() {
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
            Column {
                Text(
                    text = "TimberCalc Pro",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1D1B20),
                    lineHeight = 18.sp
                )
                Text(
                    text = "Merchant Volume Utility",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF49454F)
                )
            }
        }
        // Top options symbol action for density feel
        IconButton(
            onClick = {},
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = null,
                tint = Color(0xFF49454F)
            )
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
                                "${item.width}\" w × ${item.thickness}\" t × ${item.length}′ l$pcsString (Sawn)"
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
    onShareInvoice: () -> Unit
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

            // Share Digital Receipt Button
            Button(
                onClick = onShareInvoice,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF6750A4)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "SHARE RECEIPT",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun KeypadFooter(
    viewModel: TimberViewModel
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFE7E0EC))
            .navigationBarsPadding()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val keysList = listOf(
            listOf("7", "8", "9", "CLR"),
            listOf("4", "5", "6", "¼"),
            listOf("1", "2", "3", "½"),
            listOf("0", ".", "BACK", "¾")
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
                                    "CLR" -> Color(0xFFFFDADA)
                                    "¼", "½", "¾" -> Color(0xFFD0BCFF)
                                    else -> Color.White
                                }
                            )
                            .clickable {
                                when (key) {
                                    "CLR" -> viewModel.clearActiveField()
                                    "¼" -> viewModel.appendFraction(0.25)
                                    "½" -> viewModel.appendFraction(0.50)
                                    "¾" -> viewModel.appendFraction(0.75)
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
                                    "CLR" -> Color(0xFF410002)
                                    "¼", "½", "¾" -> Color(0xFF21005D)
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

// Share text receipt sender
private fun shareTallyReceipt(
    context: android.content.Context,
    items: List<TallyItem>,
    subtotal: Double,
    wastagePct: Double,
    wastageCft: Double,
    totalCft: Double,
    rate: Double,
    totalVal: Double
) {
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    val currentDateString = sdf.format(Date())

    val message = StringBuilder().apply {
        append("🌲 *TIMBER ESTIMATION RECEIPT* 🌲\n")
        append("📅 Date: $currentDateString\n")
        append("==============================\n")
        items.forEachIndexed { idx, item ->
            val num = idx + 1
            if (item.type == "RECTANGULAR") {
                val pcsString = if (item.units > 1) " × ${item.units} pcs" else ""
                append("$num. Sawn: ${item.width}\" W × ${item.thickness}\" T × ${item.length}′ L$pcsString = ")
                append(String.format(java.util.Locale.US, "%.3f CFT\n", item.calculatedCft))
            } else {
                val rule = if (item.useHoppusRule) "Hoppus" else "Cylinder"
                append("$num. Log ($rule): G: ${item.girth}\" × L: ${item.length}′ = ")
                append(String.format(java.util.Locale.US, "%.3f CFT\n", item.calculatedCft))
            }
        }
        append("==============================\n")
        append(String.format(java.util.Locale.US, "Subtotal: %.3f CFT\n", subtotal))
        if (wastagePct > 0.0) {
            append(String.format(java.util.Locale.US, "Wastage Loss (+%.0f%%): %.3f CFT\n", wastagePct * 100, wastageCft))
        }
        append(String.format(java.util.Locale.US, "Total Net Wood: %.3f CFT\n", totalCft))
        if (rate > 0.0) {
            append(String.format(java.util.Locale.US, "Rate: $ %.2f / CFT\n", rate))
            append(String.format(java.util.Locale.US, "------------------------------\n"))
            append(String.format(java.util.Locale.US, "💡 *GRAND TOTAL PRICE: $ %.2f*\n", totalVal))
        }
        append("==============================\n")
        append("Calculated fast via *Timber CFT Calculator App*")
    }.toString()

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
