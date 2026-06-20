package com.yunhao.studio.spatial.chart.content

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pico.spatial.ui.design.Button
import com.pico.spatial.ui.design.ButtonDefaults
import com.pico.spatial.ui.design.Checkbox
import com.pico.spatial.ui.design.CircularProgressIndicator
import com.pico.spatial.ui.design.Icon
import com.pico.spatial.ui.design.IconButton
import com.pico.spatial.ui.design.IconButtonDefaults
import com.pico.spatial.ui.design.TitleBar
import com.pico.spatial.ui.design.Text
import com.pico.spatial.ui.design.TextField
import com.pico.spatial.ui.design.menu.Menu
import com.pico.spatial.ui.design.menu.MenuItem
import com.pico.spatial.ui.design.windows.Sheet
import com.pico.spatial.ui.design.windows.Subwindow
import com.pico.spatial.ui.design.windows.SubwindowPlacement
import com.pico.spatial.ui.design.windows.Toolbar
import com.pico.spatial.ui.foundation.content.SpatialView
import com.pico.spatial.ui.foundation.gesture.detectSpatialDragGesture
import com.pico.spatial.ui.foundation.gesture.detectSpatialScaleGesture
import com.pico.spatial.ui.foundation.tooltip
import com.pico.spatial.ui.platform.containers.LocalSpatialNavigator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt

const val SPREADSHEET_FILE_PICKER_WINDOW_ID = "SpreadsheetFilePickerWindow"
const val SPREADSHEET_RANGE_PICKER_WINDOW_ID = "SpreadsheetRangePickerWindow"

private val PanelBackgroundColor = Color(0xDDEFF2F5)
private val CardBackgroundColor = Color(0xAAFFFFFF)
private val CardBorderColor = Color(0x554A5A6A)
private val PrimaryTextColor = Color(0xFF1D2733)
private val SecondaryTextColor = Color(0xFF556273)
private val ErrorTextColor = Color(0xFFB63B4D)
private val AccentColor = Color(0xFF3D8BFF)
private val AccentTintColor = Color(0x333D8BFF)
private val RangePickerSurfaceColor = Color(0xFFF7F9FC)
private val RangePickerHeaderColor = Color(0xFFF0F4F8)
private val RangePickerGridColor = Color(0xFFCAD4DE)
private val RangePickerSelectionFill = Color(0x553D8BFF)
private val RangePickerSelectionBorder = Color(0xFF2F6FDB)

private val RangePickerRowHeaderWidth = 64.dp
private val RangePickerColumnHeaderHeight = 42.dp
private val RangePickerCellWidth = 108.dp
private val RangePickerCellHeight = 42.dp
private val RangePickerTitleBarHeight = 72.dp
private val RangePickerSideNavWidth = 180.dp
private val RangePickerAutoScrollThreshold = 64.dp
private val RangePickerAutoScrollStep = 36.dp
private val RangePickerSliderThickness = 22.dp
private val RangePickerSliderMinThumbLength = 56.dp
private const val TranslationGestureGain = 0.0008f
private const val RotationDragGain = 0.08f
private const val SpatialScaleJitterThreshold = 0.01f
private const val RangePickerViewportBufferCells = 2

private enum class RangePickerScrollAxis {
    HORIZONTAL,
    VERTICAL,
}

private enum class InspectorPanel {
    DATA,
    SCALE,
    VIEW,
}

private enum class AxisEditor {
    Z,
    X,
    Y,
}

