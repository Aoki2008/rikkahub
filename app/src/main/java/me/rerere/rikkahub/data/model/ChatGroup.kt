package me.rerere.rikkahub.data.model

import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

/**
 * 群聊（多角色对话）
 *
 * 对应 SillyTavern 的 Group Chat：一个群聊引用多个角色(助手)，
 * 每轮回复按激活策略选择由哪些成员发言。成员使用各自的角色定义
 * (系统提示词/世界书/人设等)生成回复。
 */
@Serializable
data class ChatGroup(
    val id: Uuid = Uuid.random(),
    val name: String = "",
    val avatar: Avatar = Avatar.Dummy,
    val memberIds: List<Uuid> = emptyList(),        // 成员助手 ID，按顺序
    val mutedMemberIds: Set<Uuid> = emptySet(),     // 被静音(不参与发言)的成员
    val activationStrategy: GroupActivationStrategy = GroupActivationStrategy.NATURAL,
    val allowSelfResponses: Boolean = false,         // 是否允许角色连续对自己发言
)

/**
 * 群聊激活策略（对应 SillyTavern 的 activation strategy）
 */
@Serializable
enum class GroupActivationStrategy {
    /** 自然：被最新消息提及的成员发言；无提及时按轮转选一位 */
    NATURAL,

    /** 列表：按成员顺序轮流发言 */
    LIST,

    /** 手动：由用户手动选择发言成员 */
    MANUAL,
}

/**
 * 群聊中当前启用(未静音且存在)的成员，保持顺序。
 */
fun ChatGroup.activeMembers(): List<Uuid> =
    memberIds.filter { it !in mutedMemberIds }

/**
 * 选择本轮发言成员（纯函数，便于测试）。
 *
 * @param memberNames 成员 ID → 名称，用于 NATURAL 策略的提及检测
 * @param lastMessageText 最新一条消息文本（用于提及检测）
 * @param lastSpeakerId 上一位发言成员（用于轮转与防止自言自语）
 * @return 本轮应发言的成员 ID(有序)；MANUAL 策略返回空列表(交由 UI 决定)
 */
fun ChatGroup.selectResponders(
    memberNames: Map<Uuid, String>,
    lastMessageText: String,
    lastSpeakerId: Uuid?,
): List<Uuid> {
    val active = activeMembers()
    if (active.isEmpty()) return emptyList()

    val raw = when (activationStrategy) {
        GroupActivationStrategy.MANUAL -> emptyList()

        GroupActivationStrategy.LIST -> listOfNotNull(nextInRotation(active, lastSpeakerId))

        GroupActivationStrategy.NATURAL -> {
            val mentioned = active.filter { id ->
                val name = memberNames[id]?.takeIf { it.isNotBlank() } ?: return@filter false
                lastMessageText.contains(name, ignoreCase = true)
            }
            mentioned.ifEmpty { listOfNotNull(nextInRotation(active, lastSpeakerId)) }
        }
    }

    return if (!allowSelfResponses && lastSpeakerId != null) {
        val filtered = raw.filter { it != lastSpeakerId }
        // 若过滤后为空(仅剩自己)，保留原结果避免无人发言
        filtered.ifEmpty { raw }
    } else {
        raw
    }
}

/** 轮转选择下一位：上一位发言者的下一个；无上一位则取第一个 */
private fun nextInRotation(active: List<Uuid>, lastSpeakerId: Uuid?): Uuid? {
    if (active.isEmpty()) return null
    val lastIndex = lastSpeakerId?.let { active.indexOf(it) } ?: -1
    return active[(lastIndex + 1) % active.size]
}
