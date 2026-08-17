import Foundation

// Port of windows/.../Stt/KeyValidator.cs — validates a key with a 400 ms silent WAV sent only to the
// selected provider, proving the exact request shape dictation uses without sending speech.
enum KeyValidation: Equatable { case valid, rejected, unverified(String) }

struct KeyValidator {
    let session: URLSession

    func validate(provider: SttProvider, key: String, model: String, customBaseURL: String) async -> KeyValidation {
        if key.trimmed.isEmpty { return .unverified("no key entered") }
        if model.trimmed.isEmpty { return .unverified("no model selected") }
        if provider == .custom && !isHTTPS(customBaseURL) { return .unverified("custom endpoint not configured") }

        let wav = Self.createSilentWav()
        defer { try? FileManager.default.removeItem(at: wav) }
        do {
            let client = try TranscriptionClientFactory.create(
                provider: provider, key: key.trimmed, customBaseURL: customBaseURL.trimmed, session: session)
            _ = try await client.transcribe(audioPath: wav, options: TranscriptionOptions(model: model.trimmed))
            return .valid
        } catch let e as TranscriptionException where e.message.range(of: "API key was rejected", options: .caseInsensitive) != nil {
            return .rejected
        } catch {
            return .unverified(concise(error))
        }
    }

    /// Returns false only for a confirmed quota/credit denial; nil stays honest about inconclusive results.
    func hasPaidAccess(provider: SttProvider, key: String, model: String, customBaseURL: String) async -> Bool? {
        let wav = Self.createSilentWav()
        defer { try? FileManager.default.removeItem(at: wav) }
        do {
            let client = try TranscriptionClientFactory.create(
                provider: provider, key: key.trimmed, customBaseURL: customBaseURL.trimmed, session: session)
            _ = try await client.transcribe(audioPath: wav, options: TranscriptionOptions(model: model.trimmed))
            return true
        } catch let e as TranscriptionException where
            e.message.range(of: "quota", options: .caseInsensitive) != nil ||
            e.message.range(of: "no available credit", options: .caseInsensitive) != nil {
            return false
        } catch {
            return nil
        }
    }

    private func isHTTPS(_ value: String) -> Bool {
        guard let url = URL(string: value) else { return false }
        return url.scheme?.lowercased() == "https" && (url.host?.isEmpty == false)
    }

    private func concise(_ error: Error) -> String {
        if let e = error as? TranscriptionException { return e.message }
        if (error as NSError).domain == NSURLErrorDomain { return "no network connection" }
        return "could not verify right now"
    }

    static func createSilentWav() -> URL {
        let sampleRate = 16_000, durationMs = 400
        let dataBytes = sampleRate * 2 * durationMs / 1000
        let url = FileManager.default.temporaryDirectory.appendingPathComponent("quill-keytest-\(UUID().uuidString).wav")
        var d = Data()
        func u32(_ v: UInt32) { var x = v.littleEndian; d.append(Data(bytes: &x, count: 4)) }
        func u16(_ v: UInt16) { var x = v.littleEndian; d.append(Data(bytes: &x, count: 2)) }
        d.append(Data("RIFF".utf8)); u32(UInt32(36 + dataBytes)); d.append(Data("WAVE".utf8))
        d.append(Data("fmt ".utf8)); u32(16); u16(1); u16(1); u32(UInt32(sampleRate))
        u32(UInt32(sampleRate * 2)); u16(2); u16(16)
        d.append(Data("data".utf8)); u32(UInt32(dataBytes)); d.append(Data(count: dataBytes))
        try? d.write(to: url)
        return url
    }
}
