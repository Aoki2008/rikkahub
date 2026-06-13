package me.rerere.rikkahub.data.export

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.InjectionPosition
import me.rerere.rikkahub.data.model.Lorebook
import me.rerere.rikkahub.data.model.PromptInjection
import me.rerere.rikkahub.data.model.SelectiveLogic

/**
 * 将 [Assistant]（及其绑定的世界书）导出为 SillyTavern V3 角色卡 JSON。
 *
 * 与 AssistantImporter 的解析逻辑对称，使导入→导出可往返。纯函数，便于测试。
 */
object CharacterCardExporter {

    fun buildV3Card(assistant: Assistant, boundLorebooks: List<Lorebook>): JsonObject {
        val card = assistant.characterCard
        val firstMessage = card?.firstMessage?.ifBlank { null }
            ?: assistant.presetMessages
                .firstOrNull { it.role == MessageRole.ASSISTANT }
                ?.parts
                ?.filterIsInstance<UIMessagePart.Text>()
                ?.joinToString("") { it.text }
            ?: ""

        return buildJsonObject {
            put("spec", "chara_card_v3")
            put("spec_version", "3.0")
            put("data", buildJsonObject {
                put("name", assistant.name)
                put("description", card?.description.orEmpty())
                put("personality", card?.personality.orEmpty())
                put("scenario", card?.scenario.orEmpty())
                put("first_mes", firstMessage)
                put("mes_example", card?.mesExample.orEmpty())
                put("creator_notes", card?.creatorNotes.orEmpty())
                put("system_prompt", "")
                put("post_history_instructions", card?.postHistoryInstructions.orEmpty())
                put("creator", card?.creator.orEmpty())
                put("character_version", card?.characterVersion.orEmpty())
                put("tags", buildJsonArray { card?.tags?.forEach { add(it) } })
                put("alternate_greetings", buildJsonArray {
                    card?.alternateGreetings?.forEach { add(it) }
                })

                val entries = boundLorebooks.flatMap { it.entries }
                if (entries.isNotEmpty()) {
                    put("character_book", buildCharacterBook(boundLorebooks, entries))
                }
            })
        }
    }

    private fun buildCharacterBook(
        lorebooks: List<Lorebook>,
        entries: List<PromptInjection.RegexInjection>,
    ): JsonObject = buildJsonObject {
        put("name", lorebooks.firstOrNull()?.name.orEmpty())
        put("token_budget", lorebooks.maxOfOrNull { it.tokenBudget } ?: 0)
        put("recursive_scanning", true)
        put("entries", buildJsonArray {
            entries.forEachIndexed { index, entry ->
                add(buildJsonObject {
                    put("keys", buildJsonArray { entry.keywords.forEach { add(it) } })
                    put("secondary_keys", buildJsonArray { entry.secondaryKeys.forEach { add(it) } })
                    put("content", entry.content)
                    put("comment", entry.name)
                    put("name", entry.name)
                    put("enabled", entry.enabled)
                    put("constant", entry.constantActive)
                    put("selective", entry.secondaryKeys.isNotEmpty())
                    put("insertion_order", entry.priority)
                    put("case_sensitive", entry.caseSensitive)
                    // 仅 before/after_char 写顶层 position 字符串；其余位置依赖 extensions.position
                    positionString(entry.position)?.let { put("position", it) }
                    put("id", index)
                    put("extensions", buildJsonObject {
                        put("position", positionCode(entry.position))
                        put("depth", entry.injectDepth)
                        put("role", roleCode(entry.role))
                        put("probability", entry.probability)
                        put("useProbability", entry.useProbability)
                        put("selectiveLogic", logicCode(entry.selectiveLogic))
                        put("group", entry.inclusionGroup)
                        put("exclude_recursion", entry.excludeRecursion)
                        put("prevent_recursion", entry.preventRecursion)
                        put("delay_until_recursion", entry.delayUntilRecursion)
                    })
                })
            }
        })
    }

    /** 顶层 position 字符串（仅 before/after_char 有意义，其余返回 null 由 extensions.position 表达） */
    private fun positionString(position: InjectionPosition): String? = when (position) {
        InjectionPosition.BEFORE_SYSTEM_PROMPT -> "before_char"
        InjectionPosition.AFTER_SYSTEM_PROMPT -> "after_char"
        else -> null
    }

    /** extensions.position 整数编码（SillyTavern world_info_position） */
    private fun positionCode(position: InjectionPosition): Int = when (position) {
        InjectionPosition.BEFORE_SYSTEM_PROMPT -> 0
        InjectionPosition.AFTER_SYSTEM_PROMPT -> 1
        InjectionPosition.TOP_OF_CHAT -> 2
        InjectionPosition.BOTTOM_OF_CHAT -> 3
        InjectionPosition.AT_DEPTH -> 4
    }

    private fun roleCode(role: MessageRole): Int = when (role) {
        MessageRole.ASSISTANT -> 2
        MessageRole.SYSTEM -> 0
        else -> 1
    }

    private fun logicCode(logic: SelectiveLogic): Int = when (logic) {
        SelectiveLogic.AND_ANY -> 0
        SelectiveLogic.NOT_ALL -> 1
        SelectiveLogic.NOT_ANY -> 2
        SelectiveLogic.AND_ALL -> 3
    }
}
