package com.mooscles.designsystem.spike

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.mooscles.designsystem.tokens.MoosclesColors

// Throwaway spike S1 (phase 3): can the workout share card be drawn with common canvas APIs
// (CanvasDrawScope + TextMeasurer into an ImageBitmap), off the main thread? Never merged.

data class SpikeExercise(val name: String, val best: String, val record: Boolean = false)

data class SpikeCard(
    val title: String,
    val date: String,
    val duration: String,
    val sets: String,
    val volume: String,
    val records: List<String>,
    val exercises: List<SpikeExercise>,
)

/** Styles resolved in composition (fonts of the design system); drawing runs outside it. */
class SpikeCardStyles(
    val brand: TextStyle,
    val title: TextStyle,
    val subtitle: TextStyle,
    val number: TextStyle,
    val numberLabel: TextStyle,
    val rowName: TextStyle,
    val rowDetail: TextStyle,
    val record: TextStyle,
)

const val SPIKE_CARD_WIDTH = 1080
private const val MARGIN = 72
private const val MAX_PAGE_HEIGHT = 1350
private const val ROW_GAP = 28
private const val NAME_WIDTH = 600

/** Rows (text lines) the exercise [name] takes at the card's name column width. Used for the layout signature. */
fun spikeRowLines(name: String, measurer: TextMeasurer, styles: SpikeCardStyles, density: Density): Int =
    measurer.measure(
        name, styles.rowName, constraints = Constraints(maxWidth = NAME_WIDTH), density = density,
    ).lineCount

private class Placed(val layout: TextLayoutResult, val x: Float, val y: Float, val color: androidx.compose.ui.graphics.Color)

private class PagePlan(val items: MutableList<Placed> = mutableListOf(), var height: Int = 0)

/** Draws the card as 1080 px wide pages, each at most [MAX_PAGE_HEIGHT] px tall. */
fun paintSpikeCard(
    card: SpikeCard,
    measurer: TextMeasurer,
    styles: SpikeCardStyles,
    density: Density,
): List<ImageBitmap> {
    val c = MoosclesColors.Dark
    val inner = SPIKE_CARD_WIDTH - 2 * MARGIN
    fun m(text: String, style: TextStyle, width: Int = inner) =
        measurer.measure(text, style, constraints = Constraints(maxWidth = width), density = density)

    val pages = mutableListOf(PagePlan())
    var y = MARGIN
    fun place(layout: TextLayoutResult, x: Int, color: androidx.compose.ui.graphics.Color) {
        pages.last().items += Placed(layout, x.toFloat(), y.toFloat(), color)
    }

    // Header (first page only): brand, title, date, then the three stats.
    m("MOOSCLES", styles.brand).also { place(it, MARGIN, c.accentText); y += it.size.height + 36 }
    m(card.title, styles.title).also { place(it, MARGIN, c.textPrimary); y += it.size.height + 8 }
    m(card.date, styles.subtitle).also { place(it, MARGIN, c.textSecondary); y += it.size.height + 56 }
    val third = inner / 3
    listOf(
        card.duration to "DURATION", card.sets to "SETS", card.volume to "VOLUME",
    ).forEachIndexed { i, (value, label) ->
        val v = m(value, styles.number, third)
        val l = m(label, styles.numberLabel, third)
        pages.last().items += Placed(v, (MARGIN + i * third).toFloat(), y.toFloat(), c.textPrimary)
        pages.last().items += Placed(l, (MARGIN + i * third).toFloat(), (y + v.size.height + 4).toFloat(), c.textSecondary)
    }
    y += 150
    if (card.records.isNotEmpty()) {
        for (r in card.records) {
            m("PR  $r", styles.record).also { place(it, MARGIN, c.prSolid); y += it.size.height + 10 }
        }
        y += 24
    }
    pages.last().height = y

    // Exercise rows; a new page starts when the next row would pass MAX_PAGE_HEIGHT.
    for (ex in card.exercises) {
        val name = m(ex.name, styles.rowName, NAME_WIDTH)
        val best = m(ex.best, styles.rowDetail, inner - NAME_WIDTH - 24)
        val rowHeight = maxOf(name.size.height, best.size.height)
        if (y + rowHeight > MAX_PAGE_HEIGHT - MARGIN) {
            pages.last().height = y
            pages += PagePlan()
            y = MARGIN
        }
        place(name, MARGIN, if (ex.record) c.prSolid else c.textPrimary)
        pages.last().items += Placed(best, (MARGIN + NAME_WIDTH + 24).toFloat(), y.toFloat(), c.textSecondary)
        y += rowHeight + ROW_GAP
    }
    pages.last().height = y + MARGIN - ROW_GAP

    return pages.map { page ->
        val height = page.height.coerceAtLeast(1)
        val bitmap = ImageBitmap(SPIKE_CARD_WIDTH, height)
        val size = Size(SPIKE_CARD_WIDTH.toFloat(), height.toFloat())
        CanvasDrawScope().draw(density, LayoutDirection.Ltr, Canvas(bitmap), size) {
            drawRect(c.bgBase, size = size)
            for (p in page.items) drawPlaced(p)
        }
        bitmap
    }
}

private fun DrawScope.drawPlaced(p: Placed) {
    drawText(p.layout, color = p.color, topLeft = Offset(p.x, p.y))
}

/** The fixture: 6 exercises (one with a long name), 2 records, 18 sets. */
fun spikeFixture() = SpikeCard(
    title = "Upper body A",
    date = "Wed, Oct 29 · 18:02–18:44",
    duration = "42:18",
    sets = "18",
    volume = "12,480 kg",
    records = listOf("Bench press 100 kg", "Barbell row 8 x 80 kg"),
    exercises = listOf(
        SpikeExercise("Bench press", "100 kg x 5", record = true),
        SpikeExercise("Barbell row", "80 kg x 8", record = true),
        SpikeExercise("Agachamento búlgaro com halteres e pausa", "24 kg x 10"),
        SpikeExercise("Overhead press", "50 kg x 6"),
        SpikeExercise("Lat pulldown", "65 kg x 10"),
        SpikeExercise("Cable curl", "30 kg x 12"),
    ),
)
