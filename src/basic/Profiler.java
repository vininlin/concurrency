/**
 * 
 */
package basic;

/**
 * Àà/½Ó¿Ú×¢ÊÍ
 * 
 * @author linwn@ucweb.com
 * @createDate 2015-9-15
 * 
 */
public class Profiler {

    private static final ThreadLocal<Long> TIME_THREADLOCAL = new ThreadLocal<Long>(){
        protected Long initialValue(){
            return System.currentTimeMillis();
        }
    };
    
    public static final void begin(){
        TIME_THREADLOCAL.set(System.currentTimeMillis());
    }
    
    public static final Long end(){
        return System.currentTimeMillis() -TIME_THREADLOCAL.get();
    }
    
    public static void main(String[] args) {
        begin();
        SleepUtils.second(4);
        System.out.println("Cost " + end() + "mills.");
    }

}
