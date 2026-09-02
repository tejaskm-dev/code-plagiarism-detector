public class StackAlice {
    private int[] items = new int[8];
    private int count = 0;

    public void push(int value) {
        if (count == items.length) {
            int[] bigger = new int[items.length * 2];
            System.arraycopy(items, 0, bigger, 0, items.length);
            items = bigger;
        }
        items[count] = value;
        count++;
    }

    public int pop() {
        if (count == 0) {
            throw new IllegalStateException("stack is empty");
        }
        count--;
        return items[count];
    }

    public int peek() {
        if (count == 0) {
            throw new IllegalStateException("stack is empty");
        }
        return items[count - 1];
    }

    public boolean isEmpty() {
        return count == 0;
    }

    public int size() {
        return count;
    }
}
