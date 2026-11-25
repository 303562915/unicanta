package io.github.samolego.canta.ui.update

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.samolego.canta.ui.screen.UpdateState
import io.github.samolego.canta.ui.screen.shownPercent

/**
 * 更新进行到哪一步 —— 给**顶栏**看的那一份。
 *
 * 为什么要有它：进度原来只画在「关于」页那张卡片里，人在别的页面（应用列表、消息、帖子）时什么都
 * 看不见，一次下载/安装跑了几分钟，看起来就像「我已经点了，但没反应」。顶栏是每一页都在的地方
 * （[io.github.samolego.canta.ui.fl.FlTopBar]），所以进度要能在那儿一直显示。
 *
 * 数据只有一份：[UpdateCenter] 每次改 `state` 都调 [sync]，卡片和顶栏读的是同一个状态，不会出现
 * 「卡片在动、顶栏不动」。
 */
internal object UpdateProgress {

    /** 更新现在处在哪个大阶段。只有这两段有"进度"可言，其余（已下载、交给系统安装器、失败）都不画。 */
    enum class Phase { DOWNLOADING, INSTALLING }

    /**
     * 顶栏那条进度条要画的东西。
     *
     * @param version 版本标签（`4.3.6 (dev 456)`），条上面那行字要它。
     * @param fraction 0f..1f；null 表示这一次没有可量的长度（服务器没给 `Content-Length`，或者
     *   安装正处在 `pm install` 这种自己不报进度的阶段），这种时候画不确定进度条，不编数字。
     */
    data class Item(
        val phase: Phase,
        val version: String,
        val fraction: Float?,
    )

    /** 没有更新在跑时是 null；顶栏据此决定画不画这一条。 */
    var active by mutableStateOf<Item?>(null)
        private set

    /**
     * 把 [UpdateCenter] 的状态翻成顶栏要画的那一条。
     *
     * 不认识的阶段一律当"没在跑"：`ReadyToInstall` 是等人点、`FailedWith` 是该看卡片上的原因，
     * 两种都不该在顶栏上留一条永远走不完的进度。
     */
    fun sync(state: UpdateState) {
        active = when (state) {
            is UpdateState.Downloading -> Item(
                phase = Phase.DOWNLOADING,
                version = state.version,
                fraction = if (state.progress < 0) null else state.progress.coerceIn(0, 100) / 100f,
            )

            is UpdateState.Installing -> Item(
                phase = Phase.INSTALLING,
                version = state.version,
                fraction = state.shownPercent()?.let { it.coerceIn(0, 100) / 100f },
            )

            else -> null
        }
    }
}
