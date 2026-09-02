import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class LoaderJia {

    protected final List<String> lines = new ArrayList<>();
    private Path source;

    public LoaderJia(Path source) {
        this.source = source;
    }

    public void readAll() throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(source)) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
        }
    }

    public int lineCount() {
        return lines.size();
    }

    public Path getSource() {
        return source;
    }

    public String longestLine() {
        String best = "";
        for (String line : lines) {
            if (line.length() > best.length()) {
                best = line;
            }
        }
        return best;
    }
}
