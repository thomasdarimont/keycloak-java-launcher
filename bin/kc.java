import java.io.File;
import java.lang.management.ManagementFactory;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchKey;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;


class Launcher {

    private static final Logger LOG = Logger.getLogger(Launcher.class.getName());

    public static void main(String[] args) throws Exception {

        LOG.info("Running with JAVA_OPTS: " + System.getenv("JAVA_OPTS"));
        LOG.info("Memory Usage: " + ManagementFactory.getMemoryMXBean().getHeapMemoryUsage());

        var supervisor = new Supervisor(Executors.newSingleThreadExecutor());
        var appClass = args.length > 0 ? args[0] : "App.java";
        supervisor.setAppClass(appClass);
        // perhaps read App JVM options from a file app.conf or a custom ENV variable APP_JAVA_OPTS
        var appJavaOpts = Optional.ofNullable(System.getenv("APP_JAVA_OPTS")).orElse("-Xmx256m");
        supervisor.setAppEnv(Collections.singletonMap("JAVA_OPTS", appJavaOpts));
        supervisor.start();

        supervisor.startChangeTracking();
    }

    static class Supervisor {

        private final ExecutorService executorService;

        private String appClass;

        private Map<String, String> appEnv;

        private Process process;

        public Supervisor(ExecutorService executorService) {
            this.executorService = executorService;
        }

        public void signal() {
            process.destroy();
            start();
        }

        public Runnable watch() {

            return () -> {
                LOG.info("Starting App");
                this.process = runCommand(List.of(getJavaLauncherPath(), appClass), appEnv);
                try {
                    process.waitFor();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            };
        }

        public void start() {
            executorService.execute(watch());
        }

        public void setAppClass(String appClass) {
            this.appClass = appClass;
        }

        public void setAppEnv(Map<String, String> appEnv) {
            this.appEnv = appEnv;
        }

        public void startChangeTracking() throws Exception {

            Path path = Paths.get(".");
            LOG.info("Start change tracking in folder: " + path.toFile().getAbsolutePath());

            var watchService = path.getFileSystem().newWatchService();
            path.register(watchService, StandardWatchEventKinds.ENTRY_MODIFY);

            var debounceIntervalMillis = 0;
            var changeDetectedAt = new AtomicLong();
            WatchKey watchKey;
            while (!Thread.currentThread().isInterrupted()) {
                watchKey = watchService.poll(150, TimeUnit.MILLISECONDS);
                if (watchKey == null) {
                    continue;
                }

                for (var event : watchKey.pollEvents()) {
                    if (!event.context().toString().equals(appClass)) {
                        // only signal on to target file changes
                        continue;
                    }
                    var timeSinceLastChange = System.currentTimeMillis() - changeDetectedAt.get();
                    if (timeSinceLastChange > debounceIntervalMillis) {
                        LOG.info("Changes detected: " + event.context());
                        signal();
                        changeDetectedAt.set(System.currentTimeMillis());
                    }
                }

                watchKey.reset();
            }
        }
    }

    private static String getJavaLauncherPath() {
        return ProcessHandle.current().info().command().get();
    }

    private static Process runCommand(List<String> commandLine, Map<String, String> env) {
        var pb = new ProcessBuilder(commandLine);
        pb.environment().putAll(env);
        pb.directory(new File("."));
        pb.inheritIO();
        try {
            var process = pb.start();
            return process;
        } catch (Exception ex) {
            System.err.printf("Could not run command: %s.", commandLine);
            ex.printStackTrace();
            return null;
        }
    }
}
