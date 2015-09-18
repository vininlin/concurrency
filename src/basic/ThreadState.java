/**
 * 
 */
package basic;

/**
 * 类/接口注释
 * 
 * @author linwn@ucweb.com
 * @createDate 2015-9-11
 * 
 */
public class ThreadState {

    public static void main(String[] args){
        new Thread(new TimedWaiting(),"TimedWaitingThread").start();
        new Thread(new Waiting(),"WaitingThread").start();
        //创建两个Blocked线程，一个获取实例成功，另一个阻塞
        new Thread(new Blocked(),"BlockedThread-1").start();
        new Thread(new Blocked(),"BlockedThread-2").start();
    }
    
    //该线程不断地进行休眠
    static class TimedWaiting implements Runnable{
        @Override
        public void run() {
            while(true){
                SleepUtils.second(100);
            }
        }
    }
    
    //该线程在Waiting.class实例上等待
    static class Waiting implements Runnable{
        @Override
        public void run() {
            while(true){
                synchronized(Waiting.class){
                    try {
                        Waiting.class.wait();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }
    
    //该线程在Blocked.class实例加锁后，不会释放该锁
    static class Blocked implements Runnable{
        @Override
        public void run() {
            while(true){
                synchronized(Blocked.class){
                    SleepUtils.second(100);
                }
            }
        }
    }
}
