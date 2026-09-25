package com.nevoit.material.navigation.shape

import androidx.compose.foundation.shape.CornerSize
import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

@Immutable
sealed interface G3RoundedRectangularShape : Shape {

    fun corners(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Corners

    data class Corners(
        val topLeft: Float,
        val topRight: Float,
        val bottomRight: Float,
        val bottomLeft: Float
    )
}

@Immutable
data class G3RectangleCornerRadii(
    val topStart: CornerSize,
    val topEnd: CornerSize,
    val bottomEnd: CornerSize,
    val bottomStart: CornerSize
) {

    constructor(
        topStart: Dp,
        topEnd: Dp,
        bottomEnd: Dp,
        bottomStart: Dp
    ) : this(
        CornerSize(topStart),
        CornerSize(topEnd),
        CornerSize(bottomEnd),
        CornerSize(bottomStart)
    )

    constructor(
        topStart: Int,
        topEnd: Int,
        bottomEnd: Int,
        bottomStart: Int
    ) : this(
        CornerSize(topStart),
        CornerSize(topEnd),
        CornerSize(bottomEnd),
        CornerSize(bottomStart)
    )
}

@Immutable
class G3Capsule : G3RoundedRectangularShape {

    override fun corners(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): G3RoundedRectangularShape.Corners {
        val radius = size.minDimension * 0.5f
        return G3RoundedRectangularShape.Corners(
            topLeft = radius,
            topRight = radius,
            bottomRight = radius,
            bottomLeft = radius
        )
    }

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        if (size.width <= 0f || size.height <= 0f) {
            return Outline.Rectangle(Rect(Offset.Zero, size))
        }

        return Outline.Generic(
            createG3RoundedRectPath(
                width = size.width,
                height = size.height,
                radius = size.minDimension * 0.5f
            )
        )
    }

    fun copy(): G3Capsule = G3Capsule()

    override fun equals(other: Any?): Boolean {
        return this === other || other is G3Capsule
    }

    override fun hashCode(): Int = G3CapsuleHashCode

    override fun toString(): String = "G3Capsule"
}

@Immutable
class G3RoundedRectangle(
    val radius: CornerSize
) : G3RoundedRectangularShape {

    constructor(radius: Dp) : this(CornerSize(radius))

    constructor(percent: Int) : this(CornerSize(percent))

    val cornerRadius: CornerSize get() = radius

    override fun corners(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): G3RoundedRectangularShape.Corners {
        val minDimension = min(abs(size.width), abs(size.height))
        val radius = radius.toPx(size, density).coerceIn(0f, minDimension * 0.5f)
        return G3RoundedRectangularShape.Corners(
            topLeft = radius,
            topRight = radius,
            bottomRight = radius,
            bottomLeft = radius
        )
    }

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        if (size.width <= 0f || size.height <= 0f) {
            return Outline.Rectangle(Rect(Offset.Zero, size))
        }

        val coercedRadius = corners(size, layoutDirection, density).topLeft
        if (coercedRadius <= 0f) {
            return Outline.Rectangle(Rect(Offset.Zero, size))
        }

        return Outline.Generic(createG3RoundedRectPath(size.width, size.height, coercedRadius))
    }

    fun copy(radius: CornerSize = this.radius): G3RoundedRectangle {
        return G3RoundedRectangle(radius)
    }

    fun copy(radius: Dp): G3RoundedRectangle {
        return G3RoundedRectangle(radius)
    }

    fun copy(percent: Int): G3RoundedRectangle {
        return G3RoundedRectangle(percent)
    }

    override fun equals(other: Any?): Boolean {
        return this === other || other is G3RoundedRectangle && radius == other.radius
    }

    override fun hashCode(): Int = radius.hashCode()

    override fun toString(): String = "G3RoundedRectangle(cornerRadius=$radius)"
}

