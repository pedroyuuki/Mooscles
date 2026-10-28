package com.mooscles.designsystem.gallery

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Density
import com.mooscles.designsystem.components.MChip
import com.mooscles.designsystem.components.MChipKind
import com.mooscles.designsystem.components.MText
import com.mooscles.designsystem.components.TopBar
import com.mooscles.designsystem.i18n.LocalAppLocale
import com.mooscles.designsystem.i18n.ProvideAppLocale
import com.mooscles.designsystem.resources.Res
import com.mooscles.designsystem.resources.gallery_controls_language
import com.mooscles.designsystem.resources.gallery_controls_motion
import com.mooscles.designsystem.resources.gallery_controls_scale
import com.mooscles.designsystem.resources.gallery_controls_theme
import com.mooscles.designsystem.resources.gallery_language_en
import com.mooscles.designsystem.resources.gallery_language_pt_br
import com.mooscles.designsystem.resources.gallery_language_system
import com.mooscles.designsystem.resources.gallery_reduce_motion
import com.mooscles.designsystem.resources.gallery_scale_100
import com.mooscles.designsystem.resources.gallery_scale_130
import com.mooscles.designsystem.resources.gallery_scale_200
import com.mooscles.designsystem.resources.gallery_theme_dark
import com.mooscles.designsystem.resources.gallery_theme_light
import com.mooscles.designsystem.resources.gallery_title
import com.mooscles.designsystem.theme.MTheme
import com.mooscles.designsystem.theme.MoosclesDesignTheme
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** The text scales the gallery offers, with their labels. */
enum class GalleryFontScale(val scale: Float, val label: StringResource) {
    Normal(1f, Res.string.gallery_scale_100),
    Large(1.3f, Res.string.gallery_scale_130),
    Largest(2f, Res.string.gallery_scale_200),
}

/** The languages the gallery offers. [tag] null is the system language. */
enum class GalleryLanguage(val tag: String?, val label: StringResource) {
    System(null, Res.string.gallery_language_system),
    English("en", Res.string.gallery_language_en),
    Portuguese("pt-BR", Res.string.gallery_language_pt_br),
}

/** Test tags of the gallery screen and its controls. */
object GalleryTags {
    const val SCREEN = "gallery-screen"
    const val REDUCED_MOTION = "gallery-reduced-motion"

    fun theme(dark: Boolean): String = if (dark) "gallery-theme-dark" else "gallery-theme-light"

    fun scale(scale: GalleryFontScale): String = "gallery-scale-${scale.name}"

    fun language(language: GalleryLanguage): String = "gallery-language-${language.name}"
}

/**
 * The top controls of the gallery. They apply to the open page (and the page list) and live
 * in memory only: nothing is saved, and they start over each time the gallery opens.
 */
@Stable
class GalleryControls(dark: Boolean) {
    var dark by mutableStateOf(dark)
    var fontScale by mutableStateOf(GalleryFontScale.Normal)
    var reducedMotion by mutableStateOf(false)
    var language by mutableStateOf(GalleryLanguage.System)
}

/** The open page and the controls. A host may hoist it to route the system back to [back]. */
@Stable
class GalleryState(initialPage: GalleryPage?, dark: Boolean) {
    var open by mutableStateOf(initialPage)
    val controls = GalleryControls(dark)

    /**
     * Closes the open page. On the page list it leaves instead: the system language comes back
     * first, while the gallery is still composed, and only then [leave] runs, so the screen that
     * comes next composes in the system language.
     */
    fun back(leave: () -> Unit) {
        if (open != null) {
            open = null
            return
        }
        restoreLanguage()
        leave()
    }

    /** Puts the system language back. Hosts call it when they navigate away by another path. */
    fun restoreLanguage() {
        controls.language = GalleryLanguage.System
        LocalAppLocale.restore()
    }
}

@Composable
fun rememberGalleryState(initialPage: GalleryPage? = null): GalleryState {
    val dark = MTheme.isDark
    return remember { GalleryState(initialPage, dark) }
}

