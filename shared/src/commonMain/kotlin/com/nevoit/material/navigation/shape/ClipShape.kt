package com.nevoit.material.navigation.shape

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageBitmapConfig
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.node.invalidateMeasurement
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.toIntSize
import kotlin.math.min

fun Modifier.clipShape(
    shape: Shape,
    cache: Boolean = true,
    enabled: Boolean = true,
): Modifier {
    return then(ClipShapeElement(shape, cache, enabled))
}

private class ClipShapeElement(
    val shape: Shape,
    val cache: Boolean,
    val enabled: Boolean,
) : ModifierNodeElement<ClipShapeNode>() {

    override fun create(): ClipShapeNode {
        return ClipShapeNode(shape, cache, enabled)
    }

    override fun update(node: ClipShapeNode) {
        val wasRectangle = node.shape == RectangleShape
        val isRectangle = shape == RectangleShape
        val layoutChanged = wasRectangle != isRectangle || node.enabled != enabled
        node.shape = shape
        node.cache = cache
        node.enabled = enabled
        node.invalidateDraw()
        if (layoutChanged) {
            node.invalidateMeasurement()
        }
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "g3Clip"
        properties["shape"] = shape
        properties["cache"] = cache
        properties["enabled"] = enabled
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ClipShapeElement) return false
        return shape == other.shape && cache == other.cache && enabled == other.enabled
    }

    override fun hashCode(): Int {
        var result = shape.hashCode()
        result = 31 * result + cache.hashCode()
        result = 31 * result + enabled.hashCode()
        return result
    }
}

private class ClipShapeNode(
    var shape: Shape,
    var cache: Boolean,
    var enabled: Boolean,
) : Modifier.Node(), LayoutModifierNode, DrawModifierNode {

    override val shouldAutoInvalidate: Boolean = false

    private var bitmap: ImageBitmap? = null
    private var bitmapSize: IntSize? = null
    private var bitmapShape: Shape? = null

    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints
    ): MeasureResult {
        val placeable = measurable.measure(constraints)
        return layout(placeable.width, placeable.height) {
            if (enabled && shape == RectangleShape) {
                placeable.placeWithLayer(0, 0) {
                    clip = true
                }
            } else {
                placeable.place(0, 0)
            }
        }
    }

    override fun ContentDrawScope.draw() {
        val shape = shape
        if (!enabled || shape == RectangleShape) {
            drawContent()
            return
        }

        if ((shape is G3RoundedRectangle || shape is G3Capsule) && cache) {
            val cornerMask = getG3CornerMask(shape)
            if (cornerMask == null) {
                drawContent()
                return
            }
            drawWithMaskLayer {
                drawG3CornerMasks(cornerMask)
            }
            return
        }

        val shapeMask =
            if (cache) {
                getCachedShapeMask(shape)
            } else {
                getLocalShapeMask(shape)
            }

        if (shapeMask == null) {
            drawContent()
            return
        }

        drawWithMaskLayer {
            drawImage(
                image = shapeMask,
                blendMode = BlendMode.DstIn
            )
        }
    }

    private inline fun ContentDrawScope.drawWithMaskLayer(
        drawMask: ContentDrawScope.() -> Unit
    ) {
        val canvas = drawContext.canvas
        canvas.saveLayer(Rect(0f, 0f, size.width, size.height), MaskLayerPaint)
        try {
            drawContent()
            drawMask()
        } finally {
            canvas.restore()
        }
    }

    private fun ContentDrawScope.getCachedShapeMask(shape: Shape): ImageBitmap? {
        val intSize = size.toIntSize()
        if (intSize.width <= 0 || intSize.height <= 0) return null
        val key = ShapeCacheKey(intSize, shape)
        return ShapeMaskCache.getOrPut(key) {
            createShapeMask(shape, intSize, layoutDirection, this)
        }
    }

    private fun ContentDrawScope.getLocalShapeMask(shape: Shape): ImageBitmap? {
        val intSize = size.toIntSize()
        if (intSize.width <= 0 || intSize.height <= 0) {
            bitmap = null
            bitmapSize = null
            bitmapShape = null
            return null
        }
        if (bitmapSize != intSize || bitmapShape != shape) {
            bitmapSize = intSize
            bitmapShape = shape
            bitmap = createShapeMask(shape, intSize, layoutDirection, this)
        }
        return bitmap
    }

    private fun ContentDrawScope.getG3CornerMask(shape: G3RoundedRectangularShape): ImageBitmap? {
        val intSize = size.toIntSize()
        if (intSize.width <= 0 || intSize.height <= 0) return null

        val corners = shape.corners(size, layoutDirection, this)
        val radius = corners.topLeft
        if (radius <= 0f) return null

        val mask = createG3CornerMaskPath(intSize.toSize(), radius) ?: return null
        val width = mask.size.width.toInt()
        val height = mask.size.height.toInt()
        if (width <= 0 || height <= 0) return null

        val key = G3CornerMaskCacheKey(IntSize(width, height), radius)
        return G3CornerMaskCache.getOrPut(key) {
            createOutsideCornerMask(mask.path, width, height)
        }
    }
}

