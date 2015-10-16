/**
 * 
 */
package collections;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import collections.sources.SynchronousQueue;

import tools.source.Exchanger;

/**
 * Àà/½Ó¿Ú×¢ÊÍ
 * 
 * @author linwn@ucweb.com
 * @createDate 2015-10-15
 * 
 */
public class SynchronousQueueTest {
    private volatile static boolean isDone = false;
    
    public static void main(String[] args) {
        ExecutorService executor = Executors.newCachedThreadPool();
        SynchronousQueue<Integer> queue = new SynchronousQueue<Integer>(true);
        Producer producer = new Producer(queue);
        Consumer consumer = new Consumer(queue);
        executor.execute(producer);
        executor.execute(consumer);
        executor.shutdown();
        try {
            executor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    static class Producer implements Runnable{

        private SynchronousQueue<Integer> queue;
        
        public Producer(SynchronousQueue<Integer> queue){
            this.queue = queue;
        }
        
        @Override
        public void run() {
            while(!Thread.interrupted() && !isDone){
                for(int i = 1; i <= 3; i++){
                    try {
                        System.out.println(Thread.currentThread().getId() + " producer put to queue,data=  " + i );
                        queue.put(i);
                        
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                isDone = true;
            }
        }
    }
    
    static class Consumer implements Runnable{

        private SynchronousQueue<Integer> queue;
        
        public Consumer(SynchronousQueue<Integer> queue){
            this.queue = queue;
        }
        
        @Override
        public void run() {
            while(!Thread.interrupted() && !isDone){
                try {
                   // TimeUnit.SECONDS.sleep(1);
                    System.out.println(Thread.currentThread().getId() + " consumer waiting for queue:");
                    Integer data = queue.take();
                    System.out.println(Thread.currentThread().getId() + " consumer take from queue,data="+data);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
