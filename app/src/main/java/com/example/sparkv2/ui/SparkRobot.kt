package com.example.sparkv2.ui

import android.provider.Settings
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.sparkv2.R
import com.example.sparkv2.ui.theme.SparkBlue
import com.example.sparkv2.ui.theme.SparkBlueDeep
import com.example.sparkv2.ui.theme.SparkBlueGlow
import com.example.sparkv2.ui.theme.SparkCyan

/**
 * Friendly hovering droid mascot for SparkV2.
 *
 * A polished little assistant bot: a glossy rounded body with a dark glass
 * "screen face" showing two glowing eyes and a soft smile. It bobs gently,
 * blinks, and casts a parallax ground shadow. When [active] it perks up,
 * glows brighter, and blinks/bobs faster.
 */
@Composable
fun SparkRobotMascot(
    modifier: Modifier = Modifier,
    size: Dp = 160.dp,
    active: Boolean = false,
) {
    if (rememberAnimatorEnabled()) {
        SparkRobotMascotAnimated(modifier, size, active)
    } else {
        SparkRobotMascotStatic(modifier, size, active)
    }
}

@Composable
private fun rememberAnimatorEnabled(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            ) > 0f
        }.getOrDefault(true)
    }
}

@Composable
private fun SparkRobotMascotAnimated(
    modifier: Modifier,
    size: Dp,
    active: Boolean,
) {
    val transition = rememberInfiniteTransition(label = "spark_robot")
    val bob by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (active) 1700 else 2900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "bob",
    )
    val eyeGlow by transition.animateFloat(
        initialValue = if (active) 0.7f else 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (active) 1100 else 2200),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "eyeGlow",
    )
    val blink by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = if (active) 2600 else 4200
                1f at 0
                1f at (if (active) 2300 else 3900)
                0.08f at (if (active) 2440 else 4040)
                1f at (if (active) 2560 else 4160)
            },
            repeatMode = RepeatMode.Restart,
        ),
        label = "blink",
    )
    SparkRobotCanvas(modifier, size, active, bob, eyeGlow, blink)
}

@Composable
private fun SparkRobotMascotStatic(
    modifier: Modifier,
    size: Dp,
    active: Boolean,
) {
    SparkRobotCanvas(
        modifier = modifier,
        size = size,
        active = active,
        bob = 0.5f,
        eyeGlow = if (active) 0.85f else 0.6f,
        blink = 1f,
    )
}

@Composable
private fun SparkRobotCanvas(
    modifier: Modifier,
    size: Dp,
    active: Boolean,
    bob: Float,
    eyeGlow: Float,
    blink: Float,
) {
    val mascotDescription = stringResource(R.string.mascot_content_description)
    val geometry = remember(size) { RobotGeometry() }

    Canvas(
        modifier = modifier
            .size(size)
            .semantics { contentDescription = mascotDescription },
    ) {
        val w = this.size.width
        val h = this.size.height
        geometry.ensureLayout(w, h)
        val cx = w / 2f
        val bobAmt = (if (active) 0.030f else 0.018f) * h
        val bobOffset = (bob - 0.5f) * 2f * bobAmt

        val lift = (bobOffset + bobAmt) / (2f * bobAmt)
        val shadowW = w * (0.40f - 0.06f * lift)
        val shadowAlpha = 0.28f - 0.10f * lift
        drawOval(
            brush = Brush.radialGradient(
                colors = listOf(Color.Black.copy(alpha = shadowAlpha), Color.Transparent),
                center = Offset(cx, h * 0.92f),
                radius = shadowW,
            ),
            topLeft = Offset(cx - shadowW, h * 0.92f - shadowW * 0.28f),
            size = Size(shadowW * 2f, shadowW * 0.56f),
        )

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    SparkBlue.copy(alpha = if (active) 0.34f else 0.16f),
                    Color.Transparent,
                ),
                center = Offset(cx, h * 0.45f),
                radius = w * 0.55f,
            ),
            radius = w * 0.55f,
            center = Offset(cx, h * 0.45f),
        )

        translate(left = 0f, top = bobOffset) {
            drawRobotBody(w, h, cx, active, eyeGlow, blink, geometry)
        }
    }
}

