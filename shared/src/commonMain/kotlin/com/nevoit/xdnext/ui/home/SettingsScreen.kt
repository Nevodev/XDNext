package com.nevoit.xdnext.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.nevoit.material.core.component.PageHeader

@Composable
fun SettingsScreen() {
    var simplifyTimeline by remember { mutableStateOf(false) }
    var lowElectricityWarning by remember { mutableStateOf(true) }
    var courseReminder by remember { mutableStateOf(true) }
    var tableBackground by remember { mutableStateOf(false) }

    PageColumn {
        item { PageHeader(title = "设置") }

//        SectionTitle("程序信息")
//        Card {
//            Rows(
//                {
//                    ListRow(
//                        title = "关于本程序",
//                        leadingIcon = Symbol.Info,
//                        trailing = { RowChevron() },
//                    )
//                },
//                { ListRow(title = "版本号", trailing = { RowValue("1.0 (1)") }) },
//            )
//        }
//
//        SectionTitle("界面设置")
//        Card {
//            Rows(
//                {
//                    ListRow(
//                        title = "颜色设置",
//                        leadingIcon = Symbol.Palette,
//                        trailing = { RowValue("跟随系统") },
//                    )
//                },
//                {
//                    ListRow(
//                        title = "设置深浅色",
//                        leadingIcon = Symbol.DarkMode,
//                        trailing = { RowValue("跟随系统") },
//                    )
//                },
//                {
//                    ListRow(
//                        title = "字体大小与粗细",
//                        leadingIcon = Symbol.FormatSize,
//                        trailing = { RowValue("100% · 常规") },
//                    )
//                },
//                {
//                    ListRow(
//                        title = "语言",
//                        leadingIcon = Symbol.Language,
//                        trailing = { RowValue("简体中文") },
//                    )
//                },
//                {
//                    ListRow(
//                        title = "简化日程时间轴",
//                        description = "没有日程时 减少空间占用",
//                        trailing = {
//                            Switch(
//                                checked = simplifyTimeline,
//                                onCheckedChange = { simplifyTimeline = it },
//                            )
//                        },
//                    )
//                },
//                {
//                    ListRow(
//                        title = "低电量卡片变色提醒",
//                        description = "电量小于阈值时 电量卡片变色提醒",
//                        trailing = {
//                            Switch(
//                                checked = lowElectricityWarning,
//                                onCheckedChange = { lowElectricityWarning = it },
//                            )
//                        },
//                    )
//                },
//                {
//                    ListRow(
//                        title = "低电量阈值",
//                        description = "当前为 5 度",
//                        trailing = { RowChevron() },
//                    )
//                },
//            )
//        }
//
//        SectionTitle("账号设置")
//        Card {
//            Rows(
//                {
//                    ListRow(
//                        title = "体育系统密码设置",
//                        leadingIcon = Symbol.Run,
//                        trailing = { RowChevron() },
//                    )
//                },
//                {
//                    ListRow(
//                        title = "物理实验系统密码设置",
//                        leadingIcon = Symbol.Science,
//                        trailing = { RowChevron() },
//                    )
//                },
//                {
//                    ListRow(
//                        title = "校园网帐号密码设置",
//                        description = "不设置查看不了网费",
//                        leadingIcon = Symbol.Wifi,
//                        trailing = { RowChevron() },
//                    )
//                },
//            )
//        }
//
//        SectionTitle("通知设置")
//        Card {
//            ListRow(
//                title = "课前通知设置",
//                description = "设置课前提醒通知",
//                leadingIcon = Symbol.Notifications,
//                trailing = {
//                    Switch(
//                        checked = courseReminder,
//                        onCheckedChange = { courseReminder = it },
//                    )
//                },
//            )
//        }
//
//        SectionTitle("课表相关设置")
//        Card {
//            Rows(
//                {
//                    ListRow(
//                        title = "开启课表背景图",
//                        leadingIcon = Symbol.Image,
//                        trailing = {
//                            Switch(
//                                checked = tableBackground,
//                                onCheckedChange = { tableBackground = it },
//                            )
//                        },
//                    )
//                },
//                {
//                    ListRow(
//                        title = "课表显示与样式",
//                        description = "时间指示和课程卡片样式",
//                        leadingIcon = Symbol.Palette,
//                        trailing = { RowChevron() },
//                    )
//                },
//                {
//                    ListRow(
//                        title = "清除所有用户添加课程",
//                        leadingIcon = Symbol.Tune,
//                        trailing = { RowChevron() },
//                    )
//                },
//                {
//                    ListRow(
//                        title = "强制刷新课表",
//                        leadingIcon = Symbol.Refresh,
//                        trailing = { RowChevron() },
//                    )
//                },
//                {
//                    ListRow(
//                        title = "课程偏移设置",
//                        description = "目前为 0 天",
//                        leadingIcon = Symbol.Calendar,
//                        trailing = { RowChevron() },
//                    )
//                },
//            )
//        }
//
//        SectionTitle("缓存登录设置")
//        Card {
//            Rows(
//                {
//                    ListRow(
//                        title = "查看网络拦截器和日志",
//                        leadingIcon = Symbol.Article,
//                        trailing = { RowChevron() },
//                    )
//                },
//                {
//                    ListRow(
//                        title = "清除缓存后重启",
//                        leadingIcon = Symbol.Restart,
//                        trailing = { RowChevron() },
//                    )
//                },
//                {
//                    // Destructive, so it takes the error role rather than the content colour.
//                    CompositionLocalProvider(
//                        LocalContentColor provides MaterialTheme.colors.error
//                    ) {
//                        ListRow(
//                            title = "退出登录并重启应用",
//                            leadingIcon = Symbol.Logout,
//                            trailing = { RowChevron() },
//                        )
//                    }
//                },
//            )
//        }
    }
}
