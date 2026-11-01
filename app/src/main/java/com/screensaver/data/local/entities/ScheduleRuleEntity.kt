package com.screensaver.data.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 一条切换规则 = 一段“当天的分钟区间” + 绑定的素材。
 *
 * 关键约束（对齐架构文档“剪开拉直”模型）：
 *  - 一条记录【永远不跨午夜】，即 endMinute > startMinute，且都落在 [0, 1440]。
 *  - 用户若要表达 22:00–02:00，需分别建立 22:00–24:00 与 00:00–02:00 两条规则。
 *  - 因此调度引擎里【不需要】任何“跨午夜 wrap-around”特殊分支，只需判断 start <= now < end。
 *
 * startMinute / endMinute 含义：当天第几分钟（0=00:00, 1439=23:59, 1440=24:00）。
 */
@Entity(tableName = "rules")
data class ScheduleRuleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "start_minute")
    val startMinute: Int,

    @ColumnInfo(name = "end_minute")
    val endMinute: Int,

    /**
     * 绑定的壁纸图 URI（SAF grant 的 content:// URI），可空。
     * 编辑器只允许在已获得持久读取授权后保存非空 URI；字段保留可空以兼容数据库读取，
     * 调度与应用链路会跳过空值。
     */
    @ColumnInfo(name = "image_uri")
    val imageUri: String? = null,

    /**
     * 所属壁纸域。
     * LOCK=屏保域(设置锁屏壁纸) / SYSTEM=桌面域(设置桌面壁纸)，默认 SYSTEM 以兼容既有规则。
     */
    @ColumnInfo(name = "wallpaper_domain")
    val wallpaperDomain: WallpaperDomain = WallpaperDomain.SYSTEM,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,
)
