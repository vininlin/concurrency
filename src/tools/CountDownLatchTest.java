/**
 * 
 */
package tools;

import java.util.concurrent.CountDownLatch;

/**
 * Àà/½Ó¿Ú×¢ÊÍ
 * 
 * @author linwn@ucweb.com
 * @createDate 2015-10-12
 * 
 */
public class CountDownLatchTest {
    
    public static void main(String[] args) {
        CountDownLatch latch = new CountDownLatch(4);
        int n = 4;
        for(int i = 0 ; i < 4; i++){
            Thread t = new Thread(new Worker(latch,"Thread-" + String.valueOf(i)));
            t.start();
        }
        System.out.println("waiting task complete...");
        try {
            latch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        System.out.println("all task finish.");
    }
    
    static class Worker implements Runnable{
        
        private CountDownLatch latch;
        private String threadName; 
        
        public Worker(CountDownLatch latch,String threadName){
            this.latch = latch;
            this.threadName = threadName;
        }

        @Override
        public void run() {
            try {
                System.out.println("Thread " + threadName + " start working" );
                Thread.sleep(2000);
                System.out.println("Thread " + threadName + " finish working" );
                latch.countDown();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

}
