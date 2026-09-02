import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Word-frequency oriented analyser. */
public class AnalyserAlice {
    private final List<String> lines = new ArrayList<>();

    public void load(List<String> input) {
        lines.addAll(input);
    }

    public Map<String, Integer> wordFrequencies() {
        Map<String, Integer> counts = new HashMap<>();
        for (String line : lines) {
            for (String word : line.toLowerCase().split("\\W+")) {
                if (!word.isEmpty()) {
                    counts.merge(word, 1, Integer::sum);
                }
            }
        }
        return counts;
    }

    public String mostCommonWord() {
        String best = null;
        int bestCount = -1;
        for (Map.Entry<String, Integer> entry : wordFrequencies().entrySet()) {
            if (entry.getValue() > bestCount) {
                best = entry.getKey();
                bestCount = entry.getValue();
            }
        }
        return best;
    }
}
