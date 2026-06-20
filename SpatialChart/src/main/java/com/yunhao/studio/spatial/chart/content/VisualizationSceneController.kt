package com.yunhao.studio.spatial.chart.content

import com.pico.spatial.core.container.SpatialViewContent
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.ModelComponent
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.ecs.resource.BlendingMode
import com.pico.spatial.core.ecs.resource.Material
import com.pico.spatial.core.ecs.resource.MeshModel
import com.pico.spatial.core.ecs.resource.MeshResource
import com.pico.spatial.core.ecs.resource.PhysicallyBasedMaterial
import com.pico.spatial.core.ecs.resource.Resource
import com.pico.spatial.core.math.Color4
import com.pico.spatial.core.math.EulerAngles
import com.pico.spatial.core.math.Vector2
import com.pico.spatial.core.math.Vector3
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

private const val CHART_WIDTH = 1.1f
private const val CHART_DEPTH = 1.1f
private const val CHART_HEIGHT = 0.72f
private const val CHART_FLOOR_Y = -0.34f
private const val GRID_DIVISIONS = 8
private const val COLUMN_MAX_POINTS = 144
private const val POINT_CLOUD_MAX_POINTS = 196
private const val MANIFOLD_MAX_POINTS = 100
private const val MANIFOLD_MIN_SAMPLES = 12
private const val MANIFOLD_MAX_SAMPLES = 24
private const val MAX_AXIS_VISUAL_SCALE = 4f

data class RenderRequest(
    val points: List<DataPoint3D>,
    val visualizationMethod: VisualizationMethod,
    val visualizationSettings: VisualizationSettings,
    val interpretationMethod: InterpretationMethod,
    val sheetName: String,
)

data class ViewTransformSettings(
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val offsetZ: Float = 0f,
    val pitchDegrees: Float = 0f,
    val yawDegrees: Float = 0f,
    val rollDegrees: Float = 0f,
    val uniformScale: Float = 1f,
)

data class ResolvedColorSelection(
    val colorRange: SheetRangeReference? = null,
    val errorMessage: String? = null,
)

data class RenderBuildResult(
    val request: RenderRequest,
    val resolvedSelection: ResolvedDataSelection,
    val resolvedColorSelection: ResolvedColorSelection,
)

class VisualizationSceneController {
    private val chartRoot = Entity().apply { setName("SpreadsheetChartRoot") }
    private val frameRoot = Entity().apply { setName("SpreadsheetFrameRoot") }
    private val dataRoot = Entity().apply { setName("SpreadsheetDataRoot") }
    private val persistentResources = mutableListOf<Resource>()
    private val transientResources = mutableListOf<Resource>()
    private var attached = false
    private var frameBuilt = false
    private var lastRenderRequest: RenderRequest? = null
    private var lastViewTransform: ViewTransformSettings? = null

    fun initialize(content: SpatialViewContent) {
        if (!attached) {
            chartRoot.addChild(frameRoot)
            chartRoot.addChild(dataRoot)
            content.addEntity(chartRoot)
            attached = true
        }
        if (!frameBuilt) {
            buildBaseFrame()
            frameBuilt = true
        }
    }

    fun dispose() {
        chartRoot.destroy(true)
        clearTransientResources()
        clearPersistentResources()
        attached = false
        frameBuilt = false
        lastRenderRequest = null
        lastViewTransform = null
    }

    fun updateViewTransform(viewTransform: ViewTransformSettings) {
        if (viewTransform == lastViewTransform) return
        val transform = chartRoot.components[TransformComponent::class.java] ?: TransformComponent().also(chartRoot.components::set)
        transform.position = Vector3(viewTransform.offsetX, viewTransform.offsetY, viewTransform.offsetZ)
        transform.eulerAngles = EulerAngles(viewTransform.pitchDegrees, viewTransform.yawDegrees, viewTransform.rollDegrees)
        val scale = viewTransform.uniformScale.coerceIn(0.35f, 3.5f)
        transform.scaleVector = Vector3(scale, scale, scale)
        lastViewTransform = viewTransform
    }

    fun render(request: RenderRequest) {
        if (request == lastRenderRequest) return

        val optimizedRequest = request.copy(
            points = downsamplePoints(request.points, maxPointsFor(request.visualizationMethod)),
        )

        dataRoot.removeAllChildren()
        clearTransientResources()

        if (optimizedRequest.points.isEmpty()) {
            lastRenderRequest = request
            return
        }

        when (optimizedRequest.visualizationMethod) {
            VisualizationMethod.COLUMN -> buildColumns(optimizedRequest)
            VisualizationMethod.MANIFOLD -> buildManifold(optimizedRequest)
            VisualizationMethod.POINT_CLOUD -> buildPointCloud(optimizedRequest)
        }

        lastRenderRequest = request
    }

