# Group Chat — Generation Integration Design

This is the **one remaining piece** of the SillyTavern group-chat feature. The
engine (`ChatGroup` + `selectResponders`), persistence (`Settings.chatGroups`),
and management UI (`ChatGroupSection`) are done. This doc specifies exactly how to
wire group turns into the generation loop. It is intentionally precise so it can be
implemented in one focused session **with a working build** (the change touches the
core chat path and must be compiled/tested, which the authoring environment could
not do: no Android SDK + intentionally-absent `app/google-services.json`).

## Current single-assistant flow (anchors)

- `ChatService.sendMessage(...)` (`ChatService.kt:325`) appends the user message,
  then triggers generation.
- `ChatService.handleMessageComplete(conversationId, messageRange)`
  (`ChatService.kt:491`) is the core:
  1. resolves the assistant from `conversation.assistantId`
     (`getAssistantById ?: getCurrentAssistant`, line 497);
  2. resolves `model` from `assistant.chatModelId ?: settings.chatModelId` (line 499);
  3. computes `senderName` (line 501);
  4. calls `generationHandler.generateText(... assistant = assistant ...)`
     (line 529) with that assistant's memories, transformers, tools.
- A generated reply becomes a new ASSISTANT `MessageNode` on the conversation;
  `UIMessage` carries `modelId: Uuid?` and `annotations: List<UIMessageAnnotation>`
  (`ai/.../Message.kt:20`). There is no per-member attribution field today.
- New conversations are seeded in `ChatService` (the `else` branch around line 305)
  via `buildInitialMessageNodes(presetMessages, alternateGreetings)`.

## Required changes

### 1. Mark a conversation as a group conversation
Add `groupId: Uuid? = null` to `Conversation` (`data/model/Conversation.kt:16`) and
persist it in `ConversationEntity` + DAO mapping (Room migration — additive,
nullable). When `groupId != null`, the conversation is a group chat.

> **Confirmed (persistence):** `ConversationEntity` (`data/db/entity/ConversationEntity.kt`)
> stores fields as **Room columns** (`assistant_id`, `nodes`, `mode_injection_ids`,
> `lorebook_ids`, …), not a single serialized blob. Therefore persisting `groupId`
> requires a real **Room schema-version bump + `Migration`** adding a
> `group_id TEXT` column (nullable / default). This is the part that **must not be
> done without a build** — a bad migration crashes every user at startup or risks
> data loss for the whole conversation table, not just group users.
>
> **Safe MVP without migration:** declare the field as `@Transient val groupId: Uuid? = null`
> on `Conversation`. Room mapping ignores `@Transient`, so no migration is needed,
> and group chat works for the **current session** (resets to single-assistant on
> reload). Ship this first to validate the generation loop, then add the column +
> migration in a follow-up once verified in a build.

### 2. Per-member message attribution
To support `selectResponders`'s `lastSpeakerId` and per-member avatar/name in the
UI, generated group messages must record which member produced them. Two options:
- **Preferred:** add `senderId: Uuid? = null` to `UIMessage` (`ai` module,
  additive default — serialization-safe). Set it to the member assistant id.
- **No `ai` change:** add a `UIMessageAnnotation` subtype carrying the member id.

`lastSpeakerId` = the `senderId` of the last ASSISTANT message in the conversation.

### 3. Group branch in `handleMessageComplete`

**Confirmed flow shape** (`ChatService.kt:491–632`): the body is a single
`runCatching { generationHandler.generateText(...).onCompletion{...}.collect { chunk ->
updateCurrentMessages(chunk.messages) } }.onFailure{...}.onSuccess{ save; generateTitle;
generateSuggestion }`. `generateText` is a streaming `Flow<GenerationChunk>`; each
`GenerationChunk.Messages` carries the full message list and `updateCurrentMessages`
id-matches (updates existing, appends new). So **one `generateText` call appends one
new assistant message**; running it again with the updated conversation as input
appends the next member's reply.

**Refactor:** extract lines ~499–631 (assistant/model/senderName resolution + the
`runCatching{…}` block) into:

