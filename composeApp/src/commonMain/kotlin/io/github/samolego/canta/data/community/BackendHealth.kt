package io.github.samolego.canta.data.community

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 社区后端现在通不通，只回答这一个事实。
 *
 * 主界面以前是拿 `isVpnActive()` 定时硬判：没开 VPN 就弹"检查科学上网"。那是在猜原因——
 * 没开 VPN 不等于连不上（分流规则、公司网络、桌面版），开着 VPN 也不等于连得上（节点挂了、
 * 订阅过期、DNS 污染）。猜错的代价是每次启动都打扰一遍已经把应用用得好好的用户。
 *
 * 但"某一次请求没答上来"也不等于"链路断了"：冷启动时网络栈和代理还没起来、切页面时的一次
 * 超时、后台被系统暂停的那一拍，都会让单个请求失败。所以这里分成两级：
 *
 * - [suspicion]：**可疑**。任何一次请求没答上来只记到这里，界面什么都不显示。它的唯一作用
 *   是让主界面立刻去做一次专门的确认探测。
 * - [networkTrouble]：**确认断了**。只有主界面的确认探测连着两次都没答上来，才由
 *   [confirmUnreachable] 置位，提示这时才出现。
 *
 * 反方向只要一个信号：任何一次请求答了话——哪怕后端拒绝——就证明链路通，
 * [reportReachable] 把可疑与提示一起清掉。
 */
object BackendHealth {

    private val _networkTrouble = MutableStateFlow(false)

    /** true 表示确认探测连着两次没答上来；false 表示后端答过话（或还没有探测过）。 */
    val networkTrouble: StateFlow<Boolean> = _networkTrouble.asStateFlow()

    private val _suspicion = MutableStateFlow(false)

    /** true 表示最近一次请求没答上来，还没经过确认探测；界面不读它。 */
    val suspicion: StateFlow<Boolean> = _suspicion.asStateFlow()

    /** 某次请求没出去、被挡住、超时：只登记为可疑，等确认探测说了算。 */
    fun reportUnreachable() {
        _suspicion.value = true
    }

    /** 后端答了话：成功、或者明确拒绝，两种都证明这台手机能到后端。 */
    fun reportReachable() {
        _suspicion.value = false
        _networkTrouble.value = false
    }

    /** 确认探测连着两次都没答上来：到这一步才算链路真的断了，提示可以出现。 */
    fun confirmUnreachable() {
        _networkTrouble.value = true
    }
}
