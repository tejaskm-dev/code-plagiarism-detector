public class MoneyEvan {
    private final long pennies;

    public MoneyEvan(long pennies) {
        this.pennies = pennies;
    }

    public long getCents() {
        return pennies;
    }

    public MoneyEvan plus(MoneyEvan other) {
        return new MoneyEvan(pennies + other.pennies);
    }

    public String toString() {
        double asDollars = pennies / 100.0;
        return String.format("$%.2f", asDollars);
    }
}
