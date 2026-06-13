# SillyTavern Parity Roadmap

Goal: build on RikkaHub's existing architecture to make it a capable Android
SillyTavern replacement, reusing existing concepts (Assistant, Lorebook,
PromptInjection, AssistantRegex, MessageNode branching, Pebble templates) rather
than rebuilding.

## Current capability (baseline)

| Area | RikkaHub today | ST | Status |
|------|----------------|----|--------|
| Character cards | shallow import: name/desc/personality/scenario/first_mes flattened into systemPrompt; **drops** embedded lorebook, alternate greetings, example dialogue, depth_prompt, post_history | full V2/V3 | **partial** |
| World Info / Lorebook | `Lorebook` + `PromptInjection.RegexInjection`: keyword/regex trigger, scanDepth, constant, position (before/after sys, top/bottom, at-depth), priority | keys+secondary keys, selective logic, recursion, probability, inclusion groups, token budget, sticky/cooldown | **partial** |
| Regex scripts | `AssistantRegex`: find/replace, scope user/assistant, visualOnly | + slash/world-info scope, min/max depth, markdown/prompt-only, runOnEdit | **partial** |
| Personas | only a global `userNickname` string; `{{user}}` macro | full persona: name+avatar+description, position/depth, persona lore | **missing** |
| Author's Note | none | note at depth, interval | **missing** |
| Macros | `{{char}}`,`{{user}}`,`{{nickname}}`,date/time,device | dozens incl. {{description}},{{personality}},{{scenario}},{{persona}},{{random}},{{roll}},{{pick}} | **partial** |
| Swipes / branching | MessageNode branching (regenerate → alt messages) + swipeable greetings (alternate_greetings) | swipes incl. greeting swipes | **full** |
| Group chats | none | multi-character groups | **missing** |
| Quick replies | `QuickMessage` (quickMessageIds) | quick reply sets | **partial** |

## Roadmap (priority order)

### Phase 1 — Character fidelity (highest value)
1. **[DONE] Comprehensive character-card import (V2/V3, +V1 fallback)**
   - Parses all standard fields; includes `mes_example` in prompt.
   - Imports embedded `character_book` → creates a `Lorebook` (entries mapped:
     keys→keywords, content, constant→constantActive, case_sensitive, position
     0-4/before_char/after_char→InjectionPosition, insertion_order→priority,
     extensions.depth/role), persisted to settings, bound via `lorebookIds`.
   - `extensions.depth_prompt` → constant AT_DEPTH injection.
   - `post_history_instructions` → constant BOTTOM_OF_CHAT injection.
   - Raw card fields stored on `Assistant.characterCard: CharacterCard`.
   - Files: `AssistantImporter.kt`, `Assistant.kt` (CharacterCard).
   - [DONE] 1b: `alternate_greetings` → swipeable greeting. `buildInitialMessageNodes`
     merges first_mes + alternateGreetings into one `MessageNode` (reuses existing
     swipe UI), wired into new-conversation creation in `ChatService`. Tested in
     `InitialMessageNodesTest`.
2. **[PARTIAL] Macro expansion**
   - [DONE] `{{description}}`,`{{personality}}`,`{{scenario}}` (from characterCard).
   - [DONE] parametric: `{{random:a,b}}`/`{{random::a::b}}`, `{{pick:..}}`,
     `{{roll:NdM}}`, `{{newline}}`, `{{// comment}}`/`{{comment ..}}`
     (`PlaceholderTransformer.expandParametricMacros`, tested in
     `ParametricMacroTest`).
   - [TODO] `{{persona}}` (needs Persona feature, Phase 2).

> Build/test note: project requires `app/google-services.json` (absent here);
> validate `ParametricMacroTest` + import flow once that file is provided.

### Phase 2 — Persona + Author's Note
3. **[DONE] User Persona**
   - `Persona` model (id, name, avatar, description, enabled, position, depth, role).
   - Settings: `personas` + `selectedPersonaId` (DataStore persisted, dedup/validated).
   - `PersonaTransformer` injects active persona description at its position
     (reuses `applyInjections`), registered before `PlaceholderTransformer`.
   - Macros: `{{user}}`/`{{nickname}}` → active persona name (fallback nickname),
     `{{persona}}` → active persona description.
   - UI: persona management section (add/edit/delete/select) in
     `SettingPreferencesGeneralPage`.
