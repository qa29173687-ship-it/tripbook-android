package tw.tripbook.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tw.tripbook.cloud.CItem
import tw.tripbook.cloud.Cloud
import tw.tripbook.domain.CMember
import tw.tripbook.domain.PaymentMethod
import tw.tripbook.domain.ShoppingText
import tw.tripbook.ui.components.*
import tw.tripbook.ui.theme.*
import tw.tripbook.util.Money
import java.io.File

/**
 * Shared 我要買 list (cloud). Everyone writes and edits their own items; others' items are read-only
 * but can be marked 我幫你找 / 我看到了.
 */
@Composable
fun SharedShoppingPane(
    cloudId: String,
    myMemberId: String,
    myName: String,
    members: List<CMember>,
    myCash: Long,
    myStartCash: Long,
    currency: String,
    onBuy: suspend (CItem, Long, PaymentMethod) -> Unit,
    onUnbuy: suspend (CItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val items by remember(cloudId) { Cloud.itemsFlow(cloudId) }.collectAsStateWithLifecycle(emptyList())
    val uid = Cloud.user?.uid
    val scope = rememberCoroutineScope()
    var filter by rememberSaveable { mutableStateOf("me") } // me / all / <memberId>
    var newName by remember { mutableStateOf("") }
    var pendingPhoto by remember { mutableStateOf<String?>(null) }
    var buying by remember { mutableStateOf<CItem?>(null) }
    var unticking by remember { mutableStateOf<CItem?>(null) }
    var editing by remember { mutableStateOf<CItem?>(null) }
    var viewing by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val mine = items.filter { it.memberId == myMemberId }
    val shown = when (filter) {
        "me" -> mine
        "all" -> items
        else -> items.filter { it.memberId == filter }
    }.sortedWith(compareBy<CItem> { it.memberId != myMemberId }.thenBy { it.memberName }.thenBy { it.done }.thenBy { it.createdAt })
    val others = members.filter { it.id != myMemberId }

    fun run(block: suspend () -> Unit) = scope.launch {
        busy = true
        runCatching { block() }.onFailure { error = "沒有連上網路或沒有權限，請再試一次" }
        busy = false
    }

    Column(modifier.imePadding()) {
        LazyColumn(
            Modifier.weight(1f).padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                SoftCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            CashSummaryBlock(remaining = myCash, starting = myStartCash, currency = currency, remainingLabel = "💵 我的剩餘現金")
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("我已買", color = InkSoft, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text("${mine.count { it.done }} / ${mine.size}", fontWeight = FontWeight.Black, fontSize = 22.sp, color = MintDeep)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill("我的", filter == "me", mint = false) { filter = "me" }
                    others.forEach { m -> Pill("${m.name}的", filter == m.id, mint = false) { filter = m.id } }
                    Pill("全部", filter == "all", mint = false) { filter = "all" }
                }
                error?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, color = PeachDeep, fontSize = 13.sp, modifier = Modifier.clickable { error = null })
                }
            }
            if (shown.isEmpty()) item {
                Text(
                    if (filter == "me") "把想買的東西寫在這裡，可以拍照。旅伴都看得到，可以幫你找。"
                    else "這裡還沒有東西。",
                    color = InkSoft, fontSize = 14.sp, modifier = Modifier.padding(8.dp)
                )
            }
            var lastOwner: String? = null
            shown.forEach { it ->
                if (filter == "all" && it.memberId != lastOwner) {
                    lastOwner = it.memberId
                    val who = if (it.memberId == myMemberId) "我的清單" else "${it.memberName}的清單（只能看，可以幫找）"
                    item(key = "h_${it.memberId}") {
                        Text(who, color = InkSoft, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
                    }
                }
                item(key = it.id) {
                    val own = it.memberId == myMemberId
                    CloudRow(
                        item = it, own = own, myUid = uid, currency = currency,
                        onCheck = { if (own && !busy) { if (it.done) unticking = it else buying = it } },
                        onEdit = { if (own) editing = it },
                        onPhoto = { p -> viewing = p },
                        onHelp = { status -> run { Cloud.setHelp(cloudId, it.id, myName, status) } }
                    )
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().background(Cream).padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PhotoPicker(
                path = pendingPhoto,
                onPicked = { pendingPhoto = it },
                onRemove = { p -> runCatching { File(p).delete() }; pendingPhoto = null },
                size = 56
            )
            Spacer(Modifier.width(8.dp))
            VisibleTextField(
                newName, { newName = it }, if (pendingPhoto != null) "名稱（可留白）" else "寫下我要買的東西",
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            FilledIconButton(
                onClick = {
                    if ((newName.isNotBlank() || pendingPhoto != null) && !busy) {
                        val n = ShoppingText.itemName(newName, pendingPhoto != null)
                        val p = pendingPhoto
                        newName = ""; pendingPhoto = null; filter = "me"
                        run {
                            val b64 = p?.let { withContext(Dispatchers.IO) { Cloud.photoToBase64(it) } }
                            p?.let { runCatching { File(it).delete() } }
                            Cloud.addItem(cloudId, myMemberId, myName, n, b64)
                        }
                    }
                },
                modifier = Modifier.size(56.dp),
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = MintDeep)
            ) { Icon(Icons.Default.Add, contentDescription = "新增", tint = Color.White) }
        }
    }

    buying?.let { item ->
        CloudBoughtDialog(item, myCash, currency, onDismiss = { buying = null }) { amount, method ->
            buying = null
            run { onBuy(item, amount, method) }
        }
    }
    unticking?.let { item ->
        AlertDialog(
            onDismissRequest = { unticking = null },
            title = { Text("取消「${item.name}」的打勾？") },
            text = { Text("會一併刪除這筆支出，付現的金額會加回剩餘現金。") },
            confirmButton = { TextButton(onClick = { unticking = null; run { onUnbuy(item) } }) { Text("取消打勾", color = PeachDeep) } },
            dismissButton = { TextButton(onClick = { unticking = null }) { Text("保留") } }
        )
    }
    editing?.let { item ->
        CloudEditDialog(
            item,
            onDismiss = { editing = null },
            onSave = { name, qty, note, newPhoto, removePhoto ->
                editing = null
                run {
                    val fields = mutableMapOf<String, Any?>(
                        "name" to ShoppingText.itemName(name, item.photo != null || newPhoto != null),
                        "qty" to qty.coerceAtLeast(1), "note" to note.trim()
                    )
                    if (newPhoto != null) {
                        fields["photo"] = withContext(Dispatchers.IO) { Cloud.photoToBase64(newPhoto) } ?: ""
                        runCatching { File(newPhoto).delete() }
                    } else if (removePhoto) fields["photo"] = ""
                    Cloud.updateItem(cloudId, item.id, fields)
                }
            },
            onDelete = { editing = null; run { if (item.done) onUnbuy(item); Cloud.deleteItem(cloudId, item.id) } }
        )
    }
    viewing?.let { b64 -> B64Viewer(b64) { viewing = null } }
}

@Composable
fun B64Thumb(b64: String, size: Int, modifier: Modifier = Modifier) {
    var bmp by remember(b64) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(b64) { bmp = withContext(Dispatchers.IO) { Cloud.decodePhoto(b64) } }
    Box(modifier.size(size.dp).clip(RoundedCornerShape(12.dp)).background(MintSoft), contentAlignment = Alignment.Center) {
        bmp?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
    }
}

@Composable
private fun B64Viewer(b64: String, onClose: () -> Unit) {
    var bmp by remember(b64) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(b64) { bmp = withContext(Dispatchers.IO) { Cloud.decodePhoto(b64) } }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black).clickable(onClick = onClose), contentAlignment = Alignment.Center) {
            bmp?.let { Image(it.asImageBitmap(), "商品照片", Modifier.fillMaxWidth(), contentScale = ContentScale.Fit) }
            Text("點一下關閉", color = Color.White, fontSize = 13.sp, modifier = Modifier.align(Alignment.BottomCenter).padding(32.dp))
        }
    }
}