private fun DrawScope.drawG3CornerMasks(mask: ImageBitmap) {
    drawImage(
        image = mask,
        blendMode = BlendMode.DstOut
    )

    val canvas = drawContext.canvas

    canvas.save()
    try {
        canvas.translate(size.width, 0f)
        canvas.scale(-1f, 1f)
        drawImage(
            image = mask,
            blendMode = BlendMode.DstOut
        )
    } finally {
        canvas.restore()
    }

    canvas.save()
    try {
        canvas.translate(size.width, size.height)
        canvas.scale(-1f, -1f)
        drawImage(
            image = mask,
            blendMode = BlendMode.DstOut
        )
    } finally {
        canvas.restore()
    }

    canvas.save()
    try {
        canvas.translate(0f, size.height)
        canvas.scale(1f, -1f)
        drawImage(
            image = mask,
            blendMode = BlendMode.DstOut
        )
    } finally {
        canvas.restore()
    }
}

private fun createShapeMask(
    shape: Shape,
    intSize: IntSize,
    layoutDirection: LayoutDirection,
    density: Density
): ImageBitmap {
    val bitmap = ImageBitmap(
        width = intSize.width,
        height = intSize.height,
        config = ImageBitmapConfig.Alpha8
    )
    val canvas = Canvas(bitmap)
    val outline = shape.createOutline(intSize.toSize(), layoutDirection, density)
    canvas.drawOutline(outline, FillMaskPaint)
    return bitmap
}

private fun createOutsideCornerMask(
    insidePath: Path,
    width: Int,
    height: Int
): ImageBitmap {
    val bitmap = ImageBitmap(
        width = width,
        height = height,
        config = ImageBitmapConfig.Alpha8
    )
    val canvas = Canvas(bitmap)
    canvas.drawRect(Rect(0f, 0f, width.toFloat(), height.toFloat()), FillMaskPaint)
    canvas.drawPath(insidePath, CutInsideMaskPaint)
    return bitmap
}

private fun createG3CornerMaskPath(
    size: Size,
    radius: Float
): G3CornerMaskPath? {
    val minDimension = min(size.width, size.height)
    if (radius >= minDimension) {
        val path = Path().apply {
            arcTo(Rect(0f, 0f, radius, radius), 180f, 90f, true)
            lineTo(radius, radius)
            close()
        }
        return G3CornerMaskPath(path, Size(radius, radius))
    }

    val r = radius.coerceIn(0f, minDimension * 0.5f)
    if (r <= 0f) return null

    val uniforms = g3RoundedRectUniforms(size, r)
    val x0 = uniforms.bezierX.controlPoint(0, 0)
    val x1 = uniforms.bezierX.controlPoint(0, 1)
    val x2 = uniforms.bezierX.controlPoint(0, 2)
    val x3 = uniforms.bezierX.controlPoint(0, 3)
    val y3 = uniforms.bezierY.controlPoint(0, 3)
    val x4 = uniforms.bezierX.controlPoint(4, 1)
    val y4 = uniforms.bezierY.controlPoint(4, 1)
    val x5 = uniforms.bezierX.controlPoint(4, 2)
    val y5 = uniforms.bezierY.controlPoint(4, 2)
    val x6 = uniforms.bezierX.controlPoint(4, 3)
    val y6 = uniforms.bezierY.controlPoint(4, 3)
    val y7 = uniforms.bezierY.controlPoint(8, 1)
    val y8 = uniforms.bezierY.controlPoint(8, 2)
    val y9 = uniforms.bezierY.controlPoint(8, 3)

    val path = Path().apply {
        moveTo(0f, r - y9 * r)
        cubicTo(
            0f, r - y8 * r,
            0f, r - y7 * r,
            r - x6 * r, r - y6 * r
        )
        cubicTo(
            r - x5 * r, r - y5 * r,
            r - x4 * r, r - y4 * r,
            r - x3 * r, r - y3 * r
        )
        cubicTo(
            r - x2 * r, 0f,
            r - x1 * r, 0f,
            r - x0 * r, 0f
        )
        lineTo(uniforms.cornerScale.x * r, uniforms.cornerScale.y * r)
        close()
    }
    return G3CornerMaskPath(
        path = path,
        size = Size(uniforms.cornerScale.x * r, uniforms.cornerScale.y * r)
    )
}

private fun FloatArray.controlPoint(offset: Int, index: Int): Float {
    val a = this[offset]
    val b = this[offset + 1]
    val c = this[offset + 2]
    val d = this[offset + 3]
    val p0 = d
    val p1 = (c / 3f) + p0
    val p2 = (b - (3f * p0) + (6f * p1)) / 3f
    val p3 = a + p0 - (3f * p1) + (3f * p2)
    return when (index) {
        0 -> p0
        1 -> p1
        2 -> p2
        3 -> p3
        else -> error("Invalid cubic point index: $index")
    }
}

private fun IntSize.toSize(): Size {
    return Size(width.toFloat(), height.toFloat())
}

private data class G3CornerMaskPath(
    val path: Path,
    val size: Size
)

private data class ShapeCacheKey(
    val size: IntSize,
    val shape: Shape
)

private data class G3CornerMaskCacheKey(
    val size: IntSize,
    val radius: Float
)

private val FillMaskPaint = Paint().apply {
    color = Color.White
}

private val CutInsideMaskPaint = Paint().apply {
    color = Color.White
    blendMode = BlendMode.DstOut
}

private val MaskLayerPaint = Paint()

private val ShapeMaskCache = LinkedHashMap<ShapeCacheKey, ImageBitmap>()
private val G3CornerMaskCache = LinkedHashMap<G3CornerMaskCacheKey, ImageBitmap>()
