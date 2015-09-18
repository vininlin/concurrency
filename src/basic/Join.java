/**
 * 
 */
package basic;

/**
 * 类/接口注释
 * 
 * @author linwn@ucweb.com
 * @createDate 2015-9-15
 * 
 */
public class Join {

    public static void main(String[] args) {
        Thread previous = Thread.currentThread();
        for(int i = 0; i < 10; i++){
            //每个线程拥有前一个线程的引用，需要等待前一个线程结束才能从等待中返回
            Thread thread = new Thread(new Domino(previous),String.valueOf(i));
             thread.start();
             previous = thread;
        }
        SleepUtils.second(2);
        System.out.println(Thread.currentThread().getName() + " terminated" );
    }

    static class Domino implements Runnable{
        private Thread thread;
        public Domino(Thread thread){
            this.thread = thread;
        }
        
       @Override
       public void run(){
           try {
               thread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
           System.out.println(Thread.currentThread().getName() + " terminated" );
       }
    }
}
