public class RomanIvan {

    /* Builds the numeral by repeatedly taking the largest chunk that fits. */
    public String convert(int amount) {
        StringBuilder output = new StringBuilder();
        int balance = amount;

        while (balance > 0) {
            if (balance >= 1000) {
                output.append('M');
                balance = balance - 1000;
            } else if (balance >= 900) {
                output.append("CM");
                balance = balance - 900;
            } else if (balance >= 500) {
                output.append('D');
                balance = balance - 500;
            } else if (balance >= 100) {
                output.append('C');
                balance = balance - 100;
            } else if (balance >= 90) {
                output.append("XC");
                balance = balance - 90;
            } else if (balance >= 50) {
                output.append('L');
                balance = balance - 50;
            } else if (balance >= 10) {
                output.append('X');
                balance = balance - 10;
            } else if (balance >= 9) {
                output.append("IX");
                balance = balance - 9;
            } else if (balance >= 5) {
                output.append('V');
                balance = balance - 5;
            } else if (balance >= 4) {
                output.append("IV");
                balance = balance - 4;
            } else {
                output.append('I');
                balance = balance - 1;
            }
        }
        return output.toString();
    }
}
