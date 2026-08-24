@file:Suppress("FunctionName")

package io.github.daniele21.localllm.phonetest

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

internal enum class HarnessDestination(val route: String, val label: String, val compactLabel: String = label) {
    OVERVIEW("overview", "Overview"),
    PLAYGROUND("playground", "Playground"),
    PERFORMANCE("performance", "Performance", "Perf"),
    MODELS("models", "Models"),
    DIAGNOSTICS("diagnostics", "Diagnostics", "Diag"),
    SETTINGS("settings", "Settings"),
    ;

    companion object {
        val main = listOf(OVERVIEW, PLAYGROUND, PERFORMANCE, MODELS, DIAGNOSTICS)

        fun fromRoute(route: String?): HarnessDestination = entries.firstOrNull { it.route == route } ?: OVERVIEW
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HarnessTopBar(destination: HarnessDestination, onOpenSettings: () -> Unit, onNavigateBack: () -> Unit) {
    TopAppBar(
        modifier = Modifier.testTag("harnessTopBar"),
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
        navigationIcon = {
            if (destination == HarnessDestination.SETTINGS) {
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier.semantics { contentDescription = "Back to Harness" },
                ) {
                    HarnessDestinationIcon(HarnessDestination.OVERVIEW, selected = false, backArrow = true)
                }
            }
        },
        title = {
            if (destination == HarnessDestination.SETTINGS) {
                Column {
                    Text("Settings", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Preferences and local controls",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(R.drawable.harness_launcher_foreground),
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Column {
                        Text("Harness", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "Local AI Console",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        actions = {
            if (destination != HarnessDestination.SETTINGS) {
                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier.semantics { contentDescription = "Open settings" },
                ) {
                    HarnessDestinationIcon(HarnessDestination.SETTINGS, selected = false)
                }
            }
        },
    )
}

@Composable
internal fun HarnessBottomBar(destination: HarnessDestination, onNavigate: (HarnessDestination) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Column {
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.65f))
            Row(
                modifier = Modifier.fillMaxWidth().height(58.dp).selectableGroup(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                HarnessDestination.main.forEach { item ->
                    val selected = destination == item
                    val color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .testTag("nav-${item.route}")
                            .semantics { contentDescription = item.label }
                            .selectable(
                                selected = selected,
                                role = Role.Tab,
                                onClick = { onNavigate(item) },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            HarnessDestinationIcon(item, selected = selected, modifier = Modifier.size(18.dp))
                            Text(
                                item.compactLabel,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                color = color,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun HarnessRuntimeGlyph(ready: Boolean, modifier: Modifier = Modifier.size(58.dp)) {
    val accent = if (ready) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
    val primary = MaterialTheme.colorScheme.primary
    val background = MaterialTheme.colorScheme.background
    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)
        drawCircle(accent.copy(alpha = 0.08f), radius = size.minDimension * 0.48f, center = center)
        drawHexagon(
            radius = size.minDimension * 0.39f,
            color = accent.copy(alpha = 0.38f),
            style = Stroke(width = 1.4.dp.toPx()),
        )
        drawHexagon(
            radius = size.minDimension * 0.29f,
            brush = Brush.linearGradient(
                colors = listOf(primary, accent),
                start = Offset.Zero,
                end = Offset(size.width, size.height),
            ),
        )
        drawHexagon(
            radius = size.minDimension * 0.17f,
            color = background.copy(alpha = 0.72f),
        )
    }
}

private fun DrawScope.drawHexagon(
    radius: Float,
    color: Color? = null,
    brush: Brush? = null,
    style: androidx.compose.ui.graphics.drawscope.DrawStyle = androidx.compose.ui.graphics.drawscope.Fill,
) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val points = (0 until 6).map { index ->
        val angle = Math.toRadians((60.0 * index) - 30.0)
        Offset(
            center.x + kotlin.math.cos(angle).toFloat() * radius,
            center.y + kotlin.math.sin(angle).toFloat() * radius,
        )
    }
    val path = Path().apply {
        moveTo(points.first().x, points.first().y)
        points.drop(1).forEach { lineTo(it.x, it.y) }
        close()
    }
    if (brush != null) drawPath(path, brush = brush, style = style)
    if (color != null) drawPath(path, color = color, style = style)
}

@Composable
internal fun HarnessDestinationIcon(
    destination: HarnessDestination,
    selected: Boolean,
    backArrow: Boolean = false,
    modifier: Modifier = Modifier.size(20.dp),
) {
    val color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(modifier = modifier) {
        val stroke = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        val width = size.width
        val height = size.height
        if (backArrow) {
            drawLine(color, Offset(width * 0.76f, height * 0.18f), Offset(width * 0.28f, height * 0.5f), stroke.width)
            drawLine(color, Offset(width * 0.28f, height * 0.5f), Offset(width * 0.76f, height * 0.82f), stroke.width)
            return@Canvas
        }
        when (destination) {
            HarnessDestination.OVERVIEW -> {
                val path = Path().apply {
                    moveTo(width * 0.14f, height * 0.48f)
                    lineTo(width * 0.5f, height * 0.16f)
                    lineTo(width * 0.86f, height * 0.48f)
                    moveTo(width * 0.24f, height * 0.42f)
                    lineTo(width * 0.24f, height * 0.84f)
                    lineTo(width * 0.76f, height * 0.84f)
                    lineTo(width * 0.76f, height * 0.42f)
                }
                drawPath(path, color, style = stroke)
            }

            HarnessDestination.PLAYGROUND -> {
                drawCircle(color, radius = width * 0.38f, style = stroke)
                val play = Path().apply {
                    moveTo(width * 0.43f, height * 0.35f)
                    lineTo(width * 0.69f, height * 0.5f)
                    lineTo(width * 0.43f, height * 0.65f)
                    close()
                }
                drawPath(play, color)
            }

            HarnessDestination.PERFORMANCE -> {
                drawLine(color, Offset(width * 0.14f, height * 0.78f), Offset(width * 0.14f, height * 0.28f), stroke.width)
                drawLine(color, Offset(width * 0.14f, height * 0.78f), Offset(width * 0.88f, height * 0.78f), stroke.width)
                val chart = Path().apply {
                    moveTo(width * 0.22f, height * 0.68f)
                    lineTo(width * 0.4f, height * 0.5f)
                    lineTo(width * 0.57f, height * 0.58f)
                    lineTo(width * 0.82f, height * 0.28f)
                }
                drawPath(chart, color, style = stroke)
            }

            HarnessDestination.MODELS -> {
                val hexagon = Path().apply {
                    moveTo(width * 0.5f, height * 0.12f)
                    lineTo(width * 0.84f, height * 0.31f)
                    lineTo(width * 0.84f, height * 0.69f)
                    lineTo(width * 0.5f, height * 0.88f)
                    lineTo(width * 0.16f, height * 0.69f)
                    lineTo(width * 0.16f, height * 0.31f)
                    close()
                }
                drawPath(hexagon, color, style = stroke)
                drawLine(color, Offset(width * 0.5f, height * 0.12f), Offset(width * 0.5f, height * 0.88f), stroke.width)
            }

            HarnessDestination.DIAGNOSTICS -> {
                val pulse = Path().apply {
                    moveTo(width * 0.08f, height * 0.56f)
                    lineTo(width * 0.28f, height * 0.56f)
                    lineTo(width * 0.4f, height * 0.3f)
                    lineTo(width * 0.58f, height * 0.75f)
                    lineTo(width * 0.7f, height * 0.48f)
                    lineTo(width * 0.92f, height * 0.48f)
                }
                drawPath(pulse, color, style = stroke)
            }

            HarnessDestination.SETTINGS -> {
                drawCircle(color, radius = width * 0.24f, style = stroke)
                drawCircle(color, radius = width * 0.07f, style = stroke)
                listOf(0f, 45f, 90f, 135f, 180f, 225f, 270f, 315f).forEach { degrees ->
                    val radians = Math.toRadians(degrees.toDouble())
                    val inner = Offset(
                        x = width * 0.5f + kotlin.math.cos(radians).toFloat() * width * 0.33f,
                        y = height * 0.5f + kotlin.math.sin(radians).toFloat() * height * 0.33f,
                    )
                    val outer = Offset(
                        x = width * 0.5f + kotlin.math.cos(radians).toFloat() * width * 0.43f,
                        y = height * 0.5f + kotlin.math.sin(radians).toFloat() * height * 0.43f,
                    )
                    drawLine(color, inner, outer, stroke.width)
                }
            }
        }
    }
}
