import java.util.ArrayList;
import java.util.List;

public class CalculatorKwan {

    private final List<String> history = new ArrayList<>();
    private final String label;

    public CalculatorKwan(String label) {
        this.label = label;
    }

    protected void log(String entry) {
        history.add(entry);
    }

    public List<String> getHistory() {
        return List.copyOf(history);
    }

    public String getLabel() {
        return label;
    }

    public void reset() {
        history.clear();
    }

    public int evaluate(String expression) {
        int result = java.util.Arrays.stream(expression.split("\\+"))
                .map(String::trim)
                .mapToInt(Integer::parseInt)
                .sum();
        log(expression);
        return result;
    }
}
