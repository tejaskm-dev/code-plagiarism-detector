import java.util.ArrayList;
import java.util.List;

public class ReportLiam {

    private final List<String> lines = new ArrayList<>();
    private final String title;

    public ReportLiam(String title) {
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
        ReportLiam report = new ReportLiam("demo");
        report.addLine("alpha");
        report.addLine("beta");
        System.out.println(report.render());
    }

    public String render() {
        String body = "";
        int index = 1;
        for (String line : lines) {
            body = body + index + ". " + line + System.lineSeparator();
            index++;
        }
        return title + System.lineSeparator() + body;
    }
}
