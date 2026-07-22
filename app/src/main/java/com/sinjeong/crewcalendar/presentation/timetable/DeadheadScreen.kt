package com.sinjeong.crewcalendar.presentation.timetable

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sinjeong.crewcalendar.domain.model.BundledTimetable

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DeadheadScreen(onBack: () -> Unit) {
    var holiday by remember { mutableStateOf(false) } // false=평일, true=휴일

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(BundledTimetable.TITLE) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Text(
                BundledTimetable.SUBTITLE,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
            SingleChoiceSegmentedButtonRow(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                SegmentedButton(
                    selected = !holiday,
                    onClick = { holiday = false },
                    shape = SegmentedButtonDefaults.itemShape(0, 2),
                ) { Text("평일") }
                SegmentedButton(
                    selected = holiday,
                    onClick = { holiday = true },
                    shape = SegmentedButtonDefaults.itemShape(1, 2),
                ) { Text("휴일") }
            }
            LazyColumn(Modifier.fillMaxSize()) {
                items(BundledTimetable.ROWS) { row ->
                    val stripe = row.hour % 2 == 0
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(
                                if (stripe) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                else MaterialTheme.colorScheme.surface
                            )
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "${row.hour}시",
                            modifier = Modifier.width(44.dp),
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                        )
                        FlowRow(
                            Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            (if (holiday) row.holiday else row.weekday).forEach { m ->
                                Text(
                                    "%02d".format(m),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontFeatureSettings = "tnum",
                                    ),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
