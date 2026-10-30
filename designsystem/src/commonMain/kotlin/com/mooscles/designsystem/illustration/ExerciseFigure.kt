package com.mooscles.designsystem.illustration

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import com.mooscles.designsystem.interaction.mFocusRing
import com.mooscles.designsystem.resources.Res
import com.mooscles.designsystem.resources.ds_figure_label
import com.mooscles.designsystem.resources.ds_figure_show_end
import com.mooscles.designsystem.resources.ds_figure_show_start
import com.mooscles.designsystem.theme.MTheme
import com.mooscles.designsystem.tokens.MoosclesColors
import com.mooscles.designsystem.tokens.MoosclesIllustration
import kotlin.math.min
import org.jetbrains.compose.resources.stringResource

/** Test-facing: the frame an [AnimatedExerciseFigure] shows, `"start"` or `"end"`. */
val FigureFrameKey = SemanticsPropertyKey<String>("FigureFrame")

/** Test-facing: reads the opacity the shown frame is drawn with right now (1 when the figure is still). */
val FigureFrameAlpha = SemanticsPropertyKey<() -> Float>("FigureFrameAlpha")

const val AnimatedFigureTag = "animated-figure"

/**
 * Test hook: called with the name of each scope of an [AnimatedExerciseFigure] on every composition
 * of it: [FigureScopeOuter] (label, tap, visibility), [FigureScopeAlternation] (the infinite
 * transition) and [FigureScopeLayer] (the frames). Null outside tests.
 */
internal val LocalFigureLayerProbe = staticCompositionLocalOf<((String) -> Unit)?> { null }

internal const val FigureScopeOuter = "outer"
internal const val FigureScopeAlternation = "alternation"
internal const val FigureScopeLayer = "layer"

// Inline, so it runs in the caller's scope: a function of its own would be skipped on recomposition.
@Suppress("NOTHING_TO_INLINE")
@Composable
private inline fun ProbeComposition(scope: String) {
    val probe = LocalFigureLayerProbe.current
    if (probe != null) SideEffect { probe(scope) }
}

/**
 * One still frame of an exercise: [pose] drawn with forward kinematics in a square that fits the
 * component (give it a size with [modifier]), on a transparent background. The figure is
 * `text.secondary`, the far limbs behind it `text.tertiary`, the equipment `text.tertiary` and the
 * [highlights] `accent.solid`, all with round caps. The geometry is rebuilt only when the size or
 * the pose changes; nothing is drawn to a bitmap. [contentDescription] null makes it decorative.
 */
@Composable
fun ExerciseFigure(
    pose: FigurePose,
    highlights: Set<FigureSegment>,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    val palette = figurePalette(MTheme.colors)
    val illustration = MTheme.illustration
    val solved = remember(pose) {
        val joints = FigureKinematics.solve(pose).joints
        joints to FigureKinematics.equipment(pose, joints)
    }
    val draw = remember(solved, highlights, palette, illustration) {
        Modifier.drawWithCache {
            val operations = figureOperations(solved.first, solved.second, highlights, palette, illustration, size)
            onDrawBehind { for (operation in operations) operation.draw(this) }
        }
    }
    val described = if (contentDescription != null) {
        Modifier.semantics {
            this.contentDescription = contentDescription
            role = Role.Image
        }
    } else {
        Modifier
    }
    Spacer(modifier.then(described).then(draw))
}

/**
 * An exercise's two frames, [start] and [end], for the detail screen (spec D5). They alternate
 * every `timing.illustrationFrame` with a `motion.base` cross-fade. The alternation stops on the
 * end frame, whole, with reduced motion, while not [foreground] and while the figure is scrolled
 * out of view, and starts again when that ends. With reduced motion a tap switches the frames
 * (the action says which one comes); while it alternates there is no tap action. The label names
 * [exerciseName]: the figure is an image with that label ([Role.Image]), or a button with it while
 * a tap switches the frames. Only the alternation and its frame layer recompose, once per frame
 * change; the cross-fade itself is read in the draw phase. Give it a size with [modifier].
 *
 * In tests: the alternation is an infinite transition, which the test clock cancels for good while
 * it advances on its own (`mainClock.autoAdvance = true`, the default). Such a test sees the figure
 * still on the end frame, as with reduced motion. A test that needs the alternation sets
 * `mainClock.autoAdvance = false` before the figure first composes and advances the clock by hand;
 * stopping the clock after the figure is shown does not bring the alternation back.
 */