@Immutable
class G3UnevenRoundedRectangle(
    val cornerRadii: G3RectangleCornerRadii
) : G3RoundedRectangularShape {

    constructor(
        topStart: CornerSize,
        topEnd: CornerSize,
        bottomEnd: CornerSize,
        bottomStart: CornerSize
    ) : this(G3RectangleCornerRadii(topStart, topEnd, bottomEnd, bottomStart))

    constructor(
        topStart: Dp = 0.dp,
        topEnd: Dp = 0.dp,
        bottomEnd: Dp = 0.dp,
        bottomStart: Dp = 0.dp
    ) : this(G3RectangleCornerRadii(topStart, topEnd, bottomEnd, bottomStart))

    constructor(
        topStart: Int,
        topEnd: Int,
        bottomEnd: Int,
        bottomStart: Int
    ) : this(G3RectangleCornerRadii(topStart, topEnd, bottomEnd, bottomStart))

    val cornerSizes: G3RectangleCornerRadii get() = cornerRadii
    val topStart: CornerSize get() = cornerRadii.topStart
    val topEnd: CornerSize get() = cornerRadii.topEnd
    val bottomEnd: CornerSize get() = cornerRadii.bottomEnd
    val bottomStart: CornerSize get() = cornerRadii.bottomStart

    override fun corners(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): G3RoundedRectangularShape.Corners {
        val isLtr = layoutDirection == LayoutDirection.Ltr
        var topLeft = (if (isLtr) topStart else topEnd).toPx(size, density)
        var topRight = (if (isLtr) topEnd else topStart).toPx(size, density)
        var bottomRight = (if (isLtr) bottomEnd else bottomStart).toPx(size, density)
        var bottomLeft = (if (isLtr) bottomStart else bottomEnd).toPx(size, density)
        val minDimension = min(abs(size.width), abs(size.height))

        topLeft = topLeft.coerceIn(0f, minDimension)
        topRight = topRight.coerceIn(0f, minDimension)
        bottomRight = bottomRight.coerceIn(0f, minDimension)
        bottomLeft = bottomLeft.coerceIn(0f, minDimension)

        if (size.width >= size.height) {
            val leftSum = topLeft + bottomLeft
            if (leftSum > minDimension) {
                val scale = minDimension / leftSum
                topLeft *= scale
                bottomLeft *= scale
            }
            val rightSum = topRight + bottomRight
            if (rightSum > minDimension) {
                val scale = minDimension / rightSum
                topRight *= scale
                bottomRight *= scale
            }
        } else {
            val topSum = topLeft + topRight
            if (topSum > minDimension) {
                val scale = minDimension / topSum
                topLeft *= scale
                topRight *= scale
            }
            val bottomSum = bottomLeft + bottomRight
            if (bottomSum > minDimension) {
                val scale = minDimension / bottomSum
                bottomLeft *= scale
                bottomRight *= scale
            }
        }

        return G3RoundedRectangularShape.Corners(
            topLeft = topLeft,
            topRight = topRight,
            bottomRight = bottomRight,
            bottomLeft = bottomLeft
        )
    }

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        if (size.width <= 0f || size.height <= 0f) {
            return Outline.Rectangle(Rect(Offset.Zero, size))
        }

        val isLtr = layoutDirection == LayoutDirection.Ltr
        val topLeftRaw = (if (isLtr) topStart else topEnd).toPx(size, density)
        val topRightRaw = (if (isLtr) topEnd else topStart).toPx(size, density)
        val bottomRightRaw = (if (isLtr) bottomEnd else bottomStart).toPx(size, density)
        val bottomLeftRaw = (if (isLtr) bottomStart else bottomEnd).toPx(size, density)
        val rect = Rect(Offset.Zero, size)

        if (
            topLeftRaw <= 0f &&
            topRightRaw <= 0f &&
            bottomRightRaw <= 0f &&
            bottomLeftRaw <= 0f
        ) {
            return Outline.Rectangle(rect)
        }

        val corners = corners(size, layoutDirection, density)
        return Outline.Generic(
            Path().apply {
                addG3UnevenRoundedRect(
                    width = size.width,
                    height = size.height,
                    topLeft = corners.topLeft,
                    topRight = corners.topRight,
                    bottomRight = corners.bottomRight,
                    bottomLeft = corners.bottomLeft
                )
            }
        )
    }

    fun copy(cornerRadii: G3RectangleCornerRadii = this.cornerRadii): G3UnevenRoundedRectangle {
        return G3UnevenRoundedRectangle(cornerRadii)
    }

    fun copy(
        topStart: CornerSize = this.topStart,
        topEnd: CornerSize = this.topEnd,
        bottomEnd: CornerSize = this.bottomEnd,
        bottomStart: CornerSize = this.bottomStart
    ): G3UnevenRoundedRectangle {
        return G3UnevenRoundedRectangle(topStart, topEnd, bottomEnd, bottomStart)
    }

    override fun equals(other: Any?): Boolean {
        return this === other ||
                other is G3UnevenRoundedRectangle &&
                cornerRadii == other.cornerRadii
    }

    override fun hashCode(): Int = cornerRadii.hashCode()

    override fun toString(): String {
        return "G3UnevenRoundedRectangle(cornerRadii=$cornerRadii)"
    }
}

