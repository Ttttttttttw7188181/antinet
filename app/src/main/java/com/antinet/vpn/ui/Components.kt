package com.antinet.vpn.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.antinet.vpn.data.Profile
import com.antinet.vpn.data.ProxyProtocol
import com.antinet.vpn.data.TrafficPoint
import com.antinet.vpn.util.Format

@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: Dp = 18.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = MaterialTheme.shapes.large
    val color = MaterialTheme.colorScheme.surfaceContainerLow
    val border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = modifier.fillMaxWidth(),
            shape = shape,
            color = color,
            border = border,
        ) {
            Column(Modifier.padding(contentPadding), content = content)
        }
    } else {
        Surface(
            modifier = modifier.fillMaxWidth(),
            shape = shape,
            color = color,
            border = border,
        ) {
            Column(Modifier.padding(contentPadding), content = content)
        }
    }
}

@Composable
fun ScreenHeader(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Назад")
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = if (onBack == null) 12.dp else 4.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, content = actions)
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        modifier = modifier.padding(start = 6.dp, top = 4.dp, bottom = 8.dp),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        letterSpacing = MaterialTheme.typography.labelMedium.letterSpacing,
    )
}

@Composable
fun SettingsGroup(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
    ) {
        Column(Modifier.padding(vertical = 6.dp), content = content)
    }
}

@Composable
fun SettingDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 68.dp, end = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
    )
}

@Composable
fun SettingRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    value: String? = null,
    tint: Color? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(icon = icon, tint = tint)
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (value != null) {
            Text(
                value,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier.padding(start = 10.dp),
            )
        }
        if (trailing != null) {
            Box(Modifier.padding(start = 8.dp)) { trailing() }
        } else if (onClick != null) {
            Icon(
                Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp).size(20.dp),
            )
        }
    }
}

@Composable
fun SwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(icon = icon, enabled = enabled)
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

@Composable
fun IconBadge(icon: ImageVector, tint: Color? = null, enabled: Boolean = true) {
    val color = tint ?: MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(MaterialTheme.shapes.small)
            .background(color.copy(alpha = if (enabled) 0.14f else 0.07f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (enabled) color else color.copy(alpha = 0.45f),
            modifier = Modifier.size(21.dp),
        )
    }
}

@Composable
fun InfoNote(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Rounded.Info,
    tone: Color = MaterialTheme.colorScheme.primary,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = tone.copy(alpha = 0.09f),
        border = BorderStroke(1.dp, tone.copy(alpha = 0.22f)),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
            Icon(icon, contentDescription = null, tint = tone, modifier = Modifier.size(19.dp))
            Text(
                text,
                modifier = Modifier.padding(start = 10.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun StatusPill(text: String, active: Boolean, modifier: Modifier = Modifier) {
    val color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = color.copy(alpha = 0.13f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(color),
            )
            Text(
                text,
                modifier = Modifier.padding(start = 6.dp),
                style = MaterialTheme.typography.labelSmall,
                color = color,
            )
        }
    }
}

@Composable
fun PingBadge(ms: Long, loading: Boolean = false, modifier: Modifier = Modifier) {
    val color = when {
        loading -> MaterialTheme.colorScheme.onSurfaceVariant
        ms < 0 -> MaterialTheme.colorScheme.onSurfaceVariant
        ms < 120 -> MaterialTheme.colorScheme.primary
        ms < 350 -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.error
    }
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = color.copy(alpha = 0.12f),
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(11.dp),
                    strokeWidth = 1.5.dp,
                    color = color,
                )
            } else {
                Text(Format.ping(ms), style = MaterialTheme.typography.labelSmall, color = color)
            }
        }
    }
}

@Composable
fun ProtocolBadge(protocol: ProxyProtocol) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
    ) {
        Text(
            text = protocolLabel(protocol),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
        )
    }
}

