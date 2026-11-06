package tw.tripbook.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import tw.tripbook.TripViewModel
import tw.tripbook.ui.components.*
import tw.tripbook.ui.theme.Cream
import tw.tripbook.ui.theme.InkSoft
import tw.tripbook.ui.theme.MintDeep

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditTripScreen(vm: TripViewModel, onDone: (Long) -> Unit, onBack: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var place by remember { mutableStateOf("") }
    var currency by remember { mutableStateOf("TWD") }
    var start by remember { mutableStateOf("") }
    var end by remember { mutableStateOf("") }
    var memberNames by remember { mutableStateOf(listOf("宏威")) }
    var draftName by remember { mutableStateOf("") }
    val scroll = rememberScrollState()

    Scaffold(
        containerColor = Cream,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = { Text("新增行程", fontWeight = FontWeight.Black) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Cream),
                windowInsets = WindowInsets.statusBars
            )
        }
    ) { pad ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .imePadding()
                .verticalScroll(scroll)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                name, { name = it },
                label = { Text("行程名稱") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                place, { place = it },
                label = { Text("地點") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            CurrencyDropdown(currency, onChange = { currency = it })
            DatePickerButton("開始日期", start, onPicked = { start = it })
            DatePickerButton("結束日期", end, onPicked = {
                end = it
                // if end before start, keep but user can fix
            })
            Text("旅伴（一個一個新增，第一位是我）", fontWeight = FontWeight.Bold, color = InkSoft)
            MemberChipList(
                names = memberNames,
                onRemove = { idx ->
                    if (memberNames.size <= 1) return@MemberChipList
                    memberNames = memberNames.toMutableList().also { it.removeAt(idx) }
                },
                meIndex = 0
            )
            AddMemberRow(
                name = draftName,
                onNameChange = { draftName = it },
                onAdd = {
                    val n = draftName.trim()
                    if (n.isEmpty()) return@AddMemberRow
                    if (memberNames.any { it.equals(n, ignoreCase = true) }) return@AddMemberRow
                    memberNames = memberNames + n
                    draftName = ""
                }
            )
            Text("之後可在旅伴頁調整現金與公款。", color = InkSoft, style = MaterialTheme.typography.bodySmall)
            MintButton("建立行程", Modifier.fillMaxWidth()) {
                if (name.isBlank() || memberNames.isEmpty()) return@MintButton
                if (start.isBlank() || end.isBlank()) return@MintButton
                vm.createTrip(
                    name.trim(), place.trim(), currency,
                    start, end, memberNames, onDone
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
