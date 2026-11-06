package tw.tripbook.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tw.tripbook.TripViewModel
import tw.tripbook.data.MemberDraft
import tw.tripbook.ui.components.*
import tw.tripbook.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditTripScreen(vm: TripViewModel, onDone: (Long) -> Unit, onBack: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var place by remember { mutableStateOf("") }
    var currency by remember { mutableStateOf("TWD") }
    var start by remember { mutableStateOf("") }
    var end by remember { mutableStateOf("") }
    var drafts by remember { mutableStateOf(listOf(MemberDraft("宏威", 0))) }
    var draftName by remember { mutableStateOf("") }
    var draftCash by remember { mutableStateOf("") }
    var myCash by remember { mutableStateOf("") }
    var poolCash by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val scroll = rememberScrollState()
    val snack = remember { SnackbarHostState() }

    Scaffold(
        containerColor = Cream,
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snack) },
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
                .padding(horizontal = 16.dp)
                .padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            VisibleTextField(name, { name = it }, "行程名稱")
            VisibleTextField(place, { place = it }, "地點")
            CurrencyDropdown(currency, onChange = { currency = it })
            DatePickerButton("開始日期", start, onPicked = { start = it })
            DatePickerButton("結束日期", end, onPicked = { end = it })

            Text("本人帶了多少現金", fontWeight = FontWeight.Bold, color = Ink, fontSize = 15.sp)
            VisibleMoneyField(
                myCash,
                {
                    myCash = it
                    if (drafts.isNotEmpty()) {
                        drafts = drafts.toMutableList().also { list ->
                            list[0] = list[0].copy(startingCash = it.toLongOrNull() ?: 0)
                        }
                    }
                },
                "本人初始現金"
            )
            VisibleMoneyField(poolCash, { poolCash = it }, "公款初始現金（可留空）")

            Text("旅伴（第一位是本人，不可刪除）", fontWeight = FontWeight.Bold, color = InkSoft)
            drafts.forEachIndexed { i, d ->
                SoftCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        MemberAvatar(d.name, i, 36)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (i == 0) "${d.name}（本人）" else d.name,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = Ink,
                                maxLines = 1
                            )
                            Text(
                                "初始現金 ${d.startingCash}",
                                color = MintDeep,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                maxLines = 1
                            )
                        }
                        if (i > 0) {
                            IconButton(onClick = {
                                drafts = drafts.toMutableList().also { it.removeAt(i) }
                            }) { Icon(Icons.Default.Close, null, tint = InkSoft) }
                        }
                    }
                }
            }

            SoftCard {
                Text("新增旅伴", fontWeight = FontWeight.Bold, color = Ink)
                Spacer(Modifier.height(8.dp))
                VisibleTextField(draftName, { draftName = it }, "旅伴名字")
                Spacer(Modifier.height(8.dp))
                VisibleMoneyField(draftCash, { draftCash = it }, "他帶了多少現金（可留空）")
                Spacer(Modifier.height(10.dp))
                MintButton("新增旅伴", Modifier.fillMaxWidth()) {
                    val n = draftName.trim()
                    if (n.isEmpty()) { error = "請輸入旅伴名字"; return@MintButton }
                    if (drafts.any { it.name.equals(n, true) }) { error = "已經有這位旅伴了"; return@MintButton }
                    drafts = drafts + MemberDraft(n, draftCash.toLongOrNull() ?: 0)
                    draftName = ""; draftCash = ""
                }
            }

            MintButton("建立行程", Modifier.fillMaxWidth()) {
                when {
                    name.isBlank() -> error = "請填行程名稱"
                    start.isBlank() || end.isBlank() -> error = "請選擇開始與結束日期"
                    drafts.isEmpty() -> error = "至少一位旅伴"
                    else -> {
                        val synced = drafts.toMutableList().also {
                            if (it.isNotEmpty()) it[0] = it[0].copy(
                                startingCash = myCash.toLongOrNull() ?: it[0].startingCash
                            )
                        }
                        vm.createTrip(
                            name.trim(), place.trim(), currency, start, end, synced,
                            poolStarting = poolCash.toLongOrNull() ?: 0, onDone = onDone
                        )
                    }
                }
            }
            // Extra space so last fields can sit above tall Zhuyin IME
            Spacer(Modifier.height(280.dp))
        }
    }

    LaunchedEffect(error) {
        error?.let {
            snack.showSnackbar(it)
            error = null
        }
    }
}
