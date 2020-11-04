public class TestDebuggee {

    public static void main(String[] args){
        String name = "Piumi";
        int a = 2;
        int b = 3;

        System.out.println("Hi " + name);

        add(a, b);
    }

    public static void add(int a, int b){
        int total = a + b;
        System.out.println(total);
    }
}