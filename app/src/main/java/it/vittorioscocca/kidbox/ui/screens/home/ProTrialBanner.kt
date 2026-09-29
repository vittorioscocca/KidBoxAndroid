package it.vittorioscocca.kidbox.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.vittorioscocca.kidbox.R
import it.vittorioscocca.kidbox.ui.theme.kidBoxColors

private val TrialTint = Color(0xFF2563EB)

/**
 * Banner in Home durante la prova Pro: quanti giorni restano e un tocco per
 * vedere i piani. La prova la concede il server (functions/proTrial.js).
 * Gemello di ProTrialBanner.swift su iOS.
 */
@Composable
fun ProTrialBanner(daysLeft: Int, onClick: () -> Unit) {
    val kb = MaterialTheme.kidBoxColors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(TrialTint.copy(alpha = 0.10f))
            .clickable(onClick = onClick)
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Star, contentDescription = null, tint = TrialTint, modifier = Modifier.size(26.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                if (daysLeft == 1) {
                    stringResource(R.string.trial_home_title_last)
                } else {
                    stringResource(R.string.trial_home_title_days, daysLeft)
                },
                color = kb.title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                stringResource(R.string.trial_home_body),
                color = kb.subtitle,
                fontSize = 12.sp,
                lineHeight = 16.sp,
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = kb.subtitle,
        )
    }
}
