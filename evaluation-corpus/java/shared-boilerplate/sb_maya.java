import java.util.ArrayList;
import java.util.List;

public class ReportMaya {

    private final List<String> lines = new ArrayList<>();
    private final String title;

    public ReportMaya(String title) {
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
        ReportMaya report = new ReportMaya("demo");
        report.addLine("alpha");
        report.addLine("beta");
        System.out.println(report.render());
    }

    public String render() {
        StringBuilder buffer = new StringBuilder();
        buffer.append("=== ").append(title).append(" ===");
        lines.forEach(entry -> buffer.append('\n').append('\t').append(entry));
        return buffer.toString();
    }
}
