/**
 * 
 */
package lock;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import basic.SleepUtils;

/**
 * Àà/½Ó¿Ú×¢ÊÍ
 * 
 * @author linwn@ucweb.com
 * @createDate 2015-9-24
 * 
 */
public class BoundedQueue<T> {

    private Object[] items;
    private int addIndex,removeIndex,count;
    private Lock lock = new ReentrantLock();
    private Condition notEmptyCondition = lock.newCondition();
    private Condition notFullCondition = lock.newCondition();
    
    public BoundedQueue(int size){
        items = new Object[size];
    }
    
    public void add(T t) throws InterruptedException{
        lock.lock();
        try{
            while(count == items.length){
                notFullCondition.await();
            }
            items[addIndex] = t;
            if(++addIndex == items.length)
                addIndex = 0;
            ++count;
            notEmptyCondition.signal();
        }finally{
            lock.unlock();
        }
    }
    
    public T remove(T t) throws InterruptedException{
        lock.lock();
        try{
            while(count == 0){
                notEmptyCondition.await();
            }
            Object x = items[removeIndex];
            if(++removeIndex == items.length)
                removeIndex = 0;
            --count;
            notFullCondition.signal();
            return (T)x;
        }finally{
            lock.unlock();
        }
    }
    
    public static void main(String[] args){
        final BoundedQueue<String> queue = new BoundedQueue<String>(5);
        for(int i = 0; i < 10;i++){
            Thread t = new Thread(new Runnable(){
                @Override
                public void run(){
                    try {
                        SleepUtils.mills(500);
                        queue.add("aaaaa");
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            },"addthread-"+String.valueOf(i));
            t.start();
        }
        for(int i = 0; i < 10;i++){
            Thread t = new Thread(new Runnable(){
                @Override
                public void run(){
                    try {
                        SleepUtils.mills(100);
                        queue.remove("aaaaa");
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            },"removeThread-"+String.valueOf(i));
            t.start();
        }
    }
}
