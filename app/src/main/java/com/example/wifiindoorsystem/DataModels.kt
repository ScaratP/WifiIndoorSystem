package com.example.wifiindoorsystem

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

// --- Entities ---
@Entity(
    tableName = "reference_points",
    indices = [Index("imageId")] // 添加索引以提高查詢效率
)
data class ReferencePointEntity(
    @PrimaryKey val id: String,
    val name: String,
    val x: Double,
    val y: Double,
    val timestamp: Long,
    val imageId: Int = R.drawable.floor_map // 默認使用 floor_map 圖片
)

@Entity(
    tableName = "wifi_readings",
    foreignKeys = [
        ForeignKey(
            entity = ReferencePointEntity::class,
            parentColumns = ["id"],
            childColumns = ["referencePointId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("referencePointId")]
)
data class WifiReadingEntity(
    @PrimaryKey(autoGenerate = true) val readingId: Long = 0,
    val referencePointId: String,
    val bssid: String,
    val ssid: String,
    val level: Int,
    val frequency: Int
)

data class ReferencePointWithReadings(
    @Embedded val point: ReferencePointEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "referencePointId"
    )
    val wifiReadings: List<WifiReadingEntity>
)

// --- DAO ---
@Dao
interface ReferencePointDao {
    @Transaction
    @Query("SELECT * FROM reference_points")
    suspend fun getAllWithReadings(): List<ReferencePointWithReadings>
    
    // 新增：按圖片 ID 查詢參考點
    @Transaction
    @Query("SELECT * FROM reference_points WHERE imageId = :imageId")
    suspend fun getPointsByImageId(imageId: Int): List<ReferencePointWithReadings>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPoint(point: ReferencePointEntity)

    @Query("DELETE FROM reference_points WHERE id = :pointId")
    suspend fun deletePointById(pointId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReadings(readings: List<WifiReadingEntity>)

    @Query("DELETE FROM wifi_readings WHERE referencePointId = :pointId")
    suspend fun deleteReadingsForPoint(pointId: String)
}

// --- Room Database ---
@Database(
    entities = [ReferencePointEntity::class, WifiReadingEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun referencePointDao(): ReferencePointDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "wifi_indoor_db"
                ).build().also { INSTANCE = it }
            }
    }
}

// --- Domain Models ---
data class WifiReading(
    val bssid: String, val ssid: String,
    val level: Int, val frequency: Int
)
data class ReferencePoint(
    val id: String, val name: String,
    val x: Double, val y: Double,
    val timestamp: Long,
    val wifiReadings: List<WifiReading>,
    val imageId: Int = R.drawable.floor_map // 加入 imageId 屬性
) {
    companion object {
        fun createSimplePoint(name: String, x: Double, y: Double, imageId: Int = R.drawable.floor_map): ReferencePoint {
            return ReferencePoint(
                id = UUID.randomUUID().toString(),
                name = name,
                x = x,
                y = y,
                timestamp = System.currentTimeMillis(),
                wifiReadings = emptyList(), // MapScreen 中的點預設沒有 wifiReadings
                imageId = imageId // 設置圖片 ID
            )
        }
    }
}
data class CurrentPosition(val x: Double, val y: Double, val accuracy: Double)

// --- 圖片資訊模型 ---
data class MapImage(
    val id: Int,
    val name: String,
    val description: String = ""
)

// --- Repository / helper ---
class ReferencePointDatabase private constructor(context: Context) {
    private val dao = AppDatabase.getInstance(context).referencePointDao()

    companion object {
        @Volatile private var INSTANCE: ReferencePointDatabase? = null
        fun getInstance(ctx: Context): ReferencePointDatabase =
            INSTANCE ?: synchronized(this) {
                ReferencePointDatabase(ctx).also { INSTANCE = it }
            }
            
        // 定義可用的地圖圖片列表
        val availableMapImages = listOf(
            MapImage(R.drawable.se1, "理工一樓平面圖"),
            MapImage(R.drawable.se2, "理工二樓平面圖"),
            MapImage(R.drawable.se3, "理工三樓平面圖"),
            MapImage(R.drawable.sea4, "理工A棟四樓平面圖"),
            MapImage(R.drawable.seb4, "理工B棟四樓平面圖"),
            MapImage(R.drawable.sec4, "理工C棟四樓平面圖"),
            MapImage(R.drawable.sea5, "理工A棟五樓平面圖"),
            MapImage(R.drawable.sec5, "理工C棟五樓平面圖")
        )
    }

    // 同步取出所有點
    val referencePoints: List<ReferencePoint>
        get() = runBlocking {
            dao.getAllWithReadings().map { wr ->
                ReferencePoint(
                    id = wr.point.id,
                    name = wr.point.name,
                    x = wr.point.x,
                    y = wr.point.y,
                    timestamp = wr.point.timestamp,
                    wifiReadings = wr.wifiReadings.map { re ->
                        WifiReading(re.bssid, re.ssid, re.level, re.frequency)
                    },
                    imageId = wr.point.imageId
                )
            }
        }
    
    // 根據圖片 ID 獲取參考點
    fun getReferencePointsByImageId(imageId: Int): List<ReferencePoint> = runBlocking {
        dao.getPointsByImageId(imageId).map { wr ->
            ReferencePoint(
                id = wr.point.id,
                name = wr.point.name,
                x = wr.point.x,
                y = wr.point.y,
                timestamp = wr.point.timestamp,
                wifiReadings = wr.wifiReadings.map { re ->
                    WifiReading(re.bssid, re.ssid, re.level, re.frequency)
                },
                imageId = wr.point.imageId
            )
        }
    }

    fun addReferencePoint(point: ReferencePoint) = runBlocking {
        dao.upsertPoint(
            ReferencePointEntity(
                id = point.id, name = point.name,
                x = point.x, y = point.y,
                timestamp = point.timestamp,
                imageId = point.imageId
            )
        )
        dao.deleteReadingsForPoint(point.id)
        dao.insertReadings(point.wifiReadings.map { wr ->
            WifiReadingEntity(
                referencePointId = point.id,
                bssid = wr.bssid, ssid = wr.ssid,
                level = wr.level, frequency = wr.frequency
            )
        })
    }

    fun deleteReferencePoint(id: String) = runBlocking {
        dao.deletePointById(id)
    }

    // 匯出JSON - 加入圖片ID
    fun exportAllPointsToJson(): String {
        val arr = JSONArray()
        referencePoints.forEach { p ->
            val obj = JSONObject().apply {
                put("id", p.id)
                put("name", p.name)
                put("x", p.x)
                put("y", p.y)
                put("timestamp", p.timestamp)
                put("imageId", p.imageId)
                val readings = JSONArray()
                p.wifiReadings.forEach { wr ->
                    readings.put(JSONObject().apply {
                        put("bssid", wr.bssid)
                        put("ssid", wr.ssid)
                        put("level", wr.level)
                        put("frequency", wr.frequency)
                    })
                }
                put("wifiReadings", readings)
            }
            arr.put(obj)
        }
        return arr.toString()
    }

    // 計算當前位置（範例：加權重心）
    fun calculateCurrentPosition(scanResults: List<android.net.wifi.ScanResult>): CurrentPosition? {
        // TODO: 填入實際演算法
        return if (scanResults.isEmpty()) null
        else CurrentPosition(50.0, 50.0, 0.5)
    }
}
