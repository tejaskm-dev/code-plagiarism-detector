/**
 * Converts integers into Roman numerals.
 * Written for the week 4 exercise.
 */
public class RomanGus {

    // Symbol for each magnitude, largest first.
    private static final String[] TOKENS = {"M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"};

    // Numeric weight matching each symbol above.
    private static final int[] WEIGHTS = {1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};

    public String convert(int target) {
        if (target <= 0 || target > 3999) {
            throw new IllegalArgumentException("out of range: " + target);
        }
        StringBuilder accumulator = new StringBuilder();
        int leftToSpend = target;
        for (int position = 0; position < WEIGHTS.length; position++) {
            while (leftToSpend >= WEIGHTS[position]) {
                accumulator.append(TOKENS[position]);
                leftToSpend -= WEIGHTS[position];
            }
        }
        return accumulator.toString();
    }
}
