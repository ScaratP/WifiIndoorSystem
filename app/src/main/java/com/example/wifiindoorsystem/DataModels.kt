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
    val imageId: Int = R.drawable.floor_map, // 默認使用 floor_map 圖片
    val scanCount: Int = 0 // 新增掃描次數字段，預設為0
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
    val frequency: Int,
    val batchId: String, // 新增: 批次識別符
    val scanTime: Long   // 新增: 掃描時間
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
    val bssid: String, 
    val ssid: String,
    val level: Int, 
    val frequency: Int,
    val batchId: String = UUID.randomUUID().toString(), // 預設每個讀數有自己的批次ID
    val scanTime: Long = System.currentTimeMillis()    // 預設為當前時間
)
data class ReferencePoint(
    val id: String, val name: String,
    val x: Double, val y: Double,
    val timestamp: Long,
    val wifiReadings: List<WifiReading>,
    val imageId: Int = R.drawable.floor_map, // 加入 imageId 屬性
    val appendReadings: Boolean = false, // 新增: 決定是否追加而非覆蓋
    val scanCount: Int = 0 // 新增掃描次數字段，預設為0
) {
    companion object {
        fun createSimplePoint(name: String, x: Double, y: Double, imageId: Int = R.drawable.floor_map, scanCount: Int = 0): ReferencePoint {
            return ReferencePoint(
                id = UUID.randomUUID().toString(),
                name = name,
                x = x,
                y = y,
                timestamp = System.currentTimeMillis(),
                wifiReadings = emptyList(), // MapScreen 中的點預設沒有 wifiReadings
                imageId = imageId, // 設置圖片 ID
                scanCount = scanCount // 設置掃描次數為0
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

// 新增統計模式枚舉類
enum class StatsMode {
    AVERAGE, MAX, MIN, LATEST, RAW
}

// 新增擴展WifiReading的方法，用於分組批次處理
fun List<WifiReading>.groupByBSSIDAndBatch(): Map<String, Map<String, List<WifiReading>>> {
    return this.groupBy { it.bssid }.mapValues { (_, readings) ->
        readings.groupBy { it.batchId }
    }
}

// 新增統計方法
fun List<WifiReading>.getStatsByMode(mode: StatsMode): List<WifiReading> {
    if (this.isEmpty()) return emptyList()
    if (mode == StatsMode.RAW) return this
    if (mode == StatsMode.LATEST) {
        // 按BSSID分組然後取每組中最新的一條
        return this.groupBy { it.bssid }
            .mapValues { (_, readings) -> 
                readings.maxByOrNull { it.scanTime } ?: readings.first() 
            }
            .values.toList()
    }
    
    // 按BSSID分組處理統計數據
    return this.groupBy { it.bssid }
        .map { (bssid, readings) ->
            val levels = readings.map { it.level }
            val freqs = readings.map { it.frequency }
            
            // 根據模式選擇適當的統計值
            val level = when(mode) {
                StatsMode.AVERAGE -> levels.average().toInt()
                StatsMode.MAX -> levels.maxOrNull() ?: 0
                StatsMode.MIN -> levels.minOrNull() ?: 0
                else -> levels.firstOrNull() ?: 0
            }
            
            val frequency = when(mode) {
                StatsMode.AVERAGE -> freqs.average().toInt()
                StatsMode.MAX -> freqs.maxOrNull() ?: 0
                StatsMode.MIN -> freqs.minOrNull() ?: 0
                else -> freqs.firstOrNull() ?: 0
            }
            
            WifiReading(
                bssid = bssid,
                ssid = readings.firstOrNull()?.ssid ?: "Unknown",
                level = level,
                frequency = frequency,
                batchId = "stats-${mode.name.lowercase()}",
                scanTime = readings.maxOfOrNull { it.scanTime } ?: System.currentTimeMillis()
            )
        }
}

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
                        WifiReading(re.bssid, re.ssid, re.level, re.frequency, re.batchId, re.scanTime)
                    },
                    imageId = wr.point.imageId,
                    scanCount = wr.point.scanCount // 添加掃描次數
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
                    WifiReading(re.bssid, re.ssid, re.level, re.frequency, re.batchId, re.scanTime)
                },
                imageId = wr.point.imageId,
                scanCount = wr.point.scanCount // 添加掃描次數
            )
        }
    }

    fun addReferencePoint(point: ReferencePoint) = runBlocking {
        dao.upsertPoint(
            ReferencePointEntity(
                id = point.id, name = point.name,
                x = point.x, y = point.y,
                timestamp = point.timestamp,
                imageId = point.imageId,
                scanCount = point.scanCount // 添加掃描次數
            )
        )
        
        // 刪除現有讀數，但僅當不是追加模式時
        if (!point.appendReadings) {
            dao.deleteReadingsForPoint(point.id)
        }
        
        // 確保每一批次的讀數都有相同的批次ID
        val batchId = UUID.randomUUID().toString()
        val scanTime = System.currentTimeMillis()
        
        // 將批次ID應用於所有讀數，並確保所有讀數都有相同的scanTime
        val readings = point.wifiReadings.map { wr ->
            WifiReadingEntity(
                referencePointId = point.id,
                bssid = wr.bssid, 
                ssid = wr.ssid,
                level = wr.level, 
                frequency = wr.frequency,
                batchId = wr.batchId.takeIf { it != "stats-average" } ?: batchId,
                scanTime = wr.scanTime.takeIf { it > 0 } ?: scanTime
            )
        }
        
        dao.insertReadings(readings)
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
                put("scanCount", p.scanCount) // 添加掃描次數
                val readings = JSONArray()
                p.wifiReadings.forEach { wr ->
                    readings.put(JSONObject().apply {
                        put("bssid", wr.bssid)
                        put("ssid", wr.ssid)
                        put("level", wr.level)
                        put("frequency", wr.frequency)
                        put("batchId", wr.batchId)
                        put("scanTime", wr.scanTime)
                    })
                }
                put("wifiReadings", readings)
            }
            arr.put(obj)
        }
        return arr.toString()
    }

    // 新增: 從JSON字串匯入參考點資料
    fun importReferencePointsFromJson(jsonString: String): Int = runBlocking {
        try {
            val jsonArray = JSONArray(jsonString)
            var importedCount = 0
            
            // 先獲取現有參考點資料，用於後續合併
            val existingPoints = referencePoints.associateBy { it.id }
            
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                
                // 解析基本資料
                val id = obj.getString("id")
                val name = obj.getString("name")
                val x = obj.getDouble("x")
                val y = obj.getDouble("y")
                val timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                val imageId = obj.optInt("imageId", R.drawable.floor_map)
                val scanCount = obj.optInt("scanCount", 0)
                
                // 解析 WiFi 讀數
                val readingsArray = obj.getJSONArray("wifiReadings")
                val wifiReadings = mutableListOf<WifiReading>()
                
                for (j in 0 until readingsArray.length()) {
                    val readingObj = readingsArray.getJSONObject(j)
                    val reading = WifiReading(
                        bssid = readingObj.getString("bssid"),
                        ssid = readingObj.getString("ssid"),
                        level = readingObj.getInt("level"),
                        frequency = readingObj.getInt("frequency"),
                        batchId = readingObj.optString("batchId", UUID.randomUUID().toString()),
                        scanTime = readingObj.optLong("scanTime", System.currentTimeMillis())
                    )
                    wifiReadings.add(reading)
                }
                
                // 檢查是否已存在相同ID的參考點
                val existingPoint = existingPoints[id]
                val combinedReadings = if (existingPoint != null) {
                    // 合併現有讀數和新讀數
                    existingPoint.wifiReadings + wifiReadings
                } else {
                    wifiReadings
                }
                
                // 如果點已存在，使用現有掃描次數和新掃描次數的較大值
                val finalScanCount = if (existingPoint != null) {
                    maxOf(existingPoint.scanCount, scanCount)
                } else {
                    scanCount
                }
                
                // 創建參考點物件並儲存，設置 appendReadings 為 true 以保留現有資料
                val point = ReferencePoint(
                    id = id,
                    name = name,
                    x = x,
                    y = y,
                    timestamp = timestamp,
                    wifiReadings = combinedReadings,
                    imageId = imageId,
                    appendReadings = existingPoint != null, // 如果點已存在，設為追加模式
                    scanCount = finalScanCount
                )
                
                addReferencePoint(point)
                importedCount++
            }
            
            return@runBlocking importedCount
            
        } catch (e: Exception) {
            e.printStackTrace()
            return@runBlocking -1
        }
    }

    // 計算當前位置，使用加權 k-NN 演算法
    fun calculateCurrentPosition(
        scanResults: List<android.net.wifi.ScanResult>,
        statsMode: StatsMode = StatsMode.AVERAGE // 新增統計模式參數
    ): Pair<CurrentPosition?, MapImage?> {
        // 如果沒有掃描結果或參考點，則無法計算位置
        if (scanResults.isEmpty() || referencePoints.isEmpty()) {
            return Pair(null, null)
        }

        // 將掃描結果轉換為 BSSID -> 信號強度 的映射，方便後續計算
        val currentWifiMap = scanResults.associate { it.BSSID to it.level }
        
        // 如果沒有足夠的 Wi-Fi 訊號，無法可靠計算
        if (currentWifiMap.size < 3) {
            return Pair(null, null)
        }
        
        // 計算每個參考點與當前 Wi-Fi 環境的相似度及權重
        val weightedPoints = referencePoints
            .filter { it.wifiReadings.isNotEmpty() } // 只使用有 Wi-Fi 讀數的參考點
            .map { point ->
                // 根據選定的統計模式處理Wi-Fi讀數
                val processedReadings = point.wifiReadings.getStatsByMode(statsMode)
                
                // 計算歐幾里德距離（訊號強度差的平方和）
                val signalDistances = processedReadings
                    .filter { it.bssid in currentWifiMap.keys } // 只比較出現在當前掃描中的 BSSID
                    .map { reading ->
                        val currentLevel = currentWifiMap[reading.bssid] ?: -100
                        val diff = reading.level - currentLevel
                        diff * diff // 平方差
                    }
                    
                // 如果沒有共同的 AP，給予一個極高的距離值
                val distance = if (signalDistances.isEmpty()) {
                    Double.MAX_VALUE
                } else {
                    // 計算訊號差距的均方根
                    Math.sqrt(signalDistances.sum().toDouble() / signalDistances.size)
                }
                
                // 計算權重 (距離越小，權重越大)
                // 對距離值進行反比例變換，避免權重過大差異
                val weight = if (distance > 0 && distance < Double.MAX_VALUE) {
                    1.0 / (distance + 1.0)
                } else {
                    0.0 // 完全不匹配的參考點權重為 0
                }
                
                // 返回點的座標及權重
                Triple(point, weight, point.imageId)
            }
            // 過濾掉權重為 0 的點
            .filter { it.second > 0.0 }
            // 選擇權重最大的 K 個點（K=5）
            .sortedByDescending { it.second }
            .take(5)
            
        // 如果沒有足夠相似的參考點，無法計算位置
        if (weightedPoints.isEmpty()) {
            return Pair(null, null)
        }
        
        // 使用提供的加權平均公式計算位置
        var weightSum = 0.0
        var weightedX = 0.0
        var weightedY = 0.0
        
        weightedPoints.forEach { (point, weight, _) ->
            weightedX += weight * point.x
            weightedY += weight * point.y
            weightSum += weight
        }
        
        // 計算最終位置
        val finalX = weightedX / weightSum
        val finalY = weightedY / weightSum
        
        // 計算定位精度 (基於權重分佈)
        // 如果最大權重與其他權重差異大，表示定位較準確
        val maxWeight = weightedPoints.firstOrNull()?.second ?: 0.0
        val avgWeight = weightedPoints.map { it.second }.average()
        
        // 計算權重比率作為精度指標 (0.0 - 1.0)
        val accuracyRatio = if (avgWeight > 0) {
            (maxWeight / avgWeight).coerceIn(0.0, 5.0) / 5.0
        } else {
            0.0
        }
        
        // 判斷應該在哪張地圖上
        val bestImageId = determineBestMapImage(weightedPoints)
        val mapImage = availableMapImages.find { it.id == bestImageId }
        
        val position = CurrentPosition(finalX, finalY, accuracyRatio)
        return Pair(position, mapImage)
    }

    // 根據參考點權重判斷當前可能所在的地圖
    private fun determineBestMapImage(weightedPoints: List<Triple<ReferencePoint, Double, Int>>): Int {
        // 如果沒有參考點，使用默認地圖
        if (weightedPoints.isEmpty()) {
            return availableMapImages.first().id
        }
        
        // 計算每個地圖 ID 的累計權重
        val mapWeights = mutableMapOf<Int, Double>()
        
        weightedPoints.forEach { (_, weight, imageId) ->
            mapWeights[imageId] = (mapWeights[imageId] ?: 0.0) + weight
        }
        
        // 返回權重最大的地圖 ID
        return mapWeights.maxByOrNull { it.value }?.key ?: availableMapImages.first().id
    }
}
