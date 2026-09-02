import java.util.ArrayList;
import java.util.List;

public class BoxJia {
    private final List<String> contents = new ArrayList<>();

    public void add(String item) {
        contents.add(item);
    }

    public int count() {
        return contents.size();
    }

    public String join() {
        StringBuilder b = new StringBuilder();
        for (String item : contents) {
            b.append(item);
        }
        return b.toString();
    }
}