private const val G3CapsuleHashCode = 0x47334350

private fun Path.addG3UnevenRoundedRect(
    width: Float,
    height: Float,
    topLeft: Float,
    topRight: Float,
    bottomRight: Float,
    bottomLeft: Float
) {
    val w = width.toDouble()
    val h = height.toDouble()
    val tl = topLeft.toDouble()
    val tr = topRight.toDouble()
    val br = bottomRight.toDouble()
    val bl = bottomLeft.toDouble()

    if (tl > 0.0) {
        val data = G3RoundedRectCorner.getData(w / (tl + tr) - 1.0, h / (tl + bl) - 1.0)
        moveTo(0f, (tl - data.y9 * tl).toFloat())
        cubicTo(
            0f, (tl - data.y8 * tl).toFloat(),
            0f, (tl - data.y7 * tl).toFloat(),
            (tl - data.x6 * tl).toFloat(), (tl - data.y6 * tl).toFloat()
        )
        cubicTo(
            (tl - data.x5 * tl).toFloat(), (tl - data.y5 * tl).toFloat(),
            (tl - data.x4 * tl).toFloat(), (tl - data.y4 * tl).toFloat(),
            (tl - data.x3 * tl).toFloat(), (tl - data.y3 * tl).toFloat()
        )
        cubicTo(
            (tl - data.x2 * tl).toFloat(), 0f,
            (tl - data.x1 * tl).toFloat(), 0f,
            (tl - data.x0 * tl).toFloat(), 0f
        )
    } else {
        moveTo(0f, 0f)
    }

    if (tr > 0.0) {
        val left = w - tr
        val data = G3RoundedRectCorner.getData(w / (tl + tr) - 1.0, h / (tr + br) - 1.0)
        lineTo((left + data.x0 * tr).toFloat(), 0f)
        cubicTo(
            (left + data.x1 * tr).toFloat(), 0f,
            (left + data.x2 * tr).toFloat(), 0f,
            (left + data.x3 * tr).toFloat(), (tr - data.y3 * tr).toFloat()
        )
        cubicTo(
            (left + data.x4 * tr).toFloat(), (tr - data.y4 * tr).toFloat(),
            (left + data.x5 * tr).toFloat(), (tr - data.y5 * tr).toFloat(),
            (left + data.x6 * tr).toFloat(), (tr - data.y6 * tr).toFloat()
        )
        cubicTo(
            w.toFloat(), (tr - data.y7 * tr).toFloat(),
            w.toFloat(), (tr - data.y8 * tr).toFloat(),
            w.toFloat(), (tr - data.y9 * tr).toFloat()
        )
    } else {
        lineTo(width, 0f)
    }

    if (br > 0.0) {
        val left = w - br
        val top = h - br
        val rightSum = tr + br
        val data = G3RoundedRectCorner.getData(w / rightSum - 1.0, h / rightSum - 1.0)
        lineTo(w.toFloat(), (top + data.y9 * br).toFloat())
        cubicTo(
            w.toFloat(), (top + data.y8 * br).toFloat(),
            w.toFloat(), (top + data.y7 * br).toFloat(),
            (left + data.x6 * br).toFloat(), (top + data.y6 * br).toFloat()
        )
        cubicTo(
            (left + data.x5 * br).toFloat(), (top + data.y5 * br).toFloat(),
            (left + data.x4 * br).toFloat(), (top + data.y4 * br).toFloat(),
            (left + data.x3 * br).toFloat(), (top + data.y3 * br).toFloat()
        )
        cubicTo(
            (left + data.x2 * br).toFloat(), h.toFloat(),
            (left + data.x1 * br).toFloat(), h.toFloat(),
            (left + data.x0 * br).toFloat(), h.toFloat()
        )
    } else {
        lineTo(width, height)
    }

    if (bl > 0.0) {
        val top = h - bl
        val data = G3RoundedRectCorner.getData(w / (bl + br) - 1.0, h / (tl + bl) - 1.0)
        lineTo((bl - data.x0 * bl).toFloat(), h.toFloat())
        cubicTo(
            (bl - data.x1 * bl).toFloat(), h.toFloat(),
            (bl - data.x2 * bl).toFloat(), h.toFloat(),
            (bl - data.x3 * bl).toFloat(), (top + data.y3 * bl).toFloat()
        )
        cubicTo(
            (bl - data.x4 * bl).toFloat(), (top + data.y4 * bl).toFloat(),
            (bl - data.x5 * bl).toFloat(), (top + data.y5 * bl).toFloat(),
            (bl - data.x6 * bl).toFloat(), (top + data.y6 * bl).toFloat()
        )
        cubicTo(
            0f, (top + data.y7 * bl).toFloat(),
            0f, (top + data.y8 * bl).toFloat(),
            0f, (top + data.y9 * bl).toFloat()
        )
    } else {
        lineTo(0f, height)
    }
    close()
}

