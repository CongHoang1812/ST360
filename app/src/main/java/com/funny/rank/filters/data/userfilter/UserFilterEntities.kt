package com.funny.rank.filters.data.userfilter

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

/**
 * Filter do người dùng tự tạo. [id] là UUID, cũng là tên thư mục ảnh
 * filesDir/user_filters/<id>/.
 */
@Entity(tableName = "user_filter")
data class UserFilter(
    @PrimaryKey val id: String,
    val name: String,
    /** Đường dẫn tuyệt đối tới ảnh bìa trong filesDir; null thì dùng ảnh đầu tiên. */
    @ColumnInfo(name = "cover_path") val coverPath: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

/** Một ảnh của filter, [position] từ 0 đến 11 (ứng với 12 ô hạng). */
@Entity(
    tableName = "user_filter_photo",
    foreignKeys = [
        ForeignKey(
            entity = UserFilter::class,
            parentColumns = ["id"],
            childColumns = ["filter_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("filter_id")],
)
data class UserFilterPhoto(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "filter_id") val filterId: String,
    val position: Int,
    /** Đường dẫn tuyệt đối tới file ảnh đã copy vào filesDir. */
    val path: String,
)

/** Filter kèm danh sách ảnh (quan hệ 1-n). */
data class UserFilterWithPhotos(
    @Embedded val filter: UserFilter,
    /** Chưa sắp xếp; dùng [photos]. */
    @Relation(parentColumn = "id", entityColumn = "filter_id")
    val photoRows: List<UserFilterPhoto>,
) {
    /** Ảnh đã sắp theo [UserFilterPhoto.position]. */
    val photos: List<UserFilterPhoto> get() = photoRows.sortedBy { it.position }

    /** Ảnh bìa: coverPath nếu có, không thì ảnh đầu tiên. */
    val coverPath: String? get() = filter.coverPath ?: photos.firstOrNull()?.path
}
