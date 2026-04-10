package com.koma.client.ui.reader.epub

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.koma.client.ui.reader.common.EpubFontFamily
import com.koma.client.ui.reader.common.EpubTheme
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EpubSettingsSheet(
    fontFamily: EpubFontFamily,
    fontSize: Int,
    lineHeight: Float,
    margin: Int,
    theme: EpubTheme,
    justified: Boolean,
    overridePublisherFont: Boolean,
    onFontFamilyChange: (EpubFontFamily) -> Unit,
    onFontSizeChange: (Int) -> Unit,
    onLineHeightChange: (Float) -> Unit,
    onMarginChange: (Int) -> Unit,
    onThemeChange: (EpubTheme) -> Unit,
    onJustifiedChange: (Boolean) -> Unit,
    onOverridePublisherFontChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(
                "Reading Settings",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )

            // Theme picker
            Column {
                Text("Theme", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    EpubTheme.entries.forEach { t ->
                        val isSelected = t == theme
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(Color(android.graphics.Color.parseColor(t.bgColor)))
                                .border(
                                    width = if (isSelected) 3.dp else 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outline,
                                    shape = CircleShape,
                                )
                                .clickable { onThemeChange(t) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                t.label.first().toString(),
                                color = Color(android.graphics.Color.parseColor(t.textColor)),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                }
            }

            // Font family
            Column {
                Text("Font", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    EpubFontFamily.entries.forEach { f ->
                        val isSelected = f == fontFamily
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surfaceVariant,
                                )
                                .clickable { onFontFamilyChange(f) }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        ) {
                            Text(
                                f.label,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            // Font size
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Font Size", style = MaterialTheme.typography.labelMedium)
                    Text("${fontSize}sp", style = MaterialTheme.typography.bodySmall)
                }
                Slider(
                    value = fontSize.toFloat(),
                    onValueChange = { onFontSizeChange(it.roundToInt()) },
                    valueRange = 12f..32f,
                    steps = 19,
                )
            }

            // Line height
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Line Height", style = MaterialTheme.typography.labelMedium)
                    Text("%.1f".format(lineHeight), style = MaterialTheme.typography.bodySmall)
                }
                Slider(
                    value = lineHeight,
                    onValueChange = {
                        // Round to nearest 0.1
                        onLineHeightChange((it * 10).roundToInt() / 10f)
                    },
                    valueRange = 1.0f..2.5f,
                )
            }

            // Margin
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Margin", style = MaterialTheme.typography.labelMedium)
                    Text("${margin}dp", style = MaterialTheme.typography.bodySmall)
                }
                Slider(
                    value = margin.toFloat(),
                    onValueChange = { onMarginChange(it.roundToInt()) },
                    valueRange = 0f..48f,
                    steps = 11,
                )
            }

            // Justified text toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Justify Text", style = MaterialTheme.typography.labelMedium)
                Switch(checked = justified, onCheckedChange = onJustifiedChange)
            }

            // Override publisher font
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("Override Publisher Font", style = MaterialTheme.typography.labelMedium)
                    Text(
                        "Use selected font instead of book's font",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Switch(
                    checked = overridePublisherFont,
                    onCheckedChange = onOverridePublisherFontChange,
                )
            }
        }
    }
}
