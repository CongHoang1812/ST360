package com.funny.rank.filters.data.userfilter

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface UserFilterDao {

    /** Danh sách filter, mới nhất trước. */
    @Transaction
    @Query("SELECT * FROM user_filter ORDER BY created_at DESC")
    fun observeAll(): Flow<List<UserFilterWithPhotos>>

    @Transaction
    @Query("SELECT * FROM user_filter WHERE id = :id")
    suspend fun getById(id: String): UserFilterWithPhotos?

    @Upsert
    suspend fun upsertFilter(filter: UserFilter)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPhotos(photos: List<UserFilterPhoto>)

    @Query("DELETE FROM user_filter_photo WHERE filter_id = :filterId")
    suspend fun deletePhotos(filterId: String)

    /** Ảnh bị xóa theo (ForeignKey CASCADE). */
    @Query("DELETE FROM user_filter WHERE id = :id")
    suspend fun deleteById(id: String)

    /** Thêm mới hoặc sửa: ghi filter rồi thay toàn bộ danh sách ảnh. */
    @Transaction
    suspend fun save(filter: UserFilter, photos: List<UserFilterPhoto>) {
        upsertFilter(filter)
        deletePhotos(filter.id)
        insertPhotos(photos)
    }
}