@Composable
private fun CloudRow(
    item: CItem, own: Boolean, myUid: String?, currency: String,
    onCheck: () -> Unit, onEdit: () -> Unit, onPhoto: (String) -> Unit, onHelp: (String?) -> Unit
) {
    SoftCard(Modifier.clickable(onClick = onEdit)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(32.dp).clip(RoundedCornerShape(10.dp))
                    .background(if (item.done) MintDeep else Color.White)
                    .border(2.dp, if (item.done) MintDeep else if (own) Color(0xFFB7C9C4) else Color(0xFFDDE6E3), RoundedCornerShape(10.dp))
                    .clickable(enabled = own, onClick = onCheck),
                contentAlignment = Alignment.Center
            ) {
                if (item.done) Icon(Icons.Default.Check, contentDescription = "已買", tint = Color.White, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            item.photo?.let { p ->
                B64Thumb(p, 64, Modifier.clickable { onPhoto(p) })
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    if (item.qty > 1) "${item.name} ×${item.qty}" else item.name,
                    fontWeight = FontWeight.Black, fontSize = 16.sp,
                    color = if (item.done) InkSoft else Ink,
                    textDecoration = if (item.done) TextDecoration.LineThrough else null
                )
                if (!own) Text("${item.memberName} 要的", color = InkSoft, fontSize = 12.sp)
                if (item.note.isNotBlank()) Text(item.note, color = InkSoft, fontSize = 12.sp)
                if (item.done && item.amountCents > 0) Text(
                    if (item.method == "CASH") "現金 ${Money.format(item.amountCents, currency)}${if (own) " · 已扣剩餘現金" else ""}"
                    else "信用卡 ${Money.format(item.amountCents, currency)}",
                    color = MintDeep, fontSize = 12.sp, fontWeight = FontWeight.Bold
                )
                item.helpers.filterKeys { it != myUid }.values.forEach { h ->
                    Text(
                        if (h.status == "SEEN") "👀 ${h.name} 看到了" else "🔍 ${h.name} 在幫找",
                        color = PeachDeep, fontSize = 12.sp, fontWeight = FontWeight.Bold
                    )
                }
            }
            if (!own && !item.done) {
                val mine = myUid?.let { item.helpers[it]?.status }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    SmallTag("我幫你找", mine == "FIND") { onHelp(if (mine == "FIND") null else "FIND") }
                    SmallTag("我看到了", mine == "SEEN") { onHelp(if (mine == "SEEN") null else "SEEN") }
                }
            }
        }
    }
}

