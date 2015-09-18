/**
 * 
 */
package lock;

import java.util.concurrent.TimeUnit;

import java.util.concurrent.locks.AbstractQueuedSynchronizer;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;

import basic.SleepUtils;

/**
 * 类/接口注释
 * 
 * @author linwn@ucweb.com
 * @createDate 2015-9-17
 * 
 */
public class TwinsLock implements Lock{
   
    private final Sync sync = new Sync(2);
    
    @Override
    public void lock() {
        sync.acquireShared(1);
    }
    
    @Override
    public void unlock() {
        sync.releaseShared(1);
    }
    
    @Override
    public void lockInterruptibly() throws InterruptedException {
        
    }
    
    @Override
    public boolean tryLock() {
        return false;
    }
    
    @Override
    public boolean tryLock(long time, TimeUnit unit) throws InterruptedException {
        return false;
    }
    
    @Override
    public Condition newCondition() {
        return null;
    }
    
    static class Sync extends AbstractQueuedSynchronizer{
        
        Sync(int count){
            if(count < 0){
                throw new IllegalArgumentException("count must be larger than 0");
            }
            setState(count);
        }
        
        @Override
        protected int tryAcquireShared(int reduce) {
            for(;;){
                int currentCount = getState();
                int newCount = currentCount - reduce;
                if(newCount < 0 || compareAndSetState(currentCount, newCount)){
                    return newCount;
                }
            }
        }

        @Override
        protected boolean tryReleaseShared(int add) {
            for(;;){
                int currentCount = getState();
                int newCount = currentCount + add;
                if(compareAndSetState(currentCount, newCount)){
                    return true;
                }
            }
        }
    }
 
    public static void main(String[] args) {
        final TwinsLock lock = new TwinsLock();
        //启动10个线程
        for(int i = 0 ; i < 10; i++){
            Worker worker = new Worker(lock);
            //worker.setDaemon(true);
            worker.start();
        }
        //每秒换一行
        for(int i = 0 ; i < 10; i++){
            SleepUtils.second(1);
            System.out.println();
        }
    }
    
    static class Worker extends Thread{
        final TwinsLock lock ;
        public Worker(TwinsLock lock){
            this.lock = lock;
        }
        @Override
        public void run() {
           while(true){
               lock.lock();
               try{
                   SleepUtils.second(1);
                   System.out.println(Thread.currentThread().getName());
                   SleepUtils.second(1);
               }finally{
                   lock.unlock();
               }
           }
        }
    }
}
