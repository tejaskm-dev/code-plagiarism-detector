import java.util.List;
import java.util.TreeSet;

/** Vocabulary and character-distribution oriented. */
public class AnalyserEvan {
    private final TreeSet<String> vocabulary = new TreeSet<>();
    private final int[] letterCounts = new int[26];

    public void load(List<String> input) {
        for (String line : input) {
            for (char c : line.toCharArray()) {
                if (Character.isLetter(c)) {
                    letterCounts[Character.toLowerCase(c) - 'a']++;
                }
            }
            for (String token : line.split("\\s+")) {
                String cleaned = token.replaceAll("[^A-Za-z]", "").toLowerCase();
                if (!cleaned.isEmpty()) {
                    vocabulary.add(cleaned);
                }
            }
        }
    }

    public int vocabularySize() {
        return vocabulary.size();
    }

    public char mostFrequentLetter() {
        int best = 0;
        for (int i = 1; i < letterCounts.length; i++) {
            if (letterCounts[i] > letterCounts[best]) {
                best = i;
            }
        }
        return (char) ('a' + best);
    }
}
