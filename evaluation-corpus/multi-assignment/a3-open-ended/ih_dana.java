import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Line-oriented search with ranking. */
public class AnalyserDana {

    public static final class Hit {
        public final int lineNumber;
        public final String text;
        public final int occurrences;

        Hit(int lineNumber, String text, int occurrences) {
            this.lineNumber = lineNumber;
            this.text = text;
            this.occurrences = occurrences;
        }
    }

    private final List<String> corpus = new ArrayList<>();

    public void load(List<String> input) {
        corpus.clear();
        corpus.addAll(input);
    }

    public List<Hit> search(String needle) {
        List<Hit> hits = new ArrayList<>();
        for (int i = 0; i < corpus.size(); i++) {
            int count = countOccurrences(corpus.get(i), needle);
            if (count > 0) {
                hits.add(new Hit(i + 1, corpus.get(i), count));
            }
        }
        hits.sort(Comparator.comparingInt((Hit h) -> h.occurrences).reversed());
        return hits;
    }

    private int countOccurrences(String haystack, String needle) {
        int total = 0;
        int from = 0;
        while (true) {
            int at = haystack.indexOf(needle, from);
            if (at < 0) {
                return total;
            }
            total++;
            from = at + needle.length();
        }
    }
}
