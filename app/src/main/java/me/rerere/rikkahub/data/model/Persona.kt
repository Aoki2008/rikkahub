package me.rerere.rikkahub.data.model

import kotlinx.serialization.Serializable
import me.rerere.ai.core.MessageRole
import kotlin.uuid.Uuid

/**
 * 用户角色 (User Persona)
 *
 * 对应 SillyTavern 的 Persona：代表"用户"在角色扮演中的身份。
 * 包含名称、头像与描述，描述会按配置的位置注入到提示词中，
 * 名称用于 {{user}} 宏，描述用于 {{persona}} 宏。
 */
@Serializable
data class Persona(
    val id: Uuid = Uuid.random(),
    val name: String = "",
    val avatar: Avatar = Avatar.Dummy,
    val description: String = "",
    val enabled: Boolean = true,
    // 描述注入位置（默认置于系统提示词之后）
    val position: InjectionPosition = InjectionPosition.AFTER_SYSTEM_PROMPT,
    val injectDepth: Int = 4,            // position 为 AT_DEPTH 时使用
    val role: MessageRole = MessageRole.USER, // AT_DEPTH 注入时的消息角色
)