private fun DrawScope.drawRobotBody(
    w: Float,
    h: Float,
    cx: Float,
    active: Boolean,
    eyeGlow: Float,
    blink: Float,
    geometry: RobotGeometry,
) {
    val headTop = h * 0.20f
    val tip = Offset(cx, headTop - h * 0.085f)
    drawLine(
        color = SparkBlueDeep,
        start = Offset(cx, headTop + h * 0.01f),
        end = tip,
        strokeWidth = w * 0.022f,
        cap = StrokeCap.Round,
    )
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(SparkCyan.copy(alpha = eyeGlow * 0.9f), Color.Transparent),
            center = tip,
            radius = w * 0.075f,
        ),
        radius = w * 0.075f,
        center = tip,
    )
    drawCircle(color = SparkCyan, radius = w * 0.032f, center = tip)
    drawCircle(
        color = Color.White.copy(alpha = 0.85f),
        radius = w * 0.012f,
        center = Offset(tip.x - w * 0.008f, tip.y - w * 0.008f),
    )

    val armY = h * 0.62f
    drawArmPath(geometry.leftArmPath, cx - w * 0.30f, armY, w * 0.075f, left = true)
    drawArmPath(geometry.rightArmPath, cx + w * 0.30f, armY, w * 0.075f, left = false)

    val bodyLeft = geometry.bodyLeft
    val bodyTopY = geometry.bodyTopY
    val bodyH = geometry.bodyH
    val bodyW = geometry.bodyW

    drawPath(
        geometry.bodyPath,
        color = SparkBlueDeep.copy(alpha = 0.55f),
        style = Stroke(width = w * 0.03f),
    )
    drawPath(
        geometry.bodyPath,
        brush = Brush.linearGradient(
            colors = listOf(SparkBlueGlow, SparkBlue, SparkBlueDeep),
            start = Offset(bodyLeft, bodyTopY),
            end = Offset(bodyLeft + bodyW, bodyTopY + bodyH),
        ),
    )
    drawPath(
        geometry.sheenPath,
        brush = Brush.verticalGradient(
            colors = listOf(Color.White.copy(alpha = 0.30f), Color.Transparent),
            startY = bodyTopY + bodyH * 0.05f,
            endY = bodyTopY + bodyH * 0.35f,
        ),
    )

    val faceTop = geometry.faceTop
    val faceH = geometry.faceH
    val faceW = geometry.faceW
    drawPath(
        geometry.facePath,
        brush = Brush.verticalGradient(
            colors = listOf(Color(0xFF0A1424), Color(0xFF050A14)),
            startY = faceTop,
            endY = faceTop + faceH,
        ),
    )
    drawPath(
        geometry.facePath,
        color = Color.White.copy(alpha = 0.08f),
        style = Stroke(width = w * 0.012f),
    )

    val eyeR = faceH * 0.28f
    val eyeCy = faceTop + faceH * 0.44f
    val eyeDx = faceW * 0.22f
    drawEye(Offset(cx - eyeDx, eyeCy), eyeR, blink, eyeGlow)
    drawEye(Offset(cx + eyeDx, eyeCy), eyeR, blink, eyeGlow)

    drawPath(
        geometry.smilePath,
        color = SparkCyan.copy(alpha = 0.55f + eyeGlow * 0.35f),
        style = Stroke(width = w * 0.018f, cap = StrokeCap.Round),
    )

    val chestCy = bodyTopY + bodyH * 0.74f
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(SparkCyan.copy(alpha = eyeGlow), SparkBlueDeep),
            center = Offset(cx, chestCy),
            radius = w * 0.05f,
        ),
        radius = w * 0.05f,
        center = Offset(cx, chestCy),
    )
    drawCircle(
        color = Color.White.copy(alpha = 0.7f),
        radius = w * 0.014f,
        center = Offset(cx - w * 0.012f, chestCy - w * 0.012f),
    )
    val ventColor = SparkBlueDeep.copy(alpha = 0.5f)
    for (i in -1..1) {
        val vy = chestCy + i * h * 0.035f
        drawLine(ventColor, Offset(cx - w * 0.20f, vy), Offset(cx - w * 0.12f, vy), strokeWidth = w * 0.012f, cap = StrokeCap.Round)
        drawLine(ventColor, Offset(cx + w * 0.12f, vy), Offset(cx + w * 0.20f, vy), strokeWidth = w * 0.012f, cap = StrokeCap.Round)
    }
}