    private fun buildBaseFrame() {
        val base = primitiveEntity(
            mesh = rememberPersistentMesh(MeshResource.createBox(Vector3(CHART_WIDTH, 0.015f, CHART_DEPTH), 0f)),
            material = pbrMaterial(
                color = Color4(0.10f, 0.16f, 0.28f, 0.32f),
                blendingMode = BlendingMode.TRANSPARENT,
                roughness = 0.86f,
                metallic = 0.02f,
                persistent = true,
            ),
            position = Vector3(0f, CHART_FLOOR_Y - 0.01f, 0f),
        )
        frameRoot.addChild(base)

        repeat(GRID_DIVISIONS + 1) { index ->
            val ratio = index.toFloat() / GRID_DIVISIONS.toFloat()
            val x = -CHART_WIDTH / 2f + (ratio * CHART_WIDTH)
            val z = -CHART_DEPTH / 2f + (ratio * CHART_DEPTH)
            val isMajor = index == 0 || index == GRID_DIVISIONS || index == GRID_DIVISIONS / 2
            val thickness = if (isMajor) 0.007f else 0.0035f
            val alpha = if (isMajor) 0.80f else 0.35f

            frameRoot.addChild(
                primitiveEntity(
                    mesh = rememberPersistentMesh(
                        MeshResource.createBox(Vector3(thickness, 0.003f, CHART_DEPTH), 0f),
                    ),
                    material = pbrMaterial(
                        color = Color4(0.73f, 0.88f, 1.0f, alpha),
                        blendingMode = BlendingMode.TRANSPARENT,
                        roughness = 0.15f,
                        metallic = 0.0f,
                        persistent = true,
                    ),
                    position = Vector3(x, CHART_FLOOR_Y - 0.001f, 0f),
                ),
            )
            frameRoot.addChild(
                primitiveEntity(
                    mesh = rememberPersistentMesh(
                        MeshResource.createBox(Vector3(CHART_WIDTH, 0.003f, thickness), 0f),
                    ),
                    material = pbrMaterial(
                        color = Color4(0.73f, 0.88f, 1.0f, alpha),
                        blendingMode = BlendingMode.TRANSPARENT,
                        roughness = 0.15f,
                        metallic = 0.0f,
                        persistent = true,
                    ),
                    position = Vector3(0f, CHART_FLOOR_Y - 0.001f, z),
                ),
            )
        }

        val xAxis = primitiveEntity(
            mesh = rememberPersistentMesh(MeshResource.createBox(Vector3(CHART_WIDTH, 0.01f, 0.01f), 0f)),
            material = pbrMaterial(
                color = Color4.fromSRGBHex("#65D5FF"),
                roughness = 0.22f,
                metallic = 0.08f,
                persistent = true,
            ),
            position = Vector3(0f, CHART_FLOOR_Y, -CHART_DEPTH / 2f),
        )
        frameRoot.addChild(xAxis)

        val yAxis = primitiveEntity(
            mesh = rememberPersistentMesh(MeshResource.createBox(Vector3(0.01f, CHART_HEIGHT, 0.01f), 0f)),
            material = pbrMaterial(
                color = Color4.fromSRGBHex("#FEC84B"),
                roughness = 0.24f,
                metallic = 0.1f,
                persistent = true,
            ),
            position = Vector3(-CHART_WIDTH / 2f, CHART_FLOOR_Y + CHART_HEIGHT / 2f, -CHART_DEPTH / 2f),
        )
        frameRoot.addChild(yAxis)

        val zAxis = primitiveEntity(
            mesh = rememberPersistentMesh(MeshResource.createBox(Vector3(0.01f, 0.01f, CHART_DEPTH), 0f)),
            material = pbrMaterial(
                color = Color4.fromSRGBHex("#89E780"),
                roughness = 0.24f,
                metallic = 0.1f,
                persistent = true,
            ),
            position = Vector3(-CHART_WIDTH / 2f, CHART_FLOOR_Y, 0f),
        )
        frameRoot.addChild(zAxis)
    }

