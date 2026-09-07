using System.Text.RegularExpressions;
namespace Quill.Windows;
public static class ContactVocabulary {
 public static List<string> Extract(IEnumerable<string> names) {
  var terms = new List<string>(); var seen = new HashSet<string>(StringComparer.OrdinalIgnoreCase);
  void Offer(string term) { if (terms.Count < 1000 && seen.Add(term)) terms.Add(term); }
  foreach (var raw in names) {
   if (terms.Count >= 1000) break;
   var name = Regex.Replace(raw.Trim(),@"\s+"," "); if (name.Length is < 2 or > 48) continue;
   var tokens = name.Split(' '); if (tokens.Length > 4 || tokens.Any(t => !Regex.IsMatch(t,@"^\p{L}+(?:['\-]\p{L}+)*\.?$"))) continue;
   Offer(name); if (tokens.Length > 1) foreach (var token in tokens) if (token.Length >= 2) Offer(token);
  }
  return terms;
 }
}
