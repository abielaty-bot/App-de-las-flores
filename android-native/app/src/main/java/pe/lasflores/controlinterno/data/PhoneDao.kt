package pe.lasflores.controlinterno.data

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface PhoneDao{
    @Query("SELECT * FROM phone_checks ORDER BY dateIso DESC,nameSnapshot COLLATE NOCASE") fun observeChecks():Flow<List<PhoneCheckEntity>>
    @Query("SELECT * FROM phone_checks") suspend fun allChecks():List<PhoneCheckEntity>
    @Query("SELECT * FROM phone_checks WHERE dateIso=:day") suspend fun dayChecks(day:String):List<PhoneCheckEntity>
    @Upsert suspend fun save(item:PhoneCheckEntity)
    @Query("DELETE FROM phone_checks WHERE dateIso=:day AND staffId=:id AND status='PENDING'") suspend fun removePending(day:String,id:String)
    @Query("DELETE FROM phone_checks") suspend fun clear()
    @Query("SELECT * FROM activity_days WHERE module='celulares'") fun observeDays():Flow<List<DailyActivityEntity>>
    @Query("SELECT * FROM activity_days WHERE module='celulares' AND dateIso=:day LIMIT 1") suspend fun dayStatus(day:String):DailyActivityEntity?
    @Query("DELETE FROM activity_days WHERE module='celulares' AND dateIso=:day") suspend fun reopen(day:String)
}
