import java.util.ArrayList;
import java.util.List;

public class ReportKwan {

    private final List<String> lines = new ArrayList<>();
    private final String title;

    public ReportKwan(String title) {
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
        ReportKwan report = new ReportKwan("demo");
        report.addLine("alpha");
        report.addLine("beta");
        System.out.println(report.render());
    }

    public String render() {
        return title + "\n" + String.join("\n", lines.stream().map(l -> "  * " + l).toList());
    }
}
