import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/** Pipeline/filter oriented design. */
public class AnalyserFarah {
    private List<String> data = new ArrayList<>();
    private final List<Predicate<String>> filters = new ArrayList<>();

    public void load(List<String> input) {
        data = new ArrayList<>(input);
    }

    public AnalyserFarah where(Predicate<String> filter) {
        filters.add(filter);
        return this;
    }

    public List<String> collect() {
        List<String> out = new ArrayList<>();
        outer:
        for (String line : data) {
            for (Predicate<String> filter : filters) {
                if (!filter.test(line)) {
                    continue outer;
                }
            }
            out.add(line);
        }
        return out;
    }

    public long countMatching() {
        return collect().size();
    }
}
