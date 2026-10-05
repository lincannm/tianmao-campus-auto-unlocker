package com.fucktmall.unlockassistant

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 一次「自动跳过弹窗」的时间窗与动作日志。
 *
 * 为什么要有时间窗：无障碍服务一旦常驻乱点，会干扰日常用机。
 * 这里只在用户真的打开开锁界面之后的窗口期内动作，窗口结束自动失效。
 *
 * Activity 与 AccessibilityService 同进程，用 SharedPreferences 传状态最省事，
 * 也天然跨进程重建（服务被系统重启后仍能读到）。
 */
object Session {

    /** logcat 用同一个 tag，和 UnlockAccessibilityService 对齐，方便一条命令抓全。 */
    private const val TAG = "UnlockAssistant"

    private const val PREF = "unlock_session"
    private const val KEY_UNTIL_ELAPSED = "until_elapsed"
    private const val KEY_UNTIL_WALL = "until_wall"
    private const val KEY_FIRED = "fired_at_wall"

    /**
     * 「到门锁页后可以代按一次开锁」的授权标志。
     *
     * 只有这次跳转**真的开着**对应开关（设置里「打开 App 时自动点开锁」/
     * 「点小部件后自动点开锁」，见 [AppState]）才置位；服务按下一次立刻清掉
     * （[consumeUnlockArm] 是原子的），所以**一次跳转最多按一下**，不会连点。
     */
    private const val KEY_UNLOCK_ARMED = "unlock_armed"

    /** 串行化「取走授权」，避免同一瞬间两个事件都以为自己拿到了许可。 */
    private val armLock = Any()

    /**
     * 时间窗长度：**90 秒**。
     * 够覆盖「开屏 → 开锁页面 → 自己开锁 → 开锁后广告」，又不会让服务长时间保持可点状态。
     */
    const val DEFAULT_WINDOW_MS = 90 * 1000L

    /** 内存里的动作日志，供界面显示（进程内共享）。 */
    private val log = ArrayDeque<String>()
    private const val LOG_MAX = 40

    fun prefs(ctx: Context): SharedPreferences =
        ctx.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    /** 开始一次时间窗。 */
    fun start(ctx: Context, windowMs: Long = DEFAULT_WINDOW_MS) {
        prefs(ctx).edit()
            .putLong(KEY_UNTIL_ELAPSED, SystemClock.elapsedRealtime() + windowMs)
            .putLong(KEY_UNTIL_WALL, System.currentTimeMillis() + windowMs)
            .putLong(KEY_FIRED, System.currentTimeMillis())
            .apply()
    }

    fun stop(ctx: Context) {
        prefs(ctx).edit()
            .putLong(KEY_UNTIL_ELAPSED, 0L)
            .putLong(KEY_UNTIL_WALL, 0L)
            .putBoolean(KEY_UNLOCK_ARMED, false)   // 窗关了就不该再按
            .apply()
    }

    // ------------------------------------------------------------------
    // 「代按一次开锁」的授权
    // ------------------------------------------------------------------

    /** 置位/撤销授权。只有 [MainActivity.tryFireUnlock] 会调用。 */
    fun armUnlock(ctx: Context, armed: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_UNLOCK_ARMED, armed).apply()
    }

    fun isUnlockArmed(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_UNLOCK_ARMED, false)

    /**
     * **原子地取走授权**：返回 true 的那一次才允许真的按下。
     *
     * 必须原子，因为无障碍事件可能连着来；用 commit() 而不是 apply()，否则下一次
     * 读到的还是旧值，会按两下。
     */
    fun consumeUnlockArm(ctx: Context): Boolean = synchronized(armLock) {
        val p = prefs(ctx)
        if (!p.getBoolean(KEY_UNLOCK_ARMED, false)) return false
        p.edit().putBoolean(KEY_UNLOCK_ARMED, false).commit()
        true
    }

    fun isActive(ctx: Context): Boolean {
        val p = prefs(ctx)
        val untilElapsed = p.getLong(KEY_UNTIL_ELAPSED, 0L)
        if (untilElapsed <= 0L) return false
        if (SystemClock.elapsedRealtime() > untilElapsed) {
            // 到点自动失效
            p.edit().putLong(KEY_UNTIL_ELAPSED, 0L).putLong(KEY_UNTIL_WALL, 0L).apply()
            return false
        }
        return true
    }

    /** 剩余毫秒；0 表示当前不在时间窗内。 */
    fun remainingMs(ctx: Context): Long {
        val until = prefs(ctx).getLong(KEY_UNTIL_ELAPSED, 0L)
        if (until <= 0L) return 0L
        val left = until - SystemClock.elapsedRealtime()
        return if (left > 0L) left else 0L
    }

    /**
     * 界面日志的时间戳（`HH:mm:ss`）。
     *
     * `SimpleDateFormat` 不是线程安全的，而 [addLog] 会被 UI 线程与无障碍线程同时调用，
     * 所以按线程各持一份。
     */
    private val stampFmt = ThreadLocal.withInitial { SimpleDateFormat("HH:mm:ss", Locale.US) }

    /**
     * 记一条动作。
     *
     * 除了界面上的内存日志，**同时打到 logcat**（tag `UnlockAssistant`）：
     * 排查时经常是 App 已经跳走了、界面日志看不到，只能靠 logcat
     * （例如「到底是拦下了还是照旧抛了 deep link」这种判定）。
     * 内存日志跨进程重启会丢，logcat 不会。
     *
     * 时间戳**只加在界面日志上**：logcat 自己每行都带时间，再加一遍只会更难读。
     */
    fun addLog(msg: String) {
        Log.i(TAG, msg)
        val stamp = stampFmt.get()?.format(Date()) ?: ""
        synchronized(log) {
            log.addLast(if (stamp.isEmpty()) msg else "$stamp  $msg")
            while (log.size > LOG_MAX) log.removeFirst()
        }
    }

    fun snapshot(): List<String> = synchronized(log) { log.toList() }
}
