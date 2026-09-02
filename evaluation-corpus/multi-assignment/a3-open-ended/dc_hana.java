import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AnalyserHana {

    private final List<String> buffer = new ArrayList<>();

    // Returns whichever term appears most often across the loaded text.
    public String mostCommonWord() {
        String champion = null;
        int championTally = -1;
        for (Map.Entry<String, Integer> pair : wordFrequencies().entrySet()) {
            if (pair.getValue() > championTally) {
                champion = pair.getKey();
                championTally = pair.getValue();
            }
        }
        return champion;
    }

    public void load(List<String> incoming) {
        buffer.addAll(incoming);
    }

    /* Tallies every word, case-insensitively. */
    public Map<String, Integer> wordFrequencies() {
        Map<String, Integer> tally = new HashMap<>();
        for (String row : buffer) {
            for (String term : row.toLowerCase().split("\\W+")) {
                if (!term.isEmpty()) {
                    tally.merge(term, 1, Integer::sum);
                }
            }
        }
        return tally;
    }
}
