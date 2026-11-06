package tw.tripbook.ui.screens

import androidx.compose.foundation.background
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
import tw.tripbook.ui.components.MintButton
import tw.tripbook.ui.theme.Cream
import tw.tripbook.ui.theme.InkSoft

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditTripScreen(vm: TripViewModel, onDone: (Long) -> Unit, onBack: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var place by remember { mutableStateOf("") }
    var currency by remember { mutableStateOf("TWD") }
    var start by remember { mutableStateOf("") }
    var end by remember { mutableStateOf("") }
    var members by remember { mutableStateOf("宏威, 小美, 阿明") }

    Scaffold(
        containerColor = Cream,
        topBar = {
            TopAppBar(
                title = { Text("新增行程", fontWeight = FontWeight.Black) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Cream)
            )
        }
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(name, { name = it }, label = { Text("行程名稱") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(place, { place = it }, label = { Text("地點") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(currency, { currency = it.uppercase() }, label = { Text("幣別（ISO，預設 TWD）") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(start, { start = it }, label = { Text("開始日期 yyyy-MM-dd") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(end, { end = it }, label = { Text("結束日期 yyyy-MM-dd") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(members, { members = it }, label = { Text("旅伴（逗號分隔，第一位是我）") }, modifier = Modifier.fillMaxWidth())
            Text("之後可在旅伴頁調整現金與公款。", color = InkSoft, fontSize = MaterialTheme.typography.bodySmall.fontSize)
            MintButton("建立行程", Modifier.fillMaxWidth()) {
                val names = members.split(",", "，").map { it.trim() }.filter { it.isNotEmpty() }
                if (name.isBlank() || names.isEmpty()) return@MintButton
                vm.createTrip(name.trim(), place.trim(), currency.ifBlank { "TWD" }, start.trim(), end.trim(), names, onDone)
            }
        }
    }
}
