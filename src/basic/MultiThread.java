/**
 * 
 */
package basic;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;


/**
 * ��/�ӿ�ע��
 * 
 * @author linwn@ucweb.com
 * @createDate 2015-9-10
 * 
 */
public class MultiThread {

    public static void main(String[] args){
        //��ȡJava�̹߳���MXBean
        ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
        //����Ҫ��ȡͬ����monitor��synchronizer��Ϣ��ֻ��ȡ�̺߳Ͷ�ջ��Ϣ��
        ThreadInfo[] threadInfos = threadMXBean.dumpAllThreads(false, false);
        for(ThreadInfo threadInfo : threadInfos){
            System.out.println("[" + threadInfo.getThreadId() + "]" + threadInfo.getThreadName());
        }
        
    }
}
