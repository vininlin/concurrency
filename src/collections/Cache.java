/**
 * 
 */
package collections;

import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.TimeUnit;

import collections.sources.ConcurrentHashMap;

/**
 * 类/接口注释
 * 
 * @author linwn@ucweb.com
 * @createDate 2015-10-8
 * 
 */
public class Cache<K,V> {

    private final ConcurrentMap<K,V> cache = new ConcurrentHashMap<K,V>();
    
    private final DelayQueue<DelayItem<Pair<K,V>>> q = new DelayQueue<DelayItem<Pair<K,V>>>();
    
    private Thread daemonThread;
    
    public Cache(){
        Runnable checkTask = new Runnable(){
            @Override
            public void run(){
                checkTimeout();
            }
        };
        daemonThread = new Thread(checkTask);
        daemonThread.setDaemon(true);
        daemonThread.start();
    }
    
    private void checkTimeout(){
        for(;;){
            try {
                DelayItem<Pair<K,V>> item = q.take();
                if(item != null){
                    Pair<K,V> pair = item.getItem();
                    //删除超时的对象
                    cache.remove(pair.first,pair.second);
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
                break;
            }
        }
    }
    
    public void put(K key,V value,long time,TimeUnit unit){
        V oldValue = cache.put(key, value);
        if(oldValue != null)
            cache.remove(oldValue);
        long nanoTime = TimeUnit.NANOSECONDS.convert(time,unit);
        q.put(new DelayItem<Pair<K,V>>(new Pair<K,V>(key,value),nanoTime));
    }
    
    public V get(K key){
        return cache.get(key);
    }
   
    public static void main(String[] args) throws InterruptedException {
       Cache<Integer,String> cache = new Cache<Integer,String>();
       cache.put(1, "aaaa", 3, TimeUnit.SECONDS);
       Thread.sleep(2000);
       System.out.println(cache.get(1));
       Thread.sleep(2000);
       System.out.println(cache.get(1));
    }

}
