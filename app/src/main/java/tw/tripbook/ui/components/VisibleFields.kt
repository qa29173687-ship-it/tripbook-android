package tw.tripbook.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.sp
import tw.tripbook.ui.theme.Ink
import tw.tripbook.ui.theme.InkSoft
import tw.tripbook.ui.theme.Mint
import tw.tripbook.ui.theme.MintDeep

@Composable
fun visibleFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Ink,
    unfocusedTextColor = Ink,
    disabledTextColor = InkSoft,
    cursorColor = MintDeep,
    focusedBorderColor = MintDeep,
    unfocusedBorderColor = Color(0xFFB7C9C4),
    focusedLabelColor = MintDeep,
    unfocusedLabelColor = InkSoft,
    focusedContainerColor = Color.White,
    unfocusedContainerColor = Color.White,
    disabledContainerColor = Color.White,
    focusedPlaceholderColor = InkSoft,
    unfocusedPlaceholderColor = InkSoft
)

@Composable
fun VisibleTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
    placeholder: String? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        modifier = modifier.fillMaxWidth(),
        singleLine = singleLine,
        textStyle = TextStyle(color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Medium),
        colors = visibleFieldColors(),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType)
    )
}

@Composable
fun VisibleMoneyField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier
) {
    VisibleTextField(
        value = value,
        onValueChange = { onValueChange(it.filter { ch -> ch.isDigit() }) },
        label = label,
        modifier = modifier,
        keyboardType = KeyboardType.Number,
        placeholder = "0"
    )
}
