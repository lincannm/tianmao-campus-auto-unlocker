package com.fucktmall.unlockassistant

/**
 * 弹窗识别规则。
 *
 * 事实来源：
 *  - 天猫校园 5.7.2 的 dex 字符串与资源表（work/resources.txt、work/dex 全量字符串扫描）
 *  - AnyThink(Taku) 开屏广告 SDK 的控件文案
 *  - 真机实测到的 MIUI「启动应用」唤醒确认弹窗
 *
 * 实测到的相关文案：
 *   跳过 / 跳过｜5 / 跳过广告 %1$d / 跳过 %1$d / %1$d 跳过
 *   关闭广告 / 轻点关闭 / 点击关闭按钮 / 广告将会在_SEC_秒后关闭
 *   暂不安装 / 去升级 / 继续升级 / 升级提醒 / 检查更新
 *   您有新版本可以更新，请问需要更新吗 / 已经给您准备了新版本的{app_name}，是否安装
 *   取消升级，将无法使用{app_name}
 *
 * 设计原则：**只点「拒绝 / 跳过 / 关闭」类控件**。
 * 任何一个「接受 / 前往 / 下载 / 安装」类控件都在 DENY 里，永不点击。
 * 开锁控件（「点击开锁」）**不在这里**：它走 [UNLOCK_LABELS] + 服务里的授权标志
 * （3.1 用户要求，见 `../AGENTS.md` §3.1 与 `docs/20-core-mechanisms-and-invariants.md` §9）——
 * 没授权时一个字都不会点；授权被 `Session.consumeUnlockArm()` 原子取走，一次跳转最多按一下。
 */
object PopupRules {

    /**
     * 允许点击的文本 / 无障碍描述。
     * 用正则，因为广告跳过按钮常带倒计时数字（"跳过 3"、"5 跳过"）。
     */
    val ALLOW: List<Regex> = listOf(
        // 开屏 / 插屏广告的跳过
        Regex("^跳\\s*过(广告)?\\s*[｜|]?\\s*\\d*\\s*秒?$"),
        Regex("^\\d+\\s*秒?\\s*跳\\s*过$"),
        Regex("^跳\\s*过\\s*广\\s*告$"),
        // 关闭类
        Regex("^关\\s*闭$"),
        Regex("^关\\s*闭\\s*广\\s*告$"),
        Regex("^轻\\s*点\\s*关\\s*闭$"),
        Regex("^点\\s*击\\s*关\\s*闭\\s*按\\s*钮$"),
        Regex("^\\s*[×xX✕✖]\\s*$"),
        // 更新弹窗的「不要更新」一侧
        Regex("^暂\\s*不\\s*安\\s*装$"),
        Regex("^暂\\s*不\\s*更\\s*新$"),
        Regex("^暂\\s*不\\s*升\\s*级$"),
        Regex("^以\\s*后\\s*再\\s*说$"),
        Regex("^稍\\s*后\\s*再\\s*说$"),
        Regex("^稍\\s*后$"),
        Regex("^取\\s*消\\s*升\\s*级$"),
        Regex("^残\\s*忍\\s*拒\\s*绝$"),
        Regex("^继\\s*续\\s*使\\s*用$"),
        Regex("^我\\s*知\\s*道\\s*了$"),
        Regex("^下\\s*次\\s*再\\s*说$"),
        Regex("^不\\s*再\\s*提\\s*示$"),
        Regex("^不\\s*再\\s*提\\s*醒$")
    )

    /**
     * 绝对不点的文本。误点「去升级」会跳应用市场，误点「点击开锁」会真的开门。
     * 注意：这条是**天猫校园等普通 App 内**的底线；
     * MIUI「启动应用」确认框的「允许」由 [MIUI_CONFIRM_ALLOW] 单独放行，不从这里走。
     */
    val DENY: List<Regex> = listOf(
        Regex("去\\s*升\\s*级"),
        Regex("继\\s*续\\s*升\\s*级"),
        Regex("立\\s*即\\s*更\\s*新"),
        Regex("立\\s*即\\s*升\\s*级"),
        Regex("立\\s*即\\s*安\\s*装"),
        Regex("立\\s*即\\s*下\\s*载"),
        Regex("下\\s*载\\s*并\\s*安\\s*装"),
        Regex("前\\s*往\\s*应\\s*用\\s*市\\s*场"),
        Regex("点\\s*击\\s*开\\s*锁"),
        Regex("开\\s*锁"),
        Regex("确\\s*认"),
        Regex("同\\s*意"),
        Regex("允\\s*许"),
        Regex("立\\s*即\\s*参\\s*与"),
        Regex("立\\s*即\\s*体\\s*验"),
        Regex("立\\s*即\\s*查\\s*看"),
        Regex("立\\s*即\\s*领\\s*取"),
        Regex("立\\s*即\\s*购\\s*买")
    )

