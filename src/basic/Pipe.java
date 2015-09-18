/**
 * 
 */
package basic;

import java.io.IOException;
import java.io.PipedReader;
import java.io.PipedWriter;

/**
 * Àà/½Ó¿Ú×¢ÊÍ
 * 
 * @author linwn@ucweb.com
 * @createDate 2015-9-15
 * 
 */
public class Pipe {

    public static void main(String[] args) throws IOException {
        PipedWriter out = new PipedWriter();
        PipedReader in = new PipedReader();
        try {
            out.connect(in);
            Thread printThread = new Thread(new Printer(in),"PrintThread");
            printThread.start();
            int receive = 0;
            while((receive = System.in.read()) != -1){
                out.write(receive);
            }
        }finally{
            out.close();
        }
        

    }
    
    static class Printer implements Runnable{
        private PipedReader in;
        public Printer(PipedReader in){
            this.in = in;
        }
        @Override
        public void run(){
            int receive = 0;
            try {
                while((receive = in.read()) != -1){
                    System.out.print((char)receive);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