    private fun buildColumns(request: RenderRequest) {
        val cellSize = estimatePointFootprint(request.points)
        request.points.forEach { point ->
            val height = max(point.z, 0.01f)
            val color = pointColor(request.visualizationSettings.colorScheme, point)
            val mesh = when (request.visualizationSettings.columnGeometry) {
                ColumnGeometry.BOX -> {
                    rememberTransientMesh(MeshResource.createBox(Vector3(cellSize, height, cellSize), 0f))
                }
                ColumnGeometry.CYLINDER -> {
                    rememberTransientMesh(MeshResource.createCylinder(height, cellSize / 2f))
                }
            }
            val entity = primitiveEntity(
                mesh = mesh,
                material = pbrMaterial(
                    color = color,
                    roughness = 0.28f,
                    metallic = 0.14f,
                ),
                position = Vector3(point.x, CHART_FLOOR_Y + height / 2f, point.y),
            )
            dataRoot.addChild(entity)
        }
    }

    private fun buildPointCloud(request: RenderRequest) {
        val radius = request.visualizationSettings.pointRadius.coerceIn(0.006f, 0.04f)
        val pointMesh = rememberTransientMesh(MeshResource.createSphere(radius))
        request.points.forEach { point ->
            val entity = primitiveEntity(
                mesh = pointMesh,
                material = pbrMaterial(
                    color = pointColor(request.visualizationSettings.colorScheme, point),
                    roughness = 0.16f,
                    metallic = 0.18f,
                ),
                position = Vector3(point.x, CHART_FLOOR_Y + point.z, point.y),
            )
            dataRoot.addChild(entity)
        }
    }

    private fun buildManifold(request: RenderRequest) {
        val manifold = primitiveEntity(
            mesh = buildManifoldMesh(request),
            material = pbrMaterial(
                color = Color4(1f, 1f, 1f, 1f),
                roughness = 0.42f,
                metallic = 0.08f,
            ),
            position = Vector3(0f, 0f, 0f),
        )
        dataRoot.addChild(manifold)
    }

    private fun buildManifoldMesh(request: RenderRequest): MeshResource {
        val samplesPerAxis = determineManifoldSamples(request.points.size)
        val heights = Array(samplesPerAxis) { FloatArray(samplesPerAxis) }
        val positions = ArrayList<Vector3>(samplesPerAxis * samplesPerAxis)
        val normals = ArrayList<Vector3>(samplesPerAxis * samplesPerAxis)
        val uv0 = ArrayList<Vector2>(samplesPerAxis * samplesPerAxis)
        val colors = ArrayList<Color4>(samplesPerAxis * samplesPerAxis)

        for (xIndex in 0 until samplesPerAxis) {
            for (yIndex in 0 until samplesPerAxis) {
                val xRatio = xIndex.toFloat() / (samplesPerAxis - 1).coerceAtLeast(1).toFloat()
                val yRatio = yIndex.toFloat() / (samplesPerAxis - 1).coerceAtLeast(1).toFloat()
                val worldX = -CHART_WIDTH / 2f + (xRatio * CHART_WIDTH)
                val worldY = -CHART_DEPTH / 2f + (yRatio * CHART_DEPTH)
                val height = interpolateHeight(
                    request.points,
                    worldX,
                    worldY,
                    request.visualizationSettings.interpolationMethod,
                )
                heights[xIndex][yIndex] = height
                positions += Vector3(worldX, CHART_FLOOR_Y + height, worldY)
                uv0 += Vector2(xRatio, yRatio)
                colors += scalarSchemeColor(request.visualizationSettings.colorScheme, ((xRatio + yRatio) * 0.5f).coerceIn(0f, 1f))
            }
        }

        val xStep = CHART_WIDTH / (samplesPerAxis - 1).coerceAtLeast(1).toFloat()
        val yStep = CHART_DEPTH / (samplesPerAxis - 1).coerceAtLeast(1).toFloat()
        for (xIndex in 0 until samplesPerAxis) {
            for (yIndex in 0 until samplesPerAxis) {
                val leftHeight = heights[max(xIndex - 1, 0)][yIndex]
                val rightHeight = heights[min(xIndex + 1, samplesPerAxis - 1)][yIndex]
                val backHeight = heights[xIndex][max(yIndex - 1, 0)]
                val frontHeight = heights[xIndex][min(yIndex + 1, samplesPerAxis - 1)]
                normals += normalize(
                    Vector3(
                        (leftHeight - rightHeight) / (2f * xStep.coerceAtLeast(0.0001f)),
                        1f,
                        (backHeight - frontHeight) / (2f * yStep.coerceAtLeast(0.0001f)),
                    ),
                )
            }
        }

        val triangleIndices = ArrayList<Int>((samplesPerAxis - 1) * (samplesPerAxis - 1) * 6)
        fun vertexIndex(xIndex: Int, yIndex: Int): Int = (xIndex * samplesPerAxis) + yIndex

        for (xIndex in 0 until samplesPerAxis - 1) {
            for (yIndex in 0 until samplesPerAxis - 1) {
                val topLeft = vertexIndex(xIndex, yIndex)
                val topRight = vertexIndex(xIndex + 1, yIndex)
                val bottomLeft = vertexIndex(xIndex, yIndex + 1)
                val bottomRight = vertexIndex(xIndex + 1, yIndex + 1)
                triangleIndices += topLeft
                triangleIndices += bottomLeft
                triangleIndices += topRight
                triangleIndices += topRight
                triangleIndices += bottomLeft
                triangleIndices += bottomRight
            }
        }

        return rememberTransientMesh(
            MeshResource.createWithMeshModel(
                model = MeshModel(
                    positions = positions,
                    triangleIndices = triangleIndices,
                    normals = normals,
                    uv0 = uv0,
                    colors = colors,
                ),
                name = "SpreadsheetManifoldSurface",
            ),
        )
    }