4. **[DONE] Author's Note**: per-assistant note injected at configurable depth +
   role with insertion interval (every N messages). `AuthorsNote` model on
   `Assistant`, `AuthorsNoteTransformer` (reuses `applyInjections`, registered
   after `PersonaTransformer`), UI card in `AssistantPromptPage`
   (`AuthorsNoteCard`), tested in `AuthorsNoteTransformerTest`.

### Phase 3 — World Info depth
5. **[DONE] Secondary keys + selective logic** (AND_ANY / NOT_ALL / NOT_ANY /
   AND_ALL) + whole-word matching + case sensitivity — `RegexInjection` +
   `isTriggered` (tested in `WorldInfoActivationTest`); importer maps
   `secondary_keys`.
6. **[DONE]** World-Info refinements
   - [DONE] Probability / useProbability gating (`selectTriggeredEntries` +
     `defaultRoll`, threaded into `collectInjections`; importer maps
     `extensions.probability`/`useProbability`).
   - [DONE] Inclusion groups (`RegexInjection.inclusionGroup`; one winner per
     group by priority; importer maps `extensions.group`/`inclusion_group`).
   - [DONE] `selective` + `selectiveLogic` mapped from card extensions.
   - [DONE] Recursion: `expandRecursive` (preventRecursion / excludeRecursion /
     delayUntilRecursion, bounded by `DEFAULT_MAX_RECURSION_STEPS`), wired into
     `collectInjections`; importer maps `exclude_recursion`/`prevent_recursion`/
     `delay_until_recursion`. Tested in `WorldInfoRecursionTest`.
   - [DONE] Token budget: `applyTokenBudget` (char-based; constant entries always
     kept; highest-priority non-constant win the budget; original order
     preserved). `Lorebook.tokenBudget` field; importer maps `token_budget`.
     Tested in `WorldInfoBudgetTest`.
   - Probability/groups tested in `WorldInfoSelectionTest`.

### Phase 4 — Group chats, character export
7. **[PARTIAL] Multi-character group conversations**
   - [DONE] Engine: `ChatGroup` model (members, muted, strategy, self-responses)
     + `GroupActivationStrategy` (NATURAL/LIST/MANUAL) + pure `selectResponders`
     (mention detection, round-robin rotation, self-response filtering).
     Tested in `GroupActivationTest`.
   - [DONE] Persistence: `Settings.chatGroups` (DataStore key/decode/encode/dedup,
     mirrors personas pattern).
   - [DONE] Group management UI: `ChatGroupSection`/`ChatGroupEditDialog` in
     `SettingPreferencesGeneralPage` (create/edit/delete, member multi-select,
     strategy picker, self-response toggle).
   - [TODO] Generation integration: per-turn member selection in `ChatService`,
     each responder generates with its own assistant config + per-message
     attribution. **Turnkey design in `docs/group-chat-integration-design.md`**
     (change points, data-model deltas, `handleMessageComplete` group branch,
     MVP slice, test plan, risks). Needs a build to verify.
8. **[DONE] Character card export** (round-trips the rich import).
   - [DONE] `CharacterCardExporter.buildV3Card(assistant, lorebooks)` builds a
     `chara_card_v3` JSON symmetric to the importer (fields + `character_book`
     entries incl. extensions: position/depth/role/probability/selectiveLogic/
     group/recursion). Tested in `CharacterCardExporterTest`.
   - [DONE] `PngTextChunk` util: add/read PNG `tEXt` chunks (CRC32, replaces
     same-keyword chunk, preserves IEND). Tested in `PngTextChunkTest`.
   - [DONE] UI: `CharacterCardExportButton` (SAF `CreateDocument`) saves the V3
     card as JSON, in `AssistantPromptPage`.
   - [DONE] PNG card export: when the assistant has an image avatar, embeds the
     V3 JSON (base64) into the avatar PNG via `PngTextChunk` under both `chara`
     (V2) and `ccv3` (V3) keywords — a SillyTavern-importable PNG character card.

## Notes
- `Assistant` and `Lorebook` are `@Serializable`, stored in DataStore settings — adding fields with defaults is migration-safe.
- Injection pipeline: `PromptInjectionTransformer` (mode + lorebook), `PlaceholderTransformer` (macros), `TemplateTransformer` (Pebble messageTemplate).
