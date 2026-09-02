import java.util.ArrayList;
import java.util.List;

public class BoxKwan {
    private final List<String> contents = new ArrayList<>();

    public void add(String item) {
        contents.add(item);
    }

    public int count() {
        return contents.size();
    }

    public String join() {
        return String.join("", contents);
    }
}
