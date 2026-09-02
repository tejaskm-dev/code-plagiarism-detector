import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Regex-driven extraction of structured tokens. */
public class AnalyserBen {
    private static final Pattern EMAIL = Pattern.compile("[\\w.]+@[\\w.]+\\.\\w+");
    private static final Pattern NUMBER = Pattern.compile("-?\\d+(\\.\\d+)?");

    private String document = "";

    public void load(List<String> input) {
        document = String.join("\n", input);
    }

    public List<String> emails() {
        return matchAll(EMAIL);
    }

    public List<String> numbers() {
        return matchAll(NUMBER);
    }

    private List<String> matchAll(Pattern pattern) {
        Matcher matcher = pattern.matcher(document);
        List<String> found = new java.util.ArrayList<>();
        while (matcher.find()) {
            found.add(matcher.group());
        }
        return found;
    }
}
