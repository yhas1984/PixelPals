package com.pixelpals.app.core.rest

/** Local wall-clock minutes; re-evaluate after clock/time-zone changes and resume. */
data class PetRestSchedule(
    val enabled: Boolean = true,
    val followDoNotDisturb: Boolean = true,
    val startMinute: Int = 23 * 60,
    val endMinute: Int = 7 * 60,
) {
    init {
        require(startMinute in 0 until 1440 && endMinute in 0 until 1440)
    }

    fun shouldRest(localMinute: Int, doNotDisturb: Boolean): Boolean {
        require(localMinute in 0 until 1440)
        if (followDoNotDisturb && doNotDisturb) return true
        if (!enabled || startMinute == endMinute) return false
        return if (startMinute < endMinute) localMinute in startMinute until endMinute
        else localMinute >= startMinute || localMinute < endMinute
    }
}
