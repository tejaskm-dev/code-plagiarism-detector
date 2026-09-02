import java.util.TreeMap;

public class RomanHana
{
    private final TreeMap<Integer, String> lookup = new TreeMap<>();

    public String convert(int target)
    {
        if (target < 1)
        {
            return "";
        }
        int nearest = lookup.floorKey(target);
        if (target == nearest)
        {
            return lookup.get(target);
        }
        return lookup.get(nearest) + convert(target - nearest);
    }

    public RomanHana()
    {
        lookup.put(1, "I");
        lookup.put(4, "IV");
        lookup.put(5, "V");
        lookup.put(9, "IX");
        lookup.put(10, "X");
        lookup.put(40, "XL");
        lookup.put(50, "L");
        lookup.put(90, "XC");
        lookup.put(100, "C");
        lookup.put(400, "CD");
        lookup.put(500, "D");
        lookup.put(900, "CM");
        lookup.put(1000, "M");
    }
}
