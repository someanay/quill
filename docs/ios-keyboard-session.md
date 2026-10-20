# iOS keyboard recording session — October 20, 2026

Status: implemented; native iPhone/iPad execution and final visual review in progress.
This is the M14 follow-up explicitly requested by the owner. Android, Windows,
Mac and unrelated website work remain outside this change.

## Platform findings

Apple's [custom keyboard guide](https://developer.apple.com/library/archive/documentation/General/Conceptual/ExtensibilityPG/CustomKeyboard.html)
does not give keyboard extensions microphone access. The containing app owns
capture. Apple's [audio-session documentation](https://developer.apple.com/documentation/avfaudio/avaudiosession/category-swift.struct/record)
supports background recording with the audio background mode and microphone
permission. That does not allow a suspended or terminated app to be silently
woken by a keyboard command.

Apple's [AudioRecordingIntent documentation](https://developer.apple.com/documentation/AppIntents/AudioRecordingIntent)
requires a Live Activity for that protocol; it does not provide a cold background
microphone escape hatch. An [Apple DTS answer](https://developer.apple.com/forums/thread/816408)
also confirms the general background-activation privacy block. Quill does not
pretend to be a phone/VoIP app to bypass it.

Wispr's [session documentation](https://docs.wisprflow.ai/articles/4841123325-Longer-dictation-sessions-%E2%80%94-now-up-to-20-minutes)
describes a configurable iOS Flow Session timer, defaulting to five minutes.
Borrow the explicitly enabled live-session model. Quill must disclose that the
microphone remains active between takes; it cannot promise hardware microphone
privacy while simultaneously keeping background capture ready.

## Implemented interaction

1. Start a five-minute keyboard session visibly in Quill after key/mic setup.
2. Return to the destination app once and choose the Quill keyboard.
3. Tap Dictate, speak, then Stop dictation. Quill records/transcribes in the
   background; the keyboard inserts once into the unchanged original field.
4. Repeat without opening Quill or pressing Send/Insert between takes.
5. End the session from Quill or the keyboard. Expiry, interruptions, removed
   input routes and protected-data locking release the microphone. Restarting
   Quill never silently resumes an old session.

The explicit foreground start keeps a real microphone input engine alive.
Idle input buffers are discarded without files, provider calls or playback.
Only requested takes enter the existing 16 kHz recorder, durable recovery,
provider, cleanup and local-history pipeline. The OS microphone indicator stays
visible. There is no silent-audio playback, private host-app discovery, responder
chain URL trick, automatic app return or microphone use in the extension.

## Safeguards and verification gates

- A short live heartbeat and bounded lease prevent stale keyboard commands.
  Commands include session/capture IDs and a timestamp, with one pending command
  and replay refusal. The live session also has a monotonic deadline.
- App Group files contain opaque command/state IDs and the existing protected
  one-use transcript. Audio, credentials and destination text remain outside the
  command protocol. Files use complete protection and are excluded from backup.
- Automatic insertion also requires the original document identifier, selection
  and surrounding context, held only in keyboard memory. Changed/hidden fields
  require explicit insertion. Secure/phone fields and missing Full Access refuse
  dictation commands. No automatic replay follows extension/process restart.
- Provider/storage failure preserves the normal retry/cleanup behavior. Private
  mode and reset end the session and prevent late delivery.
- Clearing pending keyboard text also ends the live session and cancels a take
  already reaching its provider. A late response cannot republish cleared text.
- Core tests cover lease/command/anchor invariants. Native tests cover actual
  idle input, two real recorder takes, protected file coordination and keyboard
  auto-insertion/refusal. Installed-keyboard tests require two actual background
  dictations into a separate UIKit app, exact editor readback, no host foreground
  activation, consume-once and keyboard End, on iPhone/iPad baseline/current OS.
- Full native host/keyboard tests, recorder tests, phone/tablet UI screenshots,
  Light/Dark layouts, and device/simulator Release package inspection are required.

Simulator acceptance cannot establish physical-device battery use, audio quality,
lock behavior, call/audio-route reliability, system termination, App Store review,
signing or live-provider transcription quality. Those remain physical/account gates.
