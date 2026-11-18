package app.aaps.core.keys

import app.aaps.core.keys.interfaces.ElementVisibility

/**
 * Visibility rules shared by the AutoISF keys.
 */
internal object AutoIsfVisibility {

    /**
     * Shown only while easy tuning ([BooleanKey.ApsAutoIsfEasyMode]) is off. With easy tuning on,
     * the two sliders give the AutoISF and SMB delivery numbers and the full settings are not used.
     */
    val FULL_SETTINGS: ElementVisibility = ElementVisibility {
        !it.preferences.get(BooleanKey.ApsAutoIsfEasyMode)
    }
}
