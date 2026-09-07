package com.paymentslab.feature.lab

import androidx.compose.runtime.Immutable
import com.paymentslab.core.designsystem.GatewayStatusUi
import com.paymentslab.core.designsystem.RegionCount
import com.siddharth.kmp.mvi.StateViewModel
import com.siddharth.kmp.paymentsapi.Capability
import com.siddharth.kmp.paymentsapi.GatewayId
import com.siddharth.kmp.paymentsapi.PaymentGatewayRegistry
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.flow.StateFlow

/** One provider row on the Lab home — everything the card needs, no domain types leaking to UI. */
@Immutable
data class ProviderRow(
    val id: GatewayId,
    val displayName: String,
    val status: GatewayStatusUi,
    val region: String,
    val blurb: String,
    val capabilities: ImmutableList<String>,
)

/** A status-grouped slice of the filtered catalog, in the E1 display order. */
@Immutable
data class ProviderSection(
    val status: GatewayStatusUi,
    val label: String,
    val providers: ImmutableList<ProviderRow>,
)

/** Immutable state for [LabHomeScreen]: the full catalog plus the current search/filter selection. */
@Immutable
data class LabHomeUiState(
    val allProviders: ImmutableList<ProviderRow> = persistentListOf(),
    val searchQuery: String = "",
    val selectedStatuses: ImmutableSet<GatewayStatusUi> = persistentSetOf(),
    val selectedRegions: ImmutableSet<String> = persistentSetOf(),
) {
    /** Region tile sizes for [com.paymentslab.core.designsystem.RegionCoverageMap] — always
     *  computed over the FULL catalog, not the filtered result, so tapping a region never shrinks
     *  the map out from under itself. */
    val regionCounts: ImmutableList<RegionCount>
        get() =
            allProviders
                .groupingBy { it.region }
                .eachCount()
                .map { (region, count) -> RegionCount(region, count) }
                .sortedByDescending { it.count }
                .toImmutableList()

    private val filteredProviders: List<ProviderRow>
        get() =
            allProviders.filter { provider ->
                (selectedStatuses.isEmpty() || provider.status in selectedStatuses) &&
                    (selectedRegions.isEmpty() || provider.region in selectedRegions) &&
                    (
                        searchQuery.isBlank() ||
                            provider.displayName.contains(searchQuery, ignoreCase = true) ||
                            provider.region.contains(searchQuery, ignoreCase = true) ||
                            provider.blurb.contains(searchQuery, ignoreCase = true)
                    )
            }

    /** Filtered catalog grouped by status, in the "Sandbox ready → Mock mode → KYC gated → Coming
     *  soon" order from the plan's E1 spec; empty sections are dropped rather than shown blank. */
    val sections: ImmutableList<ProviderSection>
        get() {
            val grouped = filteredProviders.groupBy { it.status }
            return SECTION_ORDER
                .mapNotNull { status ->
                    grouped[status]?.let { rows ->
                        ProviderSection(
                            status = status,
                            label = status.sectionLabel(),
                            providers = rows.toImmutableList(),
                        )
                    }
                }.toImmutableList()
        }

    private companion object {
        val SECTION_ORDER =
            listOf(
                GatewayStatusUi.SANDBOX_READY,
                GatewayStatusUi.MOCK_MODE,
                GatewayStatusUi.KYC_GATED,
                GatewayStatusUi.COMING_SOON,
            )
    }
}

private fun GatewayStatusUi.sectionLabel(): String =
    when (this) {
        GatewayStatusUi.SANDBOX_READY -> "Sandbox ready"
        GatewayStatusUi.MOCK_MODE -> "Mock mode"
        GatewayStatusUi.KYC_GATED -> "KYC gated"
        GatewayStatusUi.COMING_SOON -> "Coming soon"
    }

/**
 * Reads the [PaymentGatewayRegistry] once and projects each registered gateway's catalog metadata
 * into a [ProviderRow]; owns the search/filter selection the E1 catalog UI needs (region map tap,
 * status chips, free-text search). The registry itself is a synchronous, in-memory contract, so the
 * base catalog never changes after construction — only the filter selection does.
 */
class LabHomeViewModel(
    registry: PaymentGatewayRegistry,
) : StateViewModel<LabHomeUiState>(
        LabHomeUiState(
            allProviders =
                registry.gateways
                    .map { gateway ->
                        ProviderRow(
                            id = gateway.id,
                            displayName = gateway.meta.displayName,
                            status = gateway.meta.status.toUi(),
                            region = gateway.meta.region,
                            blurb = gateway.meta.blurb,
                            capabilities =
                                gateway.meta.capabilities
                                    .map { it.label() }
                                    .toImmutableList(),
                        )
                    }.toImmutableList(),
        ),
    ) {
    val uiState: StateFlow<LabHomeUiState> get() = state

    fun onSearchQueryChange(query: String) {
        setState { copy(searchQuery = query) }
    }

    fun onToggleStatusFilter(status: GatewayStatusUi) {
        setState { copy(selectedStatuses = selectedStatuses.toggle(status)) }
    }

    fun onToggleRegionFilter(region: String) {
        setState { copy(selectedRegions = selectedRegions.toggle(region)) }
    }

    fun onClearFilters() {
        setState {
            copy(
                searchQuery = "",
                selectedStatuses = persistentSetOf(),
                selectedRegions = persistentSetOf(),
            )
        }
    }

    private companion object {
        const val TAG = "LabHomeViewModel"
    }
}

private fun <T> ImmutableSet<T>.toggle(value: T): ImmutableSet<T> =
    if (value in this) (this - value).toImmutableSet() else (this + value).toImmutableSet()

/** Human-readable capability label for a chip. */
private fun Capability.label(): String =
    when (this) {
        Capability.ONE_TIME_PAYMENT -> "One-time"
        Capability.UPI -> "UPI"
        Capability.CARDS -> "Cards"
        Capability.WALLET -> "Wallet"
        Capability.NET_BANKING -> "Net banking"
        Capability.REFUND -> "Refund"
        Capability.MANDATE -> "Mandate"
    }
