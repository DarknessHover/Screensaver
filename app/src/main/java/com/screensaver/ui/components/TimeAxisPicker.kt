@file:OptIn(ExperimentalMaterial3Api::class)

package com.screensaver.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.screensaver.ui.theme.Radius
import com.screensaver.ui.theme.Space
import com.screensaver.ui.util.formatMinute
import com.screensaver.ui.util.nowMinuteOfDay
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * 时间轴拖选组件：把一天当作 0:00 → 24:00 的一条直线，
 * 用户直接拖动两端手柄选择"从几分 → 到几分"。
 *
 * 底层用 Material3 RangeSlider，每档 5 分钟。
 * 因为轴是线性的、start < end 恒成立，所以天然不支持跨午夜——
 * 这正是"剪开拉直"模型：跨午夜由用户在编辑器里拆成两条规则表达。
 *
 * 新增体验：当前时间竖线（红）+ 起止时间数字（手柄上方）+ 2 小时一档刻度。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeAxisPicker(
    startMinute: Int,
    endMinute: Int,
    onRangeChange: (start: Int, end: Int) -> Unit,
    modifier: Modifier = Modifier,
    occupiedIntervals: List<Pair<Int, Int>> = emptyList(),
) {
    val steps = (1440 / 5) - 1
    val stepFloat = 1f / (steps + 1)

    var range by remember(startMinute, endMinute) {
        mutableStateOf((startMinute / 1440f)..(endMinute / 1440f))
    }

    // 当前时间（每分钟刷新）；用 Calendar 基于 nowMinuteOfDay()，兼容 API24/25（java.time 需 26+）
    var nowMinute by remember { mutableIntStateOf(nowMinuteOfDay()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000L)
            nowMinute = nowMinuteOfDay()
        }
    }

    Column(modifier.padding(horizontal = Space.S, vertical = Space.XS)) {
        // 起止时间数字（手柄上方）
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Space.L),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = formatMinute(startMinute),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = formatMinute(endMinute),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.height(Space.XS))
        // E1: 当前时间竖线 + RangeSlider
        BoxWithConstraints(modifier = Modifier.fillMaxWidth().height(48.dp)) {
            val trackPadding = 16.dp
            val trackWidth = maxWidth - trackPadding * 2
            val nowPercent = nowMinute / 1440f
            val nowX = trackPadding + trackWidth * nowPercent

            // 已占用区间灰带（其他规则），可视定位"哪里已被占用、应避开"
            occupiedIntervals.forEach { (os, oe) ->
                if (oe > os) {
                    val px = trackPadding + trackWidth * (os / 1440f)
                    val w = trackWidth * ((oe - os) / 1440f)
                    Box(
                        modifier = Modifier
                            .offset { IntOffset(px.roundToPx(), 2) }
                            .width(w)
                            .fillMaxHeight()
                            .padding(vertical = Space.S)
                            .clip(RoundedCornerShape(Radius.S))
                            .background(
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.22f),
                            ),
                    )
                }
            }

            // 范围选择器
            RangeSlider(
                value = range,
                onValueChange = { r ->
                    var s = r.start
                    var e = r.endInclusive
                    if (e - s < stepFloat) e = (s + stepFloat).coerceAtMost(1f)
                    range = s..e
                    onRangeChange((s * 1440).roundToInt(), (e * 1440).roundToInt())
                },
                valueRange = 0f..1f,
                steps = steps,
                modifier = Modifier.fillMaxWidth(),
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    activeTickColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.35f),
                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    inactiveTickColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f),
                ),
            )

            // 当前时间竖线
            Box(
                modifier = Modifier
                    .offset { IntOffset(nowX.roundToPx() - 1, 0) }
                    .width(2.dp)
                    .fillMaxHeight()
                    .padding(vertical = 2.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.error),
            )
            // "现在" 标签贴底，避免压住滑块手柄
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .offset { IntOffset(nowX.roundToPx() - 18, 0) }
                    .clip(RoundedCornerShape(Radius.S))
                    .background(MaterialTheme.colorScheme.error)
                    .padding(horizontal = Space.XS, vertical = 1.dp),
            ) {
                Text(
                    text = "现在",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onError,
                )
            }
        }
        Spacer(Modifier.height(Space.XS))
        // 刻度：每 4 小时一档。原为每 2 小时共 13 个标签，窄屏上会互相重叠。
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Space.M),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            listOf(0, 4, 8, 12, 16, 20, 24).forEach { h ->
                Text(
                    text = formatMinute(h * 60),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
