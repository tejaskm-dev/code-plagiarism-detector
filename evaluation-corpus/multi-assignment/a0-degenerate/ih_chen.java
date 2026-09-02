public class PairChen {
    private final String key;
    private final String value;

    public PairChen(String key, String value) {
        this.key = key;
        this.value = value;
    }

    public String getKey() {
        return key;
    }

    public String getValue() {
        return value;
    }

    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof PairChen)) {
            return false;
        }
        PairChen that = (PairChen) other;
        return key.equals(that.key) && value.equals(that.value);
    }

    public String toString() {
        return "(" + key + ", " + value + ")";
    }
}