/**
 * The gallery: the top bar, the controls (theme, text size, reduced motion and language) and
 * either the page list or the open page. Back closes the page, then leaves through [onBack].
 */
@Composable
fun GalleryScreen(
    onBack: () -> Unit,
    initialPage: GalleryPage? = null,
    state: GalleryState = rememberGalleryState(initialPage),
) {
    val controls = state.controls
    ProvideAppLocale(controls.language.tag) {
        Column(Modifier.fillMaxSize().background(MTheme.colors.bgBase).testTag(GalleryTags.SCREEN)) {
            val page = state.open
            TopBar(stringResource(page?.title ?: Res.string.gallery_title), onBack = { state.back(onBack) })
            Controls(controls)
            val density = LocalDensity.current
            MoosclesDesignTheme(dark = controls.dark, reducedMotion = controls.reducedMotion, haptics = MTheme.haptics) {
                CompositionLocalProvider(LocalDensity provides Density(density.density, controls.fontScale.scale)) {
                    Box(Modifier.weight(1f).fillMaxWidth().background(MTheme.colors.bgBase)) {
                        if (page == null) PageList(onOpen = { state.open = it }) else page.Content()
                    }
                }
            }
        }
    }
}

@Composable
private fun Controls(controls: GalleryControls) {
    Column(Modifier.fillMaxWidth()) {
        ChoiceRow(stringResource(Res.string.gallery_controls_theme)) {
            for (dark in listOf(false, true)) {
                MChip(
                    stringResource(if (dark) Res.string.gallery_theme_dark else Res.string.gallery_theme_light),
                    selected = controls.dark == dark,
                    onClick = { controls.dark = dark },
                    modifier = Modifier.testTag(GalleryTags.theme(dark)),
                    kind = MChipKind.Segmented,
                )
            }
        }
        ChoiceRow(stringResource(Res.string.gallery_controls_scale)) {
            for (scale in GalleryFontScale.entries) {
                MChip(
                    stringResource(scale.label),
                    selected = controls.fontScale == scale,
                    onClick = { controls.fontScale = scale },
                    modifier = Modifier.testTag(GalleryTags.scale(scale)),
                    kind = MChipKind.Segmented,
                )
            }
        }
        ChoiceRow(stringResource(Res.string.gallery_controls_language)) {
            for (language in GalleryLanguage.entries) {
                MChip(
                    stringResource(language.label),
                    selected = controls.language == language,
                    onClick = { controls.language = language },
                    modifier = Modifier.testTag(GalleryTags.language(language)),
                    kind = MChipKind.Segmented,
                )
            }
        }
        ChoiceRow(stringResource(Res.string.gallery_controls_motion)) {
            MChip(
                stringResource(Res.string.gallery_reduce_motion),
                selected = controls.reducedMotion,
                onClick = { controls.reducedMotion = !controls.reducedMotion },
                modifier = Modifier.testTag(GalleryTags.REDUCED_MOTION),
            )
        }
    }
}

/**
 * A label and its choices on one line, which scrolls sideways when the text is large. One line
 * per control keeps the page below as tall as possible.
 */
@Composable
private fun ChoiceRow(label: String, choices: @Composable RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = MTheme.space.s4),
        horizontalArrangement = Arrangement.spacedBy(MTheme.space.s2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MText(label, MTheme.type.labelMd, color = MTheme.colors.textSecondary)
        choices()
    }
}

@Composable
private fun PageList(onOpen: (GalleryPage) -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = MTheme.space.s4),
        verticalArrangement = Arrangement.spacedBy(MTheme.space.s2),
    ) {
        for (entry in GalleryPage.entries) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .sizeIn(minHeight = MTheme.targets.min)
                    .testTag(galleryEntryTag(entry))
                    .clickable(role = Role.Button) { onOpen(entry) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MText(stringResource(entry.title), MTheme.type.titleSm, color = MTheme.colors.textPrimary)
            }
        }
    }
}
