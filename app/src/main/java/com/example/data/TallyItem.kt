package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tally_items")
data class TallyItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val type: String, // "RECTANGULAR" or "ROUND"
    val timestamp: Long = System.currentTimeMillis(),
    
    // Dimensions
    val length: Double, // in feet
    val width: Double? = null, // in inches
    val thickness: Double? = null, // in inches
    val girth: Double? = null, // in inches
    
    // Formula options
    val useHoppusRule: Boolean = true, // true for Hoppus, false for Actual volume (Cylinder)
    
    // Calculated Result
    val calculatedCft: Double
)
