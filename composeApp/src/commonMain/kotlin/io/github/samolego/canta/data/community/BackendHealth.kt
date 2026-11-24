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
 * 现在反过来：页面照常预加载自己的数据，任何一次请求**根本没答上来**（没有路由、被挡、超时）
 * 才置 [networkTrouble] = true；任何一次请求答了话——哪怕后端拒绝——就证明链路通，
 * 立刻置回 false。提示由此驱动，而不是由"有没有看到 VPN 网卡"驱动。
 *
 * 这里只有事实，没有节流：写方是各个仓储的失败分支，收敛点就那么几处；读方是主界面一个
 * `collectAsStateWithLifecycle`，StateFlow 自己去重，同一个值重复写入不会引发重组。
 */
object BackendHealth {

    private val _networkTrouble = MutableStateFlow(false)

    /** true 表示最近一次请求没答上来；false 表示后端答过话（或还没有请求过）。 */
    val networkTrouble: StateFlow<Boolean> = _networkTrouble.asStateFlow()

    /** 请求没出去、被挡住、超时：链路这一侧的问题。 */
    fun reportUnreachable() {
        _networkTrouble.value = true
    }

    /** 后端答了话：成功、或者明确拒绝，两种都证明这台手机能到后端。 */
    fun reportReachable() {
        _networkTrouble.value = false
    }
}
