public class EvenNina {

    public boolean isEven(int number) {
        return number % 2 == 0;
    }

    public static void main(String[] args) {
        EvenNina checker = new EvenNina();
        System.out.println(checker.isEven(4));
    }
}
