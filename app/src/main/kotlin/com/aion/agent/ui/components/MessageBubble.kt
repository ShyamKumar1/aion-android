package com.aion.agent.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.aion.agent.ui.chat.ChatMessage

/**
 * One row in the chat scroll. The shape, alignment, and color change based
 * on whether the message is from the user, the assistant, a tool/system note,
 * or a confirmation prompt.
 *
 * Features:
 *  - Copy-to-clipboard on every message (tap copy icon)
 *  - Edit button on user messages (tap to fill input with that message)
 *  - Confirm/Cancel buttons on skill confirmation requests
 *  - Streaming indicator on in-progress assistant messages
 *  - Error text on failed messages
 */
@Composable
fun MessageBubble(
    message: ChatMessage,
    onConfirm: ((ChatMessage) -> Unit)? = null,
    onCancel: ((ChatMessage) -> Unit)? = null,
    onEdit: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val isUser = message.role == ChatMessage.Role.USER
    val bubbleColor = when (message.role) {
        ChatMessage.Role.USER -> MaterialTheme.colorScheme.primary
        ChatMessage.Role.ASSISTANT -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
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
        // Role label for assistant and system messages
        if (!isUser && message.role != ChatMessage.Role.TOOL) {
            Text(
                text = when (message.role) {
                    ChatMessage.Role.ASSISTANT -> "AION"
                    ChatMessage.Role.SYSTEM -> "System"
                    else -> ""
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 2.dp, start = 4.dp),
            )
        }

        // Message bubble with action buttons
        Row(verticalAlignment = Alignment.Bottom) {
            // For user messages: edit button on the left
            if (isUser && message.content.isNotBlank()) {
                SmallActionButton(
                    icon = Icons.Filled.Edit,
                    contentDesc = "Edit message",
                    onClick = { onEdit?.invoke(message.content) },
                )
                Spacer(Modifier.size(4.dp))
            }

            // The bubble itself
            Box(
                modifier = Modifier
                    .widthIn(max = 320.dp)
                    .clip(RoundedCornerShape(if (isUser) 16.dp else 16.dp))
                    .background(bubbleColor)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Text(
                    text = message.content.ifBlank { " " },
                    color = textColor,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            // Copy button on the right (every message)
            if (message.content.isNotBlank()) {
                Spacer(Modifier.size(4.dp))
                SmallActionButton(
                    icon = Icons.Filled.ContentCopy,
                    contentDesc = "Copy message",
                    onClick = copyToClipboard(context, message.content),
                )
            }
        }

        // Streaming indicator
        if (message.isStreaming) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = "●",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelSmall,
            )
        }

        // Confirmation buttons
        if (message.pendingConfirmation != null) {
            Spacer(Modifier.height(6.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(start = 4.dp),
            ) {
                Button(onClick = { onConfirm?.invoke(message) }) {
                    Text("Confirm")
                }
                OutlinedButton(onClick = { onCancel?.invoke(message) }) {
                    Text("Cancel")
                }
            }
        }

        // Error display
        if (message.error != null) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = message.error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
    }
}

@Composable
private fun SmallActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDesc: String,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(28.dp),
        colors = IconButtonDefaults.iconButtonColors(
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
        ),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDesc,
            modifier = Modifier.size(16.dp),
        )
    }
}

private fun copyToClipboard(context: Context, text: String): () -> Unit = {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("AION message", text)
    clipboard.setPrimaryClip(clip)
}
