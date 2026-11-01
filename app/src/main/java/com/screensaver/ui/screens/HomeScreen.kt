@file:OptIn(ExperimentalMaterial3Api::class)

package com.screensaver.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.screensaver.data.local.entities.ScheduleRuleEntity
import com.screensaver.data.local.entities.WallpaperDomain
import com.screensaver.ui.components.DayTimelineBar
import com.screensaver.ui.theme.BrandGradient
import com.screensaver.ui.theme.Radius
import com.screensaver.ui.theme.Sizes
import com.screensaver.ui.theme.Space
import com.screensaver.ui.util.formatDuration
import com.screensaver.ui.util.formatMinute
import com.screensaver.ui.util.nowMinuteOfDay
import com.screensaver.ui.viewmodel.HomeViewModel
import kotlinx.coroutines.delay

/**
 * 首页。视觉层级自上而下：
 *   域切换 → 状态 Hero（唯一的品牌色大色块）→ 警告条（按需）→ 分区标题 → 规则列表
 *
 * 顶栏刻意退回中性底色：原来顶栏和 Hero 卡都是 primaryContainer，
 * 两个大色块叠在一起互相抢焦点，反而让"当前生效规则"这个最重要的信息被稀释。
 */
@Composable
fun HomeScreen(navController: NavController, vm: HomeViewModel) {
    val filteredRules by vm.filteredRules.collectAsStateWithLifecycle()
    val currentRuleForTab by vm.currentRuleForTab.collectAsStateWithLifecycle()
    val selectedTab by vm.selectedTab.collectAsStateWithLifecycle()
    val exactGranted by vm.exactAlarmGranted.collectAsStateWithLifecycle()
    val batteryWhitelisted by vm.batteryWhitelisted.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // 从系统设置页返回时自动重新检测精确闹钟权限
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                vm.refreshExactAlarmStatus()
                // 白名单跳转 Intent 无返回结果（Output: Nothing），回到前台必须复查，
                // 授权后卡片消失 / 日后撤销时卡片对称重现
                vm.refreshBatteryWhitelist()
                // 暖启动（从最近任务/点图标恢复）不走 onCreate，
                // 必须在此重算"当前生效规则"，否则 Hero/生效中标签停留在上次前台时刻的旧状态
                vm.recomputeCurrent()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // App 停留前台跨过时间边界时，"生效中"要跟着走：每分钟重算一次（与规则分钟粒度一致）
    LaunchedEffect(Unit) {
        while (true) {
            vm.recomputeCurrent()
            delay(60_000L)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "定时屏保",
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
        floatingActionButton = {
            // 刻意用纯色而非渐变：Surface 会按 tonalElevation 往容器色上叠一层覆盖，
            // 透明容器 + 自绘渐变会糊出色块；且 56dp 高的控件根本读不出渐变，只会显脏。
            // 渐变留给 Hero 那种大面积品牌色块。
            ExtendedFloatingActionButton(
                onClick = { navController.navigate("editor/-1?domain=${selectedTab.name}") },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("新建", style = MaterialTheme.typography.labelLarge) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = Space.Screen),
        ) {
            Spacer(Modifier.height(Space.S))

            DomainSegmentedTabs(
                selected = selectedTab,
                onSelect = vm::selectTab,
            )

            Spacer(Modifier.height(Space.M))

            StatusHeroCard(
                domain = selectedTab,
                current = currentRuleForTab,
            )

            if (!exactGranted) {
                Spacer(Modifier.height(Space.M))
                WarningBanner(
                    title = "精确闹钟未授权",
                    description = "当前为非精确模式，切换壁纸可能延迟数分钟。授权后可精准切换。",
                    actionText = "去授权",
                    onActionClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            val intent = Intent(
                                android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM
                            ).apply {
                                data = Uri.parse("package:${context.packageName}")
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            try {
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                // 某些 ROM 没有这个 action，回退到应用详情页
                                val fallback = Intent(
                                    android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                                ).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                }
                                context.startActivity(fallback)
                            }
                        }
                    },
                )
            }

            // 后台保活（电池优化白名单）引导卡：与精确闹钟卡可能同屏，间距沿用 Space.M
            if (!batteryWhitelisted) {
                Spacer(Modifier.height(Space.M))
                WarningBanner(
                    title = "后台保活未授权",
                    description = "系统省电策略可能冻结本应用，导致定时切换失效。" +
                        "加入电池优化白名单可降低失效概率；部分 ROM 仍可能有独立的省电策略。",
                    actionText = "去授权",
                    onActionClick = {
                        val intent = Intent(
                            android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                        ).apply {
                            data = Uri.parse("package:${context.packageName}")
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        try {
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            // 部分 ROM 拒绝/无此 action，回退到应用详情页（一定能打开）
                            val fallback = Intent(
                                android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                            ).apply {
                                data = Uri.parse("package:${context.packageName}")
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            context.startActivity(fallback)
                        }
                    },
                )
            }

            Spacer(Modifier.height(Space.L))

            // 分区标题行：左标题 + 右"立即应用"（次要操作，不再占满一行）
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${WallpaperDomain.labelOf(selectedTab)}规则",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = vm::applyNow) {
                    Text("立即应用", style = MaterialTheme.typography.labelLarge)
                }
            }

            Spacer(Modifier.height(Space.S))

            if (filteredRules.isEmpty()) {
                EmptyRulesHint(
                    domain = selectedTab,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(Space.S),
                    contentPadding = PaddingValues(bottom = 96.dp),
                ) {
                    items(filteredRules, key = { it.id }) { rule ->
                        RuleCard(
                            rule = rule,
                            isCurrent = currentRuleForTab?.id == rule.id,
                            onEdit = { navController.navigate("editor/${rule.id}") },
                            onDelete = { vm.deleteRule(rule) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * 顶部"屏保 / 桌面"切换器。两选项互斥单选，切换后列表与 Hero 卡随之刷新。
 * 加了图标：文字之外多一条识别通道，眼睛不必读字就能认出当前在哪个域。
 */
@Composable
private fun DomainSegmentedTabs(
    selected: WallpaperDomain,
    onSelect: (WallpaperDomain) -> Unit,
) {
    val options = WallpaperDomain.entries
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, domain ->
            SegmentedButton(
                selected = domain == selected,
                onClick = { onSelect(domain) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                icon = {
                    Icon(
                        imageVector = domainIcon(domain),
                        contentDescription = null,
                        modifier = Modifier.size(SegmentedButtonDefaults.IconSize),
                    )
                },
            ) {
                Text(
                    text = WallpaperDomain.labelOf(domain),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

private fun domainIcon(domain: WallpaperDomain): ImageVector = when (domain) {
    WallpaperDomain.LOCK -> Icons.Filled.Lock
    WallpaperDomain.SYSTEM -> Icons.Filled.Home
}

/**
 * 状态 Hero 卡：当前所选域的生效规则。
 *
 * 生效时用 primaryContainer —— 全屏唯一的品牌色块，视线第一落点；
 * 未生效时退回中性描边卡，不再用灰块把"没有规则"渲染得像出错了。
 * 卡内嵌 [DayTimelineBar]，把这段时间落在一天里的位置直接画出来。
 */
@Composable
private fun StatusHeroCard(domain: WallpaperDomain, current: ScheduleRuleEntity?) {
    val isActive = current != null

    // 当前时刻：供 Hero 卡时间条画"现在"竖线，每分钟刷新一次
    var nowMinute by remember { mutableIntStateOf(nowMinuteOfDay()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000L)
            nowMinute = nowMinuteOfDay()
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.XL),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        border = if (isActive) {
            null
        } else {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        },
    ) {
        // 生效：品牌渐变铺满，全屏唯一的彩色块；未生效：中性纯色卡
        val heroBg: Modifier = if (isActive) {
            Modifier.background(BrandGradient.hero(), RoundedCornerShape(Radius.XL))
        } else {
            Modifier.background(
                MaterialTheme.colorScheme.surfaceContainerLow,
                RoundedCornerShape(Radius.XL),
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(heroBg)
                .padding(Space.L),
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(Sizes.IconBadge)
                            .clip(CircleShape)
                            // 渐变上用半透明黑压深，白图标在明暗主题下都够对比度
                            .background(
                                if (isActive) {
                                    Color.Black.copy(alpha = 0.22f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerHighest
                                }
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = if (isActive) Icons.Filled.CheckCircle else Icons.Filled.Info,
                            contentDescription = null,
                            tint = if (isActive) {
                                Color.White
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.size(Sizes.IconS),
                        )
                    }
                    Spacer(Modifier.width(Space.M))
                    Text(
                        text = WallpaperDomain.labelOf(domain),
                        style = MaterialTheme.typography.titleSmall,
                        color = if (isActive) {
                            Color.White
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        modifier = Modifier.weight(1f),
                    )
                    if (isActive) {
                        // 渐变上不做实心药丸（换图就换底，实心色随时会撞）。
                        // 白描边 + 白字，对比度由渐变本身保证（实测 5.7–7.2:1）
                        Box(
                            modifier = Modifier
                                .border(
                                    width = 1.dp,
                                    color = Color.White.copy(alpha = 0.75f),
                                    shape = CircleShape,
                                )
                                .padding(horizontal = 10.dp, vertical = 3.dp),
                        ) {
                            Text(
                                text = "生效中",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                            )
                        }
                    } else {
                        StatusPill(
                            text = "未生效",
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Spacer(Modifier.height(Space.M))

                if (isActive) {
                    Text(
                        text = "${formatMinute(current!!.startMinute)} – ${formatMinute(current.endMinute)}",
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color.White,
                    )
                    Spacer(Modifier.height(Space.XS))
                    Text(
                        text = "共 ${formatDuration(current.endMinute - current.startMinute)}",
                        style = MaterialTheme.typography.bodySmall,
                        // 0.9 而非 0.75：渐变底比浅色容器深，压太狠会掉出 AA
                        color = Color.White.copy(alpha = 0.9f),
                    )
                    Spacer(Modifier.height(Space.M))
                    DayTimelineBar(
                        startMinute = current.startMinute,
                        endMinute = current.endMinute,
                        nowMinute = nowMinute,
                        height = Sizes.TimelineThick,
                        trackColor = Color.White.copy(alpha = 0.25f),
                        segmentColor = Color.White,
                    )
                } else {
                    Text(
                        text = "当前无该域生效规则，保持已有${WallpaperDomain.labelOf(domain)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * 规则卡片。起止时间是唯一标识，因此给它最大字号；
 * 下方一行时长 + 时间条，让"这条规则占一天的哪一段"可以扫读而不必逐条读字。
 */
@Composable
private fun RuleCard(
    rule: ScheduleRuleEntity,
    isCurrent: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.XL),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        border = if (isCurrent) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f))
        } else {
            null
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = Space.L, end = Space.XS, top = Space.M, bottom = Space.M),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${formatMinute(rule.startMinute)} – ${formatMinute(rule.endMinute)}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (isCurrent) {
                        Spacer(Modifier.width(Space.S))
                        StatusPill(
                            text = "生效中",
                            containerColor = MaterialTheme.colorScheme.tertiary,
                            contentColor = MaterialTheme.colorScheme.onTertiary,
                        )
                    }
                }
                Spacer(Modifier.height(Space.XS))
                Text(
                    text = formatDuration(rule.endMinute - rule.startMinute),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(Space.S))
                DayTimelineBar(
                    startMinute = rule.startMinute,
                    endMinute = rule.endMinute,
                    segmentColor = if (isCurrent) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                    },
                )
            }
            IconButton(onClick = onEdit) {
                Icon(
                    Icons.Filled.Edit,
                    contentDescription = "编辑",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { showDeleteDialog = true }) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "删除",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            shape = RoundedCornerShape(Radius.XXL),
            title = { Text("删除规则") },
            text = {
                Text("确定删除规则「${formatMinute(rule.startMinute)} – ${formatMinute(rule.endMinute)}」吗？此操作不可撤销。")
            },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    onDelete()
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("取消") }
            },
        )
    }
}

/**
 * 空状态：列表为空时给出下一步文字指引。
 * 主操作由 Scaffold 级常驻 FAB 承担，这里不再内嵌"新建"按钮——
 * 空态按钮与 FAB 曾构成同屏双入口（DESIGN §1 P3 条款随之修订为：
 * 空态给指引，主操作由常驻 FAB 承担）。
 */
@Composable
private fun EmptyRulesHint(
    domain: WallpaperDomain,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = Space.L),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(BrandGradient.hero()),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.DateRange,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(28.dp),
            )
        }
        Spacer(Modifier.height(Space.L))
        Text(
            text = "还没有${WallpaperDomain.labelOf(domain)}规则",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(Space.XS))
        Text(
            text = "点击右下角「新建」，选一张图并设定生效时段",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** 状态药丸：小尺寸标签，只用于承载"生效中 / 未生效"这类两态信息。 */
@Composable
private fun StatusPill(
    text: String,
    containerColor: Color,
    contentColor: Color,
) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(containerColor)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
        )
    }
}

/**
 * 通用警告 Banner：errorContainer 卡 + 警示图标 + 标题/说明 + 动作按钮。
 * 精确闹钟与电池优化白名单两处共用（视觉硬规则：一屏唯一品牌色块是 Hero，
 * 警示类一律 errorContainer 中性样式，不引入第二个品牌色块）。
 */
@Composable
private fun WarningBanner(
    title: String,
    description: String,
    actionText: String,
    onActionClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.XL),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(Space.L),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(Sizes.IconBadge)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.error),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onError,
                    modifier = Modifier.size(Sizes.IconS),
                )
            }
            Spacer(Modifier.width(Space.M))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Spacer(Modifier.width(Space.S))
            TextButton(onClick = onActionClick) { Text(actionText) }
        }
    }
}
