public class EvenOmar {

    public boolean isEven(int value) {
        if (value % 2 == 0) {
            return true;
        } else {
            return false;
        }
    }

    public static void main(String[] args) {
        EvenOmar checker = new EvenOmar();
        System.out.println(checker.isEven(7));
    }
}
