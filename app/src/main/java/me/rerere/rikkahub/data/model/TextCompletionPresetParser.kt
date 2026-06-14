package me.rerere.rikkahub.data.model

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

fun parseSillyTavernContextPreset(json: JsonObject, fallbackName: String): ContextPreset {
    fun str(key: String) = json[key]?.let { runCatching { it.jsonPrimitive.contentOrNull }.getOrNull() }
    fun int(key: String) = json[key]?.let { runCatching { it.jsonPrimitive.intOrNull }.getOrNull() }
    fun bool(key: String) = json[key]?.let { runCatching { it.jsonPrimitive.booleanOrNull }.getOrNull() }

    return ContextPreset(
        name = str("name") ?: fallbackName,
        storyString = str("story_string") ?: ContextPreset().storyString,
        exampleSeparator = str("example_separator").orEmpty(),
        chatStart = str("chat_start").orEmpty(),
        useStopStrings = bool("use_stop_strings") ?: false,
        namesAsStopStrings = bool("names_as_stop_strings") ?: true,
        storyStringPosition = int("story_string_position") ?: 0,
        storyStringDepth = int("story_string_depth") ?: 1,
        storyStringRole = int("story_string_role") ?: 0,
        alwaysForceName2 = bool("always_force_name2") ?: false,
        trimSentences = bool("trim_sentences") ?: false,
        singleLine = bool("single_line") ?: true,
    )
}

fun parseSillyTavernInstructPreset(json: JsonObject, fallbackName: String): InstructPreset {
    fun str(key: String) = json[key]?.let { runCatching { it.jsonPrimitive.contentOrNull }.getOrNull() }
    fun bool(key: String) = json[key]?.let { runCatching { it.jsonPrimitive.booleanOrNull }.getOrNull() }

    return InstructPreset(
        name = str("name") ?: fallbackName,
        inputSequence = str("input_sequence").orEmpty(),
        outputSequence = str("output_sequence").orEmpty(),
        lastOutputSequence = str("last_output_sequence").orEmpty(),
        systemSequence = str("system_sequence").orEmpty(),
        stopSequence = str("stop_sequence").orEmpty(),
        wrap = bool("wrap") ?: true,
        macro = bool("macro") ?: true,
        namesBehavior = str("names_behavior") ?: "none",
        activationRegex = str("activation_regex").orEmpty(),
        firstOutputSequence = str("first_output_sequence").orEmpty(),
        skipExamples = bool("skip_examples") ?: false,
        outputSuffix = str("output_suffix").orEmpty(),
        inputSuffix = str("input_suffix").orEmpty(),
        systemSuffix = str("system_suffix").orEmpty(),
        userAlignmentMessage = str("user_alignment_message").orEmpty(),
        systemSameAsUser = bool("system_same_as_user") ?: false,
        lastSystemSequence = str("last_system_sequence").orEmpty(),
        firstInputSequence = str("first_input_sequence").orEmpty(),
        lastInputSequence = str("last_input_sequence").orEmpty(),
        sequencesAsStopStrings = bool("sequences_as_stop_strings") ?: true,
        storyStringPrefix = str("story_string_prefix").orEmpty(),
        storyStringSuffix = str("story_string_suffix").orEmpty(),
    )
}

fun parseSillyTavernSystemPromptPreset(json: JsonObject, fallbackName: String): SystemPromptPreset {
    fun str(key: String) = json[key]?.let { runCatching { it.jsonPrimitive.contentOrNull }.getOrNull() }

    return SystemPromptPreset(
        name = str("name") ?: fallbackName,
        content = str("content").orEmpty(),
        postHistory = str("post_history").orEmpty(),
    )
}
