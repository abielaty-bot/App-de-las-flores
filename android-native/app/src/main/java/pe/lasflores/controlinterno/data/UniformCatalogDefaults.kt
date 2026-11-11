package pe.lasflores.controlinterno.data

import pe.lasflores.controlinterno.domain.STAFF_ROLES
import pe.lasflores.controlinterno.domain.UniformRules

/**
 * Requisitos base equivalentes a los 9 de 4.0001. Usados solo en instalaciones nuevas
 * y nunca para sobrescribir personalizaciones existentes.
 */
fun defaultRequirements(): List<UniformRequirementEntity> {
    val items = listOf(
        Triple("unas", "Uñas cortas y limpias", "Presentación"),
        Triple("cabello", "Cabello corto o recogido", "Presentación"),
        Triple("camisa", "Uniforme limpio y planchado", "Uniforme"),
        Triple("toca", "Toca o redecilla", "Presentación"),
        Triple("mandil", "Mandil limpio", "Uniforme"),
        Triple("trapo", "Trapo limpio", "Implementos"),
        Triple("lapiceros", "Lapiceros (mínimo 3)", "Implementos"),
        Triple("sacacorchos", "Sacacorchos", "Implementos"),
        Triple("encendedor", "Encendedor", "Implementos")
    )
    return items.mapIndexed { index, (id, label, category) ->
        val roles = STAFF_ROLES.filter { role ->
            UniformRules.forRole(role).any { it.id == id }
        }
        UniformRequirementEntity(
            id = id, label = label, category = category,
            minQty = if (id == "lapiceros") 3 else 0,
            rolesCsv = "|" + roles.joinToString("|") + "|",
            isActive = true, sortOrder = index, updatedAtMillis = 0L
        )
    }
}
