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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
            singleLine = true
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
    modifier: Modifier = Modifier
) {
    var open by remember { mutableStateOf(false) }
    val initial = Dates.isoToMillisStartOfDay(isoDate)
    val state = rememberDatePickerState(initialSelectedDateMillis = initial)

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
                    state.selectedDateMillis?.let { onPicked(Dates.millisToIso(it)) }
                    open = false
                }) { Text("確定", color = MintDeep) }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("取消") } }
        ) {
            DatePicker(state = state)
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
        OutlinedTextField(
            value = name,
            onValueChange = onNameChange,
            label = { Text("旅伴名字") },
            modifier = Modifier.weight(1f),
            singleLine = true
        )
        MintButton(buttonLabel, Modifier.height(56.dp)) { onAdd() }
    }
}
