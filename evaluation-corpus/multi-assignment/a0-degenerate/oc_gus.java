public class PairGus {

    private final String first;
    private final String second;

    public PairGus(String first, String second) {

        this.first = first;
        this.second = second;
    }


    public String getFirst() {

        return first;
    }


    public String getSecond() {

        return second;
    }


    public boolean equals(Object other) {

        if (this == other) {

            return true;
        }

        if (!(other instanceof PairGus)) {

            return false;
        }

        PairGus that = (PairGus) other;
        return first.equals(that.first) && second.equals(that.second);
    }


    public String toString() {

        return "(" + first + ", " + second + ")";
    }

}
