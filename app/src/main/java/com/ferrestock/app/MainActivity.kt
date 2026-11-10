package com.ferrestock.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
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
            // Las barras de abajo son azul marino: íconos del sistema claros.
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
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

    // Barrido lateral en el sentido de la navegación: hacia adelante entra desde la derecha,
    // al volver entra desde la izquierda. Sin fundido.
    NavHost(
        nav,
        startDestination = "home",
        enterTransition = { slideIntoContainer(SlideDirection.Start, navTween()) },
        exitTransition = { slideOutOfContainer(SlideDirection.Start, navTween()) },
        popEnterTransition = { slideIntoContainer(SlideDirection.End, navTween()) },
        popExitTransition = { slideOutOfContainer(SlideDirection.End, navTween()) },
    ) {
        screen("home") {
            HomeScreen(
                vm = vm,
                updates = updates,
                onOpenProduct = { nav.navigate("product/$it") },
                onAddProduct = { editProduct(null) },
                onNewOrder = { nav.navigate("orderNew") },
                onOpenOrder = { nav.navigate("order/$it") },
            )
        }
        screen("product/{id}") { entry ->
            val id = entry.arguments?.getString("id").orEmpty()
            ProductDetailScreen(vm, id, onBack = { nav.popBackStack() }, onEdit = { editProduct(id) })
        }
        screen(
            "productEdit?id={id}",
            arguments = listOf(navArgument("id") { type = NavType.StringType; nullable = true; defaultValue = null }),
        ) { entry ->
            ProductEditScreen(vm, entry.arguments?.getString("id"), onDone = { nav.popBackStack() })
        }
        screen("orderNew") {
            NewOrderScreen(
                vm = vm,
                onBack = { nav.popBackStack() },
                onCreated = { orderId ->
                    nav.navigate("order/$orderId") { popUpTo("home") }
                },
            )
        }
        screen("order/{id}") { entry ->
            OrderDetailScreen(vm, entry.arguments?.getString("id").orEmpty(), onBack = { nav.popBackStack() })
        }
    }
}

private const val NAV_MS = 380

private fun <T> navTween() = tween<T>(NAV_MS, easing = FastOutSlowInEasing)

/** Igual que composable(), pero con desenfoque de movimiento horizontal durante el barrido. */
private fun NavGraphBuilder.screen(
    route: String,
    arguments: List<NamedNavArgument> = emptyList(),
    content: @Composable (NavBackStackEntry) -> Unit,
) = composable(route, arguments) { entry -> MotionBlur(this) { content(entry) } }

/**
 * Desenfoque solo en el eje X, más fuerte cuanto más lejos está la pantalla de su lugar:
 * se lee como estela de movimiento lateral. Necesita Android 12+; antes queda solo el barrido.
 */
@Composable
private fun MotionBlur(scope: AnimatedVisibilityScope, content: @Composable () -> Unit) {
    val radius by scope.transition.animateFloat(
        transitionSpec = { navTween() },
        label = "motionBlur",
    ) { state -> if (state == EnterExitState.Visible) 0f else 60f }
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { renderEffect = if (radius > 0.5f) BlurEffect(radius, 0f, TileMode.Decal) else null }
    ) { content() }
}
