public class RomanDana {

    public String convert(int input) {
        StringBuilder builder = new StringBuilder();
        int value = input;

        while (value > 0) {
            if (value >= 1000) {
                builder.append('M');
                value = value - 1000;
            } else if (value >= 900) {
                builder.append("CM");
                value = value - 900;
            } else if (value >= 500) {
                builder.append('D');
                value = value - 500;
            } else if (value >= 100) {
                builder.append('C');
                value = value - 100;
            } else if (value >= 90) {
                builder.append("XC");
                value = value - 90;
            } else if (value >= 50) {
                builder.append('L');
                value = value - 50;
            } else if (value >= 10) {
                builder.append('X');
                value = value - 10;
            } else if (value >= 9) {
                builder.append("IX");
                value = value - 9;
            } else if (value >= 5) {
                builder.append('V');
                value = value - 5;
            } else if (value >= 4) {
                builder.append("IV");
                value = value - 4;
            } else {
                builder.append('I');
                value = value - 1;
            }
        }
        return builder.toString();
    }
}
