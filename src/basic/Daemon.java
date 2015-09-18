/**
 * 
 */
package basic;

/**
 * Àà/½Ó¿Ú×¢ÊÍ
 * 
 * @author linwn@ucweb.com
 * @createDate 2015-9-11
 * 
 */
public class Daemon {

    public static void main(String[] args){
        Thread daemonThread = new Thread(new DaemonRunner(),"DaemonThread");
        daemonThread.setDaemon(true);
        daemonThread.start();
    }
    
    static class DaemonRunner implements Runnable{
        @Override
        public void run() {
           try{
               SleepUtils.second(10);
           }finally{
               System.out.println("DaemonThread finally run.");
           }
        }
    }
}
