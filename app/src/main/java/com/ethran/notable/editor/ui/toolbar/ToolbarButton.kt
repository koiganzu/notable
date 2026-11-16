package com.ethran.notable.editor.ui.toolbar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ethran.notable.data.datastore.BUTTON_SIZE
import com.ethran.notable.ui.noRippleClickable

/**
 * Toolbar button: a rounded tile that turns solid black when selected. A pen's ink colour is
 * shown as a small bar under the icon rather than by filling the whole button.
 */
@Composable
fun ToolbarButton(
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    onSelect: () -> Unit = {},
    iconId: Int? = null,
    vectorIcon: ImageVector? = null,
    imageVector: ImageVector? = null,
    text: String? = null,
    penColor: Color? = null,
    contentDescription: String = ""
) {
    val tint = if (isSelected) Color.White else Color.Black
    // Black and grey inks need no marker; the icon itself already reads as black.
    val accent = penColor?.takeIf {
        it != Color.Black && it != Color.DarkGray && it != Color.LightGray && it != Color.Gray
    }
    val tile = (BUTTON_SIZE - 6).dp
    val iconModifier = Modifier.size(26.dp)

    Box(
        Modifier
            .then(modifier)
            .noRippleClickable { onSelect() }
            .padding(3.dp)
            .defaultMinSize(minWidth = tile, minHeight = tile)
            .background(
                color = if (isSelected) Color.Black else Color.Transparent,
                shape = RoundedCornerShape(10.dp)
            )
            .padding(horizontal = 7.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            when {
                iconId != null ->
                    Icon(painterResource(iconId), contentDescription, iconModifier, tint = tint)

                vectorIcon != null ->
                    Icon(vectorIcon, contentDescription, iconModifier, tint = tint)

                imageVector != null ->
                    Icon(imageVector, contentDescription, iconModifier, tint = tint)

                text != null ->
                    Text(text, fontSize = 20.sp, fontWeight = FontWeight.Medium, color = tint)
            }
            if (accent != null) {
                Spacer(Modifier.height(2.dp))
                Box(
                    Modifier
                        .width(20.dp)
                        .height(4.dp)
                        .background(accent, RoundedCornerShape(2.dp))
                )
            }
        }
    }
}
