public class MoneyGus
{
    private final long cents;

    public MoneyGus(long cents)
    {
        this.cents = cents;
    }

    public long getCents()
    {
        return cents;
    }

    public MoneyGus plus(MoneyGus other)
    {
        return new MoneyGus(this.cents + other.cents);
    }

    public String toString()
    {
        return String.format("$%d.%02d", cents / 100, cents % 100);
    }
}
