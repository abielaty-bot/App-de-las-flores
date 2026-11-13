package pe.lasflores.controlinterno.data

import androidx.room3.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName="operational_templates")
data class OperationalTemplateEntity(
    @PrimaryKey val id:String, val version:Int, val groupName:String,
    val titular:String, val location:String, val frequency:String,
    val deadline:String, val label:String, val evaluation:String,
    val standard:String, val priority:String, val evidencePolicy:String,
    val source:String, val validation:String, val active:Boolean
)

@Entity(tableName="operational_instances", indices=[Index(value=["periodKey"])])
data class OperationalInstanceEntity(
    @PrimaryKey val id:String, val periodKey:String, val templateId:String,
    val templateVersion:Int, val labelSnapshot:String, val standardSnapshot:String,
    val locationSnapshot:String, val staffId:String?, val staffNameSnapshot:String,
    val initialResult:String, val correction:String, val note:String,
    val attributable:Boolean, val reviewedAtMillis:Long?, val updatedAtMillis:Long
)

@Entity(tableName="operational_events", indices=[Index(value=["instanceId"])])
data class OperationalEventEntity(
    @PrimaryKey val id:String, val instanceId:String, val action:String,
    val beforeValue:String, val afterValue:String, val note:String, val atMillis:Long
)

@Dao
interface OperationalDao {
    @Query("SELECT * FROM operational_templates ORDER BY groupName, id")
    fun observeTemplates():Flow<List<OperationalTemplateEntity>>
    @Query("SELECT * FROM operational_templates")
    suspend fun templates():List<OperationalTemplateEntity>
    @Insert(onConflict=OnConflictStrategy.IGNORE)
    suspend fun insertTemplates(rows:List<OperationalTemplateEntity>)
    @Upsert suspend fun saveTemplate(row:OperationalTemplateEntity)
    @Query("SELECT * FROM operational_instances WHERE periodKey=:periodKey ORDER BY locationSnapshot,labelSnapshot")
    fun observePeriod(periodKey:String):Flow<List<OperationalInstanceEntity>>
    @Query("SELECT * FROM operational_instances WHERE id=:id")
    suspend fun instance(id:String):OperationalInstanceEntity?
    @Insert(onConflict=OnConflictStrategy.IGNORE)
    suspend fun insertInstance(row:OperationalInstanceEntity):Long
    @Upsert suspend fun saveInstance(row:OperationalInstanceEntity)
    @Insert suspend fun addEvent(row:OperationalEventEntity)
    @Query("SELECT * FROM operational_instances") suspend fun allInstances():List<OperationalInstanceEntity>
    @Query("SELECT * FROM operational_events") suspend fun allEvents():List<OperationalEventEntity>
    @Query("DELETE FROM operational_events") suspend fun clearEvents()
    @Query("DELETE FROM operational_instances") suspend fun clearInstances()
    @Query("DELETE FROM operational_templates") suspend fun clearTemplates()
    @Query("SELECT * FROM operational_events WHERE instanceId=:id ORDER BY atMillis")
    fun observeEvents(id:String):Flow<List<OperationalEventEntity>>
}
