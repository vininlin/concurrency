/**
 * 
 */
package lock;

import java.util.concurrent.atomic.AtomicReference;

import basic.SleepUtils;

/**
 * 类/接口注释
 * 
 * @author linwn@ucweb.com
 * @createDate 2015-9-16
 * 
 */
public class ClhSpinLock {
    
    private final ThreadLocal<Node> prev;
    private final ThreadLocal<Node> node;
    private final AtomicReference<Node> tail = new AtomicReference<Node>(new Node());
    
    public ClhSpinLock(){
        this.node = new ThreadLocal<Node>(){
            protected Node initialValue(){
                return new Node();
            }
        };
        this.prev = new ThreadLocal<Node>(){
            protected Node initialValue(){
                return null;
            }
        };
    }
    
    public void lock(){
        final Node node = this.node.get();
        node.locked = true;
        //一个CAS操作即可将当前线程对应的节点加到队列中，
        //并且同时获取前继节点的引用，然后等待前继节点释放锁
        Node prev = this.tail.getAndSet(node);
        this.prev.set(prev);
        System.out.println(Thread.currentThread().getName() + " waiting for lock");
        while(prev.locked){//进入自旋
        }
        System.out.println(Thread.currentThread().getName() + " get the lock");
    }
    
    public void unlock(){
        final Node node = this.node.get();
        node.locked = false;
        this.node.set(this.prev.get());
        System.out.println(Thread.currentThread().getName() + " releases the lock.");
    }
    
    private static class Node{
        private volatile boolean locked;
    }
    
    public static void main(String[] args) {
        final ClhSpinLock lock = new ClhSpinLock();
        lock.lock();
        for(int i = 0; i < 10; i++){
            new Thread(new Runnable(){
                @Override
                public void run() {
                    lock.lock();
                    SleepUtils.mills(10);
                    lock.unlock();
                }
            },"Thread"+i).start();
           
        }
        lock.unlock();
    }
}
