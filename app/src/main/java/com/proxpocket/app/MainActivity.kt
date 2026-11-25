package com.proxpocket.app

import android.hardware.usb.UsbManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.proxpocket.app.ui.ProxPocketTheme
import com.proxpocket.app.ui.screens.ConnectScreen
import com.proxpocket.app.ui.screens.CpuCardScreen
import com.proxpocket.app.ui.screens.DumpViewScreen
import com.proxpocket.app.ui.screens.EmrtdScreen
import com.proxpocket.app.ui.screens.FilesScreen
import com.proxpocket.app.ui.screens.HomeScreen
import com.proxpocket.app.ui.screens.Iso15693Screen
import com.proxpocket.app.ui.screens.LowFreqScreen
import com.proxpocket.app.ui.screens.MifareScreen
import com.proxpocket.app.ui.screens.PlaceholderScreen
import com.proxpocket.app.ui.screens.TerminalScreen
import com.proxpocket.app.ui.screens.TransitScreen
import com.proxpocket.app.ui.screens.UltralightScreen

class MainActivity : ComponentActivity() {

    private val vm: Pm3ViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // USB 设备插入拉起 App 时刷新设备列表
        if (intent?.action == UsbManager.ACTION_USB_DEVICE_ATTACHED) {
            vm.refreshDevices()
        }
        setContent {
            ProxPocketTheme {
                ProxPocketApp(vm)
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        if (intent.action == UsbManager.ACTION_USB_DEVICE_ATTACHED) {
            vm.refreshDevices()
        }
    }
}

@Composable
fun ProxPocketApp(vm: Pm3ViewModel) {
    val nav = rememberNavController()
    val connState by vm.connState.collectAsState()
    val notice by vm.notice.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(notice) {
        notice?.let {
            snackbarHostState.showSnackbar(it, duration = SnackbarDuration.Short)
            vm.consumeNotice()
        }
    }

    // 连接成功后自动进入主页；断开连接不强制退出，留在当前页面（按钮会提示未连接）
    LaunchedEffect(connState) {
        if (connState is Pm3ViewModel.ConnState.Connected) {
            nav.navigate("home") { popUpTo("connect") { inclusive = true } }
        }
    }

    Box(Modifier.fillMaxSize()) {
        NavHost(navController = nav, startDestination = "connect") {
            composable("connect") {
                ConnectScreen(vm, onBrowse = { nav.navigate("home") })
            }
            composable("home") {
                HomeScreen(vm, onNavigate = { nav.navigate(it) })
            }
            composable("mifare") {
                MifareScreen(vm, onBack = { nav.popBackStack() })
            }
            composable("lowfreq") {
                LowFreqScreen(vm, onBack = { nav.popBackStack() })
            }
            composable("files") {
                FilesScreen(
                    vm = vm,
                    onBack = { nav.popBackStack() },
                    onViewDump = { file ->
                        vm.selectDump(file)
                        nav.navigate("dumpview")
                    },
                )
            }
            composable("dumpview") {
                val dump = vm.selectedDump.value
                if (dump == null) {
                    LaunchedEffect(Unit) { nav.popBackStack() }
                } else {
                    DumpViewScreen(
                        dump = dump,
                        onBack = { nav.popBackStack() },
                        onSave = vm::saveDumpEdits,
                    )
                }
            }
            composable("terminal") {
                TerminalScreen(vm, onBack = { nav.popBackStack() })
            }
            composable("ultralight") {
                UltralightScreen(vm, onBack = { nav.popBackStack() })
            }
            composable("iso15693") {
                Iso15693Screen(vm, onBack = { nav.popBackStack() })
            }
            composable("cpu") {
                CpuCardScreen(vm, onBack = { nav.popBackStack() })
            }
            composable("transit") {
                TransitScreen(vm, onBack = { nav.popBackStack() })
            }
            composable("emrtd") {
                EmrtdScreen(vm, onBack = { nav.popBackStack() })
            }
            composable("device") {
                PlaceholderScreen(
                    title = "设备配置",
                    hint = "查看设备状态与天线参数",
                    primaryCmd = "hw status",
                    primaryLabel = "查看设备状态",
                    vm = vm,
                    onBack = { nav.popBackStack() },
                )
            }
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}
