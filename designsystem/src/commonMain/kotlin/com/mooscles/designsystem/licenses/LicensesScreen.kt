package com.mooscles.designsystem.licenses

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.mooscles.designsystem.components.MText
import com.mooscles.designsystem.components.SectionHeader
import com.mooscles.designsystem.components.TopBar
import com.mooscles.designsystem.resources.Res
import com.mooscles.designsystem.resources.ds_licenses_title
import com.mooscles.designsystem.theme.MTheme
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.stringResource

/** The tag of the screen's scrolling content. */
const val LicensesContentTag = "licenses-content"

/**
 * A bundled work and its license. The names are proper names, the same in every language,
 * and the text is the license file shipped in `files/licenses/`, unchanged.
 */
private class BundledLicense(val name: String, val license: String, val path: String)

private val bundled = listOf(
    BundledLicense("Inter", "SIL Open Font License 1.1", "files/licenses/inter_ofl.txt"),
    BundledLicense("Barlow Condensed", "SIL Open Font License 1.1", "files/licenses/barlow_condensed_ofl.txt"),
    BundledLicense("Lucide", "ISC License", "files/licenses/lucide_isc.txt"),
)

/**
 * The open-source licenses the app must show: Inter and Barlow Condensed (OFL) and the Lucide
 * icons (ISC), each with its full notice read from the bundled files.
 */
@OptIn(ExperimentalResourceApi::class)
@Composable
fun LicensesScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val texts by produceState(emptyMap<String, String>()) {
        value = bundled.associate { it.path to Res.readBytes(it.path).decodeToString() }
    }
    val space = MTheme.space
    Column(modifier.fillMaxSize().background(MTheme.colors.bgBase)) {
        TopBar(stringResource(Res.string.ds_licenses_title), onBack = onBack)
        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .testTag(LicensesContentTag)
                .verticalScroll(rememberScrollState())
                .padding(bottom = space.s6),
        ) {
            bundled.forEach { entry ->
                SectionHeader(entry.name)
                MText(
                    entry.license,
                    MTheme.type.labelLg,
                    color = MTheme.colors.textSecondary,
                    modifier = Modifier.padding(horizontal = space.s4),
                )
                texts[entry.path]?.let { text ->
                    MText(
                        text,
                        MTheme.type.bodyMd,
                        color = MTheme.colors.textSecondary,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = space.s4, vertical = space.s3),
                    )
                }
            }
        }
    }
}