private data class RenderComputation(
    val request: RenderRequest,
    val resolvedSelection: ResolvedDataSelection,
    val resolvedColorSelection: ResolvedColorSelection = ResolvedColorSelection(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)

private data class BaseRenderComputation(
    val resolvedSelection: ResolvedDataSelection,
    val resolvedColorSelection: ResolvedColorSelection = ResolvedColorSelection(),
    val parsedPoints: List<DataPoint3D> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)

private data class GridCellCoordinate(
    val rowIndex: Int,
    val columnIndex: Int,
)

private data class GridCellRange(
    val anchor: GridCellCoordinate,
    val active: GridCellCoordinate,
) {
    val startRow: Int get() = minOf(anchor.rowIndex, active.rowIndex)
    val endRow: Int get() = maxOf(anchor.rowIndex, active.rowIndex)
    val startColumn: Int get() = minOf(anchor.columnIndex, active.columnIndex)
    val endColumn: Int get() = maxOf(anchor.columnIndex, active.columnIndex)

    fun contains(rowIndex: Int, columnIndex: Int): Boolean {
        return rowIndex in startRow..endRow && columnIndex in startColumn..endColumn
    }
}

@Composable
fun HomeVolume(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val navigator = LocalSpatialNavigator.current
    val visualizationController = remember { VisualizationSceneController() }
    val pendingUri by SpreadsheetLaunchBridge.pendingUri.collectAsState()
    val filePickerVisible by SpreadsheetLaunchBridge.filePickerVisible.collectAsState()
    val rangePickerRequest by SpreadsheetLaunchBridge.rangePickerRequest.collectAsState()
    val rangePickerResult by SpreadsheetLaunchBridge.rangePickerResult.collectAsState()

    var activePanel by remember { mutableStateOf<InspectorPanel?>(null) }
    var expandedAxis by remember { mutableStateOf(AxisEditor.Z) }
    var document by remember { mutableStateOf<SpreadsheetDocument?>(null) }
    var dataSelectionSettings by remember { mutableStateOf(DataSelectionSettings()) }
    var interpretationSettings by remember { mutableStateOf(InterpretationSettings()) }
    var visualizationMethod by remember { mutableStateOf(VisualizationMethod.COLUMN) }
    var visualizationSettings by remember { mutableStateOf(VisualizationSettings()) }
    var viewTransform by remember { mutableStateOf(ViewTransformSettings()) }
    var documentLoadingMessage by remember { mutableStateOf<String?>(null) }
    var documentErrorMessage by remember { mutableStateOf<String?>(null) }
    var selectionSheetVisible by remember { mutableStateOf(false) }
    var selectionConfirmed by remember { mutableStateOf(false) }

    DisposableEffect(visualizationController) {
        onDispose { visualizationController.dispose() }
    }

    LaunchedEffect(rangePickerResult) {
        val result = rangePickerResult ?: return@LaunchedEffect
        when (result.target) {
            RangeSelectionTarget.Z -> {
                dataSelectionSettings = dataSelectionSettings.copy(zRangeExpression = result.rangeExpression)
                visualizationSettings = visualizationSettings.resetColorSourceToAutomaticIfNeeded()
                expandedAxis = AxisEditor.Z
            }

            RangeSelectionTarget.X -> {
                dataSelectionSettings = dataSelectionSettings.copy(
                    xSource = dataSelectionSettings.xSource.copy(
                        type = AxisSourceType.RANGE,
                        rangeExpression = result.rangeExpression,
                    ),
                )
                expandedAxis = AxisEditor.X
            }

            RangeSelectionTarget.Y -> {
                dataSelectionSettings = dataSelectionSettings.copy(
                    ySource = dataSelectionSettings.ySource.copy(
                        type = AxisSourceType.RANGE,
                        rangeExpression = result.rangeExpression,
                    ),
                )
                expandedAxis = AxisEditor.Y
            }

            RangeSelectionTarget.COLOR -> {
                visualizationSettings = visualizationSettings.copy(
                    colorSource = ColorSourceSelection(
                        type = ColorSourceType.RANGE,
                        rangeExpression = result.rangeExpression,
                    ),
                )
            }
        }
        SpreadsheetLaunchBridge.consumeRangeSelection()
    }

    fun updateDataSelection(nextSettings: DataSelectionSettings) {
        val zRangeChanged = nextSettings.zRangeExpression != dataSelectionSettings.zRangeExpression
        dataSelectionSettings = nextSettings
        if (zRangeChanged) {
            visualizationSettings = visualizationSettings.resetColorSourceToAutomaticIfNeeded()
        }
    }

    fun openRangePicker(target: RangeSelectionTarget) {
        val currentDocument = document ?: return
        val initialExpression = when (target) {
            RangeSelectionTarget.Z -> dataSelectionSettings.zRangeExpression
            RangeSelectionTarget.X -> dataSelectionSettings.xSource.rangeExpression
            RangeSelectionTarget.Y -> dataSelectionSettings.ySource.rangeExpression
            RangeSelectionTarget.COLOR -> visualizationSettings.colorSource.rangeExpression
        }
        SpreadsheetLaunchBridge.openRangePicker(
            SpreadsheetRangePickerRequest(
                target = target,
                document = currentDocument,
                initialExpression = initialExpression,
            ),
        )
        navigator.openWindowContainer(SPREADSHEET_RANGE_PICKER_WINDOW_ID)
    }

    fun abortDocumentSelection() {
        navigator.closeWindowContainer(SPREADSHEET_RANGE_PICKER_WINDOW_ID)
        SpreadsheetLaunchBridge.clearRangePickerRequest()
        document = null
        dataSelectionSettings = DataSelectionSettings()
        interpretationSettings = InterpretationSettings()
        visualizationSettings = VisualizationSettings()
        viewTransform = ViewTransformSettings()
        selectionConfirmed = false
        selectionSheetVisible = false
        activePanel = null
        expandedAxis = AxisEditor.Z
    }

    LaunchedEffect(pendingUri) {
        val uri = pendingUri ?: return@LaunchedEffect
        documentLoadingMessage = "Loading spreadsheet…"
        documentErrorMessage = null
        activePanel = null
        expandedAxis = AxisEditor.Z

        runCatching {
            withContext(Dispatchers.IO) {
                SpreadsheetParser.parse(context.contentResolver, uri)
            }
        }.onSuccess { parsedDocument ->
            document = parsedDocument
            dataSelectionSettings = DataSelectionSettings()
            interpretationSettings = InterpretationSettings()
            visualizationSettings = VisualizationSettings()
            viewTransform = ViewTransformSettings()
            selectionConfirmed = false
            selectionSheetVisible = true
        }.onFailure { throwable ->
            document = null
            dataSelectionSettings = DataSelectionSettings()
            interpretationSettings = InterpretationSettings()
            visualizationSettings = VisualizationSettings()
            viewTransform = ViewTransformSettings()
            selectionConfirmed = false
            selectionSheetVisible = false
            documentErrorMessage = throwable.message ?: "Unable to open this spreadsheet."
        }

        SpreadsheetLaunchBridge.consume(uri)
        documentLoadingMessage = null
    }

    val baseRenderComputation by produceState(
        initialValue =
            BaseRenderComputation(
                resolvedSelection = ResolvedDataSelection(zError = "Open a spreadsheet to begin."),
                resolvedColorSelection = ResolvedColorSelection(),
            ),
        document,
        dataSelectionSettings,
        visualizationSettings.colorSource,
        selectionConfirmed,
    ) {
        val currentDocument = document
        if (currentDocument == null) {
            value =
                BaseRenderComputation(
                    resolvedSelection = ResolvedDataSelection(zError = "Open a spreadsheet to begin."),
                    resolvedColorSelection = ResolvedColorSelection(),
                )
            return@produceState
        }

        val resolvedSelection = resolveDataSelection(currentDocument, dataSelectionSettings)
        val resolvedColorSelection = resolveColorSelection(
            currentDocument,
            visualizationSettings.colorSource,
            resolvedSelection.zRange,
        )
        if (!selectionConfirmed) {
            value =
                BaseRenderComputation(
                    resolvedSelection = resolvedSelection,
                    resolvedColorSelection = resolvedColorSelection,
                )
            return@produceState
        }

        value =
            BaseRenderComputation(
                resolvedSelection = resolvedSelection,
                resolvedColorSelection = resolvedColorSelection,
                isLoading = true,
            )

        value =
            withContext(Dispatchers.Default) {
                runCatching {
                    BaseRenderComputation(
                        resolvedSelection = resolvedSelection,
                        resolvedColorSelection = resolvedColorSelection,
                        parsedPoints = buildDataPoints(
                            document = currentDocument,
                            dataSelectionSettings = dataSelectionSettings,
                            colorSource = visualizationSettings.colorSource,
                            resolvedSelection = resolvedSelection,
                            resolvedColorSelection = resolvedColorSelection,
                        ),
                    )
                }.getOrElse { throwable ->
                    BaseRenderComputation(
                        resolvedSelection = resolvedSelection,
                        resolvedColorSelection = resolvedColorSelection,
                        errorMessage = throwable.message ?: "Unable to update the visualization.",
                    )
                }
            }
    }

    val renderComputation by remember(
        baseRenderComputation,
        interpretationSettings,
        visualizationMethod,
        visualizationSettings,
        selectionConfirmed,
        document,
    ) {
        derivedStateOf {
            val currentDocument = document
            val request =
                if (!selectionConfirmed || currentDocument == null) {
                    emptyRenderRequest(visualizationMethod, visualizationSettings, currentDocument?.displayName ?: "No spreadsheet loaded")
                } else {
                    RenderRequest(
                        points = mapPointsToScene(baseRenderComputation.parsedPoints, interpretationSettings),
                        visualizationMethod = visualizationMethod,
                        visualizationSettings = visualizationSettings,
                        interpretationMethod = InterpretationMethod.GRID_HEIGHT_MAP,
                        sheetName = baseRenderComputation.resolvedSelection.zRange?.sheetName ?: currentDocument.displayName,
                    )
                }

            RenderComputation(
                request = request,
                resolvedSelection = baseRenderComputation.resolvedSelection,
                resolvedColorSelection = baseRenderComputation.resolvedColorSelection,
                isLoading = baseRenderComputation.isLoading,
                errorMessage = baseRenderComputation.errorMessage,
            )
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        SpatialView(
            modifier = Modifier.fillMaxSize(),
            update = { _, _ ->
                visualizationController.updateViewTransform(viewTransform)
                visualizationController.render(renderComputation.request)
            },
            initial = { content, _ ->
                visualizationController.initialize(content)
                visualizationController.updateViewTransform(viewTransform)
                visualizationController.render(renderComputation.request)
            },
        )

        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            viewTransform = viewTransform.copy(
                                pitchDegrees = (viewTransform.pitchDegrees + (dragAmount.y * RotationDragGain)).coerceIn(-85f, 85f),
                                yawDegrees = viewTransform.yawDegrees + (dragAmount.x * RotationDragGain),
                            )
                            change.consume()
                        }
                    }
                    .pointerInput(context) {
                        detectSpatialScaleGesture(context = context) { scaleValue ->
                            val delta = scaleValue.scaleValue
                            if (abs(delta - 1f) < SpatialScaleJitterThreshold) return@detectSpatialScaleGesture
                            viewTransform = viewTransform.copy(
                                uniformScale = (viewTransform.uniformScale * delta).coerceIn(0.35f, 3.5f),
                            )
                        }
                    }
                    .pointerInput(Unit) {
                        detectSpatialDragGesture(context = context) { dragValue ->
                            viewTransform = viewTransform.copy(
                                offsetX = (viewTransform.offsetX + (dragValue.dragAmount.x * TranslationGestureGain)).coerceIn(-1.8f, 1.8f),
                                offsetY = (viewTransform.offsetY - (dragValue.dragAmount.y * TranslationGestureGain)).coerceIn(-1.8f, 1.8f),
                                offsetZ = (viewTransform.offsetZ + (dragValue.dragAmount.z * TranslationGestureGain)).coerceIn(-1.8f, 1.8f),
                            )
                        }
                    },
        )

        Toolbar(
            content = {
                ToolbarIconButton(
                    iconRes = android.R.drawable.ic_menu_upload,
                    tooltipTitle = "Open spreadsheet",
                    tooltipDescription = "Open or close the left-side file picker window.",
                ) {
                    documentErrorMessage = null
                    if (filePickerVisible) {
                        navigator.closeWindowContainer(SPREADSHEET_FILE_PICKER_WINDOW_ID)
                    } else {
                        navigator.openWindowContainer(SPREADSHEET_FILE_PICKER_WINDOW_ID)
                    }
                }
                ToolbarIconButton(
                    iconRes = android.R.drawable.ic_menu_edit,
                    tooltipTitle = "Data selection",
                    tooltipDescription = "Open or close the data-source selection augment.",
                ) {
                    activePanel = activePanel.toggle(InspectorPanel.DATA)
                }
                ToolbarIconButton(
                    iconRes = android.R.drawable.ic_menu_manage,
                    tooltipTitle = "Scale and standardize",
                    tooltipDescription = "Open or close the scaling augment.",
                ) {
                    activePanel = activePanel.toggle(InspectorPanel.SCALE)
                }
                ToolbarIconButton(
                    iconRes = android.R.drawable.ic_menu_view,
                    tooltipTitle = "View options",
                    tooltipDescription = "Open or close the appearance augment.",
                ) {
                    activePanel = activePanel.toggle(InspectorPanel.VIEW)
                }
            },
        )

        activePanel?.let { panel ->
            Subwindow(placement = SubwindowPlacement.Right) {
                PanelContainer {
                    when (panel) {
                        InspectorPanel.DATA -> DataSelectionPanel(
                            document = document,
                            dataSelectionSettings = dataSelectionSettings,
                            onDataSelectionSettingsChange = ::updateDataSelection,
                            resolvedSelection = renderComputation.resolvedSelection,
                            expandedAxis = expandedAxis,
                            onExpandedAxisChange = { expandedAxis = it },
                            onPickRange = ::openRangePicker,
                            renderedPointCount = renderComputation.request.points.size,
                            selectionConfirmed = selectionConfirmed,
                        )

                        InspectorPanel.SCALE -> ScalePanel(
                            interpretationSettings = interpretationSettings,
                            onInterpretationSettingsChange = { interpretationSettings = it },
                        )

                        InspectorPanel.VIEW -> ViewPanel(
                            document = document,
                            resolvedSelection = renderComputation.resolvedSelection,
                            resolvedColorSelection = renderComputation.resolvedColorSelection,
                            visualizationMethod = visualizationMethod,
                            onVisualizationMethodChange = { visualizationMethod = it },
                            visualizationSettings = visualizationSettings,
                            onVisualizationSettingsChange = { visualizationSettings = it },
                            onPickRange = ::openRangePicker,
                        )
                    }
                }
            }
        }

        if (selectionSheetVisible && document != null) {
            SelectionSheet(
                document = document!!,
                dataSelectionSettings = dataSelectionSettings,
                onDataSelectionSettingsChange = ::updateDataSelection,
                resolvedSelection = renderComputation.resolvedSelection,
                expandedAxis = expandedAxis,
                onExpandedAxisChange = { expandedAxis = it },
                onPickRange = ::openRangePicker,
                onContinue = {
                    selectionConfirmed = true
                    selectionSheetVisible = false
                },
                onAbort = ::abortDocumentSelection,
            )
        }

        if (documentLoadingMessage != null || renderComputation.isLoading) {
            LoadingOverlay(message = documentLoadingMessage ?: "Refreshing visualization…")
        }

        documentErrorMessage?.let { message ->
            StatusPill(
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 28.dp),
                message = message,
                background = Color(0xCCF8D7DA),
                textColor = ErrorTextColor,
            )
        }

        renderComputation.errorMessage?.let { message ->
            StatusPill(
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 72.dp),
                message = message,
                background = Color(0xCCF8D7DA),
                textColor = ErrorTextColor,
            )
        }
    }
}

