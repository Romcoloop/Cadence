package app.aaps.plugins.aps.openAPSAutoISF.logAnalysis

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.plugins.aps.openAPSAutoISF.AutoIsfValues
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs

/** One AutoISF or SMB delivery number as the loop uses it now. */
data class AutoIsfValue(
    val title: TextRef,
    val value: Double,
    /** Set when the analyzer can suggest a new value for it. */
    val param: AutoIsfParam?,
    /** True for a whole number (the IOB threshold). */
    val whole: Boolean
)

/** Result of [AutoIsfLogAnalysis.analyzeRecent]. */
data class AutoIsfLogAnalysisResult(
    val filesRead: Int,
    /** How many hours before now were looked at. */
    val hours: Int,
    /** The numbers of the last hours, null when the log has too few loop runs. */
    val recent: AutoIsfRecentHours?,
    /** All 15 numbers the loop uses now. */
    val values: List<AutoIsfValue>,
    /** Facts, events and the suggestions of the last hours. */
    val report: AutoIsfLogReport,
    /**
     * The settings stored now are not the ones the last run used: a setting was changed after that
     * run, or the run is from an older build. A suggestion would start from the wrong number, so
     * none is made.
     */
    val valuesChanged: Boolean = false,
    /**
     * Easy tuning is on: the loop uses the values of the two sliders, so a change of a setting
     * would do nothing. No suggestion is made.
     */
    val easyTuning: Boolean = false
)

/** Result of [AutoIsfLogAnalysis.apply]. */
enum class AutoIsfApplyResult {

    Applied,

    /** The setting was changed after the analysis. Nothing was written, run the analysis again. */
    ChangedMeanwhile
}

/**
 * Runs the AutoISF log analysis and writes a suggested value when the user asks for it.
 *
 * It looks at the last hours before now (as many as the user chooses), whatever happened in them: the numbers of the
 * runs, the SMBs before a low, and a suggestion. A suggestion is made only when the last run used
 * the values that are stored now, and not while easy tuning is on; [apply] writes to the AutoISF
 * settings.
 *
 * Nothing is ever changed without the user: [apply] is only called from a button, after a
 * confirmation dialog.
 */
@Inject
class AutoIsfLogAnalysis(
    private val source: AutoIsfLogSource,
    private val preferences: Preferences,
    private val aapsLogger: AAPSLogger,
    val dateUtil: DateUtil,
    /** For the screen: BG in the units the user chose. */
    val profileUtil: ProfileUtil,
    /** For the screen: numbers in the user's locale. */
    val decimalFormatter: DecimalFormatter
) {

    /** The numbers the loop uses: the settings, or the easy tuning table. */
    private val settings = AutoIsfValues(preferences)

    /** False where there are no log files to read. Then the card must not be shown. */
    val available: Boolean get() = source.available

    /** Looks at the last [hours] hours of the log, as many as the user chose. */
    suspend fun analyzeRecent(hours: Int): AutoIsfLogAnalysisResult {
        val now = dateUtil.now()
        val since = now - (hours * 60 + LOG_MARGIN_MINUTES) * MINUTE_MILLIS
        val parser = AutoIsfLogParser()
        val files = source.readLines(since) { parser.accept(it) }
        val result = withContext(Dispatchers.Default) { recent(now, since, hours, parser.runs, files) }
        aapsLogger.debug(
            LTag.APS,
            "AutoISF log analysis: last $hours h, $files files, ${result.report.stats.runs} runs, " +
                "${result.report.suggestions.size} suggestions"
        )
        return result
    }

    private fun recent(now: Long, since: Long, hours: Int, runs: List<AutoIsfLogRun>, files: Int): AutoIsfLogAnalysisResult {
        val analyzer = AutoIsfLogAnalyzer()
        val from = now - hours * 60 * MINUTE_MILLIS
        val inPeriod = analyzer.oneRunPerBg(runs.filter { it.timestamp >= since }.sortedBy { it.timestamp })
        val recent = AutoIsfRecentAnalyzer().look(inPeriod, now, hours * 60)
            ?: return AutoIsfLogAnalysisResult(files, hours, null, values(), emptyReport(since))
        val inTime = inPeriod.filter { it.timestamp in from..now }
        val easy = settings.easy
        val current = currentValues()
        val valuesChanged = !usedValues(inTime.last(), current)
        val full = analyzer.analyze(inTime, from, current, MIN_HOURS_RECENT)
        val report = if (easy || valuesChanged || !recent.used) full.copy(suggestions = emptyList()) else full
        return AutoIsfLogAnalysisResult(files, hours, recent, values(), report, valuesChanged = valuesChanged, easyTuning = easy)
    }

    /** All 15 numbers the loop uses now, in the order of the settings screens. */
    fun values(): List<AutoIsfValue> {
        val doubles = AutoIsfValues.DOUBLE_KEYS.map { key ->
            AutoIsfValue(key.title, settings.get(key), AutoIsfParam.entries.firstOrNull { it.key == key }, whole = false)
        }
        val ints = AutoIsfValues.INT_KEYS.map { key ->
            AutoIsfValue(key.title, settings.get(key).toDouble(), null, whole = true)
        }
        return doubles + ints
    }

    private fun emptyReport(since: Long) = AutoIsfLogAnalyzer().analyze(emptyList(), since, emptyMap())

    /**
     * Writes the suggested value to the setting. Not while easy tuning is on: the loop would not
     * use the new value.
     */
    fun apply(suggestion: AutoIsfSuggestion): AutoIsfApplyResult {
        if (settings.easy) return AutoIsfApplyResult.ChangedMeanwhile
        val key = suggestion.param.key
        val now = preferences.get(key)
        if (abs(now - suggestion.current) > 1e-6) return AutoIsfApplyResult.ChangedMeanwhile
        val value = suggestion.param.clamp(suggestion.suggested)
        preferences.put(key, value)
        aapsLogger.info(LTag.APS, "AutoISF log analysis: ${key.key} changed by user from $now to $value (${suggestion.reason})")
        return AutoIsfApplyResult.Applied
    }

    private fun currentValues(): Map<AutoIsfParam, Double> =
        AutoIsfParam.entries.associateWith { settings.get(it.key) }

    companion object {

        private const val MINUTE_MILLIS = 60_000L

        /** Quick choices next to the slider. */
        val HOURS_CHOICES = listOf(3, 6)

        /** The most hours the user can choose: the slider goes from 1 to this. */
        const val MAX_HOURS = 24

        /** How many hours the analysis looks at when the user chooses nothing. */
        const val DEFAULT_HOURS = 3

        /** The log is read this many minutes further back, so the first run of the time looked at is not missed. */
        private const val LOG_MARGIN_MINUTES = 30

        /** Less than this much time of runs with the same settings, and only the facts are shown. */
        const val MIN_HOURS_RECENT = 2.0

        /**
         * True when [run] used the values in [current]: each value the log line of the run has is
         * the same as the stored one. A value the line does not have is not checked.
         */
        fun usedValues(run: AutoIsfLogRun, current: Map<AutoIsfParam, Double>): Boolean =
            current.all { (param, value) ->
                val logged = run.settings?.get(param.logName) ?: return@all true
                abs(logged - value) <= 1e-6
            }
    }
}