private fun createG3RoundedRectPath(width: Float, height: Float, radius: Float): Path {
    val r = radius.toDouble()
    val w = width.toDouble()
    val h = height.toDouble()
    val data = G3RoundedRectCorner.getData(((w * 0.5) - r) / r, ((h * 0.5) - r) / r)

    val x0 = data.x0 * r
    val x1 = data.x1 * r
    val x2 = data.x2 * r
    val x3 = data.x3 * r
    val y3 = data.y3 * r
    val x4 = data.x4 * r
    val y4 = data.y4 * r
    val x5 = data.x5 * r
    val y5 = data.y5 * r
    val x6 = data.x6 * r
    val y6 = data.y6 * r
    val y7 = data.y7 * r
    val y8 = data.y8 * r
    val y9 = data.y9 * r

    val right = w - r
    val bottom = h - r

    return Path().apply {
        moveTo((r - r).toFloat(), (r - y9).toFloat())
        cubicTo(
            (r - r).toFloat(), (r - y8).toFloat(),
            (r - r).toFloat(), (r - y7).toFloat(),
            (r - x6).toFloat(), (r - y6).toFloat()
        )
        cubicTo(
            (r - x5).toFloat(), (r - y5).toFloat(),
            (r - x4).toFloat(), (r - y4).toFloat(),
            (r - x3).toFloat(), (r - y3).toFloat()
        )
        cubicTo(
            (r - x2).toFloat(), (r - r).toFloat(),
            (r - x1).toFloat(), (r - r).toFloat(),
            (r - x0).toFloat(), (r - r).toFloat()
        )
        lineTo((right + x0).toFloat(), (r - r).toFloat())
        cubicTo(
            (right + x1).toFloat(), (r - r).toFloat(),
            (right + x2).toFloat(), (r - r).toFloat(),
            (right + x3).toFloat(), (r - y3).toFloat()
        )
        cubicTo(
            (right + x4).toFloat(), (r - y4).toFloat(),
            (right + x5).toFloat(), (r - y5).toFloat(),
            (right + x6).toFloat(), (r - y6).toFloat()
        )
        cubicTo(
            (right + r).toFloat(), (r - y7).toFloat(),
            (right + r).toFloat(), (r - y8).toFloat(),
            (right + r).toFloat(), (r - y9).toFloat()
        )
        lineTo((right + r).toFloat(), (bottom + y9).toFloat())
        cubicTo(
            (right + r).toFloat(), (bottom + y8).toFloat(),
            (right + r).toFloat(), (bottom + y7).toFloat(),
            (right + x6).toFloat(), (bottom + y6).toFloat()
        )
        cubicTo(
            (right + x5).toFloat(), (bottom + y5).toFloat(),
            (right + x4).toFloat(), (bottom + y4).toFloat(),
            (right + x3).toFloat(), (bottom + y3).toFloat()
        )
        cubicTo(
            (right + x2).toFloat(), (bottom + r).toFloat(),
            (right + x1).toFloat(), (bottom + r).toFloat(),
            (right + x0).toFloat(), (bottom + r).toFloat()
        )
        lineTo((r - x0).toFloat(), (bottom + r).toFloat())
        cubicTo(
            (r - x1).toFloat(), (bottom + r).toFloat(),
            (r - x2).toFloat(), (bottom + r).toFloat(),
            (r - x3).toFloat(), (bottom + y3).toFloat()
        )
        cubicTo(
            (r - x4).toFloat(), (bottom + y4).toFloat(),
            (r - x5).toFloat(), (bottom + y5).toFloat(),
            (r - x6).toFloat(), (bottom + y6).toFloat()
        )
        cubicTo(
            (r - r).toFloat(), (bottom + y7).toFloat(),
            (r - r).toFloat(), (bottom + y8).toFloat(),
            (r - r).toFloat(), (bottom + y9).toFloat()
        )
        close()
    }
}

