/**
 * 
 */
package tools;

import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;


/**
 * 类/接口注释
 * 
 * @author linwn@ucweb.com
 * @createDate 2015-10-10
 * 
 */
public class CyclicBarrierTest {

    /**
     * @param args
     */
    public static void main(String[] args) {
        int n = 4;
        CyclicBarrier barrier = new CyclicBarrier(n,new Runnable(){
            @Override
            public void run(){
                System.out.println("thread " + Thread.currentThread().getName() + " do something else...");
            }
        });
        for(int i = 0; i < 4; i++){
            new Thread(new Writer(barrier,(1000*(i+1))),"Thread-" + String.valueOf(i)).start();
        }
    }
    
    static class Writer implements Runnable{

        private CyclicBarrier barrier ; 
        
        private final long sleepTime ;
        
        public Writer(CyclicBarrier barrier,long sleepTime){
            this.barrier = barrier;
            this.sleepTime = sleepTime;
        }
        
        @Override
        public void run() {
            System.out.println("thread " + Thread.currentThread().getName() + " is writing...");
            try {
                Thread.sleep(sleepTime);
                System.out.println("thread " + Thread.currentThread().getName() + "  done");
                //等待其它线程写入
                int index = barrier.await();
                System.out.println("thread " + Thread.currentThread().getName() + "  arrival index is " + index);
            } catch (InterruptedException e) {
                e.printStackTrace();
            } catch (BrokenBarrierException e) {
                e.printStackTrace();
            }
            System.out.println("thread " + Thread.currentThread().getName() + " All thread done");
        }
    }

}
