public class PairEvan {
    private final String one;
    private final String two;

    public PairEvan(String one, String two) {
        this.one = one;
        this.two = two;
    }

    public String getOne() {
        return one;
    }

    public String getTwo() {
        return two;
    }

    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof PairEvan)) {
            return false;
        }
        PairEvan that = (PairEvan) other;
        return one.equals(that.one) && two.equals(that.two);
    }

    public String toString() {
        return "(" + one + ", " + two + ")";
    }
}
