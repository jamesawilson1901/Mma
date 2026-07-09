package com.draft.editor.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AddLink
import androidx.compose.material.icons.filled.FormatAlignCenter
import androidx.compose.material.icons.automirrored.filled.FormatAlignLeft
import androidx.compose.material.icons.automirrored.filled.FormatAlignRight
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatColorFill
import androidx.compose.material.icons.filled.FormatColorText
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.StrikethroughS
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.mohamedrejeb.richeditor.model.HeadingStyle
import com.mohamedrejeb.richeditor.model.RichTextState

/** Fixed palettes: predictable, readable in both themes. */
private val TEXT_COLORS = listOf(
    "#D32F2F", "#F57C00", "#388E3C", "#1976D2",
    "#7B1FA2", "#C2185B", "#5D4037", "#455A64",
)
private val HIGHLIGHT_COLORS = listOf(
    "#FFF176", "#FFCC80", "#B9F6CA", "#84FFFF", "#F8BBD0", "#E1BEE7",
)

private enum class ToolbarSegment { PARAGRAPH, COLOR, INSERT, SELECT }

/**
 * The two-row, thumb-reachable formatting toolbar pinned above the keyboard.
 * Row 1 never changes: bold, italic, underline, strikethrough, undo, redo.
 * Row 2 swaps between Paragraph / Colour / Insert / Selection segments.
 */
