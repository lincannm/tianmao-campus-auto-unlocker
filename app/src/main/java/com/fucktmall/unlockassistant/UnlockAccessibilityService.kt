package com.fucktmall.unlockassistant

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.TextUtils
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * 弹窗清理服务，外加在授权下代按一次开锁。
 *
 * 职责边界（刻意收窄）：
 *  1. 只在「自动跳过弹窗」的时间窗（90 秒，见 [Session]）内动作，窗一过就完全静默；
 *  2. 只在前台窗口属于白名单包时动作；
 *  3. 天猫校园里只点「跳过 / 关闭 / 暂不」类控件；
 *     全屏广告窗口（广告 SDK 的 Activity）里没有可点文案时按返回键退回上一页；
 *     MIUI 的「启动应用」唤醒确认框**单独一套规则**，且必须先验明弹窗身份才点「允许」。
 *
 * 代按开锁：只有这次跳转**开着**对应入口开关才置位授权标志
 * （见 [AppState] / [MainActivity.tryFireUnlock]）。授权被 [Session.consumeUnlockArm]
 * 原子取走，所以**一次跳转最多按一下**；没授权时这里一个字都不会点。命中判定用精确文本
 * 「点击开锁」，点击走 [clickSelfOrAncestor]，**绝不按坐标盲点**。
 */
class UnlockAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "UnlockAssistant"
        private const val SCAN_THROTTLE_MS = 250L
        private const val MAX_NODES = 4000
        private const val CLICK_COOLDOWN_MS = 900L
        private const val MAX_CLIMB = 6

        /**
         * 开锁控件的轮询：门锁页是 H5，WebView 的虚拟节点树不是一开始就在无障碍树里
         * （页面渲染完的第一时间整个 WebView 可能没有任何子节点）。所以授权期内主动轮询。
         */
        private const val UNLOCK_POLL_MS = 10_000L
        private const val UNLOCK_POLL_INTERVAL_MS = 400L

        /** 「看到开锁控件但没授权」这条日志的节流（同一页面别刷屏）。 */
        private const val UNLOCK_SEEN_LOG_MS = 10_000L

        /** 广告窗口里按返回键的间隔：按一次多半就退回去了，留出看"确认退出"框的时间。 */
        private const val AD_BACK_COOLDOWN_MS = 1_200L

        /** 同一个广告窗口最多按几次返回键（防"按了没用"时无限按）。 */
        private const val AD_BACK_MAX = 3

        const val TMALL_PKG = "com.tmall.campus.and"
        const val MIUI_SECURITY_PKG = "com.miui.securitycenter"

        /**
         * 走通用「跳过 / 关闭 / 暂不」规则的包：**只有天猫校园**。
         * 服务没有理由去点本 App 自己的界面（最小权限）。
         */
        private val GENERIC_PACKAGES = setOf(TMALL_PKG)

        @Volatile
        var running: Boolean = false
            private set

        @Volatile
        var clickCount: Int = 0
            private set
    }

    private var lastScanAt = 0L
    private val lastClickAt = HashMap<String, Long>()
    private val handler = Handler(Looper.getMainLooper())
    private var unlockPollUntil = 0L
    private var polling = false

    /** 当前这个广告窗口的 Activity 类名；`null` = 不在广告窗口里。 */
    private var adWindowClass: String? = null
    private var adBackAt = 0L
    private var adBackCount = 0

    override fun onServiceConnected() {
        super.onServiceConnected()
        running = true
        // 服务这一侧的动作日志也落到 files/session.log（有些 ROM 会把第三方 App 的 logcat 行滤掉）。
        Session.attachLogFile(this)
        // XML 里已经配了一份，这里再钉一遍，避免不同 ROM 忽略 XML 字段。
        serviceInfo?.apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                    AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                    AccessibilityEvent.TYPE_WINDOWS_CHANGED
            // ⚠️ 当前是 FEEDBACK_GENERIC，**不要改成 FEEDBACK_ALL_MASK**：读屏类声明只在 HyperOS 上
            // 躲得开「划掉卡片 / ✕一键清理」那种强停，而本工作区要复现的正是别的机型那种失效路径
            // （服务掉进 `Crashed services` 后由 ensureConnected 重绑回来）。XML 里那一处同值，要改一起改。
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = flags or
                    AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            notificationTimeout = 100
        }
        Session.addLog("无障碍服务已连接")
        Log.i(TAG, "service connected")
    }

    override fun onInterrupt() {
        // 无需处理
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        running = false
        Session.addLog("无障碍服务已断开")
        Log.i(TAG, "service unbound")
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        // 1) 时间窗（只在「自动跳过弹窗」的 90 秒内动作）
        if (!Session.isActive(this)) return

        // 2) 包名
        val pkg = event.packageName?.toString() ?: return
        val isMiui = pkg == MIUI_SECURITY_PKG
        if (!isMiui && pkg !in GENERIC_PACKAGES) return

        val now = SystemClock.uptimeMillis()

        // 广告窗口的记账放在节流之前：窗口切换事件只来一次，正好撞进节流窗口就丢了。
        val isAdEvent = !isMiui && trackAdWindow(event)

        // 3) 节流（广告窗口切换不受它限制：丢了这一次就没人关那只广告了）
        if (!isAdEvent && now - lastScanAt < SCAN_THROTTLE_MS) return
        lastScanAt = now

        val root = try {
            rootInActiveWindow
        } catch (t: Throwable) {
            Log.w(TAG, "rootInActiveWindow failed: ${t.message}")
            null
        } ?: return

        try {
            if (isMiui) {
                handleMiuiStartConfirm(root, now)
            } else {
                // 一次事件只做一件事：先按文案清弹窗；广告窗口里没文案可点就按返回键；
                // 都不是才轮到代按开锁。
                if (!scanAndClick(root, now)) {
                    if (isAdEvent) closeAdWindow(now) else tryUnlock(root, now)
                }
                if (Session.isUnlockArmed(this)) startUnlockPolling(now)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "scan failed: ${t.message}")
        }
    }

    // ------------------------------------------------------------------
    // 全屏广告窗口（点开锁之后弹出来的那种）
    // ------------------------------------------------------------------

    /**
     * 记住这件事是不是「广告窗口刚切到前台」，返回值就是判据。
     *
     * 判据：`TYPE_WINDOW_STATE_CHANGED` 事件的类名落在广告 SDK 的包下（见
     * [PopupRules.AD_ACTIVITY_PREFIXES]）；切回任何普通页面就把状态清掉。
     *
     * **只在窗口切换事件上动手**（不在后续的 content-changed 上补按）：广告退回去时
     * 底下的门锁页会发一次正常的窗口切换，状态随即清掉；若拿 content-changed 补按，
     * 一旦那一次切换没来，返回键就会按到门锁页上。
     */
    private fun trackAdWindow(event: AccessibilityEvent): Boolean {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return false
        val cls = event.className?.toString().orEmpty()
        if (!PopupRules.isAdWindow(cls)) {
            adWindowClass = null
            return false
        }
        if (cls != adWindowClass) {
            adBackAt = 0L
            adBackCount = 0
        }
        adWindowClass = cls
        return true
    }

    /**
     * 用返回键关掉广告窗口。
     *
     * 为什么是返回键：这类窗口的关闭按钮常是**没有文字、没有无障碍描述**的纯图标
     * （实测 `TTFullScreenVideoActivity` 的 × 就是一只 ImageView），文案规则匹配不到；
     * 而广告 Activity 都压在门锁页之上，返回键只会退回门锁页 —— 既不盲点坐标，
     * 也不会把广告主的落地页拉起来。有的 SDK 会先弹「放弃奖励离开 / 继续观看」的确认框，
     * 那一支由 [PopupRules.ALLOW] 里的两条文案点掉。
     */
    private fun closeAdWindow(now: Long) {
        val cls = adWindowClass ?: return
        if (adBackCount >= AD_BACK_MAX) return
        if (now - adBackAt < AD_BACK_COOLDOWN_MS) return
        adBackAt = now
        adBackCount++
        val ok = try {
            performGlobalAction(GLOBAL_ACTION_BACK)
        } catch (t: Throwable) {
            false
        }
        val simple = cls.substringAfterLast('.')
        val msg = if (ok) {
            "关闭广告窗口：$simple（返回键）"
        } else {
            "关闭广告窗口：$simple（返回键没发出去）"
        }
        Session.addLog(msg)
        Log.i(TAG, "$msg ok=$ok")
    }

    override fun onDestroy() {
        handler.removeCallbacks(unlockPoll)
        running = false
        super.onDestroy()
    }

    // ------------------------------------------------------------------
    // 代按开锁
    // ------------------------------------------------------------------

    /**
     * 门锁页的「点击开锁」。
     *
     * 顺序是刻意的：**先找到控件，再取走授权**。反过来的话，先取走授权却找不到控件，
     * 这一次跳转的许可就白白用掉了（用户点了小部件却什么都没发生）。
     */
    private fun tryUnlock(root: AccessibilityNodeInfo, now: Long): Boolean {
        if (!Session.isUnlockArmed(this)) return false
        val target = findUnlockNode(root) ?: return false
        if (!Session.consumeUnlockArm(this)) return false

        val bounds = Rect().also { target.getBoundsInScreen(it) }
        val ok = clickSelfOrAncestor(target)
        val msg = if (ok) "代按开锁：\"点击开锁\" $bounds" else "找到开锁控件但点不动 $bounds"
        Session.addLog(msg)
        Log.i(TAG, "$msg clicked=$ok")
        return ok
    }

    private fun findUnlockNode(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        for (node in collectNodes(root)) {
            for (label in labelsOf(node)) {
                if (PopupRules.isUnlockControl(label)) return node
            }
        }
        return null
    }

    /** 授权期内轮询（理由见 [UNLOCK_POLL_MS]）；授权被取走或超时就停。 */
    private fun startUnlockPolling(now: Long) {
        unlockPollUntil = now + UNLOCK_POLL_MS
        if (polling) return
        polling = true
        handler.postDelayed(unlockPoll, UNLOCK_POLL_INTERVAL_MS)
    }

    private val unlockPoll = object : Runnable {
        override fun run() {
            val now = SystemClock.uptimeMillis()
            if (now > unlockPollUntil || !Session.isUnlockArmed(this@UnlockAccessibilityService)) {
                polling = false
                return
            }
            try {
                rootInActiveWindow?.let { tryUnlock(it, now) }
            } catch (t: Throwable) {
                Log.w(TAG, "unlock poll failed: ${t.message}")
            }
            if (Session.isUnlockArmed(this@UnlockAccessibilityService)) {
                handler.postDelayed(this, UNLOCK_POLL_INTERVAL_MS)
            } else {
                polling = false
            }
        }
    }

    /** 有界广度遍历，避免大页面（WebView）把时间耗光。 */
    private fun collectNodes(root: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
        val out = ArrayList<AccessibilityNodeInfo>(256)
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.addLast(root)
        while (queue.isNotEmpty() && out.size < MAX_NODES) {
            val node = queue.removeFirst()
            out.add(node)
            val n = node.childCount
            for (i in 0 until n) {
                val c = try {
                    node.getChild(i)
                } catch (t: Throwable) {
                    null
                }
                if (c != null) queue.addLast(c)
            }
        }
        return out
    }

    private fun labelsOf(node: AccessibilityNodeInfo): List<CharSequence> {
        val list = ArrayList<CharSequence>(2)
        val t = node.text
        if (!TextUtils.isEmpty(t)) list.add(t)
        val d = node.contentDescription
        if (!TextUtils.isEmpty(d)) list.add(d)
        return list
    }

    /** 天猫校园：通用「跳过 / 关闭 / 暂不」规则。返回这次事件有没有点掉东西。 */
    private fun scanAndClick(root: AccessibilityNodeInfo, now: Long): Boolean {
        var sawUnlock = false
        for (node in collectNodes(root)) {
            for (label in labelsOf(node)) {
                if (PopupRules.isUnlockControl(label)) sawUnlock = true
                if (!PopupRules.shouldClick(label)) continue
                val key = PopupRules.normalized(label)
                val last = lastClickAt[key] ?: 0L
                if (now - last < CLICK_COOLDOWN_MS) continue

                lastClickAt[key] = now
                if (clickSelfOrAncestor(node)) {
                    clickCount++
                    val msg = "点掉弹窗：\"$key\""
                    Session.addLog(msg)
                    Log.i(TAG, "$msg pkg=${node.packageName} rule=${PopupRules.matchedRule(label)}")
                    return true // 一次事件只处理一个，避免连环误点
                }
            }
        }
        // 没授权时也把「看到开锁控件」记一笔，用来确认识别对不对（这一步只读不点）。
        if (sawUnlock && !Session.isUnlockArmed(this)) {
            val key = "UNLOCK_SEEN"
            val last = lastClickAt[key] ?: 0L
            if (now - last > UNLOCK_SEEN_LOG_MS) {
                lastClickAt[key] = now
                val msg = "看到「点击开锁」，但这次没授权代按（入口开关关着或没开）"
                Session.addLog(msg)
                Log.i(TAG, msg)
            }
        }
        return false
    }

    /**
     * MIUI「启动应用」唤醒确认框。
     * 必须先验明身份（窗口里出现「启动应用」或「想要打开」）才点「允许」，
     * 否则普通权限弹窗会被误点。命中后优先点「始终允许」——一次授权以后不再问。
     */
    private fun handleMiuiStartConfirm(root: AccessibilityNodeInfo, now: Long) {
        val nodes = collectNodes(root)

        var isStartConfirm = false
        for (node in nodes) {
            for (label in labelsOf(node)) {
                val s = PopupRules.normalized(label)
                if (PopupRules.MIUI_CONFIRM_MARKERS.any { s.contains(it) }) {
                    isStartConfirm = true
                    break
                }
            }
            if (isStartConfirm) break
        }
        if (!isStartConfirm) return

        // 先找「始终允许」，没有再退「本次允许」。
        val allowNodes = ArrayList<AccessibilityNodeInfo>(4)
        for (node in nodes) {
            for (label in labelsOf(node)) {
                if (PopupRules.shouldClickMiuiAllow(label)) {
                    allowNodes.add(node)
                    break
                }
            }
        }
        if (allowNodes.isEmpty()) return

        val preferAlways = allowNodes.filter {
            PopupRules.normalized(it.text).replace(" ", "").contains("始终")
        }
        val ordered = if (preferAlways.isNotEmpty()) preferAlways + allowNodes else allowNodes

        for (node in ordered) {
            // 冷却键**刻意共用一个常量**：这个弹窗上「始终允许」和「本次允许」同时存在，
            // 若按按钮文案分别计时，一次事件里会把两个都点掉。
            // 共用一个键 = 同一秒内只点一次。
            val key = "MIUI_START_CONFIRM"
            val last = lastClickAt[key] ?: 0L
            if (now - last < CLICK_COOLDOWN_MS) continue
            lastClickAt[key] = now
            if (clickSelfOrAncestor(node)) {
                clickCount++
                val msg = "点了 MIUI 启动确认：\"${PopupRules.normalized(node.text)}\""
                Session.addLog(msg)
                Log.i(TAG, msg)
                return
            }
        }
    }

    /**
     * 广告的「跳过」文本节点本身通常不可点，可点的是它的某个祖先容器。
     * 自下而上找一个可点的祖先；找不到就返回 false（**绝不按坐标盲点**，
     * 盲点右上角会误触门锁页的「客服」）。
     */
    private fun clickSelfOrAncestor(node: AccessibilityNodeInfo): Boolean {
        var cur: AccessibilityNodeInfo? = node
        var depth = 0
        while (cur != null && depth <= MAX_CLIMB) {
            if (cur.isClickable && cur.isEnabled) {
                if (cur.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
            }
            cur = try {
                cur.parent
            } catch (t: Throwable) {
                null
            }
            depth++
        }
        return false
    }
}
