package com.ferrestock.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.drawWithContent
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
    // Al volver de otra app (galería, cámara) Supabase recarga la sesión y pasa un instante por
    // "Initializing". Si en ese instante se desmontara MainNav, se perdería la navegación y la app
    // volvería al inicio. Por eso, una vez adentro, solo se sale con NotAuthenticated.
    // Es saveable para que, si Android cerró el proceso, al volver se restaure la pantalla.
    var signedIn by rememberSaveable { mutableStateOf(false) }
    when (status) {
        is SessionStatus.Authenticated, is SessionStatus.RefreshFailure -> signedIn = true
        is SessionStatus.NotAuthenticated -> signedIn = false
        else -> Unit
    }
    when {
        signedIn -> MainNav()
        status is SessionStatus.NotAuthenticated -> LoginScreen()
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

    // Una pantalla barre a la otra: al avanzar la nueva entra desde la derecha por encima de
    // la actual (que se corre un poco y se oscurece); al volver, la de arriba sale hacia la
    // derecha y descubre la anterior. Sin fundido.
    NavHost(
        nav,
        startDestination = "home",
        enterTransition = { NavMotion.popping = false; slideInHorizontally(navTween()) { it } },
        exitTransition = { slideOutHorizontally(navTween()) { -it / 4 } },
        popEnterTransition = { NavMotion.popping = true; slideInHorizontally(navTween()) { -it / 4 } },
        popExitTransition = { slideOutHorizontally(navTween()) { it } },
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

private const val NAV_MS = 260

private fun <T> navTween() = tween<T>(NAV_MS, easing = FastOutSlowInEasing)

/** Sentido de la navegación en curso (lo fijan las transiciones del NavHost al arrancar). */
private object NavMotion {
    var popping by mutableStateOf(false)
}

/** Igual que composable(), pero con desenfoque de movimiento durante el barrido. */
private fun NavGraphBuilder.screen(
    route: String,
    arguments: List<NamedNavArgument> = emptyList(),
    content: @Composable (NavBackStackEntry) -> Unit,
) = composable(route, arguments) { entry -> MotionBlur(this) { content(entry) } }

/**
 * Desenfoque horizontal que sigue la velocidad del barrido: nulo al arrancar y al terminar,
 * máximo a mitad de camino, sobre toda la pantalla. La que barre (arriba) se desenfoca
 * fuerte; la de abajo, que apenas se corre, poco, y además se oscurece al quedar tapada.
 * El desenfoque necesita Android 12+; antes queda el barrido solo.
 */
@Composable
private fun MotionBlur(scope: AnimatedVisibilityScope, content: @Composable () -> Unit) {
    // 0 = fuera (antes de entrar), 1 = en su lugar, 2 = fuera (después de salir).
    val position by scope.transition.animateFloat(
        transitionSpec = { navTween() },
        label = "navPosition",
    ) { state ->
        when (state) {
            EnterExitState.PreEnter -> 0f
            EnterExitState.Visible -> 1f
            EnterExitState.PostExit -> 2f
        }
    }
    val entering = scope.transition.targetState == EnterExitState.Visible
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                val progress = if (entering) position else position - 1f
                val onTop = entering != NavMotion.popping
                // Curva aplanada: el desenfoque sube rápido y se sostiene casi todo el recorrido.
                val speed = sqrt(sin(PI * progress.coerceIn(0f, 1f))).toFloat()
                // Radio fijo en dp (no depende del tamaño que informe la capa, que puede llegar en 0).
                val radius = (if (onTop) 72.dp else 14.dp).toPx() * speed
                renderEffect = if (radius > 0.5f) BlurEffect(radius, 0f, TileMode.Decal) else null
            }
            .drawWithContent {
                drawContent()
                // La pantalla de abajo se oscurece a medida que la tapa la otra.
                val progress = if (entering) position else position - 1f
                val onTop = entering != NavMotion.popping
                if (!onTop) {
                    val covered = if (entering) 1f - progress else progress
                    drawRect(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.2f * covered.coerceIn(0f, 1f)))
                }
            }
    ) { content() }
}