    // ------------------------------------------------------------------
    // MIUI「启动应用」唤醒确认弹窗（com.miui.securitycenter）
    //
    //真机实测形态：
    //   标题：启动应用
    //   正文：<发起跳转的App名> 想要打开 天猫校园，是否允许？
    //         （1.0–2.2 实测原文是「一键开门 想要打开 天猫校园，是否允许？」；
    //           3.0 应用名改成「开锁」后这句正文也会跟着变 —— 那是系统用应用名生成的，
    //           判定标记词是「启动应用 / 想要打开」，与本App 叫什么无关）
    //   按钮：本次允许 / 始终允许
    //
    // 为什么单独一套规则：这个弹窗必须点「允许」，而「允许」在 DENY 里。
    // 做法是**先验证弹窗身份再放行**：只有窗口里同时出现下面的标记词，
    // 才认为它是启动确认框，才允许点允许类按钮。
    // 这样普通权限弹窗（允许/拒绝）不会被误点。
    // ------------------------------------------------------------------

    /** 判定「这就是 MIUI 启动确认框」的标记词（命中任意一个即可）。 */
    val MIUI_CONFIRM_MARKERS: List<String> = listOf(
        "启动应用",
        "想要打开"
    )

    // ------------------------------------------------------------------
    // 门锁页的「点击开锁」控件（3.1 新增：用户要求小部件点开后可以代按一次）
    //
    // **实测**（天猫校园 5.7.2 / HyperOS OS3.0 / 1280×2772，证据 work/v7/dump_doorlock2.xml）：
    //   文本节点  TextView  text="点击开锁"   bounds [555,1332][724,1407]
    //   可点祖先  View      clickable=true    bounds [425,1046][854,1478]  （整颗圆形按钮）
    // 所以判定只认**精确文本**，点击仍旧走 clickSelfOrAncestor（自下而上找可点祖先），
    // 绝不按坐标盲点 —— 盲点会误触同一页右上角的「客服」。
    //
    // 注意：它**不在** [ALLOW] 里，也不靠 [DENY] 挡住。允许与否由「授权标志」决定
    // （Session.armUnlock / consumeUnlockArm）：没授权时这里一个字都不会点。
    // ------------------------------------------------------------------

    val UNLOCK_LABELS: List<Regex> = listOf(
        Regex("^点\\s*击\\s*开\\s*锁$")
    )

    /** 这个标签是不是门锁页的开锁控件。 */
    fun isUnlockControl(labelRaw: CharSequence?): Boolean {
        val label = normalized(labelRaw)
        if (label.isEmpty() || label.length > MAX_LABEL_LEN) return false
        return UNLOCK_LABELS.any { it.containsMatchIn(label) }
    }

    /** MIUI 启动确认框里允许点击的按钮。优先「始终允许」，一次授权以后不再问。 */
    val MIUI_CONFIRM_ALLOW: List<Regex> = listOf(
        Regex("^始\\s*终\\s*允\\s*许$"),
        Regex("^本\\s*次\\s*允\\s*许$"),
        Regex("^允\\s*许$")
    )

    /**
     * 误报防护：这些词里含「跳过」，但不是广告跳过按钮。
     */
    private val FALSE_POSITIVE_SUBSTRINGS = listOf(
        "跳过绑定"
    )

    /** 文本长度上限：广告跳过按钮一定很短，超过这个长度的多半是正文。 */
    private const val MAX_LABEL_LEN = 12

    fun normalized(s: CharSequence?): String =
        s?.toString()?.trim()?.replace('\u00a0', ' ') ?: ""

    /** 这个标签是否应该被点击（天猫校园等普通 App 的规则）。 */
    fun shouldClick(labelRaw: CharSequence?): Boolean {
        val label = normalized(labelRaw)
        if (label.isEmpty() || label.length > MAX_LABEL_LEN) return false
        if (FALSE_POSITIVE_SUBSTRINGS.any { label.contains(it) }) return false
        if (DENY.any { it.containsMatchIn(label) }) return false
        return ALLOW.any { it.containsMatchIn(label) }
    }

    /** 便于日志显示：命中的是哪条规则。 */
    fun matchedRule(labelRaw: CharSequence?): String? {
        val label = normalized(labelRaw)
        if (!shouldClick(label)) return null
        return ALLOW.firstOrNull { it.containsMatchIn(label) }?.pattern
    }

    /** MIUI 启动确认框里，这个按钮该不该点。 */
    fun shouldClickMiuiAllow(labelRaw: CharSequence?): Boolean {
        val label = normalized(labelRaw)
        if (label.isEmpty() || label.length > MAX_LABEL_LEN) return false
        return MIUI_CONFIRM_ALLOW.any { it.containsMatchIn(label) }
    }
}
