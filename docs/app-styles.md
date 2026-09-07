# App style defaults

Quill includes 540 Android package mappings: 87 Formal, 346 Neutral (normal), and
107 Casual (relaxed). These are suggestions for an app's usual writing context.
Individual app overrides always win. Unlisted apps use the user's global style.

Email and workplace communication generally use Formal. Personal messaging,
notes, browsers, and general assistants use Neutral. Social posting and community
discussion generally use Casual. Existing defaults are preserved. A package cannot
distinguish work and personal conversations, or different sites inside a browser.

The catalog includes mainstream Play apps and open-source clients, including some
separate beta and distribution packages. Its count is package mappings, not 540
distinct products or 540 tested text editors.

## Sources and maintenance

`app-style-catalog.csv` records each package, published app name, assigned style,
source URL, and metadata check date. Package identifiers and names were checked on
September 6, 2026 against public Google Play listings or the official F-Droid
repository index downloaded from `https://f-droid.org/repo/index-v1.jar`.
Style assignments are Quill's defaults, not recommendations from those publishers.
Metadata checks establish identity; they do not establish insertion compatibility.

The CSV is the source of truth. After editing it, run:

```powershell
./scripts/generate-app-styles.ps1
./scripts/generate-app-styles.ps1 -Check
```

The generator rejects invalid identifiers, duplicate packages, unsupported styles,
and missing source references. Commit the generated Kotlin file with the CSV.
The app uses only the generated map: no catalog download, account, or new
permission is required. Updates to these defaults ship with app updates.
