public class StackIvan {

    private Link head = null;
    private int height = 0;

    private static class Link {
        final int payload;
        final Link beneath;

        Link(int payload, Link beneath) {
            this.payload = payload;
            this.beneath = beneath;
        }
    }

    public int size() {
        return height;
    }

    public boolean isEmpty() {
        return head == null;
    }

    public void push(int payload) {
        head = new Link(payload, head);
        height++;
    }

    public int peek() {
        if (head == null) {
            throw new IllegalStateException("empty");
        }
        return head.payload;
    }

    public int pop() {
        if (head == null) {
            throw new IllegalStateException("empty");
        }
        int payload = head.payload;
        head = head.beneath;
        height--;
        return payload;
    }
}
