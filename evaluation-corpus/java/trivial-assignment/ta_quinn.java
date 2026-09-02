public class EvenQuinn {

    public boolean isEven(int candidate) {
        int remainder = candidate % 2;
        return remainder == 0;
    }

    public static void main(String[] args) {
        EvenQuinn checker = new EvenQuinn();
        System.out.println(checker.isEven(13));
    }
}
