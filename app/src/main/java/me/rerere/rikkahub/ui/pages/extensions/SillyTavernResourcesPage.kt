package me.rerere.rikkahub.ui.pages.extensions

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Download01
import me.rerere.hugeicons.stroke.FileImport
import me.rerere.hugeicons.stroke.Puzzle
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.model.SillyTavernMarketAsset
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.utils.plus
import org.koin.androidx.compose.koinViewModel

@Composable
fun SillyTavernResourcesPage(
    vm: SillyTavernResourcesVM = koinViewModel(),
) {
    val context = LocalContext.current
    val toaster = LocalToaster.current
    val uiState by vm.uiState.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val fileImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        vm.importFromFile(context, uri) { success, message ->
            toaster.show(
                if (success) {
                    context.getString(R.string.st_resources_import_success, message)
                } else {
                    context.getString(R.string.st_resources_import_failed, message)
                }
            )
        }
    }

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.st_resources_page_title)) },
                navigationIcon = { BackButton() },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors,
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.topBarColors.containerColor,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = innerPadding + PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                CardGroup(
                    title = { Text(stringResource(R.string.st_resources_import_section)) },
                ) {
                    item(
                        onClick = {
                            fileImportLauncher.launch(
                                arrayOf(
                                    "application/json",
                                    "text/*",
                                    "image/png",
                                    "application/octet-stream",
                                )
                            )
                        },
                        leadingContent = { Icon(HugeIcons.FileImport, null) },
                        headlineContent = { Text(stringResource(R.string.st_resources_import_from_file)) },
                        supportingContent = { Text(stringResource(R.string.st_resources_import_from_file_desc)) },
                        trailingContent = {
                            if (uiState.loading) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            }
                        },
                    )
                    item(
                        onClick = {
                            vm.loadOfficialAssets { success, message ->
                                toaster.show(
                                    if (success) {
                                        context.getString(R.string.st_resources_market_loaded, message)
                                    } else {
                                        context.getString(R.string.st_resources_import_failed, message)
                                    }
                                )
                            }
                        },
                        leadingContent = { Icon(HugeIcons.Download01, null) },
                        headlineContent = { Text(stringResource(R.string.st_resources_load_market)) },
                        supportingContent = { Text(stringResource(R.string.st_resources_load_market_desc)) },
                    )
                }
            }

            if (uiState.assets.isNotEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.st_resources_market_section),
                        style = MaterialTheme.typography.titleSmallEmphasized,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 8.dp),
                    )
                }
                items(uiState.assets, key = { it.filename }) { asset ->
                    SillyTavernMarketAssetItem(
                        asset = asset,
                        importing = uiState.importingAsset == asset.filename,
                        onImport = {
                            vm.importAsset(asset) { success, message ->
                                toaster.show(
                                    if (success) {
                                        context.getString(R.string.st_resources_import_success, message)
                                    } else {
                                        context.getString(R.string.st_resources_import_failed, message)
                                    }
                                )
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SillyTavernMarketAssetItem(
    asset: SillyTavernMarketAsset,
    importing: Boolean,
    onImport: () -> Unit,
) {
    CardGroup {
        item(
            onClick = if (importing) null else onImport,
            leadingContent = { Icon(HugeIcons.Puzzle, null) },
            headlineContent = { Text(asset.displayName) },
            supportingContent = { Text(asset.type) },
            trailingContent = {
                if (importing) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(R.string.st_resources_import_asset))
                }
            },
        )
    }
}
