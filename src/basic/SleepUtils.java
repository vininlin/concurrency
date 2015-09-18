/**
 * 
 */
package basic;

import java.util.concurrent.TimeUnit;

/**
 * Àà/½Ó¿Ú×¢ÊÍ
 * 
 * @author linwn@ucweb.com
 * @createDate 2015-9-11
 * 
 */
public class SleepUtils {

    public static final void second(long seconds){
        try {
            TimeUnit.SECONDS.sleep(seconds);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
    
    public static final void mills(long mills){
        try {
            TimeUnit.MILLISECONDS.sleep(mills);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
