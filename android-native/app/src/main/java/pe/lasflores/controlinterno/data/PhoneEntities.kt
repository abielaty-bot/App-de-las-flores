package pe.lasflores.controlinterno.data

import androidx.room3.Entity
import androidx.room3.Index

/** Una fila por trabajador y jornada; no es un registro de custodia. */
@Entity(tableName="phone_checks",primaryKeys=["dateIso","staffId"],indices=[Index(value=["staffId"])])
data class PhoneCheckEntity(
    val dateIso:String,val staffId:String,val nameSnapshot:String,val roleSnapshot:String,
    val status:String,val reason:String,val note:String,val updatedAtMillis:Long
)
