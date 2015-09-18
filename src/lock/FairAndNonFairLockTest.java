/**
 * 
 */
package lock;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.commons.lang.StringUtils;

/**
 * 类/接口注释
 * 
 * @author linwn@ucweb.com
 * @createDate 2015-9-18
 * 
 */
public class FairAndNonFairLockTest {
    
    private static Lock fairLock = new ReentrantLock2(true);
    private static Lock nonFairLock = new ReentrantLock2(false);
    
    public static void testFairLock(){
        for(int i = 0 ; i < 5; i++){
            Job job = new Job(fairLock);
            job.start();
        }
    }
    
    public static  void testNonFairLock(){
        for(int i = 0 ; i < 5; i++){
            Job job = new Job(nonFairLock);
            job.start();
        }
    }
    
    private static class Job extends Thread{
        private Lock lock ;
        
        public Job(Lock lock){
            this.lock = lock;
        }
        
        @Override
        public void run(){
            ReentrantLock2 reLock = (ReentrantLock2)lock;
            reLock.lock();
            System.out.println("Locked by [" + reLock.getOwner() + "];" +
            		"waiting by [" + StringUtils.join(reLock.getQueuedThread(),",") + "]");
            reLock.unlock();
            
        }
    }

    public static void main(String[] args) {
        testFairLock();
    }
    
    private static class ReentrantLock2 extends ReentrantLock{
        
        public ReentrantLock2(boolean fair){
            super(fair);
        }
        //获取等待队列中的线程
        public Collection<Thread> getQueuedThread(){
            List<Thread> threadList = new ArrayList<Thread>(super.getQueuedThreads());
            Collections.reverse(threadList);
            return threadList;
        }
        
        public Thread getOwner(){
            return super.getOwner();
        }
        
    }

}
