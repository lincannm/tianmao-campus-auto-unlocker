package com.fucktmall.unlockassistant.widget

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.fucktmall.unlockassistant.R
import com.fucktmall.unlockassistant.Session

/**
 * 桌面把小部件**真的加上去**之后的回调（`AppWidgetManager.requestPinAppWidget` 的 successCallback）。
 *
 * 为什么要它：「已请求」只说明请求发出去了，桌面认不认、加没加，返回值与界面都说不清。
 * 这个广播是**唯一可靠的落地证据** —— `Session.addLog` 那行 logcat 与设置面板的日志都能查到。
 *
 * toast 只是顺手：App 若已退到后台，Android 11+ 会**直接丢掉**它（不是排队），
 * 所以不能把 toast 当成唯一反馈。
 */
class WidgetPinnedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // 桌面会把分配到的 id 塞进来；取不到不影响这行日志的意义。
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
        Session.addLog(if (id >= 0) "小组件已添加到桌面（#$id）" else "小组件已添加到桌面")
        Toast.makeText(context, R.string.wpin_added, Toast.LENGTH_SHORT).show()
    }
}
