package com.djzrs.htmlmanager.navigation

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.djzrs.htmlmanager.server.ServerBridge
import com.djzrs.htmlmanager.ui.edit.EditScreen
import com.djzrs.htmlmanager.ui.edit.EditViewModel
import com.djzrs.htmlmanager.ui.home.HomeScreen
import com.djzrs.htmlmanager.ui.home.HomeViewModel
import com.djzrs.htmlmanager.ui.server.ServerScreen
import com.djzrs.htmlmanager.ui.server.ServerViewModel

object Routes {
    const val HOME = "home"
    const val SERVER = "server"
    const val EDIT = "edit"
    private const val ARG_NAME = "name"
    const val EDIT_PATTERN = "$EDIT?$ARG_NAME={$ARG_NAME}"

    fun edit(name: String? = null): String =
        if (name == null) EDIT else "$EDIT?$ARG_NAME=${android.net.Uri.encode(name)}"
}

@Composable
fun AppNavHost(
    navController: NavHostController = rememberNavController()
) {
    // 通知栏、悬浮窗这类外部入口通过 NavBridge 投递跳转请求。
    val pendingRoute by NavBridge.pending.collectAsStateWithLifecycle()
    LaunchedEffect(pendingRoute) {
        pendingRoute?.let { route ->
            navController.navigate(route) { launchSingleTop = true }
            NavBridge.consume()
        }
    }

    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            val homeViewModel: HomeViewModel = viewModel()
            val serverState by ServerBridge.state.collectAsStateWithLifecycle()
            HomeScreen(
                viewModel = homeViewModel,
                isServerRunning = serverState.running,
                onCreate = { navController.navigate(Routes.edit()) },
                onEdit = { name -> navController.navigate(Routes.edit(name)) },
                onOpenServer = { navController.navigate(Routes.SERVER) }
            )
        }

        composable(Routes.SERVER) {
            val serverViewModel: ServerViewModel = viewModel()
            ServerScreen(
                viewModel = serverViewModel,
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Routes.EDIT_PATTERN,
            arguments = listOf(
                navArgument("name") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { entry ->
            val name = entry.arguments?.getString("name")
            val application = LocalContext.current.applicationContext as Application
            val editViewModel: EditViewModel = viewModel { EditViewModel(application, name) }
            EditScreen(
                viewModel = editViewModel,
                onExit = { navController.popBackStack() }
            )
        }
    }
}
