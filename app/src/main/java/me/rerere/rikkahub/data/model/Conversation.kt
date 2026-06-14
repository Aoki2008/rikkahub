package me.rerere.rikkahub.data.model

import android.net.Uri
import androidx.core.net.toUri
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.ai.util.InstantSerializer
import me.rerere.rikkahub.data.datastore.DEFAULT_ASSISTANT_ID
import java.time.Instant
import kotlin.uuid.Uuid

@Serializable
data class Conversation(
    val id: Uuid = Uuid.random(),
    val assistantId: Uuid,
    val title: String = "",
    val messageNodes: List<MessageNode>,
    val chatSuggestions: List<String> = emptyList(),
    val isPinned: Boolean = false,
    @Serializable(with = InstantSerializer::class)
    val createAt: Instant = Instant.now(),
    @Serializable(with = InstantSerializer::class)
    val updateAt: Instant = Instant.now(),
    val customSystemPrompt: String? = null,
    val modeInjectionIds: Set<Uuid> = emptySet(),
    val lorebookIds: Set<Uuid> = emptySet(),
    // 非空时表示这是一个群聊(多角色)对话，引用 Settings.chatGroups 中的某个 ChatGroup。
    // 此时每轮回复由群聊激活策略选择成员发言，assistantId 取首位成员(供既有单人路径回退使用)。
    val groupId: Uuid? = null,
    @Transient
    val newConversation: Boolean = false
) {
    val files: List<Uri>
        get() = messageNodes
            .flatMap { node -> node.messages.flatMap { it.parts } }
            .collectAllParts()
            .mapNotNull { it.fileUri() }

    /**
     *  当前选中的 message
     */
    val currentMessages
        get(): List<UIMessage> {
            return messageNodes.map { node -> node.messages[node.selectIndex] }
        }

    fun getMessageNodeByMessage(message: UIMessage): MessageNode? {
        return messageNodes.firstOrNull { node -> node.messages.contains(message) }
    }

    fun getMessageNodeByMessageId(messageId: Uuid): MessageNode? {
        return messageNodes.firstOrNull { node -> node.messages.any { it.id == messageId } }
    }

    fun updateCurrentMessages(messages: List<UIMessage>): Conversation {
        val newNodes = this.messageNodes.toMutableList()

        messages.forEachIndexed { index, message ->
            val node = newNodes
                .getOrElse(index) { message.toMessageNode() }

            val newMessages = node.messages.toMutableList()
            var newMessageIndex = node.selectIndex
            if (newMessages.any { it.id == message.id }) {
                newMessages[newMessages.indexOfFirst { it.id == message.id }] = message
            } else {
                newMessages.add(message)
                newMessageIndex = newMessages.lastIndex
            }

            val newNode = node.copy(
                messages = newMessages,
                selectIndex = newMessageIndex
            )

            // 更新newNodes
            if (index > newNodes.lastIndex) {
                newNodes.add(newNode)
            } else {
                newNodes[index] = newNode
            }
        }

        return this.copy(
            messageNodes = newNodes
        )
    }

    companion object {
        fun ofId(
            id: Uuid,
            assistantId: Uuid = DEFAULT_ASSISTANT_ID,
            messages: List<MessageNode> = emptyList(),
            groupId: Uuid? = null,
            newConversation: Boolean = false
        ) = Conversation(
            id = id,
            assistantId = assistantId,
            messageNodes = messages,
            groupId = groupId,
            newConversation = newConversation,
        )
    }
}

@Serializable
data class MessageNode(
    val id: Uuid = Uuid.random(),
    val messages: List<UIMessage>,
    val selectIndex: Int = 0,
    @Transient
    val isFavorite: Boolean = false,
) {
    val currentMessage get() = if (messages.isEmpty() || selectIndex !in messages.indices) {
        throw IllegalStateException("MessageNode has no valid current message: messages.size=${messages.size}, selectIndex=$selectIndex")
    } else {
        messages[selectIndex]
    }

    val role get() = messages.firstOrNull()?.role ?: MessageRole.USER

    companion object {
        fun of(message: UIMessage) = MessageNode(
            messages = listOf(message),
            selectIndex = 0
        )
    }
}

fun UIMessage.toMessageNode(): MessageNode {
    return MessageNode(
        messages = listOf(this),
        selectIndex = 0
    )
}

/**
 * 构建新对话的初始消息节点。
 *
 * 对应 SillyTavern 的"备选问候语"：第一条 ASSISTANT 预设消息（问候语）会与
 * alternateGreetings 合并到同一个 [MessageNode] 中，使其可像普通消息一样左右滑动切换。
 * selectIndex 默认为 0（即原始 first_mes）。
 */
fun buildInitialMessageNodes(
    presetMessages: List<UIMessage>,
    alternateGreetings: List<String>,
): List<MessageNode> {
    var greetingExpanded = false
    return presetMessages.map { message ->
        if (!greetingExpanded &&
            message.role == MessageRole.ASSISTANT &&
            alternateGreetings.isNotEmpty()
        ) {
            greetingExpanded = true
            MessageNode(
                messages = listOf(message) + alternateGreetings.map { UIMessage.assistant(it) },
                selectIndex = 0,
            )
        } else {
            message.toMessageNode()
        }
    }
}

/**
 * 群聊初始问候语来源：每个成员的预设消息 + 备选问候语。
 */
data class GroupGreetingMember(
    val assistantId: Uuid,
    val presetMessages: List<UIMessage>,
    val alternateGreetings: List<String>,
)

/**
 * 构建群聊新对话的初始问候节点。
 *
 * 对应 SillyTavern 群聊会展示各成员的问候语：为每个成员复用单人问候逻辑
 * （first_mes + alternate_greetings 合并为可左右滑动的节点），仅保留其中的
 * ASSISTANT 问候节点，并为消息打上 [UIMessage.senderId] 以便归属到对应成员
 * （头像/名称）。没有问候语的成员会被跳过。
 */
fun buildGroupInitialNodes(
    members: List<GroupGreetingMember>,
): List<MessageNode> = members.flatMap { member ->
    buildInitialMessageNodes(member.presetMessages, member.alternateGreetings)
        .filter { node -> node.role == MessageRole.ASSISTANT }
        .map { node ->
            node.copy(messages = node.messages.map { it.copy(senderId = member.assistantId) })
        }
}

/**
 * 递归展开所有 parts，包括工具调用结果中的嵌套 parts。
 */
private fun List<UIMessagePart>.collectAllParts(): List<UIMessagePart> =
    this + filterIsInstance<UIMessagePart.Tool>().flatMap { it.output.collectAllParts() }

/**
 * 提取 part 中引用的本地文件 URI，新增文件类型时只需在此处添加。
 */
private fun UIMessagePart.fileUri(): Uri? = when (this) {
    is UIMessagePart.Image -> url.takeIf { it.startsWith("file://") }?.toUri()
    is UIMessagePart.Document -> url.takeIf { it.startsWith("file://") }?.toUri()
    is UIMessagePart.Video -> url.takeIf { it.startsWith("file://") }?.toUri()
    is UIMessagePart.Audio -> url.takeIf { it.startsWith("file://") }?.toUri()
    else -> null
}
