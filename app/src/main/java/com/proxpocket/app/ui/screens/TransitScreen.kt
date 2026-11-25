package com.proxpocket.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.proxpocket.app.Pm3ViewModel
import com.proxpocket.app.ui.CardInfoHeader

/** 公交卡读取：余额 + 卡号 + 有效期 + 最近交易记录 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransitScreen(
    vm: Pm3ViewModel,
    onBack: () -> Unit,
) {
    val busy by vm.busy.collectAsState()
    val running by vm.transitRunning.collectAsState()
    val step by vm.transitStep.collectAsState()
    val result by vm.transitResult.collectAsState()
    val cardInfo by vm.cardInfo.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("公交卡") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            CardInfoHeader(cardInfo = cardInfo, busy = busy)
            Spacer(Modifier.height(12.dp))

            Text(
                "把卡片放在天线上再开始读取。支持交通联合/住建部标准的 CPU 公交卡（余额+交易记录），以及八达通、深圳通 FeliCa 版（余额）。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
            Spacer(Modifier.height(12.dp))

            Button(
                onClick = { vm.readTransitPboc() },
                enabled = !busy && !running,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.DirectionsBus, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text("读取公交卡（CPU / 交通联合）")
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { vm.readFelicaBalance() },
                enabled = !busy && !running,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("读取八达通 / 深圳通 FeliCa")
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { vm.readSuica() },
                enabled = !busy && !running,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("读取 Suica / PASMO 乘车记录")
            }

            if (running) {
                Spacer(Modifier.height(12.dp))
                Card(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.size(10.dp))
                        Text(step.ifBlank { "读取中…" }, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            result?.let { r ->
                Spacer(Modifier.height(12.dp))
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (r.success) MaterialTheme.colorScheme.surface
                        else MaterialTheme.colorScheme.errorContainer
                    ),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        if (!r.success) {
                            Text(r.message ?: "读取失败", color = MaterialTheme.colorScheme.error)
                            return@Column
                        }
                        r.message?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                            Spacer(Modifier.height(4.dp))
                        }
                        r.balance?.let {
                            Text("余额", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
                            Text("${r.balanceUnit} $it", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(8.dp))
                        }
                        r.serial?.let { InfoRow("卡号", it) }
                        r.version?.let { InfoRow("版本", it) }
                        r.validity?.let { InfoRow("有效期", it) }
                        if (r.aids.isNotEmpty()) {
                            InfoRow("应用", r.aids.joinToString("\n"), mono = true)
                        }
                    }
                }

                if (r.suicaEntries.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text("乘车记录", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(6.dp))
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(vertical = 4.dp)) {
                            r.suicaEntries.forEach { e ->
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text("${e.date}  ${e.kind}", style = MaterialTheme.typography.bodySmall)
                                        e.detail?.let {
                                            Text(it, style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.outline,
                                                fontFamily = FontFamily.Monospace)
                                        }
                                    }
                                    e.balance?.let {
                                        Text("¥$it", style = MaterialTheme.typography.titleMedium)
                                    }
                                }
                            }
                        }
                    }
                }

                if (r.txns.isNotEmpty()) {                    Spacer(Modifier.height(12.dp))
                    Text("最近交易", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(6.dp))
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(vertical = 4.dp)) {
                            r.txns.forEach { t ->
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(t.dateTime, style = MaterialTheme.typography.bodySmall)
                                        Text(
                                            "终端 ${t.terminal}" + (t.overdraft?.let { " · 透支 $it" } ?: ""),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.outline,
                                            fontFamily = FontFamily.Monospace,
                                        )
                                    }
                                    Text(
                                        "${if (t.expense) "-" else "+"}${t.amount}",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = if (t.expense) MaterialTheme.colorScheme.onSurface
                                        else MaterialTheme.colorScheme.secondary,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String, mono: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(width = 56.dp, height = 20.dp),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = if (mono) FontFamily.Monospace else null,
        )
    }
}
