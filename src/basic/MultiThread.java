/**
 * 
 */
package basic;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;


/**
 * 类/接口注释
 * 
 * @author linwn@ucweb.com
 * @createDate 2015-9-10
 * 
 */
public class MultiThread {

    public static void main(String[] args){
        //获取Java线程管理MXBean
        ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
        //不需要获取同步的monitor和synchronizer信息，只获取线程和堆栈信息。
        ThreadInfo[] threadInfos = threadMXBean.dumpAllThreads(false, false);
        for(ThreadInfo threadInfo : threadInfos){
            System.out.println("[" + threadInfo.getThreadId() + "]" + threadInfo.getThreadName());
        }
        
    }
}
