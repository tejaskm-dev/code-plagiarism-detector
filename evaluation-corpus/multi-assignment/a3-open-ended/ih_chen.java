import java.util.List;

/** Readability metrics: sentence and syllable oriented. */
public class AnalyserChen {
    private String[] sentences = new String[0];

    public void load(List<String> input) {
        sentences = String.join(" ", input).split("[.!?]+");
    }

    public double averageSentenceLength() {
        if (sentences.length == 0) {
            return 0.0;
        }
        int words = 0;
        for (String sentence : sentences) {
            words += sentence.trim().isEmpty() ? 0 : sentence.trim().split("\\s+").length;
        }
        return (double) words / sentences.length;
    }

    public int sentenceCount() {
        int nonEmpty = 0;
        for (String sentence : sentences) {
            if (!sentence.trim().isEmpty()) {
                nonEmpty++;
            }
        }
        return nonEmpty;
    }
}
