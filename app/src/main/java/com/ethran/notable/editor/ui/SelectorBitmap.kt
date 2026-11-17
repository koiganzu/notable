package com.ethran.notable.editor.ui

import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material.Text
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.window.Dialog
import androidx.compose.runtime.LaunchedEffect
import compose.icons.feathericons.Link
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.layout.onSizeChanged
import com.ethran.notable.data.datastore.AppSettings
import com.ethran.notable.data.datastore.GlobalAppSettings
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntSize
import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round
import com.ethran.notable.R
import com.ethran.notable.data.datastore.BUTTON_SIZE
import com.ethran.notable.daybook.DatePickerDialog
import com.ethran.notable.editor.EditorControlTower
import com.ethran.notable.editor.ui.toolbar.ToolbarButton
import com.ethran.notable.io.shareBitmap
import com.ethran.notable.ui.noRippleClickable
import compose.icons.FeatherIcons
import compose.icons.feathericons.Clipboard
import compose.icons.feathericons.BookOpen
import compose.icons.feathericons.Calendar
import compose.icons.feathericons.Copy
import compose.icons.feathericons.Hash
import compose.icons.feathericons.Key
import compose.icons.feathericons.Scissors
import compose.icons.feathericons.Share2

val strokeStyle = Stroke(
    width = 2f,
    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
)