@Composable
fun SpreadsheetFilePickerWindow(modifier: Modifier = Modifier) {
    val navigator = LocalSpatialNavigator.current
    var didAutoLaunch by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("The system file picker will open here.") }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) {
            statusMessage = "No file selected. You can retry or close this panel."
        } else {
            SpreadsheetLaunchBridge.publish(uri)
            statusMessage = "Loading ${uri.lastPathSegment ?: "spreadsheet"}…"
            navigator.closeWindowContainer()
        }
    }

    DisposableEffect(Unit) {
        SpreadsheetLaunchBridge.setFilePickerVisible(true)
        onDispose { SpreadsheetLaunchBridge.setFilePickerVisible(false) }
    }

    LaunchedEffect(Unit) {
        if (!didAutoLaunch) {
            didAutoLaunch = true
            launcher.launch(SpreadsheetParser.supportedMimeTypes())
        }
    }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(PanelBackgroundColor)
                .padding(24.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                text = "Open spreadsheet",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = PrimaryTextColor,
            )
            Text(
                text = "Pick a CSV, TSV, XLS, or XLSX file. Closing this window cancels the action.",
                color = SecondaryTextColor,
            )
            StatusPill(
                message = statusMessage,
                background = AccentTintColor,
                textColor = PrimaryTextColor,
            )
            Button(
                modifier = Modifier.tooltip(
                    text = "Choose spreadsheet",
                    description = "Launch the system document picker again.",
                ),
                onClick = { launcher.launch(SpreadsheetParser.supportedMimeTypes()) },
            ) {
                Text("Choose spreadsheet")
            }
            Button(
                modifier = Modifier.tooltip(
                    text = "Close file picker",
                    description = "Abort file selection and close this dedicated picker window.",
                ),
                onClick = { navigator.closeWindowContainer() },
                colors = flatButtonColors(selected = false),
            ) {
                Text("Close")
            }
            Text(
                text = "Tip: ranges use Excel-style references such as Sheet1!A1:C8.",
                color = SecondaryTextColor,
            )
        }
    }
}

@Composable
private fun SelectionSheet(
    document: SpreadsheetDocument,
    dataSelectionSettings: DataSelectionSettings,
    onDataSelectionSettingsChange: (DataSelectionSettings) -> Unit,
    resolvedSelection: ResolvedDataSelection,
    expandedAxis: AxisEditor,
    onExpandedAxisChange: (AxisEditor) -> Unit,
    onPickRange: (RangeSelectionTarget) -> Unit,
    onContinue: () -> Unit,
    onAbort: () -> Unit,
) {
    Sheet(
        onDismissRequest = onAbort,
        title = { Text("Select X / Y / Z sources", color = PrimaryTextColor, fontWeight = FontWeight.Bold) },
        leadingAction = {
            IconButton(
                onClick = onContinue,
                enabled = resolvedSelection.isComplete,
                modifier = Modifier.tooltip(
                    text = "Confirm data configuration",
                    description = "Close this sheet and proceed with the visualization.",
                ),
            ) {
                Icon(
                    painter = painterResource(id = android.R.drawable.checkbox_on_background),
                    contentDescription = "Confirm data configuration",
                )
            }
        },
        trailingAction = {
            IconButton(
                onClick = onAbort,
                modifier = Modifier.tooltip(
                    text = "Abort spreadsheet loading",
                    description = "Close this sheet and discard the loaded spreadsheet.",
                ),
            ) {
                Icon(
                    painter = painterResource(id = android.R.drawable.ic_menu_close_clear_cancel),
                    contentDescription = "Abort spreadsheet loading",
                )
            }
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = document.displayName,
                color = PrimaryTextColor,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Choose the Z range first, then set X and Y. Visualization stays blocked until all three are valid.",
                color = SecondaryTextColor,
            )
            SelectionEditorContent(
                document = document,
                dataSelectionSettings = dataSelectionSettings,
                onDataSelectionSettingsChange = onDataSelectionSettingsChange,
                resolvedSelection = resolvedSelection,
                expandedAxis = expandedAxis,
                onExpandedAxisChange = onExpandedAxisChange,
                onPickRange = onPickRange,
            )
        }
    }
}

@Composable
private fun DataSelectionPanel(
    document: SpreadsheetDocument?,
    dataSelectionSettings: DataSelectionSettings,
    onDataSelectionSettingsChange: (DataSelectionSettings) -> Unit,
    resolvedSelection: ResolvedDataSelection,
    expandedAxis: AxisEditor,
    onExpandedAxisChange: (AxisEditor) -> Unit,
    onPickRange: (RangeSelectionTarget) -> Unit,
    renderedPointCount: Int,
    selectionConfirmed: Boolean,
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        PanelTitle(title = "Data", subtitle = "Selection only. Use the scale panel for standardization and scale.")

        document?.let {
            SectionCard {
                Text(it.displayName, color = PrimaryTextColor, fontWeight = FontWeight.SemiBold)
                Text(buildDocumentSummary(it), color = SecondaryTextColor)
            }
        } ?: SectionCard {
            Text("No spreadsheet loaded", color = PrimaryTextColor, fontWeight = FontWeight.SemiBold)
            Text("Use the toolbar file icon to open a spreadsheet.", color = SecondaryTextColor)
        }

        SelectionEditorContent(
            document = document,
            dataSelectionSettings = dataSelectionSettings,
            onDataSelectionSettingsChange = onDataSelectionSettingsChange,
            resolvedSelection = resolvedSelection,
            expandedAxis = expandedAxis,
            onExpandedAxisChange = onExpandedAxisChange,
            onPickRange = onPickRange,
        )

        SectionCard {
            Text("Preview status", color = PrimaryTextColor, fontWeight = FontWeight.SemiBold)
            Text(
                text = when {
                    !selectionConfirmed -> "Open or confirm selections in the modal sheet before visualization starts."
                    resolvedSelection.isComplete -> "Rendering $renderedPointCount points from the current selection."
                    else -> "Resolve the remaining selection errors to update the visualization."
                },
                color = SecondaryTextColor,
            )
        }
    }
}

@Composable
private fun ScalePanel(
    interpretationSettings: InterpretationSettings,
    onInterpretationSettingsChange: (InterpretationSettings) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        PanelTitle(title = "Scale", subtitle = "Standardization and manual scale live here now.")

        SectionCard {
            Text(
                text = "Scale always multiplies the current axis extent. If standardization is enabled, scaling is applied on top of the standardized result. This panel changes data interpretation, while Spatial Scale Gesture only scales the rendered chart root globally.",
                color = SecondaryTextColor,
            )
        }

        AxisTransformControls(
            axisLabel = "X",
            standardize = interpretationSettings.xStandardize,
            onStandardizeChange = {
                onInterpretationSettingsChange(interpretationSettings.copy(xStandardize = it))
            },
            scale = interpretationSettings.xScale,
            onScaleChange = {
                onInterpretationSettingsChange(interpretationSettings.copy(xScale = it))
            },
        )
        AxisTransformControls(
            axisLabel = "Y",
            standardize = interpretationSettings.yStandardize,
            onStandardizeChange = {
                onInterpretationSettingsChange(interpretationSettings.copy(yStandardize = it))
            },
            scale = interpretationSettings.yScale,
            onScaleChange = {
                onInterpretationSettingsChange(interpretationSettings.copy(yScale = it))
            },
        )
        AxisTransformControls(
            axisLabel = "Z",
            standardize = interpretationSettings.zStandardize,
            onStandardizeChange = {
                onInterpretationSettingsChange(interpretationSettings.copy(zStandardize = it))
            },
            scale = interpretationSettings.zScale,
            onScaleChange = {
                onInterpretationSettingsChange(interpretationSettings.copy(zScale = it))
            },
        )
    }
}

