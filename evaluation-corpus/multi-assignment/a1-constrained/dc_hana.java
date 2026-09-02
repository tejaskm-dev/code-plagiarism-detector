/** Immutable amount of money, stored in minor units. */
public class MoneyHana {
    private final long minorUnits;

    public MoneyHana(long minorUnits) {
        this.minorUnits = minorUnits;
    }

    // Formats as dollars and cents.
    public String toString() {
        return String.format("$%d.%02d", minorUnits / 100, minorUnits % 100);
    }

    public MoneyHana plus(MoneyHana addend) {
        return new MoneyHana(this.minorUnits + addend.minorUnits);
    }

    public long getCents() {
        return minorUnits;
    }
}
