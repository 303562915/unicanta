package io.github.a303562915.unicanta

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import io.github.samolego.canta.ui.CantaApp

class MainActivity : FragmentActivity() {

    private val app: CantaApplication
        get() = application as CantaApplication

    override fun onCreate(savedInstanceState: Bundle?) {
        // 所有版本都显式开，不再按 SDK 分叉。
        //
        // 原来只在 Android 15 以下调用，15+ 交给系统强制；实测在**三键导航**（经典导航键）下
        // 有的 ROM 并不铺满窗口：底部留一条纯黑，内容被顶上去——Bug 反馈群里红米 K80 Pro 那位
        // 说的「这里不是全屏」就是这个，他切到全面屏手势就正常了。`enableEdgeToEdge()` 自己按
        // 版本处理差异、重复调用无副作用，明确开一次比赌系统默认行为可靠。
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        app.platform.attachActivity(this)
        // 冷启动：这一份 intent 可能就是点通知带进来的，先把它交出去，界面起来之后自己导航。
        app.platform.takeChatIntent(intent)
        // 也可能是点了群邀请链接（`unicanta://group/<code>`）进来的，同一个入口一起交。
        app.platform.takeInviteIntent(intent)

        setContent {
            CantaApp(
                deps = app.dependencies,
                closeApp = { finishAndRemoveTask() },
                crashReports = app.crashReports,
            )
        }
    }

    /**
     * 已经在跑的时候点通知：`singleTop` 让系统把 intent 送到这里，而不是再开一个 Activity。
     *
     * `setIntent` 是必须的：`onCreate` 之后 `getIntent()` 返回的还是最初那一个，不换掉的话
     * 从这一页再点一次通知会读到旧 extra（已经被摘掉了），导航就不会发生。
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        app.platform.takeChatIntent(intent)
        // 已经在跑的时候点邀请链接走这里；data 在平台那一侧会被摘掉，重复进不来。
        app.platform.takeInviteIntent(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        app.platform.detachActivity(this)
    }
}
