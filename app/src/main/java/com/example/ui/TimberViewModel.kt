package com.example.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.TallyItem
import com.example.data.TallyRepository
import com.example.data.CustomerBill
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.floor
import kotlin.math.pow

enum class TimberMode {
    RECTANGULAR, ROUND
}

enum class ActiveField {
    RECT_WIDTH, RECT_THICKNESS, RECT_LENGTH, RECT_UNITS,
    ROUND_GIRTH, ROUND_LENGTH,
    RATE
}

class TimberViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: TallyRepository

    init {
        val database = AppDatabase.getDatabase(application)
        repository = TallyRepository(database.tallyDao())
    }

    // Reactive list of tally items from database
    val tallyItems: StateFlow<List<TallyItem>> = repository.allItems.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Reactive list of saved customer bills from database
    val customerBills: StateFlow<List<CustomerBill>> = repository.allBills.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // UI Configuration & Inputs
    var currentMode by mutableStateOf(TimberMode.RECTANGULAR)
    var useHoppusRule by mutableStateOf(true) // true = Hoppus (2304), false = Cylinder (1810)
    var activeField by mutableStateOf(ActiveField.RECT_WIDTH)

    // Rectangular Inputs (Width in inches, Thickness in inches, Length in feet, Units/Pieces)
    var rectWidth by mutableStateOf("")
    var rectThickness by mutableStateOf("")
    var rectLength by mutableStateOf("")
    var rectUnits by mutableStateOf("1")

    // Round Log Inputs (Girth in inches, Length in feet)
    var roundGirth by mutableStateOf("")
    var roundLength by mutableStateOf("")

    // Pricing & Wastage Configuration
    var ratePerCft by mutableStateOf("")
    var wastagePercent by mutableStateOf(0.0) // e.g., 0.10 for 10%

    // Instant/Active calculations
    val currentCalculatedCft: Double
        get() {
            return when (currentMode) {
                TimberMode.RECTANGULAR -> {
                    val w = rectWidth.toDoubleOrNull() ?: 0.0
                    val t = rectThickness.toDoubleOrNull() ?: 0.0
                    val l = rectLength.toDoubleOrNull() ?: 0.0
                    val u = rectUnits.toIntOrNull() ?: 1
                    val finalUnits = if (u <= 0) 1 else u
                    if (w <= 0.0 || t <= 0.0 || l <= 0.0) 0.0
                    else (w * t * l) / 144.0 * finalUnits
                }
                TimberMode.ROUND -> {
                    val g = roundGirth.toDoubleOrNull() ?: 0.0
                    val l = roundLength.toDoubleOrNull() ?: 0.0
                    if (g <= 0.0 || l <= 0.0) 0.0
                    else {
                        val divisor = if (useHoppusRule) 2304.0 else 1810.0
                        (g.pow(2.0) * l) / divisor
                    }
                }
            }
        }

    // Append standard fractions (0.25, 0.50, 0.75) to the active text field
    fun appendFraction(fraction: Double) {
        if (activeField == ActiveField.RECT_UNITS) return
        val currentText = getActiveText()
        val num = currentText.toDoubleOrNull() ?: 0.0
        val whole = floor(num)
        val newVal = whole + fraction
        
        // Format to prevent floating point representation noise, e.g., 5.2500000001
        val formatted = if (newVal % 1.0 == 0.0) {
            newVal.toInt().toString()
        } else {
            String.format(java.util.Locale.US, "%.2f", newVal)
        }
        
        setActiveText(formatted)
    }

    // Helper functions to get/set the active text field
    fun getActiveFieldValue(): String {
        return getActiveText()
    }

    fun appendChar(char: String) {
        if (activeField == ActiveField.RECT_UNITS && char == ".") return
        val currentText = getActiveText()
        if (char == ".") {
            if (currentText.contains(".")) return
            if (currentText.isEmpty()) {
                setActiveText("0.")
                return
            }
        }
        setActiveText(currentText + char)
    }

    fun backspaceActiveField() {
        val current = getActiveText()
        if (current.isNotEmpty()) {
            setActiveText(current.dropLast(1))
        }
    }

    private fun getActiveText(): String {
        return when (activeField) {
            ActiveField.RECT_WIDTH -> rectWidth
            ActiveField.RECT_THICKNESS -> rectThickness
            ActiveField.RECT_LENGTH -> rectLength
            ActiveField.RECT_UNITS -> rectUnits
            ActiveField.ROUND_GIRTH -> roundGirth
            ActiveField.ROUND_LENGTH -> roundLength
            ActiveField.RATE -> ratePerCft
        }
    }

    private fun setActiveText(text: String) {
        when (activeField) {
            ActiveField.RECT_WIDTH -> rectWidth = text
            ActiveField.RECT_THICKNESS -> rectThickness = text
            ActiveField.RECT_LENGTH -> rectLength = text
            ActiveField.RECT_UNITS -> {
                val filtered = text.filter { it.isDigit() }
                rectUnits = filtered
            }
            ActiveField.ROUND_GIRTH -> roundGirth = text
            ActiveField.ROUND_LENGTH -> roundLength = text
            ActiveField.RATE -> ratePerCft = text
        }
    }

    fun handleTextInput(text: String) {
        // Ensure input contains only numbers, decimal points
        val filtered = text.filter { it.isDigit() || it == '.' }
        
        // Prevent multiple decimals
        if (filtered.count { it == '.' } > 1) return
        
        setActiveText(filtered)
    }

    fun clearActiveField() {
        setActiveText("")
    }

    fun clearAllCurrentInputs() {
        rectWidth = ""
        rectThickness = ""
        rectLength = ""
        rectUnits = "1"
        roundGirth = ""
        roundLength = ""
    }

    fun addCurrentToTally() {
        val cft = currentCalculatedCft
        if (cft <= 0.0) return

        val item = when (currentMode) {
            TimberMode.RECTANGULAR -> {
                val u = rectUnits.toIntOrNull() ?: 1
                val finalUnits = if (u <= 0) 1 else u
                TallyItem(
                    type = "RECTANGULAR",
                    length = rectLength.toDoubleOrNull() ?: 0.0,
                    width = rectWidth.toDoubleOrNull() ?: 0.0,
                    thickness = rectThickness.toDoubleOrNull() ?: 0.0,
                    units = finalUnits,
                    calculatedCft = cft
                )
            }
            TimberMode.ROUND -> {
                TallyItem(
                    type = "ROUND",
                    length = roundLength.toDoubleOrNull() ?: 0.0,
                    girth = roundGirth.toDoubleOrNull() ?: 0.0,
                    useHoppusRule = useHoppusRule,
                    calculatedCft = cft
                )
            }
        }

        viewModelScope.launch {
            repository.insert(item)
            // Clear dimensions after successfully adding, keeping the user in flow
            clearAllCurrentInputs()
        }
    }

    fun deleteTallyItem(id: Int) {
        viewModelScope.launch {
            repository.delete(id)
        }
    }

    fun clearTally() {
        viewModelScope.launch {
            repository.clearAll()
        }
    }

    private fun formatValueForInput(value: Double): String {
        return if (value % 1.0 == 0.0) {
            value.toInt().toString()
        } else {
            value.toString()
        }
    }

    fun rerunCalculation(item: TallyItem) {
        if (item.type == "RECTANGULAR") {
            currentMode = TimberMode.RECTANGULAR
            rectWidth = item.width?.let { formatValueForInput(it) } ?: ""
            rectThickness = item.thickness?.let { formatValueForInput(it) } ?: ""
            rectLength = item.length.let { formatValueForInput(it) }
            rectUnits = item.units.toString()
            activeField = ActiveField.RECT_WIDTH
        } else {
            currentMode = TimberMode.ROUND
            roundGirth = item.girth?.let { formatValueForInput(it) } ?: ""
            roundLength = item.length.let { formatValueForInput(it) }
            useHoppusRule = item.useHoppusRule
            activeField = ActiveField.ROUND_LENGTH
        }
    }

    // Customer Bill actions
    fun saveCustomerBill(
        customerName: String,
        subtotal: Double,
        wastagePercent: Double,
        totalCft: Double,
        rate: Double,
        totalPrice: Double,
        items: List<TallyItem>
    ) {
        val serialized = CustomerBill.serializeItems(items)
        val bill = CustomerBill(
            customerName = customerName.trim(),
            subtotalCft = subtotal,
            wastagePercent = wastagePercent,
            totalCft = totalCft,
            ratePerCft = rate,
            totalPrice = totalPrice,
            itemsJson = serialized
        )
        viewModelScope.launch {
            repository.insertBill(bill)
            repository.clearAll() // Clear active slate to start new tally sheet
            clearAllCurrentInputs()
        }
    }

    fun deleteCustomerBill(id: Int) {
        viewModelScope.launch {
            repository.deleteBill(id)
        }
    }

    fun restoreBill(bill: CustomerBill) {
        val items = CustomerBill.deserializeItems(bill.itemsJson)
        viewModelScope.launch {
            repository.restoreLiveTally(items)
            
            // Recover rate and wastage from saved bill state
            ratePerCft = if (bill.ratePerCft > 0.0) {
                if (bill.ratePerCft % 1.0 == 0.0) bill.ratePerCft.toInt().toString() else bill.ratePerCft.toString()
            } else ""
            wastagePercent = bill.wastagePercent
            activeField = if (items.firstOrNull()?.type == "RECTANGULAR") {
                currentMode = TimberMode.RECTANGULAR
                ActiveField.RECT_WIDTH
            } else {
                currentMode = TimberMode.ROUND
                ActiveField.ROUND_LENGTH
            }
        }
    }
}
