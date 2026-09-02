public class PairBen {
    private final String left;
    private final String right;

    public PairBen(String left, String right) {
        this.left = left;
        this.right = right;
    }

    public String getLeft() {
        return left;
    }

    public String getRight() {
        return right;
    }

    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof PairBen)) {
            return false;
        }
        PairBen that = (PairBen) other;
        return left.equals(that.left) && right.equals(that.right);
    }

    public String toString() {
        return "(" + left + ", " + right + ")";
    }
}
