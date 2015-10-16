/**
 * 
 */
package executor;

import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * 类/接口注释
 * 
 * @author linwn@ucweb.com
 * @createDate 2015-10-15
 * 
 */
public class FutureTaskTest {
    
    public static void main(String[] args) throws InterruptedException{
        FutureTask<Integer> futureTask = new FutureTask<Integer>(new Task());
        ExecutorService executor = Executors.newCachedThreadPool();
        executor.submit(futureTask);
        executor.shutdown();
        System.out.println("main  线程在执行任务中");
        TimeUnit.SECONDS.sleep(1);
         try {
            System.out.println("获取异步结果");
            Integer result = futureTask.get();
            System.out.println("异步结果为：" + result);
        } catch (ExecutionException e) {
            e.printStackTrace();
        }
    }
   
    private static class Task implements Callable<Integer>{

        @Override
        public Integer call() throws Exception {
            System.out.println("进行异步结果计算中..");
            TimeUnit.SECONDS.sleep(2);
            int sum = 0;
            for(int i = 0 ; i < 100; i++)
                sum += i;
            return sum;
        }
        
    }
}
