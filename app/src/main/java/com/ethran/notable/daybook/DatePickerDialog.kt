package com.ethran.notable.daybook

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import java.time.LocalDate
import java.time.YearMonth

/** A small month calendar in a dialog. Tapping a day calls [onPick] with it. */
@Composable
fun DatePickerDialog(
    title: String,
    onPick: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val today = remember { LocalDate.now() }
    var month by remember { mutableStateOf(YearMonth.from(today)) }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .width(440.dp)
                .background(Color.White)
                .border(2.dp, Color.Black, RectangleShape)
                .padding(16.dp)
        ) {
            Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.Black)
            Spacer(Modifier.height(12.dp))
            MonthHeader(
                month = month,
                onPrevious = { month = month.minusMonths(1) },
                onNext = { month = month.plusMonths(1) },
                onToday = { onPick(today) },
            )
            Spacer(Modifier.height(8.dp))
            MonthGrid(
                month = month,
                today = today,
                selected = today,
                eventDays = emptySet(),
                noteDays = emptySet(),
                onSelect = onPick,
            )
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth()) {
                Spacer(Modifier.weight(1f))
                InkButton("Cancel") { onDismiss() }
            }
        }
    }
}
