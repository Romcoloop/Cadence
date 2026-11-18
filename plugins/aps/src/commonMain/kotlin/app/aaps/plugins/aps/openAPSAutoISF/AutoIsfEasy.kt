package app.aaps.plugins.aps.openAPSAutoISF

import app.aaps.core.keys.DoubleKey
import app.aaps.core.keys.IntKey

/**
 * Easy tuning: two sliders instead of the 15 AutoISF and SMB delivery numbers.
 *
 * Each slider has nine steps, from [MIN_STEP] (milder) to [MAX_STEP] (harder). Step 0 is the middle.
 * - "Rise" says how hard the loop acts while BG climbs.
 * - "Plateau" says how hard the loop acts while BG stays high.
 *
 * This table is the only place where a step becomes a number. The limits, the low side and the
 * SMB delivery values are fixed: no slider reaches them.
 *
 * The numbers are a careful start and not a tested dosing. Step +4 of "Rise" has the weights of
 * the author's own setup, but under lower limits (`autoISF_max` 1.4, `smb_max_range_extension` 2).
 */
object AutoIsfEasy {

    const val MIN_STEP = -4
    const val MAX_STEP = 4

    /** Slider "Rise": one value for each step, from -4 to +4. */
    val rise: Map<DoubleKey, List<Double>> = mapOf(
        DoubleKey.ApsAutoIsfBgAccelWeight to listOf(0.05, 0.08, 0.10, 0.12, 0.15, 0.19, 0.22, 0.26, 0.30),
        DoubleKey.ApsAutoIsfPpWeight to listOf(0.010, 0.015, 0.020, 0.025, 0.030, 0.035, 0.040, 0.050, 0.060),
        // The brake holds the dose back when the rise slows down. A harder step makes it only a
        // little weaker, and never zero.
        DoubleKey.ApsAutoIsfBgBrakeWeight to listOf(0.12, 0.12, 0.11, 0.11, 0.10, 0.10, 0.09, 0.09, 0.08)
    )

    /** Slider "Plateau": one value for each step, from -4 to +4. */
    val plateau: Map<DoubleKey, List<Double>> = mapOf(
        DoubleKey.ApsAutoIsfDuraWeight to listOf(0.00, 0.01, 0.02, 0.03, 0.05, 0.07, 0.10, 0.15, 0.20),
        DoubleKey.ApsAutoIsfHighBgWeight to listOf(0.20, 0.25, 0.30, 0.35, 0.40, 0.45, 0.50, 0.55, 0.60)
    )

    /** The same for every step. */
    val fixedDoubles: Map<DoubleKey, Double> = mapOf(
        DoubleKey.ApsAutoIsfMin to 0.7,
        DoubleKey.ApsAutoIsfMax to 1.4,
        DoubleKey.ApsAutoIsfLowBgWeight to 0.3,
        DoubleKey.ApsAutoIsfBgBlendWeight to 0.0,
        // Not used while the BG range below is above 0. It is here so that every key has a value.
        DoubleKey.ApsAutoIsfSmbDeliveryRatio to 0.5,
        // At target and below it the SMB gives this share of the insulin needed ...
        DoubleKey.ApsAutoIsfSmbDeliveryRatioMin to 0.5,
        // ... and this share from the BG range above target on.
        DoubleKey.ApsAutoIsfSmbDeliveryRatioMax to 0.8,
        // In mmol/l: the plugin takes a value below 10 as mmol/l. A short range gives the full
        // share early, when a meal starts.
        DoubleKey.ApsAutoIsfSmbDeliveryRatioBgRange to 0.5,
        DoubleKey.ApsAutoIsfSmbMaxRangeExtension to 2.0
    )

    /** The same for every step. */
    val fixedInts: Map<IntKey, Int> = mapOf(
        IntKey.ApsAutoIsfIobThPercent to 80
    )

    /** The value of [key] for the two slider steps, or null when the table does not have the key. */
    fun double(key: DoubleKey, riseStep: Int, plateauStep: Int): Double? =
        rise[key]?.get(index(riseStep))
            ?: plateau[key]?.get(index(plateauStep))
            ?: fixedDoubles[key]

    /** The value of [key], or null when the table does not have the key. */
    fun int(key: IntKey): Int? = fixedInts[key]

    /** A step as the log shows it: "+2", "0", "-3". */
    fun stepText(step: Int): String {
        val inRange = step.coerceIn(MIN_STEP, MAX_STEP)
        return if (inRange > 0) "+$inRange" else inRange.toString()
    }

    /** A step outside -4..+4 counts as the nearest end. */
    private fun index(step: Int): Int = step.coerceIn(MIN_STEP, MAX_STEP) - MIN_STEP
}
