# OTA Copy Bundle — wire format & signing contract (M13a)

This document is the **contract** between the device-side OTA copy pipeline (M13a, this repo) and the
authoring/CI signing tool (M13b, a separate repo). The signer **must** produce byte-identical canonical
input to what the device verifies, or every bundle is rejected.

See `spec.md` §2.3 / §6 for the rationale (signed-bundle integrity, rollback protection, compliance
lock). The pinned public key and verification live in `app/.../copy/ota/`.

---

## 1. Wire format

A bundle is a single JSON object served over HTTPS as a **flat file** at the bucket root —
`copy-bundles/strings.signed.json` (v1 ships `en` only). Pass B's CI signer **must** upload to bucket
`copy-bundles`, object key exactly `strings.signed.json` (upsert/overwrite); the app fetches
`https://tznxppfwlgdhbvsplewl.supabase.co/storage/v1/object/public/copy-bundles/strings.signed.json`:

```json
{
  "revision": 42,
  "strings": {
    "onboarding.welcome.title": "Dictate into any app you use",
    "settings.keys.set": "%1$d set"
  },
  "signature": "<base64 Ed25519 signature>"
}
```

| Field | Type | Notes |
|---|---|---|
| `revision` | int | Monotonic. The device applies a fetched bundle only if `revision` **strictly exceeds** the last applied revision (rollback guard). |
| `strings` | object (string→string) | Copy overrides keyed by `CopyKeys`. Only **overridable** keys take effect; compliance keys are ignored on-device even if present. Unknown keys (not in `CopyKeys.ALL`) **reject the whole bundle**. |
| `signature` | base64 string | Ed25519 signature (64 raw bytes, base64) over the **canonical signing input** (§2). |

The device tolerates extra/unknown top-level fields and any wire whitespace or `strings` key ordering —
because the signature is computed over a *reconstruction*, not the raw bytes (§2).

---

## 2. Canonical signing input (sign over **exactly** these bytes)

The signature does **not** cover the raw file bytes (those include `signature` itself). Both sides
reconstruct a canonical byte string from `(revision, strings)` and sign / verify over that.

Canonical form — compact UTF-8 JSON, **no insignificant whitespace**, exactly two members in this order:

```
{"revision":<R>,"strings":{<members>}}
```

- **`<R>`** — `revision` as a base-10 integer in shortest form: no leading zeros (except the literal
  `0`), no `+`, no decimal point. (Kotlin `Int.toString()` / standard integer rendering.)
- **`<members>`** — each `strings` entry as `"<key>":"<value>"`, joined by `,`, **no spaces**. Entries
  ordered by **key ascending in UTF-8 byte lexicographic order**. (All keys are ASCII, so this equals
  Unicode code-point order and Java `String.compareTo`.)
- **String escaping** (keys and values), and nothing else:
  - `\` → `\\`
  - `"` → `\"`
  - U+0008 → `\b`, U+0009 → `\t`, U+000A → `\n`, U+000C → `\f`, U+000D → `\r`
  - any other code point `< 0x20` → `\u00xx` (lowercase hex, 4 digits)
  - every other character — including `/`, `$`, and all non-ASCII — emitted **literally as UTF-8**
    (no escaping; `/` is **not** escaped; non-ASCII is **not** `\u`-escaped)
- Encode the whole string as **UTF-8, no BOM, no trailing newline**. These bytes are the Ed25519 message.

### Worked example

`revision = 42`, `strings = {"a.b": "Hi", "z": "%1$d"}` →

```
{"revision":42,"strings":{"a.b":"Hi","z":"%1$d"}}
```

(`$` is byte `0x24` — emitted literally, not escaped.)

### Reference signer (any standard Ed25519 implementation is compatible)

```python
import json, base64
from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey

def canonical(revision: int, strings: dict[str, str]) -> bytes:
    def esc(s: str) -> str:
        out = []
        for ch in s:
            o = ord(ch)
            if ch == '\\': out.append('\\\\')
            elif ch == '"': out.append('\\"')
            elif o == 0x08: out.append('\\b')
            elif o == 0x09: out.append('\\t')
            elif o == 0x0A: out.append('\\n')
            elif o == 0x0C: out.append('\\f')
            elif o == 0x0D: out.append('\\r')
            elif o < 0x20: out.append('\\u%04x' % o)
            else: out.append(ch)
        return '"' + ''.join(out) + '"'
    keys = sorted(strings.keys(), key=lambda k: k.encode('utf-8'))
    members = ','.join(esc(k) + ':' + esc(strings[k]) for k in keys)
    return ('{"revision":%d,"strings":{%s}}' % (revision, members)).encode('utf-8')

priv = Ed25519PrivateKey.generate()  # M13b: load the CI-secret private key instead
sig = priv.sign(canonical(revision, strings))
bundle = {"revision": revision, "strings": strings, "signature": base64.b64encode(sig).decode()}
```

The matching **public** key is pinned in the app
(`Ed25519Verifier.PINNED_PUBLIC_KEY_B64`, raw 32 bytes, base64). The private key is an M13b CI secret
and never ships in the binary or reaches the authoring browser (spec §2.3).

---

## 3. Device validation gauntlet (any failure → discard bundle, keep current resolved copy)

When **accepting a freshly fetched** bundle (`CopyUpdater`):

1. **Ed25519 signature** verifies against the pinned key over the canonical bytes (§2). Fail → reject.
2. **Revision** strictly greater than the stored applied revision. ≤ → reject (rollback guard).
3. **Schema + placeholder parity** — every key ∈ `CopyKeys.ALL`; every value's `%…` format placeholders
   match the baked-in template for that key. Any mismatch → reject the **whole** bundle.
4. Only then apply (`CopyRepository.applyOverride`), which **ignores compliance keys** (the OTA layer can
   never change the accessibility disclosure, permission rationales, privacy promises, or the BYOK
   warning — they are binary-only).

On **re-applying the cached** bundle at launch (`CopyRepository.applyCachedOverride`), steps 1, 3, 4 run
again (the signature is re-verified every launch); step 2 is skipped because the cached revision *equals*
the applied revision — re-verifying the signature still proves authenticity, so it is not a rollback.

---

## 4. Caching & resolution layers

Three layers, resolved offline-first (spec §2.3):

1. **Baked-in** — `assets/copy/strings.default.json`, complete, always the fallback.
2. **Cached verified override** — the last accepted bundle's **raw signed bytes** in `filesDir/copy/`
   (backup-excluded), re-verified and applied every launch.
3. **Live fetch** — background, off the critical path, run unconditionally on every launch (no user
   setting — the OTA fetch is part of how the app works), `If-None-Match`/ETag so an unchanged bundle is
   a cheap `304`. An accepted bundle is
   cached and applied on the **next launch** (no mid-session flicker).

Offline / fetch failure / rejected bundle is **silent** — no user-facing error — and leaves the resolved
copy at cached-or-baked-in.
