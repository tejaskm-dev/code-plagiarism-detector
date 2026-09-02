public class StackEvan {
    private static final int CAPACITY = 256;
    private final int[] slots = new int[CAPACITY];
    private int pointer = -1;

    public void push(int value) {
        if (pointer + 1 >= CAPACITY) {
            throw new IllegalStateException("stack overflow");
        }
        pointer = pointer + 1;
        slots[pointer] = value;
    }

    public int pop() {
        int value = peek();
        pointer = pointer - 1;
        return value;
    }

    public int peek() {
        if (pointer < 0) {
            throw new IllegalStateException("stack is empty");
        }
        return slots[pointer];
    }

    public boolean isEmpty() {
        return pointer < 0;
    }

    public int size() {
        return pointer + 1;
    }
}
