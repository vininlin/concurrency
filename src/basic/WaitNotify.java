/**
 * 
 */
package basic;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * ��/�ӿ�ע��
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
                //������������ɹ���
                System.out.println(Thread.currentThread() + " flag is false.running @" + 
                        new SimpleDateFormat("HH:mm:ss").format(new Date()));
            }
        }
    }
    
    static class Notify implements Runnable{
        @Override
        public void run() {
            synchronized(lock){
                //��ȡ����Ȼ�����֪ͨ��֪ͨʱ�����ͷ���
                //ֱ����ǰ�߳��ͷ�����WaitThread���ܴ�wait()����
                System.out.println(Thread.currentThread() + " hold lock.notify @" + 
                        new SimpleDateFormat("HH:mm:ss").format(new Date()));
                lock.notifyAll();
                flag = false;
                SleepUtils.second(5);
            }
            //�ٴμ���
            synchronized(lock){
                System.out.println(Thread.currentThread() + " hold lock again.sleep @" + 
                        new SimpleDateFormat("HH:mm:ss").format(new Date()));
                SleepUtils.second(5);
            }
        }
    }

}
