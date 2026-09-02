public class PairIvan {
    private final String start;
    private final String finish;

    public PairIvan(String start, String finish) {
        this.start = start;
        this.finish = finish;
    }

    public String getStart() {
        return start;
    }

    public String getFinish() {
        return finish;
    }

    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof PairIvan)) {
            return false;
        }
        PairIvan that = (PairIvan) other;
        return start.equals(that.start) && finish.equals(that.finish);
    }

    public String toString() {
        return "(" + start + ", " + finish + ")";
    }
}
