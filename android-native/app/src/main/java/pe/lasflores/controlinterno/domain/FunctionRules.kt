package pe.lasflores.controlinterno.domain

import pe.lasflores.controlinterno.data.FunctionCheckEntity
import pe.lasflores.controlinterno.data.FunctionEvidenceEntity
import pe.lasflores.controlinterno.data.FunctionTaskEntity

object FunctionRules {
    const val PENDING = "PENDING"
    const val OK = "OK"
    const val BAD = "BAD"
    val RESULTS = setOf(PENDING, OK, BAD)
    val PERIODS = setOf("DIARIA", "MENSUAL")
    val AREAS = listOf("Oficina I", "Oficina II", "Funciones generales")

    data class Summary(
        val assigned: Int, val evaluated: Int, val ok: Int, val bad: Int,
        val pending: Int, val corrected: Int, val missingEvidence: Int,
        val sentEvidence: Int
    ) {
        val rate: Int? get() = if (evaluated == 0) null else (ok * 100.0 / evaluated).toInt()
    }

    /** Los anulados y los pendientes no alteran el porcentaje de cumplimiento. */
    fun summarize(
        entries: Collection<FunctionCheckEntity>,
        evidence: Collection<FunctionEvidenceEntity> = emptyList()
    ): Summary {
        val active = entries.filter { it.voidedAtMillis == null }
        val ok = active.count { it.result == OK }
        val bad = active.count { it.result == BAD }
        val byCheck = evidence.groupBy { it.checkId }
        return Summary(
            assigned = active.size, evaluated = ok + bad, ok = ok, bad = bad,
            pending = active.count { it.result == PENDING },
            corrected = active.count { it.result == BAD && it.correctedAtMillis != null },
            missingEvidence = active.count { byCheck[it.id].isNullOrEmpty() ||
                byCheck[it.id]!!.none { e -> e.groupSentAtMillis != null } },
            sentEvidence = active.count { byCheck[it.id]?.any { e -> e.groupSentAtMillis != null } == true }
        )
    }

    fun resultName(c: FunctionCheckEntity): String = when {
        c.voidedAtMillis != null -> "Anulada"
        c.result == OK -> "Cumplió"
        c.result == BAD && c.correctedAtMillis != null -> "No cumplió · Corregido"
        c.result == BAD -> "No cumplió"
        else -> "Pendiente de revisar"
    }

    /** Catálogo base de la web; el usuario puede crear tareas mensuales y editar estas. */
    fun defaultTasks(): List<FunctionTaskEntity> {
        val groups = listOf(
            "Oficina I" to listOf("Caja", "Estrado", "Entrada", "Ventana", "C", "Vasos"),
            "Oficina II" to listOf("Caja", "Entrada", "O1", "O2", "T1", "T2"),
            "Funciones generales" to listOf(
                "Manteles", "Heladería", "Desperdicios", "Jabas y utensilios",
                "Exhibidor de vinos"
            )
        )
        return groups.flatMap { (area, labels) ->
            labels.map { label ->
                FunctionTaskEntity(
                    id = ("base-" + area + "-" + label)
                        .lowercase().replace(Regex("[^a-z0-9]"), "-"),
                    area = area, label = label, periodicity = "DIARIA",
                    isActive = true, sortOrder = 0, updatedAtMillis = 0
                )
            }
        }.mapIndexed { n, t -> t.copy(sortOrder = n) }
    }
}
