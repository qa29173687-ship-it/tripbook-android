package tw.tripbook.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tw.tripbook.TripViewModel
import tw.tripbook.domain.*
import tw.tripbook.ui.components.GradientHeader
import tw.tripbook.ui.components.SoftCard
import tw.tripbook.ui.theme.*
import tw.tripbook.util.Money
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CashTimelineScreen(vm: TripViewModel, tripId: Long, memberId: Long?, onBack: () -> Unit) {
    val trip by vm.trip(tripId).collectAsStateWithLifecycle(null)
    val mems by vm.members(tripId).collectAsStateWithLifecycle(emptyList())
    val member = mems.firstOrNull { it.id == memberId }
    val isPool = memberId == null
    val title = if (isPool) "公款" else "${member?.name ?: ""}現金"
    val balance = if (isPool) {
        trip?.poolStartingCashCents ?: trip?.poolCashCents ?: 0
    } else {
        member?.startingCashCents ?: 0
    }
    var entries by remember { mutableStateOf<List<CashTimelineEntry>>(emptyList()) }

    LaunchedEffect(trip?.poolCashCents, mems, memberId) {
        entries = vm.timeline(tripId, memberId)
            .filter { it.kind == CashKind.EXPENSE || it.kind == CashKind.EXPENSE_REVERSE }
    }

    Scaffold(
        containerColor = Cream,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = { Text(title, fontWeight = FontWeight.Black) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Cream),
                windowInsets = WindowInsets.statusBars
            )
        }
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad).padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            item {
                GradientHeader(peach = !isPool) {
                    Text("初始現金", color = Color.White.copy(alpha = 0.95f), fontWeight = FontWeight.Bold)
                    Text(
                        Money.format(balance, trip?.currency ?: "TWD"),
                        color = Color.White,
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Black
                    )
                }
                Spacer(Modifier.height(14.dp))
                Text("現金分帳支出紀錄", fontWeight = FontWeight.Bold, color = InkSoft)
                Spacer(Modifier.height(8.dp))
            }
            if (entries.isEmpty()) {
                item { SoftCard { Text("還沒有現金分帳支出。", color = InkSoft) } }
            }
            items(entries, key = { it.id }) { e ->
                SoftCard(Modifier.padding(bottom = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(PeachSoft),
                            contentAlignment = Alignment.Center
                        ) { Text("−", color = PeachDeep, fontWeight = FontWeight.Black, fontSize = 20.sp) }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(e.note.ifBlank { e.kind.label }, fontWeight = FontWeight.Bold, color = Ink, maxLines = 2)
                            val fmt = DateTimeFormatter.ofPattern("M/d HH:mm").withZone(ZoneId.of("Asia/Taipei"))
                            Text(fmt.format(Instant.ofEpochMilli(e.occurredAt)), color = InkSoft, fontSize = 12.sp)
                        }
                        Text(
                            "−" + Money.format(e.amountCents, trip?.currency ?: "TWD"),
                            color = PeachDeep,
                            fontWeight = FontWeight.Black,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}
