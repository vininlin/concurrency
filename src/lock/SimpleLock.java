/**
 * 
 */
package lock;

import java.util.concurrent.locks.AbstractQueuedSynchronizer;

import basic.SleepUtils;

/**
 * 类/接口注释
 * 
 * @author linwn@ucweb.com
 * @createDate 2015-9-15
 * 
 */
public class SimpleLock extends AbstractQueuedSynchronizer {
    
   
    @Override
    protected boolean tryAcquire(int unused) {
        if(compareAndSetState(0,1)){
            this.setExclusiveOwnerThread(Thread.currentThread());
            return true;
        }
        return false;
    }

   
    @Override
    protected boolean tryRelease(int unused) {
        this.setExclusiveOwnerThread(null);
        this.setState(0);
        return true;
    }
    
    public void lock(){
        this.acquire(1);
    }
    
    public boolean tryLock(){
        return this.tryAcquire(1);
    }
    
    public void unlock(){
        this.release(1);
    }
    
    public boolean isLock(){
        return this.isHeldExclusively();
    }

    public static void main(String[] args) {
            final SimpleLock lock = new SimpleLock();
            //lock.lock();
            for(int i = 0; i < 10; i++){
                new Thread(new Runnable(){
                    @Override
                    public void run() {
                        lock.lock();
                        System.out.println(Thread.currentThread().getId() + " acquired the lock.");
                        lock.unlock();
                    }
                }).start();
                //简单地让线程让for循环的顺序阻塞在lock上
                SleepUtils.second(10);
            }
            System.out.println("main thread unlock");
            //lock.unlock();
    }

}
