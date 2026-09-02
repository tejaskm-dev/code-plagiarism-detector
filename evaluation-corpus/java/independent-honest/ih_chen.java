public class RomanChen {

    private static final String[] THOUSANDS = {"", "M", "MM", "MMM"};
    private static final String[] HUNDREDS = {"", "C", "CC", "CCC", "CD", "D", "DC", "DCC", "DCCC", "CM"};
    private static final String[] TENS = {"", "X", "XX", "XXX", "XL", "L", "LX", "LXX", "LXXX", "XC"};
    private static final String[] UNITS = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX"};

    public String convert(int number) {
        int thousandsDigit = number / 1000;
        int hundredsDigit = (number % 1000) / 100;
        int tensDigit = (number % 100) / 10;
        int unitsDigit = number % 10;
        return THOUSANDS[thousandsDigit] + HUNDREDS[hundredsDigit] + TENS[tensDigit] + UNITS[unitsDigit];
    }
}
