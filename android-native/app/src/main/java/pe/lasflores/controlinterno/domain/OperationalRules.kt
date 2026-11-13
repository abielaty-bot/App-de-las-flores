package pe.lasflores.controlinterno.domain

import java.time.LocalDate

/** Pure operational rules; no dependency on Android or storage. */
object OperationalRules {
    enum class Frequency { DAILY, WEEKLY, MONTHLY, SPECIFIC_DAYS, EVENT }
    enum class Result { UNREVIEWED, CONFORMING, NONCONFORMING, BLOCKED, NOT_APPLICABLE }
    enum class Correction { NONE, PENDING, REPORTED, VERIFIED, REOPENED }
    data class Definition(
        val id: String, val version: Int, val location: String,
        val frequency: Frequency, val days: Set<Int> = emptySet(),
        val event: String? = null, val critical: Boolean = false
    )
    data class Assignment(val workerId: String, val available: Boolean)
    data class Review(val initial: Result = Result.UNREVIEWED,
        val correction: Correction = Correction.NONE, val attributable: Boolean = false)
    fun instanceKey(task: Definition, date: LocalDate): String {
        val period = when (task.frequency) {
            Frequency.WEEKLY -> date.minusDays((date.dayOfWeek.value - 1).toLong()).toString()
            Frequency.MONTHLY -> date.toString().take(7)
            else -> date.toString()
        }
        return "${task.id}:${task.location}:$period"
    }
    fun active(task: Definition, date: LocalDate, openLocations: Set<String>, events: Set<String>): Boolean =
        task.location in openLocations && when(task.frequency) {
            Frequency.SPECIFIC_DAYS -> date.dayOfWeek.value in task.days
            Frequency.EVENT -> task.event != null && task.event in events
            else -> true
        }
    fun responsible(primary: Assignment?, substitutes: List<Assignment>): String? =
        primary?.takeIf { it.available }?.workerId ?: substitutes.firstOrNull { it.available }?.workerId
    fun correct(review: Review, next: Correction): Review {
        require(review.initial == Result.NONCONFORMING || review.initial == Result.BLOCKED)
        val allowed = when(review.correction) {
            Correction.NONE -> setOf(Correction.PENDING)
            Correction.PENDING -> setOf(Correction.REPORTED)
            Correction.REPORTED -> setOf(Correction.VERIFIED, Correction.REOPENED)
            Correction.VERIFIED -> setOf(Correction.REOPENED)
            Correction.REOPENED -> setOf(Correction.REPORTED)
        }
        require(next in allowed)
        return review.copy(correction = next)
    }
    fun initialCompliance(reviews: List<Review>): Double? {
        val evaluated = reviews.filter { it.initial == Result.CONFORMING || it.initial == Result.NONCONFORMING }
        return if(evaluated.isEmpty()) null else evaluated.count { it.initial == Result.CONFORMING }.toDouble() / evaluated.size
    }
    fun ready(controls: List<Pair<Definition, Review>>): Boolean =
        controls.isNotEmpty() && controls.filter { it.first.critical }.all { (_, review) ->
            review.initial == Result.CONFORMING || review.initial == Result.NOT_APPLICABLE ||
                review.correction == Correction.VERIFIED
        } && controls.any { it.second.initial == Result.CONFORMING || it.second.correction == Correction.VERIFIED }
}
