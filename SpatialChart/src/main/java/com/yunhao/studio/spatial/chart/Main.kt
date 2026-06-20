package com.yunhao.studio.spatial.chart

import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yunhao.studio.spatial.chart.content.HomeVolume
import com.yunhao.studio.spatial.chart.content.SPREADSHEET_FILE_PICKER_WINDOW_ID
import com.yunhao.studio.spatial.chart.content.SPREADSHEET_RANGE_PICKER_WINDOW_ID
import com.yunhao.studio.spatial.chart.content.SpreadsheetFilePickerWindow
import com.yunhao.studio.spatial.chart.content.SpreadsheetRangePickerWindow
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.foundation.dsl.DefaultWindowContainer
import com.pico.spatial.ui.foundation.dsl.Form
import com.pico.spatial.ui.foundation.dsl.SpatialAppScope
import com.pico.spatial.ui.foundation.dsl.WindowContainer
import com.pico.spatial.ui.foundation.dsl.WindowContainerSize

fun mainApp(scope: SpatialAppScope) =
    with(scope) {
        DefaultWindowContainer {
            PicoTheme {
                HomeVolume(
                    Modifier.windowConstraints(
                        minWidth = 720.dp,
                        minHeight = 720.dp,
                        minDepth = 720.dp,
                        maxWidth = 1600.dp,
                        maxHeight = 1600.dp,
                        maxDepth = 1600.dp,
                    )
                )
            }
        }

        WindowContainer(
            id = SPREADSHEET_FILE_PICKER_WINDOW_ID,
            form = Form.Planar,
            defaultSize = WindowContainerSize(width = 560.dp, height = 420.dp),
            enableMaterialBackground = false,
        ) {
            PicoTheme {
                SpreadsheetFilePickerWindow(
                    Modifier.windowConstraints(width = 560.dp, height = 420.dp)
                )
            }
        }

        WindowContainer(
            id = SPREADSHEET_RANGE_PICKER_WINDOW_ID,
            form = Form.Planar,
            defaultSize = WindowContainerSize(width = 1280.dp, height = 860.dp),
            enableMaterialBackground = false,
        ) {
            PicoTheme {
                SpreadsheetRangePickerWindow(
                    Modifier.windowConstraints(width = 1280.dp, height = 860.dp)
                )
            }
        }

    }
