import java.util.ArrayList;
import java.util.List;

public class ReportJia {

    private final List<String> lines = new ArrayList<>();
    private final String title;

    public ReportJia(String title) {
        this.title = title;
    }

    public void addLine(String line) {
        if (line == null) {
            throw new IllegalArgumentException("line must not be null");
        }
        lines.add(line);
    }

    public int lineCount() {
        return lines.size();
    }

    public String getTitle() {
        return title;
    }

    public static void main(String[] args) {
        ReportJia report = new ReportJia("demo");
        report.addLine("alpha");
        report.addLine("beta");
        System.out.println(report.render());
    }

    public String render() {
        StringBuilder out = new StringBuilder(title);
        out.append('\n');
        for (String line : lines) {
            out.append("  - ").append(line).append('\n');
        }
        return out.toString();
    }
}
