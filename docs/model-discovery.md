# Model discovery

Speech and cleanup settings refresh model lists when opened or resumed, with a manual refresh button. The last successful list is cached locally. Failed requests keep cached or built-in choices; refresh never changes the selected model.

Custom endpoints require an explicit refresh. This prevents sending a saved key to a partially typed URL. All catalog requests require HTTPS, keep credentials in headers, disable redirects, and use bounded timeouts. The cache contains model metadata only.

## Provider behavior

| Provider | Discovery | Filter |
|---|---|---|
| Groq | `/openai/v1/models` | Transcription families for dictation; text models excluding speech and guard models for cleanup |
| OpenAI | `/v1/models` | Transcription families; excludes realtime and diarization variants |
| OpenRouter | `/api/v1/models?output_modalities=transcription` | Dedicated transcription output capability |
| Gemini | `/v1beta/models`, including subsequent pages | Gemini models supporting `generateContent`; excludes live, image, TTS, embedding, and specialized agent variants |
| Custom | `<saved base URL>/models` | Provider-returned IDs; manual entry remains available |

Sorting favors stable, responsive candidates, then creation time and model generation. This is a suitability heuristic, not an accuracy benchmark. Catalogs do not guarantee billing access, quota, or compatibility with every custom server. Gemini and OpenAI expose less capability metadata than OpenRouter, so filtering also uses model families. A manual model ID can be used when discovery cannot classify a new family.

Cleanup remains on Groq and uses the selected cleanup model. Timeout, provider failure, and the existing meaning-preservation guard still fall back to the raw transcript.

Provider references checked September 6, 2026:

- [Groq model API](https://console.groq.com/docs/models)
- [OpenAI model API](https://developers.openai.com/api/reference/resources/models/methods/list)
- [OpenRouter transcription discovery](https://openrouter.ai/docs/guides/overview/multimodal/stt)
- [Gemini model listing](https://ai.google.dev/api/models)
