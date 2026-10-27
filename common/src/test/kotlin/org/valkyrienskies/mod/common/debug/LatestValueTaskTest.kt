package org.valkyrienskies.mod.common.debug

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.ArrayDeque
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class LatestValueTaskTest {
    @Test
    fun `a burst keeps the newest sample and queues one task`() {
        val tasks = ArrayDeque<Runnable>()
        val received = ArrayList<Int>()
        val updates = LatestValueTask<Int>({ tasks.addLast(it) }, { received.add(it) })
        repeat(60) { updates.offer(it) }
        assertEquals(1, tasks.size)
        tasks.removeFirst().run()
        assertEquals(listOf(59), received)
        assertTrue(tasks.isEmpty())
    }

    @Test
    fun `data received during a task starts one more task`() {
        val tasks = ArrayDeque<Runnable>()
        val received = ArrayList<Int>()
        lateinit var updates: LatestValueTask<Int>
        updates = LatestValueTask({ tasks.addLast(it) }) {
            received.add(it)
            if (it == 1) {
                updates.offer(2)
                updates.offer(3)
            }
        }
        updates.offer(1)
        tasks.removeFirst().run()
        assertEquals(1, tasks.size)
        tasks.removeFirst().run()
        assertEquals(listOf(1, 3), received)
        assertTrue(tasks.isEmpty())
    }

    @Test
    fun `clear removes data that has not been used`() {
        val tasks = ArrayDeque<Runnable>()
        val received = ArrayList<Int>()
        val updates = LatestValueTask<Int>({ tasks.addLast(it) }, { received.add(it) })
        updates.offer(1)
        updates.clear()
        tasks.removeFirst().run()
        assertTrue(received.isEmpty())
        updates.offer(2)
        tasks.removeFirst().run()
        assertEquals(listOf(2), received)
    }

    @Test
    fun `a pending task can use fresh data after clear`() {
        val tasks = ArrayDeque<Runnable>()
        val received = ArrayList<Int>()
        val updates = LatestValueTask<Int>({ tasks.addLast(it) }, { received.add(it) })
        updates.offer(1)
        updates.clear()
        updates.offer(2)
        assertEquals(1, tasks.size)
        tasks.removeFirst().run()
        assertEquals(listOf(2), received)
    }

    @Test
    fun `a failed consumer does not block fresh data`() {
        val tasks = ArrayDeque<Runnable>()
        val received = ArrayList<Int>()
        lateinit var updates: LatestValueTask<Int>
        updates = LatestValueTask({ tasks.addLast(it) }) {
            if (it == 1) {
                updates.offer(2)
                throw IllegalStateException("Test failure")
            }
            received.add(it)
        }
        updates.offer(1)
        assertThrows(IllegalStateException::class.java) { tasks.removeFirst().run() }
        tasks.removeFirst().run()
        assertEquals(listOf(2), received)
    }

    @Test
    fun `a slow worker skips old samples from another thread`() {
        val executor = Executors.newSingleThreadExecutor()
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val finished = CountDownLatch(1)
        val received = ArrayList<Int>()
        try {
            val updates = LatestValueTask<Int>({ executor.execute(it) }) {
                received.add(it)
                if (it == 1) {
                    started.countDown()
                    check(release.await(5, TimeUnit.SECONDS))
                } else finished.countDown()
            }
            updates.offer(1)
            assertTrue(started.await(5, TimeUnit.SECONDS))
            for (sample in 2..1000) updates.offer(sample)
            release.countDown()
            assertTrue(finished.await(5, TimeUnit.SECONDS))
            assertEquals(listOf(1, 1000), received)
        } finally {
            release.countDown()
            executor.shutdownNow()
        }
    }
}