    private fun determineManifoldSamples(pointCount: Int): Int {
        val suggestedSamples = (sqrt(pointCount.coerceAtLeast(1).toFloat()) * 2.1f).toInt()
        return suggestedSamples.coerceIn(MANIFOLD_MIN_SAMPLES, MANIFOLD_MAX_SAMPLES)
    }

    private fun interpolateHeight(
        points: List<DataPoint3D>,
        x: Float,
        y: Float,
        method: SurfaceInterpolationMethod,
    ): Float {
        var nearestPoint = points.first()
        var nearestDistance = Float.MAX_VALUE
        var weightedValue = 0f
        var weightSum = 0f
        points.forEach { point ->
            val distance = max(0.0001f, distance(x, y, point.x, point.y))
            if (distance < nearestDistance) {
                nearestDistance = distance
                nearestPoint = point
            }
            val weight = when (method) {
                SurfaceInterpolationMethod.LINEAR_BLEND -> 1f / distance
                SurfaceInterpolationMethod.PARABOLIC_BLEND -> 1f / distance.pow(2)
                SurfaceInterpolationMethod.NEAREST_NEIGHBOR -> 0f
            }
            weightedValue += point.z * weight
            weightSum += weight
        }
        return when (method) {
            SurfaceInterpolationMethod.NEAREST_NEIGHBOR -> nearestPoint.z
            else -> weightedValue / weightSum.coerceAtLeast(0.0001f)
        }
    }

    private fun estimatePointFootprint(points: List<DataPoint3D>): Float {
        val footprint = if (points.isEmpty()) {
            0.08f
        } else {
            min(CHART_WIDTH, CHART_DEPTH) / max(4f, sqrt(points.size.toFloat()) * 1.8f)
        }
        return footprint.coerceIn(0.03f, 0.12f)
    }

    private fun maxPointsFor(method: VisualizationMethod): Int = when (method) {
        VisualizationMethod.COLUMN -> COLUMN_MAX_POINTS
        VisualizationMethod.POINT_CLOUD -> POINT_CLOUD_MAX_POINTS
        VisualizationMethod.MANIFOLD -> MANIFOLD_MAX_POINTS
    }

    private fun downsamplePoints(points: List<DataPoint3D>, maxPoints: Int): List<DataPoint3D> {
        if (points.size <= maxPoints) return points

        return List(maxPoints) { index ->
            val ratio = if (maxPoints == 1) 0f else index.toFloat() / (maxPoints - 1).toFloat()
            val sampledIndex = (ratio * points.lastIndex).toInt().coerceIn(0, points.lastIndex)
            points[sampledIndex]
        }
    }

    private fun primitiveEntity(
        mesh: MeshResource,
        material: Material,
        position: Vector3,
    ): Entity {
        return Entity().apply {
            val transform = components[TransformComponent::class.java] ?: TransformComponent().also(components::set)
            transform.position = position
            transform.eulerAngles = com.pico.spatial.core.math.EulerAngles(0f, 0f, 0f)
            transform.scaleVector = Vector3.ONE
            components.set(ModelComponent(mesh, material))
        }
    }

