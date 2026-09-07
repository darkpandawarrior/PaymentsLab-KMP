package com.paymentslab.feature.lab

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paymentslab.core.designsystem.DesignTokens
import com.paymentslab.core.designsystem.GatewayBrandAsset
import com.paymentslab.core.designsystem.GatewayBranding
import com.paymentslab.core.designsystem.GatewayStatusBadge
import com.paymentslab.core.designsystem.GatewayStatusUi
import com.paymentslab.core.designsystem.LabScaffold
import com.paymentslab.core.designsystem.RegionCoverageMap
import com.paymentslab.core.designsystem.SectionHeader
import com.siddharth.kmp.paymentsapi.GatewayId
import kotlinx.collections.immutable.ImmutableList
import org.koin.compose.viewmodel.koinViewModel

/** Stateful entry point: resolves the ViewModel and hands its state to the stateless [LabHomeScreen]. */
@Composable
fun LabHomeRoot(
    onOpenProvider: (GatewayId) -> Unit,
    onOpenSettings: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    viewModel: LabHomeViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LabHomeScreen(
        state = state,
        onSearchQueryChange = viewModel::onSearchQueryChange,
        onToggleStatusFilter = viewModel::onToggleStatusFilter,
        onToggleRegionFilter = viewModel::onToggleRegionFilter,
        onClearFilters = viewModel::onClearFilters,
        onOpenProvider = onOpenProvider,
        onOpenSettings = onOpenSettings,
        modifier = modifier,
    )
}

/** Stateless catalog of payment providers — search, region map, status chips, sectioned rows. */
@Composable
fun LabHomeScreen(
    state: LabHomeUiState,
    onOpenProvider: (GatewayId) -> Unit,
    onOpenSettings: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    onSearchQueryChange: (String) -> Unit = {},
    onToggleStatusFilter: (GatewayStatusUi) -> Unit = {},
    onToggleRegionFilter: (String) -> Unit = {},
    onClearFilters: () -> Unit = {},
) {
    val hasActiveFilters =
        state.searchQuery.isNotBlank() || state.selectedStatuses.isNotEmpty() || state.selectedRegions.isNotEmpty()

    LabScaffold(
        title = "Integration Lab",
        actions = {
            // Only Android wires a real onOpenSettings today (AiSettingsViewModel needs
            // ModelManager/OnDeviceLlm/SecureKeyStore, unbound on iOS/web) — render the gear
            // only where it does something, rather than showing dead chrome everywhere.
            if (onOpenSettings != null) {
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Filled.Settings, contentDescription = "AI settings")
                }
            }
        },
    ) { padding ->
        LazyColumn(
            modifier =
                modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = DesignTokens.Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.md),
        ) {
            item {
                SectionHeader(text = "Coverage")
                RegionCoverageMap(
                    regions = state.regionCounts,
                    selectedRegions = state.selectedRegions,
                    onToggleRegion = onToggleRegionFilter,
                    modifier = Modifier.padding(bottom = DesignTokens.Spacing.md),
                )
            }
            item {
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = onSearchQueryChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search providers, regions…") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                )
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StatusFilterChips(
                        selected = state.selectedStatuses,
                        onToggle = onToggleStatusFilter,
                        modifier = Modifier.weight(1f),
                    )
                    if (hasActiveFilters) {
                        TextButton(onClick = onClearFilters) { Text("Clear") }
                    }
                }
            }
            if (state.sections.isEmpty()) {
                item {
                    Text(
                        text = "No providers match the current filters.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = DesignTokens.Spacing.xl),
                    )
                }
            }
            state.sections.forEach { section ->
                item(key = "header_${section.status.name}") {
                    SectionHeader(text = "${section.label} (${section.providers.size})")
                }
                items(section.providers, key = { it.id.value }) { provider ->
                    ProviderCard(
                        provider = provider,
                        onClick = { onOpenProvider(provider.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusFilterChips(
    selected: Set<GatewayStatusUi>,
    onToggle: (GatewayStatusUi) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.sm),
    ) {
        GatewayStatusUi.entries.forEach { status ->
            FilterChip(
                selected = status in selected,
                onClick = { onToggle(status) },
                label = { Text(status.chipLabel()) },
            )
        }
    }
}

private fun GatewayStatusUi.chipLabel(): String =
    when (this) {
        GatewayStatusUi.SANDBOX_READY -> "Sandbox ready"
        GatewayStatusUi.MOCK_MODE -> "Mock mode"
        GatewayStatusUi.KYC_GATED -> "KYC gated"
        GatewayStatusUi.COMING_SOON -> "Coming soon"
    }

@Composable
private fun ProviderCard(
    provider: ProviderRow,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(
        modifier =
            modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = DesignTokens.Elevation.card),
    ) {
        Column(
            modifier = Modifier.padding(DesignTokens.Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.sm),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    GatewayBrandBadge(id = provider.id.value, displayName = provider.displayName)
                    Text(
                        text = provider.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                GatewayStatusBadge(status = provider.status)
            }
            Text(
                text = provider.blurb,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = provider.region,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            CapabilityChips(capabilities = provider.capabilities)
        }
    }
}

@Composable
private fun GatewayBrandBadge(
    id: String,
    displayName: String,
    modifier: Modifier = Modifier,
) {
    when (val asset = GatewayBranding.forId(id, displayName)) {
        is GatewayBrandAsset.Logo ->
            Icon(
                imageVector = asset.imageVector,
                contentDescription = null,
                modifier = modifier.size(24.dp),
                tint = Color.Unspecified,
            )
        is GatewayBrandAsset.Monogram ->
            Surface(
                modifier = modifier.size(24.dp),
                shape = CircleShape,
                color = asset.color,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = asset.letter.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                }
            }
    }
}

@Composable
private fun CapabilityChips(
    capabilities: ImmutableList<String>,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.xs),
    ) {
        capabilities.forEach { label ->
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = MaterialTheme.shapes.small,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier =
                        Modifier.padding(
                            horizontal = DesignTokens.Spacing.sm,
                            vertical = DesignTokens.Spacing.xs,
                        ),
                )
            }
        }
    }
}
