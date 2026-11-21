package com.tuxungo.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

private data class GuidePage(val title: String, val body: String)

/**
 * Full-screen guide shown the first time the app opens after an
 * install or a version update (and re-openable from the home
 * footer): welcome, what is new, the feature tour, the model story
 * and the declarations. Seven pages, skippable.
 */
@Composable
fun OnboardingScreen(
    versionName: String,
    isUpdate: Boolean,
    whatsNew: String,
    onFinish: () -> Unit
) {
    val pages = listOf(
        GuidePage(
            if (isUpdate) "已更新到 $versionName" else "欢迎来到 TuxunGo",
            "一款免费、开源、完全离线的围棋 App，内置 KataGo 引擎。" +
                "人对人、人对 AI、AI 对 AI 都能下，棋盘 5–25 路任意宽高，让子、贴目、棋钟全都可调。" +
                "两个人用真棋盘下棋时，还能把手机当棋钟用。"
        ),
        GuidePage("本版更新", whatsNew),
        GuidePage(
            "对弈与 AI",
            "· AI 托管：黑白双方可以各用一个模型、各设一个强度，互相对打或帮你下\n" +
                "· 模拟人类落子：选点像真人棋手，强搜索兜底不放水\n" +
                "· 专注模式藏起所有提示，一色棋全靠记忆，系列赛让两个 AI 连打多局\n" +
                "· 新朋友：开局前可以「猜先」，按老规矩决定谁执黑\n" +
                "· 摆棋：主页的「摆棋（自己摆局面开局）」能自由摆一个残局或题目局面，摆好直接开局对弈\n" +
                "· 智能裁判（设置里开）：局面定型后自动数目判终，不用手动点死子；全实力 AI 大势已去时会提议认输，同意与否由你定"
        ),
        GuidePage(
            "分析与训练",
            "· AI 分析给候选着点、胜率和变化图，还会标出人类一选（蓝圈）——真人高手多半下哪里\n" +
                "· 形势判断按目数明细算账：活子、空、贴目、死子一笔笔列清，棋盘上的领地颜色越深归属越确定\n" +
                "· 对局时胜率在后台悄悄算好，终局复盘秒开，恶手妙手自动标出；复盘还能看任意一手当时的领地\n" +
                "· 猜一选（还能用你自己的棋谱出题）、死活题（有限时挑战）两种训练，错题会自动进错题本"
        ),
        GuidePage(
            "看懂形势判断",
            "· 胜率和目数差是两回事：胜率说谁赢面大，目数差说大概领先多少；局面平稳时两个一起看，乱战时先看胜率\n" +
                "· 棋盘上颜色越深的地方，归属越确定；颜色浅、发灰的地带是还没定型的「势力」，后面几手就可能翻盘\n" +
                "· 判断面板里的「可信度」低，多半是棋形还没定型，或者有大龙对杀——内置轻量模型在复杂对杀里本来就拿不准，换强力模型会明显更稳\n" +
                "· 死子判定可以在设置 → 形势判断里调严格程度：拿不准的棋，宁可调保守一点，最后数目时还能手动改"
        ),
        GuidePage(
            "模型",
            "· 轻量模型：内置，速度快，日常对局和分析够用\n" +
                "· 人类风格模型：内置，配合「模拟人类落子」和人类一选使用\n" +
                "· 强力模型：判断明显更准，改为免费按需下载（约 98 MB），在模型库里点下载即可，不下也不影响使用\n" +
                "· 所有模型都能删除腾空间（预装的删除后可免费恢复），也能导入你自己的 KataGo 模型"
        ),
        GuidePage(
            "声明",
            "· 没有账号、不收集数据：棋谱、设置、进度全都存在你的手机里，可在设置里一键备份导出\n" +
                "· 只有两处会联网，且都要你亲手点：设置里的「检查更新」，和下载强力模型\n" +
                "· AI 的判断和推荐仅供参考，正式比赛请以裁判为准\n" +
                "· 本 App 以 GPLv3 开源，引擎 KataGo 遵循 MIT 许可，感谢开源社区"
        )
    )
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    val lastPage = pagerState.currentPage == pages.size - 1

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Mascot(modifier = Modifier.size(46.dp))
                Spacer(modifier = Modifier.size(12.dp))
                Text(text = "TuxunGo", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.weight(1f))
                if (!lastPage) {
                    TextButton(onClick = onFinish) { Text("跳过") }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f)
            ) { page ->
                val p = pages[page]
                Column {
                    Text(
                        text = p.title,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(text = p.body, fontSize = 15.sp, lineHeight = 24.sp)
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                for (i in pages.indices) {
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 3.dp)
                            .size(if (i == pagerState.currentPage) 9.dp else 7.dp)
                            .background(
                                if (i == pagerState.currentPage) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    Color(0xFFCCCCCC)
                                },
                                CircleShape
                            )
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    if (lastPage) {
                        onFinish()
                    } else {
                        scope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Text(
                    text = if (lastPage) "开始使用" else "下一页（${pagerState.currentPage + 1}/${pages.size}）",
                    fontSize = 16.sp
                )
            }
        }
    }
}
