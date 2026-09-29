package com.example.minimaltodo.ui.theme

import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Wenige eigene Vektor-Icons, damit das große material-icons-extended-Paket
 * nicht benötigt wird. Alle auf einem 24×24-Raster.
 */
object AppIcons {

    private fun icon(name: String, fillType: PathFillType = PathFillType.NonZero, block: PathBuilder.() -> Unit) =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
            .path(fill = SolidColor(Color.Black), pathFillType = fillType, pathBuilder = block)
            .build()

    val Pin: ImageVector by lazy {
        icon("Pin") {
            moveTo(16f, 9f); verticalLineTo(4f); horizontalLineTo(17f); verticalLineTo(2f)
            horizontalLineTo(7f); verticalLineTo(4f); horizontalLineTo(8f); verticalLineTo(9f)
            curveTo(8f, 10.66f, 6.66f, 12f, 5f, 12f); verticalLineTo(14f); horizontalLineTo(11f)
            verticalLineTo(21f); lineTo(12f, 22f); lineTo(13f, 21f); verticalLineTo(14f); horizontalLineTo(19f)
            verticalLineTo(12f); curveTo(17.34f, 12f, 16f, 10.66f, 16f, 9f); close()
        }
    }

    val Notes: ImageVector by lazy {
        icon("Notes") {
            moveTo(4f, 6f); horizontalLineTo(20f); verticalLineTo(8f); horizontalLineTo(4f); close()
            moveTo(4f, 11f); horizontalLineTo(20f); verticalLineTo(13f); horizontalLineTo(4f); close()
            moveTo(4f, 16f); horizontalLineTo(14f); verticalLineTo(18f); horizontalLineTo(4f); close()
        }
    }

    val Folder: ImageVector by lazy {
        icon("Folder", PathFillType.EvenOdd) {
            moveTo(10f, 4f); horizontalLineTo(4f)
            curveTo(2.9f, 4f, 2f, 4.9f, 2f, 6f); verticalLineTo(18f)
            curveTo(2f, 19.1f, 2.9f, 20f, 4f, 20f); horizontalLineTo(20f)
            curveTo(21.1f, 20f, 22f, 19.1f, 22f, 18f); verticalLineTo(8f)
            curveTo(22f, 6.9f, 21.1f, 6f, 20f, 6f); horizontalLineTo(12f); close()
            moveTo(20f, 18f); horizontalLineTo(4f); verticalLineTo(8f); horizontalLineTo(20f); close()
        }
    }

    val Today: ImageVector by lazy {
        icon("Today", PathFillType.EvenOdd) {
            moveTo(19f, 4f); horizontalLineTo(18f); verticalLineTo(2f); horizontalLineTo(16f); verticalLineTo(4f)
            horizontalLineTo(8f); verticalLineTo(2f); horizontalLineTo(6f); verticalLineTo(4f); horizontalLineTo(5f)
            curveTo(3.89f, 4f, 3f, 4.9f, 3f, 6f); verticalLineTo(20f)
            curveTo(3f, 21.1f, 3.89f, 22f, 5f, 22f); horizontalLineTo(19f)
            curveTo(20.1f, 22f, 21f, 21.1f, 21f, 20f); verticalLineTo(6f)
            curveTo(21f, 4.9f, 20.1f, 4f, 19f, 4f); close()
            moveTo(19f, 20f); horizontalLineTo(5f); verticalLineTo(10f); horizontalLineTo(19f); close()
            moveTo(7f, 12f); horizontalLineTo(12f); verticalLineTo(17f); horizontalLineTo(7f); close()
        }
    }

    val Copy: ImageVector by lazy {
        icon("Copy", PathFillType.EvenOdd) {
            moveTo(16f, 1f); horizontalLineTo(4f); curveTo(2.9f, 1f, 2f, 1.9f, 2f, 3f); verticalLineTo(17f)
            horizontalLineTo(4f); verticalLineTo(3f); horizontalLineTo(16f); close()
            moveTo(19f, 5f); horizontalLineTo(8f); curveTo(6.9f, 5f, 6f, 5.9f, 6f, 7f); verticalLineTo(21f)
            curveTo(6f, 22.1f, 6.9f, 23f, 8f, 23f); horizontalLineTo(19f)
            curveTo(20.1f, 23f, 21f, 22.1f, 21f, 21f); verticalLineTo(7f)
            curveTo(21f, 5.9f, 20.1f, 5f, 19f, 5f); close()
            moveTo(19f, 21f); horizontalLineTo(8f); verticalLineTo(7f); horizontalLineTo(19f); close()
        }
    }

    val Sort: ImageVector by lazy {
        icon("Sort") {
            moveTo(3f, 6f); horizontalLineTo(21f); verticalLineTo(8f); horizontalLineTo(3f); close()
            moveTo(3f, 11f); horizontalLineTo(15f); verticalLineTo(13f); horizontalLineTo(3f); close()
            moveTo(3f, 16f); horizontalLineTo(9f); verticalLineTo(18f); horizontalLineTo(3f); close()
        }
    }

    /** Pfeil in einen Ordner – „Verschieben“. */
    val Move: ImageVector by lazy {
        icon("Move") {
            moveTo(4f, 11f); horizontalLineTo(16.17f); lineTo(12.59f, 7.41f); lineTo(14f, 6f)
            lineTo(20f, 12f); lineTo(14f, 18f); lineTo(12.59f, 16.59f); lineTo(16.17f, 13f)
            horizontalLineTo(4f); close()
        }
    }

    val Mic: ImageVector by lazy {
        icon("Mic") {
            moveTo(12f, 14f); curveTo(13.66f, 14f, 15f, 12.66f, 15f, 11f); verticalLineTo(5f)
            curveTo(15f, 3.34f, 13.66f, 2f, 12f, 2f); curveTo(10.34f, 2f, 9f, 3.34f, 9f, 5f); verticalLineTo(11f)
            curveTo(9f, 12.66f, 10.34f, 14f, 12f, 14f); close()
            moveTo(17.3f, 11f); curveTo(17.3f, 14f, 14.76f, 16.1f, 12f, 16.1f)
            curveTo(9.24f, 16.1f, 6.7f, 14f, 6.7f, 11f); horizontalLineTo(5f)
            curveTo(5f, 14.41f, 7.72f, 17.23f, 11f, 17.72f); verticalLineTo(21f); horizontalLineTo(13f)
            verticalLineTo(17.72f); curveTo(16.28f, 17.24f, 19f, 14.42f, 19f, 11f); close()
        }
    }

    val StarFilled: ImageVector by lazy { icon("StarFilled") { star(10f, 4.2f) } }

    val StarOutline: ImageVector by lazy {
        icon("StarOutline", PathFillType.EvenOdd) {
            star(10f, 4.2f)
            star(7.2f, 3.0f)
        }
    }

    /** Fünfzackiger Stern um (12, 12.6). */
    private fun PathBuilder.star(outer: Float, inner: Float) {
        val cx = 12f
        val cy = 12.8f
        for (i in 0 until 10) {
            val r = if (i % 2 == 0) outer else inner
            val angle = -PI / 2 + i * PI / 5
            val x = cx + (r * cos(angle)).toFloat()
            val y = cy + (r * sin(angle)).toFloat()
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }
}