@Composable
private fun SmallTag(text: String, on: Boolean, onClick: () -> Unit) {
    Text(
        text,
        Modifier.clip(RoundedCornerShape(50)).background(if (on) PeachDeep else PeachSoft)
            .clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 5.dp),
        color = if (on) Color.White else PeachDeep, fontSize = 12.sp, fontWeight = FontWeight.Bold
    )
}

@Composable
private fun CloudBoughtDialog(
    item: CItem, remaining: Long, currency: String,
    onDismiss: () -> Unit, onConfirm: (Long, PaymentMethod) -> Unit
) {
    var amountText by remember { mutableStateOf("") }
    var method by remember { mutableStateOf(PaymentMethod.CASH) }
    val amount = amountText.toLongOrNull() ?: 0L
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("買到了！${if (item.qty > 1) "${item.name} ×${item.qty}" else item.name}", fontWeight = FontWeight.Black) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item.photo?.let { B64Thumb(it, 96) }
                VisibleMoneyField(amountText, { amountText = it }, "實付金額")
                Text("付款方式", color = InkSoft, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill("現金", method == PaymentMethod.CASH, mint = false) { method = PaymentMethod.CASH }
                    Pill("信用卡", method == PaymentMethod.CARD, mint = false) { method = PaymentMethod.CARD }
                }
                if (method == PaymentMethod.CASH) Text(
                    "剩餘現金 ${Money.format(remaining, currency)} → 扣完剩 ${Money.format(remaining - amount, currency)}",
                    color = MintDeep, fontWeight = FontWeight.Bold, fontSize = 14.sp
                ) else Text("信用卡只記進支出，不扣現金", color = InkSoft, fontSize = 13.sp)
            }
        },
        confirmButton = {
            TextButton(enabled = amount > 0, onClick = { onConfirm(amount, method) }) {
                Text("打勾並記帳", color = if (amount > 0) MintDeep else InkSoft, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun CloudEditDialog(
    item: CItem,
    onDismiss: () -> Unit,
    onSave: (String, Int, String, String?, Boolean) -> Unit,
    onDelete: () -> Unit
) {
    var name by remember { mutableStateOf(item.name) }
    var qty by remember { mutableStateOf(item.qty.toString()) }
    var note by remember { mutableStateOf(item.note) }
    var newPhoto by remember { mutableStateOf<String?>(null) }
    var removed by remember { mutableStateOf(false) }
    val hasPhoto = newPhoto != null || (item.photo != null && !removed)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("編輯要買的東西", fontWeight = FontWeight.Black) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (newPhoto == null && item.photo != null && !removed) {
                        Box(Modifier.clickable { removed = true }) { B64Thumb(item.photo, 72) }
                        Spacer(Modifier.width(10.dp))
                        Text("點照片可移除，再按相機換新的", color = InkSoft, fontSize = 13.sp)
                    } else {
                        PhotoPicker(path = newPhoto, onPicked = { newPhoto = it }, onRemove = { newPhoto = null }, size = 72)
                        Spacer(Modifier.width(10.dp))
                        Text(if (newPhoto != null) "點照片可換或移除" else "點相機加照片", color = InkSoft, fontSize = 13.sp)
                    }
                }
                VisibleTextField(name, { name = it }, "東西")
                VisibleMoneyField(qty, { qty = it }, "數量")
                VisibleTextField(note, { note = it }, "備註（選填）", placeholder = "例如：要藍色的")
                TextButton(onClick = onDelete) { Text("刪除這項", color = PeachDeep) }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank() || hasPhoto, onClick = {
                onSave(name, qty.toIntOrNull() ?: 1, note, newPhoto, removed && newPhoto == null)
            }) { Text("儲存", color = MintDeep, fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
