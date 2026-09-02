public class PairDana {
    private final String head;
    private final String tail;

    public PairDana(String head, String tail) {
        this.head = head;
        this.tail = tail;
    }

    public String getHead() {
        return head;
    }

    public String getTail() {
        return tail;
    }

    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof PairDana)) {
            return false;
        }
        PairDana that = (PairDana) other;
        return head.equals(that.head) && tail.equals(that.tail);
    }

    public String toString() {
        return "(" + head + ", " + tail + ")";
    }
}
