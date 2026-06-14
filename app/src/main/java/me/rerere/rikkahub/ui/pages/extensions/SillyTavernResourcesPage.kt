package me.rerere.rikkahub.ui.pages.extensions

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import me.rerere.rikkahub.utils.openUrl
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
    val assetTypes = remember(uiState.assets) {
        uiState.assets.map { it.type }.distinct().sorted()
    }
    val filteredAssets = remember(uiState.assets, uiState.searchQuery, uiState.selectedType) {
        val query = uiState.searchQuery.trim()
        uiState.assets.asSequence()
            .filter { asset -> uiState.selectedType == null || asset.type == uiState.selectedType }
            .filter { asset ->
                query.isBlank() ||
                    asset.displayName.contains(query, ignoreCase = true) ||
                    asset.description?.contains(query, ignoreCase = true) == true ||
                    asset.type.contains(query, ignoreCase = true) ||
                    asset.filename.contains(query, ignoreCase = true)
            }
            .toList()
    }
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

            item {
                ExtensionCompatibilitySection(
                    onOpenDocs = {
                        context.openUrl("https://docs.sillytavern.app/extensions/")
                    },
                    onOpenOfficialExtensions = {
                        context.openUrl("https://github.com/SillyTavern/SillyTavern/tree/release/public/scripts/extensions")
                    },
                )
            }

            if (uiState.assets.isNotEmpty()) {
                item {
                    CardGroup(
                        title = { Text(stringResource(R.string.st_resources_filter_section)) },
                    ) {
                        item {
                            OutlinedTextField(
                                value = uiState.searchQuery,
                                onValueChange = vm::updateSearchQuery,
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text(stringResource(R.string.st_resources_search_placeholder)) },
                            )
                        }
                        item {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp),
                            ) {
                                item {
                                    FilterChip(
                                        selected = uiState.selectedType == null,
                                        onClick = { vm.updateSelectedType(null) },
                                        label = { Text(stringResource(R.string.st_resources_filter_all)) },
                                    )
                                }
                                items(assetTypes, key = { it }) { type ->
                                    FilterChip(
                                        selected = uiState.selectedType == type,
                                        onClick = { vm.updateSelectedType(type) },
                                        label = { Text(type) },
                                    )
                                }
                            }
                        }
                    }
                }
                item {
                    Text(
                        text = stringResource(
                            R.string.st_resources_market_section_with_count,
                            filteredAssets.size,
                            uiState.assets.size,
                        ),
                        style = MaterialTheme.typography.titleSmallEmphasized,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 8.dp),
                    )
                }
                items(filteredAssets, key = { it.filename }) { asset ->
                    SillyTavernMarketAssetItem(
                        asset = asset,
                        importing = uiState.importingAsset == asset.filename,
                        onImport = {
                            if (asset.type.equals("extension", ignoreCase = true)) {
                                context.openUrl(asset.downloadUrl)
                            } else {
                                vm.importAsset(asset) { success, message ->
                                    toaster.show(
                                        if (success) {
                                            context.getString(R.string.st_resources_import_success, message)
                                        } else {
                                            context.getString(R.string.st_resources_import_failed, message)
                                        }
                                    )
                                }
                            }
                        },
                    )
                }
            }
        }
    }
}

@Immutable
private data class SillyTavernExtensionCompat(
    val name: String,
    val status: SillyTavernExtensionStatus,
    val description: String,
)

private enum class SillyTavernExtensionStatus(val label: String) {
    Native("Native"),
    Partial("Partial"),
    Unsupported("Unsupported"),
}