```
private suspend fun generateOneReply(
    conversationId: Uuid, settings: Settings,
    assistant: Assistant, model: Model,
    senderId: Uuid?, messageRange: ClosedRange<Int>? = null,
)
```

Keep the `groupId == null` path calling it with the conversation's single assistant —
**byte-for-byte equivalent** to today. Then add the branch:

```
val conversation = getConversationFlow(conversationId).value
val group = conversation.groupId?.let { id -> settings.chatGroups.firstOrNull { it.id == id } }
if (group == null) {
    val assistant = settings.getAssistantById(conversation.assistantId) ?: settings.getCurrentAssistant()
    val model = settings.findModelById(assistant.chatModelId ?: settings.chatModelId) ?: return
    generateOneReply(conversationId, settings, assistant, model, senderId = null, messageRange)
} else {
    val memberNames = group.memberIds.associateWith { settings.getAssistantById(it)?.name.orEmpty() }
    val lastText = conversation.currentMessages.lastOrNull { it.role == MessageRole.USER }?.toText().orEmpty()
    val lastSpeaker = conversation.currentMessages.lastOrNull { it.role == MessageRole.ASSISTANT }?.senderId
    for (memberId in group.selectResponders(memberNames, lastText, lastSpeaker)) {
        val member = settings.getAssistantById(memberId) ?: continue
        val model = settings.findModelById(member.chatModelId ?: settings.chatModelId) ?: continue
        generateOneReply(conversationId, settings, member, model, senderId = memberId) // sequential: await each
        // generateOneReply re-reads conversation.currentMessages as input, so member N+1 sees member N's reply
    }
}
```

**`senderId` stamping — the subtle part.** `generateText` creates the assistant
`UIMessage` internally, so `generateOneReply` cannot set `senderId` on it directly.
Two options:
- **Preferred:** thread `senderId` into `GenerationHandler.generateText(...)` and have
  it stamp the assistant message(s) it emits (one extra internal param).
- **Post-hoc:** after the member's flow completes, find the newest ASSISTANT node
  whose message has `senderId == null` and set it. Fragile under streaming/branching —
  prefer the threaded approach.

**Safety:** sequential (await each), loop bound = `responders.size` (finite), and the
existing session cancellation must break the loop (check the session/job state
between turns).

### 4. Starting a group conversation + group greetings
- Provide a way to start a chat against a `ChatGroup` (e.g. a group entry in the
  assistant/group picker that creates a `Conversation` with `groupId` set and
  `assistantId` = the first member, or a sentinel).
- Seed greetings from members: for each member with a greeting, optionally add a
  greeting `MessageNode` (SillyTavern shows group greetings). Reuse
  `buildInitialMessageNodes` per member or add a `buildGroupInitialNodes(members)`.

### 5. UI
- Chat rendering: show each ASSISTANT message's member avatar/name via `senderId`
  (fall back to model name when null).
- Group picker entry point to start a group conversation.
- (Management UI already exists in `SettingPreferencesGeneralPage`.)

## Minimal viable slice (smallest shippable)
1. `Conversation.groupId` + Room migration.
2. `UIMessage.senderId` (or annotation).
3. `handleMessageComplete` group branch with sequential `generateOneReply` loop
   (LIST strategy is enough to start; NATURAL/MANUAL already supported by
   `selectResponders`).
4. A single entry point to create a group conversation.
Defer: group greetings, fancy per-member avatars (can fall back to model name).

## Test plan
- Unit (already done): `selectResponders` in `GroupActivationTest`.
- Unit (add): `lastSpeakerId` derivation from messages; `generateOneReply`
  extraction behavior parity with the old single path (no behavior change when
  `groupId == null`).
- Manual/E2E (needs build): create a 2–3 member group, send a message, verify each
  member replies in order with its own persona/system prompt and correct
  attribution; verify cancellation stops mid-loop.

## Risks
- `handleMessageComplete` is on the hot, frequently-edited chat path — extract
  `generateOneReply` carefully and keep the `groupId == null` path byte-for-byte
  equivalent.
- Room migration for `groupId` must be additive/nullable with a default.
- `UIMessage` change is in the shared `ai` module — keep it an additive nullable
  default to avoid breaking serialization/other consumers.
