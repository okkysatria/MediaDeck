package com.mediadeck.app.util.cache

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class CacheEntryLock {
    private val locks = ConcurrentHashMap<String, LockWrapper>()

    private class LockWrapper {
        val mutex = Mutex()
        val waiters = AtomicInteger(0)
    }

    suspend fun <T> withLock(key: String, action: suspend () -> T): T {
        val wrapper = locks.compute(key) { _, existing ->
            (existing ?: LockWrapper()).apply { waiters.incrementAndGet() }
        }!!

        return try {
            wrapper.mutex.withLock {
                action()
            }
        } finally {
            locks.compute(key) { _, existing ->
                if (existing === wrapper) {
                    if (existing.waiters.decrementAndGet() == 0) {
                        null
                    } else {
                        existing
                    }
                } else {
                    existing
                }
            }
        }
    }
}
