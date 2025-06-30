package dev.gmetal.metador

/**
 * An entity that can return a Unix timestamp of the current date
 */
fun interface Clock {
    /**
     * Returns a Unix timestamp
     *
     * @return the current Unix timestamp as a [Long]
     */
    fun timeMillis(): Long
}
