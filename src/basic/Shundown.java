/**
 * 
 */
package basic;

import java.util.concurrent.TimeUnit;

/**
 * ��/�ӿ�ע��
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
        //����1�룬main�̶߳�CountThread�жϣ�ʹCountThread�߳��ܸ�֪�ж϶�����
        SleepUtils.second(1);
        countThread.interrupt();
        Runner two = new Runner();
        countThread = new Thread(two,"CountThread");
        countThread.start();
        //����1�룬main�̶߳�CountThread�жϣ�ʹCountThread�߳��ܸ�֪�ж϶�����
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
