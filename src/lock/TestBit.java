/**
 * 
 */
package lock;

/**
 * Àà/½Ó¿Ú×¢ÊÍ
 * 
 * @author linwn@ucweb.com
 * @createDate 2015-9-21
 * 
 */
public class TestBit {
    static final int SHARED_SHIFT   = 16;
    static final int SHARED_UNIT    = (1 << SHARED_SHIFT);
    static final int MAX_COUNT      = (1 << SHARED_SHIFT) - 1;
    static final int EXCLUSIVE_MASK = (1 << SHARED_SHIFT) - 1;
    /**
     * @param args
     */
    public static void main(String[] args) {
       System.out.println((1<<16)-1);
       System.out.println(3<<16);
       System.out.println(Integer.toBinaryString(EXCLUSIVE_MASK));
       System.out.println(exclusiveCount(1));
       System.out.println(sharedCount(32));
    }
    
    
    static int exclusiveCount(int c) { return c & EXCLUSIVE_MASK; }

    static int sharedCount(int c)    { return c >>> SHARED_SHIFT; }
}
