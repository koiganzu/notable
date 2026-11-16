package com.ethran.notable.editor.ui.toolbar

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material.Text
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import com.ethran.notable.data.datastore.AppSettings
import com.ethran.notable.data.datastore.BUTTON_SIZE
import com.ethran.notable.data.datastore.GlobalAppSettings
import com.ethran.notable.editor.utils.PenSetting
import com.ethran.notable.ui.components.ScaledPopup
import kotlin.math.roundToInt


@Composable
fun StrokeMenu(
    value: PenSetting,
    onChange: (setting: PenSetting) -> Unit,
    onClose: () -> Unit,
    sizeOptions: List<Pair<String, Float>>,
    colorOptions: List<Color>,
) {
    val panel = RoundedCornerShape(16.dp)
    val panelWidth = maxOf(320, colorOptions.size * 48 + 28)

    ScaledPopup(
        offset = toolbarPopupOffset(),
        onDismissRequest = { onClose() },
        properties = PopupProperties(focusable = true),
        alignment = toolbarPopupAlignment()
    ) {
        Column(
            modifier = Modifier
                .width(panelWidth.dp)
                .background(Color.White, panel)
                .border(2.dp, Color.Black, panel)
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            val sizes = sizeOptions.map { it.second }.distinct().sorted()
            if (sizes.size >= 2) {
                SliderLabel("Thickness", value.strokeSize.roundToInt().toString())
                DiscreteThicknessSlider(
                    value = value.strokeSize,
                    onValueChange = { newSize ->
                        if (newSize != value.strokeSize) {
                            onChange(PenSetting(strokeSize = newSize, color = value.color))
                        }
                    },
                    values = sizes,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .padding(horizontal = 6.dp, vertical = 6.dp)
                )
                Spacer(Modifier.height(10.dp))
            }

            // Opacity is the ink's alpha, in ten steps. The stroke settles to it once drawn.
            val alpha = (value.color ushr 24) and 0xFF
            val step = (alpha / 25.5f).roundToInt().coerceIn(1, 10)
            SliderLabel("Opacity", "${step * 10}%")
            LevelSlider(
                level = step,
                levels = 10,
                onLevelChange = { newStep ->
                    if (newStep != step) {
                        val newAlpha = (newStep * 25.5f).roundToInt().coerceIn(1, 255)
                        onChange(
                            PenSetting(
                                strokeSize = value.strokeSize,
                                color = (value.color and 0x00FFFFFF) or (newAlpha shl 24)
                            )
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .padding(horizontal = 6.dp)
            )

            if (colorOptions.size > 1) {
                Spacer(Modifier.height(8.dp))
                ColorPicker(
                    value = value,
                    onChange = onChange,
                    colorOptions = colorOptions,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    embedded = true
                )
            }
        }
    }
}

@Composable
private fun SliderLabel(name: String, amount: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(name, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        Text(amount, fontSize = 17.sp)
    }
}

/** A plain stepped slider: a rail, a filled part and a round handle. Tap or drag to set. */
@Composable
private fun LevelSlider(
    level: Int,
    levels: Int,
    onLevelChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    fun levelAt(x: Float, width: Int): Int {
        val fraction = (x / width.coerceAtLeast(1)).coerceIn(0f, 1f)
        return (1 + fraction * (levels - 1)).roundToInt().coerceIn(1, levels)
    }
    Box(
        modifier
            .pointerInput(levels) {
                detectTapGestures { onLevelChange(levelAt(it.x, size.width)) }
            }
            .pointerInput(levels) {
                detectDragGestures { change, _ ->
                    onLevelChange(levelAt(change.position.x, size.width))
                }
            }
    ) {
        Canvas(Modifier.matchParentSize()) {
            val y = size.height / 2f
            val handle = size.height * 0.3f
            val start = handle
            val end = size.width - handle
            val x = start + (end - start) * (level - 1) / (levels - 1).coerceAtLeast(1)
            drawLine(Color.Gray, Offset(start, y), Offset(end, y), 6f, StrokeCap.Round)
            drawLine(Color.Black, Offset(start, y), Offset(x, y), 10f, StrokeCap.Round)
            drawCircle(Color.White, handle + 3f, Offset(x, y))
            drawCircle(Color.Black, handle, Offset(x, y))
        }
    }
}


@Composable
private fun ColorPicker(
    value: PenSetting,
    onChange: (setting: PenSetting) -> Unit,
    colorOptions: List<Color>,
    modifier: Modifier = Modifier,
    embedded: Boolean = false
) {
    val rowModifier = if (embedded) {
        modifier.height(IntrinsicSize.Max)
    } else {
        modifier.height(IntrinsicSize.Max)
    }

    Row(rowModifier) {
        colorOptions.map { color ->
            Box(
                modifier = Modifier
                    .padding(4.dp)
                    .size(40.dp)
                    .border(
                        3.dp,
                        if (sameInk(color, value.color)) Color.Black else Color.Transparent,
                        CircleShape
                    )
                    .padding(5.dp)
                    .background(color, CircleShape)
                    .border(1.dp, Color.Black, CircleShape)
                    .clickable {
                        // A solid swatch keeps the opacity already dialled in.
                        val keep = (value.color ushr 24) and 0xFF
                        val a = if (color.alpha >= 1f) keep else (color.alpha * 255).toInt()
                        onChange(
                            PenSetting(
                                strokeSize = value.strokeSize, color = android.graphics.Color.argb(
                                    a,
                                    (color.red * 255).toInt(),
                                    (color.green * 255).toInt(),
                                    (color.blue * 255).toInt()
                                )
                            )
                        )
                    }
                    .padding(8.dp))
        }
    }

    if (!embedded) {
        Spacer(Modifier.height(4.dp))
    }

}

private fun sameInk(option: Color, ink: Int): Boolean {
    val o = android.graphics.Color.argb(
        255, (option.red * 255).toInt(), (option.green * 255).toInt(), (option.blue * 255).toInt()
    )
    return (o and 0x00FFFFFF) == (ink and 0x00FFFFFF)
}

// Old Picker
@Suppress("unused")
@Composable
private fun ThicknessPicker(
    value: PenSetting,
    onChange: (setting: PenSetting) -> Unit,
    sizeOptions: List<Pair<String, Float>>,
    modifier: Modifier = Modifier
) {

    Row(
        modifier = modifier
            .background(Color.White)
            .border(1.dp, Color.Black),
        horizontalArrangement = Arrangement.Center
    ) {
        sizeOptions.forEach {
            ToolbarButton(
                text = it.first, isSelected = value.strokeSize == it.second, onSelect = {
                    onChange(
                        PenSetting(
                            strokeSize = it.second, color = value.color
                        )
                    )
                }, modifier = Modifier
            )
        }
    }
}

/**
 * Discrete slider:
 * - Track is a wedge: thin on the left, gradually thicker to the right.
 * - Tap or drag to change; snapping to nearest integer value between min and max.
 * - Single down arrow/rect thumb for clarity (high contrast).
 * - Takes a List<Float> of suggested values; indicators are shown above these.
 * - All integer values between min and max are selectable by dragging/tapping.
 */
@Composable
private fun DiscreteThicknessSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    values: List<Float>,
    modifier: Modifier = Modifier,
) {
    if (values.size < 2) return // Require at least two values to define a range

    // Use only sorted, distinct values
    val displayValues = values.distinct().sorted()
    val minVal = displayValues.first().toInt()
    val maxVal = displayValues.last().toInt()


    // Map each integer to a normalized position [0,1] along the slider
    fun fractionForInt(v: Int): Float = (v - minVal).toFloat() / (maxVal - minVal).toFloat()

    // For the indicators only: map the suggested values to fractions
    val indicatorFractions = displayValues.map { v ->
        val intV = v.toInt().coerceIn(minVal, maxVal)
        fractionForInt(intV)
    }

    // Clamp and snap to nearest integer on user interaction
    fun snapToNearestInt(xFraction: Float): Float {
        val clamped = xFraction.coerceIn(0f, 1f)
        val rawValue = minVal + ((maxVal - minVal) * clamped)
        return rawValue.roundToInt().coerceIn(minVal, maxVal).toFloat()
    }

    // Thumb's proportional position
    val thumbInt = value.roundToInt().coerceIn(minVal, maxVal)
    val thumbFraction = fractionForInt(thumbInt)

    Box(
        modifier = modifier
            .pointerInput(displayValues) {
                detectTapGestures { offset ->
                    val w = size.width.coerceAtLeast(1)
                    onValueChange(snapToNearestInt(offset.x / w))
                }
            }
            .pointerInput(displayValues) {
                detectDragGestures(onDrag = { change, _ ->
                    val w = size.width.coerceAtLeast(1)
                    onValueChange(snapToNearestInt(change.position.x / w))
                })
            }) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val w = size.width
            val h = size.height
            val centerY = h / 2f

            // Wedge parameters (thin -> thick)
            val minTrack = 6f
            val maxTrack = (h * 0.75f).coerceAtLeast(minTrack + 4f)

            fun thicknessAtX(x: Float): Float {
                val frac = if (w > 0) (x / w).coerceIn(0f, 1f) else 0f
                return minTrack + (maxTrack - minTrack) * frac
            }

            // Wedge track path with left thin, right thick (bottom edge kept centered)
            val trackPath = Path().apply {
                moveTo(0f, centerY - minTrack / 2f) // top-left
                lineTo(w, centerY - maxTrack / 2f)  // top-right
                lineTo(w, centerY)                  // bottom-right
                lineTo(0f, centerY)                 // bottom-left
                close()
            }

            // Outline first (white halo), then fill, then crisp black outline
            drawPath(
                path = trackPath,
                color = Color.White,
                style = Stroke(
                    width = 6f,
                    cap = StrokeCap.Butt,
                    join = StrokeJoin.Round
                )
            )
            drawPath(
                path = trackPath,
                color = Color.Black,
                style = Fill
            )
            drawPath(
                path = trackPath,
                color = Color.Black,
                style = Stroke(
                    width = 2f,
                    cap = StrokeCap.Butt,
                    join = StrokeJoin.Round
                )
            )

            // Rounded end hints
            val leftR = minTrack / 4f
            val rightR = maxTrack / 4f
            drawCircle(Color.Black, radius = leftR, center = Offset(2f, centerY))
            drawCircle(Color.Black, radius = rightR, center = Offset(w - 3f, centerY - rightR))

            // Ticks (indicators) along the top for the suggested values only
            val tickGap = 6f
            val tickHeight = 12f
            for (frac in indicatorFractions) {
                val x = frac * w
                val t = thicknessAtX(x)
                val topY = (centerY - t / 2f)
                val startY = (topY - tickGap - tickHeight).coerceAtLeast(0f)
                val endY = (topY - tickGap).coerceAtLeast(0f)
                drawLine(
                    color = Color.Black,
                    start = Offset(x, startY),
                    end = Offset(x, endY),
                    strokeWidth = 3f,
                    cap = StrokeCap.Round
                )
            }

            // Thumb position and prominence
            val thumbX = thumbFraction * w
            val currentThickness = thicknessAtX(thumbX)

            // Highly visible vertical rounded thumb (white border + black body)
            val thumbW = 10f
            val thumbH = (currentThickness + 14f).coerceAtMost(h * 0.9f) / 2
            val thumbRadius = 6f
            val thumbOuterPadding = 3f

            drawRoundRect(
                color = Color.White,
                topLeft = Offset(
                    thumbX - thumbW / 2f - thumbOuterPadding, centerY - thumbH - thumbOuterPadding
                ),
                size = Size(
                    thumbW + thumbOuterPadding * 2f, thumbH + thumbOuterPadding * 2f
                ),
                cornerRadius = CornerRadius(thumbRadius, thumbRadius)
            )
            drawRoundRect(
                color = Color.Black,
                topLeft = Offset(thumbX - thumbW / 2f, centerY - thumbH),
                size = Size(thumbW, thumbH),
                cornerRadius = CornerRadius(thumbRadius, thumbRadius)
            )

            // Down arrow pointing to current position for visibility
            val arrowWidth = 30f
            val arrowHeight = 30f
            val downArrow = Path().apply {
                val tipY = (centerY + 6f).coerceAtMost(h)
                moveTo(thumbX, tipY) // tip (upwards)
                lineTo(thumbX - arrowWidth / 2f, (tipY + arrowHeight).coerceAtMost(h))
                lineTo(thumbX + arrowWidth / 2f, (tipY + arrowHeight).coerceAtMost(h))
                close()
            }
            drawPath(downArrow, color = Color.Black)
        }
    }
}