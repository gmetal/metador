package dev.gmetal.metador.response

import dev.gmetal.metador.Clock

internal class FridgeCache<K, V>(
    private val clock: Clock,
    private val defaultCacheEntryLifetime: Long,
    private val maxSize: Int
) {
    private val store: MutableMap<K, FridgeEntry<V>> = mutableMapOf()
    private var counter: Int = 0

    fun containsKey(key: K): Boolean = store.containsKey(key)

    @Synchronized
    operator fun get(key: K): V? =
        if (store.containsKey(key)) {
            if (store[key]?.isExpired(clock.timeMillis()) == true) {
                store.remove(key)
                null
            } else {
                store[key]?.value
            }
        } else {
            null
        }

    @Synchronized
    fun put(key: K, value: V) {
        put(key, value, defaultCacheEntryLifetime)
    }

    @Synchronized
    fun put(key: K, value: V, cacheEntryLifetime: Long) {
        val now = clock.timeMillis()
        if (isFull()) {
            cleanupFridge(now)
        }

        store[key] = FridgeEntry(now, cacheEntryLifetime, counter++, value)
    }

    private fun isFull() = store.size == maxSize

    private fun cleanupFridge(now: Long) {
        removeExpiredEntries(now)
        if (isFull()) {
            removeOldestEntry()
        }
    }

    private fun removeExpiredEntries(now: Long) {
        store.keys.filter { key -> store[key]?.isExpired(now) == true }
            .forEach { key -> store.remove(key) }
    }

    private fun removeOldestEntry() {
        if (store.isEmpty()) {
            return
        }

        val oldestEntry = store.entries.sortedByDescending { entry -> entry.value.cachedTimestamp }
            .groupBy { entry -> entry.value.cachedTimestamp }
            .flatMap { entry -> entry.value }
            .sortedBy { entry -> entry.value.counter }
            .first()

        store.remove(oldestEntry.key)
    }

    private data class FridgeEntry<V>(
        val cachedTimestamp: Long,
        val cacheFor: Long,
        val counter: Int,
        val value: V
    ) {
        fun isExpired(now: Long) = (now - cachedTimestamp) > cacheFor
    }
}
