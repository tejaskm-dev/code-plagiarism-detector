public class MoneyChen {
    private final long value;

    public MoneyChen(long value) {
        if (value < 0) {
            throw new IllegalArgumentException("negative money");
        }
        this.value = value;
    }

    public long getCents() {
        return value;
    }

    public MoneyChen plus(MoneyChen other) {
        return new MoneyChen(this.value + other.value);
    }

    public String toString() {
        return String.format("$%d.%02d", value / 100, value % 100);
    }
}
