package com.example.jkt48bonusmonitor.ui.home

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.jkt48bonusmonitor.data.history.HistoryEntry
import com.example.jkt48bonusmonitor.domain.model.MemberSlot
import com.example.jkt48bonusmonitor.domain.model.MonitoringStatus
import com.example.jkt48bonusmonitor.notification.QUOTA_THRESHOLD
import com.example.jkt48bonusmonitor.ui.theme.Green
import com.example.jkt48bonusmonitor.ui.theme.Muted
import com.example.jkt48bonusmonitor.ui.theme.Orange
import com.example.jkt48bonusmonitor.ui.theme.Pink40
import com.example.jkt48bonusmonitor.ui.theme.Red

@Composable
fun HomeScreen(viewModel: HomeViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val history by viewModel.history.collectAsState()
    val exclusiveIdInput by viewModel.exclusiveIdInput.collectAsState()
    val exclusiveIdError by viewModel.exclusiveIdError.collectAsState()
    val focusManager = LocalFocusManager.current
    var showClearHistoryDialog by remember { mutableStateOf(false) }

    val notifPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    if (showClearHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showClearHistoryDialog = false },
            title = { Text("Clear History") },
            text = { Text("Delete all monitoring history? This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = { viewModel.clearHistory(); showClearHistoryDialog = false }) {
                    Text("Clear", color = Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearHistoryDialog = false }) { Text("Cancel") }
            }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { HeaderSection() }

        item {
            ExclusiveIdCard(
                value = exclusiveIdInput,
                error = exclusiveIdError,
                onValueChange = viewModel::onExclusiveIdChange,
                onDone = { focusManager.clearFocus() }
            )
        }

        item {
            MonitoringStatusCard(
                monitoringStatus = uiState.monitoringStatus,
                lastCheckTimestamp = uiState.lastCheckTimestamp,
                lastRequestStatus = uiState.lastRequestStatus
            )
        }

        item {
            ControlButtonsCard(
                isMonitoring = uiState.monitoringStatus == MonitoringStatus.ACTIVE,
                isLoading = uiState.isLoading,
                onStart = { focusManager.clearFocus(); viewModel.startMonitoring() },
                onStop = viewModel::stopMonitoring,
                onCheckNow = { focusManager.clearFocus(); viewModel.checkNow() }
            )
        }

        // ── Live Slot Cards (shown after CHECK NOW) ──────────────────
        if (uiState.slots.isNotEmpty()) {
            item {
                Text(
                    "Astrella's Slots (${uiState.slots.size} found)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            items(uiState.slots) { slot -> SlotCard(slot = slot) }
        }

        // ── History ──────────────────────────────────────────────────
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Monitoring History",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                if (history.isNotEmpty()) {
                    TextButton(onClick = { showClearHistoryDialog = true }) {
                        Text("Clear", color = Red, fontSize = 13.sp)
                    }
                }
            }
        }

        if (history.isEmpty()) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No history yet. Tap CHECK NOW to start.", color = Muted)
                }
            }
        } else {
            items(history) { entry -> HistoryEntryCard(entry = entry) }
        }
    }
}

@Composable
private fun HeaderSection() {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(
            "JKT48 Bonus Monitor",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = Pink40
        )
        Text(
            "Monitor Astrella Virgiananda's bonus quota",
            style = MaterialTheme.typography.bodyMedium,
            color = Muted
        )
    }
}

@Composable
private fun ExclusiveIdCard(
    value: String, error: String?,
    onValueChange: (String) -> Unit, onDone: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Exclusive ID", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = value, onValueChange = onValueChange,
                label = { Text("Exclusive ID (e.g. EX79CB)") },
                isError = error != null,
                supportingText = if (error != null) { { Text(error, color = Red) } } else null,
                singleLine = true, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { onDone() })
            )
        }
    }
}

@Composable
private fun MonitoringStatusCard(
    monitoringStatus: MonitoringStatus,
    lastCheckTimestamp: String?,
    lastRequestStatus: String?
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Monitoring Status", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            StatusRow("Status", monitoringStatus.name,
                valueColor = if (monitoringStatus == MonitoringStatus.ACTIVE) Green else Muted)
            Divider(modifier = Modifier.padding(vertical = 6.dp))
            StatusRow("Last Check", lastCheckTimestamp ?: "Never")
            Divider(modifier = Modifier.padding(vertical = 6.dp))
            StatusRow("API Status", lastRequestStatus ?: "—")
        }
    }
}

@Composable
private fun StatusRow(label: String, value: String, valueColor: Color = Color.Unspecified) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Muted, style = MaterialTheme.typography.bodySmall)
        Text(value, style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium, color = valueColor)
    }
}

@Composable
private fun ControlButtonsCard(
    isMonitoring: Boolean, isLoading: Boolean,
    onStart: () -> Unit, onStop: () -> Unit, onCheckNow: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Controls", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onStart, enabled = !isMonitoring, modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Green)) {
                    Text("▶ START", fontSize = 12.sp)
                }
                Button(onClick = onStop, enabled = isMonitoring, modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Red)) {
                    Text("■ STOP", fontSize = 12.sp)
                }
            }
            OutlinedButton(onClick = onCheckNow, enabled = !isLoading, modifier = Modifier.fillMaxWidth()) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Checking…")
                } else {
                    Text("🔄  CHECK NOW")
                }
            }
        }
    }
}

@Composable
fun SlotCard(slot: MemberSlot) {
    val (color, label) = when {
        slot.availableQuota == 0 -> Red to "Sold Out"
        slot.availableQuota < QUOTA_THRESHOLD -> Orange to "Low ⚠"
        else -> Green to "Normal"
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${slot.sessionDate}  ${slot.sessionLabel}  (${slot.slotLabel})",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "${slot.sessionStartTime}  •  ${slot.sessionCode}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Muted
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "Quota: ${slot.availableQuota}",
                    fontWeight = FontWeight.Bold,
                    color = color,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(label, style = MaterialTheme.typography.labelSmall, color = color)
            }
        }
    }
}

@Composable
fun HistoryEntryCard(entry: HistoryEntry) {
    val (statusColor, statusLabel) = when (entry.status) {
        "SOLD_OUT" -> Red to "Sold Out"
        "LOW" -> Orange to "Low"
        "ERROR" -> Muted to "Error"
        else -> Green to "Normal"
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(1.dp),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(entry.timestamp, style = MaterialTheme.typography.bodySmall, color = Muted)
                val sessionInfo = if (entry.sessionDate.isNotEmpty())
                    "${entry.sessionDate} ${entry.sessionLabel} (${entry.slotLabel})"
                else entry.exclusiveId
                Text(sessionInfo, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.width(12.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "Quota: ${entry.availableQuota}",
                    fontWeight = FontWeight.Bold,
                    color = statusColor,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(statusLabel, style = MaterialTheme.typography.labelSmall, color = statusColor)
            }
        }
    }
}