@Composable
private fun ViewPanel(
    document: SpreadsheetDocument?,
    resolvedSelection: ResolvedDataSelection,
    resolvedColorSelection: ResolvedColorSelection,
    visualizationMethod: VisualizationMethod,
    onVisualizationMethodChange: (VisualizationMethod) -> Unit,
    visualizationSettings: VisualizationSettings,
    onVisualizationSettingsChange: (VisualizationSettings) -> Unit,
    onPickRange: (RangeSelectionTarget) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        PanelTitle(title = "View", subtitle = "Menus control appearance options and show the current selection.")

        SectionCard {
            OptionMenuField(
                label = "Visualization style",
                tooltip = "Choose the overall 3D representation.",
                currentLabel = visualizationMethod.label,
                options = VisualizationMethod.entries,
                optionLabel = { it.label },
                optionTooltip = { it.tooltip },
                isSelected = { it == visualizationMethod },
                onSelect = onVisualizationMethodChange,
            )
        }

        SectionCard {
            OptionMenuField(
                label = "Color scheme",
                tooltip = "Pick the gradient used for automatic colors or custom color-range values.",
                currentLabel = visualizationSettings.colorScheme.label,
                options = ColorScheme.entries,
                optionLabel = { it.label },
                optionTooltip = { it.tooltip },
                isSelected = { it == visualizationSettings.colorScheme },
                onSelect = {
                    onVisualizationSettingsChange(visualizationSettings.copy(colorScheme = it))
                },
            )
        }

        SectionCard {
            ColorSourceSection(
                document = document,
                zRange = resolvedSelection.zRange,
                colorSource = visualizationSettings.colorSource,
                resolvedColorSelection = resolvedColorSelection,
                onColorSourceChange = {
                    onVisualizationSettingsChange(visualizationSettings.copy(colorSource = it))
                },
                onPickRange = { onPickRange(RangeSelectionTarget.COLOR) },
            )
        }

        when (visualizationMethod) {
            VisualizationMethod.COLUMN -> {
                SectionCard {
                    OptionMenuField(
                        label = "Column geometry",
                        tooltip = "Choose how each bar primitive is shaped.",
                        currentLabel = visualizationSettings.columnGeometry.label,
                        options = ColumnGeometry.entries,
                        optionLabel = { it.label },
                        optionTooltip = { "Render columns as ${it.label.lowercase()}." },
                        isSelected = { it == visualizationSettings.columnGeometry },
                        onSelect = {
                            onVisualizationSettingsChange(visualizationSettings.copy(columnGeometry = it))
                        },
                    )
                }
            }

            VisualizationMethod.MANIFOLD -> {
                SectionCard {
                    OptionMenuField(
                        label = "Surface interpolation",
                        tooltip = "Control how the manifold surface blends between sampled points.",
                        currentLabel = visualizationSettings.interpolationMethod.label,
                        options = SurfaceInterpolationMethod.entries,
                        optionLabel = { it.label },
                        optionTooltip = { it.tooltip },
                        isSelected = { it == visualizationSettings.interpolationMethod },
                        onSelect = {
                            onVisualizationSettingsChange(visualizationSettings.copy(interpolationMethod = it))
                        },
                    )
                }
            }

            VisualizationMethod.POINT_CLOUD -> {
                SectionCard {
                    PositiveFloatField(
                        label = "Point radius",
                        tooltip = "Any value above 0 is allowed. Typical values are around 0.005 to 0.04.",
                        value = visualizationSettings.pointRadius,
                        onValueCommit = {
                            onVisualizationSettingsChange(visualizationSettings.copy(pointRadius = it))
                        },
                        placeholder = "0.012",
                    )
                }
            }
        }
    }
}

@Composable
private fun SelectionEditorContent(
    document: SpreadsheetDocument?,
    dataSelectionSettings: DataSelectionSettings,
    onDataSelectionSettingsChange: (DataSelectionSettings) -> Unit,
    resolvedSelection: ResolvedDataSelection,
    expandedAxis: AxisEditor,
    onExpandedAxisChange: (AxisEditor) -> Unit,
    onPickRange: (RangeSelectionTarget) -> Unit,
) {
    val zReady = resolvedSelection.zRange != null

    SectionCard {
        Text("Data to be visualized", color = PrimaryTextColor, fontWeight = FontWeight.SemiBold)
        Button(
            modifier = Modifier.fillMaxWidth().tooltip(
                text = "Pick data to be visualized",
                description = "Open the spreadsheet sheet and choose the rectangular Z-value range.",
            ),
            onClick = { onPickRange(RangeSelectionTarget.Z) },
            enabled = document != null,
            colors = flatButtonColors(selected = false),
        ) {
            Text(resolvedSelection.zRange?.displayLabel() ?: dataSelectionSettings.zRangeExpression.ifBlank { "no selection made" })
        }
        Text(
            text = rangeFieldHelpText(document),
            color = SecondaryTextColor,
        )
        (if (document == null) "Open a spreadsheet to begin." else resolvedSelection.zError)?.let {
            Text(it, color = ErrorTextColor)
        }
    }

    AxisSourceSection(
        axisName = "X",
        selection = dataSelectionSettings.xSource,
        resolvedRange = resolvedSelection.xRange,
        errorMessage = resolvedSelection.xError,
        enabled = zReady,
        onPickRange = { onPickRange(RangeSelectionTarget.X) },
        onSelectionChange = {
            onDataSelectionSettingsChange(dataSelectionSettings.copy(xSource = it))
        },
    )

    AxisSourceSection(
        axisName = "Y",
        selection = dataSelectionSettings.ySource,
        resolvedRange = resolvedSelection.yRange,
        errorMessage = resolvedSelection.yError,
        enabled = zReady,
        onPickRange = { onPickRange(RangeSelectionTarget.Y) },
        onSelectionChange = {
            onDataSelectionSettingsChange(dataSelectionSettings.copy(ySource = it))
        },
    )
}

@Composable
private fun AxisSourceSection(
    axisName: String,
    selection: AxisSourceSelection,
    resolvedRange: SheetRangeReference?,
    errorMessage: String?,
    enabled: Boolean,
    onPickRange: () -> Unit,
    onSelectionChange: (AxisSourceSelection) -> Unit,
) {
    SectionCard {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OptionMenuField(
                label = "Data source for $axisName axis",
                tooltip = "Choose a matching range, row index, or column index for the $axisName axis.",
                currentLabel = currentAxisSelectionLabel(selection, resolvedRange),
                options = AxisSourceType.entries.filterNot { it == AxisSourceType.NONE },
                optionLabel = { it.label },
                optionTooltip = { it.tooltip },
                isSelected = { it == selection.type },
                onSelect = { type ->
                    val nextSelection = selection.copy(
                        type = type,
                        rangeExpression = if (type == AxisSourceType.RANGE) selection.rangeExpression else "",
                    )
                    onSelectionChange(nextSelection)
                    if (enabled && type == AxisSourceType.RANGE) {
                        onPickRange()
                    }
                },
            )
            Text(
                text = if (selection.type == AxisSourceType.RANGE) {
                    "Choose Rectangle range again from the menu to reopen the table picker. It must match the Z range dimensions exactly."
                } else {
                    "Choose a matching range, row index, or column index for the $axisName axis."
                },
                color = SecondaryTextColor,
            )
            (if (enabled) errorMessage else "Select a valid Z range first.")?.let {
                Text(it, color = ErrorTextColor)
            }
        }
    }
}

@Composable
private fun ColorSourceSection(
    document: SpreadsheetDocument?,
    zRange: SheetRangeReference?,
    colorSource: ColorSourceSelection,
    resolvedColorSelection: ResolvedColorSelection,
    onColorSourceChange: (ColorSourceSelection) -> Unit,
    onPickRange: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OptionMenuField(
            label = "Color data source",
            tooltip = "Choose whether colors come from the automatic mapping or from a matching spreadsheet range.",
            currentLabel = currentColorSourceSelectionLabel(colorSource, resolvedColorSelection),
            options = ColorSourceType.entries,
            optionLabel = { it.label },
            optionTooltip = { it.tooltip },
            isSelected = { it == colorSource.type },
            onSelect = { type ->
                val next = colorSource.copy(
                    type = type,
                    rangeExpression = if (type == ColorSourceType.RANGE) colorSource.rangeExpression else "",
                )
                onColorSourceChange(next)
                if (document != null && zRange != null && type == ColorSourceType.RANGE) {
                    onPickRange()
                }
            },
        )

        Text(
            text = when {
                colorSource.type != ColorSourceType.RANGE -> "Use automatic mapping, or choose Rectangle range from the menu to pick table-driven colors."
                zRange == null -> "Pick a valid Z range first."
                resolvedColorSelection.colorRange != null -> "Selected color range: ${resolvedColorSelection.colorRange.displayLabel()}"
                else -> "Choose Rectangle range again from the menu to reopen the table picker. Color range must match Z dimensions: ${zRange.rowCount}×${zRange.columnCount}."
            },
            color = SecondaryTextColor,
        )

        resolvedColorSelection.errorMessage?.let {
            Text(it, color = ErrorTextColor)
        }
    }
}

