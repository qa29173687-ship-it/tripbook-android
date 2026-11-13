package tw.tripbook.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tw.tripbook.TripViewModel
import tw.tripbook.ui.components.*
import tw.tripbook.ui.theme.*
import tw.tripbook.util.Dates
import tw.tripbook.util.Money

@Composable
fun HomeScreen(
    vm: TripViewModel,
    onOpenTrip: (Long) -> Unit,
    onCreateTrip: () -> Unit,
    onCash: (Long, Long?) -> Unit,
    onJoin: () -> Unit = {},
    onOpenShared: (String) -> Unit = {}
) {
    val joined by vm.joined.collectAsStateWithLifecycle()
    val trips by vm.trips.collectAsStateWithLifecycle()
    val active = trips.count { !it.settled }
    val monthSpend = remember(trips) { mutableStateOf(0L) }
    // rough: sum from first active trip loaded lazily below
    var meCash by remember { mutableStateOf<Pair<Long, Long>?>(null) } // tripId to cash
    var meName by remember { mutableStateOf("旅人") }

    LaunchedEffect(trips) {
        val t = trips.firstOrNull { !it.settled } ?: trips.firstOrNull()
        if (t != null) {
            val me = vm.me(t.id)
            meName = me?.name ?: "旅人"
            meCash = me?.let { t.id to it.cashCents }
            val exps = vm.expenses(t.id)
            // just leave monthSpend via collecting below
        }
    }

    val firstTripId = trips.firstOrNull { !it.settled }?.id ?: trips.firstOrNull()?.id
    val firstExps = if (firstTripId != null) {
        vm.expenses(firstTripId).collectAsStateWithLifecycle(emptyList()).value
    } else emptyList()
    LaunchedEffect(firstExps) { monthSpend.value = firstExps.sumOf { it.amountCents } }

    Box(Modifier.fillMaxSize().background(Cream)) {
        LazyColumn(
            Modifier.fillMaxSize().padding(horizontal = 20.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 96.dp)
        ) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text("早安，$meName ☀️", color = InkSoft, fontSize = 15.sp)
                        Text("旅伴帳", color = MintDeep, fontSize = 32.sp, fontWeight = FontWeight.Black)
                        Text("出遊分帳，一筆算清楚", color = InkSoft, fontSize = 14.sp)
                    }
                    Image(
                        painter = androidx.compose.ui.res.painterResource(tw.tripbook.R.drawable.suitcase),
                        contentDescription = "行李箱",
                        modifier = Modifier.size(96.dp).clip(RoundedCornerShape(20.dp)),
                        contentScale = ContentScale.Crop
                    )
                }
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SoftCard(Modifier.weight(1f)) {
                        Text("進行中行程", color = InkSoft, fontSize = 13.sp)
                        Text("$active", color = MintDeep, fontSize = 28.sp, fontWeight = FontWeight.Black)
                    }
                    SoftCard(Modifier.weight(1f)) {
                        Text("目前行程已記", color = InkSoft, fontSize = 13.sp)
                        Text(Money.format(monthSpend.value, trips.firstOrNull()?.currency ?: "TWD"), fontSize = 22.sp, fontWeight = FontWeight.Black)
                    }
                }
                meCash?.let { (tripId, cash) ->
                    Spacer(Modifier.height(12.dp))
                    SoftCard(Modifier.clickable { onCash(tripId, null).let { /* open me */ }
                        // open my cash: need member id — use tripId with me resolved in parent via members screen; pass via callback with member from state
                    }) {
                        // Fixed below with proper click
                    }
                }
            }
            item {
                val t = trips.firstOrNull { !it.settled } ?: trips.firstOrNull()
                if (t != null) {
                    var mid by remember(t.id) { mutableStateOf<Long?>(null) }
                    var cash by remember(t.id) { mutableStateOf(0L) }
                    LaunchedEffect(t.id) {
                        val me = vm.me(t.id)
                        mid = me?.id; cash = me?.cashCents ?: 0
                        meCash = mid?.let { t.id to cash }
                    }
                    // observe members for live cash
                    val mems by vm.members(t.id).collectAsStateWithLifecycle(emptyList())
                    val me = mems.firstOrNull { it.isMe } ?: mems.firstOrNull()
                    if (me != null) {
                        Spacer(Modifier.height(4.dp))
                        Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(22.dp))
                            .background(Color.White)
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("💵", fontSize = 28.sp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("${t.name} · 剩餘現金", color = InkSoft, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text(Money.format(me.cashCents, t.currency), fontSize = 22.sp, fontWeight = FontWeight.Black, color = Ink)
                            Text("初始 ${Money.format(me.startingCashCents, t.currency)}", color = InkSoft, fontSize = 12.sp)
                        }
                    }
                    }
                }
            }
            item {
                Spacer(Modifier.height(18.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("旅伴分享給我的", color = InkSoft, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    TextButton(onClick = onJoin) { Text("＋ 輸入邀請碼加入", color = MintDeep, fontWeight = FontWeight.Bold) }
                }
            }
            items(joined, key = { "j_" + it.cloudId }) { j ->
                SoftCard(Modifier.padding(bottom = 12.dp).clickable { onOpenShared(j.cloudId) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(MintSoft),
                            contentAlignment = Alignment.Center
                        ) { Text("🤝", fontSize = 26.sp) }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(j.tripName, fontWeight = FontWeight.Black, fontSize = 18.sp)
                            Text("我是${j.memberName} · 寫我要買、看我的花費", color = InkSoft, fontSize = 13.sp)
                        }
                    }
                }
            }
            item {
                Spacer(Modifier.height(6.dp))
                Text("我的行程", color = InkSoft, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(Modifier.height(10.dp))
            }
            items(trips, key = { it.id }) { trip ->
                val mems by vm.members(trip.id).collectAsStateWithLifecycle(emptyList())
                val exps by vm.expenses(trip.id).collectAsStateWithLifecycle(emptyList())
                SoftCard(Modifier.padding(bottom = 12.dp).clickable { onOpenTrip(trip.id) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(56.dp).clip(RoundedCornerShape(16.dp))
                                .background(if (trip.settled) MintSoft else PeachSoft),
                            contentAlignment = Alignment.Center
                        ) { Text(if (trip.place.contains("碧潭")) "🛶" else "🗼", fontSize = 26.sp) }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(trip.name, fontWeight = FontWeight.Black, fontSize = 18.sp, modifier = Modifier.weight(1f))
                                Text(
                                    if (trip.settled) "已結清" else "進行中",
                                    Modifier.clip(RoundedCornerShape(50))
                                        .background(if (trip.settled) MintSoft else PeachSoft)
                                        .padding(horizontal = 10.dp, vertical = 4.dp),
                                    color = if (trip.settled) MintDeep else PeachDeep,
                                    fontSize = 12.sp, fontWeight = FontWeight.Bold
                                )
                            }
                            Text("${Dates.toDisplay(trip.startDate)} – ${Dates.toDisplay(trip.endDate)} · ${trip.place}", color = InkSoft, fontSize = 13.sp)
                            Row {
                                mems.take(5).forEach { MemberAvatar(it.name, it.colorIndex, 28) }
                            }
                            Spacer(Modifier.height(6.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(Money.format(exps.sumOf { it.amountCents }, trip.currency), color = MintDeep, fontWeight = FontWeight.Black, fontSize = 18.sp)
                                Text("${mems.size} 人 · ${exps.size} 筆", color = InkSoft, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }
        FloatingActionButton(
            onClick = onCreateTrip,
            Modifier.align(Alignment.BottomEnd).padding(24.dp),
            containerColor = MintDeep, contentColor = Color.White,
            shape = RoundedCornerShape(18.dp)
        ) { Icon(Icons.Default.Add, contentDescription = "新增行程") }
    }
}
