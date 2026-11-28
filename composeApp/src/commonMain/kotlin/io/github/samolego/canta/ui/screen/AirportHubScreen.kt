package io.github.samolego.canta.ui.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import io.github.samolego.canta.core.CantaPlatform
import io.github.samolego.canta.data.community.PlanRepository
import io.github.samolego.canta.data.recommend.CommunityAirport
import io.github.samolego.canta.ui.component.ScreenTopBar
import io.github.samolego.canta.ui.fl.FlSegment
import io.github.samolego.canta.ui.fl.FlSegmentedControl
import io.github.samolego.canta.util.IdentitySetup

/**
 * 「机场」独立页：推荐 / 投票 / 众筹三格，从设置页那一行点进来（不占底栏）。
 *
 * 为什么从推荐页搬出来：推荐页原来四格（应用目录、社区投票、机场、机场投票），
 * 后两格是**另一个系统**——目录读的是社区后端的一张表，机场读的是另一张，投票门槛、详情页、
 * 评论区、订阅链接全都各有一套；再塞第三格众筹，那一页就变成四个系统挤在一条分段控件上。
 * 搬出来之后，推荐页只回答「装什么应用」，这一页只回答「用哪家机场、跟谁合买」。
 *
 * 三格共用一份 [AirportTabState]：在排行榜投的一票，切回推荐那一格立刻是新票数；
 * 从这一页点进详情页投的票，回来也是新的（详情页与这里拿的是同一个实例）。
 *
 * @param state 机场列表的跨页快照，由调用方持有（与机场详情页共用）。
 * @param onOpenAirport 点开一条机场：交给调用方导航到 [AirportDetailScreen]。
 */
@Composable
fun AirportHubScreen(
    platform: CantaPlatform,
    repository: PlanRepository?,
    author: String,
    authorId: String,
    state: AirportTabState,
    onNavigateBack: () -> Unit,
    onOpenAirport: (CommunityAirport) -> Unit = {},
    setupGate: IdentitySetup = IdentitySetup.UNSET,
) {
    /** 0 推荐 / 1 投票 / 2 众筹。存着它，从这里进详情再回来还停在原来那一格。 */
    var tab by rememberSaveable { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            Column {
                ScreenTopBar(
                    onNavigateBack = onNavigateBack,
                    title = "机场",
                )
                FlSegmentedControl(
                    segments = listOf(
                        FlSegment("airport", "推荐"),
                        FlSegment("airport-vote", "投票"),
                        FlSegment("crowd", "众筹"),
                    ),
                    selectedIndex = tab,
                    onSelect = { tab = it },
                )
            }
        },
    ) { padding ->
        when (tab) {
            // 已经过线的机场：只摆出来，点一条进详情（官网、订阅、评论都在详情里）。
            0 -> RecommendationsAirportTab(
                repository = repository,
                platform = platform,
                author = author,
                authorId = authorId,
                state = state,
                modifier = Modifier.fillMaxSize().padding(padding),
                setupGate = setupGate,
                stateKey = "airport",
                onOpenAirport = onOpenAirport,
            )

            // 候选池：还没过线的机场 + 「分享一个机场」的入口，行里直接投票。
            1 -> RecommendationsAirportTab(
                repository = repository,
                platform = platform,
                author = author,
                authorId = authorId,
                state = state,
                modifier = Modifier.fillMaxSize().padding(padding),
                setupGate = setupGate,
                board = true,
                stateKey = "airport-vote",
                onOpenAirport = onOpenAirport,
            )

            // 合买：另一条数据线（`CrowdAirport`），见 [AirportCrowdTab]。
            else -> AirportCrowdTab(
                repository = repository,
                platform = platform,
                author = author,
                authorId = authorId,
                modifier = Modifier.fillMaxSize().padding(padding),
                setupGate = setupGate,
            )
        }
    }
}