    private fun pbrMaterial(
        color: Color4,
        blendingMode: BlendingMode = BlendingMode.OPAQUE,
        roughness: Float = 0.32f,
        metallic: Float = 0.1f,
        persistent: Boolean = false,
    ): Material {
        val material = PhysicallyBasedMaterial.create(blendingMode)
        material.setBaseColor(color)
        material.setRoughness(roughness)
        material.setMetallic(metallic)
        rememberResource(material, persistent)
        return material
    }

    private fun rememberPersistentMesh(mesh: MeshResource): MeshResource = mesh.also {
        rememberResource(it, persistent = true)
    }

    private fun rememberTransientMesh(mesh: MeshResource): MeshResource = mesh.also {
        rememberResource(it, persistent = false)
    }

    private fun rememberResource(resource: Resource, persistent: Boolean) {
        if (persistent) {
            persistentResources += resource
        } else {
            transientResources += resource
        }
    }

    private fun clearPersistentResources() {
        persistentResources.forEach { runCatching { it.close() } }
        persistentResources.clear()
    }

    private fun clearTransientResources() {
        transientResources.forEach { runCatching { it.close() } }
        transientResources.clear()
    }

    private fun pointColor(scheme: ColorScheme, point: DataPoint3D): Color4 {
        return point.colorRatio?.let { scalarSchemeColor(scheme, it) }
            ?: scalarSchemeColor(scheme, ((point.xRatio * 0.58f) + (point.yRatio * 0.42f)).coerceIn(0f, 1f))
    }

    private fun scalarSchemeColor(scheme: ColorScheme, ratio: Float): Color4 {
        val stops = when (scheme) {
            ColorScheme.OCEAN -> listOf(
                Color4.fromSRGBHex("#1F4DFF"),
                Color4.fromSRGBHex("#4BD6FF"),
                Color4.fromSRGBHex("#92FFF4"),
            )
            ColorScheme.SUNSET -> listOf(
                Color4.fromSRGBHex("#B63CFF"),
                Color4.fromSRGBHex("#FF4F87"),
                Color4.fromSRGBHex("#FFD166"),
            )
            ColorScheme.FOREST -> listOf(
                Color4.fromSRGBHex("#197278"),
                Color4.fromSRGBHex("#1F8A4C"),
                Color4.fromSRGBHex("#F4D35E"),
            )
            ColorScheme.HEATMAP -> listOf(
                Color4.fromSRGBHex("#2C7BFF"),
                Color4.fromSRGBHex("#00D1FF"),
                Color4.fromSRGBHex("#FFE45E"),
                Color4.fromSRGBHex("#FF7A00"),
                Color4.fromSRGBHex("#FF204E"),
            )
            ColorScheme.RAINBOW -> listOf(
                Color4.fromSRGBHex("#6A00FF"),
                Color4.fromSRGBHex("#005BFF"),
                Color4.fromSRGBHex("#00D084"),
                Color4.fromSRGBHex("#FFE600"),
                Color4.fromSRGBHex("#FF7A00"),
                Color4.fromSRGBHex("#FF006E"),
            )
            ColorScheme.NEON -> listOf(
                Color4.fromSRGBHex("#FF00B8"),
                Color4.fromSRGBHex("#FF7A00"),
                Color4.fromSRGBHex("#00F5FF"),
            )
        }
        return interpolateColorStops(stops, ratio.coerceIn(0f, 1f))
    }

    private fun interpolateColorStops(stops: List<Color4>, ratio: Float): Color4 {
        if (stops.isEmpty()) return Color4(1f, 1f, 1f, 1f)
        if (stops.size == 1) return stops.first()
        val scaled = ratio * (stops.lastIndex)
        val startIndex = scaled.toInt().coerceIn(0, stops.lastIndex - 1)
        val localRatio = (scaled - startIndex).coerceIn(0f, 1f)
        return stops[startIndex].blendWith(stops[startIndex + 1], localRatio)
    }

    private fun distance(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x1 - x2
        val dy = y1 - y2
        return sqrt(dx * dx + dy * dy)
    }

    private fun normalize(vector: Vector3): Vector3 {
        val magnitude = sqrt((vector.x * vector.x) + (vector.y * vector.y) + (vector.z * vector.z))
        if (magnitude < 0.0001f) return Vector3(0f, 1f, 0f)
        return Vector3(vector.x / magnitude, vector.y / magnitude, vector.z / magnitude)
    }
}

