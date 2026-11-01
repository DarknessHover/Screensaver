@file:OptIn(ExperimentalMaterial3Api::class)

package com.screensaver.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.screensaver.data.local.entities.WallpaperDomain
import com.screensaver.ui.components.TimeAxisPicker
import com.screensaver.ui.theme.Radius
import com.screensaver.ui.theme.Sizes
import com.screensaver.ui.theme.Space
import com.screensaver.ui.util.formatDuration
import com.screensaver.ui.util.formatMinute
import com.screensaver.ui.util.parseMinute
import com.screensaver.ui.viewmodel.RuleEditorViewModel
import kotlinx.coroutines.launch

@Composable
fun RuleEditorScreen(
    navController: NavController,
    ruleId: Long?,
    defaultDomain: WallpaperDomain,
    vm: RuleEditorViewModel,
) {
    val state by vm.state.collectAsStateWithLifecycle()

    LaunchedEffect(ruleId, defaultDomain) { vm.load(ruleId, defaultDomain) }

    // 时间编辑 Dialog 状态：null = 无弹窗，START/END = 在改起/止
    var editingTimeDialog by remember { mutableStateOf<WhichTime?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // 收集一次性校验与失败消息。
    LaunchedEffect(vm) {
        vm.events.collect { snackbarHostState.showSnackbar(it) }
    }

    // 通过 SAF 选图，并确认应用获得了持久读取授权。
    val context = LocalContext.current
    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val persisted = runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
            context.contentResolver.persistedUriPermissions.any {
                it.uri == uri && it.isReadPermission
            }
        }.getOrDefault(false)
        if (persisted) {
            vm.setImageUri(uri.toString())
        } else {
            scope.launch {
                snackbarHostState.showSnackbar("无法保留该图片的长期读取权限，请选择其他图片")
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (state.isNew) "新建规则" else "编辑规则",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                // 保存中或未选图时点击无副作用；ViewModel 还会再次阻止重复提交。
                onClick = {
                    if (state.selectedImageUri != null && !state.saving) {
                        vm.save { navController.popBackStack() }
                    }
                },
                containerColor = if (state.selectedImageUri != null && !state.saving) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                contentColor = if (state.selectedImageUri != null && !state.saving) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            ) {
                // 保存中显示进度圈，明确"正在提交"
                if (state.saving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Icon(Icons.Filled.Check, contentDescription = "保存")
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = Space.Screen, end = Space.Screen, top = Space.L, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(Space.L),
        ) {
            // ========== 壁纸域 ==========
            // 两个壁纸域各自拥有独立时间线；切换域后同步刷新占用区间。
            SectionCard(
                title = "壁纸域",
                icon = Icons.Filled.Settings,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Space.S),
                ) {
                    WallpaperDomain.entries.forEach { d ->
                        FilterChip(
                            selected = state.wallpaperDomain == d,
                            onClick = { vm.setDomain(d) },
                            label = { Text(WallpaperDomain.labelOf(d)) },
                            modifier = Modifier.weight(1f),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                        )
                    }
                }
                Spacer(Modifier.height(Space.S))
                Text(
                    WallpaperDomain.hintOf(state.wallpaperDomain),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // ========== 段 1: 时间段 ==========
            // 时间卡片用于精确输入，时间轴用于预览和拖动微调。
            SectionCard(
                title = "时间段",
                icon = Icons.Filled.DateRange,
            ) {
                // 起止时间可点击卡片
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    TimeCard(
                        label = "开始",
                        timeText = formatMinute(state.startMinute),
                        onClick = { editingTimeDialog = WhichTime.START },
                        modifier = Modifier.weight(1f),
                    )
                    TimeCard(
                        label = "结束",
                        timeText = formatMinute(state.endMinute),
                        onClick = { editingTimeDialog = WhichTime.END },
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(Space.M))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "灰带为其他规则已占用时段，请避开；也可直接拖下方时间轴微调",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(Space.S))
                    Text(
                        formatDuration(state.endMinute - state.startMinute),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(Modifier.height(Space.S))
                TimeAxisPicker(
                    startMinute = state.startMinute,
                    endMinute = state.endMinute,
                    onRangeChange = vm::setRange,
                    occupiedIntervals = state.occupiedIntervals,
                )
            }

            // ========== 段 3: 素材 ==========
            // 图片直接绑定到当前规则，所有规则默认生效。
            SectionCard(
                title = "壁纸图",
                icon = Icons.Filled.Favorite,
            ) {
                if (state.selectedImageUri != null) {
                    // 已选图：预览 + 更换/移除
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(Radius.M))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outlineVariant,
                                shape = RoundedCornerShape(Radius.M),
                            ),
                    ) {
                        AsyncImage(
                            model = state.selectedImageUri,
                            contentDescription = "壁纸预览",
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .clip(RoundedCornerShape(Radius.M)),
                        )
                    }
                    Spacer(Modifier.height(Space.M))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "已选图片",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { imagePicker.launch(arrayOf("image/*")) }) {
                            Text("更换")
                        }
                        TextButton(onClick = { vm.setImageUri(null) }) {
                            Text("移除", color = MaterialTheme.colorScheme.error)
                        }
                    }
                } else {
                    // 未选：描边占位卡，明确"这里是可点区域"
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { imagePicker.launch(arrayOf("image/*")) },
                        shape = RoundedCornerShape(Radius.M),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = Space.XXL, horizontal = Space.L),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Filled.Add,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(28.dp),
                                )
                            }
                            Spacer(Modifier.height(Space.M))
                            Text(
                                "点击为${WallpaperDomain.labelOf(state.wallpaperDomain)}选一张图",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Spacer(Modifier.height(Space.XS))
                            Text(
                                "推荐使用竖屏高清图",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }

    // 时间编辑 Dialog：根据 editingTimeDialog 状态决定弹哪个
    editingTimeDialog?.let { which ->
        val title = if (which == WhichTime.START) "选择开始时间" else "选择结束时间"
        val initial = if (which == WhichTime.START) state.startMinute else state.endMinute
        TimePickerDialog(
            title = title,
            initialMinuteOfDay = initial,
            allowEndOfDay = which == WhichTime.END,
            onDismiss = { editingTimeDialog = null },
            onConfirm = { newMinute ->
                // 先规范化候选区间，再检查它是否与当前域的已有规则冲突。
                val (candStart, candEnd) = if (which == WhichTime.START) {
                    val maxStart = (state.endMinute - 5).coerceAtLeast(0)
                    newMinute.coerceIn(0, maxStart) to state.endMinute
                } else {
                    val minEnd = (state.startMinute + 5).coerceAtMost(1440)
                    state.startMinute to newMinute.coerceIn(minEnd, 1440)
                }
                val conflict = vm.findConflict(candStart, candEnd)
                if (conflict != null) {
                    scope.launch { snackbarHostState.showSnackbar(conflict) }
                    editingTimeDialog = null
                } else {
                    vm.setRange(candStart, candEnd)
                    editingTimeDialog = null
                }
            },
        )
    }
}

