import java.util.ArrayList;
import java.util.List;

public class StackBen {
    private final List<Integer> storage = new ArrayList<>();

    public void push(int value) {
        storage.add(value);
    }

    public int pop() {
        if (storage.isEmpty()) {
            throw new IllegalStateException("nothing to pop");
        }
        return storage.remove(storage.size() - 1);
    }

    public int peek() {
        if (storage.isEmpty()) {
            throw new IllegalStateException("nothing to peek");
        }
        return storage.get(storage.size() - 1);
    }

    public boolean isEmpty() {
        return storage.isEmpty();
    }

    public int size() {
        return storage.size();
    }
}
