package com.mooscles.designsystem.icons

import androidx.compose.ui.graphics.vector.ImageVector
import com.mooscles.designsystem.icons.lucide.*
import com.mooscles.designsystem.icons.own.*

/**
 * Every icon of the design system, as image vectors on a 24 x 24 grid with a 2 stroke, round
 * caps and joins. The first 28 are a closed subset of Lucide (ISC, see the license in
 * resources); the last 10 are Mooscles' own equipment icons. All are drawn in black and
 * tinted by [MIcon].
 */
object MIcons {
    val ArrowLeft: ImageVector get() = LucideArrowLeft
    val Check: ImageVector get() = LucideCheck
    val X: ImageVector get() = LucideX
    val Plus: ImageVector get() = LucidePlus
    val Minus: ImageVector get() = LucideMinus
    val ChevronRight: ImageVector get() = LucideChevronRight
    val ChevronDown: ImageVector get() = LucideChevronDown
    val ChevronUp: ImageVector get() = LucideChevronUp
    val EllipsisVertical: ImageVector get() = LucideEllipsisVertical
    val CircleAlert: ImageVector get() = LucideCircleAlert
    val CircleCheck: ImageVector get() = LucideCircleCheck
    val Info: ImageVector get() = LucideInfo
    val Lock: ImageVector get() = LucideLock
    val Sun: ImageVector get() = LucideSun
    val Moon: ImageVector get() = LucideMoon
    val MonitorSmartphone: ImageVector get() = LucideMonitorSmartphone
    val Download: ImageVector get() = LucideDownload
    val Upload: ImageVector get() = LucideUpload
    val RefreshCw: ImageVector get() = LucideRefreshCw
    val Timer: ImageVector get() = LucideTimer
    val SkipForward: ImageVector get() = LucideSkipForward
    val Pause: ImageVector get() = LucidePause
    val Play: ImageVector get() = LucidePlay
    val Trash2: ImageVector get() = LucideTrash2
    val Undo2: ImageVector get() = LucideUndo2
    val FileText: ImageVector get() = LucideFileText
    val Wifi: ImageVector get() = LucideWifi
    val ImageOff: ImageVector get() = LucideImageOff

    val Plate: ImageVector get() = OwnPlate
    val PlateRecord: ImageVector get() = OwnPlateRecord
    val Dumbbell: ImageVector get() = OwnDumbbell
    val Barbell: ImageVector get() = OwnBarbell
    val Kettlebell: ImageVector get() = OwnKettlebell
    val Bench: ImageVector get() = OwnBench
    val Cable: ImageVector get() = OwnCable
    val Machine: ImageVector get() = OwnMachine
    val Rope: ImageVector get() = OwnRope
    val Body: ImageVector get() = OwnBody

    /** Name and icon of all 38, Lucide first. Used by the gallery and the tests. */
    val all: List<Pair<String, ImageVector>> by lazy(LazyThreadSafetyMode.NONE) {
        listOf(
            "ArrowLeft" to ArrowLeft,
            "Check" to Check,
            "X" to X,
            "Plus" to Plus,
            "Minus" to Minus,
            "ChevronRight" to ChevronRight,
            "ChevronDown" to ChevronDown,
            "ChevronUp" to ChevronUp,
            "EllipsisVertical" to EllipsisVertical,
            "CircleAlert" to CircleAlert,
            "CircleCheck" to CircleCheck,
            "Info" to Info,
            "Lock" to Lock,
            "Sun" to Sun,
            "Moon" to Moon,
            "MonitorSmartphone" to MonitorSmartphone,
            "Download" to Download,
            "Upload" to Upload,
            "RefreshCw" to RefreshCw,
            "Timer" to Timer,
            "SkipForward" to SkipForward,
            "Pause" to Pause,
            "Play" to Play,
            "Trash2" to Trash2,
            "Undo2" to Undo2,
            "FileText" to FileText,
            "Wifi" to Wifi,
            "ImageOff" to ImageOff,
            "Plate" to Plate,
            "PlateRecord" to PlateRecord,
            "Dumbbell" to Dumbbell,
            "Barbell" to Barbell,
            "Kettlebell" to Kettlebell,
            "Bench" to Bench,
            "Cable" to Cable,
            "Machine" to Machine,
            "Rope" to Rope,
            "Body" to Body,
        )
    }
}
