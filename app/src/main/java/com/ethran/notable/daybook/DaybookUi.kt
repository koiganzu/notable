package com.ethran.notable.daybook

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ethran.notable.data.db.Digest
import com.ethran.notable.ui.noRippleClickable

// Shared building blocks for the Today and Digest screens: plain black on white, thick
// borders and large tap targets, which is what reads and refreshes well on e-ink.

@Composable
fun InkButton(
    text: String,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
    onClick: () -> Unit,
) {
    Text(
        text = text,
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
        color = if (filled) Color.White else Color.Black,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .background(if (filled) Color.Black else Color.White, RoundedCornerShape(50))
            .border(2.dp, Color.Black, RoundedCornerShape(50))
            .noRippleClickable { onClick() }
            .padding(horizontal = 18.dp, vertical = 9.dp)
    )
}

/** Screen header: a title with buttons on the right, over a rule. */
@Composable
fun ScreenHeader(title: String, actions: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Text(title, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color.Black)
            Spacer(Modifier.weight(1f))
            actions()
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(Color.Black)
        )
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(
        text = text.uppercase(),
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        color = Color.Black,
        letterSpacing = 1.5.sp,
        modifier = Modifier.padding(top = 20.dp, bottom = 8.dp)
    )
}

@Composable
fun Hairline() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Color(0xFF999999))
    )
}

@Composable
fun ButtonGap() = Spacer(Modifier.width(8.dp))

/** A saved passage with its caption and a row of [actions]. */
@Composable
fun DigestCard(
    digest: Digest,
    caption: String,
    maxLines: Int,
    onClick: () -> Unit,
    actions: @Composable () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .border(1.dp, Color.Black, RectangleShape)
            .padding(12.dp)
    ) {
        Text(
            text = digest.text,
            fontSize = 17.sp,
            lineHeight = 24.sp,
            color = Color.Black,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .noRippleClickable { onClick() }
        )
        Spacer(Modifier.height(6.dp))
        Text(caption, fontSize = 14.sp, color = Color.DarkGray)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) { actions() }
    }
}


/** Heading over a block of the Library: bold, with a short rule under it. */
@Composable
fun LibrarySection(text: String) {
    Text(text, fontSize = 21.sp, fontWeight = FontWeight.Bold, color = Color.Black)
    Box(
        Modifier
            .padding(top = 4.dp, bottom = 12.dp)
            .width(44.dp)
            .height(3.dp)
            .background(Color.Black)
    )
}
