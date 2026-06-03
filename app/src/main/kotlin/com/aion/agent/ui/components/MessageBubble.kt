package com.aion.agent.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.aion.agent.ui.chat.ChatMessage

/**
 * One row in the chat scroll. The shape, alignment, and color change based
 * on whether the message is from the user, the assistant, a tool/system note,
 * or a confirmation prompt.
 */
@Composable
fun MessageBubble(
    message: ChatMessage,
    onConfirm: ((ChatMessage) -> Unit)? = null,
    onCancel: ((ChatMessage) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val isUser = message.role == ChatMessage.Role.USER
    val bubbleColor = when (message.role) {
        ChatMessage.Role.USER -> MaterialTheme.colorScheme.primary
        ChatMessage.Role.ASSISTANT -> MaterialTheme.colorScheme.surface
        ChatMessage.Role.SYSTEM -> MaterialTheme.colorScheme.surfaceVariant
        ChatMessage.Role.TOOL -> MaterialTheme.colorScheme.surfaceVariant
    }
    val textColor = when (message.role) {
        ChatMessage.Role.USER -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onSurface
    }
    val align = if (isUser) Alignment.End else Alignment.Start

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalAlignment = align,
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(bubbleColor)
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Text(
                text = message.content.ifBlank { " " },
                color = textColor,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (message.isStreaming) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = "●",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelSmall,
            )
        }
        if (message.pendingConfirmation != null) {
            Spacer(Modifier.height(6.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(onClick = { onConfirm?.invoke(message) }) {
                    Text("Confirm")
                }
                OutlinedButton(onClick = { onCancel?.invoke(message) }) {
                    Text("Cancel")
                }
            }
        }
        if (message.error != null) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = message.error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}
