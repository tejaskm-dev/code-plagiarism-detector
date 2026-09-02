public class PairHana {
    private final String alpha;
    private final String beta;

    public PairHana(String alpha, String beta) {
        this.alpha = alpha;
        this.beta = beta;
    }

    public String getAlpha() {
        return alpha;
    }

    public String getBeta() {
        return beta;
    }

    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof PairHana)) {
            return false;
        }
        PairHana that = (PairHana) other;
        return alpha.equals(that.alpha) && beta.equals(that.beta);
    }

    public String toString() {
        return "(" + alpha + ", " + beta + ")";
    }
}
