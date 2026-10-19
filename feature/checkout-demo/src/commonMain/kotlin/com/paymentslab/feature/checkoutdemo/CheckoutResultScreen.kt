package com.paymentslab.feature.checkoutdemo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.paymentslab.core.designsystem.DesignTokens
import com.paymentslab.core.designsystem.PrimaryButton
import com.paymentslab.core.designsystem.TerminalMark
import com.paymentslab.core.designsystem.TerminalMarkKind

/**
 * The full-screen terminal page for a checkout. Stateless: the VM only supplies a [CheckoutResult]
 * once the server has answered, so this page never shows success on a client SDK callback alone.
 * Actions follow the outcome: success has Done; failure has Retry and Change method; pending has
 * Done only, because paying again could charge twice.
 */
@Composable
fun CheckoutResultScreen(
    result: CheckoutResult,
    onDone: () -> Unit,
    onRetry: () -> Unit,
    onChangeMethod: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val (kind, headline) =
        when (result.outcome) {
            CheckoutOutcome.SUCCESS -> TerminalMarkKind.Success to "Payment successful"
            CheckoutOutcome.FAILURE -> TerminalMarkKind.Failure to "Payment not completed"
            CheckoutOutcome.PENDING_VERIFICATION -> TerminalMarkKind.Pending to "Waiting for confirmation"
        }
    Scaffold(
        modifier = modifier,
        bottomBar = { ResultActions(result.outcome, onDone, onRetry, onChangeMethod) },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = DesignTokens.Spacing.lg)
                    .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.md, Alignment.CenterVertically),
        ) {
            TerminalMark(kind = kind)
            Column(
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.xs),
            ) {
                Text(text = headline, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(
                    text = result.amountLabel,
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                )
                result.reason?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            ReceiptCard(result)
        }
    }
}

@Composable
private fun ReceiptCard(result: CheckoutResult) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = DesignTokens.Elevation.card),
    ) {
        Column(
            modifier = Modifier.padding(DesignTokens.Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.sm),
        ) {
            ReceiptLine(label = "Gateway", value = result.gatewayName)
            result.orderId?.let { ReceiptLine(label = "Order ID", value = it, copyable = true) }
            result.paymentId?.let { ReceiptLine(label = "Payment ID", value = it, copyable = true) }
            ReceiptLine(label = "Time (UTC)", value = formatUtc(result.completedAtMs))
        }
    }
}

@Composable
private fun ReceiptLine(
    label: String,
    value: String,
    copyable: Boolean = false,
) {
    val clipboard = LocalClipboardManager.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            if (copyable) {
                TextButton(onClick = { clipboard.setText(AnnotatedString(value)) }) { Text("Copy") }
            }
        }
    }
}

@Composable
private fun ResultActions(
    outcome: CheckoutOutcome,
    onDone: () -> Unit,
    onRetry: () -> Unit,
    onChangeMethod: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
                .padding(horizontal = DesignTokens.Spacing.lg, vertical = DesignTokens.Spacing.md),
        verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.sm),
    ) {
        when (outcome) {
            CheckoutOutcome.FAILURE -> {
                PrimaryButton(text = "Retry", onClick = onRetry, modifier = Modifier.fillMaxWidth())
                OutlinedButton(
                    onClick = onChangeMethod,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(DesignTokens.Radius.md),
                ) { Text("Change method", fontWeight = FontWeight.SemiBold) }
            }
            CheckoutOutcome.SUCCESS, CheckoutOutcome.PENDING_VERIFICATION ->
                PrimaryButton(text = "Done", onClick = onDone, modifier = Modifier.fillMaxWidth())
        }
    }
}

/** `yyyy-MM-dd HH:mm` in UTC from epoch milliseconds, without a date-time dependency (civil-from-days). */
@Suppress("MagicNumber") // Hinnant's civil-from-days constants, kept literal so it reads against the source.
internal fun formatUtc(epochMs: Long): String {
    val secs = epochMs.floorDiv(1000L)
    val days = secs.floorDiv(86_400L)
    val secOfDay = secs.mod(86_400L).toInt()
    val z = days + 719_468
    val era = z.floorDiv(146_097L)
    val doe = (z - era * 146_097).toInt()
    val yoe = (doe - doe / 1_460 + doe / 36_524 - doe / 146_096) / 365
    val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
    val mp = (5 * doy + 2) / 153
    val day = doy - (153 * mp + 2) / 5 + 1
    val month = if (mp < 10) mp + 3 else mp - 9
    val year = (yoe + era * 400 + if (month <= 2) 1 else 0).toInt()

    fun Int.p2() = toString().padStart(2, '0')
    return "$year-${month.p2()}-${day.p2()} ${(secOfDay / 3_600).p2()}:${(secOfDay % 3_600 / 60).p2()}"
}
