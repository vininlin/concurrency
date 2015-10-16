/**
 * 
 */
package tools;


import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import tools.source.Exchanger;

/**
 * Àà/½Ó¿Ú×¢ÊÍ
 * 
 * @author linwn@ucweb.com
 * @createDate 2015-10-12
 * 
 */
public class ExchangerTest {
    
    private static volatile boolean isDone = false;

    public static void main(String[] args) {
        ExecutorService executor = Executors.newCachedThreadPool();
        Exchanger<Integer> exchanger = new Exchanger<Integer>();
        Producer producer = new Producer(exchanger);
        Consumer consumer = new Consumer(exchanger);
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

        private Exchanger<Integer> exchanger;
        private static int data = 1;
        
        public Producer(Exchanger<Integer> exchanger){
            this.exchanger = exchanger;
        }
        
        @Override
        public void run() {
            while(!Thread.interrupted() && !isDone){
                for(int i = 1; i <= 3; i++){
                    try {
                        TimeUnit.SECONDS.sleep(1);
                        data = i;
                        System.out.println(Thread.currentThread().getId() + " produce before , data = " + data);
                        data = exchanger.exchange(data);
                        System.out.println(Thread.currentThread().getId() + " produce after , data = " + data);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                isDone = true;
            }
        }
    }
    
    static class Consumer implements Runnable{

        private Exchanger<Integer> exchanger;
        private static int data = 0;
        
        public Consumer(Exchanger<Integer> exchanger){
            this.exchanger = exchanger;
        }
        
        @Override
        public void run() {
            while(!Thread.interrupted() && !isDone){
                data = 0;
                System.out.println(Thread.currentThread().getId() + " consume before , data = " + data);
                try {
                    TimeUnit.SECONDS.sleep(1);
                    data = exchanger.exchange(data);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                System.out.println(Thread.currentThread().getId() + " consume after , data = " + data);
            }
        }
    }

}
