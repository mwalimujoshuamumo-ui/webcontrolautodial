package com.example.autodial.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ForwardToInbox
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.autodial.data.Transaction
import com.example.autodial.ui.theme.CardBackground
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TransactionItem(
    transaction: Transaction,
    isHidden: Boolean,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    isSelectionMode: Boolean = false,
    onLongClick: () -> Unit = {},
    onClick: () -> Unit = {},
    onMarkAsComplete: () -> Unit = {}
) {
    val dateStr = formatDateTime(transaction.date)
    val statusColor = when (transaction.status) {
        Transaction.STATUS_SUCCESS -> if (transaction.isVerified) Color(0xFF00E676) else Color(0xFF00E676).copy(alpha = 0.4f)
        Transaction.STATUS_FAILED -> Color.Red
        Transaction.STATUS_PROCESSING -> Color(0xFFFFA000)
        Transaction.STATUS_WAITING -> Color(0xFFFFA000)
        Transaction.STATUS_SCHEDULED -> Color(0xFF2196F3)
        Transaction.STATUS_RELAYED -> Color(0xFF00C853) // Green for Relayed
        Transaction.STATUS_SKIPPED -> Color.Gray
        Transaction.STATUS_DISABLED -> Color.Gray
        Transaction.STATUS_UNMATCHED -> Color.Gray
        else -> Color.Gray
    }

    val initials = if (transaction.senderName.isNotEmpty()) {
        val names = transaction.senderName.split(" ")
        if (names.size >= 2) {
            "${names[0].take(1)}${names[1].take(1)}".uppercase()
        } else {
            names[0].take(2).uppercase()
        }
    } else "MP"

    val maskedPhone = if (isHidden) {
        if (transaction.customerNumber.length >= 10) {
            transaction.customerNumber.take(4) + "*****" + transaction.customerNumber.takeLast(2)
        } else {
            "****"
        }
    } else transaction.customerNumber

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .combinedClickable(
                onLongClick = onLongClick,
                onClick = onClick
            ),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isSelected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                transaction.status == Transaction.STATUS_FAILED -> Color.Red.copy(alpha = 0.1f)
                else -> MaterialTheme.colorScheme.surface
            }
        ),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            // Left Color Stripe
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(4.dp)
                    .background(statusColor)
            )

            Row(
                modifier = Modifier
                    .padding(horizontal = 12.dp, vertical = 8.dp) // Reduced padding
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isSelectionMode) {
                    Icon(
                        imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                        contentDescription = null,
                        tint = if (isSelected) Color(0xFF00E676) else Color.Gray,
                        modifier = Modifier.padding(end = 12.dp)
                    )
                }

                // Initials Avatar
                Box(
                    modifier = Modifier
                        .size(36.dp) // Slightly smaller
                        .clip(CircleShape)
                        .background(statusColor.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = initials,
                        color = statusColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    // Name
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = transaction.senderName.ifEmpty { "M-PESA" }.uppercase(),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        
                        // Status Ticks
                        val tickIcon = when {
                            transaction.isVerified -> Icons.Default.DoneAll
                            transaction.status == Transaction.STATUS_SUCCESS -> Icons.Default.DoneAll
                            transaction.status == Transaction.STATUS_FAILED -> Icons.Default.Close
                            transaction.status == Transaction.STATUS_WAITING || 
                            transaction.status == Transaction.STATUS_PROCESSING -> Icons.Default.Check
                            else -> null
                        }
                        
                        val tickColor = when {
                            transaction.isVerified -> Color(0xFF2196F3) // Blue Tick (Verified)
                            transaction.status == Transaction.STATUS_SUCCESS -> Color(0xFF00C853) // Green Tick (Local Success)
                            transaction.status == Transaction.STATUS_FAILED -> Color.Red // Red Cross
                            else -> Color.Gray // Single Gray Tick
                        }

                        if (tickIcon != null) {
                            Spacer(Modifier.width(4.dp))
                            Icon(
                                imageVector = tickIcon,
                                contentDescription = "Status",
                                tint = tickColor,
                                modifier = Modifier.size(14.dp)
                            )
                        }

                        if (transaction.senderName.startsWith("RELAY:")) {
                            Spacer(Modifier.width(4.dp))
                            Surface(
                                color = Color(0xFF2196F3).copy(alpha = 0.1f),
                                shape = RoundedCornerShape(4.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2196F3).copy(alpha = 0.4f))
                            ) {
                                Text(
                                    "INCOMING", 
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                    fontSize = 8.sp, 
                                    fontWeight = FontWeight.Black, 
                                    color = Color(0xFF2196F3)
                                )
                            }
                        }

                        if (transaction.status == Transaction.STATUS_RELAYED) {
                            Spacer(Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ForwardToInbox,
                                contentDescription = "Relayed",
                                tint = Color(0xFF00C853),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                    
                    // Phone Number row
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Call,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            modifier = Modifier.size(10.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = maskedPhone,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }

                    // Offer row
                    Text(
                        text = "Ksh ${transaction.amount.toInt()} - ${transaction.offerName}",
                        fontSize = 12.sp,
                        color = Color(0xFF00E676), // Bright Green
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }

                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.SpaceBetween) {
                    // Status Badge with Verified Icon
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (transaction.isVerified) {
                            Icon(
                                imageVector = Icons.Default.Verified,
                                contentDescription = "Verified",
                                tint = Color(0xFF2196F3), // Blue Verified Color
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                        }
                        
                        Surface(
                            color = Color.Transparent,
                            border = androidx.compose.foundation.BorderStroke(1.dp, statusColor.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = transaction.status,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                color = statusColor,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))

                    // Time
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Schedule,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            modifier = Modifier.size(10.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = dateStr,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                }
            }
        }
    }
}

private fun formatDateTime(timestamp: Long): String {
    val now = Calendar.getInstance()
    val time = Calendar.getInstance().apply { timeInMillis = timestamp }
    
    return when {
        isSameDay(now, time) -> {
            val diff = now.timeInMillis - timestamp
            when {
                diff < 60000 -> "Just now"
                diff < 3600000 -> "${diff / 60000} Min ago"
                else -> SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(timestamp))
            }
        }
        isYesterday(time) -> "Yesterday"
        else -> SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(timestamp))
    }
}

private fun isSameDay(cal1: Calendar, cal2: Calendar): Boolean {
    return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
            cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
}

private fun isYesterday(then: Calendar): Boolean {
    val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
    return isSameDay(yesterday, then)
}
