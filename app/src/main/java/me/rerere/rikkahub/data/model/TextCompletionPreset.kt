package me.rerere.rikkahub.data.model

import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

/**
 * SillyTavern text-completion context preset.
 *
 * These presets define the "story string" used by classic/textgen-style prompts:
 * system prompt, character definitions, world info, persona, and anchors are rendered
 * into a single pre-history block before chat turns are appended.
 */
@Serializable
data class ContextPreset(
    val id: Uuid = Uuid.random(),
    val name: String = "",
    val storyString: String = DEFAULT_STORY_STRING,
    val exampleSeparator: String = "",
    val chatStart: String = "",
    val useStopStrings: Boolean = false,
    val namesAsStopStrings: Boolean = true,
    val storyStringPosition: Int = 0,
    val storyStringDepth: Int = 1,
    val storyStringRole: Int = 0,
    val alwaysForceName2: Boolean = false,
    val trimSentences: Boolean = false,
    val singleLine: Boolean = true,
)

@Serializable
data class InstructPreset(
    val id: Uuid = Uuid.random(),
    val name: String = "",
    val inputSequence: String = "",
    val outputSequence: String = "",
    val lastOutputSequence: String = "",
    val systemSequence: String = "",
    val stopSequence: String = "",
    val wrap: Boolean = true,
    val macro: Boolean = true,
    val namesBehavior: String = "none",
    val activationRegex: String = "",
    val firstOutputSequence: String = "",
    val skipExamples: Boolean = false,
    val outputSuffix: String = "",
    val inputSuffix: String = "",
    val systemSuffix: String = "",
    val userAlignmentMessage: String = "",
    val systemSameAsUser: Boolean = false,
    val lastSystemSequence: String = "",
    val firstInputSequence: String = "",
    val lastInputSequence: String = "",
    val sequencesAsStopStrings: Boolean = true,
    val storyStringPrefix: String = "",
    val storyStringSuffix: String = "",
)

@Serializable
data class SystemPromptPreset(
    val id: Uuid = Uuid.random(),
    val name: String = "",
    val content: String = "",
    val postHistory: String = "",
)

private const val DEFAULT_STORY_STRING =
    "{{#if system}}{{system}}\n{{/if}}{{#if wiBefore}}{{wiBefore}}\n{{/if}}" +
        "{{#if description}}{{description}}\n{{/if}}{{#if personality}}{{personality}}\n{{/if}}" +
        "{{#if scenario}}{{scenario}}\n{{/if}}{{#if wiAfter}}{{wiAfter}}\n{{/if}}" +
        "{{#if persona}}{{persona}}\n{{/if}}{{trim}}"