/** 时间卡片：点击后弹 TimePicker Dialog */
@Composable
private fun TimeCard(
    label: String,
    timeText: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        onClick = onClick,
        shape = RoundedCornerShape(Radius.M),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Space.L, vertical = Space.M),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Space.XS))
            Text(
                timeText,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/** 标记正在编辑的时间端 */
private enum class WhichTime { START, END }

/**
 * 时间输入 Dialog（键盘输入 HH:mm，24 小时制）。
 * - 替代 Material3 TimePicker：后者表盘仅 12 小时制（1-12 + AM/PM），无法选 0-23，且不支持直接键盘输入。
 * - 输入后校验格式与 0-23 时、0-59 分范围，非法则红框提示且不允许确认。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(
    title: String,
    initialMinuteOfDay: Int,
    allowEndOfDay: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    var text by remember(initialMinuteOfDay) {
        mutableStateOf(formatMinute(initialMinuteOfDay))
    }
    var invalid by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(Radius.XXL),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
        ) {
            Column(
                modifier = Modifier.padding(Space.XXL),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 16.dp),
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { t ->
                        // 仅允许数字与冒号，最多 5 字符（HH:mm）
                        val cleaned = t.filter { it.isDigit() || it == ':' }.take(5)
                        text = cleaned
                        invalid = false
                    },
                    label = { Text("HH:mm") },
                    placeholder = { Text("例如 09:30") },
                    isError = invalid,
                    supportingText = {
                        if (invalid) Text("请按 24 小时制输入，如 14:05")
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done,
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) { Text("取消") }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = {
                        val parsed = parseMinute(text, allowEndOfDay)
                        if (parsed != null) {
                            onConfirm(parsed)
                        } else {
                            invalid = true
                        }
                    }) { Text("确定") }
                }
            }
        }
    }
}

/**
 * 段卡片：图标徽标 + 标题 + 内容。
 *
 * 图标放进 32dp 圆形色块，而不是裸放一个 20dp 小图标——
 * 裸图标在长表单里几乎看不见，加了底之后它才真正起到"段落路标"的作用。
 */
@Composable
private fun SectionCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.XL),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
    ) {
        Column(modifier = Modifier.padding(Space.L)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(Sizes.IconBadge)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(Sizes.IconS),
                    )
                }
                Spacer(Modifier.width(Space.M))
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Spacer(Modifier.height(Space.M))
            content()
        }
    }
}
