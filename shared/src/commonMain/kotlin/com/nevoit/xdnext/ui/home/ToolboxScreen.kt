package com.nevoit.xdnext.ui.home

import androidx.compose.runtime.Composable
import com.nevoit.material.core.component.PageHeader
import com.nevoit.material.core.component.Text
import com.nevoit.material.core.layout.ListRowAccessory
import com.nevoit.material.core.layout.ListStack

@Composable
fun ToolboxScreen() {
    ListStack {
        item { PageHeader(title = "其他功能", extraPadding = true) }
        Section {
            Row(accessory = ListRowAccessory.Chevron) {
                Text("缴费系统")
            }
            Row(accessory = ListRowAccessory.Chevron) {
                Text("订水系统")
            }
            Row(accessory = ListRowAccessory.Chevron) {
                Text("后勤保修")
            }
            Row(accessory = ListRowAccessory.Chevron) {
                Text("空间预约")
            }
            Row(accessory = ListRowAccessory.Chevron) {
                Text("移动门户")
            }
            Row(accessory = ListRowAccessory.Chevron) {
                Text("网络查询")
            }
            Row(accessory = ListRowAccessory.Chevron) {
                Text("物理计算")
            }
            Row(accessory = ListRowAccessory.Chevron) {
                Text("睿思导航")
            }
        }
    }
}