@Composable
fun AnimatedExerciseFigure(
    start: FigurePose,
    end: FigurePose,
    highlights: Set<FigureSegment>,
    exerciseName: String,
    modifier: Modifier = Modifier,
    foreground: Boolean = true,
) {
    ProbeComposition(FigureScopeOuter)
    val label = stringResource(Res.string.ds_figure_label, exerciseName)
    val reducedMotion = MTheme.reducedMotion
    var onScreen by remember { mutableStateOf(true) }
    // Every switch to reduced motion starts again on the end frame.
    var showStart by remember(reducedMotion) { mutableStateOf(false) }
    val alternating = !reducedMotion && foreground && onScreen
    val toggle = if (reducedMotion) {
        val source = remember { MutableInteractionSource() }
        val action = stringResource(if (showStart) Res.string.ds_figure_show_end else Res.string.ds_figure_show_start)
        Modifier
            .clickable(interactionSource = source, indication = null, onClickLabel = action, role = Role.Button) {
                showStart = !showStart
            }
            .mFocusRing(RoundedCornerShape(MTheme.radius.md), source)
    } else {
        Modifier
    }
    Box(
        modifier
            .testTag(AnimatedFigureTag)
            // Clipped away by a scrolling parent, the bounds in the root are empty.
            .onGloballyPositioned { onScreen = !it.boundsInRoot().isEmpty }
            .then(toggle)
            .semantics(mergeDescendants = true) {
                contentDescription = label
                // With reduced motion the tap already makes it a button.
                if (!reducedMotion) role = Role.Image
            },
    ) {
        if (alternating) {
            AlternatingFrames(start, end, highlights)
        } else {
            FrameLayer(start, end, highlights, showingStart = showStart, startAlpha = null)
        }
    }
}

/**
 * The alternation: the start frame's opacity holds at 0 for a frame time, fades in, holds at 1,
 * fades out. An infinite transition, not a delay loop, so the test clock and its infinite animation
 * policy control it (with `autoAdvance` on, tests see it stopped on the end frame).
 */
@Composable
private fun AlternatingFrames(start: FigurePose, end: FigurePose, highlights: Set<FigureSegment>) {
    ProbeComposition(FigureScopeAlternation)
    val hold = MTheme.timing.illustrationFrame.toInt()
    val fade = MTheme.motion.base
    val easing = MTheme.motion.easeInOut
    val startAlpha = rememberInfiniteTransition(label = "figure").animateFloat(
        initialValue = 0f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            keyframes {
                durationMillis = 2 * (hold + fade)
                0f at hold using easing
                1f at hold + fade
                1f at 2 * hold + fade using easing
            },
            RepeatMode.Restart,
        ),
        label = "figureStartAlpha",
    )
    val showingStart by remember(startAlpha) { derivedStateOf { startAlpha.value >= 0.5f } }
    FrameLayer(start, end, highlights, showingStart, startAlpha)
}

/**
 * The frames themselves. [startAlpha] null is a still figure: only the shown frame, opaque.
 * Otherwise both frames, cross-faded by [startAlpha] in their graphics layers.
 */
@Composable
private fun FrameLayer(
    start: FigurePose,
    end: FigurePose,
    highlights: Set<FigureSegment>,
    showingStart: Boolean,
    startAlpha: State<Float>?,
) {
    ProbeComposition(FigureScopeLayer)
    val shownAlpha: () -> Float = when {
        startAlpha == null -> ({ 1f })
        showingStart -> ({ startAlpha.value })
        else -> ({ 1f - startAlpha.value })
    }
    Box(
        Modifier.fillMaxSize().semantics {
            this[FigureFrameKey] = if (showingStart) "start" else "end"
            this[FigureFrameAlpha] = shownAlpha
        },
    ) {
        if (startAlpha == null) {
            ExerciseFigure(if (showingStart) start else end, highlights, contentDescription = null, Modifier.fillMaxSize())
        } else {
            val startLayer = remember(startAlpha) { Modifier.fillMaxSize().graphicsLayer { alpha = startAlpha.value } }
            val endLayer = remember(startAlpha) { Modifier.fillMaxSize().graphicsLayer { alpha = 1f - startAlpha.value } }
            ExerciseFigure(start, highlights, contentDescription = null, startLayer)
            ExerciseFigure(end, highlights, contentDescription = null, endLayer)
        }
    }
}

