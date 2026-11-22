package io.github.samolego.canta.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The package filter of any page that lists packages: it narrows what is already on screen, so
 * there is no submit step and no separate result page — the list filters as the reader types, and
 * the only control beside the field is the way out.
 *
 * The field takes the focus itself when it appears: it was opened by a tap on a search glyph, and a
 * keyboard the reader has to tap a second time to raise is a tap wasted.
 */
@Composable
fun PackageSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
    /** Shown while the field is empty; the rows print package names, so this is what to type. */
    placeholder: String = "搜索包名，如 com.miui.daemon",
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            // The same neutral grey the comment box uses: both are "somewhere to type" on this
            // app, and two differently tinted boxes on one page read as two components that came
            // from different apps.
            .clip(RoundedCornerShape(percent = 50))
            .background(inputFieldColor())
            .heightIn(min = 44.dp)
            .padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = null,
            modifier = Modifier.size(17.dp),
            tint = scheme.onSurfaceVariant,
        )
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp, top = 11.dp, bottom = 11.dp)
                .focusRequester(focus),
            textStyle = TextStyle(fontSize = 14.sp, color = scheme.onSurface),
            singleLine = true,
            cursorBrush = SolidColor(scheme.primary),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {}),
            decorationBox = { inner ->
                if (query.isEmpty()) {
                    Text(
                        text = placeholder,
                        fontSize = 14.sp,
                        color = scheme.onSurfaceVariant,
                    )
                }
                inner()
            },
        )
        IconButton(onClick = onClose) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "关闭搜索",
                modifier = Modifier.size(16.dp),
                tint = scheme.onSurfaceVariant,
            )
        }
    }
}