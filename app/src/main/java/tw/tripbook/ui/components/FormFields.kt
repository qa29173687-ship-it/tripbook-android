package tw.tripbook.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tw.tripbook.ui.theme.Ink
import tw.tripbook.ui.theme.InkSoft
import tw.tripbook.ui.theme.Line
import tw.tripbook.ui.theme.MintDeep
import tw.tripbook.util.Currencies
import tw.tripbook.util.Dates

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CurrencyDropdown(
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val label = Currencies.labelOf(value)
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = label,
            onValueChange = {},
            readOnly = true,
            label = { Text("幣別") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
            singleLine = true,
            textStyle = TextStyle(color = Ink, fontSize = 18.sp),
            colors = visibleFieldColors()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            Currencies.options.forEach { opt ->
                DropdownMenuItem(
                    text = { Text(opt.label) },
                    onClick = {
                        onChange(opt.code)
                        expanded = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerButton(
    label: String,
    isoDate: String,
    onPicked: (String) -> Unit,
    modifier: Modifier = Modifier,
    minIso: String? = null,
    maxIso: String? = null
) {
    var open by remember { mutableStateOf(false) }
    val initial = Dates.isoToMillisStartOfDay(isoDate)
    val minMs = minIso?.let { Dates.isoToMillisStartOfDay(it) }
    val maxMs = maxIso?.let { Dates.isoToMillisStartOfDay(it) }
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial,
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                val iso = Dates.millisToIso(utcTimeMillis)
                if (minIso != null && iso < minIso) return false
                if (maxIso != null && iso > maxIso) return false
                return true
            }
        }
    )

    Surface(
        onClick = { open = true },
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, Line)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(label, color = InkSoft, fontSize = 12.sp)
                Spacer(Modifier.height(4.dp))
                Text(
                    if (isoDate.isBlank()) "點選開啟日曆" else Dates.toDisplay(isoDate),
                    fontWeight = FontWeight.Bold,
                    color = if (isoDate.isBlank()) InkSoft else MintDeep,
                    fontSize = 16.sp
                )
            }
            Icon(Icons.Default.DateRange, contentDescription = "選擇日期", tint = MintDeep)
        }
    }

    if (open) {
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { picked ->
                        var iso = Dates.millisToIso(picked)
                        if (minIso != null || maxIso != null) {
                            iso = Dates.clampIsoToRange(iso, minIso ?: iso, maxIso ?: iso)
                        }
                        onPicked(iso)
                    }
                    open = false
                }) { Text("確定", color = MintDeep) }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("取消") } }
        ) {
            DatePicker(state = state)
        }
    }
}

/** One Material3 DateRangePicker: first tap = start, second = end, then 確定. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateRangePickerButton(
    startIso: String,
    endIso: String,
    onPicked: (start: String, end: String) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "旅程日期"
) {
    var open by remember { mutableStateOf(false) }
    val initStart = Dates.isoToMillisStartOfDay(startIso)
    val initEnd = Dates.isoToMillisStartOfDay(endIso)
    val state = rememberDateRangePickerState(
        initialSelectedStartDateMillis = initStart,
        initialSelectedEndDateMillis = initEnd
    )

    val display = when {
        startIso.isBlank() && endIso.isBlank() -> "點選開啟日曆（第一天 → 最後一天）"
        startIso.isNotBlank() && endIso.isBlank() -> "${Dates.toDisplay(startIso)} – …"
        else -> "${Dates.toDisplay(startIso)} – ${Dates.toDisplay(endIso)}"
    }

    Surface(
        onClick = { open = true },
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, Line)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(label, color = InkSoft, fontSize = 12.sp)
                Spacer(Modifier.height(4.dp))
                Text(
                    display,
                    fontWeight = FontWeight.Bold,
                    color = if (startIso.isBlank()) InkSoft else MintDeep,
                    fontSize = 16.sp
                )
            }
            Icon(Icons.Default.DateRange, contentDescription = "選擇日期區間", tint = MintDeep)
        }
    }

    if (open) {
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    val s = state.selectedStartDateMillis
                    val e = state.selectedEndDateMillis
                    if (s != null && e != null) {
                        onPicked(Dates.millisToIso(s), Dates.millisToIso(e))
                        open = false
                    }
                }) { Text("確定", color = MintDeep) }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("取消") } }
        ) {
            DateRangePicker(
                state = state,
                title = {
                    Text(
                        "選旅程第一天與最後一天",
                        Modifier.padding(start = 24.dp, top = 16.dp),
                        fontWeight = FontWeight.Bold
                    )
                },
                headline = {
                    val s = state.selectedStartDateMillis?.let { Dates.toDisplay(Dates.millisToIso(it)) } ?: "第一天"
                    val e = state.selectedEndDateMillis?.let { Dates.toDisplay(Dates.millisToIso(it)) } ?: "最後一天"
                    Text("$s – $e", Modifier.padding(start = 24.dp, bottom = 12.dp), fontSize = 18.sp)
                },
                showModeToggle = false,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(460.dp)
            )
        }
    }
}

@Composable
fun MemberChipList(
    names: List<String>,
    onRemove: (Int) -> Unit,
    meIndex: Int = 0
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        names.forEachIndexed { i, n ->
            SoftCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MemberAvatar(n, i, 36)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        n + if (i == meIndex) "（我）" else "",
                        Modifier.weight(1f),
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = { onRemove(i) }) {
                        Icon(Icons.Default.Close, contentDescription = "刪除", tint = InkSoft)
                    }
                }
            }
        }
    }
}

@Composable
fun AddMemberRow(
    name: String,
    onNameChange: (String) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
    buttonLabel: String = "新增"
) {
    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        VisibleTextField(
            value = name,
            onValueChange = onNameChange,
            label = "旅伴名字",
            modifier = Modifier.weight(1f)
        )
        MintButton(buttonLabel, Modifier.height(56.dp)) { onAdd() }
    }
}
