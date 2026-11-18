package app.aaps.plugins.aps.openAPSAutoISF

import app.aaps.core.keys.DoubleKey
import app.aaps.core.keys.IntKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AutoIsfEasyTest {

    private val steps = AutoIsfEasy.MIN_STEP..AutoIsfEasy.MAX_STEP

    @Test
    fun everyNumberOfTheLoopHasAValueAtEveryStep() {
        for (rise in steps) for (plateau in steps) {
            AutoIsfValues.DOUBLE_KEYS.forEach { key ->
                assertNotNull(AutoIsfEasy.double(key, rise, plateau), "${key.key} at rise $rise, plateau $plateau")
            }
        }
        AutoIsfValues.INT_KEYS.forEach { key -> assertNotNull(AutoIsfEasy.int(key), key.key) }
    }

    @Test
    fun theTableHasNoOtherKeysAndNoKeyTwice() {
        val tableKeys = AutoIsfEasy.rise.keys.toList() + AutoIsfEasy.plateau.keys.toList() + AutoIsfEasy.fixedDoubles.keys.toList()
        assertEquals(tableKeys.size, tableKeys.toSet().size, "a key is in two parts of the table")
        assertEquals(AutoIsfValues.DOUBLE_KEYS.toSet(), tableKeys.toSet())
        assertEquals(AutoIsfValues.INT_KEYS.toSet(), AutoIsfEasy.fixedInts.keys)
    }

    @Test
    fun aKeyThatIsNotAnAutoIsfNumberIsNotInTheTable() {
        assertNull(AutoIsfEasy.double(DoubleKey.ApsMaxBasal, 0, 0))
        assertNull(AutoIsfEasy.int(IntKey.ApsMaxSmbFrequency))
    }

    @Test
    fun everySliderHasNineSteps() {
        (AutoIsfEasy.rise.values + AutoIsfEasy.plateau.values).forEach { assertEquals(9, it.size) }
    }

    @Test
    fun everyValueIsInsideTheLimitsOfItsSetting() {
        for (rise in steps) for (plateau in steps) {
            AutoIsfValues.DOUBLE_KEYS.forEach { key ->
                val value = assertNotNull(AutoIsfEasy.double(key, rise, plateau))
                assertTrue(value >= key.min && value <= key.max, "${key.key} = $value is outside ${key.min}..${key.max}")
            }
        }
        AutoIsfValues.INT_KEYS.forEach { key ->
            val value = assertNotNull(AutoIsfEasy.int(key))
            assertTrue(value >= key.min && value <= key.max, "${key.key} = $value is outside ${key.min}..${key.max}")
        }
    }

    @Test
    fun aHarderStepNeverGivesLessAndTheBrakeNeverMore() {
        fun rising(values: List<Double>) = values.zipWithNext().all { (a, b) -> b >= a }
        fun falling(values: List<Double>) = values.zipWithNext().all { (a, b) -> b <= a }
        assertTrue(rising(AutoIsfEasy.rise.getValue(DoubleKey.ApsAutoIsfBgAccelWeight)))
        assertTrue(rising(AutoIsfEasy.rise.getValue(DoubleKey.ApsAutoIsfPpWeight)))
        assertTrue(falling(AutoIsfEasy.rise.getValue(DoubleKey.ApsAutoIsfBgBrakeWeight)))
        assertTrue(rising(AutoIsfEasy.plateau.getValue(DoubleKey.ApsAutoIsfHighBgWeight)))
    }

    @Test
    fun theBrakeIsNeverSwitchedOff() {
        assertTrue(AutoIsfEasy.rise.getValue(DoubleKey.ApsAutoIsfBgBrakeWeight).all { it >= 0.08 })
    }

    @Test
    fun eachSliderMovesOnlyItsOwnValues() {
        // "Rise" does not touch the plateau values
        assertEquals(
            AutoIsfEasy.double(DoubleKey.ApsAutoIsfHighBgWeight, -4, 1),
            AutoIsfEasy.double(DoubleKey.ApsAutoIsfHighBgWeight, 4, 1)
        )
        // "Plateau" does not touch the rise values
        assertEquals(
            AutoIsfEasy.double(DoubleKey.ApsAutoIsfBgAccelWeight, 2, -4),
            AutoIsfEasy.double(DoubleKey.ApsAutoIsfBgAccelWeight, 2, 4)
        )
        // no slider touches a limit or the SMB delivery values
        AutoIsfEasy.fixedDoubles.keys.forEach { key ->
            assertEquals(AutoIsfEasy.double(key, -4, -4), AutoIsfEasy.double(key, 4, 4), key.key)
        }
    }

    @Test
    fun aStepOutsideTheSliderCountsAsItsEnd() {
        assertEquals(AutoIsfEasy.double(DoubleKey.ApsAutoIsfBgAccelWeight, 4, 0), AutoIsfEasy.double(DoubleKey.ApsAutoIsfBgAccelWeight, 99, 0))
        assertEquals(AutoIsfEasy.double(DoubleKey.ApsAutoIsfHighBgWeight, 0, -4), AutoIsfEasy.double(DoubleKey.ApsAutoIsfHighBgWeight, 0, -99))
    }

    @Test
    fun theAgreedNumbers() {
        // the ends and the middle of both sliders
        assertEquals(0.05, AutoIsfEasy.double(DoubleKey.ApsAutoIsfBgAccelWeight, -4, 0))
        assertEquals(0.15, AutoIsfEasy.double(DoubleKey.ApsAutoIsfBgAccelWeight, 0, 0))
        assertEquals(0.30, AutoIsfEasy.double(DoubleKey.ApsAutoIsfBgAccelWeight, 4, 0))
        assertEquals(0.030, AutoIsfEasy.double(DoubleKey.ApsAutoIsfPpWeight, 0, 0))
        assertEquals(0.060, AutoIsfEasy.double(DoubleKey.ApsAutoIsfPpWeight, 4, 0))
        assertEquals(0.12, AutoIsfEasy.double(DoubleKey.ApsAutoIsfBgBrakeWeight, -4, 0))
        assertEquals(0.08, AutoIsfEasy.double(DoubleKey.ApsAutoIsfBgBrakeWeight, 4, 0))
        assertEquals(0.20, AutoIsfEasy.double(DoubleKey.ApsAutoIsfHighBgWeight, 0, -4))
        assertEquals(0.40, AutoIsfEasy.double(DoubleKey.ApsAutoIsfHighBgWeight, 0, 0))
        assertEquals(0.60, AutoIsfEasy.double(DoubleKey.ApsAutoIsfHighBgWeight, 0, 4))
        // the fixed values
        assertEquals(0.03, AutoIsfEasy.double(DoubleKey.ApsAutoIsfDuraWeight, 0, -4))
        assertEquals(0.03, AutoIsfEasy.double(DoubleKey.ApsAutoIsfDuraWeight, 0, 4))
        assertEquals(1.4, AutoIsfEasy.double(DoubleKey.ApsAutoIsfMax, 0, 0))
        assertEquals(0.7, AutoIsfEasy.double(DoubleKey.ApsAutoIsfMin, 0, 0))
        assertEquals(0.3, AutoIsfEasy.double(DoubleKey.ApsAutoIsfLowBgWeight, 0, 0))
        assertEquals(0.0, AutoIsfEasy.double(DoubleKey.ApsAutoIsfBgBlendWeight, 0, 0))
        assertEquals(0.5, AutoIsfEasy.double(DoubleKey.ApsAutoIsfSmbDeliveryRatioMin, 0, 0))
        assertEquals(0.8, AutoIsfEasy.double(DoubleKey.ApsAutoIsfSmbDeliveryRatioMax, 0, 0))
        assertEquals(0.5, AutoIsfEasy.double(DoubleKey.ApsAutoIsfSmbDeliveryRatioBgRange, 0, 0))
        assertEquals(2.0, AutoIsfEasy.double(DoubleKey.ApsAutoIsfSmbMaxRangeExtension, 0, 0))
        assertEquals(80, AutoIsfEasy.int(IntKey.ApsAutoIsfIobThPercent))
    }

    @Test
    fun theStepIsShownWithItsSign() {
        assertEquals("+2", AutoIsfEasy.stepText(2))
        assertEquals("0", AutoIsfEasy.stepText(0))
        assertEquals("-3", AutoIsfEasy.stepText(-3))
        assertEquals("+4", AutoIsfEasy.stepText(9))
    }
}
