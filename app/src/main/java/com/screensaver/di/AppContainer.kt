package com.screensaver.di

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.util.Log
import androidx.room.Room
import com.screensaver.data.local.AppDatabase
import com.screensaver.data.local.entities.ScheduleRuleEntity
import com.screensaver.data.local.entities.WallpaperDomain
import com.screensaver.data.repository.ScreensaverRepository
import com.screensaver.domain.scheduler.ScheduleEngine
import com.screensaver.scheduler.AlarmScheduler
import com.screensaver.scheduler.WallpaperApplier
import com.screensaver.ui.util.formatMinute
import com.screensaver.ui.util.nowMinuteOfDay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 手动依赖容器；应用规模较小，显式构造依赖比引入依赖注入框架更直接。
 * 在 Application.onCreate 中构建一次，全局复用。
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    private val database: AppDatabase = Room.databaseBuilder(
        appContext,
        AppDatabase::class.java,
        AppDatabase.DATABASE_NAME
    ).fallbackToDestructiveMigration() // 规则数据可随时重建：schema 变更时直接重建空库，不写逐代 Migration（见 AppDatabase 注释）
        .build()

    val repository = ScreensaverRepository(database.ruleDao())

    private val scheduleEngine: ScheduleEngine = ScheduleEngine

    private val wallpaperApplier: WallpaperApplier = WallpaperApplier(appContext)

    val alarmScheduler: AlarmScheduler =
        AlarmScheduler(appContext, repository, scheduleEngine)

    /**
     * 电池优化白名单查询（API 23+，minSdk 24 全版本可用）。
     * ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS 跳转无返回结果（Output: Nothing），
     * 白名单真实状态只能由此复查——供 HomeViewModel 在 ON_RESUME 刷新引导卡片。
     */
    fun isIgnoringBatteryOptimizations(): Boolean {
        val pm = appContext.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(appContext.packageName)
    }

    /**
     * 立即应用两个壁纸域当前应生效的规则。
     * MainActivity、规则保存、开机恢复、闹钟到点和手动预览共用此链路。
     * 若无规则命中，不做事并保持该域已有壁纸。
     */
    suspend fun applyCurrentNow() = withContext(Dispatchers.IO) {
        val now = nowMinuteOfDay()
        val rules = repository.getAllRules()
        // 屏保域→锁屏壁纸、桌面域→桌面壁纸，各自独立生效、可共存。
        applyDomain(rules, now, WallpaperDomain.LOCK)
        applyDomain(rules, now, WallpaperDomain.SYSTEM)
    }

    /** 只应用指定壁纸域；供亮屏补偿复用同一条解析、日志与失败处理链路。 */
    suspend fun applyCurrentDomain(domain: WallpaperDomain): Boolean = withContext(Dispatchers.IO) {
        val now = nowMinuteOfDay()
        applyDomain(repository.getAllRules(), now, domain)
    }

    /** 单域应用：解析命中规则 → 设置该域壁纸，并记录排查日志。 */
    private suspend fun applyDomain(
        rules: List<ScheduleRuleEntity>,
        now: Int,
        domain: WallpaperDomain,
    ): Boolean {
        val cur = ScheduleEngine.resolve(now, rules, domain)
        if (cur == null) {
            Log.d(TAG, "$domain 当前无生效规则，保持已有壁纸。now=$now")
            return false
        }
        val uri = cur.imageUri
        val range = "${formatMinute(cur.startMinute)}-${formatMinute(cur.endMinute)}"
        if (uri == null) {
            Log.d(TAG, "$domain 当前规则 $range 未绑定图片，跳过切换。")
            return false
        }
        val ok = wallpaperApplier.apply(uri, domain)
        Log.d(TAG, "切换壁纸 domain=$domain：rule=$range uri=$uri ok=$ok")
        return ok
    }

    /** 删除或替换规则后，仅在没有其它规则引用时释放旧图片的持久化读取授权。 */
    suspend fun releasePersistedUriIfUnused(uriString: String?) = withContext(Dispatchers.IO) {
        if (uriString.isNullOrBlank()) return@withContext
        if (repository.getAllRules().any { it.imageUri == uriString }) return@withContext

        val uri = Uri.parse(uriString)
        val resolver = appContext.contentResolver
        val hasReadGrant = resolver.persistedUriPermissions.any {
            it.uri == uri && it.isReadPermission
        }
        if (!hasReadGrant) return@withContext

        runCatching {
            resolver.releasePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }.onFailure { Log.w(TAG, "释放旧图片 URI 授权失败：$uri", it) }
    }

    private companion object {
        const val TAG = "WallpaperSwitch"
    }
}