internal class G3RoundedRectUniforms(
    val radius: Float,
    val bezierX: FloatArray,
    val bezierY: FloatArray,
    val bezierP3: Offset,
    val bezierP6: Offset,
    val arcCenter: Offset,
    val arcRadius: Float,
    val cornerScale: Offset
)

internal fun g3RoundedRectUniforms(size: Size, radius: Float): G3RoundedRectUniforms {
    val r = radius.coerceIn(0f, size.minDimension * 0.5f)
    if (r <= 0f) {
        return G3RoundedRectUniforms(
            radius = 0f,
            bezierX = FloatArray(12),
            bezierY = FloatArray(12),
            bezierP3 = Offset.Zero,
            bezierP6 = Offset.Zero,
            arcCenter = Offset.Zero,
            arcRadius = 0f,
            cornerScale = Offset.Zero
        )
    }

    val data = G3RoundedRectCorner.getData(
        ((size.width * 0.5) - r) / r,
        ((size.height * 0.5) - r) / r
    )
    val bezierX = FloatArray(12)
    val bezierY = FloatArray(12)

    writeCubic(bezierX, 0, data.x0, data.x1, data.x2, data.x3)
    writeCubic(bezierY, 0, 1.0, 1.0, 1.0, data.y3)
    writeCubic(bezierX, 4, data.x3, data.x4, data.x5, data.x6)
    writeCubic(bezierY, 4, data.y3, data.y4, data.y5, data.y6)
    writeCubic(bezierX, 8, data.x6, 1.0, 1.0, 1.0)
    writeCubic(bezierY, 8, data.y6, data.y7, data.y8, data.y9)

    val arcRadius: Double
    val arcCenter: Offset
    if (data.kappaH == data.kappaV) {
        arcRadius = 1.0 / data.kappaH
        val center = (Sqrt1Over2 * (1.0 - arcRadius)).toFloat()
        arcCenter = Offset(center, center)
    } else {
        arcRadius = 0.0
        arcCenter = Offset.Zero
    }

    return G3RoundedRectUniforms(
        radius = r,
        bezierX = bezierX,
        bezierY = bezierY,
        bezierP3 = Offset(data.x3.toFloat(), data.y3.toFloat()),
        bezierP6 = Offset(data.x6.toFloat(), data.y6.toFloat()),
        arcCenter = arcCenter,
        arcRadius = arcRadius.toFloat(),
        cornerScale = Offset(1f - data.x0.toFloat(), 1f - data.y9.toFloat())
    )
}

