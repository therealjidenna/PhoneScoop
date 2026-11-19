package com.jidenna.activitylog.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface ActivityDao {
    @Insert
    suspend fun insert(entry: ActivityEntry)

    @Query("SELECT * FROM activity_entries ORDER BY timestamp DESC LIMIT 500")
    suspend fun recent(): List<ActivityEntry>

    @Query(
        "SELECT * FROM activity_entries " +
        "WHERE content LIKE '%' || :query || '%' OR source LIKE '%' || :query || '%' " +
        "ORDER BY timestamp DESC LIMIT 500"
    )
    suspend fun search(query: String): List<ActivityEntry>

    @Query("SELECT * FROM activity_entries WHERE timestamp >= :sinceMillis ORDER BY timestamp DESC")
    suspend fun since(sinceMillis: Long): List<ActivityEntry>

    @Query("DELETE FROM activity_entries WHERE timestamp < :cutoffMillis")
    suspend fun deleteOlderThan(cutoffMillis: Long)
}
