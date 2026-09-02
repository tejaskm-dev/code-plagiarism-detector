import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class LoaderLiam {

    protected final List<String> lines = new ArrayList<>();
    private Path source;

    public LoaderLiam(Path source) {
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
        int bestIndex = -1;
        int bestLength = -1;
        for (int i = 0; i < lines.size(); i++) {
            int length = lines.get(i).length();
            if (length > bestLength) {
                bestLength = length;
                bestIndex = i;
            }
        }
        return bestIndex < 0 ? "" : lines.get(bestIndex);
    }
}
