package app.aaps.plugins.aps.openAPSAutoISF.logAnalysis

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AutoIsfLogAnalysisTest {

    private val current = mapOf(
        AutoIsfParam.AutoIsfMax to 1.6,
        AutoIsfParam.BgAccelWeight to 0.3,
        AutoIsfParam.HighBgWeight to 0.6,
        AutoIsfParam.SmbRatioMax to 0.8,
        AutoIsfParam.SmbRangeExtension to 5.0
    )

    private val sameSettings = mapOf(
        "autoISF_max" to 1.6, "bgAccel_ISF_weight" to 0.3, "higher_ISFrange_weight" to 0.6,
        "smb_delivery_ratio_max" to 0.8, "smb_max_range_extension" to 5.0
    )

    private fun run(settings: Map<String, Double>?) = AutoIsfLogRun(
        timestamp = 1_800_000_000_000L,
        bgTimestamp = null,
        bg = 120.0,
        eventualBg = null,
        targetBg = null,
        insulinReq = null,
        smbUnits = 0.0,
        iob = null,
        finalIsfFactor = null,
        acceIsf = null,
        bgIsf = null,
        ppIsf = null,
        duraIsf = null,
        limitedByAutoIsfMax = false,
        evenTargetSmb = false,
        smbRatioAtMax = false,
        smbStoppedByMinGuard = false,
        maxBolus = null,
        settings = settings
    )

    @Test
    fun theRunUsedTheStoredValues() {
        assertTrue(AutoIsfLogAnalysis.usedValues(run(sameSettings), current))
    }

    @Test
    fun aValueThatIsNotTheStoredOneIsSeen() {
        // the run used 1.4, for example with the values of a meal slot of an older build
        assertFalse(AutoIsfLogAnalysis.usedValues(run(sameSettings + ("autoISF_max" to 1.4)), current))
        assertFalse(AutoIsfLogAnalysis.usedValues(run(sameSettings + ("smb_max_range_extension" to 3.0)), current))
    }

    @Test
    fun aSettingTheAnalyzerDoesNotChangeIsNotCompared() {
        assertTrue(AutoIsfLogAnalysis.usedValues(run(sameSettings + ("dura_ISF_weight" to 0.5)), current))
    }

    @Test
    fun aValueTheLogLineDoesNotHaveIsNotCompared() {
        assertTrue(AutoIsfLogAnalysis.usedValues(run(sameSettings - "autoISF_max"), current))
        assertTrue(AutoIsfLogAnalysis.usedValues(run(null), current))
    }
}
