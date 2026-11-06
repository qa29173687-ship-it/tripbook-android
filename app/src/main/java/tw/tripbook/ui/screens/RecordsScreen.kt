package tw.tripbook.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tw.tripbook.TripViewModel
import tw.tripbook.ui.components.SoftCard
import tw.tripbook.ui.theme.*
import tw.tripbook.util.Money

@Composable
fun RecordsScreen(vm: TripViewModel, onOpen: (Long) -> Unit) {
    val trips by vm.trips.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().background(Cream).padding(20.dp)) {
        Text("紀錄", fontSize = 28.sp, fontWeight = FontWeight.Black, color = MintDeep)
        Text("點行程看所有支出", color = InkSoft)
        Spacer(Modifier.height(12.dp))
        LazyColumn {
            items(trips) { t ->
                val exps by vm.expenses(t.id).collectAsStateWithLifecycle(emptyList())
                SoftCard(Modifier.padding(bottom = 10.dp).clickable { onOpen(t.id) }) {
                    Text(t.name, fontWeight = FontWeight.Black)
                    Text("${exps.size} 筆 · ${Money.format(exps.sumOf { it.amountCents }, t.currency)}", color = InkSoft)
                }
            }
        }
    }
}
