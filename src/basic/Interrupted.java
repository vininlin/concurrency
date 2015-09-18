/**
 * 
 */
package basic;

/**
 * ��/�ӿ�ע��
 * 
 * @author linwn@ucweb.com
 * @createDate 2015-9-11
 * 
 */
public class Interrupted {

    public static void main(String[] args){
        //SleepRunner�̲߳�ͣ�س���˯��
        Thread sleepThread = new Thread(new SleepRunner(),"SleepThread");
        sleepThread.setDaemon(true);
        Thread busyThread = new Thread(new BusyRunner(),"BusyThread");
        busyThread.setDaemon(true);
        sleepThread.start();
        busyThread.start();
        //����5�룬��sleepThread��busyThread�������
        SleepUtils.second(5);
        sleepThread.interrupt();
        busyThread.interrupt();
        System.out.println("SleepThread interrupted is " +  sleepThread.isInterrupted());
        System.out.println("BusyThread interrupted is " +  busyThread.isInterrupted());
        //��ֹsleepThread��busyThread�����˳�
        SleepUtils.second(2);
    }
    
    static class SleepRunner implements Runnable{
        @Override
        public void run() {
            while(true){
                SleepUtils.second(10);
            }
        }
    }
    
    static class BusyRunner implements Runnable{
        @Override
        public void run() {
            while(true){
            }
        }
    }
}
