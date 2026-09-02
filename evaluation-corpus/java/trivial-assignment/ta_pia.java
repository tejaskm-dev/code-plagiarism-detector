public class EvenPia {

    public boolean isEven(int n) {
        return (n & 1) == 0;
    }

    public static void main(String[] args) {
        EvenPia checker = new EvenPia();
        System.out.println(checker.isEven(10));
    }
}
