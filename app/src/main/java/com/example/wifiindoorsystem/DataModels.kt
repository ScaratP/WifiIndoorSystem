package com.example.wifiindoorsystem

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.room.*
import kotlinx.coroutines.flow.Flow

// ----------------- Room 資料庫相關類別 -----------------

// Room Entity 類別 - 參考點
@Entity(tableName = "reference_points")
data class ReferencePointEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val x: Double,
    val y: Double,
    val colorArgb: Int,  // 儲存顏色的ARGB值
    val timestamp: Long
)

// Room Entity 類別 - WiFi讀數
@Entity(
    tableName = "wifi_readings",
    foreignKeys = [
        ForeignKey(
            entity = ReferencePointEntity::class,
            parentColumns = ["id"],
            childColumns = ["referencePointId"],
            onDelete = ForeignKey.CASCADE  // 當參考點刪除時，相關的WiFi讀數也一併刪除
        )
    ],
    indices = [Index("referencePointId")]
)
data class WifiReadingEntity(
    @PrimaryKey(autoGenerate = true)
    val readingId: Long = 0,
    val referencePointId: String,
    val bssid: String,
    val ssid: String,
    val level: Int,
    val frequency: Int
)

// 使用 TypeConverter 處理 Color 與 Int 的轉換
class Converters {
    @TypeConverter
    fun fromColor(color: Color): Int {
        return color.toArgb()
    }
    
    @TypeConverter
    fun toColor(colorArgb: Int): Color {
        return Color(colorArgb)
    }
}

// 參考點與其WiFi讀數的關聯
data class ReferencePointWithReadings(
    @Embedded val point: ReferencePointEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "referencePointId"
    )
    val wifiReadings: List<WifiReadingEntity>
)

// DAO - 資料存取介面
@Dao
interface ReferencePointDao {
    @Query("SELECT * FROM reference_points")
    fun getAllReferencePoints(): Flow<List<ReferencePointEntity>>
    
    @Query("SELECT * FROM reference_points")
    suspend fun getAllReferencePointsSync(): List<ReferencePointEntity>
    
    @Transaction
    @Query("SELECT * FROM reference_points")
    suspend fun getReferencePointsWithReadings(): List<ReferencePointWithReadings>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReferencePoint(point: ReferencePointEntity)
    
    @Delete
    suspend fun deleteReferencePoint(point: ReferencePointEntity)
    
    @Query("DELETE FROM reference_points WHERE id = :pointId")
    suspend fun deleteReferencePointById(pointId: String)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWifiReading(reading: WifiReadingEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWifiReadings(readings: List<WifiReadingEntity>)
    
    @Query("DELETE FROM wifi_readings WHERE referencePointId = :pointId")
    suspend fun deleteWifiReadingsForPoint(pointId: String)
    
    @Transaction
    suspend fun updateReferencePointWithReadings(point: ReferencePointEntity, readings: List<WifiReadingEntity>) {
        insertReferencePoint(point)
        deleteWifiReadingsForPoint(point.id)
        insertWifiReadings(readings)
    }
    
    @Query("SELECT * FROM reference_points WHERE id = :pointId")
    suspend fun getReferencePointById(pointId: String): ReferencePointEntity?
    
    @Transaction
    @Query("SELECT * FROM reference_points WHERE id = :pointId")
    suspend fun getReferencePointWithReadingsById(pointId: String): ReferencePointWithReadings?
}

// Room Database
@Database(
    entities = [ReferencePointEntity::class, WifiReadingEntity::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun referencePointDao(): ReferencePointDao
    
    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null
        
        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "wifi_indoor_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
