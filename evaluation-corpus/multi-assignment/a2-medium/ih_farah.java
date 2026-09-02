import java.util.Optional;

public class StackFarah {
    private int[] buffer = new int[4];
    private int used = 0;

    private void grow() {
        int[] replacement = new int[buffer.length + buffer.length / 2 + 1];
        for (int i = 0; i < used; i++) {
            replacement[i] = buffer[i];
        }
        buffer = replacement;
    }

    public void push(int value) {
        if (used >= buffer.length) {
            grow();
        }
        buffer[used++] = value;
    }

    public Optional<Integer> tryPop() {
        if (used == 0) {
            return Optional.empty();
        }
        return Optional.of(buffer[--used]);
    }

    public int pop() {
        return tryPop().orElseThrow(() -> new IllegalStateException("empty stack"));
    }

    public int peek() {
        if (used == 0) {
            throw new IllegalStateException("empty stack");
        }
        return buffer[used - 1];
    }

    public boolean isEmpty() {
        return used == 0;
    }

    public int size() {
        return used;
    }
}
