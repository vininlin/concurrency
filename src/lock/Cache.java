/**
 * 
 */
package lock;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Àà/½Ó¿Ú×¢ÊÍ
 * 
 * @author linwn@ucweb.com
 * @createDate 2015-9-23
 * 
 */
public class Cache {

    static Map<String,Object> map = new HashMap<String,Object>();
    static ReentrantReadWriteLock rwl = new ReentrantReadWriteLock();
    static Lock rlock = rwl.readLock();
    static Lock wlock = rwl.writeLock();
    
    public static final Object get(String key){
        rlock.lock();
        try{
            return map.get(key);
        }finally{
            rlock.unlock();
        }
    }
    
    public static final void put(String key,Object value){
        wlock.lock();
        try{
            map.put(key, value);
        }finally{
            wlock.unlock();
        }
    }
    
    public static final void clear(){
        wlock.lock();
        try{
            map.clear();
        }finally{
            wlock.unlock();
        }
    }
    
}