fun buildRenderRequest(
    document: SpreadsheetDocument,
    dataSelectionSettings: DataSelectionSettings,
    interpretationSettings: InterpretationSettings,
    visualizationMethod: VisualizationMethod,
    visualizationSettings: VisualizationSettings,
): RenderBuildResult {
    val resolvedSelection = resolveDataSelection(document, dataSelectionSettings)
    val resolvedColorSelection = resolveColorSelection(document, visualizationSettings.colorSource, resolvedSelection.zRange)
    val parsedPoints = buildDataPoints(document, dataSelectionSettings, visualizationSettings.colorSource, resolvedSelection, resolvedColorSelection)
    return RenderBuildResult(
        request = RenderRequest(
            points = mapPointsToScene(parsedPoints, interpretationSettings),
            visualizationMethod = visualizationMethod,
            visualizationSettings = visualizationSettings,
            interpretationMethod = InterpretationMethod.GRID_HEIGHT_MAP,
            sheetName = resolvedSelection.zRange?.sheetName ?: document.displayName,
        ),
        resolvedSelection = resolvedSelection,
        resolvedColorSelection = resolvedColorSelection,
    )
}

fun resolveColorSelection(
    document: SpreadsheetDocument,
    colorSource: ColorSourceSelection,
    zRange: SheetRangeReference?,
): ResolvedColorSelection {
    if (colorSource.type == ColorSourceType.AUTOMATIC) {
        return ResolvedColorSelection()
    }
    if (zRange == null) {
        return ResolvedColorSelection(errorMessage = "Select a valid Z range before choosing a color range.")
    }
    val colorRange = parseSheetRangeReference(colorSource.rangeExpression, document)
    val errorMessage = validateColorSourceSelection(colorSource, colorRange, zRange)
    return ResolvedColorSelection(colorRange = colorRange, errorMessage = errorMessage)
}

fun resolveDataSelection(
    document: SpreadsheetDocument,
    dataSelectionSettings: DataSelectionSettings,
): ResolvedDataSelection {
    val zRange = parseSheetRangeReference(dataSelectionSettings.zRangeExpression, document)
    val zError = when {
        dataSelectionSettings.zRangeExpression.isBlank() -> "Select the values to visualize first."
        zRange == null -> "Enter a valid range like Sheet1!A1:C8."
        else -> null
    }

    if (zRange == null) {
        return ResolvedDataSelection(zError = zError)
    }

    val xRange = when (dataSelectionSettings.xSource.type) {
        AxisSourceType.RANGE -> parseSheetRangeReference(dataSelectionSettings.xSource.rangeExpression, document)
        else -> null
    }
    val yRange = when (dataSelectionSettings.ySource.type) {
        AxisSourceType.RANGE -> parseSheetRangeReference(dataSelectionSettings.ySource.rangeExpression, document)
        else -> null
    }

    val xError = validateAxisSource("X", dataSelectionSettings.xSource, xRange, zRange)
    val yError = validateAxisSource("Y", dataSelectionSettings.ySource, yRange, zRange)

    return ResolvedDataSelection(
        zRange = zRange,
        xRange = xRange,
        yRange = yRange,
        zError = zError,
        xError = xError,
        yError = yError,
    )
}

fun buildDataPoints(
    document: SpreadsheetDocument,
    dataSelectionSettings: DataSelectionSettings,
    colorSource: ColorSourceSelection = ColorSourceSelection(),
    resolvedSelection: ResolvedDataSelection = resolveDataSelection(document, dataSelectionSettings),
    resolvedColorSelection: ResolvedColorSelection = resolveColorSelection(document, colorSource, resolvedSelection.zRange),
): List<DataPoint3D> {
    val zRange = resolvedSelection.zRange ?: return emptyList()
    if (!resolvedSelection.isComplete) return emptyList()

    return buildList {
        for (rowOffset in 0 until zRange.rowCount) {
            for (columnOffset in 0 until zRange.columnCount) {
                val zValue = document.cellValue(
                    sheetName = zRange.sheetName,
                    rowIndex = zRange.startRow + rowOffset,
                    columnIndex = zRange.startColumn + columnOffset,
                )?.toFloatOrNull() ?: continue

                val xValue = resolveAxisValue(
                    document = document,
                    axisSource = dataSelectionSettings.xSource,
                    axisRange = resolvedSelection.xRange,
                    zRange = zRange,
                    rowOffset = rowOffset,
                    columnOffset = columnOffset,
                ) ?: continue

                val yValue = resolveAxisValue(
                    document = document,
                    axisSource = dataSelectionSettings.ySource,
                    axisRange = resolvedSelection.yRange,
                    zRange = zRange,
                    rowOffset = rowOffset,
                    columnOffset = columnOffset,
                ) ?: continue

                val colorMetric = resolveColorValue(
                    document = document,
                    colorSource = colorSource,
                    colorRange = resolvedColorSelection.colorRange,
                    rowOffset = rowOffset,
                    columnOffset = columnOffset,
                )

                add(DataPoint3D(x = xValue, y = yValue, z = zValue, colorMetric = colorMetric))
            }
        }
    }
}

