/*
 * Copyright 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.jetsnack.ui.ar

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.jetsnack.R
import com.example.jetsnack.model.DonutGlaze
import com.example.jetsnack.model.DonutStyle
import com.example.jetsnack.ui.components.JetsnackSurface
import com.example.jetsnack.ui.theme.JetsnackTheme

/** "Build your donut": pick the glaze and toppings, previewed live on the 3D / AR donut. */
@Composable
fun DonutCustomizer(style: DonutStyle, onStyleChange: (DonutStyle) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            text = stringResource(R.string.build_your_donut),
            style = MaterialTheme.typography.labelSmall,
            color = JetsnackTheme.colors.textHelp,
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            DonutGlaze.entries.forEach { glaze ->
                GlazeSwatch(
                    glaze = glaze,
                    selected = style.glaze == glaze,
                    onClick = { onStyleChange(style.copy(glaze = glaze)) },
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ToppingChip(
                label = stringResource(R.string.donut_sprinkles),
                checked = style.sprinkles,
                onCheckedChange = { onStyleChange(style.copy(sprinkles = it)) },
            )
            ToppingChip(
                label = stringResource(R.string.donut_drizzle),
                checked = style.drizzle,
                onCheckedChange = { onStyleChange(style.copy(drizzle = it)) },
            )
        }
    }
}

@Composable
private fun GlazeSwatch(glaze: DonutGlaze, selected: Boolean, onClick: () -> Unit) {
    val swatchColor = Color(
        red = glaze.linearColor[0],
        green = glaze.linearColor[1],
        blue = glaze.linearColor[2],
        colorSpace = ColorSpaces.LinearSrgb,
    ).convert(ColorSpaces.Srgb)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .border(
                    BorderStroke(2.dp, if (selected) JetsnackTheme.colors.brand else Color.Transparent),
                    CircleShape,
                )
                .padding(4.dp)
                .clip(CircleShape)
                .background(swatchColor)
                .border(1.dp, JetsnackTheme.colors.uiBorder.copy(alpha = 0.3f), CircleShape),
        )
        Text(
            text = stringResource(glaze.label),
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) JetsnackTheme.colors.brand else JetsnackTheme.colors.textHelp,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun ToppingChip(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    JetsnackSurface(
        shape = CircleShape,
        color = if (checked) JetsnackTheme.colors.brand else JetsnackTheme.colors.uiBackground,
        contentColor = if (checked) JetsnackTheme.colors.textInteractive else JetsnackTheme.colors.textSecondary,
        border = BorderStroke(1.dp, JetsnackTheme.colors.brand),
        modifier = Modifier.toggleable(value = checked, role = Role.Checkbox, onValueChange = onCheckedChange),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        )
    }
}

@Preview("default")
@Preview("dark theme", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun DonutCustomizerPreview() {
    JetsnackTheme {
        JetsnackSurface {
            DonutCustomizer(style = DonutStyle(), onStyleChange = {}, modifier = Modifier.padding(16.dp))
        }
    }
}
