import java.lang.String;

public class Debuggee {
    static int a = 3;

    public static void main(String[] args) {
        int b = 4;
        int sum = 0;
        if(a<b) {
            sum = add(a,b);
        }
        else{
            sum = 0;
        }
        System.out.println(sum);
    }

    public static int add(int x, int y){
        return x + y;
    }
}
