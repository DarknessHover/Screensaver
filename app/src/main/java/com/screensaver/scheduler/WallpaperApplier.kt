package com.screensaver.scheduler

import android.annotation.SuppressLint
import android.app.WallpaperManager
import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import androidx.core.net.toUri
import com.screensaver.data.local.entities.WallpaperDomain
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * 应用壁纸用例：从持久化 URI 读取图片并设置为【指定壁纸域】的壁纸。
 *
 * 权限处理：
 *  - 仅当该 URI 已获得持久化读取权限时才尝试，否则安全跳过（绝不崩溃）。
 *  - 按 [WallpaperDomain] 仅设置本域壁纸——LOCK→锁屏(FLAG_LOCK)、SYSTEM→桌面(FLAG_SYSTEM)。
 *
 * 图片按屏幕尺寸降采样以降低内存峰值；LOCK 设置失败后在进程内短暂退避，
 * 避免不支持 FLAG_LOCK 的系统在每次亮屏时重复执行失败调用。
 */
class WallpaperApplier(private val context: Context) {

    @SuppressLint("MissingPermission") // SET_WALLPAPER 为 normal 权限，安装即授予
    suspend fun apply(uri: String, domain: WallpaperDomain): Boolean = withContext(Dispatchers.IO) {
        val resolver: ContentResolver = context.contentResolver
        val parsed = uri.toUri()

        // 校验持久化权限（SAF 选图时已 takePersistableUriPermission）
        val persisted = resolver.persistedUriPermissions.any { it.uri == parsed }
        if (!persisted) {
            Log.w(TAG, "URI 无持久化权限，跳过：$uri")
            return@withContext false
        }

        // 锁屏失败后短暂退避，避免每次亮屏重复重试；退避到期后允许自动恢复。
        if (domain == WallpaperDomain.LOCK && SystemClock.elapsedRealtime() < lockRetryAfter) {
            Log.d(TAG, "FLAG_LOCK 处于失败退避期，暂时跳过锁屏设置：$uri")
            return@withContext false
        }

        val bitmap = decodeDownsampled(resolver, parsed) ?: return@withContext false
        try {
            val ok = setForDomain(WallpaperManager.getInstance(context), bitmap, domain)
            Log.i(TAG, "应用结果：domain=$domain ok=$ok uri=$uri")
            ok
        } catch (e: Exception) {
            Log.e(TAG, "设置壁纸失败：domain=$domain", e)
            false
        } finally {
            bitmap.recycle()
        }
    }

    private fun setForDomain(wm: WallpaperManager, bitmap: Bitmap, domain: WallpaperDomain): Boolean {
        return when (domain) {
            WallpaperDomain.SYSTEM -> {
                // minSdk 24：所有支持版本均可按域设置壁纸。
                runCatching {
                    wm.setBitmap(bitmap, null, true, WallpaperManager.FLAG_SYSTEM)
                }.onFailure { Log.w(TAG, "设置桌面壁纸失败", it) }.isSuccess
            }
            WallpaperDomain.LOCK -> {
                // minSdk 24：FLAG_LOCK API 在所有支持版本上存在，但厂商 ROM 仍可能拒绝。
                runCatching {
                    wm.setBitmap(bitmap, null, true, WallpaperManager.FLAG_LOCK)
                }.onSuccess {
                    lockRetryAfter = 0L
                }.onFailure {
                    lockRetryAfter = SystemClock.elapsedRealtime() + LOCK_RETRY_BACKOFF_MS
                    Log.w(TAG, "设置锁屏壁纸失败，进入短暂退避后将自动重试", it)
                }.isSuccess
            }
        }
    }

    /**
     * 降采样解码：先读源图尺寸，按屏幕目标分辨率计算 inSampleSize，再解码。
     * 大幅度降低大图（如 4000×3000）切换瞬间的内存峰值与 OOM 风险。
     */
    private fun decodeDownsampled(resolver: ContentResolver, uri: Uri): Bitmap? {
        val reqW = context.resources.displayMetrics.widthPixels.coerceAtLeast(1)
        val reqH = context.resources.displayMetrics.heightPixels.coerceAtLeast(1)

        // 第一遍：只读边界（不解码像素）
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching {
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        }
        val srcW = bounds.outWidth
        val srcH = bounds.outHeight
        if (srcW <= 0 || srcH <= 0) {
            // 边界读取失败（异常格式/流），退化：全尺寸解码，保持原行为
            return runCatching {
                resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
            }.getOrElse {
                Log.e(TAG, "读取图片失败：$uri", it)
                null
            }
        }

        val sample = calculateInSampleSize(srcW, srcH, reqW, reqH)
        return try {
            if (sample <= 1) {
                resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
            } else {
                val opts = BitmapFactory.Options().apply { inSampleSize = sample }
                Log.d(TAG, "降采样解码：${srcW}x$srcH → 目标 ${reqW}x$reqH, inSampleSize=$sample")
                resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            }
        } catch (e: IOException) {
            Log.e(TAG, "读取图片失败：$uri", e)
            null
        }
    }

    /**
     * 计算 inSampleSize（2 的幂）：使得解码后尺寸不大于目标分辨率。
     * 循环保证幂次与下界 >=1，避免负值/超大图边界问题。
     */
    private fun calculateInSampleSize(width: Int, height: Int, reqW: Int, reqH: Int): Int {
        var inSampleSize = 1
        var w = width
        var h = height
        // 任一边仍超过目标约 2 倍就继续二分，使解码后的最大边落在目标尺寸的约 1–2 倍。
        while (w > reqW * 2 || h > reqH * 2) {
            w /= 2
            h /= 2
            inSampleSize *= 2
        }
        return inSampleSize
    }

    companion object {
        private const val TAG = "WallpaperApplier"

        /** 下一次允许尝试 FLAG_LOCK 的进程运行时刻。 */
        @Volatile
        private var lockRetryAfter = 0L

        private const val LOCK_RETRY_BACKOFF_MS = 30 * 60 * 1000L
    }
}
