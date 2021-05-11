package dev.gmetal.metador.response

import dev.gmetal.metador.Clock
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import org.hamcrest.CoreMatchers.nullValue
import org.hamcrest.MatcherAssert.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.hamcrest.CoreMatchers.`is` as _is

internal class FridgeCacheTest {
    @MockK
    lateinit var mockClock: Clock

    lateinit var fridgeCacheInTest: FridgeCache<String, Map<String, String>>
    private var clockCounter: Long = 0

    @BeforeEach
    fun setup() {
        MockKAnnotations.init(this)
        fridgeCacheInTest = FridgeCache(mockClock, 1000L, 2)
        every { mockClock.timeMillis() } answers {
            clockCounter += 2
            clockCounter
        }
    }

    @Test
    fun `entries added in the fridge are contained in it and can be retrieved from it`() {
        val fakeKey = "fakeKey"
        val fakeData = mapOf(fakeKey to "fakeValue")

        fridgeCacheInTest.put(fakeKey, fakeData)
        val returnedValue = fridgeCacheInTest[fakeKey]

        assertThat(fridgeCacheInTest.containsKey(fakeKey), _is(true))
        assertThat(returnedValue, _is(fakeData))
    }

    @Test
    fun `entries not added in the fridge are not contained in it and cannot be retrieved from  it`() {
        val fakeKey = "fakeKey"

        val returnedValue = fridgeCacheInTest[fakeKey]

        assertThat(fridgeCacheInTest.containsKey(fakeKey), _is(false))
        assertThat(returnedValue, _is(nullValue()))
    }

    @Test
    fun `adding more items in the fridge than it's max size replaces the oldest entries with the newest ones`() {
        val fakeKey = "fakeKey"
        val fakeKey2 = "fakeKey2"
        val fakeKey3 = "fakeKey3"
        val fakeData = mapOf(fakeKey to "fakeValue")

        fridgeCacheInTest.put(fakeKey, fakeData)
        fridgeCacheInTest.put(fakeKey2, fakeData)
        val fakeKeyContained = fridgeCacheInTest.containsKey(fakeKey)
        val fakeKeyValueReturned = fridgeCacheInTest[fakeKey]
        fridgeCacheInTest.put(fakeKey3, fakeData)
        val returnedValue = fridgeCacheInTest[fakeKey]
        val returnedValue2 = fridgeCacheInTest[fakeKey2]
        val returnedValue3 = fridgeCacheInTest[fakeKey3]

        assertAll(
            { assertThat(fakeKeyContained, _is(true)) },
            { assertThat(fakeKeyValueReturned, _is(fakeData)) },
            { assertThat(fridgeCacheInTest.containsKey(fakeKey), _is(false)) },
            { assertThat(fridgeCacheInTest.containsKey(fakeKey2), _is(true)) },
            { assertThat(fridgeCacheInTest.containsKey(fakeKey3), _is(true)) },
            { assertThat(returnedValue, _is(nullValue())) },
            { assertThat(returnedValue2, _is(fakeData)) },
            { assertThat(returnedValue3, _is(fakeData)) }
        )
    }

    @Test
    fun `the lifetime of entries can also be specified separately for each entry and expired entries are removed to make room`() {
        val fakeKey = "fakeKey"
        val fakeKey2 = "fakeKey2"
        val fakeKey3 = "fakeKey3"
        val fakeData = mapOf(fakeKey to "fakeValue")

        fridgeCacheInTest.put(fakeKey, fakeData)
        fridgeCacheInTest.put(fakeKey2, fakeData, 2)
        val fakeKey2Contained = fridgeCacheInTest.containsKey(fakeKey2)
        val fakeKey2ValueReturned = fridgeCacheInTest[fakeKey2]
        fridgeCacheInTest.put(fakeKey3, fakeData)
        val returnedValue = fridgeCacheInTest[fakeKey]
        val returnedValue2 = fridgeCacheInTest[fakeKey2]
        val returnedValue3 = fridgeCacheInTest[fakeKey3]

        assertAll(
            { assertThat(fakeKey2Contained, _is(true)) },
            { assertThat(fakeKey2ValueReturned, _is(fakeData)) },
            { assertThat(fridgeCacheInTest.containsKey(fakeKey), _is(true)) },
            { assertThat(fridgeCacheInTest.containsKey(fakeKey2), _is(false)) },
            { assertThat(fridgeCacheInTest.containsKey(fakeKey3), _is(true)) },
            { assertThat(returnedValue, _is(fakeData)) },
            { assertThat(returnedValue2, _is(nullValue())) },
            { assertThat(returnedValue3, _is(fakeData)) }
        )
    }

    @Test
    fun `a stale entry may also be removed during a get, and not returned`() {
        val fakeKey = "fakeKey"
        val fakeData = mapOf(fakeKey to "fakeValue")

        fridgeCacheInTest.put(fakeKey, fakeData, 1)
        val returnedValue = fridgeCacheInTest[fakeKey]

        assertThat(fridgeCacheInTest.containsKey(fakeKey), _is(false))
        assertThat(returnedValue, _is(nullValue()))
    }

    @Test
    fun `for entries inserted at the same instant, the order of insertion will determine which entry will be first removed`() {
        every { mockClock.timeMillis() } returns 1
        val fakeKey = "fakeKey"
        val fakeKey2 = "fakeKey2"
        val fakeKey3 = "fakeKey3"
        val fakeData = mapOf(fakeKey to "fakeValue")

        fridgeCacheInTest.put(fakeKey3, fakeData)
        fridgeCacheInTest.put(fakeKey2, fakeData)
        val fakeKeyContained = fridgeCacheInTest.containsKey(fakeKey3)
        val fakeKeyValueReturned = fridgeCacheInTest[fakeKey3]
        fridgeCacheInTest.put(fakeKey, fakeData)
        val returnedValue3 = fridgeCacheInTest[fakeKey3]
        val returnedValue2 = fridgeCacheInTest[fakeKey2]
        val returnedValue = fridgeCacheInTest[fakeKey]

        assertAll(
            { assertThat(fakeKeyContained, _is(true)) },
            { assertThat(fakeKeyValueReturned, _is(fakeData)) },
            { assertThat(fridgeCacheInTest.containsKey(fakeKey3), _is(false)) },
            { assertThat(fridgeCacheInTest.containsKey(fakeKey2), _is(true)) },
            { assertThat(fridgeCacheInTest.containsKey(fakeKey), _is(true)) },
            { assertThat(returnedValue, _is(fakeData)) },
            { assertThat(returnedValue3, _is(nullValue())) },
            { assertThat(returnedValue2, _is(fakeData)) }
        )
    }
}