@Composable
fun EditorToolbar(
    state: RichTextState,
    onInsertLink: () -> Unit,
    onSelectWord: () -> Unit,
    onSelectParagraph: () -> Unit,
    onNudge: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var segment by rememberSaveable { mutableStateOf(ToolbarSegment.PARAGRAPH) }

    Surface(modifier = modifier.fillMaxWidth(), tonalElevation = 3.dp) {
        Column {
            HorizontalDivider()

            // Row 2 selector + contents (visually on top so row 1 stays put
            // right above the keyboard where thumbs rest).
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SegmentTab(
                    icon = Icons.Filled.FormatSize,
                    description = "Paragraph styles",
                    selected = segment == ToolbarSegment.PARAGRAPH,
                    onClick = { segment = ToolbarSegment.PARAGRAPH },
                )
                SegmentTab(
                    icon = Icons.Filled.Palette,
                    description = "Colours",
                    selected = segment == ToolbarSegment.COLOR,
                    onClick = { segment = ToolbarSegment.COLOR },
                )
                SegmentTab(
                    icon = Icons.Filled.AddLink,
                    description = "Insert",
                    selected = segment == ToolbarSegment.INSERT,
                    onClick = { segment = ToolbarSegment.INSERT },
                )
                SegmentTab(
                    icon = Icons.Filled.TouchApp,
                    description = "Selection tools",
                    selected = segment == ToolbarSegment.SELECT,
                    onClick = { segment = ToolbarSegment.SELECT },
                )

                Box(
                    Modifier
                        .padding(horizontal = 4.dp)
                        .size(width = 1.dp, height = 28.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant),
                )

                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    when (segment) {
                        ToolbarSegment.PARAGRAPH -> ParagraphSegment(state)
                        ToolbarSegment.COLOR -> ColorSegment(state)
                        ToolbarSegment.INSERT -> InsertSegment(state, onInsertLink)
                        ToolbarSegment.SELECT -> SelectSegment(
                            onSelectWord,
                            onSelectParagraph,
                            onNudge,
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // Row 1: always visible.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val spanStyle = state.currentSpanStyle
                ToolbarToggle(
                    icon = Icons.Filled.FormatBold,
                    description = "Bold",
                    active = spanStyle.fontWeight == FontWeight.Bold,
                    onClick = { state.toggleSpanStyle(SpanStyle(fontWeight = FontWeight.Bold)) },
                )
                ToolbarToggle(
                    icon = Icons.Filled.FormatItalic,
                    description = "Italic",
                    active = spanStyle.fontStyle == FontStyle.Italic,
                    onClick = { state.toggleSpanStyle(SpanStyle(fontStyle = FontStyle.Italic)) },
                )
                ToolbarToggle(
                    icon = Icons.Filled.FormatUnderlined,
                    description = "Underline",
                    active = spanStyle.textDecoration?.contains(TextDecoration.Underline) == true,
                    onClick = {
                        state.toggleSpanStyle(SpanStyle(textDecoration = TextDecoration.Underline))
                    },
                )
                ToolbarToggle(
                    icon = Icons.Filled.StrikethroughS,
                    description = "Strikethrough",
                    active = spanStyle.textDecoration?.contains(TextDecoration.LineThrough) == true,
                    onClick = {
                        state.toggleSpanStyle(SpanStyle(textDecoration = TextDecoration.LineThrough))
                    },
                )
                Spacer(Modifier.weight(1f))
                ToolbarToggle(
                    icon = Icons.AutoMirrored.Filled.Undo,
                    description = "Undo",
                    active = false,
                    enabled = state.history.canUndo,
                    onClick = { state.history.undo() },
                )
                ToolbarToggle(
                    icon = Icons.AutoMirrored.Filled.Redo,
                    description = "Redo",
                    active = false,
                    enabled = state.history.canRedo,
                    onClick = { state.history.redo() },
                )
            }
        }
    }
}

// ------------------------------------------------------------------ segments

@Composable
private fun ParagraphSegment(state: RichTextState) {
    val heading = state.currentHeadingStyle
    HeadingButton("H1", "Heading 1", heading == HeadingStyle.H1) {
        state.setHeadingStyle(if (heading == HeadingStyle.H1) HeadingStyle.Normal else HeadingStyle.H1)
    }
    HeadingButton("H2", "Heading 2", heading == HeadingStyle.H2) {
        state.setHeadingStyle(if (heading == HeadingStyle.H2) HeadingStyle.Normal else HeadingStyle.H2)
    }
    HeadingButton("H3", "Heading 3", heading == HeadingStyle.H3) {
        state.setHeadingStyle(if (heading == HeadingStyle.H3) HeadingStyle.Normal else HeadingStyle.H3)
    }
    HeadingButton("¶", "Body text", heading == HeadingStyle.Normal) {
        state.setHeadingStyle(HeadingStyle.Normal)
    }

    SegmentDivider()

    ToolbarToggle(
        icon = Icons.AutoMirrored.Filled.FormatListBulleted,
        description = "Bulleted list",
        active = state.isUnorderedList,
        onClick = { state.toggleUnorderedList() },
    )
    ToolbarToggle(
        icon = Icons.Filled.FormatListNumbered,
        description = "Numbered list",
        active = state.isOrderedList,
        onClick = { state.toggleOrderedList() },
    )

    SegmentDivider()

    val align = state.currentParagraphStyle.textAlign
    ToolbarToggle(
        icon = Icons.AutoMirrored.Filled.FormatAlignLeft,
        description = "Align left",
        active = align == TextAlign.Start || align == TextAlign.Left,
        onClick = { state.addParagraphStyle(ParagraphStyle(textAlign = TextAlign.Start)) },
    )
    ToolbarToggle(
        icon = Icons.Filled.FormatAlignCenter,
        description = "Align centre",
        active = align == TextAlign.Center,
        onClick = { state.addParagraphStyle(ParagraphStyle(textAlign = TextAlign.Center)) },
    )
    ToolbarToggle(
        icon = Icons.AutoMirrored.Filled.FormatAlignRight,
        description = "Align right",
        active = align == TextAlign.End || align == TextAlign.Right,
        onClick = { state.addParagraphStyle(ParagraphStyle(textAlign = TextAlign.End)) },
    )
}

@Composable
private fun ColorSegment(state: RichTextState) {
    Icon(
        Icons.Filled.FormatColorText,
        contentDescription = null,
        modifier = Modifier.padding(start = 8.dp, end = 4.dp).size(18.dp),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    SwatchButton(
        color = null,
        description = "Default text colour",
        selected = state.currentSpanStyle.color == Color.Unspecified,
    ) {
        val current = state.currentSpanStyle.color
        if (current != Color.Unspecified) {
            state.removeSpanStyle(SpanStyle(color = current))
        }
    }
    for (hex in TEXT_COLORS) {
        val color = hexToColor(hex)
        SwatchButton(
            color = color,
            description = "Text colour $hex",
            selected = state.currentSpanStyle.color == color,
        ) {
            state.addSpanStyle(SpanStyle(color = color))
        }
    }

    SegmentDivider()

    Icon(
        Icons.Filled.FormatColorFill,
        contentDescription = null,
        modifier = Modifier.padding(start = 4.dp, end = 4.dp).size(18.dp),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    SwatchButton(
        color = null,
        description = "No highlight",
        selected = state.currentSpanStyle.background == Color.Unspecified,
    ) {
        val current = state.currentSpanStyle.background
        if (current != Color.Unspecified) {
            state.removeSpanStyle(SpanStyle(background = current))
        }
    }
    for (hex in HIGHLIGHT_COLORS) {
        val color = hexToColor(hex)
        SwatchButton(
            color = color,
            description = "Highlight $hex",
            selected = state.currentSpanStyle.background == color,
        ) {
            state.addSpanStyle(SpanStyle(background = color))
        }
    }
}

@Composable
private fun InsertSegment(state: RichTextState, onInsertLink: () -> Unit) {
    val label = if (state.isLink) "Edit link" else "Insert link"
    Surface(
        onClick = onInsertLink,
        shape = RoundedCornerShape(10.dp),
        color = if (state.isLink) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        },
        modifier = Modifier
            .padding(horizontal = 4.dp)
            .height(48.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp),
        ) {
            Icon(
                Icons.Filled.AddLink,
                contentDescription = label,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun SelectSegment(
    onSelectWord: () -> Unit,
    onSelectParagraph: () -> Unit,
    onNudge: (Int) -> Unit,
) {
    TextChipButton("Word", "Select current word", onSelectWord)
    TextChipButton("Paragraph", "Select current paragraph", onSelectParagraph)
    SegmentDivider()
    ToolbarToggle(
        icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
        description = "Move cursor one character left",
        active = false,
        onClick = { onNudge(-1) },
    )
    ToolbarToggle(
        icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
        description = "Move cursor one character right",
        active = false,
        onClick = { onNudge(1) },
    )
}

// ------------------------------------------------------------------ pieces

@Composable
private fun SegmentTab(
    icon: ImageVector,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier.size(48.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(
                    if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                    RoundedCornerShape(10.dp),
                ),
        )
        IconButton(onClick = onClick, modifier = Modifier.size(48.dp)) {
            Icon(
                icon,
                contentDescription = description,
                tint = if (selected) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun ToolbarToggle(
    icon: ImageVector,
    description: String,
    active: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .background(
                    if (active) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                    RoundedCornerShape(10.dp),
                ),
        )
        IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(48.dp)) {
            Icon(
                icon,
                contentDescription = description,
                tint = when {
                    !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    active -> MaterialTheme.colorScheme.onPrimaryContainer
                    else -> MaterialTheme.colorScheme.onSurface
                },
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun HeadingButton(
    label: String,
    description: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    // The visible chip is 38dp but sits centred in a full 48dp touch target.
    Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
        Surface(
            onClick = onClick,
            shape = RoundedCornerShape(10.dp),
            color = if (active) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                Color.Transparent
            },
            modifier = Modifier
                .size(48.dp)
                .semantics { contentDescription = description },
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(48.dp)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (active) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
            }
        }
    }
}

@Composable
private fun TextChipButton(label: String, description: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        modifier = Modifier
            .padding(horizontal = 4.dp)
            .height(48.dp)
            .semantics { contentDescription = description },
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 14.dp),
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun SwatchButton(
    color: Color?,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(48.dp)
            .semantics { contentDescription = description },
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .background(color ?: Color.Transparent, CircleShape)
                .border(
                    width = if (selected) 2.5.dp else 1.dp,
                    color = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outline
                    },
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (color == null) {
                Text(
                    "A",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun SegmentDivider() {
    Box(
        Modifier
            .padding(horizontal = 6.dp)
            .size(width = 1.dp, height = 24.dp)
            .background(MaterialTheme.colorScheme.outlineVariant),
    )
}

private fun hexToColor(hex: String): Color {
    val value = hex.removePrefix("#").toLong(16)
    return Color(0xFF000000L or value)
}
