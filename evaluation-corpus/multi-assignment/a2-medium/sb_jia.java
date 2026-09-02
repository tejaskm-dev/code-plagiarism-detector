import java.util.ArrayList;
import java.util.List;

public class CalculatorJia {

    private final List<String> history = new ArrayList<>();
    private final String label;

    public CalculatorJia(String label) {
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
        String[] parts = expression.split("\\+");
        int total = 0;
        for (String part : parts) {
            total += Integer.parseInt(part.trim());
        }
        log(expression + " = " + total);
        return total;
    }
}
