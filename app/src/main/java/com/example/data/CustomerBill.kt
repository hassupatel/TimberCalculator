package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "customer_bills")
data class CustomerBill(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val customerName: String,
    val timestamp: Long = System.currentTimeMillis(),
    val subtotalCft: Double,
    val wastagePercent: Double,
    val totalCft: Double,
    val ratePerCft: Double,
    val totalPrice: Double,
    val itemsJson: String
) {
    companion object {
        fun serializeItems(items: List<TallyItem>): String {
            if (items.isEmpty()) return ""
            return items.joinToString(separator = ";") { item ->
                listOf(
                    item.type,
                    item.timestamp.toString(),
                    item.length.toString(),
                    (item.width ?: "").toString(),
                    (item.thickness ?: "").toString(),
                    (item.girth ?: "").toString(),
                    item.useHoppusRule.toString(),
                    item.units.toString(),
                    item.calculatedCft.toString(),
                    item.rate.toString()
                ).joinToString(separator = "|")
            }
        }

        fun deserializeItems(serialized: String): List<TallyItem> {
            if (serialized.isEmpty()) return emptyList()
            return try {
                serialized.split(";").map { row ->
                    val parts = row.split("|")
                    TallyItem(
                        type = parts[0],
                        timestamp = parts[1].toLongOrNull() ?: System.currentTimeMillis(),
                        length = parts[2].toDoubleOrNull() ?: 0.0,
                        width = parts[3].toDoubleOrNull(),
                        thickness = parts[4].toDoubleOrNull(),
                        girth = parts[5].toDoubleOrNull(),
                        useHoppusRule = parts[6].toBooleanStrictOrNull() ?: true,
                        units = parts[7].toIntOrNull() ?: 1,
                        calculatedCft = parts[8].toDoubleOrNull() ?: 0.0,
                        rate = if (parts.size > 9) parts[9].toDoubleOrNull() ?: 0.0 else 0.0
                    )
                }
            } catch (e: Exception) {
                emptyList()
            }
        }
    }
}