fun mapPointsToScene(
    points: List<DataPoint3D>,
    interpretationSettings: InterpretationSettings,
): List<DataPoint3D> {
    if (points.isEmpty()) return emptyList()

    val minX = points.minOf { it.x }
    val maxX = points.maxOf { it.x }
    val minY = points.minOf { it.y }
    val maxY = points.maxOf { it.y }
    val minZ = points.minOf { it.z }
    val maxZ = points.maxOf { it.z }
    val colorValues = points.mapNotNull { it.colorMetric }
    val minColor = colorValues.minOrNull()
    val maxColor = colorValues.maxOrNull()

    fun mapAxis(
        value: Float,
        minValue: Float,
        maxValue: Float,
        standardize: Boolean,
        scale: Float,
        halfSpan: Float,
    ): Pair<Float, Float> {
        val safeScale = scale.coerceAtLeast(0.0001f)
        val maxSpan = halfSpan * MAX_AXIS_VISUAL_SCALE
        val ratio = if (abs(maxValue - minValue) < 0.0001f) 0.5f else (value - minValue) / (maxValue - minValue)
        return if (standardize) {
            ((((ratio - 0.5f) * 2f * halfSpan) * safeScale)).coerceIn(-maxSpan, maxSpan) to ratio.coerceIn(0f, 1f)
        } else {
            val centered = value - ((maxValue + minValue) / 2f)
            val raw = (centered * 0.08f * safeScale).coerceIn(-maxSpan, maxSpan)
            raw to ratio.coerceIn(0f, 1f)
        }
    }

    fun mapHeight(value: Float): Float {
        val maxHeight = CHART_HEIGHT * MAX_AXIS_VISUAL_SCALE
        val safeScale = interpretationSettings.zScale.coerceAtLeast(0.0001f)
        return if (interpretationSettings.zStandardize) {
            val standardized = if (abs(maxZ - minZ) < 0.0001f) 0.5f else (value - minZ) / (maxZ - minZ)
            (standardized * CHART_HEIGHT * safeScale).coerceIn(0.01f, maxHeight)
        } else {
            (value * 0.08f * safeScale).coerceIn(0.01f, maxHeight)
        }
    }

    return points.map { point ->
        val (sceneX, xRatio) = mapAxis(
            point.x,
            minX,
            maxX,
            interpretationSettings.xStandardize,
            interpretationSettings.xScale,
            CHART_WIDTH / 2f,
        )
        val (sceneY, yRatio) = mapAxis(
            point.y,
            minY,
            maxY,
            interpretationSettings.yStandardize,
            interpretationSettings.yScale,
            CHART_DEPTH / 2f,
        )
        DataPoint3D(
            x = sceneX,
            y = sceneY,
            z = mapHeight(point.z),
            xRatio = xRatio,
            yRatio = yRatio,
            colorMetric = point.colorMetric,
            colorRatio = point.colorMetric?.let { metric ->
                if (minColor == null || maxColor == null || abs(maxColor - minColor) < 0.0001f) {
                    0.5f
                } else {
                    ((metric - minColor) / (maxColor - minColor)).coerceIn(0f, 1f)
                }
            },
        )
    }
}

private fun validateColorSourceSelection(
    colorSource: ColorSourceSelection,
    colorRange: SheetRangeReference?,
    zRange: SheetRangeReference,
): String? {
    return when (colorSource.type) {
        ColorSourceType.AUTOMATIC -> null
        ColorSourceType.RANGE -> when {
            colorSource.rangeExpression.isBlank() -> "Enter a range for the color data source."
            colorRange == null -> "Enter a valid color range like Sheet1!A1:C8."
            colorRange.rowCount != zRange.rowCount || colorRange.columnCount != zRange.columnCount -> {
                "Color range must match the Z range dimensions (${zRange.rowCount}×${zRange.columnCount})."
            }
            else -> null
        }
    }
}

