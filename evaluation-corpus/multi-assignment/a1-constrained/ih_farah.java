public class MoneyFarah {
    private final long cents;

    public MoneyFarah(long cents) {
        this.cents = cents;
    }

    public long getCents() {
        return cents;
    }

    public MoneyFarah plus(MoneyFarah other) {
        return new MoneyFarah(this.cents + other.cents);
    }

    private String pad(long minor) {
        return minor < 10 ? "0" + minor : Long.toString(minor);
    }

    public String toString() {
        return "$" + (cents / 100) + "." + pad(cents % 100);
    }
}
