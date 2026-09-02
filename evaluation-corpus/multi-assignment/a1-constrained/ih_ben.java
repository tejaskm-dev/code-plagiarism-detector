public class MoneyBen {
    private final long amount;

    public MoneyBen(long amount) {
        this.amount = amount;
    }

    public long getCents() {
        return amount;
    }

    public MoneyBen plus(MoneyBen other) {
        return new MoneyBen(amount + other.getCents());
    }

    public String toString() {
        long dollars = amount / 100;
        long remainder = amount % 100;
        return "$" + dollars + "." + (remainder < 10 ? "0" + remainder : "" + remainder);
    }
}