private val SillyTavernExtensionCatalog = listOf(
    SillyTavernExtensionCompat(
        name = "World Info / Lorebooks",
        status = SillyTavernExtensionStatus.Native,
        description = "Imported as native lorebooks with priority, depth, constant, probability, and group rules.",
    ),
    SillyTavernExtensionCompat(
        name = "Quick Replies",
        status = SillyTavernExtensionStatus.Native,
        description = "Imported as quick messages with native pipe, variable, send, chat-message, and /expression-set support.",
    ),
    SillyTavernExtensionCompat(
        name = "Regex Scripts",
        status = SillyTavernExtensionStatus.Native,
        description = "Runs on user/assistant text with JS regex compatibility normalization.",
    ),
    SillyTavernExtensionCompat(
        name = "Token Counter",
        status = SillyTavernExtensionStatus.Native,
        description = "Chat size and context counters are available from native chat tooling.",
    ),
    SillyTavernExtensionCompat(
        name = "Summarize",
        status = SillyTavernExtensionStatus.Native,
        description = "Long chats can be compressed into summary nodes without leaving the chat screen.",
    ),
    SillyTavernExtensionCompat(
        name = "Chat Vectorization",
        status = SillyTavernExtensionStatus.Partial,
        description = "Recent-chat reference and lorebook scanning cover common retrieval flows; vector stores are not one-to-one yet.",
    ),
    SillyTavernExtensionCompat(
        name = "Prompt Presets",
        status = SillyTavernExtensionStatus.Native,
        description = "Chat Completion and Text Completion presets bind into the prompt manager.",
    ),
    SillyTavernExtensionCompat(
        name = "Reasoning Presets",
        status = SillyTavernExtensionStatus.Native,
        description = "Official ST reasoning delimiters import and split model thinking from final replies.",
    ),
    SillyTavernExtensionCompat(
        name = "Author's Note / Depth Prompt",
        status = SillyTavernExtensionStatus.Native,
        description = "Character-card depth prompts import as native depth injections.",
    ),
    SillyTavernExtensionCompat(
        name = "Sprites / Character Expressions",
        status = SillyTavernExtensionStatus.Native,
        description = "Official sprite folders import as native expression images and can be selected manually, by Quick Reply, or after replies.",
    ),
    SillyTavernExtensionCompat(
        name = "Backgrounds",
        status = SillyTavernExtensionStatus.Native,
        description = "Official background assets import as the current character chat background.",
    ),
    SillyTavernExtensionCompat(
        name = "Image Captioning",
        status = SillyTavernExtensionStatus.Native,
        description = "Image attachments are transformed through the native multimodal/OCR pipeline before generation.",
    ),
    SillyTavernExtensionCompat(
        name = "Web Search / Weather / RSS",
        status = SillyTavernExtensionStatus.Partial,
        description = "Native web search tools exist; weather and RSS slash-command parity still need dedicated mappings.",
    ),
    SillyTavernExtensionCompat(
        name = "Variables / STScript",
        status = SillyTavernExtensionStatus.Partial,
        description = "Quick Reply variables and core STScript-style commands are supported; arbitrary browser extension APIs are not.",
    ),
    SillyTavernExtensionCompat(
        name = "TTS",
        status = SillyTavernExtensionStatus.Partial,
        description = "Native TTS providers exist, but full SillyTavern extension settings are not mapped one-to-one.",
    ),
    SillyTavernExtensionCompat(
        name = "Third-party browser JS extensions",
        status = SillyTavernExtensionStatus.Unsupported,
        description = "SillyTavern web extensions cannot be executed directly inside this native client.",
    ),
)

@Composable
private fun ExtensionCompatibilitySection(
    onOpenDocs: () -> Unit,
    onOpenOfficialExtensions: () -> Unit,
) {
    CardGroup(
        title = { Text(stringResource(R.string.st_resources_extension_section)) },
    ) {
        item(
            onClick = onOpenDocs,
            leadingContent = { Icon(HugeIcons.Puzzle, null) },
            headlineContent = { Text(stringResource(R.string.st_resources_open_extension_docs)) },
            supportingContent = { Text(stringResource(R.string.st_resources_open_extension_docs_desc)) },
        )
        item(
            onClick = onOpenOfficialExtensions,
            leadingContent = { Icon(HugeIcons.Puzzle, null) },
            headlineContent = { Text(stringResource(R.string.st_resources_open_official_extensions)) },
            supportingContent = { Text(stringResource(R.string.st_resources_open_official_extensions_desc)) },
        )
        SillyTavernExtensionCatalog.forEach { extension ->
            item(
                leadingContent = { Icon(HugeIcons.Puzzle, null) },
                headlineContent = { Text(extension.name) },
                supportingContent = { Text(extension.description) },
                trailingContent = {
                    val color = when (extension.status) {
                        SillyTavernExtensionStatus.Native -> MaterialTheme.colorScheme.primary
                        SillyTavernExtensionStatus.Partial -> MaterialTheme.colorScheme.tertiary
                        SillyTavernExtensionStatus.Unsupported -> MaterialTheme.colorScheme.outline
                    }
                    Text(
                        text = extension.status.label,
                        color = color,
                        style = MaterialTheme.typography.labelMedium,
                    )
                },
            )
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
            supportingContent = {
                Text(
                    asset.description
                        ?.takeIf { it.isNotBlank() }
                        ?: asset.type,
                )
            },
            trailingContent = {
                if (importing) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text(
                        stringResource(
                            if (asset.type.equals("extension", ignoreCase = true)) {
                                R.string.st_resources_open_asset
                            } else {
                                R.string.st_resources_import_asset
                            }
                        )
                    )
                }
            },
        )
    }
}
