public class MoneyIvan {
    private final long units;

    public MoneyIvan(long units) {
        this.units = units;
    }

    public MoneyIvan plus(MoneyIvan extra) {
        long merged = this.units + extra.getCents();
        return new MoneyIvan(merged);
    }

    /* Renders the amount with two decimal places. */
    public String toString() {
        StringBuilder rendered = new StringBuilder("$");
        rendered.append(units / 100);
        rendered.append('.');
        long fraction = units % 100;
        if (fraction < 10) {
            rendered.append('0');
        }
        rendered.append(fraction);
        return rendered.toString();
    }

    public long getCents() {
        return this.units;
    }
}
