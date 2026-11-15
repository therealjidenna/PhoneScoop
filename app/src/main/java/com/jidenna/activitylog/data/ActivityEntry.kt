package com.jidenna.activitylog.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "activity_entries")
data class ActivityEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    val source: String,
    val content: String,
    val timestamp: Long
)
