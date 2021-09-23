import java.util.logging.Logger;

class App {
    private static final Logger LOG = Logger.getLogger(App.class.getName());

    public static void main(String[] args) {
        
        LOG.info("Running with JAVA_OPTS: " + System.getenv("JAVA_OPTS"));
        LOG.info("Memory Usage: " + java.lang.management.ManagementFactory.getMemoryMXBean().getHeapMemoryUsage());

        System.out.println("Hello App 1");
    }
}
