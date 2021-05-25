public class TestDebuggee {

    public static void main(String[] args) {
        int a = 3;
        int b = 4;
        int sum = 0;
        if(a<b) {
            sum = a + b;
        }
        for(int i=0; i<a; i++){
            sum++;
        }
        System.out.println(sum);
    }
}