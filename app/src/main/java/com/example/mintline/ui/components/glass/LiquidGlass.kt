package com.example.mintline.ui.components.glass

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Shader
import android.os.Build
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.mintline.theme.LocalMintColors

/**
 * Liquid Glass for Compose.
 *
 * A [GlassBackdrop] records whatever is drawn by the content marked with
 * [glassSource] into a GraphicsLayer. Any element using [liquidGlass] then
 * re-draws that layer (offset to its own position) through a blur +
 * saturation RenderEffect, clips it to its shape and adds a tint plus a
 * specular rim. Because GraphicsLayers are drawn by reference, scrolling
 * content behind the glass updates live without extra recompositions.
 *
 * Below Android 12 (no RenderEffect) it falls back to a frosted tint.
 */
@Stable
class GlassBackdrop internal constructor(internal val layer: GraphicsLayer) {
    internal var origin by mutableStateOf(Offset.Zero)
}

val LocalGlassBackdrop = staticCompositionLocalOf<GlassBackdrop?> { null }

@Composable
fun rememberGlassBackdrop(): GlassBackdrop {
    val layer = rememberGraphicsLayer()
    return remember(layer) { GlassBackdrop(layer) }
}

/** Marks the content that glass surfaces should refract. */
fun Modifier.glassSource(backdrop: GlassBackdrop): Modifier = this
    .onGloballyPositioned { backdrop.origin = it.positionInRoot() }
    .drawWithContent {
        backdrop.layer.record { this@drawWithContent.drawContent() }
        drawLayer(backdrop.layer)
    }

private val supportsBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/**
 * Liquid glass surface.
 * @param tint overrides the default theme glass tint (e.g. to colour a selected pill)
 */
fun Modifier.liquidGlass(
    shape: Shape = CircleShape,
    backdrop: GlassBackdrop? = null,
    blurRadius: Dp = 22.dp,
    tint: Color? = null,
    elevation: Dp = 18.dp,
): Modifier = composed {
    val colors = LocalMintColors.current
    val source = backdrop ?: LocalGlassBackdrop.current
    val blurLayer = rememberGraphicsLayer()
    var position by remember { mutableStateOf(Offset.Zero) }
    val blurPx = with(LocalDensity.current) { blurRadius.toPx() }
    val canBlur = supportsBlur && source != null
    val fillTint = tint ?: if (canBlur) colors.glassTint else colors.glassTint.copy(
        alpha = if (colors.isDark) 0.94f else 0.92f
    ).compositeOverOpaque(colors.isDark)

    val renderEffect = remember(blurPx) {
        if (supportsBlur) {
            val blur = android.graphics.RenderEffect.createBlurEffect(blurPx, blurPx, Shader.TileMode.CLAMP)
            val saturate = android.graphics.RenderEffect.createColorFilterEffect(
                ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(1.7f) })
            )
            android.graphics.RenderEffect.createChainEffect(saturate, blur).asComposeRenderEffect()
        } else null
    }

    this
        .shadow(
            elevation = elevation,
            shape = shape,
            clip = false,
            ambientColor = colors.glassShadow,
            spotColor = colors.glassShadow,
        )
        .onGloballyPositioned { position = it.positionInRoot() }
        .drawWithCache {
            val outline = shape.createOutline(size, layoutDirection, this)
            val path = Path().apply { addOutline(outline) }
            val rimWidth = 1.2.dp.toPx()
            val rim = Brush.verticalGradient(
                0f to colors.glassHighlight,
                0.45f to colors.glassEdge,
                1f to colors.glassHighlight.copy(alpha = colors.glassHighlight.alpha * 0.5f),
            )
            // Soft inner sheen across the top third, like light pooling on a droplet
            val sheen = Brush.verticalGradient(
                0f to Color.White.copy(alpha = if (colors.isDark) 0.10f else 0.35f),
                0.5f to Color.Transparent,
            )
            onDrawWithContent {
                if (canBlur && source != null) {
                    blurLayer.renderEffect = renderEffect
                    blurLayer.record {
                        translate(source.origin.x - position.x, source.origin.y - position.y) {
                            drawLayer(source.layer)
                        }
                    }
                    clipPath(path) { drawLayer(blurLayer) }
                }
                drawPath(path, fillTint)
                drawPath(path, sheen)
                drawPath(path, rim, style = Stroke(width = rimWidth))
                drawContent()
            }
        }
}

private fun Color.compositeOverOpaque(dark: Boolean): Color {
    val base = if (dark) Color(0xFF121411) else Color(0xFFF7F7EE)
    val a = alpha
    return Color(
        red = red * a + base.red * (1 - a),
        green = green * a + base.green * (1 - a),
        blue = blue * a + base.blue * (1 - a),
        alpha = 0.97f,
    )
}

/**
 * Progressive "gradient blur" for screen edges: the backdrop is blurred and
 * faded in with a vertical alpha mask, then a soft background-coloured scrim
 * sits on top so headers and the nav bar stay legible over busy content.
 *
 * @param fromTop true for the status-bar edge (strongest at the top),
 *                false for the bottom edge behind the nav bar.
 */
fun Modifier.edgeBlur(
    fromTop: Boolean,
    backdrop: GlassBackdrop? = null,
    blurRadius: Dp = 18.dp,
    scrimColor: Color,
    scrimAlpha: Float = 0.75f,
): Modifier = composed {
    val source = backdrop ?: LocalGlassBackdrop.current
    val layer = rememberGraphicsLayer()
    var position by remember { mutableStateOf(Offset.Zero) }
    val blurPx = with(LocalDensity.current) { blurRadius.toPx() }
    val effect = remember(blurPx) {
        if (supportsBlur) androidx.compose.ui.graphics.BlurEffect(blurPx, blurPx, androidx.compose.ui.graphics.TileMode.Clamp) else null
    }
    this
        .onGloballyPositioned { position = it.positionInRoot() }
        .graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
        .drawWithCache {
            val mask = Brush.verticalGradient(
                if (fromTop) listOf(Color.Black, Color.Black, Color.Transparent)
                else listOf(Color.Transparent, Color.Black, Color.Black)
            )
            val scrim = Brush.verticalGradient(
                if (fromTop) listOf(scrimColor.copy(alpha = scrimAlpha), scrimColor.copy(alpha = 0f))
                else listOf(scrimColor.copy(alpha = 0f), scrimColor.copy(alpha = scrimAlpha))
            )
            onDrawBehind {
                if (effect != null && source != null) {
                    layer.renderEffect = effect
                    layer.record {
                        translate(source.origin.x - position.x, source.origin.y - position.y) {
                            drawLayer(source.layer)
                        }
                    }
                    drawLayer(layer)
                    drawRect(mask, blendMode = androidx.compose.ui.graphics.BlendMode.DstIn)
                }
                drawRect(scrim)
            }
        }
}
