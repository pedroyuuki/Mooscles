package com.mooscles.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.mooscles.designsystem.icons.MIcon
import com.mooscles.designsystem.icons.MIcons
import com.mooscles.designsystem.theme.MTheme

/** The tag of the banner's icon. */
const val ErrorBannerIconTag = "error-banner-icon"

/** An error notice on `danger.subtle` with the `CircleAlert` icon, announced politely when it appears. */
@Composable
fun ErrorBanner(
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val colors = MTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .background(colors.dangerSubtle, RoundedCornerShape(MTheme.radius.md))
            .padding(MTheme.space.s4)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.spacedBy(MTheme.space.s3),
        verticalAlignment = Alignment.Top,
    ) {
        MIcon(
            MIcons.CircleAlert,
            contentDescription = null,
            size = MTheme.sizes.icon20,
            tint = colors.dangerText,
            modifier = Modifier.testTag(ErrorBannerIconTag),
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(MTheme.space.s2)) {
            MText(message, MTheme.type.bodyMd, color = colors.textPrimary)
            if (actionLabel != null && onAction != null) {
                MButton(actionLabel, onClick = onAction, variant = MButtonVariant.Text, size = MButtonSize.S)
            }
        }
    }
}
