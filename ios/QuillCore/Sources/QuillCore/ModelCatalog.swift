import Foundation

public enum ModelUse: Sendable { case dictation, cleanup }

public enum ModelCatalog {
    public static func knownFree(_ provider: Provider, _ id: String) -> Bool {
        switch provider {
        case .groq: return ["whisper-large-v3-turbo","whisper-large-v3","llama-3.3-70b-versatile"].contains(id)
        case .gemini: return ["gemini-3.8-flash","gemini-3.7-flash","gemini-3.6-flash","gemini-3.5-flash-lite","gemini-3.1-flash-lite","gemini-3.5-transcribe"].contains(id)
        case .openrouter: return id == "openrouter/free" || id.hasSuffix(":free")
        default: return false
        }
    }
    public static func parse(data: Data, provider: Provider, use: ModelUse) throws -> (models: [ModelOption], next: String?) {
        guard let root = try JSONSerialization.jsonObject(with: data) as? [String:Any], let rows = root[provider == .gemini ? "models" : "data"] as? [[String:Any]] else { throw QuillError.malformedResponse }
        let models = rows.compactMap { row -> ModelOption? in
            guard var id = row[provider == .gemini ? "name" : "id"] as? String else { return nil }
            if id.hasPrefix("models/") { id = String(id.dropFirst(7)) }
            guard !id.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty, id.count <= 250, row["active"] as? Bool != false else { return nil }
            let lower = id.lowercased(), speech = ["whisper","transcribe","transcription"].contains(where: { lower.contains($0) })
            let outputs = (row["architecture"] as? [String:Any])?["output_modalities"] as? [String] ?? []
            let methods = row["supportedGenerationMethods"] as? [String] ?? []
            if use == .cleanup {
                guard !speech, !["guard","safety","tts","orpheus","playai","speech","audio","embed","compound","realtime","image","live","robotics","computer-use"].contains(where: { lower.contains($0) }), provider != .gemini || (lower.hasPrefix("gemini-") && methods.contains("generateContent")), provider != .openrouter || outputs.contains("text") else { return nil }
            } else if provider == .openrouter { guard outputs.contains("transcription") else { return nil } }
            else if provider == .gemini { guard lower.hasPrefix("gemini-"), methods.contains("generateContent"), !["live","tts","image","robotics","computer-use","embedding"].contains(where: { lower.contains($0) }) else { return nil } }
            else if provider != .custom { guard speech, !lower.contains("diariz"), !lower.contains("realtime") else { return nil } }
            let pricing = row["pricing"] as? [String:Any]
            let prices = pricing?.values.compactMap { Double(String(describing: $0)) } ?? []
            let free = pricing == nil ? knownFree(provider, id) : pricing?["prompt"] != nil && pricing?["completion"] != nil && prices.count == pricing?.count && prices.allSatisfy { $0 == 0 }
            return ModelOption(id, name: row["displayName"] as? String ?? row["name"] as? String ?? id, isFree: free, isPaid: prices.contains { $0 > 0 } || provider == .openai || id == "gemini-3.1-pro-preview", created: row["created"] as? Double ?? 0)
        }
        return (rank(models, use: use), (root["nextPageToken"] as? String).flatMap { $0.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? nil : $0 })
    }
    public static func rank(_ models: [ModelOption], use: ModelUse) -> [ModelOption] {
        var seen: Set<String> = []
        func score(_ id: String) -> Int { (["preview","experimental","exp-","reasoning"].contains { id.contains($0) } ? 20 : 0) + ((use == .dictation ? ["turbo","mini","flash"] : ["versatile","instant","instruct"]).contains { id.contains($0) } ? 0 : 5) }
        func version(_ id: String) -> Double {
            let regex = try! NSRegularExpression(pattern: "[0-9]+(?:\\.[0-9]+)?")
            guard let match = regex.firstMatch(in: id, range: NSRange(id.startIndex..., in: id)) else { return 0 }
            return Double((id as NSString).substring(with: match.range)) ?? 0
        }
        return models.filter { seen.insert($0.id).inserted }.sorted { a,b in
            if a.isFree != b.isFree { return a.isFree }
            if score(a.id) != score(b.id) { return score(a.id) < score(b.id) }
            if a.created != b.created { return a.created > b.created }
            if version(a.id) != version(b.id) { return version(a.id) > version(b.id) }
            return a.id < b.id
        }
    }
}
