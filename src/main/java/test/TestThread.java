package test;

import java.net.URL;
import javax.net.ssl.TrustManager;


public class TestThread extends Thread
{
  private long avg;
  private int failed;
  private long elapsed;
  private final int loops;
  private final String url;
  private final String payload;
  private static final TrustManager[] tmgrs = new TrustManager[] {new FakeTrustManager()};
  
  
  public static void start(String url, int threads, int loops, String payload) throws Exception
  {
    TestThread tests[] = new TestThread[threads];
    for (int i = 0; i < tests.length; i++) tests[i] = new TestThread(loops,url,payload);
    
    System.out.println();
    System.out.println("Testing static files interface threads: "+threads+" loops: "+loops+" "+url+" no delay");
    System.out.println();

    long avg = 0;
    int failed = 0;
    long time = System.currentTimeMillis();

    for (int i = 0; i < tests.length; i++) tests[i].start();
    for (int i = 0; i < tests.length; i++) tests[i].join();
    
    for (int i = 0; i < tests.length; i++) {avg += tests[i].avg; failed += tests[i].failed;}
    
    time = System.currentTimeMillis() - time;
    System.out.println(loops*threads+" pages served in "+time/1000+" secs, failed "+failed+", "+(loops*threads*1000)/time+" pages/sec, response time "+avg/(loops*threads*1000000.0)+" ms");
  }
  
  
  private TestThread(int loops, String url, String payload)
  {
    this.url = url;
    this.loops = loops;
    this.payload = payload;
  }
  
  
  public void run()
  {
    long time = System.currentTimeMillis();      

    try
    {
      URL url = new URL(this.url);

      String path = url.getPath();
      boolean ssl = this.url.startsWith("https");
      Session session = new Session(url.getHost(),url.getPort(),ssl);

      for (int i = 0; i < loops; i++)
      {
        long req = System.nanoTime();

        try
        {
          session.invoke(path,payload);
        }
        catch (Exception e)
        {
          failed++;
        }
        
        avg += System.nanoTime()-req;
      }
    }
    catch (Exception e)
    {
      e.printStackTrace();
    }
    
    this.elapsed = System.currentTimeMillis() - time;
  }  
}