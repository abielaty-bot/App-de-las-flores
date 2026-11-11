package pe.lasflores.controlinterno.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import pe.lasflores.controlinterno.data.*

class DashboardStatsTest {
    private val date="2026-11-11"

    private fun staff(id:String)=StaffEntity(
        id=id,fullName="Trabajador "+id,role="MOZO",
        isActive=true,createdAtMillis=0,updatedAtMillis=0
    )

    private fun inspection(id:String,result:String)=UniformInspectionEntity(
        id=id,dateIso=date,staffId=id,roleSnapshot="MOZO",
        workflow="FINALIZED",initialResult=result,
        createdAtMillis=0,updatedAtMillis=0
    )

    private fun attendance(id:String,presence:String,informed:Long?=null) =
        BriefingAttendanceEntity(
            id=id,dateIso=date,staffId=id,staffNameSnapshot="Trabajador",
            categorySnapshot=4,groupHour=11,presence=presence,
            informedAtMillis=informed,informedBy=if(informed==null)null else "Maître",
            createdAtMillis=0,updatedAtMillis=0
        )

    private fun function(id:String,result:String,void:Long?=null)=FunctionCheckEntity(
        id=id,dateIso=date,staffId="a",staffNameSnapshot="Trabajador",
        taskId=id,taskLabelSnapshot="Manteles",areaSnapshot="Oficina 1",
        periodicitySnapshot="DIARIA",result=result,note="",
        correctedAtMillis=null,correctionNote="",voidedAtMillis=void,
        createdAtMillis=0,updatedAtMillis=0
    )

    @Test fun currentDayShowsOnlyRegisteredDataAndNeverInfersAbsence() {
        val x=DashboardStats.calculate(
            today=date,
            people=listOf(staff("a"),staff("b")),
            vacations=emptyList(),overrides=emptyList(),
            inspections=listOf(inspection("a","OBSERVADO"),inspection("b","CONFORME")),
            briefingDay=date,
            briefings=listOf(
                attendance("a","MESA"),attendance("b","ASISTIO")
            ),
            functions=listOf(
                function("a","PENDING"),function("b","OK"),
                function("c","BAD"),function("d","BAD",void=1L)
            ),
            incidents=emptyList()
        )
        assertEquals(2,x.scheduled)
        assertEquals(1,x.uniformObserved)
        assertEquals(2,x.uniformReviewed)
        assertEquals(1,x.briefingPending)
        assertEquals(1,x.functionPending)
        assertEquals(1,x.functionBad)
        assertEquals(2,x.functionReviewed)
        assertEquals(0,x.incidentOpen)
    }

    @Test fun viewingOtherDayDoesNotShowMisleadingBriefingZero() {
        val x=DashboardStats.calculate(
            today=date,people=emptyList(),
            vacations=emptyList(),overrides=emptyList(),inspections=emptyList(),
            briefingDay="2026-11-10",
            briefings=listOf(attendance("a","MESA")),
            functions=emptyList(),incidents=emptyList()
        )
        assertNull(x.briefingRecorded)
        assertNull(x.briefingPending)
        assertEquals(0,x.uniformReviewed)
    }
}
