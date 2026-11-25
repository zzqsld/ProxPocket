package com.proxpocket.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.proxpocket.app.Pm3ViewModel
import com.proxpocket.app.ui.CardInfoHeader
import com.proxpocket.app.ui.TerminalView

/** 电子护照 / 电子通行证（eMRTD，ICAO 9303，BAC 认证） */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmrtdScreen(
    vm: Pm3ViewModel,
    onBack: () -> Unit,
) {
    var tab by remember { mutableIntStateOf(0) }
    val busy by vm.busy.collectAsState()
    val log by vm.log.collectAsState()
    val cardInfo by vm.cardInfo.collectAsState()

    var doc by remember { mutableStateOf("") }
    var birth by remember { mutableStateOf("") }
    var expiry by remember { mutableStateOf("") }
    val inputOk = doc.isNotBlank() && birth.length == 6 && expiry.length == 6

    fun mrzArgs() = "-n ${doc.trim()} -d $birth -e $expiry"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("护照 / 电子通行证") },
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
                .padding(16.dp),
        ) {
            CardInfoHeader(cardInfo = cardInfo, busy = busy)
            Spacer(Modifier.height(12.dp))
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("读取") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("终端") })
            }
            Spacer(Modifier.height(8.dp))
            when (tab) {
                0 -> Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        "输入护照资料页底部机读码（MRZ）中的三项信息，把护照放在天线上再读取。BAC 认证全程在本地完成。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = doc,
                        onValueChange = {
                            doc = it.filter { c -> c.isLetterOrDigit() }.take(9).uppercase()
                        },
                        label = { Text("证件号码") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = birth,
                        onValueChange = { birth = it.filter(Char::isDigit).take(6) },
                        label = { Text("出生日期（YYMMDD）") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = expiry,
                        onValueChange = { expiry = it.filter(Char::isDigit).take(6) },
                        label = { Text("有效期至（YYMMDD）") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { vm.exec("hf emrtd info ${mrzArgs()}") },
                        enabled = !busy && inputOk,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Filled.Badge, null)
                        Spacer(Modifier.padding(2.dp))
                        Text("读取证件信息")
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { vm.exec("hf emrtd dump ${mrzArgs()}") },
                        enabled = !busy && inputOk,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("导出完整数据（含照片文件）")
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "导出的文件在「数据管理」中查看。读取结果在下方终端中显示。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
                else -> TerminalView(
                    log = log,
                    busy = busy,
                    onSend = { vm.exec(it) },
                    onClear = vm::clearLog,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
