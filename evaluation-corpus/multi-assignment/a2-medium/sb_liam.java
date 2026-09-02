import java.util.ArrayList;
import java.util.List;

public class CalculatorLiam {

    private final List<String> history = new ArrayList<>();
    private final String label;

    public CalculatorLiam(String label) {
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
        int running = 0;
        int cursor = 0;
        StringBuilder number = new StringBuilder();
        while (cursor <= expression.length()) {
            char c = cursor == expression.length() ? '+' : expression.charAt(cursor);
            if (c == '+') {
                running += Integer.parseInt(number.toString().trim());
                number.setLength(0);
            } else {
                number.append(c);
            }
            cursor++;
        }
        log("evaluated");
        return running;
    }
}
