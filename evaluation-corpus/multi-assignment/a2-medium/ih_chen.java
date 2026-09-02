public class StackChen {

    private static class Node {
        final int value;
        final Node below;

        Node(int value, Node below) {
            this.value = value;
            this.below = below;
        }
    }

    private Node top = null;
    private int depth = 0;

    public void push(int value) {
        top = new Node(value, top);
        depth++;
    }

    public int pop() {
        if (top == null) {
            throw new IllegalStateException("empty");
        }
        int value = top.value;
        top = top.below;
        depth--;
        return value;
    }

    public int peek() {
        if (top == null) {
            throw new IllegalStateException("empty");
        }
        return top.value;
    }

    public boolean isEmpty() {
        return top == null;
    }

    public int size() {
        return depth;
    }
}
