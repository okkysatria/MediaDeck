package com.mediadeck.app.ui.screens.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.mediadeck.app.ui.theme.*
import com.mediadeck.app.util.i18n.t

enum class SettingsAccent {
    Primary, Secondary, Tertiary, Error
}

@Composable
fun SettingsAccent.color(): Color = when (this) {
    SettingsAccent.Primary -> DeckAccentBlue
    SettingsAccent.Secondary -> DeckAccentBlue
    SettingsAccent.Tertiary -> DeckBadgeRed
    SettingsAccent.Error -> DeckBadgeRed
}


@Composable
fun SettingsSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    accent: SettingsAccent = SettingsAccent.Primary,
    subtitle: String? = null,
) {
    if (subtitle != null) {
        val tint = if (accent == SettingsAccent.Error) DeckBadgeRed else DeckAccentBlue
        Row(
            modifier = modifier
                .fillMaxWidth()
                .padding(start = 6.dp, end = 6.dp, top = 10.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (icon != null) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(tint.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = DeckTextPrimary)
                Text(subtitle, fontSize = 13.sp, color = DeckTextSecondary)
            }
        }
    } else {
        Text(
            text = title.uppercase(),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = DeckAccentBlue,
            letterSpacing = 0.8.sp,
            modifier = modifier
                .fillMaxWidth()
                .padding(start = 6.dp, end = 6.dp, top = 14.dp, bottom = 6.dp),
        )
    }
}

@Composable
fun SettingsCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = DeckSurface
        ),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(content = content)
    }
}

@Composable
fun SettingsDivider(modifier: Modifier = Modifier, inset: Boolean = true) {
    HorizontalDivider(
        modifier = modifier.padding(start = if (inset) 54.dp else 0.dp),
        color = DeckDivider,
    )
}

@Composable
fun settingsSwitchColors(): SwitchColors = SwitchDefaults.colors(
    checkedThumbColor = DeckSurface,
    checkedTrackColor = DeckAccentBlue,
    checkedBorderColor = DeckAccentBlue,
    uncheckedThumbColor = DeckTextSecondary,
    uncheckedTrackColor = DeckSurfaceVariant,
    uncheckedBorderColor = DeckSurfaceVariant,
)

@Composable
fun SettingsToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    testTag: String? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = DeckAccentBlue, modifier = Modifier.size(18.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = DeckTextPrimary)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = settingsSwitchColors(),
            modifier = if (testTag != null) Modifier.testTag(testTag) else Modifier,
        )
    }
}


data class PickerOption(
    val label: String,
    val selected: Boolean,
    val onSelect: () -> Unit,
    val subtitle: String? = null,
)


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsBottomSheet(
    title: String,
    icon: ImageVector?,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = DeckSurface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (icon != null) {
                    Icon(icon, contentDescription = null, tint = DeckAccentBlue, modifier = Modifier.size(22.dp))
                }
                Text(
                    title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = DeckTextPrimary,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = t("Close", "Tutup"), tint = DeckTextPrimary)
                }
            }
            content()
        }
    }
}


@Composable
fun SettingsOptionRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    subtitle: String? = null,
    icon: ImageVector? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) DeckAccentBlue.copy(alpha = 0.16f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (icon != null && !selected) {
            Box(modifier = Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = DeckAccentBlue, modifier = Modifier.size(20.dp))
            }
        } else {
            RadioButton(
                selected = selected,
                onClick = onClick,
                colors = RadioButtonDefaults.colors(
                    selectedColor = DeckAccentBlue,
                    unselectedColor = DeckTextSecondary,
                ),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = DeckTextPrimary)
            if (subtitle != null) {
                Text(subtitle, fontSize = 12.sp, color = DeckTextSecondary)
            }
        }
    }
}

@Composable
fun SettingsPickerRow(
    title: String,
    selectedLabel: String,
    options: List<PickerOption>,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .clickable { expanded = true }
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = DeckAccentBlue, modifier = Modifier.size(18.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = DeckTextPrimary)
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(selectedLabel, fontSize = 13.sp, color = DeckTextSecondary, fontWeight = FontWeight.Medium)
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = DeckTextSecondary,
                modifier = Modifier.size(16.dp)
            )
        }
    }
    if (expanded) {
        SettingsBottomSheet(title = title, icon = icon, onDismiss = { expanded = false }) {
            options.forEach { option ->
                SettingsOptionRow(
                    label = option.label,
                    subtitle = option.subtitle,
                    selected = option.selected,
                    onClick = {
                        option.onSelect()
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
fun SettingsDangerRow(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    testTag: String? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .clickable(enabled = enabled, onClick = onClick)
            .let { if (testTag != null) it.testTag(testTag) else it }
            .padding(horizontal = 14.dp, vertical = 6.dp)
            .alpha(if (enabled) 1f else 0.5f),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, contentDescription = null, tint = DeckBadgeRed, modifier = Modifier.size(18.dp))
        Text(
            title,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = DeckBadgeRed,
            modifier = Modifier.weight(1f),
        )
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = DeckBadgeRed.copy(alpha = 0.5f),
            modifier = Modifier.size(16.dp)
        )
    }
}


@Composable
fun SettingsConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    icon: ImageVector = Icons.Default.WarningAmber,
    confirmEnabled: Boolean = true,
) {
    val danger = MaterialTheme.colorScheme.error
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(28.dp), color = DeckSurface) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(danger.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, contentDescription = null, tint = danger, modifier = Modifier.size(26.dp))
                }
                Text(title, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = DeckTextPrimary)
                Text(message, fontSize = 15.sp, color = DeckTextSecondary, lineHeight = 22.sp)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = CircleShape,
                        border = BorderStroke(1.5.dp, DeckTextSecondary.copy(alpha = 0.5f)),
                    ) {
                        Text(t("Cancel", "Batal"), fontWeight = FontWeight.Bold, color = DeckTextPrimary)
                    }
                    Button(
                        onClick = onConfirm,
                        enabled = confirmEnabled,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = danger,
                            contentColor = MaterialTheme.colorScheme.onError,
                        ),
                    ) {
                        Text(confirmLabel, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