private fun writeCubic(
    target: FloatArray,
    index: Int,
    p0: Double,
    p1: Double,
    p2: Double,
    p3: Double
) {
    target[index] = (-p0 + 3.0 * p1 - 3.0 * p2 + p3).toFloat()
    target[index + 1] = (3.0 * p0 - 6.0 * p1 + 3.0 * p2).toFloat()
    target[index + 2] = (-3.0 * p0 + 3.0 * p1).toFloat()
    target[index + 3] = p0.toFloat()
}

private const val Sqrt1Over2 = 0.7071067811865476

private object G3RoundedRectCorner {

    private val cos = cos(0.39269908169872414)
    private val sin = sin(0.39269908169872414)
    private val cot = cos / sin
    private val cos2 = cos * cos
    private val sin2 = sin * sin
    private val sin3 = sin2 * sin
    private val sin5 = sin2 * sin2 * sin
    private val k0 =
        ((((sin2 + 9.0) * 4.0 * sin2) + 27.0) * 1.4142135623730951 * cos) +
                (((((1.4142135623730951 - (sin * 2.0)) * 4.0) * sin5) +
                        (((229.1025971044414 * cos2 * cos) - ((cos2 * cos2) * 108.0)) - (18.0 * sin2))) -
                        (((sin2 * sin2 * 4.0) + ((18.0 * sin2) + 81.0)) * (cos2 * 2.0)))
    private val k1 =
        ((((cos2 * -134.20519420888283) - ((cos2 * cos) * -94.89740289555859)) -
                (14.911688245431424 * sin2)) - (sin5 * 5.656854249492381)) -
                (((-0.5857864376269049 * cos) * ((sin2 * 2.0) + 9.0)) * ((sin2 * 2.0) + 9.0))
    private val k2 =
        (((((1.0 / sin) * 0.24264068711928566) + (cot * -0.3431457505076194)) * (cot * 9.0)) -
                0.3431457505076194) * 9.0 * sin2
    private val k3 = 2.7136367114850355 * cos
    private val lambdaCoeff = ((sqrt((2.0 * cos * sin) + 7.0) - (cos + sin)) * (cos - sin)) / 3.0

    private val data00 = getDataInternal(0.0)
    private val data11 = getDataInternal(-1.0)
    private val data01 = getDataInternal(0.0, -1.0)
    private val data10 = getDataInternal(-1.0, 0.0)

    fun getData(relativeWidth: Double, relativeHeight: Double): Data {
        val x = -relativeWidth.coerceIn(0.0, 1.0)
        val y = -relativeHeight.coerceIn(0.0, 1.0)
        return when {
            x == 0.0 && y == 0.0 -> data00
            x == -1.0 && y == -1.0 -> data11
            x == 0.0 && y == -1.0 -> data01
            x == -1.0 && y == 0.0 -> data10
            else -> getDataInternal(x, y)
        }
    }

    private fun getDataInternal(value: Double): Data {
        val kappa = solveCubicSingle(k3, k2, (sin5 * 8.0 * value) + k1, k0)
        val x3 = Sqrt1Over2 - ((Sqrt1Over2 - sin) / kappa)
        val y6 = Sqrt1Over2 - ((Sqrt1Over2 - cos) / kappa)
        val y6m = y6 - 1.0
        val y8 = (cot * y6m) + x3
        val y9 = y8 - ((((1.5 * kappa) * y6m) * y6m) / sin3)
        val lambda = lambdaCoeff / kappa
        val x4 = (cos * lambda) + x3
        val y4 = y6 - (lambda * sin)
        return Data(
            kappaH = kappa,
            kappaV = kappa,
            x0 = value,
            x1 = y9,
            x2 = y8,
            x3 = x3,
            y3 = y6,
            x4 = x4,
            y4 = y4,
            x5 = y4,
            y5 = x4,
            x6 = y6,
            y6 = x3,
            y7 = y8,
            y8 = y9,
            y9 = value
        )
    }

