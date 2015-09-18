/**
 * 
 */
package basic;

import java.util.concurrent.TimeUnit;

/**
 * 类/接口注释
 * 
 * @author linwn@ucweb.com
 * @createDate 2015-9-14
 * 
 */
public class Shundown {

    public static void main(String[] args) {
        Runner one = new Runner();
        Thread countThread = new Thread(one,"CountThread");
        countThread.start();
        //休眠1秒，main线程对CountThread中断，使CountThread线程能感知中断而结束
        SleepUtils.second(1);
        countThread.interrupt();
        Runner two = new Runner();
        countThread = new Thread(two,"CountThread");
        countThread.start();
        //休眠1秒，main线程对CountThread中断，使CountThread线程能感知中断而结束
        SleepUtils.second(1);
        two.cancel();
    }
    
    private static class Runner implements Runnable{
        
        private long i;
        private volatile boolean on = true;
        
        @Override
        public void run() {
            while(on && !Thread.currentThread().isInterrupted()){
                i++;
            }
            System.out.println("Count i=" + i);
        }
        
        public void cancel(){
            on = false;
        }
        
    }

}
