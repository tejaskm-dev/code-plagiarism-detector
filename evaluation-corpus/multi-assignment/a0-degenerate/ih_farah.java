public class PairFarah {
    private final String name;
    private final String label;

    public PairFarah(String name, String label) {
        this.name = name;
        this.label = label;
    }

    public String getName() {
        return name;
    }

    public String getLabel() {
        return label;
    }

    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof PairFarah)) {
            return false;
        }
        PairFarah that = (PairFarah) other;
        return name.equals(that.name) && label.equals(that.label);
    }

    public String toString() {
        return "(" + name + ", " + label + ")";
    }
}
