package com.booktracker.ui.theme

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.booktracker.R
import kotlinx.coroutines.delay

/** The Recto R, drawn from res/drawable/ic_recto_mark.xml. */
@Composable
fun RectoMark(modifier: Modifier = Modifier, tint: Color = Ink) {
    Icon(painterResource(R.drawable.ic_recto_mark), contentDescription = "Recto", tint = tint, modifier = modifier)
}

/**
 * Raised at rest, pressed into the ground while held. With [accent] it is the lit primary button,
 * with dark text on the accent.
 */
@Composable
fun TactileButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    accent: Color? = null,
    contentColor: Color = Ink,
    corner: Dp = 16.dp,
    content: @Composable RowScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val shape = RoundedCornerShape(corner)
    val look = when {
        !enabled -> Modifier.pressedWell(corner)
        accent != null && pressed -> Modifier.background(accent.copy(alpha = 0.85f), shape)
        accent != null -> Modifier.softShadow(accent.copy(alpha = 0.4f), 18.dp, accent, 0.dp, 8.dp, corner)
        pressed -> Modifier.pressedWell(corner)
        else -> Modifier.raised(corner)
    }
    val color = when {
        !enabled -> InkFaint
        accent != null -> Ground
        else -> contentColor
    }
    Row(
        modifier
            .heightIn(min = 48.dp)
            .then(look)
            .clip(shape)
            .clickable(interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(
            LocalContentColor provides color,
            LocalTextStyle provides MaterialTheme.typography.labelLarge.copy(fontSize = MaterialTheme.typography.bodyLarge.fontSize),
        ) { content() }
    }
}

/**
 * A round (or [corner]-rounded) icon key. [selected] shows it pressed in, as for an active filter
 * or a favourite that is on; [glow] lights it in the accent.
 */
@Composable
fun TactileIconButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    tint: Color = Ink,
    enabled: Boolean = true,
    selected: Boolean = false,
    glow: Color? = null,
    corner: Dp? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val radius = corner ?: (size / 2)
    val look = when {
        !enabled || pressed || selected -> Modifier.pressedWell(radius)
        glow != null -> Modifier
            .softShadow(glow.copy(alpha = 0.3f), 14.dp, Raised, 0.dp, 0.dp, radius)
            .raised(radius)
        else -> Modifier.raised(radius)
    }
    Box(
        modifier
            .size(size)
            .then(look)
            .clip(RoundedCornerShape(radius))
            .clickable(interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = if (enabled) tint else InkFaint.copy(alpha = 0.6f), modifier = Modifier.size(size * 0.42f))
    }
}

/**
 * A field pressed into the ground with its label above. The keyboard opens only when the field is
 * tapped; Next moves on, Done closes it. Once the keyboard is up the field scrolls into view above
 * it, so the keyboard always sits below the text being typed. Put the screen's column on
 * `imePadding()` so there is room to scroll.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TactileTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Done,
    capitalization: androidx.compose.ui.text.input.KeyboardCapitalization =
        androidx.compose.ui.text.input.KeyboardCapitalization.None,
    singleLine: Boolean = true,
    minLines: Int = 1,
    isError: Boolean = false,
    supportingText: String? = null,
    onDone: (() -> Unit)? = null,
) {
    val focusManager = LocalFocusManager.current
    val requester = remember { BringIntoViewRequester() }
    var focused by remember { mutableStateOf(false) }
    val imeBottom = WindowInsets.ime.getBottom(LocalDensity.current)
    LaunchedEffect(focused, imeBottom) {
        if (focused && imeBottom > 0) {
            delay(80)
            requester.bringIntoView()
        }
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge.copy(fontSize = MaterialTheme.typography.labelMedium.fontSize * 1.08f), color = InkSoft)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = singleLine,
            minLines = minLines,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = Ink),
            cursorBrush = SolidColor(LocalAccent.current),
            keyboardOptions = KeyboardOptions(capitalization = capitalization, keyboardType = keyboardType, imeAction = imeAction),
            keyboardActions = KeyboardActions(
                onNext = { focusManager.moveFocus(FocusDirection.Down) },
                onDone = {
                    onDone?.invoke()
                    focusManager.clearFocus()
                },
            ),
            modifier = Modifier
                .fillMaxWidth()
                .bringIntoViewRequester(requester)
                .onFocusChanged { focused = it.isFocused }
                .semantics { contentDescription = label },
            decorationBox = { inner ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .pressedWell(14.dp, if (isError) Color(0xFF231716) else Well)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (value.isEmpty() && placeholder != null) {
                        Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = InkFaint)
                    }
                    inner()
                }
            },
        )
        supportingText?.let {
            Text(it, style = MaterialTheme.typography.labelMedium, color = if (isError) Alert else InkSoft)
        }
    }
}

/** Closes the keyboard when the screen is tapped anywhere that isn't a control or a field. */
@Composable
fun Modifier.closeKeyboardOnBackgroundTap(): Modifier {
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    return this.pointerInput(Unit) {
        detectTapGestures(onTap = {
            focusManager.clearFocus()
            keyboard?.hide()
        })
    }
}

/** A small label in caps, used above sections. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, color: Color = InkSoft) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelLarge.copy(letterSpacing = MaterialTheme.typography.labelLarge.fontSize * 0.1f),
        color = color,
        modifier = modifier,
    )
}

/** A raised card on the ground, the default container for a group of content. */
@Composable
fun TactileCard(modifier: Modifier = Modifier, corner: Dp = 20.dp, content: @Composable () -> Unit) {
    Box(modifier.fillMaxWidth().raised(corner, SurfaceTone)) { content() }
}

/** A small pressed-in panel for a stat, like a coin sunk into the table. */
@Composable
fun StatWell(value: String, label: String, modifier: Modifier = Modifier, icon: ImageVector? = null, iconTint: Color = InkSoft) {
    Row(
        modifier.pressedWell(16.dp).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        icon?.let { Icon(it, contentDescription = null, tint = iconTint, modifier = Modifier.size(22.dp)) }
        Column {
            Text(value, style = MaterialTheme.typography.titleMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, fontSize = MaterialTheme.typography.titleLarge.fontSize * 0.85f))
            Text(label, style = MaterialTheme.typography.labelMedium, color = InkSoft)
        }
    }
}
