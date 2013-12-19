import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.salesforce.phoenix.jdbc.PhoenixDriver;
import com.splout.db.benchmark.SploutBenchmark;
import org.joda.time.DateTime;
import org.joda.time.Days;
import org.joda.time.Period;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * Created with IntelliJ IDEA.
 * User: ivan
 * Date: 19/12/13
 * Time: 17:00
 * To change this template use File | Settings | File Templates.
 */
public class PhoenixBenchmark {

  @Parameter(required = true, names = { "-nq", "--nqueries" }, description = "The number of queries to perform for the benchmark.")
  private Integer nQueries;

  @Parameter(required = true, names = { "-i", "--initDate" }, description = "Init date")
  private String initDate;

  @Parameter(required = true, names = { "-e", "--endDate" }, description = "End date")
  private String endDate;

  @Parameter(names = { "-n", "--niterations" }, description = "The number of iterations for running the benchmark more than once.")
  private Integer nIterations = 1;

  @Parameter(names = { "-nth", "--nthreads" }, description = "The number of threads to use for the test.")
  private Integer nThreads = 1;

  public static class Thread extends SploutBenchmark.StressThreadImpl {
    DateTime initDate;
    DateTime endDate;
    int days;

    String[] hosts = {"NA","CS","EU"};
    String[] domains = {"Salesforce.com","Apple.com","Google.com"};
    String[] features = {"Login","Report","Dashboard"};

    Random rand = new Random(System.currentTimeMillis());

    Connection conn;

    @Override
    public void init(Map<String, Object> stringObjectMap) throws Exception {
      initDate = new DateTime((String) stringObjectMap.get("initDate"));
      endDate = new DateTime((String) stringObjectMap.get("endDate"));
      days = Days.daysBetween(initDate.toDateMidnight(), endDate.toDateTime()).getDays();

      Class.forName("com.salesforce.phoenix.jdbc.PhoenixDriver");
      conn = DriverManager.getConnection("jdbc:phoenix:localhost");
    }

    @Override
    public int nextQuery() throws Exception {
      String host = hosts[rand.nextInt(hosts.length)];
      String domain = domains[rand.nextInt(domains.length)];
      String feature = features[rand.nextInt(features.length)];
      String date = initDate.plus(Period.days(rand.nextInt(days))).toString("yyyy-MM-dd");



      String query = "SELECT host, domain, feature, day, count(*) FROM performance WHERE host='%1' AND domain='%2' AND feature = '%3' and day = '%4' GROUP BY host, domain, feature, day";
      query = query.replace("%1", host).replace("%2", domain).replace("%3", feature).replace("%4", date);

      Statement stmt = conn.createStatement();
      ResultSet rs = stmt.executeQuery(query);

      try {

      if (rs.next()) {
        return (int) rs.getLong("VISITS");
      } else {
        return 0;
      }
      } catch(Exception e) {
        System.out.println(query);
        throw e;
      } finally {
        rs.close();
        stmt.close();
      }
    }
  }

  public void start() throws InterruptedException {
    Map<String, Object> context = new HashMap<String, Object>();
    context.put("initDate", initDate);
    context.put("endDate", endDate);

    SploutBenchmark benchmark = new SploutBenchmark();
    for(int i = 0; i < nIterations; i++) {
      benchmark.stressTest(nThreads, nQueries, Thread.class, context);
      benchmark.printStats(System.out);
    }
  }

  public static void main(String args[]) throws InterruptedException {
    PhoenixBenchmark bench = new PhoenixBenchmark();

    JCommander jComm = new JCommander(bench);
    jComm.setProgramName("Benchmark Tool");
    try {
      jComm.parse(args);
    } catch (ParameterException e){
      System.out.println(e.getMessage());
      System.out.println();
      jComm.usage();
      System.exit(-1);
    } catch(Throwable t) {
      t.printStackTrace();
      jComm.usage();
      System.exit(-1);
    }

    bench.start();
    System.exit(0);
  }
}