@Composable
fun SelectedBitmap(
    context: Context,
    controlTower: EditorControlTower
) {
    val selectionState = controlTower.getSnapshotOfSelectionState()
    if (selectionState.selectedBitmap == null) return

    // The calendar for tying the selection to a date is showing.
    var isPickingDate by remember { mutableStateOf(false) }
    if (isPickingDate) {
        DatePickerDialog(
            title = "Link this handwriting to a date",
            onPick = { day ->
                isPickingDate = false
                controlTower.markSelectionAsEvent(day.toString())
            },
            onDismiss = { isPickingDate = false },
        )
    }

    // The list of notebooks for linking the selection to one is showing.
    var isPickingLink by remember { mutableStateOf(false) }
    if (isPickingLink) {
        LinkPickerDialog(
            loadTargets = { controlTower.linkTargets() },
            onPick = { notebookId ->
                isPickingLink = false
                controlTower.linkSelectionTo(notebookId)
            },
            onDismiss = { isPickingLink = false },
        )
    }

    // Measured so the button row can be kept wholly on screen.
    var menuSize by remember { mutableStateOf(IntSize.Zero) }
    val screen = LocalView.current
    val density = LocalDensity.current
    val sidePanel = GlobalAppSettings.current.toolbarPosition == AppSettings.Position.Left
    val topBar = GlobalAppSettings.current.toolbarPosition == AppSettings.Position.Top

    var selectionDisplaceOffset =
        controlTower.page.applyZoom(selectionState.selectionDisplaceOffset ?: return)
    val selectionRect =
        controlTower.page.toScreenCoordinates(selectionState.selectionRect ?: return)
    val selectionStartOffset =
        controlTower.page.applyZoom(selectionState.selectionStartOffset ?: IntOffset(0, 0))


    Box(
        Modifier
            .fillMaxSize()
            .noRippleClickable {
                controlTower.applySelectionDisplace()
                selectionState.reset()
            }) {
        Image(
            bitmap = selectionState.selectedBitmap!!.asImageBitmap(),
            contentDescription = "Selection bitmap",
            modifier = Modifier
                .offset { selectionStartOffset + selectionDisplaceOffset }
                .drawBehind {
                    drawRect(
                        color = Color.Gray,
                        topLeft = Offset(0f, 0f),
                        size = size,
                        style = strokeStyle
                    )
                }
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        selectionState.selectionDisplaceOffset =
                            controlTower.page.removeZoom(
                                selectionDisplaceOffset + dragAmount.round()
                            )
                        selectionDisplaceOffset =
                            controlTower.page.applyZoom(
                                selectionState.selectionDisplaceOffset ?: return@detectDragGestures
                            )
                    }
                }
                .combinedClickable(
                    indication = null, interactionSource = remember { MutableInteractionSource() },
                    onClick = {},
                    onDoubleClick = { controlTower.duplicateSelection() }
                )
        )

        // TODO: improve this code

        val buttonCount = if (selectionState.isResizable()) 11 else 9
        val toolbarPadding = 4

        // If we can calculate offset of buttons show selection handling tools
        selectionStartOffset.let { startOffset ->
            selectionDisplaceOffset.let { displaceOffset ->
                // TODO: I think the toolbar is still not in the center.
                val bar = with(density) { (BUTTON_SIZE + 10).dp.roundToPx() }
                val gap = with(density) { 16.dp.roundToPx() }
                val anchor = startOffset + displaceOffset
                val boxWidth = selectionRect.right - selectionRect.left
                val boxHeight = selectionRect.bottom - selectionRect.top
                // Centred over the selection, then pulled back inside the screen.
                val minX = if (sidePanel) bar else 0
                val maxX = (screen.width - menuSize.width).coerceAtLeast(minX)
                val x = (anchor.x + boxWidth / 2 - menuSize.width / 2).coerceIn(minX, maxX)
                // Above the selection; below it when there is no room above.
                val minY = if (topBar) bar else 0
                val above = anchor.y - menuSize.height - gap
                val below = anchor.y + boxHeight + gap
                val maxY = (screen.height - menuSize.height).coerceAtLeast(minY)
                val y = (if (above >= minY) above else below).coerceIn(minY, maxY)
                val offset = IntOffset(x, y)
                // Overlay buttons near the selection box
                Row(
                    modifier = Modifier
                        .offset { offset }
                        .onSizeChanged { menuSize = it }
                        .background(Color.White, RoundedCornerShape(12.dp))
                        .border(2.dp, Color.Black, RoundedCornerShape(12.dp))
                        .padding(toolbarPadding.dp)
                        .height(BUTTON_SIZE.dp)
                ) {
                    ToolbarButton(
                        vectorIcon = FeatherIcons.Hash,
                        isSelected = false,
                        onSelect = { controlTower.markSelectionAsHeading() },
                        contentDescription = "Mark as heading",
                        modifier = Modifier.height(BUTTON_SIZE.dp)
                    )
                    ToolbarButton(
                        vectorIcon = FeatherIcons.Key,
                        isSelected = false,
                        onSelect = { controlTower.markSelectionAsKeyword() },
                        contentDescription = "Mark as keyword",
                        modifier = Modifier.height(BUTTON_SIZE.dp)
                    )
                    ToolbarButton(
                        vectorIcon = FeatherIcons.Calendar,
                        isSelected = false,
                        onSelect = { isPickingDate = true },
                        contentDescription = "Link to a date",
                        modifier = Modifier.height(BUTTON_SIZE.dp)
                    )
                    ToolbarButton(
                        vectorIcon = FeatherIcons.Link,
                        isSelected = false,
                        onSelect = { isPickingLink = true },
                        contentDescription = "Link to another notebook",
                        modifier = Modifier.height(BUTTON_SIZE.dp)
                    )
                    ToolbarButton(
                        vectorIcon = FeatherIcons.BookOpen,
                        isSelected = false,
                        onSelect = { controlTower.digestSelection() },
                        contentDescription = "Save document text to Digest",
                        modifier = Modifier.height(BUTTON_SIZE.dp)
                    )
                    ToolbarButton(
                        vectorIcon = FeatherIcons.Share2,
                        isSelected = false,
                        onSelect = {
                            shareBitmap(context, controlTower.getSelectedBitmap())
                        },
                        modifier = Modifier.height(BUTTON_SIZE.dp)
                    )
                    ToolbarButton(
                        iconId = R.drawable.delete,
                        isSelected = false,
                        onSelect = {
                            controlTower.deleteSelection()
                        },
                        modifier = Modifier.height(BUTTON_SIZE.dp)
                    )
                    if (selectionState.isResizable()) {
                        ToolbarButton(
                            iconId = R.drawable.plus,
                            isSelected = false,
                            onSelect = { controlTower.changeSizeOfSelection(10) },
                            modifier = Modifier.height(BUTTON_SIZE.dp)
                        )
                        ToolbarButton(
                            iconId = R.drawable.minus,
                            isSelected = false,
                            onSelect = { controlTower.changeSizeOfSelection(-10) },
                            modifier = Modifier.height(BUTTON_SIZE.dp)
                        )
                    }
                    ToolbarButton(
                        vectorIcon = FeatherIcons.Scissors,
                        isSelected = false,
                        onSelect = { controlTower.cutSelectionToClipboard(context) },
                        modifier = Modifier.height(BUTTON_SIZE.dp)
                    )
                    ToolbarButton(
                        vectorIcon = FeatherIcons.Clipboard,
                        isSelected = false,
                        onSelect = { controlTower.copySelectionToClipboard(context) },
                        modifier = Modifier.height(BUTTON_SIZE.dp)
                    )
                    ToolbarButton(
                        vectorIcon = FeatherIcons.Copy,
                        isSelected = false,
                        onSelect = { controlTower.duplicateSelection() },
                        modifier = Modifier.height(BUTTON_SIZE.dp)
                    )
                }
            }
        }

    }
}

/** Lists the other notebooks; tapping one calls [onPick] with its id. */
@Composable
private fun LinkPickerDialog(
    loadTargets: suspend () -> List<Pair<String, String>>,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var targets by remember { mutableStateOf<List<Pair<String, String>>?>(null) }
    LaunchedEffect(Unit) { targets = loadTargets() }
    val panel = RoundedCornerShape(16.dp)

    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .width(440.dp)
                .background(Color.White, panel)
                .border(2.dp, Color.Black, panel)
                .padding(18.dp)
        ) {
            Text("Link to a notebook", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            val list = targets
            when {
                list == null -> Text("Loading…", fontSize = 17.sp)
                list.isEmpty() -> Text(
                    "There are no other notebooks yet. Make one in the Library first.",
                    fontSize = 17.sp
                )

                else -> Column(
                    Modifier
                        .heightIn(max = 520.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    list.forEach { (id, title) ->
                        Text(
                            title,
                            fontSize = 18.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .noRippleClickable { onPick(id) }
                                .padding(vertical = 12.dp)
                        )
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(Color.Gray)
                        )
                    }
                }
            }
        }
    }
}
