/**
 * 
 */
package basic;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * 类/接口注释
 * 
 * @author linwn@ucweb.com
 * @createDate 2015-9-14
 * 
 */
public class WaitNotify {

    static boolean flag = true;
    static Object lock = new Object();
    
    public static void main(String[] args) {
       Thread waitThread = new Thread(new Wait(),"WaitThread");
       waitThread.start();
       SleepUtils.second(1);
       Thread notifyThread = new Thread(new Notify(),"NotifyThread");
       notifyThread.start();
    }
    
    static class Wait implements Runnable{
        @Override
        public void run() {
            synchronized(lock){
                while(flag){
                    try {
                        System.out.println(Thread.currentThread() + " flag is true.wait @" + 
                                    new SimpleDateFormat("HH:mm:ss").format(new Date()));
                        lock.wait();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                //满足条件，完成工作
                System.out.println(Thread.currentThread() + " flag is false.running @" + 
                        new SimpleDateFormat("HH:mm:ss").format(new Date()));
            }
        }
    }
    
    static class Notify implements Runnable{
        @Override
        public void run() {
            synchronized(lock){
                //获取锁，然后进行通知，通知时不会释放锁
                //直到当前线程释放锁后，WaitThread才能从wait()返回
                System.out.println(Thread.currentThread() + " hold lock.notify @" + 
                        new SimpleDateFormat("HH:mm:ss").format(new Date()));
                lock.notifyAll();
                flag = false;
                SleepUtils.second(5);
            }
            //再次加锁
            synchronized(lock){
                System.out.println(Thread.currentThread() + " hold lock again.sleep @" + 
                        new SimpleDateFormat("HH:mm:ss").format(new Date()));
                SleepUtils.second(5);
            }
        }
    }

}
