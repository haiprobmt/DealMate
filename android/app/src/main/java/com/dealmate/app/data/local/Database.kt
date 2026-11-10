package com.dealmate.app.data.local

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "recent_searches", indices = [Index(value = ["type", "query"], unique = true)])
data class RecentSearchEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    val query: String,
    val searchedAt: Long,
)

/** Last successful server response (raw JSON) per search request, for offline viewing. */
@Entity(tableName = "cached_search_results")
data class CachedSearchEntity(
    @PrimaryKey val cacheKey: String,
    val json: String,
    val savedAt: Long,
)

/** Server wishlist rows (raw JSON of the API item) in server order. */
@Entity(tableName = "wishlist_cache")
data class WishlistEntity(
    @PrimaryKey val id: Long,
    val variantId: Long,
    val productId: Long,
    val json: String,
    val position: Int,
)

@Entity(tableName = "recently_viewed")
data class RecentlyViewedEntity(
    @PrimaryKey val variantId: Long,
    val productId: Long,
    val title: String,
    val imageUrl: String?,
    val lowestFinalPrice: Long?,
    val viewedAt: Long,
)

@Dao
interface SearchDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRecent(entity: RecentSearchEntity)

    @Query("SELECT * FROM recent_searches ORDER BY searchedAt DESC LIMIT :limit")
    fun recent(limit: Int): Flow<List<RecentSearchEntity>>

    @Query("DELETE FROM recent_searches WHERE id NOT IN (SELECT id FROM recent_searches ORDER BY searchedAt DESC LIMIT 30)")
    suspend fun trimRecent()

    @Query("DELETE FROM recent_searches")
    suspend fun clearRecent()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putCached(entity: CachedSearchEntity)

    @Query("SELECT * FROM cached_search_results WHERE cacheKey = :key")
    suspend fun cached(key: String): CachedSearchEntity?

    @Query("DELETE FROM cached_search_results WHERE cacheKey NOT IN (SELECT cacheKey FROM cached_search_results ORDER BY savedAt DESC LIMIT 20)")
    suspend fun trimCached()

    @Query("DELETE FROM cached_search_results")
    suspend fun clearCached()
}

@Dao
interface WishlistDao {
    @Query("SELECT * FROM wishlist_cache ORDER BY position ASC")
    fun observe(): Flow<List<WishlistEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<WishlistEntity>)

    @Query("DELETE FROM wishlist_cache")
    suspend fun clear()

    @Query("DELETE FROM wishlist_cache WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT productId FROM wishlist_cache WHERE variantId = :variantId LIMIT 1")
    suspend fun productIdForVariant(variantId: Long): Long?

    @Query("SELECT COALESCE(MIN(position), 0) FROM wishlist_cache")
    suspend fun minPosition(): Int

    @Transaction
    suspend fun replaceAll(items: List<WishlistEntity>) {
        clear()
        insertAll(items)
    }
}

@Dao
interface ViewedDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: RecentlyViewedEntity)

    @Query("SELECT * FROM recently_viewed ORDER BY viewedAt DESC LIMIT :limit")
    fun recent(limit: Int): Flow<List<RecentlyViewedEntity>>

    @Query("DELETE FROM recently_viewed WHERE variantId NOT IN (SELECT variantId FROM recently_viewed ORDER BY viewedAt DESC LIMIT 30)")
    suspend fun trim()

    @Query("DELETE FROM recently_viewed")
    suspend fun clear()
}

@Database(
    entities = [RecentSearchEntity::class, CachedSearchEntity::class, WishlistEntity::class, RecentlyViewedEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class DealMateDatabase : RoomDatabase() {
    abstract fun searchDao(): SearchDao
    abstract fun wishlistDao(): WishlistDao
    abstract fun viewedDao(): ViewedDao
}
