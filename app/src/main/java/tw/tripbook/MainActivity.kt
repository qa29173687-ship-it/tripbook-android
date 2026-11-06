package tw.tripbook

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import tw.tripbook.ui.screens.*
import tw.tripbook.ui.theme.*

class MainActivity : ComponentActivity() {
    private val vm: TripViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TripBookTheme {
                Surface(color = Cream) {
                    AppNav(vm)
                }
            }
        }
    }
}

private sealed class Tab(val route: String, val label: String, val icon: ImageVector) {
    data object Trips : Tab("tab_trips", "行程", Icons.Outlined.Home)
    data object Records : Tab("tab_records", "紀錄", Icons.Outlined.MenuBook)
    data object Settle : Tab("tab_settle", "結算", Icons.Outlined.AccountBalanceWallet)
    data object Settings : Tab("tab_settings", "設定", Icons.Outlined.Settings)
}

@Composable
fun AppNav(vm: TripViewModel) {
    val nav = rememberNavController()
    val tabs = listOf(Tab.Trips, Tab.Records, Tab.Settle, Tab.Settings)
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route.orEmpty()
    val showBottom = route.startsWith("tab_") || route == "home"

    Scaffold(
        containerColor = Cream,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBottom) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    tabs.forEach { tab ->
                        val selected = route == tab.route || (tab == Tab.Trips && route == "home")
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                nav.navigate(tab.route) {
                                    popUpTo(nav.graph.startDestinationId) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MintDeep,
                                selectedTextColor = MintDeep,
                                indicatorColor = MintSoft
                            )
                        )
                    }
                }
            }
        }
    ) { pad ->
        NavHost(nav, startDestination = Tab.Trips.route, Modifier.padding(pad)) {
            composable(Tab.Trips.route) {
                HomeScreen(
                    vm = vm,
                    onOpenTrip = { nav.navigate("trip/$it") },
                    onCreateTrip = { nav.navigate("editTrip") },
                    onCash = { tripId, memberId -> nav.navigate("cash/$tripId/${memberId ?: -1}") }
                )
            }
            composable(Tab.Records.route) { RecordsScreen(vm) { nav.navigate("trip/$it") } }
            composable(Tab.Settle.route) { SettlePickScreen(vm) { nav.navigate("settle/$it") } }
            composable(Tab.Settings.route) { SettingsScreen(vm) }
            composable("editTrip") {
                EditTripScreen(
                    vm,
                    onDone = { id -> nav.navigate("trip/$id") { popUpTo(Tab.Trips.route) } },
                    onBack = { nav.popBackStack() }
                )
            }
            composable(
                "editTrip/{id}",
                arguments = listOf(navArgument("id") { type = NavType.LongType })
            ) { e ->
                val id = e.arguments!!.getLong("id")
                EditTripScreen(
                    vm,
                    tripId = id,
                    onDone = { nav.popBackStack() },
                    onBack = { nav.popBackStack() }
                )
            }
            composable("trip/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { e ->
                val id = e.arguments!!.getLong("id")
                TripDetailScreen(
                    vm, id,
                    onBack = { nav.popBackStack() },
                    onAddExpense = { nav.navigate("addExpense/$id") },
                    onSettle = { nav.navigate("settle/$id") },
                    onMembers = { nav.navigate("members/$id") },
                    onEditTrip = { nav.navigate("editTrip/$id") },
                    onExpense = { eid -> nav.navigate("expense/$id/$eid") },
                    onPerson = { mid -> nav.navigate("person/$id/$mid") }
                )
            }
            composable("addExpense/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { e ->
                AddExpenseScreen(vm, e.arguments!!.getLong("id"), onBack = { nav.popBackStack() })
            }
            composable(
                "editExpense/{tripId}/{expenseId}",
                arguments = listOf(
                    navArgument("tripId") { type = NavType.LongType },
                    navArgument("expenseId") { type = NavType.LongType }
                )
            ) { e ->
                AddExpenseScreen(
                    vm,
                    tripId = e.arguments!!.getLong("tripId"),
                    expenseId = e.arguments!!.getLong("expenseId"),
                    onBack = { nav.popBackStack() }
                )
            }
            composable(
                "expense/{tripId}/{expenseId}",
                arguments = listOf(
                    navArgument("tripId") { type = NavType.LongType },
                    navArgument("expenseId") { type = NavType.LongType }
                )
            ) { e ->
                val tripId = e.arguments!!.getLong("tripId")
                val expenseId = e.arguments!!.getLong("expenseId")
                ExpenseDetailScreen(
                    vm, tripId, expenseId,
                    onBack = { nav.popBackStack() },
                    onEdit = { nav.navigate("editExpense/$tripId/$expenseId") }
                )
            }
            composable(
                "person/{tripId}/{memberId}",
                arguments = listOf(
                    navArgument("tripId") { type = NavType.LongType },
                    navArgument("memberId") { type = NavType.LongType }
                )
            ) { e ->
                val tripId = e.arguments!!.getLong("tripId")
                val memberId = e.arguments!!.getLong("memberId")
                PersonDetailScreen(
                    vm, tripId, memberId,
                    onBack = { nav.popBackStack() },
                    onExpense = { eid -> nav.navigate("expense/$tripId/$eid") }
                )
            }
            composable("settle/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { e ->
                SettleScreen(vm, e.arguments!!.getLong("id"), onBack = { nav.popBackStack() })
            }
            composable("members/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { e ->
                val id = e.arguments!!.getLong("id")
                MembersScreen(
                    vm, id,
                    onBack = { nav.popBackStack() },
                    onPerson = { mid -> nav.navigate("person/$id/$mid") }
                )
            }
            composable(
                "cash/{tripId}/{memberId}",
                arguments = listOf(
                    navArgument("tripId") { type = NavType.LongType },
                    navArgument("memberId") { type = NavType.LongType }
                )
            ) { e ->
                val tripId = e.arguments!!.getLong("tripId")
                val mid = e.arguments!!.getLong("memberId").let { if (it < 0) null else it }
                CashTimelineScreen(vm, tripId, mid, onBack = { nav.popBackStack() })
            }
        }
    }
}
