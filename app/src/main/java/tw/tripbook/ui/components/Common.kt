package tw.tripbook.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tw.tripbook.domain.ExpenseCategory
import tw.tripbook.domain.PaymentMethod
import tw.tripbook.ui.theme.*

val AvatarColors = listOf(MintDeep, PeachDeep, Gold, Color(0xFF4A6FBF), Color(0xFF8A5CC4), Color(0xFFC45A7A))

@Composable
fun MemberAvatar(name: String, colorIndex: Int, size: Int = 40) {
    val c = AvatarColors[colorIndex % AvatarColors.size]
    Box(
        Modifier.size(size.dp).clip(CircleShape).background(c),
        contentAlignment = Alignment.Center
    ) {
        Text(name.take(1), color = Color.White, fontWeight = FontWeight.Bold, fontSize = (size / 2.6).sp)
    }
}

@Composable
fun SoftCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Color.White)
            .padding(16.dp),
        content = content
    )
}

@Composable
fun CategoryChip(cat: ExpenseCategory) {
    val (bg, fg) = when (cat) {
        ExpenseCategory.STAY -> Color(0xFFE8F0FF) to Color(0xFF4A6FBF)
        ExpenseCategory.TRANSIT -> Color(0xFFE8FBF3) to Color(0xFF2F9E7E)
        ExpenseCategory.FOOD -> Color(0xFFFFF0E6) to Color(0xFFD07A45)
        ExpenseCategory.TICKET -> Color(0xFFF3E8FF) to Color(0xFF8A5CC4)
        ExpenseCategory.SHOP -> Color(0xFFFFE8F0) to Color(0xFFC45A7A)
        ExpenseCategory.OTHER -> Color(0xFFEEF1F0) to Color(0xFF667774)
    }
    Text(
        cat.label,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(bg).padding(horizontal = 10.dp, vertical = 4.dp),
        color = fg, fontSize = 12.sp, fontWeight = FontWeight.Bold
    )
}

@Composable
fun PayChip(method: PaymentMethod) {
    val (bg, fg) = when (method) {
        PaymentMethod.CASH -> PeachSoft to PeachDeep
        PaymentMethod.CARD -> Color(0xFFE8F0FF) to Color(0xFF4A6FBF)
        PaymentMethod.TRANSFER -> MintSoft to MintDeep
        PaymentMethod.POOL -> MintSoft to MintDeep
    }
    Text(
        method.label,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(bg).padding(horizontal = 10.dp, vertical = 4.dp),
        color = fg, fontSize = 12.sp, fontWeight = FontWeight.Bold
    )
}

@Composable
fun Pill(text: String, selected: Boolean, mint: Boolean = true, onClick: () -> Unit) {
    val bg = when {
        selected && mint -> MintSoft
        selected -> PeachSoft
        else -> Color.White
    }
    val border = when {
        selected && mint -> Mint
        selected -> Peach
        else -> Color.Transparent
    }
    val fg = when {
        selected && mint -> MintDeep
        selected -> PeachDeep
        else -> Ink
    }
    Text(
        text,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .border(2.dp, border, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        color = fg, fontWeight = FontWeight.Bold, fontSize = 14.sp
    )
}

@Composable
fun MintButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier.height(52.dp),
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(containerColor = MintDeep)
    ) { Text(text, fontWeight = FontWeight.Bold, fontSize = 16.sp) }
}

@Composable
fun GradientHeader(modifier: Modifier = Modifier, peach: Boolean = false, content: @Composable BoxScope.() -> Unit) {
    val brush = if (peach) Brush.linearGradient(listOf(Peach, PeachDeep))
    else Brush.linearGradient(listOf(Mint, MintDeep))
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(brush)
            .padding(20.dp),
        content = content
    )
}
