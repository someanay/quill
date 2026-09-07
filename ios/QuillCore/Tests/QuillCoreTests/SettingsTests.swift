import XCTest
@testable import QuillCore

final class SettingsTests: XCTestCase {
    func testDisablingAutomaticSnapshotsCurrentProviderAndDefaultModel() {
        var settings = QuillSettings()
        settings.selectProvider(.gemini)
        settings.setCleanupAutomatic(false)
        XCTAssertEqual(settings.cleanupProvider, .gemini)
        XCTAssertEqual(settings.cleanupModel, Provider.gemini.defaultCleanupModel)
        settings.selectProvider(.openai)
        XCTAssertEqual(settings.effectiveCleanupProvider, .gemini)
        XCTAssertEqual(settings.effectiveCleanupModel, Provider.gemini.defaultCleanupModel)
    }
    func testTurningAutomaticOnEnablesCleanupButNeverAutoOptsIntoPaidProvider() {
        var settings = QuillSettings()
        settings.setCleanupEnabled(false)
        settings.setCleanupAutomatic(true)
        XCTAssertTrue(settings.effectiveCleanupEnabled)
        settings.selectProvider(.openai)
        XCTAssertFalse(settings.effectiveCleanupEnabled)
        XCTAssertTrue(settings.cleanupAutomatic)
        settings.setCleanupEnabled(true)
        XCTAssertFalse(settings.cleanupAutomatic)
        XCTAssertTrue(settings.effectiveCleanupEnabled)
        XCTAssertEqual(settings.cleanupProvider, .openai)
        XCTAssertEqual(settings.cleanupModel, Provider.openai.defaultCleanupModel)
    }
    func testManualModelsPersistIndependentlyAcrossProvidersAndDiskRoundtrip() throws {
        var settings = QuillSettings()
        settings.setCleanupModel("  chosen-groq  ")
        settings.selectCleanupProvider(.gemini)
        settings.setCleanupModel("chosen-gemini")
        settings.selectCleanupProvider(.groq)
        XCTAssertEqual(settings.cleanupModel, "chosen-groq")
        settings = try JSONDecoder().decode(QuillSettings.self, from: JSONEncoder().encode(settings))
        settings.selectCleanupProvider(.gemini)
        XCTAssertEqual(settings.cleanupModel, "chosen-gemini")
        settings.selectCleanupProvider(.custom)
        XCTAssertEqual(settings.cleanupModel, "")
        settings.setCleanupModel("private-cleanup")
        settings.selectCleanupProvider(.gemini); settings.selectCleanupProvider(.custom)
        XCTAssertEqual(settings.cleanupModel, "private-cleanup")
    }
    func testEditingAutomaticModelSnapshotsProviderAndLeavesSpeechUnchanged() {
        var settings = QuillSettings(); settings.selectProvider(.openrouter)
        let speech = settings.model
        settings.setCleanupModel("new-cleanup")
        XCTAssertEqual(settings.cleanupProvider, .openrouter)
        XCTAssertFalse(settings.cleanupAutomatic)
        XCTAssertEqual(settings.model, speech)
    }
    func testSessionCapMatchesAndroidSelectableBounds() {
        var settings = QuillSettings()
        for cap in [60, 120, 180, 240, 300] { settings.sessionCapSeconds = cap; settings.sanitize(); XCTAssertEqual(settings.sessionCapSeconds, cap) }
        settings.sessionCapSeconds = 0; settings.sanitize(); XCTAssertEqual(settings.sessionCapSeconds, 60)
        settings.sessionCapSeconds = 600; settings.sanitize(); XCTAssertEqual(settings.sessionCapSeconds, 300)
    }
    func testOlderSettingsWithoutPerProviderModelMapStillDecode() throws {
        let encoded = try JSONEncoder().encode(QuillSettings())
        var object = try XCTUnwrap(JSONSerialization.jsonObject(with: encoded) as? [String: Any])
        object.removeValue(forKey: "rememberedCleanupModels")
        let settings = try JSONDecoder().decode(QuillSettings.self, from: JSONSerialization.data(withJSONObject: object))
        XCTAssertEqual(settings, QuillSettings())
    }
}
