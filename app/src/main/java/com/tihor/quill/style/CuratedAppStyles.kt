package com.tihor.quill.style

/**
 * The curated per-package default styles (scope doc §5.1) — Quill's out-of-the-box guess at how you
 * write in each app, so the feature is worth something with zero configuration.
 *
 * **These live in code and are never snapshotted into storage** (scope doc §5). Only explicit user
 * overrides are persisted. That means an app update can improve a guess and every user who never
 * touched that app picks the better guess up automatically, and "reset to default" is just deleting an
 * override key rather than rewriting one.
 *
 * A package that isn't listed here falls through to the user's global default — deliberately, not by
 * omission. Unknown apps are the common case and Neutral-by-default is the safe answer.
 *
 * ### Package names are verified, not guessed
 * Every id below was checked against its live Play Store listing (July 2026); the labels in the
 * comments are the store names. Getting one wrong is a silent no-op — the app simply never matches —
 * so an unverifiable package is left out rather than guessed at.
 *
 * ### Why browsers are Neutral, not Formal
 * A browser text field could be a webmail compose box or a Reddit comment. The package name genuinely
 * cannot distinguish them, so browsers get the neutral middle rather than a coin flip (scope doc §5.1).
 * Same reasoning, in reverse, for why per-app is only a proxy at all (§9.3): Slack holds work channels
 * *and* DMs. Three coarse styles plus a user override keep the blast radius small.
 */
object CuratedAppStyles {

    /** Package name → curated default. Unlisted packages resolve to the global default. */
    val MAP: Map<String, WritingStyle> = mapOf(
        // --- Formal: work and external comms ---
        "com.Slack" to WritingStyle.FORMAL, // Slack (capital S — genuinely)
        "com.google.android.gm" to WritingStyle.FORMAL, // Gmail
        "com.microsoft.office.outlook" to WritingStyle.FORMAL, // Outlook
        "com.microsoft.teams" to WritingStyle.FORMAL, // Microsoft Teams
        "notion.id" to WritingStyle.FORMAL, // Notion
        "com.atlassian.android.jira.core" to WritingStyle.FORMAL, // Jira Cloud
        "com.atlassian.android.confluence.core" to WritingStyle.FORMAL, // Confluence Cloud
        "com.linkedin.android" to WritingStyle.FORMAL, // LinkedIn
        "us.zoom.videomeetings" to WritingStyle.FORMAL, // Zoom (in-meeting chat)
        "com.google.android.apps.docs.editors.docs" to WritingStyle.FORMAL, // Google Docs
        "com.google.android.apps.docs" to WritingStyle.FORMAL, // Google Drive
        "com.salesforce.chatter" to WritingStyle.FORMAL, // Salesforce
        "com.asana.app" to WritingStyle.FORMAL, // Asana
        "com.trello" to WritingStyle.FORMAL, // Trello
        "com.basecamp.bc3" to WritingStyle.FORMAL, // Basecamp
        "com.superhuman.mail" to WritingStyle.FORMAL, // Superhuman Mail
        "com.readdle.spark" to WritingStyle.FORMAL, // Spark Mail
        "ch.protonmail.android" to WritingStyle.FORMAL, // Proton Mail
        "com.zoho.mail" to WritingStyle.FORMAL, // Zoho Mail

        // --- Neutral: personal messaging, notes, and anything ambiguous ---
        // Listed explicitly rather than left to fall through: if the user sets their global default to
        // Formal or Casual, these should still be Neutral. Falling through would drag them along.
        "com.whatsapp" to WritingStyle.NEUTRAL, // WhatsApp
        "com.whatsapp.w4b" to WritingStyle.NEUTRAL, // WhatsApp Business
        "com.google.android.apps.messaging" to WritingStyle.NEUTRAL, // Google Messages
        "org.telegram.messenger" to WritingStyle.NEUTRAL, // Telegram
        "org.thoughtcrime.securesms" to WritingStyle.NEUTRAL, // Signal
        "com.facebook.orca" to WritingStyle.NEUTRAL, // Messenger
        "com.google.android.keep" to WritingStyle.NEUTRAL, // Google Keep
        "com.samsung.android.app.notes" to WritingStyle.NEUTRAL, // Samsung Notes
        "md.obsidian" to WritingStyle.NEUTRAL, // Obsidian
        "com.android.chrome" to WritingStyle.NEUTRAL, // Chrome — could be anything (see class docs)
        "org.mozilla.firefox" to WritingStyle.NEUTRAL, // Firefox — likewise
        "com.google.android.googlequicksearchbox" to WritingStyle.NEUTRAL, // Google app / search box

        // --- Casual: social and quick chats ---
        "com.instagram.android" to WritingStyle.CASUAL, // Instagram
        "com.snapchat.android" to WritingStyle.CASUAL, // Snapchat
        "com.twitter.android" to WritingStyle.CASUAL, // X (still the twitter package id)
        "com.discord" to WritingStyle.CASUAL, // Discord
        "com.reddit.frontpage" to WritingStyle.CASUAL, // Reddit
        "com.zhiliaoapp.musically" to WritingStyle.CASUAL, // TikTok (kept the musical.ly package id)
        "com.bereal.ft" to WritingStyle.CASUAL, // BeReal
        "com.instagram.barcelona" to WritingStyle.CASUAL, // Threads
        "com.tumblr" to WritingStyle.CASUAL, // Tumblr
        "tv.twitch.android.app" to WritingStyle.CASUAL, // Twitch
    )

    /** The curated default for [packageName], or `null` when it isn't curated. */
    operator fun get(packageName: String?): WritingStyle? = packageName?.let { MAP[it] }
}
