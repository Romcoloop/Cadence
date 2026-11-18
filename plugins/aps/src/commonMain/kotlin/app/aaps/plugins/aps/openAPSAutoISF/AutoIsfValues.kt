package app.aaps.plugins.aps.openAPSAutoISF

import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.DoubleKey
import app.aaps.core.keys.IntKey
import app.aaps.core.keys.interfaces.Preferences

/**
 * The 15 AutoISF and SMB delivery numbers as the loop uses them.
 *
 * With easy tuning ([BooleanKey.ApsAutoIsfEasyMode]) on they come from the table in [AutoIsfEasy]
 * and the two sliders. The settings are not read then, and nothing is written to them. With easy
 * tuning off the settings are used, so the switch off means "exactly as before".
 */
class AutoIsfValues(private val preferences: Preferences) {

    /** Easy tuning is on. In Simple mode it is always off, like the other AutoISF settings. */
    val easy: Boolean get() = preferences.get(BooleanKey.ApsAutoIsfEasyMode)

    /** Step of the slider "Rise", from [AutoIsfEasy.MIN_STEP] to [AutoIsfEasy.MAX_STEP]. */
    val riseStep: Int get() = preferences.get(IntKey.ApsAutoIsfEasyRise)

    /** Step of the slider "Plateau", from [AutoIsfEasy.MIN_STEP] to [AutoIsfEasy.MAX_STEP]. */
    val plateauStep: Int get() = preferences.get(IntKey.ApsAutoIsfEasyPlateau)

    /**
     * The AutoISF weights are used. Easy tuning needs them, so it does not wait for the switch
     * [BooleanKey.ApsUseAutoIsfWeights]: that switch is off by default, and with it off the
     * sliders would do nothing.
     */
    val weightsOn: Boolean get() = easy || preferences.get(BooleanKey.ApsUseAutoIsfWeights)

    /** The value of [key] the loop uses now. */
    fun get(key: DoubleKey): Double {
        val fromTable = if (easy) AutoIsfEasy.double(key, riseStep, plateauStep) else null
        return fromTable ?: preferences.get(key)
    }

    /** The value of [key] the loop uses now. */
    fun get(key: IntKey): Int {
        val fromTable = if (easy) AutoIsfEasy.int(key) else null
        return fromTable ?: preferences.get(key)
    }

    companion object {

        /** The 14 decimal numbers, in the order of the settings screens. */
        val DOUBLE_KEYS: List<DoubleKey> = listOf(
            DoubleKey.ApsAutoIsfMin,
            DoubleKey.ApsAutoIsfMax,
            DoubleKey.ApsAutoIsfBgAccelWeight,
            DoubleKey.ApsAutoIsfBgBrakeWeight,
            DoubleKey.ApsAutoIsfLowBgWeight,
            DoubleKey.ApsAutoIsfHighBgWeight,
            DoubleKey.ApsAutoIsfPpWeight,
            DoubleKey.ApsAutoIsfBgBlendWeight,
            DoubleKey.ApsAutoIsfDuraWeight,
            DoubleKey.ApsAutoIsfSmbDeliveryRatio,
            DoubleKey.ApsAutoIsfSmbDeliveryRatioMin,
            DoubleKey.ApsAutoIsfSmbDeliveryRatioMax,
            DoubleKey.ApsAutoIsfSmbDeliveryRatioBgRange,
            DoubleKey.ApsAutoIsfSmbMaxRangeExtension
        )

        /** The one whole number. */
        val INT_KEYS: List<IntKey> = listOf(IntKey.ApsAutoIsfIobThPercent)
    }
}
