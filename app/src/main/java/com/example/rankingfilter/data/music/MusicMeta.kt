package com.example.rankingfilter.data.music

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert

/** Thời lượng đã đọc được của một bài nhạc, lưu vào Room để lần sau mở màn Add Sound có luôn. */
@Entity(tableName = "music_meta")
data class MusicMeta(
    @PrimaryKey val id: Int,
    @ColumnInfo(name = "duration_ms") val durationMs: Long,
)

@Dao
interface MusicMetaDao {

    @Query("SELECT * FROM music_meta")
    suspend fun getAll(): List<MusicMeta>

    @Upsert
    suspend fun upsert(meta: MusicMeta)
}
