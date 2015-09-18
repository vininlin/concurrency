/**
 * 
 */
package lock;

import java.util.concurrent.atomic.AtomicReference;

import basic.SleepUtils;

/**
 * ��/�ӿ�ע��
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
        //һ��CAS�������ɽ���ǰ�̶߳�Ӧ�Ľڵ�ӵ������У�
        //����ͬʱ��ȡǰ�̽ڵ�����ã�Ȼ��ȴ�ǰ�̽ڵ��ͷ���
        Node prev = this.tail.getAndSet(node);
        this.prev.set(prev);
        System.out.println(Thread.currentThread().getName() + " waiting for lock");
        while(prev.locked){//��������
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
