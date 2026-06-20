package com.yunhao.studio.spatial.chart.content

import kotlin.math.cos
import kotlin.math.sin

data class SpreadsheetDocument(
    val displayName: String,
    val sourceLabel: String,
    val sheets: List<SpreadsheetSheet>
) {
    companion object {
        fun demo(): SpreadsheetDocument {
            val size = 8
            val rows = List(size) { row ->
                List(size) { column ->
                    val x = row.toFloat() / (size - 1).coerceAtLeast(1)
                    val y = column.toFloat() / (size - 1).coerceAtLeast(1)
                    val z = (sin(x * Math.PI).toFloat() * 0.65f) + (cos(y * Math.PI).toFloat() * 0.35f)
                    String.format("%.3f", z)
                }
            }

            return SpreadsheetDocument(
                displayName = "Demo Surface",
                sourceLabel = "Generated demo dataset",
                sheets = listOf(SpreadsheetSheet(name = "Wave", rows = rows))
            )
        }
    }
}

data class SpreadsheetSheet(
    val name: String,
    val rows: List<List<String>>
) {
    val rowCount: Int = rows.size
    val columnCount: Int = rows.maxOfOrNull { it.size } ?: 0
}

data class DataPoint3D(
    val x: Float,
    val y: Float,
    val z: Float,
    val xRatio: Float = 0f,
    val yRatio: Float = 0f,
    val colorMetric: Float? = null,
    val colorRatio: Float? = null,
)

enum class AxisSourceType(
    val label: String,
    val tooltip: String,
) {
    NONE(
        label = "Choose source",
        tooltip = "Pick where this axis should get its values from."
    ),
    RANGE(
        label = "Rectangle range",
        tooltip = "Use a rectangular cell range from any sheet, matching the dimensions of the visualized values."
    ),
    ROW_INDEX(
        label = "Data point row index",
        tooltip = "Use each data point's absolute row number in the Z range sheet."
    ),
    COLUMN_INDEX(
        label = "Data point column index",
        tooltip = "Use each data point's absolute column number in the Z range sheet."
    ),
}

data class AxisSourceSelection(
    val type: AxisSourceType = AxisSourceType.NONE,
    val rangeExpression: String = "",
)

data class DataSelectionSettings(
    val zRangeExpression: String = "",
    val xSource: AxisSourceSelection = AxisSourceSelection(),
    val ySource: AxisSourceSelection = AxisSourceSelection(),
)

enum class ColorSourceType(
    val label: String,
    val tooltip: String,
) {
    AUTOMATIC(
        label = "Automatic",
        tooltip = "Use the default automatic color mapping based on the current chart layout.",
    ),
    RANGE(
        label = "Rectangle range",
        tooltip = "Use a matching rectangle range from a sheet to drive data-point colors.",
    ),
}

data class ColorSourceSelection(
    val type: ColorSourceType = ColorSourceType.AUTOMATIC,
    val rangeExpression: String = "",
)

data class SheetRangeReference(
    val sheetName: String,
    val startRow: Int,
    val endRow: Int,
    val startColumn: Int,
    val endColumn: Int,
) {
    val rowCount: Int get() = endRow - startRow + 1
    val columnCount: Int get() = endColumn - startColumn + 1

    fun displayLabel(): String {
        val safeSheetName = if (sheetName.any { it.isWhitespace() }) "'$sheetName'" else sheetName
        return "$safeSheetName!${columnIndexToExcelName(startColumn)}${startRow + 1}:${columnIndexToExcelName(endColumn)}${endRow + 1}"
    }
}

data class ResolvedDataSelection(
    val zRange: SheetRangeReference? = null,
    val xRange: SheetRangeReference? = null,
    val yRange: SheetRangeReference? = null,
    val zError: String? = null,
    val xError: String? = null,
    val yError: String? = null,
) {
    val isComplete: Boolean get() = zRange != null && xError == null && yError == null
}

enum class VisualizationMethod(
    val label: String,
    val tooltip: String,
) {
    COLUMN(
        label = "Columns",
        tooltip = "Each data point rises from the volume floor as a 3D column."
    ),
    MANIFOLD(
        label = "Manifold",
        tooltip = "Interpolates a continuous surface across the sampled data points."
    ),
    POINT_CLOUD(
        label = "Point Cloud",
        tooltip = "Places each data point as an independent 3D marker in space."
    ),
}

enum class InterpretationMethod(
    val label: String,
    val tooltip: String,
) {
    GRID_HEIGHT_MAP(
        label = "Grid Height Map",
        tooltip = "Treat every table cell as Z, and use row/column indices as the planar axes."
    ),
    ROW_TRIPLETS(
        label = "Row Triplets",
        tooltip = "Each row contributes one point, using selected columns as X, Y, and Z."
    ),
    COLUMN_TRIPLETS(
        label = "Column Triplets",
        tooltip = "Each column contributes one point, using selected rows as X, Y, and Z."
    ),
}

enum class ColumnGeometry(val label: String) {
    BOX("Box"),
    CYLINDER("Cylinder"),
}

enum class SurfaceInterpolationMethod(
    val label: String,
    val tooltip: String,
) {
    LINEAR_BLEND(
        label = "Linear Blend",
        tooltip = "Uses nearby points with linear distance weighting for a direct surface estimate."
    ),
    PARABOLIC_BLEND(
        label = "Parabolic Blend",
        tooltip = "Uses stronger quadratic weighting so nearby points dominate the surface more sharply."
    ),
    NEAREST_NEIGHBOR(
        label = "Nearest Neighbor",
        tooltip = "Uses the closest sampled point without smoothing for a faceted surface."
    ),
}

enum class ColorScheme(
    val label: String,
    val tooltip: String,
) {
    OCEAN(
        label = "Ocean",
        tooltip = "Cool blue-to-cyan shading driven by the X/Y distribution."
    ),
    SUNSET(
        label = "Sunset",
        tooltip = "Warm orange-purple shading driven by the X/Y distribution."
    ),
    FOREST(
        label = "Forest",
        tooltip = "Green-gold shading driven by the X/Y distribution."
    ),
    HEATMAP(
        label = "Heatmap",
        tooltip = "Blue-to-cyan-to-yellow-to-red intensity for high-contrast scalar visualization."
    ),
    RAINBOW(
        label = "Rainbow",
        tooltip = "Full-spectrum coloring for vivid scalar and positional variation."
    ),
    NEON(
        label = "Neon",
        tooltip = "Electric magenta-to-cyan glow-inspired gradient for punchy visuals."
    ),
}

data class InterpretationSettings(
    val xStandardize: Boolean = true,
    val yStandardize: Boolean = true,
    val zStandardize: Boolean = true,
    val xScale: Float = 1f,
    val yScale: Float = 1f,
    val zScale: Float = 1f,
)

data class VisualizationSettings(
    val colorScheme: ColorScheme = ColorScheme.OCEAN,
    val colorSource: ColorSourceSelection = ColorSourceSelection(),
    val columnGeometry: ColumnGeometry = ColumnGeometry.BOX,
    val interpolationMethod: SurfaceInterpolationMethod = SurfaceInterpolationMethod.LINEAR_BLEND,
    val pointRadius: Float = 0.012f,
)

private fun columnIndexToExcelName(index: Int): String {
    var working = index.coerceAtLeast(0)
    val builder = StringBuilder()
    do {
        builder.append(('A'.code + (working % 26)).toChar())
        working = (working / 26) - 1
    } while (working >= 0)
    return builder.reverse().toString()
}