private fun validateAxisSource(
    axisName: String,
    axisSource: AxisSourceSelection,
    axisRange: SheetRangeReference?,
    zRange: SheetRangeReference,
): String? {
    return when (axisSource.type) {
        AxisSourceType.NONE -> "Choose a source for the $axisName axis."
        AxisSourceType.ROW_INDEX,
        AxisSourceType.COLUMN_INDEX,
        -> null
        AxisSourceType.RANGE -> when {
            axisSource.rangeExpression.isBlank() -> "Enter a range for the $axisName axis."
            axisRange == null -> "Enter a valid $axisName range like Sheet1!A1:C8."
            axisRange.rowCount != zRange.rowCount || axisRange.columnCount != zRange.columnCount -> {
                "$axisName range must match the Z range dimensions (${zRange.rowCount}×${zRange.columnCount})."
            }
            else -> null
        }
    }
}

private fun resolveAxisValue(
    document: SpreadsheetDocument,
    axisSource: AxisSourceSelection,
    axisRange: SheetRangeReference?,
    zRange: SheetRangeReference,
    rowOffset: Int,
    columnOffset: Int,
): Float? {
    return when (axisSource.type) {
        AxisSourceType.NONE -> null
        AxisSourceType.ROW_INDEX -> (zRange.startRow + rowOffset + 1).toFloat()
        AxisSourceType.COLUMN_INDEX -> (zRange.startColumn + columnOffset + 1).toFloat()
        AxisSourceType.RANGE -> document.cellValue(
            sheetName = axisRange?.sheetName ?: return null,
            rowIndex = axisRange.startRow + rowOffset,
            columnIndex = axisRange.startColumn + columnOffset,
        )?.toFloatOrNull()
    }
}

private fun resolveColorValue(
    document: SpreadsheetDocument,
    colorSource: ColorSourceSelection,
    colorRange: SheetRangeReference?,
    rowOffset: Int,
    columnOffset: Int,
): Float? {
    return when (colorSource.type) {
        ColorSourceType.AUTOMATIC -> null
        ColorSourceType.RANGE -> document.cellValue(
            sheetName = colorRange?.sheetName ?: return null,
            rowIndex = colorRange.startRow + rowOffset,
            columnIndex = colorRange.startColumn + columnOffset,
        )?.toFloatOrNull() ?: 0f
    }
}

fun parseSheetRangeReference(
    expression: String,
    document: SpreadsheetDocument,
): SheetRangeReference? {
    val trimmed = expression.trim()
    if (trimmed.isBlank()) return null

    val match = RANGE_REFERENCE_REGEX.matchEntire(trimmed) ?: return null
    val quotedSheetName = match.groups[1]?.value
    val plainSheetName = match.groups[2]?.value
    val sheetName = (quotedSheetName ?: plainSheetName)?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val startColumn = excelColumnNameToIndex(match.groups[3]?.value ?: return null)
    val startRow = (match.groups[4]?.value ?: return null).toIntOrNull()?.minus(1) ?: return null
    val endColumn = excelColumnNameToIndex(match.groups[5]?.value ?: return null)
    val endRow = (match.groups[6]?.value ?: return null).toIntOrNull()?.minus(1) ?: return null
    val sheet = document.sheets.firstOrNull { it.name == sheetName } ?: return null

    val normalizedStartRow = min(startRow, endRow)
    val normalizedEndRow = max(startRow, endRow)
    val normalizedStartColumn = min(startColumn, endColumn)
    val normalizedEndColumn = max(startColumn, endColumn)

    if (normalizedStartRow < 0 || normalizedStartColumn < 0) return null
    if (normalizedEndRow >= sheet.rowCount || normalizedEndColumn >= sheet.columnCount) return null

    return SheetRangeReference(
        sheetName = sheetName,
        startRow = normalizedStartRow,
        endRow = normalizedEndRow,
        startColumn = normalizedStartColumn,
        endColumn = normalizedEndColumn,
    )
}

private fun excelColumnNameToIndex(columnName: String): Int {
    var index = 0
    columnName.uppercase().forEach { character ->
        index = (index * 26) + (character.code - 'A'.code + 1)
    }
    return index - 1
}

private fun SpreadsheetDocument.cellValue(
    sheetName: String,
    rowIndex: Int,
    columnIndex: Int,
): String? {
    val sheet = sheets.firstOrNull { it.name == sheetName } ?: return null
    return sheet.rows.getOrNull(rowIndex)?.getOrNull(columnIndex)
}

private val RANGE_REFERENCE_REGEX =
    Regex("""^\s*(?:'([^']+)'|([^'!$]+))\s*[!$]\s*\$?([A-Za-z]+)\$?(\d+)\s*:\s*\$?([A-Za-z]+)\$?(\d+)\s*$""")