/** The four colors of a figure. */
@Immutable
internal data class FigurePalette(val body: Color, val far: Color, val equipment: Color, val highlight: Color)

/** Spec D3: the figure in `text.secondary`, the far limbs and the equipment in `text.tertiary`, the highlight in `accent.solid`. */
internal fun figurePalette(colors: MoosclesColors) = FigurePalette(
    body = colors.textSecondary,
    far = colors.textTertiary,
    equipment = colors.textTertiary,
    highlight = colors.accentSolid,
)

/** One drawing step, already in pixels. */
private sealed interface FigureOperation {
    fun draw(scope: DrawScope)

    class Line(val start: Offset, val end: Offset, val color: Color, val width: Float) : FigureOperation {
        override fun draw(scope: DrawScope) = scope.drawLine(color, start, end, strokeWidth = width, cap = StrokeCap.Round)
    }

    class Ring(val center: Offset, val radius: Float, val color: Color, val width: Float) : FigureOperation {
        override fun draw(scope: DrawScope) = scope.drawCircle(color, radius, center, style = Stroke(width))
    }

    class Disc(val center: Offset, val radius: Float, val color: Color) : FigureOperation {
        override fun draw(scope: DrawScope) = scope.drawCircle(color, radius, center)
    }

    class Frame(val topLeft: Offset, val size: Size, val color: Color, val width: Float) : FigureOperation {
        override fun draw(scope: DrawScope) = scope.drawRect(color, topLeft, size, style = Stroke(width))
    }
}

/** Which side of a segment a highlight covers: all of it, or the half toward the front or the back. */
private enum class Side { ALL, FRONT, BACK }

/**
 * The drawing steps of a solved pose in a [size] canvas: the 100-unit box is the largest centered
 * square. Back to front: far leg and arm, equipment, trunk, neck and head, near leg, near arm; each
 * segment's highlights right after it, so a limb in front covers the trunk's highlight.
 */
