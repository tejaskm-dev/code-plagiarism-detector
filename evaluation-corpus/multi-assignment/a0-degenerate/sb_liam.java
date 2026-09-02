import java.util.ArrayList;
import java.util.List;

public class BoxLiam {
    private final List<String> contents = new ArrayList<>();

    public void add(String item) {
        contents.add(item);
    }

    public int count() {
        return contents.size();
    }

    public String join() {
        String out = "";
        int i = 0;
        while (i < contents.size()) {
            out = out + contents.get(i);
            i = i + 1;
        }
        return out;
    }
}
