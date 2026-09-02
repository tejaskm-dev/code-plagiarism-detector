/**
 * A growable stack of integers backed by a plain array.
 */
public class StackHana {

    private int[] cells = new int[8];
    private int filled = 0;

    public boolean isEmpty() {
        return filled == 0;
    }

    public int size() {
        return filled;
    }

    // Doubles the backing array when it runs out of room.
    public void push(int element) {
        if (filled == cells.length) {
            int[] expanded = new int[cells.length * 2];
            System.arraycopy(cells, 0, expanded, 0, cells.length);
            cells = expanded;
        }
        cells[filled] = element;
        filled++;
    }

    public int peek() {
        if (filled == 0) {
            throw new IllegalStateException("stack is empty");
        }
        return cells[filled - 1];
    }

    public int pop() {
        if (filled == 0) {
            throw new IllegalStateException("stack is empty");
        }
        filled--;
        return cells[filled];
    }
}