@Composable
private fun AxisRangeSection(
    label: String,
    tooltip: String,
    currentLabel: String,
    expanded: Boolean,
    enabled: Boolean,
    onHeaderClick: () -> Unit,
    errorMessage: String?,
    content: @Composable ColumnScope.() -> Unit,
) {
    SectionCard {
        Text(label, color = PrimaryTextColor, fontWeight = FontWeight.SemiBold)
        Button(
            modifier = Modifier.fillMaxWidth().tooltip(text = label, description = tooltip),
            onClick = onHeaderClick,
            enabled = enabled,
            colors = flatButtonColors(selected = expanded),
        ) {
            Text(
                text = currentLabel,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
        }
        errorMessage?.let {
            Text(it, color = ErrorTextColor)
        }
    }
}

@Composable
private fun AxisTransformControls(
    axisLabel: String,
    standardize: Boolean,
    onStandardizeChange: (Boolean) -> Unit,
    scale: Float,
    onScaleChange: (Float) -> Unit,
) {
    SectionCard {
        Text("$axisLabel axis", color = PrimaryTextColor, fontWeight = FontWeight.SemiBold)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Checkbox(
                checked = standardize,
                onCheckedChange = onStandardizeChange,
                modifier = Modifier.tooltip(
                    text = "$axisLabel standardize",
                    description = "Enable or disable standardization for the $axisLabel axis.",
                ),
            )
            Text(
                text = if (standardize) {
                    "$axisLabel standardization is on."
                } else {
                    "$axisLabel standardization is off."
                },
                color = SecondaryTextColor,
            )
        }
        PositiveFloatField(
            label = "$axisLabel scale",
            tooltip = "Any positive floating-point value is allowed. This multiplier is still applied when standardization is enabled.",
            value = scale,
            onValueCommit = onScaleChange,
            placeholder = "1.0",
        )
    }
}

@Composable
private fun PositiveFloatField(
    label: String,
    tooltip: String,
    value: Float,
    onValueCommit: (Float) -> Unit,
    enabled: Boolean = true,
    placeholder: String,
) {
    var text by remember(value) { mutableStateOf(trimFloatText(value)) }
    val parsedValue = text.toFloatOrNull()
    val hasError = text.isNotBlank() && (parsedValue == null || parsedValue <= 0f)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, color = PrimaryTextColor, fontWeight = FontWeight.SemiBold)
        TextField(
            value = text,
            onValueChange = {
                text = it
                val next = it.toFloatOrNull()
                if (next != null && next > 0f) {
                    onValueCommit(next)
                }
            },
            modifier = Modifier.fillMaxWidth().tooltip(text = label, description = tooltip),
            enabled = enabled,
            isError = hasError,
            placeholder = { Text(placeholder, color = SecondaryTextColor) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            supportingText = {
                Text(
                    text = when {
                        hasError -> "Enter a value greater than 0."
                        else -> "Current value: ${trimFloatText(value)}"
                    },
                    color = if (hasError) ErrorTextColor else SecondaryTextColor,
                )
            },
        )
    }
}

@Composable
private fun <T> OptionMenuField(
    label: String,
    tooltip: String,
    currentLabel: String,
    options: Iterable<T>,
    optionLabel: (T) -> String,
    optionTooltip: (T) -> String,
    isSelected: (T) -> Boolean,
    onSelect: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(label, color = PrimaryTextColor, fontWeight = FontWeight.SemiBold)
            Button(
                modifier = Modifier.fillMaxWidth().tooltip(text = label, description = tooltip),
                onClick = { expanded = !expanded },
                colors = flatButtonColors(selected = expanded),
                trailingIcon = {
                    Icon(
                        painter = painterResource(id = com.pico.spatial.ui.design.R.drawable.ic_sui_dropdown_trigger_down),
                        contentDescription = null,
                    )
                },
            ) {
                Text(currentLabel)
            }
        }
        if (expanded) {
            Menu(onDismissRequest = { expanded = false }, modifier = Modifier.width(260.dp)) {
                options.forEach { option ->
                    MenuItem(
                        title = { Text(optionLabel(option), color = PrimaryTextColor) },
                        subtitle = { Text(optionTooltip(option), color = SecondaryTextColor) },
                        trailingIcon =
                            if (isSelected(option)) {
                                {
                                    Icon(
                                        painter = painterResource(id = com.pico.spatial.ui.design.R.drawable.ic_sui_sheet_selected),
                                        contentDescription = null,
                                    )
                                }
                            } else {
                                null
                            },
                        onClick = {
                            expanded = false
                            onSelect(option)
                        },
                    )
                }
            }
        }
    }
}

