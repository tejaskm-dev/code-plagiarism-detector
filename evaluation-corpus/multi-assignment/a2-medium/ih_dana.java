import java.util.ArrayDeque;
import java.util.Deque;

public class StackDana {
    private final Deque<Integer> delegate = new ArrayDeque<>();

    public void push(int value) {
        delegate.addFirst(value);
    }

    public int pop() {
        Integer head = delegate.pollFirst();
        if (head == null) {
            throw new IllegalStateException("stack underflow");
        }
        return head;
    }

    public int peek() {
        Integer head = delegate.peekFirst();
        if (head == null) {
            throw new IllegalStateException("stack underflow");
        }
        return head;
    }

    public boolean isEmpty() {
        return delegate.isEmpty();
    }

    public int size() {
        return delegate.size();
    }
}
