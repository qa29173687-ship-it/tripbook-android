package tw.tripbook.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tw.tripbook.TripViewModel
import tw.tripbook.ui.components.SoftCard
import tw.tripbook.ui.theme.*

@Composable
fun SettingsScreen(vm: TripViewModel) {
    val trips by vm.trips.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf<Long?>(null) }
    Column(Modifier.fillMaxSize().background(Cream).padding(20.dp)) {
        Text("設定", fontSize = 28.sp, fontWeight = FontWeight.Black, color = MintDeep)
        Spacer(Modifier.height(12.dp))
        SoftCard {
            Text("關於旅伴帳", fontWeight = FontWeight.Black)
            Spacer(Modifier.height(6.dp))
            Text("版本 1.0.0\n出遊分帳，一筆算清楚。\n離線記帳 · 現金流水 · 最少轉帳結算", color = InkSoft, fontSize = 13.sp, lineHeight = 20.sp)
        }
        Spacer(Modifier.height(16.dp))
        Text("刪除行程", fontWeight = FontWeight.Bold, color = InkSoft)
        Spacer(Modifier.height(8.dp))
        LazyColumn {
            items(trips) { t ->
                SoftCard(Modifier.padding(bottom = 8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text(t.name, fontWeight = FontWeight.Bold)
                            Text(t.place, color = InkSoft, fontSize = 12.sp)
                        }
                        TextButton(onClick = { confirmDelete = t.id }) { Text("刪除", color = PeachDeep) }
                    }
                }
            }
        }
    }
    confirmDelete?.let { id ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("確定刪除？") },
            text = { Text("行程、支出、現金流水都會刪掉，無法復原。") },
            confirmButton = {
                TextButton(onClick = { vm.deleteTrip(id); confirmDelete = null }) { Text("刪除", color = PeachDeep) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("取消") } }
        )
    }
}
