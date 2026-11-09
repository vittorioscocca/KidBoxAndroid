package it.vittorioscocca.kidbox.ui.screens.settings

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import it.vittorioscocca.kidbox.R
import it.vittorioscocca.kidbox.data.crypto.KeyHandoff
import it.vittorioscocca.kidbox.util.KBLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private sealed interface HandoffPhase {
    data object Scanning : HandoffPhase
    data object Busy : HandoffPhase
    data class Confirm(val target: KeyHandoff.Target) : HandoffPhase
    data object Sent : HandoffPhase
    data class Failed(val message: Int) : HandoffPhase
}

/**
 * Impostazioni › Sicurezza › «Inquadra un QR»: il lato di chi MANDA la chiave
 * ([KeyHandoff]). Gemello di `KeyHandoffScanView.swift`. Dice cosa sta per
 * mandare e a chi, e manda solo dopo il sì.
 */
@Composable
fun KeyHandoffScanDialog(familyIds: List<String>, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val uid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
    var phase by remember { mutableStateOf<HandoffPhase>(HandoffPhase.Scanning) }
    var scanKey by remember { mutableIntStateOf(0) }
    var cameraOk by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var cameraAsked by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        cameraOk = it; cameraAsked = true
    }
    LaunchedEffect(Unit) { if (!cameraOk) permission.launch(Manifest.permission.CAMERA) }

    fun check(code: String) {
        phase = HandoffPhase.Busy
        scope.launch {
            phase = try {
                HandoffPhase.Confirm(withContext(Dispatchers.IO) { KeyHandoff.prepare(context, code, uid, familyIds) })
            } catch (e: KeyHandoff.BadQrException) {
                HandoffPhase.Failed(R.string.handoff_err_badqr)
            } catch (e: KeyHandoff.ExpiredException) {
                HandoffPhase.Failed(R.string.handoff_err_expired)
            } catch (e: KeyHandoff.NothingToSendException) {
                HandoffPhase.Failed(R.string.handoff_err_nothing)
            } catch (e: Exception) {
                KBLog.crypto.error("KeyHandoff: controllo fallito ${e.javaClass.simpleName} ${e.message}", "KeyHandoff")
                HandoffPhase.Failed(R.string.kv_error)
            }
        }
    }

    fun send(t: KeyHandoff.Target) {
        phase = HandoffPhase.Busy
        scope.launch {
            phase = try {
                withContext(Dispatchers.IO) { KeyHandoff.deliver(context, t, uid) }
                HandoffPhase.Sent
            } catch (e: Exception) {
                KBLog.crypto.error("KeyHandoff: invio fallito ${e.javaClass.simpleName} ${e.message}", "KeyHandoff")
                HandoffPhase.Failed(R.string.handoff_err_expired)
            }
        }
    }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(dismissOnClickOutside = false)) {
        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface) {
            Column(
                Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                when (val p = phase) {
                    HandoffPhase.Scanning -> {
                        Text(stringResource(R.string.handoff_scan), style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        if (cameraOk) {
                            Box(Modifier.fillMaxWidth().height(320.dp).clip(RoundedCornerShape(16.dp))) {
                                androidx.compose.runtime.key(scanKey) {
                                    QRScannerView(onQRDetected = { code -> if (phase == HandoffPhase.Scanning) check(code) })
                                }
                            }
                        } else if (cameraAsked) {
                            Text(stringResource(R.string.handoff_camera_denied), textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.error)
                        }
                        Text(stringResource(R.string.handoff_scan_lead), style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(onClick = onClose) { Text(stringResource(R.string.handoff_close)) }
                    }

                    HandoffPhase.Busy -> CircularProgressIndicator(Modifier.padding(32.dp))

                    is HandoffPhase.Confirm -> {
                        val t = p.target
                        Icon(Icons.Filled.Key, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(44.dp))
                        val title = if (t.kind == "rk") stringResource(R.string.handoff_send_rk_title, t.receiver)
                        else stringResource(R.string.handoff_send_family_title, t.familyName.orEmpty(), t.receiver)
                        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurface)
                        Text(stringResource(if (t.kind == "rk") R.string.handoff_send_rk_body else R.string.handoff_send_family_body),
                            textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Button(onClick = { send(t) }, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.handoff_send_cta))
                        }
                        TextButton(onClick = onClose) { Text(stringResource(R.string.kv_cancel)) }
                    }

                    HandoffPhase.Sent -> {
                        Icon(Icons.Filled.CheckCircle, null, tint = Color(0xFF2E9D4F), modifier = Modifier.size(44.dp))
                        Text(stringResource(R.string.handoff_sent_title), style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        Text(stringResource(R.string.handoff_sent_body), textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Button(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.handoff_done)) }
                    }

                    is HandoffPhase.Failed -> {
                        Icon(Icons.Filled.Warning, null, tint = Color(0xFFE08A00), modifier = Modifier.size(44.dp))
                        Text(stringResource(p.message), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurface)
                        Button(onClick = { scanKey++; phase = HandoffPhase.Scanning }, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.handoff_retry))
                        }
                        TextButton(onClick = onClose) { Text(stringResource(R.string.handoff_close)) }
                    }
                }
            }
        }
    }
}
