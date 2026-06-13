package me.rerere.rikkahub.data.model

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 解析 SillyTavern Chat Completion 预设 JSON（"酒馆预设"）为 [PromptPreset]。纯函数，便于测试。
 *
 * 预设 JSON 没有 name 字段，名称来自文件名（由调用方传入）。
 */
fun parseSillyTavernPreset(json: JsonObject, name: String): PromptPreset {
    fun str(key: String) = json[key]?.let { runCatching { it.jsonPrimitive.contentOrNull }.getOrNull() }
    fun flt(key: String) = json[key]?.let { runCatching { it.jsonPrimitive.floatOrNull }.getOrNull() }
    fun int(key: String) = json[key]?.let { runCatching { it.jsonPrimitive.intOrNull }.getOrNull() }
    fun bool(key: String) = json[key]?.let { runCatching { it.jsonPrimitive.booleanOrNull }.getOrNull() }

    val prompts = json["prompts"]?.let { runCatching { it.jsonArray }.getOrNull() }
        ?.mapNotNull { el ->
            val obj = runCatching { el.jsonObject }.getOrNull() ?: return@mapNotNull null
            fun s(k: String) = obj[k]?.let { runCatching { it.jsonPrimitive.contentOrNull }.getOrNull() }
            fun b(k: String) = obj[k]?.let { runCatching { it.jsonPrimitive.booleanOrNull }.getOrNull() }
            fun i(k: String) = obj[k]?.let { runCatching { it.jsonPrimitive.intOrNull }.getOrNull() }
            val identifier = s("identifier") ?: return@mapNotNull null
            PresetPrompt(
                identifier = identifier,
                name = s("name").orEmpty(),
                role = s("role") ?: "system",
                content = s("content").orEmpty(),
                systemPrompt = b("system_prompt") ?: false,
                marker = b("marker") ?: false,
                injectionPosition = i("injection_position"),
                injectionDepth = i("injection_depth"),
            )
        } ?: emptyList()

    // prompt_order：优先取默认角色(character_id == 100000)的顺序，否则取第一条
    val orderArrays = json["prompt_order"]?.let { runCatching { it.jsonArray }.getOrNull() }
        ?.mapNotNull { runCatching { it.jsonObject }.getOrNull() }
        ?: emptyList()
    val chosen = orderArrays.firstOrNull { obj ->
        obj["character_id"]?.let { runCatching { it.jsonPrimitive.intOrNull }.getOrNull() } == 100000
    } ?: orderArrays.firstOrNull()
    val promptOrder = chosen?.get("order")?.let { runCatching { it.jsonArray }.getOrNull() }
        ?.mapNotNull { el ->
            val obj = runCatching { el.jsonObject }.getOrNull() ?: return@mapNotNull null
            val id = obj["identifier"]?.let { runCatching { it.jsonPrimitive.contentOrNull }.getOrNull() }
                ?: return@mapNotNull null
            PromptOrderEntry(
                identifier = id,
                enabled = obj["enabled"]?.let { runCatching { it.jsonPrimitive.booleanOrNull }.getOrNull() } ?: true,
            )
        } ?: emptyList()

    return PromptPreset(
        name = name,
        prompts = prompts,
        promptOrder = promptOrder,
        temperature = flt("temperature"),
        topP = flt("top_p"),
        frequencyPenalty = flt("frequency_penalty"),
        presencePenalty = flt("presence_penalty"),
        maxContext = int("openai_max_context"),
        maxTokens = int("openai_max_tokens"),
        scenarioFormat = str("scenario_format") ?: "{{scenario}}",
        personalityFormat = str("personality_format") ?: "{{personality}}",
        wiFormat = str("wi_format") ?: "{0}",
        newChatPrompt = str("new_chat_prompt").orEmpty(),
        newExampleChatPrompt = str("new_example_chat_prompt").orEmpty(),
        squashSystemMessages = bool("squash_system_messages") ?: false,
        namesBehavior = int("names_behavior") ?: 0,
    )
}
