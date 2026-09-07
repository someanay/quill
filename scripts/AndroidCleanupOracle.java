import com.tihor.quill.cleanup.CleanupGuard;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/** Runs the built Android policy directly; test expectations must not duplicate its algorithm. */
class AndroidCleanupOracle {
    public static void main(String[] args) throws Exception {
        var input = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        String line;
        while ((line = input.readLine()) != null) {
            var pair = line.split("\t", -1);
            var raw = new String(Base64.getDecoder().decode(pair[0]), StandardCharsets.UTF_8);
            var cleaned = new String(Base64.getDecoder().decode(pair[1]), StandardCharsets.UTF_8);
            System.out.println(CleanupGuard.INSTANCE.preservesMeaning(raw, cleaned));
        }
    }
}
