package com.proxpocket.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Toys
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.proxpocket.app.Pm3ViewModel

data class HomeAction(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val route: String?,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    vm: Pm3ViewModel,
    onNavigate: (String) -> Unit,
) {
    val connState by vm.connState.collectAsState()
    val connected = connState as? Pm3ViewModel.ConnState.Connected

    val actions = listOf(
        HomeAction("高频 IC 卡", "Mifare Classic / M1 卡", Icons.Filled.CreditCard, "mifare"),
        HomeAction("低频 ID 卡", "EM4100 / HID / T55xx", Icons.Filled.Radio, "lowfreq"),
        HomeAction("Amiibo / 碰碰贴", "Mifare Ultralight / NTAG", Icons.Filled.Toys, "ultralight"),
        HomeAction("特斯联卡", "ISO15693", Icons.Filled.Nfc, "iso15693"),
        HomeAction("CPU 卡", "ISO14443A / APDU", Icons.Filled.Memory, "cpu"),
        HomeAction("公交卡", "余额 / 交易记录", Icons.Filled.DirectionsBus, "transit"),
        HomeAction("护照/通行证", "eMRTD / BAC 认证", Icons.Filled.Badge, "emrtd"),
        HomeAction("数据管理", "dump / 密钥文件", Icons.Filled.FolderOpen, "files"),
        HomeAction("设备配置", "参数调整与固件信息", Icons.Filled.Settings, "device"),
    )

    Scaffold(
        topBar = { TopAppBar(title = { Text("ProxPocket") }) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
        ) {
            // 已连接设备横幅
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
                elevation = CardDefaults.cardElevation(1.dp),
            ) {
                Row(
                    Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.Memory,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp),
                    )
                    Spacer(Modifier.size(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            connected?.deviceName ?: "未连接",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                color = if (connected != null) MaterialTheme.colorScheme.secondary
                                else MaterialTheme.colorScheme.error,
                                shape = MaterialTheme.shapes.small,
                            ) {
                                Text(
                                    if (connected != null) " 已连接 " else " 未连接 ",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                )
                            }
                            Spacer(Modifier.size(6.dp))
                            Text("USB", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    IconButton(onClick = { vm.disconnect(); onNavigate("connect") }) {
                        Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "断开")
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Text("快捷操作", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(2.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(actions) { action ->
                    Card(
                        Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .clickable(enabled = action.route != null) {
                                action.route?.let(onNavigate)
                            },
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                        ),
                        elevation = CardDefaults.cardElevation(1.dp),
                    ) {
                        Column(
                            Modifier
                                .fillMaxSize()
                                .padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Icon(
                                action.icon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp),
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                action.title,
                                style = MaterialTheme.typography.titleSmall,
                                textAlign = TextAlign.Center,
                            )
                            Text(
                                action.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline,
                                maxLines = 2,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }

            // 进入终端
            Card(
                Modifier
                    .fillMaxWidth()
                    .clickable { onNavigate("terminal") },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(1.dp),
            ) {
                Row(
                    Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.Terminal,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                    )
                    Spacer(Modifier.size(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("进入终端", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "发送命令与设备交互",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                }
            }
        }
    }
}