    private fun getDataInternal(x: Double, y: Double): Data {
        if (x == y) return getDataInternal(x)

        val kappaH = solveCubicSingle(k3, k2, (sin5 * 8.0 * x) + k1, k0)
        val kappaV = solveCubicSingle(k3, k2, (sin5 * 8.0 * y) + k1, k0)
        val x3 = Sqrt1Over2 - ((Sqrt1Over2 - sin) / kappaH)
        val y6 = Sqrt1Over2 - ((Sqrt1Over2 - cos) / kappaH)
        val y6m = y6 - 1.0
        val x2 = (cot * y6m) + x3
        val x1 = x2 - ((((1.5 * kappaH) * y6m) * y6m) / sin3)
        val y7 = Sqrt1Over2 - ((Sqrt1Over2 - sin) / kappaV)
        val x6 = Sqrt1Over2 - ((Sqrt1Over2 - cos) / kappaV)
        val x6m = x6 - 1.0
        val y8 = (cot * x6m) + y7
        val y9 = y8 - ((((1.5 * kappaV) * x6m) * x6m) / sin3)
        val cos2MinusSin2 = cos2 - sin2
        val dx = x6 - x3
        val dy = y7 - y6
        val p = (cos * dy) + (sin * dx)
        val q = -((dx * cos) + (dy * sin))
        val b = (q / (1.5 * kappaV)) * 2.0
        val c = (((cos2MinusSin2 * cos2MinusSin2) * cos2MinusSin2) /
                ((1.5 * kappaH) * (1.5 * kappaV) * (1.5 * kappaV)))
        val d = (((p * cos2MinusSin2) * cos2MinusSin2) + ((1.5 * kappaH) * q * q)) /
                ((1.5 * kappaH) * (1.5 * kappaV) * (1.5 * kappaV))
        val e = (-b) / 2.0
        val depressedP = ((-d * 3.0) - (e * e)) / 3.0
        val depressedQ =
            (((((d * b) / 2.0) - ((c * c) / 8.0)) * 27.0) +
                    ((((e * 2.0) * e) * e) - ((9.0 * e) * -d))) / 27.0
        val positiveP = -depressedP
        val theta =
            acos((-depressedQ) / (sqrt(((positiveP * depressedP) * depressedP) / 27.0) * 2.0))
        val root = (cos(theta / 3.0) * (sqrt(positiveP / 3.0) * 2.0)) - (e / 3.0)
        val sqrtTerm = sqrt((root * 2.0) - b)
        val t =
            (sqrtTerm - sqrt((sqrtTerm * sqrtTerm) - ((((c / (sqrtTerm * 2.0)) + root) * 4.0)))) / 2.0
        val h = ((-q) - (((1.5 * kappaV) * t) * t)) / cos2MinusSin2

        return Data(
            kappaH = kappaH,
            kappaV = kappaV,
            x0 = x,
            x1 = x1,
            x2 = x2,
            x3 = x3,
            y3 = y6,
            x4 = (h * cos) + x3,
            y4 = y6 - (h * sin),
            x5 = x6 - (sin * t),
            y5 = (t * cos) + y7,
            x6 = x6,
            y6 = y7,
            y7 = y8,
            y8 = y9,
            y9 = y
        )
    }

    private fun solveCubicSingle(a: Double, b: Double, c: Double, d: Double): Double {
        val a2 = a * a
        val p = (((c * 3.0) / a) - ((b * b) / a2)) / 3.0
        val q =
            (((d * 27.0) / a) + (((((b * 2.0) * b) * b) / (a2 * a)) - (((9.0 * b) * c) / a2))) / 27.0
        val discriminant = sqrt((((p * p) * p) / 27.0) + ((q * q) / 4.0))
        val halfQ = (-q) / 2.0
        return cbrtSigned(halfQ - discriminant) + cbrtSigned(halfQ + discriminant) - (b / (a * 3.0))
    }

    private fun cbrtSigned(value: Double): Double {
        return if (value < 0.0) {
            -((-value).pow(1.0 / 3.0))
        } else {
            value.pow(1.0 / 3.0)
        }
    }

    data class Data(
        val kappaH: Double,
        val kappaV: Double,
        val x0: Double,
        val x1: Double,
        val x2: Double,
        val x3: Double,
        val y3: Double,
        val x4: Double,
        val y4: Double,
        val x5: Double,
        val y5: Double,
        val x6: Double,
        val y6: Double,
        val y7: Double,
        val y8: Double,
        val y9: Double
    )
}
