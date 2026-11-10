# AI Design

AI is an **optional** layer. Without `ANTHROPIC_API_KEY` the backend runs entirely on deterministic
logic (text search, URL search, matching, pricing, rule-based assistant); only image search needs AI.

**Verification status:** no API key was available during development. Every AI path is covered by
tests with a scripted fake client (prompt/response plumbing, validation, fallbacks) but **none has
been run against the live model**. Expect to tune prompts after the first real use.

## Where AI is used — and where it is not
| Capability | AI role | Deterministic guard |
|---|---|---|
| Query interpretation (`ai/query_interpreter.py`) | Structured output → `ProductQuery` | `validate_ai_query`: brand/model/volume/weight/size/storage must literally appear in the user's text or they are dropped and listed in `uncertain_fields`; budget always comes from the rule parser |
| Image identification (`ai/image_identifier.py`) | Vision → `ImageIdentification` (attributes, confidence, alternatives) | confidence < 0.6 or `is_product=false` → no search; the app asks the user. Upload validated by magic bytes and size before the model is called |
| Ambiguous matching, stage B (`ai/match_judge.py`) | Judges only listings the rules left as `likely` | `clamp`: may downgrade freely; may upgrade `likely → exact` only with confidence ≥ 0.9 and no rule-detected conflict; exact confidence capped at 0.9; hard conflicts never reach the model |
| Shopping assistant (`ai/assistant.py`) | Rephrases a fact sheet in natural Vietnamese | The fact sheet (`build_facts`) is produced by code from stored offers, already ranked; the prompt forbids re-ranking, adding data, or claiming authenticity; mock data is flagged in the facts |
| **Price calculation, ranking, "cheapest"** | **None** | `services/pricing.py`, `services/search.py` |

## Implementation
* SDK: official `anthropic` Python SDK, `AsyncAnthropic().beta.messages.parse(...)` with
  `output_format=<Pydantic model>` (structured outputs → schema-valid JSON, parsed into the model).
* Model: `DEALMATE_AI_MODEL` (default `claude-opus-5-5`), `output_config.effort = "low"`
  (short extraction tasks), 30 s timeout, 1 retry.
* Refusals: requests opt into server-side fallback (`fallbacks="default"`); a final
  `stop_reason == "refusal"`, a truncated answer, rate limiting or any API/network error raises
  `AIUnavailable`, and the caller falls back to rules (or returns `503 ai_unavailable` for image search).
* Prompt-injection posture: user text, listing titles and image text are wrapped in tags and the
  system prompts state they are data, not instructions. Because results are schema-constrained and
  then validated/clamped, a hostile listing title can at worst change a `likely` label.
* Privacy: images are sent to the model in memory and never written to disk or the database; the
  API key exists only on the server; nothing AI-related is embedded in the Android app.

## Rule-based fallbacks
* **Interpreter** — regex/lexicon parser for brand, category, model, volume/weight, size, count,
  storage, colour, pack, budget ("dưới 350k", "từ 2 đến 3 triệu", "2.000.000đ"); an ambiguous
  budget such as "dưới 350" is *not* guessed.
* **Assistant** — templated Vietnamese answers for: cheapest seller, same product?, why more
  expensive, good deal? (needs ≥ 3 history points, otherwise says so), compare two products.
  "Find a cheaper alternative" is answered only through the similar-products section; there is no
  dedicated alternative-search feature yet.

## Structured match result
```json
{"match_type": "exact | likely | similar | different", "confidence": 0.95,
 "matched_attributes": [], "different_attributes": [], "unknown_attributes": [],
 "reason": "", "decided_by": "rules | ai"}
```

## Evaluation
`backend/tests/data/matching_pairs.json` — 119 hand-labelled pairs (36 exact, 22 likely, 38 similar,
23 different). `python -m scripts.eval_matching --verbose` prints the confusion matrix. Current
deterministic stage: exact precision 1.000, exact recall 0.972 (the one miss is a brand missing from
the lexicon). **Caveat:** the rules were developed against this same set, so these are development-set
numbers, not a held-out estimate; real listings will be messier. Extend the set with real titles once
a live connector works, and keep a held-out part.