private fun figureOperations(
    joints: Map<FigureJoint, Offset>,
    equipment: List<PropShape>,
    highlights: Set<FigureSegment>,
    palette: FigurePalette,
    illustration: MoosclesIllustration,
    size: Size,
): List<FigureOperation> {
    val side = min(size.width, size.height)
    val scale = side / BOX
    val origin = Offset((size.width - side) / 2f, (size.height - side) / 2f)
    fun px(point: Offset) = origin + point * scale
    fun at(joint: FigureJoint) = px(joints.getValue(joint))
    val stroke = illustration.figureStroke * side
    val thin = illustration.figureHeadStroke * side
    val operations = mutableListOf<FigureOperation>()

    fun bone(from: FigureJoint, to: FigureJoint, color: Color) {
        operations += FigureOperation.Line(at(from), at(to), color, stroke)
    }

    /**
     * Highlights [from]–[to] between the fractions [t0] and [t1]. A side covers half the stroke:
     * a line half as wide, moved a quarter stroke toward that side, so it stays inside the capsule.
     * [limb] says the segment points away from the trunk, which turns its front the other way.
     */
    fun highlight(from: FigureJoint, to: FigureJoint, t0: Float, t1: Float, which: Side, limb: Boolean) {
        val a = at(from)
        val b = at(to)
        val along = b - a
        val start = a + along * t0
        val end = a + along * t1
        if (which == Side.ALL) {
            operations += FigureOperation.Line(start, end, palette.highlight, stroke)
            return
        }
        val length = along.getDistance()
        if (length == 0f) return
        val unit = along / length
        // Clockwise of the direction (y down) is the front of the trunk and the back of a limb.
        val clockwise = Offset(-unit.y, unit.x)
        val toFront = if (limb) -clockwise else clockwise
        val normal = if (which == Side.FRONT) toFront else -toFront
        val shift = normal * (stroke / 4f)
        operations += FigureOperation.Line(start + shift, end + shift, palette.highlight, stroke / 2f)
    }

    // Far side, behind everything.
    bone(FigureJoint.HIP, FigureJoint.KNEE_FAR, palette.far)
    bone(FigureJoint.KNEE_FAR, FigureJoint.ANKLE_FAR, palette.far)
    bone(FigureJoint.ANKLE_FAR, FigureJoint.TOE_FAR, palette.far)
    bone(FigureJoint.SHOULDER, FigureJoint.ELBOW_FAR, palette.far)
    bone(FigureJoint.ELBOW_FAR, FigureJoint.HAND_FAR, palette.far)

    for (shape in equipment) {
        operations += when (shape) {
            is PropShape.Line -> FigureOperation.Line(px(shape.start), px(shape.end), palette.equipment, thin)
            is PropShape.Circle -> FigureOperation.Ring(px(shape.center), shape.radius * scale, palette.equipment, thin)
            is PropShape.Disc -> FigureOperation.Disc(px(shape.center), shape.radius * scale, palette.equipment)
            is PropShape.Box -> FigureOperation.Frame(
                px(shape.rect.topLeft), Size(shape.rect.width * scale, shape.rect.height * scale), palette.equipment, thin,
            )
        }
    }

    bone(FigureJoint.HIP, FigureJoint.SHOULDER, palette.body)
    if (FigureSegment.TORSO_FRONT in highlights) highlight(FigureJoint.HIP, FigureJoint.SHOULDER, 0f, 1f, Side.FRONT, limb = false)
    if (FigureSegment.TORSO_BACK in highlights) highlight(FigureJoint.HIP, FigureJoint.SHOULDER, 0f, 1f, Side.BACK, limb = false)
    bone(FigureJoint.SHOULDER, FigureJoint.NECK, palette.body)
    operations += FigureOperation.Ring(at(FigureJoint.HEAD), FigureSkeleton.HEAD_RADIUS * scale, palette.body, thin)

    bone(FigureJoint.HIP, FigureJoint.KNEE_NEAR, palette.body)
    if (FigureSegment.THIGH_FRONT in highlights) highlight(FigureJoint.HIP, FigureJoint.KNEE_NEAR, 0f, 1f, Side.FRONT, limb = true)
    if (FigureSegment.THIGH_BACK in highlights) highlight(FigureJoint.HIP, FigureJoint.KNEE_NEAR, GLUTE_END, 1f, Side.BACK, limb = true)
    if (FigureSegment.HIP in highlights) highlight(FigureJoint.HIP, FigureJoint.KNEE_NEAR, 0f, GLUTE_END, Side.ALL, limb = true)
    bone(FigureJoint.KNEE_NEAR, FigureJoint.ANKLE_NEAR, palette.body)
    if (FigureSegment.SHIN_BACK in highlights) highlight(FigureJoint.KNEE_NEAR, FigureJoint.ANKLE_NEAR, 0f, CALF_END, Side.BACK, limb = true)
    bone(FigureJoint.ANKLE_NEAR, FigureJoint.TOE_NEAR, palette.body)

    bone(FigureJoint.SHOULDER, FigureJoint.ELBOW_NEAR, palette.body)
    if (FigureSegment.UPPER_ARM_FRONT in highlights) highlight(FigureJoint.SHOULDER, FigureJoint.ELBOW_NEAR, 0f, 1f, Side.FRONT, limb = true)
    if (FigureSegment.UPPER_ARM_BACK in highlights) highlight(FigureJoint.SHOULDER, FigureJoint.ELBOW_NEAR, 0f, 1f, Side.BACK, limb = true)
    if (FigureSegment.SHOULDER in highlights) highlight(FigureJoint.SHOULDER, FigureJoint.ELBOW_NEAR, 0f, DELTOID_END, Side.ALL, limb = true)
    bone(FigureJoint.ELBOW_NEAR, FigureJoint.HAND_NEAR, palette.body)
    if (FigureSegment.FOREARM in highlights) highlight(FigureJoint.ELBOW_NEAR, FigureJoint.HAND_NEAR, 0f, 1f, Side.ALL, limb = true)
    return operations
}

/** The side of the pose box, in pose units. */
private const val BOX = 100f

/**
 * Where on its bone each partial highlight ends (fraction from the joint nearer the trunk): pose
 * data, not measures. The deltoid and the glutes wrap their joint, so they light the whole width of
 * the limb's first 40%; the hamstrings take the back of the rest of the thigh, the calf the back of
 * the upper 60% of the shin. Tuned on the pilot poses (Task 8): a back-only glute band of 35% was
 * too small to see at 64 dp, with the hip thrust's thigh level.
 */
private const val DELTOID_END = 0.4f
private const val GLUTE_END = 0.4f
private const val CALF_END = 0.6f
