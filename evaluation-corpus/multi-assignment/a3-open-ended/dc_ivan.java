import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class AnalyserIvan {

    public static final class Match {
        public final int row;
        public final String content;
        public final int tally;

        Match(int row, String content, int tally) {
            this.row = row;
            this.content = content;
            this.tally = tally;
        }
    }

    private final List<String> documents = new ArrayList<>();

    private int tallyWithin(String subject, String term) {
        int seen = 0;
        int start = 0;
        while (true) {
            int found = subject.indexOf(term, start);
            if (found < 0) {
                return seen;
            }
            seen++;
            start = found + term.length();
        }
    }

    public void load(List<String> incoming) {
        documents.clear();
        documents.addAll(incoming);
    }

    public List<Match> search(String term) {
        List<Match> matches = new ArrayList<>();
        for (int i = 0; i < documents.size(); i++) {
            int tally = tallyWithin(documents.get(i), term);
            if (tally > 0) {
                matches.add(new Match(i + 1, documents.get(i), tally));
            }
        }
        matches.sort(Comparator.comparingInt((Match m) -> m.tally).reversed());
        return matches;
    }
}
