/**
 * 
 */
package tools;

import java.util.concurrent.Semaphore;

/**
 * Àà/½Ó¿Ú×¢ÊÍ
 * 
 * @author linwn@ucweb.com
 * @createDate 2015-10-12
 * 
 */
public class SemaphoreTest {

    public static void main(String[] args){
        int n = 10;
        Semaphore semaphore = new Semaphore(5,true);
        for(int i = 0 ; i < n; i++){
            Thread t = new Thread(new Worker(semaphore,"thread-" + String.valueOf(i)));
            t.start();
        }
    }
    
    static class Worker implements Runnable{
        
        private Semaphore semaphore;
        private String threadName; 
        
        public Worker(Semaphore semaphore,String threadName){
            this.semaphore = semaphore;
            this.threadName = threadName;
        }

        @Override
        public void run() {
            try {
                System.out.println("Thread " + threadName + " acquire a permit;" );
                semaphore.acquire();
                System.out.println("Thread " + threadName + " get a permit;" );
                Thread.sleep(2000);
                System.out.println("Thread " + threadName + " release a permit;" );
                semaphore.release();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
    
}
