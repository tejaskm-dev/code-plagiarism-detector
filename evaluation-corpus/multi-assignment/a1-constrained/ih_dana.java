public class MoneyDana {
    private final long cents;

    public MoneyDana(long cents) {
        this.cents = cents;
    }

    public long getCents() {
        return this.cents;
    }

    public MoneyDana plus(MoneyDana addend) {
        long combined = this.cents + addend.getCents();
        return new MoneyDana(combined);
    }

    public String toString() {
        StringBuilder text = new StringBuilder("$");
        text.append(cents / 100);
        text.append('.');
        long minor = cents % 100;
        if (minor < 10) {
            text.append('0');
        }
        text.append(minor);
        return text.toString();
    }
}
