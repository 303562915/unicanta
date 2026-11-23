package io.github.a303562915.unicanta

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import io.github.samolego.canta.ui.CantaApp

class MainActivity : FragmentActivity() {

    private val app: CantaApplication
        get() = application as CantaApplication

    override fun onCreate(savedInstanceState: Bundle?) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            enableEdgeToEdge()
        }
        super.onCreate(savedInstanceState)

        app.platform.attachActivity(this)
        // 冷启动：这一份 intent 可能就是点通知带进来的，先把它交出去，界面起来之后自己导航。
        app.platform.takeChatIntent(intent)

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
    }

    override fun onDestroy() {
        super.onDestroy()
        app.platform.detachActivity(this)
    }
}
