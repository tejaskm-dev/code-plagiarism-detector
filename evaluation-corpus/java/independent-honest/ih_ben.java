import java.util.TreeMap;

public class RomanBen {

    private final TreeMap<Integer, String> table = new TreeMap<>();

    public RomanBen() {
        table.put(1, "I");
        table.put(4, "IV");
        table.put(5, "V");
        table.put(9, "IX");
        table.put(10, "X");
        table.put(40, "XL");
        table.put(50, "L");
        table.put(90, "XC");
        table.put(100, "C");
        table.put(400, "CD");
        table.put(500, "D");
        table.put(900, "CM");
        table.put(1000, "M");
    }

    public String convert(int number) {
        if (number < 1) {
            return "";
        }
        int floorKey = table.floorKey(number);
        if (number == floorKey) {
            return table.get(number);
        }
        return table.get(floorKey) + convert(number - floorKey);
    }
}
