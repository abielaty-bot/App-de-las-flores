package pe.lasflores.controlinterno.data

import androidx.room3.withWriteTransaction
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.Flow

class OfflineMaterialRepository(
    private val db: LasFloresDatabase,
    private val staffDao: StaffDao,
    private val dao: MaterialDao
) {
    val deliveries: Flow<List<MaterialDeliveryEntity>> = dao.observeDeliveries()
    suspend fun create(staffId: String, dateIso: String, material: String,
        quantity: Int, reason: String, deliveredBy: String
    ) {
        LocalDate.parse(dateIso)
        require(material.trim().length in 2..80) { "Material no válido." }
        require(quantity in 1..100) { "Cantidad de 1 a 100." }
        require(reason.trim().length in 3..120) { "Debes indicar el motivo." }
        require(deliveredBy.trim().length in 2..120) { "Indica quién entregó el material." }
        db.withWriteTransaction {
            val worker = staffDao.getById(staffId) ?: error("Trabajador no encontrado.")
            require(worker.isActive) { "Trabajador inactivo." }
            dao.upsert(MaterialDeliveryEntity(UUID.randomUUID().toString(),
                worker.id, worker.fullName, dateIso, material.trim(),
                quantity, reason.trim(), deliveredBy.trim(), "ENTREGADA",
                "", null, System.currentTimeMillis()))
        }
    }
    suspend fun close(id: String, action: String, note: String) {
        require(action == "DEVUELTA" || action == "ANULADA") { "Acción inválida." }
        require(note.trim().length in 5..300) { "Explica el motivo." }
        db.withWriteTransaction {
            val old = dao.get(id) ?: error("Entrega no encontrada.")
            check(old.status == "ENTREGADA") { "La entrega ya está cerrada." }
            dao.upsert(old.copy(status = action, closingNote = note.trim(),
                closedAtMillis = System.currentTimeMillis()))
        }
    }
}
