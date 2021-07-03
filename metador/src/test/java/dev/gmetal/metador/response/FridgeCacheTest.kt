package dev.gmetal.metador.response

import dev.gmetal.metador.Clock
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk

internal class FridgeCacheTest : BehaviorSpec({
    lateinit var mockClock: Clock

    lateinit var fridgeCacheInTest: FridgeCache<String, Map<String, String>>
    var clockCounter: Long = 0

    beforeContainer {
        mockClock = mockk()
        fridgeCacheInTest = FridgeCache(mockClock, 1000L, 2)
        every { mockClock.timeMillis() } answers {
            clockCounter += 2
            clockCounter
        }
    }

    Given("a FridgeCache") {
        val fakeKey = "fakeKey"
        val fakeData = mapOf(fakeKey to "fakeValue")

        When("we add an entry in the cache") {
            fridgeCacheInTest.put(fakeKey, fakeData)
            val returnedValue = fridgeCacheInTest[fakeKey]
            Then("it can be retrieved from it") {

                fridgeCacheInTest.containsKey(fakeKey) shouldBe true
                returnedValue shouldBe fakeData
            }
        }

        When("an entry is not added in the cache") {
            val returnedValue = fridgeCacheInTest[fakeKey]
            Then("it cannot be retrieved from it") {
                fridgeCacheInTest.containsKey(fakeKey) shouldBe false
                returnedValue shouldBe null
            }
        }

        When("the cache is full and a new entry is added") {
            val fakeKey2 = "fakeKey2"
            val fakeKey3 = "fakeKey3"
            fridgeCacheInTest.put(fakeKey, fakeData)
            fridgeCacheInTest.put(fakeKey2, fakeData)
            val fakeKeyContained = fridgeCacheInTest.containsKey(fakeKey)
            val fakeKeyValueReturned = fridgeCacheInTest[fakeKey]
            fridgeCacheInTest.put(fakeKey3, fakeData)
            val returnedValue = fridgeCacheInTest[fakeKey]
            val returnedValue2 = fridgeCacheInTest[fakeKey2]
            val returnedValue3 = fridgeCacheInTest[fakeKey3]

            Then("the oldest entry is replaced with the new one") {
                fakeKeyContained shouldBe true
                fakeKeyValueReturned shouldBe fakeData
                fridgeCacheInTest.containsKey(fakeKey) shouldBe false
                fridgeCacheInTest.containsKey(fakeKey2) shouldBe true
                fridgeCacheInTest.containsKey(fakeKey3) shouldBe true
                returnedValue shouldBe null
                returnedValue2 shouldBe fakeData
                returnedValue3 shouldBe fakeData
            }
        }

        When("an item is given a non-default lifetime") {
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

            Then("the cache honours the non-default lifetime") {
                fakeKey2Contained shouldBe true
                fakeKey2ValueReturned shouldBe fakeData
                fridgeCacheInTest.containsKey(fakeKey) shouldBe true
                fridgeCacheInTest.containsKey(fakeKey2) shouldBe false
                fridgeCacheInTest.containsKey(fakeKey3) shouldBe true
                returnedValue shouldBe fakeData
                returnedValue2 shouldBe null
                returnedValue3 shouldBe fakeData
            }
        }

        When("a stale entry is requested from the cache") {
            fridgeCacheInTest.put(fakeKey, fakeData, 1)
            val returnedValue = fridgeCacheInTest[fakeKey]

            Then("it is removed from the cached and not returned") {
                fridgeCacheInTest.containsKey(fakeKey) shouldBe false
                returnedValue shouldBe null
            }
        }

        When("two or more entries are cached at the same instant") {
            every { mockClock.timeMillis() } returns 1
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

            Then("the order of inserts determines which entry will be removed first") {
                fakeKeyContained shouldBe true
                fakeKeyValueReturned shouldBe fakeData
                fridgeCacheInTest.containsKey(fakeKey3) shouldBe false
                fridgeCacheInTest.containsKey(fakeKey2) shouldBe true
                fridgeCacheInTest.containsKey(fakeKey) shouldBe true
                returnedValue shouldBe fakeData
                returnedValue3 shouldBe null
                returnedValue2 shouldBe fakeData
            }
        }
    }
})