fun protocolLabel(protocol: ProxyProtocol): String = when (protocol) {
    ProxyProtocol.VMESS -> "VMess"
    ProxyProtocol.VLESS -> "VLESS"
    ProxyProtocol.TROJAN -> "Trojan"
    ProxyProtocol.SHADOWSOCKS -> "SS"
    ProxyProtocol.SOCKS -> "SOCKS"
    ProxyProtocol.HTTP -> "HTTP"
    ProxyProtocol.WIREGUARD -> "WG"
    ProxyProtocol.CUSTOM -> "JSON"
    ProxyProtocol.GROUP -> "Группа"
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    text: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(76.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
        }
        Text(
            title,
            modifier = Modifier.padding(top = 18.dp),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text,
            modifier = Modifier.padding(top = 6.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        if (actionLabel != null && onAction != null) {
            Button(onClick = onAction, modifier = Modifier.padding(top = 18.dp)) {
                Text(actionLabel)
            }
        }
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String = "Удалить",
    destructive: Boolean = true,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            TextButton(onClick = { onConfirm(); onDismiss() }) {
                Text(
                    confirmLabel,
                    color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

@Composable
fun ChoiceField(
    label: String,
    value: String,
    options: List<Pair<String, String>>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { Icon(Icons.Rounded.ExpandMore, contentDescription = null) },
            modifier = Modifier.fillMaxWidth(),
        )
        Box(
            Modifier
                .matchParentSize()
                .clickable { expanded = true },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.second) },
                    onClick = {
                        onSelect(option.first)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
fun CheckRow(
    checked: Boolean,
    title: String,
    subtitle: String? = null,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Column(Modifier.weight(1f).padding(start = 4.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
fun KeyValueRow(label: String, value: String, valueColor: Color = MaterialTheme.colorScheme.onSurface) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(value, style = MaterialTheme.typography.bodyMedium, color = valueColor, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun SpeedGraph(
    points: List<TrafficPoint>,
    modifier: Modifier = Modifier,
) {
    val downColor = MaterialTheme.colorScheme.primary
    val upColor = MaterialTheme.colorScheme.tertiary
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
    val max = remember(points) {
        (points.maxOfOrNull { maxOf(it.down, it.up) } ?: 0L).coerceAtLeast(1L).toFloat()
    }
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        if (width <= 0f || height <= 0f) return@Canvas
        for (index in 1..3) {
            val y = height * index / 4f
            drawLine(gridColor, Offset(0f, y), Offset(width, y), strokeWidth = 1f)
        }
        fun build(values: List<Float>): Path {
            val path = Path()
            if (values.isEmpty()) return path
            val step = width / (values.size - 1).coerceAtLeast(1)
            values.forEachIndexed { index, value ->
                val x = if (values.size == 1) width / 2f else step * index
                val y = height - (value / max) * (height * 0.82f) - height * 0.08f
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            if (values.size == 1) path.lineTo(width / 2f + 1f, height / 2f)
            return path
        }
        if (points.isNotEmpty()) {
            val downPath = build(points.map { it.down.toFloat() })
            val fillPath = Path().apply {
                addPath(downPath)
                lineTo(width, height)
                lineTo(0f, height)
                close()
            }
            drawPath(
                fillPath,
                Brush.verticalGradient(listOf(downColor.copy(alpha = 0.22f), Color.Transparent)),
            )
            drawPath(downPath, downColor, style = Stroke(width = 2.5.dp.toPx()))
            drawPath(build(points.map { it.up.toFloat() }), upColor, style = Stroke(width = 2.dp.toPx()))
        }
    }
}

@Composable
fun MonospaceField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    minLines: Int = 5,
    isError: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = modifier.fillMaxWidth(),
        minLines = minLines,
        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
        isError = isError,
    )
}

@Composable
fun RowSpacer(width: Dp) = Spacer(Modifier.width(width))

@Composable
fun ColumnSpacer(height: Dp) = Spacer(Modifier.height(height))
