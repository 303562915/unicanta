package io.github.samolego.canta.core

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/**
 * 通知栏上那一条「有人给你发了消息」。
 *
 * 这一层的边界要说清：它只在**进程还活着**的时候有用。消息是界面里那个后台轮询问出来的
 * （见 `CantaApp` 里的未读轮询），轮询本身靠的是协程，而协程活在进程里——App 被系统回收之后
 * 没有任何东西在跑，也就没有人会在那一刻调用这里。真正的后台推送要 FCM 或一条常驻长连接，
 * 这一轮不做，所以这里也不假装做了：进程死了就是没有通知，直到人再次打开 App。
 *
 * 通知的 id 直接用会话号，于是同一会话的新消息覆盖上一条（一个人连发三条是「3 条」而不是三格
 * 通知），不同会话各占一格——这就是去重，不需要另记一份"弹过哪些"的账。
 */
internal class ChatNotifier(private val context: Context) {

    internal companion object {
        /**
         * 通知里带的会话号。入口（`MainActivity`）把它读出来交给 [AndroidCantaPlatform]，
         * 界面据此直接打开那一页。
         *
         * 放在 extra 里而不是 intent 的 data URI 上：启动 intent 是从 LAUNCHER 那条复用的，
         * 多挂一条 data 会把系统那侧的匹配改复杂，而这里要的只是"从哪条通知来的"。
         */
        const val EXTRA_CONVERSATION_ID = "io.github.samolego.canta.extra.CHAT_CONVERSATION"

        /** 一个渠道就够：以后要分时段免打扰是在设置里筛，不是再开一个渠道。 */
        private const val CHANNEL_ID = "chat"

        /** 渠道名会出现在系统的通知设置里，中文跟这批页面一致（不加 `Res.string` 键）。 */
        private const val CHANNEL_DESCRIPTION = "有人给你发消息时提醒"
    }

    private val manager: NotificationManager
        get() = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    /**
     * 通道要在第一次通知之前建好。minSdk 是 28，`NotificationChannel` 从 26 就有，所以不必再
     * 套一层版本判断。建过就返回，改名字也不会覆盖用户已经调过的设置。
     */
    private fun ensureChannel() {
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "消息",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = CHANNEL_DESCRIPTION
            setShowBadge(true)
        }
        manager.createNotificationChannel(channel)
    }

    /**
     * 弹一条，或者把同一会话的那一条换成新的。
     *
     * [unread] 只用来在标题后面报个数；[body] 是最后一条消息的正文，调用方已经把图片路径这类
     * 不是话的东西换掉了。
     */
    @SuppressLint("MissingPermission")
    fun notify(conversationId: Long, title: String, body: String, unread: Int) {
        ensureChannel()
        val intent = launchIntent(conversationId) ?: return
        val pending = PendingIntent.getActivity(
            context,
            // 每个会话自己的 requestCode：不同会话的 PendingIntent 才不会被当成同一个。
            conversationId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            // 系统资源里的"聊天气泡"剪影：不必为通知栏再加一个 drawable，它在各家 ROM 上都是
            // 通知小图标该有的白描边样子。彩色 launcher 图标当 smallIcon 会显示成一块白。
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle(if (unread > 1) "$title（$unread）" else title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pending)
            // 点开就收掉：人已经在那条会话里了，通知栏再留一条只会让人再点一次同样的东西。
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setNumber(unread)
            .build()
        // 没有 POST_NOTIFICATIONS 时这里静默失败而不是抛：权限是开关那边的事，轮询不该因为它崩。
        NotificationManagerCompat.from(context).notify(conversationId.toInt(), notification)
    }

    /** 通知栏上关于聊天的全收掉；回到前台时用。 */
    fun clearAll() {
        runCatching { NotificationManagerCompat.from(context).cancelAll() }
    }

    /**
     * 通知权限现在有没有。Android 13 起是运行时权限，更早的版本装上就有。
     *
     * 单独问这一个而不是笼统问"通知是否可用"：系统设置里被关掉整个 app 的通知，这里仍然回答
     * true，那种情况下 `notify` 会被系统丢掉——这是系统的事，不去替它判断。
     */
    fun canPost(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED

    /**
     * 点通知要打开的那个入口 intent，带上是哪个会话。
     *
     * 用 `getLaunchIntentForPackage` 而不是写死 `MainActivity::class.java`：那一个类在
     * `androidApp` 模块里，本模块（`composeApp`）看不见它——依赖方向是 app 依赖这里，反过来不行。
     * 复用 LAUNCHER 那条 intent 正好也回答了"从哪进来"这个问题。
     *
     * 已经在跑的时候复用同一个实例：`SINGLE_TOP` 让它走 `onNewIntent`，冷启动才新建。
     * 没有 `CLEAR_TOP` 的话，点三条不同会话的通知会叠出三层页面，返回要按三次。
     */
    private fun launchIntent(conversationId: Long): Intent? {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return null
        return launch.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_CONVERSATION_ID, conversationId)
        }
    }

    /** [intent] 里带的会话号；不是从通知点进来的回答 0。 */
    fun conversationOf(intent: Intent?): Long {
        val value = intent?.getLongExtra(EXTRA_CONVERSATION_ID, 0L) ?: 0L
        return if (value > 0L) value else 0L
    }
}
