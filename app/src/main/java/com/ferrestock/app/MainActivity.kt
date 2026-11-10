package com.ferrestock.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ferrestock.app.data.Demo
import com.ferrestock.app.data.Repo
import com.ferrestock.app.ui.HomeScreen
import com.ferrestock.app.ui.LoginScreen
import com.ferrestock.app.ui.NewOrderScreen
import com.ferrestock.app.ui.OrderDetailScreen
import com.ferrestock.app.ui.ProductDetailScreen
import com.ferrestock.app.ui.ProductEditScreen
import com.ferrestock.app.ui.SplashScreen
import com.ferrestock.app.ui.theme.FerrestockTheme
import com.ferrestock.app.update.UpdateViewModel
import io.github.jan.supabase.auth.status.SessionStatus

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        if (Repo.demo) Demo.init(applicationContext)
        setContent {
            FerrestockTheme { AppRoot() }
        }
    }
}

@Composable
private fun AppRoot() {
    if (Repo.demo) {
        MainNav()
        return
    }
    val status by Repo.sessionStatus.collectAsStateWithLifecycle()
    when (status) {
        is SessionStatus.Authenticated, is SessionStatus.RefreshFailure -> MainNav()
        is SessionStatus.NotAuthenticated -> LoginScreen()
        else -> SplashScreen()
    }
}

@Composable
private fun MainNav() {
    val vm: AppViewModel = viewModel()
    val updates: UpdateViewModel = viewModel()
    val nav = rememberNavController()

    // Busca versiones nuevas al abrir la app y cada vez que se vuelve a ella.
    LifecycleResumeEffect(Unit) {
        updates.checkInBackground()
        onPauseOrDispose { }
    }

    fun editProduct(id: String?) {
        vm.pendingPhoto = null
        nav.navigate(if (id == null) "productEdit" else "productEdit?id=$id")
    }

    NavHost(nav, startDestination = "home") {
        composable("home") {
            HomeScreen(
                vm = vm,
                updates = updates,
                onOpenProduct = { nav.navigate("product/$it") },
                onAddProduct = { editProduct(null) },
                onNewOrder = { nav.navigate("orderNew") },
                onOpenOrder = { nav.navigate("order/$it") },
            )
        }
        composable("product/{id}") { entry ->
            val id = entry.arguments?.getString("id").orEmpty()
            ProductDetailScreen(vm, id, onBack = { nav.popBackStack() }, onEdit = { editProduct(id) })
        }
        composable(
            "productEdit?id={id}",
            arguments = listOf(navArgument("id") { type = NavType.StringType; nullable = true; defaultValue = null }),
        ) { entry ->
            ProductEditScreen(vm, entry.arguments?.getString("id"), onDone = { nav.popBackStack() })
        }
        composable("orderNew") {
            NewOrderScreen(
                vm = vm,
                onBack = { nav.popBackStack() },
                onCreated = { orderId ->
                    nav.navigate("order/$orderId") { popUpTo("home") }
                },
            )
        }
        composable("order/{id}") { entry ->
            OrderDetailScreen(vm, entry.arguments?.getString("id").orEmpty(), onBack = { nav.popBackStack() })
        }
    }
}
