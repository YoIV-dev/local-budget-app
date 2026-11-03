package com.localbudget.app.ui.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.localbudget.app.di.AppContainer
import com.localbudget.app.ui.screens.categories.CategoryManagementScreen
import com.localbudget.app.ui.screens.categories.CategoryViewModel
import com.localbudget.app.ui.screens.expenses.ExpenseListScreen
import com.localbudget.app.ui.screens.expenses.ExpenseViewModel
import com.localbudget.app.ui.screens.fridge.FridgeScreen
import com.localbudget.app.ui.screens.fridge.FridgeViewModel
import com.localbudget.app.ui.screens.home.HomeScreen
import com.localbudget.app.ui.screens.home.HomeViewModel
import com.localbudget.app.ui.screens.statement.StatementImportHost
import com.localbudget.app.ui.screens.statement.StatementImportViewModel
import com.localbudget.app.ui.screens.statement.rememberStatementPicker

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavGraph(
    appContainer: AppContainer,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    /** Bottom-nav route to switch to, e.g. from a notification tap. */
    pendingRoute: String? = null,
    onPendingRouteHandled: () -> Unit = {}
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: Screen.Home.route

    LaunchedEffect(pendingRoute) {
        if (pendingRoute != null) {
            navController.navigate(pendingRoute) {
                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
            onPendingRouteHandled()
        }
    }

    val currentScreen = Screen.bottomNavItems.find { it.route == currentRoute } ?: Screen.Home

    // Home and the two stock tabs are used most, so their view models live at the activity and start
    // reading in the background from launch: switching to them shows their data at once. The other
    // screens create theirs, and read, only when opened.
    val selectedMonthViewModel: SelectedMonthViewModel = viewModel()
    val homeViewModel: HomeViewModel = viewModel(
        factory = HomeViewModel.Factory(
            appContainer.expenseRepository,
            appContainer.categoryRepository,
            appContainer.budgetRepository,
            selectedMonthViewModel.month,
            appContainer.subscriptionSelections
        )
    )
    val fridgeViewModel: FridgeViewModel = viewModel(
        key = Screen.Fridge.route,
        factory = FridgeViewModel.Factory(appContainer.fridgeRepository)
    )

    // Started from the add menus of both home and the expense list, so shared by them
    val statementImportViewModel: StatementImportViewModel = viewModel(
        factory = StatementImportViewModel.Factory(appContainer.cardStatementImporter, appContainer.categoryRepository)
    )
    val pickStatement = rememberStatementPicker(statementImportViewModel)

    Scaffold(
        topBar = {
            if (currentRoute != Screen.CameraScan.route && currentRoute != Screen.ReceiptGallery.route && currentRoute != Screen.RecurringExpenses.route) {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = currentScreen.title,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleLarge
                        )
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
        },
        bottomBar = {
            if (currentRoute != Screen.CameraScan.route && currentRoute != Screen.ReceiptGallery.route && currentRoute != Screen.RecurringExpenses.route) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    Screen.bottomNavItems.forEach { screen ->
                        val isSelected = currentRoute == screen.route
                        NavigationBarItem(
                            icon = { Icon(imageVector = screen.icon, contentDescription = screen.title) },
                            label = { Text(screen.title) },
                            selected = isSelected,
                            onClick = {
                                if (currentRoute != screen.route) {
                                    navController.navigate(screen.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            }
                        )
                    }
                }
            }
        },
        modifier = modifier
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { ExitTransition.None },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            composable(Screen.Home.route) {
                HomeScreen(
                    viewModel = homeViewModel,
                    onNavigateToExpenses = {
                        navController.navigate(Screen.Expenses.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onNavigateToCamera = {
                        navController.navigate(Screen.CameraScan.route)
                    },
                    onNavigateToGallery = { navController.navigate(Screen.ReceiptGallery.route) },
                    onImportStatement = pickStatement
                )
            }

            composable(Screen.Expenses.route) {
                val expenseViewModel: ExpenseViewModel = viewModel(
                    factory = ExpenseViewModel.Factory(
                        appContainer.expenseRepository,
                        appContainer.categoryRepository,
                        selectedMonthViewModel.month
                    )
                )
                ExpenseListScreen(
                    viewModel = expenseViewModel,
                    onNavigateToCamera = {
                        navController.navigate(Screen.CameraScan.route)
                    },
                    onNavigateToGallery = { navController.navigate(Screen.ReceiptGallery.route) },
                    onImportStatement = pickStatement
                )
            }

            composable(Screen.Fridge.route) {
                FridgeScreen(viewModel = fridgeViewModel)
            }

            composable(Screen.FreezerPantry.route) {
                LaunchedEffect(Unit) {
                    navController.navigate(Screen.Fridge.route) {
                        popUpTo(Screen.FreezerPantry.route) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            }

            composable(Screen.Categories.route) {
                val categoryViewModel: CategoryViewModel = viewModel(
                    factory = CategoryViewModel.Factory(
                        appContainer.categoryRepository
                    )
                )
                CategoryManagementScreen(viewModel = categoryViewModel)
            }

            composable(Screen.Settings.route) {
                val settingsViewModel: com.localbudget.app.ui.screens.settings.SettingsViewModel = viewModel(
                    factory = com.localbudget.app.ui.screens.settings.SettingsViewModel.Factory(
                        appContainer.jsonBackupManager,
                        appContainer.csvExportManager,
                        appContainer.scanLogSettings,
                        appContainer.scanLogStore,
                        appContainer.fridgeReminderSettings,
                        appContainer.fridgeReminderScheduler
                    )
                )
                com.localbudget.app.ui.screens.settings.SettingsScreen(
                    viewModel = settingsViewModel,
                    onNavigateToCategories = {
                        navController.navigate(Screen.Categories.route)
                    }
                )
            }

            composable(Screen.RecurringExpenses.route) {
                val recurringViewModel: com.localbudget.app.ui.screens.recurring.RecurringExpenseViewModel = viewModel(
                    factory = com.localbudget.app.ui.screens.recurring.RecurringExpenseViewModel.Factory(
                        appContainer.recurringExpenseRepository,
                        appContainer.categoryRepository,
                        appContainer.recurringExpenseManager
                    )
                )
                com.localbudget.app.ui.screens.recurring.RecurringExpenseScreen(
                    viewModel = recurringViewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(Screen.CameraScan.route) {
                val cameraViewModel: com.localbudget.app.ui.screens.camera.CameraReceiptViewModel = viewModel(
                    factory = com.localbudget.app.ui.screens.camera.CameraReceiptViewModel.Factory(
                        appContainer.receiptOcrEngine,
                        appContainer.expenseRepository,
                        appContainer.categoryRepository,
                        appContainer.fridgeRepository,
                        appContainer.scanLogSettings.enabled,
                        appContainer.scanLogStore
                    )
                )
                com.localbudget.app.ui.screens.camera.CameraReceiptScreen(
                    viewModel = cameraViewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable(Screen.ReceiptGallery.route) {
                val cameraViewModel: com.localbudget.app.ui.screens.camera.CameraReceiptViewModel = viewModel(
                    factory = com.localbudget.app.ui.screens.camera.CameraReceiptViewModel.Factory(
                        appContainer.receiptOcrEngine, appContainer.expenseRepository, appContainer.categoryRepository,
                        appContainer.fridgeRepository, appContainer.scanLogSettings.enabled, appContainer.scanLogStore
                    )
                )
                com.localbudget.app.ui.screens.camera.CameraReceiptScreen(
                    viewModel = cameraViewModel,
                    onNavigateBack = { navController.popBackStack() },
                    galleryOnly = true
                )
            }
        }
    }

    // After the Scaffold so its review screen is drawn over the bars too (MainActivity's Surface stacks them)
    StatementImportHost(statementImportViewModel)
}
