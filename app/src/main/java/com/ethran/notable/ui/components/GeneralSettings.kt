package com.ethran.notable.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.ethran.notable.R
import com.ethran.notable.data.datastore.AppSettings
import com.ethran.notable.navigation.LinkFormat


@Composable
fun GeneralSettings(
    settings: AppSettings, onSettingsChange: (AppSettings) -> Unit
) {
    Column {
        SelectorRow(
            label = stringResource(R.string.default_page_background_template), options = listOf(
                "blank" to stringResource(R.string.blank_page),
                "dotted" to stringResource(R.string.dot_grid),
                "lined" to stringResource(R.string.lines),
                "squared" to stringResource(R.string.small_squares_grid),
                "hexed" to stringResource(R.string.hexagon_grid),
                "meeting" to stringResource(R.string.meeting_notes),
                "call" to stringResource(R.string.call_log),
                "cornell" to stringResource(R.string.cornell_notes),
                "todo" to stringResource(R.string.todo_list),
            ), value = settings.defaultNativeTemplate, onValueChange = {
                onSettingsChange(settings.copy(defaultNativeTemplate = it))
            })
        SelectorRow(
            label = stringResource(R.string.toolbar_position), options = listOf(
                AppSettings.Position.Left to stringResource(R.string.toolbar_position_left),
                AppSettings.Position.Top to stringResource(R.string.toolbar_position_top),
                AppSettings.Position.Bottom to stringResource(
                    R.string.toolbar_position_bottom
                )
            ), value = settings.toolbarPosition, onValueChange = { newPosition ->
                onSettingsChange(settings.copy(toolbarPosition = newPosition))
            })
        SelectorRow(
            label = stringResource(R.string.link_format), options = listOf(
                LinkFormat.Plain to "Plain",
                LinkFormat.Markdown to "Markdown",
                LinkFormat.Org to "Org",
            ), value = settings.linkFormat, onValueChange = {
                onSettingsChange(settings.copy(linkFormat = it))
            })

        SettingToggleRow(
            label = stringResource(R.string.enable_scribble_to_erase),
            value = settings.scribbleToEraseEnabled,
            onToggle = { isChecked ->
                onSettingsChange(settings.copy(scribbleToEraseEnabled = isChecked))
            })

        SettingToggleRow(
            label = "Show tips as I go",
            value = settings.showTips,
            onToggle = { isChecked ->
                onSettingsChange(settings.copy(showTips = isChecked))
            })

        SettingToggleRow(
            label = "Hold the pen still at the end of a line to straighten it",
            value = settings.smartStraightLine,
            onToggle = { isChecked ->
                onSettingsChange(settings.copy(smartStraightLine = isChecked))
            })

        SettingToggleRow(
            label = "Hold two fingers down to erase",
            value = settings.twoFingerHoldErase,
            onToggle = { isChecked ->
                onSettingsChange(settings.copy(twoFingerHoldErase = isChecked))
            })

        SettingToggleRow(
            label = "Circle handwriting to lasso it",
            value = settings.circleToSelect,
            onToggle = { isChecked ->
                onSettingsChange(settings.copy(circleToSelect = isChecked))
            })

        SettingToggleRow(
            label = stringResource(R.string.enable_smooth_scrolling),
            value = settings.smoothScroll,
            onToggle = { isChecked ->
                onSettingsChange(settings.copy(smoothScroll = isChecked))
            })

        SettingToggleRow(
            label = stringResource(R.string.continuous_zoom),
            value = settings.continuousZoom,
            onToggle = { isChecked ->
                onSettingsChange(settings.copy(continuousZoom = isChecked))
            })
        SettingToggleRow(
            label = stringResource(R.string.continuous_stroke_slider),
            value = settings.continuousStrokeSlider,
            onToggle = { isChecked ->
                onSettingsChange(settings.copy(continuousStrokeSlider = isChecked))
            })

        SettingToggleRow(
            label = stringResource(R.string.paginate_pdf),
            value = settings.paginatePdf,
            onToggle = { isChecked ->
                onSettingsChange(settings.copy(paginatePdf = isChecked))
            })

        SettingToggleRow(
            label = stringResource(R.string.preview_pdf_pagination),
            value = settings.visualizePdfPagination,
            onToggle = { isChecked ->
                onSettingsChange(settings.copy(visualizePdfPagination = isChecked))
            })

        // Zoom Level (UI Scale)
        SettingSliderRow(
            label = stringResource(R.string.uiScale),
            value = settings.uiScale,
            valueRange = 0.5f..1.5f,
            steps = 9,
            onValueChangeFinished = { newValue ->
                onSettingsChange(settings.copy(uiScale = newValue))
            }
        )
    }
}
