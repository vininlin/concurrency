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
 * @createDate 2015-9-16
 * 
 */
public class Mutex implements Lock {

    private static class Sync extends AbstractQueuedSynchronizer{

        @Override
        protected boolean tryAcquire(int arg) {
            if(compareAndSetState(0,1)){
                this.setExclusiveOwnerThread(Thread.currentThread());
                return true;
            }
            return false;
        }

        @Override
        protected boolean tryRelease(int releases) {
            if(getState() == 0) throw new IllegalMonitorStateException();
            this.setExclusiveOwnerThread(null);
            this.setState(0);
            return true;
        }

        @Override
        protected boolean isHeldExclusively() {
            return getState() == 1;
        }
        
        Condition newCondition(){
            return new ConditionObject();
        }
    }
    
    private final Sync sync = new Sync();
    
    @Override
    public void lock() {
        sync.acquire(1);
    }
   
    @Override
    public void lockInterruptibly() throws InterruptedException {
        sync.acquireInterruptibly(1);
    }
   
    @Override
    public boolean tryLock() {
        return sync.tryAcquire(1);
    }

    @Override
    public boolean tryLock(long time, TimeUnit unit) throws InterruptedException {
        return sync.tryAcquireNanos(1, unit.toNanos(time));
    }

    @Override
    public void unlock() {
        sync.release(0);
    }
    
    public boolean isLocked(){
        return sync.isHeldExclusively();
    }

    @Override
    public Condition newCondition() {
        return sync.newCondition();
    }

    public static void main(String[] args) {
        final Mutex lock = new Mutex();
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
    }
}