@Composable
fun SpreadsheetRangePickerWindow(modifier: Modifier = Modifier) {
    val navigator = LocalSpatialNavigator.current
    val request by SpreadsheetLaunchBridge.rangePickerRequest.collectAsState()

    DisposableEffect(Unit) {
        SpreadsheetLaunchBridge.setRangePickerVisible(true)
        onDispose {
            SpreadsheetLaunchBridge.setRangePickerVisible(false)
            SpreadsheetLaunchBridge.clearRangePickerRequest()
        }
    }

    val pickerRequest = request
    if (pickerRequest == null) {
        Box(
            modifier = modifier.fillMaxSize().background(RangePickerSurfaceColor).padding(20.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("No spreadsheet selection is active.", color = SecondaryTextColor)
        }
        return
    }

    val initialSelection = remember(pickerRequest) {
        parseSheetRangeReference(pickerRequest.initialExpression, pickerRequest.document)
    }
    var activeSheetIndex by remember(pickerRequest) {
        mutableStateOf(
            initialSelection?.let { range ->
                pickerRequest.document.sheets.indexOfFirst { it.name == range.sheetName }.takeIf { it >= 0 }
            } ?: 0,
        )
    }
    var selectedRange by remember(pickerRequest) {
        mutableStateOf(
            initialSelection?.let {
                GridCellRange(
                    anchor = GridCellCoordinate(it.startRow, it.startColumn),
                    active = GridCellCoordinate(it.endRow, it.endColumn),
                )
            },
        )
    }
    var rangeText by remember(pickerRequest) {
        mutableStateOf(initialSelection?.displayLabel() ?: pickerRequest.initialExpression)
    }

    val activeSheet = pickerRequest.document.sheets.getOrNull(activeSheetIndex)
    val parsedRange = remember(rangeText, pickerRequest.document) {
        parseSheetRangeReference(rangeText, pickerRequest.document)
    }
    val latestTarget by rememberUpdatedState(pickerRequest.target)
    val latestActiveSheetName by rememberUpdatedState(activeSheet?.name)
    val latestParsedRangeLabel by rememberUpdatedState(parsedRange?.displayLabel())
    val latestSelectedRange by rememberUpdatedState(selectedRange)
    val currentRangeExpression = parsedRange?.displayLabel() ?: activeSheet?.let { sheet ->
        selectedRange?.toSheetRangeReference(sheet.name)?.displayLabel()
    }

    Column(
        modifier = modifier.fillMaxSize().background(RangePickerSurfaceColor).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TitleBar(
            modifier = Modifier.fillMaxWidth().height(RangePickerTitleBarHeight),
            title = {
                TextField(
                    value = rangeText,
                    onValueChange = { nextValue ->
                        rangeText = nextValue
                        parseSheetRangeReference(nextValue, pickerRequest.document)?.let { typedRange ->
                            activeSheetIndex = pickerRequest.document.sheets.indexOfFirst { it.name == typedRange.sheetName }
                                .takeIf { it >= 0 } ?: activeSheetIndex
                            selectedRange = GridCellRange(
                                anchor = GridCellCoordinate(typedRange.startRow, typedRange.startColumn),
                                active = GridCellCoordinate(typedRange.endRow, typedRange.endColumn),
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Table1!A1:Z24", color = SecondaryTextColor) },
                    isError = rangeText.isNotBlank() && parsedRange == null,
                    supportingText = {
                        Text(
                            text = if (rangeText.isBlank()) {
                                "Select cells or type an Excel-style range."
                            } else if (parsedRange == null) {
                                "Enter a valid Excel-style range, such as Sheet1!A1:C8."
                            } else {
                                "Current selection: ${parsedRange.displayLabel()}"
                            },
                            color = if (rangeText.isNotBlank() && parsedRange == null) ErrorTextColor else SecondaryTextColor,
                        )
                    },
                )
            },
            trailingActions = {
                IconButton(
                    onClick = { navigator.closeWindowContainer() },
                    enabled = !currentRangeExpression.isNullOrBlank(),
                    modifier = Modifier.tooltip(
                        text = "Confirm range selection",
                        description = "Close this range picker and confirm the current selection.",
                    ),
                ) {
                    Icon(
                        painter = painterResource(id = android.R.drawable.checkbox_on_background),
                        contentDescription = "Confirm range selection",
                    )
                }
            },
        )

        activeSheet?.let { sheet ->
            SpreadsheetRangeGrid(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                sheet = sheet,
                selectedRange = selectedRange,
                onSelectionChange = {
                    selectedRange = it
                    rangeText = it.toSheetRangeReference(sheet.name).displayLabel()
                },
            )
        } ?: Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text("This spreadsheet does not contain any sheets.", color = ErrorTextColor)
        }

    }

    SpreadsheetSheetToolbar(
        sheetNames = pickerRequest.document.sheets.map { it.name },
        activeSheetIndex = activeSheetIndex,
        onSelectSheet = { index ->
            activeSheetIndex = index.coerceIn(0, pickerRequest.document.sheets.lastIndex)
            selectedRange = null
            rangeText = ""
        },
    )

    DisposableEffect(Unit) {
        onDispose {
            val rangeExpression = latestParsedRangeLabel ?: latestActiveSheetName?.let { sheetName ->
                latestSelectedRange?.toSheetRangeReference(sheetName)?.displayLabel()
            }
            if (!rangeExpression.isNullOrBlank()) {
                SpreadsheetLaunchBridge.publishRangeSelection(
                    SpreadsheetRangePickerResult(
                        target = latestTarget,
                        rangeExpression = rangeExpression,
                    ),
                )
            }
            SpreadsheetLaunchBridge.clearRangePickerRequest()
        }
    }
}

@Composable
private fun SpreadsheetRangeGrid(
    sheet: SpreadsheetSheet,
    selectedRange: GridCellRange?,
    onSelectionChange: (GridCellRange) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val cellWidthPx = with(density) { RangePickerCellWidth.toPx() }
    val cellHeightPx = with(density) { RangePickerCellHeight.toPx() }
    val autoScrollThresholdPx = with(density) { RangePickerAutoScrollThreshold.toPx() }
    val autoScrollStepPx = with(density) { RangePickerAutoScrollStep.toPx() }
    val horizontalScrollState = rememberScrollState()
    val verticalScrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()
    var bodyViewportSize by remember(sheet) { mutableStateOf(IntSize.Zero) }
    var columnHeaderViewportSize by remember(sheet) { mutableStateOf(IntSize.Zero) }
    var rowHeaderViewportSize by remember(sheet) { mutableStateOf(IntSize.Zero) }
    val totalGridWidth = RangePickerCellWidth * sheet.columnCount.coerceAtLeast(1)
    val totalGridHeight = RangePickerCellHeight * sheet.rowCount.coerceAtLeast(1)

    val visibleColumnCount = ((ceil(bodyViewportSize.width / cellWidthPx.toDouble()).toInt()) + (RangePickerViewportBufferCells * 2))
        .coerceAtLeast(RangePickerViewportBufferCells * 2 + 1)
    val visibleRowCount = ((ceil(bodyViewportSize.height / cellHeightPx.toDouble()).toInt()) + (RangePickerViewportBufferCells * 2))
        .coerceAtLeast(RangePickerViewportBufferCells * 2 + 1)
    val visibleColumnStart = ((horizontalScrollState.value / cellWidthPx).toInt() - RangePickerViewportBufferCells)
        .coerceIn(0, sheet.columnCount.coerceAtLeast(1) - 1)
    val visibleRowStart = ((verticalScrollState.value / cellHeightPx).toInt() - RangePickerViewportBufferCells)
        .coerceIn(0, sheet.rowCount.coerceAtLeast(1) - 1)
    val visibleColumnEnd = (visibleColumnStart + visibleColumnCount - 1).coerceIn(visibleColumnStart, sheet.columnCount.coerceAtLeast(1) - 1)
    val visibleRowEnd = (visibleRowStart + visibleRowCount - 1).coerceIn(visibleRowStart, sheet.rowCount.coerceAtLeast(1) - 1)
    val visibleColumnIndices = if (sheet.columnCount == 0) IntRange.EMPTY else visibleColumnStart..visibleColumnEnd
    val visibleRowIndices = if (sheet.rowCount == 0) IntRange.EMPTY else visibleRowStart..visibleRowEnd
    val visibleContentOffsetX = RangePickerCellWidth * visibleColumnStart
    val visibleContentOffsetY = RangePickerCellHeight * visibleRowStart

    fun resolveCell(offset: Offset, clampToBounds: Boolean): GridCellCoordinate? {
        if (sheet.rowCount <= 0 || sheet.columnCount <= 0) return null
        val columnIndex = ((offset.x + horizontalScrollState.value) / cellWidthPx).toInt()
        val rowIndex = ((offset.y + verticalScrollState.value) / cellHeightPx).toInt()
        if (!clampToBounds && (rowIndex !in 0 until sheet.rowCount || columnIndex !in 0 until sheet.columnCount)) {
            return null
        }
        return GridCellCoordinate(
            rowIndex = rowIndex.coerceIn(0, sheet.rowCount - 1),
            columnIndex = columnIndex.coerceIn(0, sheet.columnCount - 1),
        )
    }

    fun resolveColumnIndex(offsetX: Float, clampToBounds: Boolean): Int? {
        if (sheet.columnCount <= 0) return null
        val columnIndex = ((offsetX + horizontalScrollState.value) / cellWidthPx).toInt()
        if (!clampToBounds && columnIndex !in 0 until sheet.columnCount) return null
        return columnIndex.coerceIn(0, sheet.columnCount - 1)
    }

    fun resolveRowIndex(offsetY: Float, clampToBounds: Boolean): Int? {
        if (sheet.rowCount <= 0) return null
        val rowIndex = ((offsetY + verticalScrollState.value) / cellHeightPx).toInt()
        if (!clampToBounds && rowIndex !in 0 until sheet.rowCount) return null
        return rowIndex.coerceIn(0, sheet.rowCount - 1)
    }

    fun scrollHorizontalByPointer(pointerX: Float, viewportWidth: Float) {
        if (viewportWidth <= 0f) return
        val horizontalDelta = when {
            pointerX < autoScrollThresholdPx -> -(autoScrollThresholdPx - pointerX).coerceAtMost(autoScrollStepPx)
            pointerX > viewportWidth - autoScrollThresholdPx ->
                (pointerX - (viewportWidth - autoScrollThresholdPx)).coerceAtMost(autoScrollStepPx)
            else -> 0f
        }
        if (abs(horizontalDelta) > 0.5f) {
            coroutineScope.launch {
                horizontalScrollState.scrollTo(
                    (horizontalScrollState.value + horizontalDelta.roundToInt()).coerceIn(0, horizontalScrollState.maxValue),
                )
            }
        }
    }

    fun scrollVerticalByPointer(pointerY: Float, viewportHeight: Float) {
        if (viewportHeight <= 0f) return
        val verticalDelta = when {
            pointerY < autoScrollThresholdPx -> -(autoScrollThresholdPx - pointerY).coerceAtMost(autoScrollStepPx)
            pointerY > viewportHeight - autoScrollThresholdPx ->
                (pointerY - (viewportHeight - autoScrollThresholdPx)).coerceAtMost(autoScrollStepPx)
            else -> 0f
        }
        if (abs(verticalDelta) > 0.5f) {
            coroutineScope.launch {
                verticalScrollState.scrollTo(
                    (verticalScrollState.value + verticalDelta.roundToInt()).coerceIn(0, verticalScrollState.maxValue),
                )
            }
        }
    }

    Box(
        modifier =
            modifier
                .border(1.dp, RangePickerGridColor, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(Color.White, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .padding(10.dp),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(modifier = Modifier.fillMaxWidth()) {
                SpreadsheetCornerHeader()
                Box(
                    modifier =
                        Modifier
                            .weight(1f)
                            .onSizeChanged { columnHeaderViewportSize = it }
                            .pointerInput(sheet) {
                                var dragAnchor: Int? = null
                                detectDragGestures(
                                    onDragStart = { offset ->
                                        resolveColumnIndex(offset.x, clampToBounds = false)?.let { columnIndex ->
                                            dragAnchor = columnIndex
                                            sheet.columnSelectionRange(columnIndex, columnIndex)?.let(onSelectionChange)
                                        }
                                    },
                                    onDrag = { change, _ ->
                                        val anchor = dragAnchor ?: return@detectDragGestures
                                        scrollHorizontalByPointer(change.position.x, columnHeaderViewportSize.width.toFloat())
                                        resolveColumnIndex(change.position.x, clampToBounds = true)?.let { columnIndex ->
                                            sheet.columnSelectionRange(anchor, columnIndex)?.let(onSelectionChange)
                                        }
                                        change.consume()
                                    },
                                    onDragEnd = { dragAnchor = null },
                                    onDragCancel = { dragAnchor = null },
                                )
                            },
                ) {
                    Box(modifier = Modifier.horizontalScroll(horizontalScrollState, enabled = false).width(totalGridWidth)) {
                        Row(modifier = Modifier.offset(x = visibleContentOffsetX)) {
                            visibleColumnIndices.forEach { columnIndex ->
                                SpreadsheetColumnHeader(
                                    columnIndex = columnIndex,
                                    onClick = {
                                        sheet.columnSelectionRange(columnIndex, columnIndex)?.let(onSelectionChange)
                                    },
                                )
                            }
                        }
                    }
                }
            }
            Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
                Box(
                    modifier =
                        Modifier
                            .width(RangePickerRowHeaderWidth)
                            .fillMaxHeight()
                            .onSizeChanged { rowHeaderViewportSize = it }
                            .pointerInput(sheet) {
                                var dragAnchor: Int? = null
                                detectDragGestures(
                                    onDragStart = { offset ->
                                        resolveRowIndex(offset.y, clampToBounds = false)?.let { rowIndex ->
                                            dragAnchor = rowIndex
                                            sheet.rowSelectionRange(rowIndex, rowIndex)?.let(onSelectionChange)
                                        }
                                    },
                                    onDrag = { change, _ ->
                                        val anchor = dragAnchor ?: return@detectDragGestures
                                        scrollVerticalByPointer(change.position.y, rowHeaderViewportSize.height.toFloat())
                                        resolveRowIndex(change.position.y, clampToBounds = true)?.let { rowIndex ->
                                            sheet.rowSelectionRange(anchor, rowIndex)?.let(onSelectionChange)
                                        }
                                        change.consume()
                                    },
                                    onDragEnd = { dragAnchor = null },
                                    onDragCancel = { dragAnchor = null },
                                )
                            },
                ) {
                    Box(modifier = Modifier.verticalScroll(verticalScrollState, enabled = false).height(totalGridHeight)) {
                        Column(modifier = Modifier.offset(y = visibleContentOffsetY)) {
                            visibleRowIndices.forEach { rowIndex ->
                                SpreadsheetRowHeader(
                                    rowIndex = rowIndex,
                                    onClick = {
                                        sheet.rowSelectionRange(rowIndex, rowIndex)?.let(onSelectionChange)
                                    },
                                )
                            }
                        }
                    }
                }
                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .onSizeChanged { bodyViewportSize = it }
                                .pointerInput(sheet) {
                                    var dragAnchor: GridCellCoordinate? = null
                                    detectDragGestures(
                                        onDragStart = { offset ->
                                            resolveCell(offset, clampToBounds = false)?.let { cell ->
                                                dragAnchor = cell
                                                onSelectionChange(GridCellRange(anchor = cell, active = cell))
                                            }
                                        },
                                        onDrag = { change, _ ->
                                            val anchor = dragAnchor ?: return@detectDragGestures
                                            scrollHorizontalByPointer(change.position.x, bodyViewportSize.width.toFloat())
                                            scrollVerticalByPointer(change.position.y, bodyViewportSize.height.toFloat())
                                            resolveCell(change.position, clampToBounds = true)?.let { cell ->
                                                onSelectionChange(GridCellRange(anchor = anchor, active = cell))
                                            }
                                            change.consume()
                                        },
                                        onDragEnd = { dragAnchor = null },
                                        onDragCancel = { dragAnchor = null },
                                    )
                                },
                    ) {
                        Box(
                            modifier =
                                Modifier
                                    .horizontalScroll(horizontalScrollState, enabled = false)
                                    .verticalScroll(verticalScrollState, enabled = false)
                                    .width(totalGridWidth)
                                    .height(totalGridHeight),
                        ) {
                            Column(modifier = Modifier.offset(x = visibleContentOffsetX, y = visibleContentOffsetY)) {
                                visibleRowIndices.forEach { rowIndex ->
                                    Row {
                                        visibleColumnIndices.forEach { columnIndex ->
                                            SpreadsheetGridCell(
                                                value = sheet.rows.getOrNull(rowIndex)?.getOrNull(columnIndex).orEmpty(),
                                                selected = selectedRange?.contains(rowIndex, columnIndex) == true,
                                                onClick = {
                                                    val cell = GridCellCoordinate(rowIndex = rowIndex, columnIndex = columnIndex)
                                                    onSelectionChange(GridCellRange(anchor = cell, active = cell))
                                                },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        RangePickerScrollSlider(
                            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(RangePickerSliderThickness),
                            axis = RangePickerScrollAxis.VERTICAL,
                            scrollFraction = verticalScrollState.progressFraction(),
                            visibleFraction = verticalScrollState.visibleFraction(),
                            onFractionChange = { fraction ->
                                coroutineScope.launch {
                                    verticalScrollState.scrollTo((fraction * verticalScrollState.maxValue).roundToInt())
                                }
                            },
                        )
                    }
                }
            }
            RangePickerScrollSlider(
                modifier = Modifier.fillMaxWidth().height(RangePickerSliderThickness).padding(start = RangePickerRowHeaderWidth + 6.dp, end = 6.dp, top = 8.dp),
                axis = RangePickerScrollAxis.HORIZONTAL,
                scrollFraction = horizontalScrollState.progressFraction(),
                visibleFraction = horizontalScrollState.visibleFraction(),
                onFractionChange = { fraction ->
                    coroutineScope.launch {
                        horizontalScrollState.scrollTo((fraction * horizontalScrollState.maxValue).roundToInt())
                    }
                },
            )
        }
    }
}

@Composable
private fun SpreadsheetSheetToolbar(
    sheetNames: List<String>,
    activeSheetIndex: Int,
    onSelectSheet: (Int) -> Unit,
) {
    Toolbar(content = {
        sheetNames.forEachIndexed { index, sheetName ->
            val isSelected = activeSheetIndex == index
            Button(
                modifier =
                    Modifier
                        .widthIn(min = 88.dp, max = 144.dp)
                        .tooltip(
                            text = sheetName,
                            description = "Switch to this table.",
                        ),
                onClick = { onSelectSheet(index) },
                colors = flatButtonColors(selected = isSelected),
            ) {
                Text(
                    text = sheetName,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                )
            }
        }
    })
}

@Composable
private fun RangePickerScrollSlider(
    axis: RangePickerScrollAxis,
    scrollFraction: Float,
    visibleFraction: Float,
    onFractionChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val minThumbLengthPx = with(density) { RangePickerSliderMinThumbLength.toPx() }
    var trackSize by remember(axis) { mutableStateOf(IntSize.Zero) }

    fun updateFromOffset(offset: Offset) {
        val axisLength = if (axis == RangePickerScrollAxis.HORIZONTAL) trackSize.width.toFloat() else trackSize.height.toFloat()
        if (axisLength <= 0f) return
        val thumbLength = (axisLength * visibleFraction.coerceIn(0.08f, 1f)).coerceAtLeast(minThumbLengthPx).coerceAtMost(axisLength)
        val draggableLength = (axisLength - thumbLength).coerceAtLeast(1f)
        val pointerPosition = if (axis == RangePickerScrollAxis.HORIZONTAL) offset.x else offset.y
        val thumbStart = (pointerPosition - (thumbLength / 2f)).coerceIn(0f, draggableLength)
        onFractionChange((thumbStart / draggableLength).coerceIn(0f, 1f))
    }

    Box(
        modifier =
            modifier
                .background(RangePickerHeaderColor, RoundedCornerShape(999.dp))
                .onSizeChanged { trackSize = it }
                .pointerInput(axis, visibleFraction) {
                    detectDragGestures(
                        onDragStart = { offset -> updateFromOffset(offset) },
                        onDrag = { change, _ ->
                            updateFromOffset(change.position)
                            change.consume()
                        },
                    )
                },
    ) {
        val axisLength = if (axis == RangePickerScrollAxis.HORIZONTAL) trackSize.width.toFloat() else trackSize.height.toFloat()
        val thumbLength = (axisLength * visibleFraction.coerceIn(0.08f, 1f)).coerceAtLeast(minThumbLengthPx).coerceAtMost(axisLength)
        val draggableLength = (axisLength - thumbLength).coerceAtLeast(0f)
        val thumbOffset = draggableLength * scrollFraction.coerceIn(0f, 1f)

        Box(
            modifier =
                if (axis == RangePickerScrollAxis.HORIZONTAL) {
                    Modifier
                        .offset(x = with(density) { thumbOffset.toDp() })
                        .fillMaxHeight()
                        .width(with(density) { thumbLength.toDp() })
                } else {
                    Modifier
                        .offset(y = with(density) { thumbOffset.toDp() })
                        .fillMaxWidth()
                        .height(with(density) { thumbLength.toDp() })
                }
                    .background(AccentColor, RoundedCornerShape(999.dp)),
        )
    }
}

@Composable
private fun SpreadsheetCornerHeader() {
    Box(
        modifier =
            Modifier
                .width(RangePickerRowHeaderWidth)
                .height(RangePickerColumnHeaderHeight)
                .background(RangePickerHeaderColor)
                .border(0.5.dp, RangePickerGridColor),
    )
}

@Composable
private fun SpreadsheetColumnHeader(columnIndex: Int, onClick: () -> Unit) {
    Box(
        modifier =
            Modifier
                .width(RangePickerCellWidth)
                .height(RangePickerColumnHeaderHeight)
                .background(RangePickerHeaderColor)
                .border(0.5.dp, RangePickerGridColor),
        contentAlignment = Alignment.Center,
    ) {
        Box(modifier = Modifier.fillMaxSize().clickable(onClick = onClick), contentAlignment = Alignment.Center) {
            Text(columnIndexToExcelLabel(columnIndex), color = PrimaryTextColor, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun SpreadsheetRowHeader(rowIndex: Int, onClick: () -> Unit) {
    Box(
        modifier =
            Modifier
                .width(RangePickerRowHeaderWidth)
                .height(RangePickerCellHeight)
                .background(RangePickerHeaderColor)
                .border(0.5.dp, RangePickerGridColor),
        contentAlignment = Alignment.Center,
    ) {
        Box(modifier = Modifier.fillMaxSize().clickable(onClick = onClick), contentAlignment = Alignment.Center) {
            Text((rowIndex + 1).toString(), color = PrimaryTextColor, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun SpreadsheetGridCell(
    value: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .width(RangePickerCellWidth)
                .height(RangePickerCellHeight)
                .background(if (selected) RangePickerSelectionFill else Color.White)
                .border(if (selected) 1.5.dp else 0.5.dp, if (selected) RangePickerSelectionBorder else RangePickerGridColor)
                .clickable(onClick = onClick)
                .padding(horizontal = 8.dp, vertical = 6.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = value.ifBlank { " " },
            color = PrimaryTextColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun PanelTitle(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = PrimaryTextColor)
        Text(subtitle, color = SecondaryTextColor)
    }
}

@Composable
private fun PanelContainer(content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(PanelBackgroundColor)
                .padding(18.dp),
        content = content,
    )
}

@Composable
private fun SectionCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .border(1.dp, CardBorderColor, RoundedCornerShape(20.dp))
                .background(CardBackgroundColor, RoundedCornerShape(20.dp))
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

@Composable
private fun ToolbarIconButton(
    iconRes: Int,
    tooltipTitle: String,
    tooltipDescription: String,
    onClick: () -> Unit,
) {
    IconButton(
        modifier = Modifier.tooltip(text = tooltipTitle, description = tooltipDescription),
        onClick = onClick,
        colors = IconButtonDefaults.iconButtonColors(containerColor = Color.Transparent),
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = tooltipTitle,
        )
    }
}

@Composable
private fun LoadingOverlay(message: String) {
    Box(
        modifier = Modifier.fillMaxSize().background(Color(0x55FFFFFF)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier =
                Modifier
                    .border(1.dp, CardBorderColor, RoundedCornerShape(24.dp))
                    .background(PanelBackgroundColor, RoundedCornerShape(24.dp))
                    .padding(horizontal = 28.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            CircularProgressIndicator()
            Text(message, color = PrimaryTextColor)
        }
    }
}

@Composable
private fun StatusPill(
    message: String,
    background: Color,
    textColor: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .background(background, RoundedCornerShape(999.dp))
                .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(message, color = textColor)
    }
}

@Composable
private fun flatButtonColors(selected: Boolean) = ButtonDefaults.buttonColors(
    containerColor = if (selected) AccentColor else AccentTintColor,
    contentColor = if (selected) Color.White else PrimaryTextColor,
)

private fun InspectorPanel?.toggle(target: InspectorPanel): InspectorPanel? {
    return if (this == target) null else target
}

private fun currentAxisSelectionLabel(
    selection: AxisSourceSelection,
    resolvedRange: SheetRangeReference?,
): String {
    return when (selection.type) {
        AxisSourceType.NONE -> "no selection made"
        AxisSourceType.RANGE -> resolvedRange?.displayLabel()
            ?: selection.rangeExpression.ifBlank { "no selection made" }
        AxisSourceType.ROW_INDEX -> AxisSourceType.ROW_INDEX.label
        AxisSourceType.COLUMN_INDEX -> AxisSourceType.COLUMN_INDEX.label
    }
}

private fun currentColorSourceSelectionLabel(
    colorSource: ColorSourceSelection,
    resolvedColorSelection: ResolvedColorSelection,
): String {
    return when (colorSource.type) {
        ColorSourceType.AUTOMATIC -> ColorSourceType.AUTOMATIC.label
        ColorSourceType.RANGE -> resolvedColorSelection.colorRange?.displayLabel()
            ?: colorSource.rangeExpression.ifBlank { "no selection made" }
    }
}

private fun VisualizationSettings.resetColorSourceToAutomaticIfNeeded(): VisualizationSettings {
    return if (colorSource.type == ColorSourceType.RANGE) {
        copy(colorSource = ColorSourceSelection())
    } else {
        this
    }
}

private fun SpreadsheetSheet.columnSelectionRange(startColumnIndex: Int, endColumnIndex: Int): GridCellRange? {
    if (rowCount <= 0 || columnCount <= 0) return null
    val startColumn = minOf(startColumnIndex, endColumnIndex).coerceIn(0, columnCount - 1)
    val endColumn = maxOf(startColumnIndex, endColumnIndex).coerceIn(0, columnCount - 1)
    val maxRow = (startColumn..endColumn)
        .maxOfOrNull { columnIndex -> lastNonEmptyRowIndex(columnIndex) }
        ?.coerceAtLeast(0)
        ?: 0
    return GridCellRange(
        anchor = GridCellCoordinate(rowIndex = 0, columnIndex = startColumn),
        active = GridCellCoordinate(rowIndex = maxRow, columnIndex = endColumn),
    )
}

private fun SpreadsheetSheet.rowSelectionRange(startRowIndex: Int, endRowIndex: Int): GridCellRange? {
    if (rowCount <= 0 || columnCount <= 0) return null
    val startRow = minOf(startRowIndex, endRowIndex).coerceIn(0, rowCount - 1)
    val endRow = maxOf(startRowIndex, endRowIndex).coerceIn(0, rowCount - 1)
    val maxColumn = (startRow..endRow)
        .maxOfOrNull { rowIndex -> lastNonEmptyColumnIndex(rowIndex) }
        ?.coerceAtLeast(0)
        ?: 0
    return GridCellRange(
        anchor = GridCellCoordinate(rowIndex = startRow, columnIndex = 0),
        active = GridCellCoordinate(rowIndex = endRow, columnIndex = maxColumn),
    )
}

private fun SpreadsheetSheet.lastNonEmptyRowIndex(columnIndex: Int): Int {
    if (rowCount <= 0) return 0
    return rows.indexOfLast { row ->
        row.getOrNull(columnIndex)?.isNotBlank() == true
    }.takeIf { it >= 0 } ?: (rows.indexOfLast { row -> row.size > columnIndex }.takeIf { it >= 0 } ?: 0)
}

private fun SpreadsheetSheet.lastNonEmptyColumnIndex(rowIndex: Int): Int {
    val row = rows.getOrNull(rowIndex) ?: return 0
    return row.indexOfLast { it.isNotBlank() }.takeIf { it >= 0 } ?: (row.lastIndex.takeIf { it >= 0 } ?: 0)
}

private fun GridCellRange.toSheetRangeReference(sheetName: String): SheetRangeReference {
    return SheetRangeReference(
        sheetName = sheetName,
        startRow = startRow,
        endRow = endRow,
        startColumn = startColumn,
        endColumn = endColumn,
    )
}

private fun columnIndexToExcelLabel(index: Int): String {
    var working = index.coerceAtLeast(0)
    val builder = StringBuilder()
    do {
        builder.append(('A'.code + (working % 26)).toChar())
        working = (working / 26) - 1
    } while (working >= 0)
    return builder.reverse().toString()
}

private fun buildDocumentSummary(document: SpreadsheetDocument): String {
    if (document.sheets.isEmpty()) return "No sheets found."
    return document.sheets.joinToString(separator = " • ") { sheet ->
        "${sheet.name} (${sheet.rowCount}×${sheet.columnCount})"
    }
}

private fun rangeFieldHelpText(document: SpreadsheetDocument?): String {
    return if (document == null || document.sheets.isEmpty()) {
        "Open a spreadsheet to see available sheet names."
    } else {
        val examples = document.sheets.take(3).joinToString { "${it.name}!A1:C8" }
        "Examples: $examples"
    }
}

private fun emptyRenderRequest(
    visualizationMethod: VisualizationMethod,
    visualizationSettings: VisualizationSettings,
    label: String,
): RenderRequest {
    return RenderRequest(
        points = emptyList(),
        visualizationMethod = visualizationMethod,
        visualizationSettings = visualizationSettings,
        interpretationMethod = InterpretationMethod.GRID_HEIGHT_MAP,
        sheetName = label,
    )
}

private fun trimFloatText(value: Float): String {
    return value.toString().removeSuffix(".0")
}

private fun androidx.compose.foundation.ScrollState.progressFraction(): Float {
    return if (maxValue <= 0) 0f else value.toFloat() / maxValue.toFloat()
}

private fun androidx.compose.foundation.ScrollState.visibleFraction(): Float {
    val totalSize = viewportSize + maxValue
    return if (totalSize <= 0) 1f else viewportSize.toFloat() / totalSize.toFloat()
}
