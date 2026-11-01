package com.screensaver.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.screensaver.ui.theme.Sizes

/**
 * 一日时间条：把 [startMinute, endMinute) 这段区间画在 0:00 → 24:00 的整条轴上。
 *
 * 首页 Hero 卡与规则列表共用同一个组件，保证"同一段时间在两处显示的比例完全一致"——
 * 用户靠形状就能认出是哪条规则，不必再逐字读时间。
 *
 * @param nowMinute 传入则额外画一条"当前时刻"竖线；列表项通常不传（避免满屏红线）。
 */
@Composable
fun DayTimelineBar(
    startMinute: Int,
    endMinute: Int,
    modifier: Modifier = Modifier,
    nowMinute: Int? = null,
    height: Dp = Sizes.TimelineThin,
    trackColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    segmentColor: Color = MaterialTheme.colorScheme.primary,
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(height),
    ) {
        val trackWidth = maxWidth
        val start = startMinute.coerceIn(0, 1440) / 1440f
        val end = endMinute.coerceIn(0, 1440) / 1440f

        // 底轨：一整天
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(trackColor),
        )

        // 生效区间
        Box(
            modifier = Modifier
                .offset(x = trackWidth * start)
                .width((trackWidth * (end - start)).coerceAtLeast(height))
                .fillMaxHeight()
                .clip(CircleShape)
                .background(segmentColor),
        )

        // 当前时刻
        if (nowMinute != null) {
            Box(
                modifier = Modifier
                    .offset(x = (trackWidth * (nowMinute.coerceIn(0, 1439) / 1440f)) - 1.dp)
                    .width(2.dp)
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.error),
            )
        }
    }
}