private class RobotGeometry {
    val bodyPath = Path()
    val sheenPath = Path()
    val facePath = Path()
    val smilePath = Path()
    val leftArmPath = Path()
    val rightArmPath = Path()

    var bodyLeft = 0f
        private set
    var bodyTopY = 0f
        private set
    var bodyH = 0f
        private set
    var bodyW = 0f
        private set
    var faceTop = 0f
        private set
    var faceH = 0f
        private set
    var faceW = 0f
        private set

    private var lastW = 0f
    private var lastH = 0f

    fun ensureLayout(w: Float, h: Float) {
        if (w == lastW && h == lastH) return
        lastW = w
        lastH = h
        rebuild(w, h)
    }

    private fun rebuild(w: Float, h: Float) {
        val cx = w / 2f
        val headTop = h * 0.20f
        bodyW = w * 0.56f
        bodyTopY = headTop + h * 0.02f
        bodyH = h * 0.62f
        bodyLeft = cx - bodyW / 2f
        val bodyRadius = bodyW * 0.34f

        bodyPath.reset()
        bodyPath.addRoundRect(
            RoundRect(
                Rect(Offset(bodyLeft, bodyTopY), Size(bodyW, bodyH)),
                CornerRadius(bodyRadius),
            ),
        )

        sheenPath.reset()
        sheenPath.addRoundRect(
            RoundRect(
                Rect(
                    Offset(bodyLeft + bodyW * 0.10f, bodyTopY + bodyH * 0.05f),
                    Size(bodyW * 0.80f, bodyH * 0.30f),
                ),
                CornerRadius(bodyW * 0.28f),
            ),
        )

        faceW = bodyW * 0.78f
        faceH = bodyH * 0.40f
        val faceLeft = cx - faceW / 2f
        faceTop = bodyTopY + bodyH * 0.10f
        facePath.reset()
        facePath.addRoundRect(
            RoundRect(
                Rect(Offset(faceLeft, faceTop), Size(faceW, faceH)),
                CornerRadius(faceH * 0.42f),
            ),
        )

        smilePath.reset()
        val sy = faceTop + faceH * 0.74f
        smilePath.moveTo(cx - faceW * 0.15f, sy)
        smilePath.quadraticBezierTo(cx, sy + faceH * 0.16f, cx + faceW * 0.15f, sy)

        rebuildArmPath(leftArmPath, w * 0.075f, h * 0.13f, left = true)
        rebuildArmPath(rightArmPath, w * 0.075f, h * 0.13f, left = false)
    }

    private fun rebuildArmPath(path: Path, width: Float, height: Float, left: Boolean) {
        path.reset()
        path.addRoundRect(
            RoundRect(
                Rect(Offset(-width / 2f, -height / 2f), Size(width, height)),
                CornerRadius(width * 0.5f),
            ),
        )
    }
}

private fun DrawScope.drawArmPath(armPath: Path, cx: Float, cy: Float, width: Float, left: Boolean) {
    val height = armPath.getBounds().height
    translate(left = cx, top = cy) {
        drawPath(
            armPath,
            brush = Brush.linearGradient(
                colors = if (left) listOf(SparkBlue, SparkBlueDeep) else listOf(SparkBlueGlow, SparkBlue),
                start = Offset(-width / 2f, -height / 2f),
                end = Offset(width / 2f, height / 2f),
            ),
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(SparkBlueGlow, SparkBlueDeep),
                center = Offset(0f, height / 2f),
                radius = width * 0.7f,
            ),
            radius = width * 0.62f,
            center = Offset(0f, height / 2f),
        )
    }
}

private fun DrawScope.drawEye(center: Offset, radius: Float, blink: Float, glow: Float) {
    scale(scaleX = 1f, scaleY = blink.coerceAtLeast(0.08f), pivot = center) {
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color.White, SparkCyan, SparkBlueGlow.copy(alpha = 0.0f)),
                center = Offset(center.x, center.y - radius * 0.2f),
                radius = radius * 1.15f,
            ),
            radius = radius,
            center = center,
        )
        drawCircle(
            color = SparkCyan.copy(alpha = glow * 0.45f),
            radius = radius * 1.25f,
            center = center,
            style = Stroke(width = radius * 0.18f),
        )
    }
}
