public class MoneyAlice {
    private final long cents;

    public MoneyAlice(long cents) {
        this.cents = cents;
    }

    public long getCents() {
        return cents;
    }

    public MoneyAlice plus(MoneyAlice other) {
        return new MoneyAlice(this.cents + other.cents);
    }

    public String toString() {
        return String.format("$%d.%02d", cents / 100, cents % 100);
    }
}
