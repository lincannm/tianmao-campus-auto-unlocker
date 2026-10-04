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
 * 弹窗清理服务（3.1 起多一件事：在授权下代按一次开锁）。
 *
 * 职责边界（刻意收窄）：
 *  1. 只在「自动跳过弹窗」的时间窗（90 秒，见 [Session]）内动作，窗一过就完全静默；
 *  2. 只在前台窗口属于白名单包时动作；
 *  3. 天猫校园里只点「跳过 / 关闭 / 暂不」类控件；
 *     MIUI 的「启动应用」唤醒确认框**单独一套规则**，且必须先验明弹窗身份才点「允许」。
 *
 * ## 代按开锁（3.1，用户要求）
 *
 * 只有这次跳转**开着**对应开关才置位授权标志（设置里两个开关：打开 App 时默认关、
 * 点小部件后默认开，见 [AppState] / [MainActivity.tryFireUnlock]）。授权被
 * [Session.consumeUnlockArm] 原子取走，所以**一次跳转最多按一下**；
 * 没授权时这里一个字都不会点。命中判定用精确文本「点击开锁」，点击走
 * [clickSelfOrAncestor]，**绝不按坐标盲点**。
 */
class UnlockAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "UnlockAssistant"
        private const val SCAN_THROTTLE_MS = 250L
        private const val MAX_NODES = 4000
        private const val CLICK_COOLDOWN_MS = 900L
        private const val MAX_CLIMB = 6

        /**
         * 开锁控件的轮询：门锁页是 H5，WebView 的虚拟节点树**不是一开始就在**无障碍树里
         * （实测：页面已渲染完，第一次 dump 整个 WebView 没有任何子节点；约 40 秒后第二次
         * dump 才出现 69 个节点，其中才有「点击开锁」）。所以授权期内主动轮询，最多 10 秒。
         */
        private const val UNLOCK_POLL_MS = 10_000L
        private const val UNLOCK_POLL_INTERVAL_MS = 400L

        /** 「看到开锁控件但没授权」这条日志的节流（同一页面别刷屏）。 */
        private const val UNLOCK_SEEN_LOG_MS = 10_000L

        const val TMALL_PKG = "com.tmall.campus.and"
        const val MIUI_SECURITY_PKG = "com.miui.securitycenter"

        /**
         * 走通用「跳过 / 关闭 / 暂不」规则的包。
         *
         * 3.0 起**只有天猫校园**：以前还包含我们自己的包，那是为了点掉 App 内置的
         * 「自检：假广告」弹窗（验证「事件 → 遍历 → 命中规则 → 点击」整条链路）。
         * 用户要求去掉那个自检按钮，所以连这条白名单一起撤掉 —— 服务现在没有理由
         * 去点本App 自己的界面（最小权限）。
         * 代价：规则链路只能靠天猫校园真投广告时验证（见 docs/10-deliverable-and-device.md §2 的验收矩阵）。
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

    override fun onServiceConnected() {
        super.onServiceConnected()
        running = true
        // XML 里已经配了一份，这里再钉一遍，避免不同 ROM 忽略 XML 字段。
        serviceInfo?.apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                    AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                    AccessibilityEvent.TYPE_WINDOWS_CHANGED
            // 声明成「读屏类」服务（含 SPOKEN / BRAILLE 等全部反馈类型），**这不是随手写的**：
            // HyperOS 的「最近任务划掉卡片」= force-stop，而它只跳过看起来像读屏的无障碍服务。
            // 实测（同一手势、同一台机器，见 work/v3/swipe_kill_test.ps1）：
            //   feedbackGeneric  → 3/3 被 `ProcessSceneCleaner: SwipeUpClean: force-stop` 强停，授权被清；
            //   feedbackAllMask  → 3/3 未被强停，pid 与授权都保住（GKD 也是这么声明的，它同样不被杀）。
            feedbackType = AccessibilityServiceInfo.FEEDBACK_ALL_MASK
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

        // 3) 节流
        val now = SystemClock.uptimeMillis()
        if (now - lastScanAt < SCAN_THROTTLE_MS) return
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
                // 一次事件只做一件事：先清弹窗；没弹窗可清时才轮到代按开锁。
                if (!scanAndClick(root, now)) tryUnlock(root, now)
                if (Session.isUnlockArmed(this)) startUnlockPolling(now)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "scan failed: ${t.message}")
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(unlockPoll)
        running = false
        super.onDestroy()
    }

    // ------------------------------------------------------------------
    // 代按开锁（3.1）
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
        // 没授权时也把「看到开锁控件」记一笔：插线验证时靠它确认识别对不对
        // （这一步只读不点，见 docs/20-core-mechanisms-and-invariants.md §9）。
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
     *
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
            // 若按按钮文案分别计时，一次事件里会把两个都点掉（实测发生过）。
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
