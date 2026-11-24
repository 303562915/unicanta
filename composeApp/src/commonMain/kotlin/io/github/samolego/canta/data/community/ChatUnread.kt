package io.github.samolego.canta.data.community

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

/**
 * 底栏「消息」那一格上的数字：全部会话的未读数之和。
 *
 * 为什么单拎一个对象出来：这个数以前只活在 `CantaApp` 的一个 `remember` 里，靠「切页 / 每分钟
 * 一拍」触发的一次**全量会话拉取**算出来，而且十秒内不再重算（省一次几百毫秒的请求）。于是有
 * 两个看着像 bug 的表现：
 *
 * 1. 点进一个群把消息读完，底栏的数字还在。那一趟计数要么根本没跑（聊天页不在计数页的名单里），
 *    要么被那十秒的 TTL 挡住，拿到的还是读之前那份列表——人刚退出来就看见数字没变。
 * 2. 在消息页下拉刷新，列表已经清零了，底栏那个数字不动。刷新的结果只写进了消息页自己的状态，
 *    底栏那份还在等下一拍。
 *
 * 现在只留一处真值：谁手上有一份更新的会话列表（消息页刷新完、本机标已读、聊天页读完某一条），
 * 就直接把数字推到这里；`CantaApp` 只读它，不再自己存一份。拿不到新数据时它保持原样，所以不会
 * 出现「网络抖一下数字自己消失」——只会在真读到东西之后往下掉。
 *
 * 线程：写它的都是 Compose 主线程上的协程（页面里的 `LaunchedEffect` / `rememberCoroutineScope`），
 * 和 [ConversationCache] 同一套前提，所以这里不加锁。
 */
object ChatUnread {

    /** 未读总数；0 的时候底栏不画数字。 */
    var total by mutableIntStateOf(0)
        private set

    /** 直接写一个数（全量计数回来、退出登录清零时用）。负数没有意义，按 0 算。 */
    fun set(value: Int) {
        total = value.coerceAtLeast(0)
    }

    /** 手上有一份（新的）会话列表：按它重算，不必再问一次后端。 */
    fun from(conversations: List<Conversation>) {
        set(conversations.sumOf { it.unread })
    }
}
