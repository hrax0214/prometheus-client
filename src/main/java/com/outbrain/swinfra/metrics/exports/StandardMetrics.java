package com.outbrain.swinfra.metrics.exports;

import com.outbrain.swinfra.metrics.Gauge;
import com.outbrain.swinfra.metrics.MetricRegistry;
import com.sun.management.OperatingSystemMXBean;
import com.sun.management.UnixOperatingSystemMXBean;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

public class StandardMetrics implements MetricsRegistrar {

    private final static double KB = 1024;

    private final StatusReader statusReader;
    private final OperatingSystemMXBean osBean;
    private final RuntimeMXBean runtimeBean;
    private final boolean unix;
    private final boolean linux;

    public StandardMetrics() {
        this(new StatusReader(),
            (OperatingSystemMXBean)ManagementFactory.getOperatingSystemMXBean(),
            ManagementFactory.getRuntimeMXBean());
    }

    StandardMetrics(final StatusReader statusReader, final OperatingSystemMXBean osBean, final RuntimeMXBean runtimeBean) {
        this.statusReader = statusReader;
        this.osBean = osBean;
        this.runtimeBean = runtimeBean;
        this.unix = (osBean instanceof UnixOperatingSystemMXBean);
        this.linux = (osBean.getName().indexOf("Linux") == 0);
    }

    @Override
    public MetricRegistry registerMetricsTo(final MetricRegistry registry) {
        registry.getOrRegister(
            new Gauge.GaugeBuilder("process_cpu_seconds_total", "Total user and system CPU time spent in seconds.").
                withValueSupplier(() -> TimeUnit.NANOSECONDS.toSeconds(osBean.getProcessCpuTime())).build());
        registry.getOrRegister(
            new Gauge.GaugeBuilder("process_start_time_seconds", "Start time of the process since unix epoch in seconds.").
                withValueSupplier(() -> TimeUnit.MILLISECONDS.toSeconds(runtimeBean.getStartTime())).build());

        if (unix) {
            final UnixOperatingSystemMXBean unixBean = (UnixOperatingSystemMXBean) osBean;
            registry.getOrRegister(
                new Gauge.GaugeBuilder("process_open_fds", "Number of open file descriptors.").
                    withValueSupplier(unixBean::getOpenFileDescriptorCount).build());
            registry.getOrRegister(
                new Gauge.GaugeBuilder("process_max_fds", "Maximum number of open file descriptors.").
                    withValueSupplier(unixBean::getMaxFileDescriptorCount).build());
        }

        // There's no standard Java or POSIX way to get memory stats,
        // so add support for just Linux for now.
        if (linux) {
            registry.getOrRegister(
                new Gauge.GaugeBuilder("process_virtual_memory_bytes", "Virtual memory size in bytes.").
                    withValueSupplier(() -> {
                        try {
                            return statusReader.readProcSelfStatus().getValue("VmSize:");
                        } catch (IOException e) {
                            return 0;
                        }
                    }).build());
            registry.getOrRegister(
                new Gauge.GaugeBuilder("process_resident_memory_bytes", "Resident memory size in bytes.").
                    withValueSupplier(() -> {
                        try {
                            return statusReader.readProcSelfStatus().getValue("VmRSS:");
                        } catch (IOException e) {
                            return 0;
                        }
                    }).build());
        }

        return registry;
    }

    static class StatusReader {

        StatusParser readProcSelfStatus() throws IOException {
            return new StatusParser(new String(Files.readAllBytes(Paths.get("/proc/self/status"))));
        }
    }

    static class StatusParser {

        private final String status;

        StatusParser(final String status) {
            this.status = status;
        }

        private String parseStatus(final String prefix) throws IOException {
            final int index = status.indexOf(prefix);
            final int endIndex = status.indexOf('\n', index);
            return (index >= 0) ? status.substring(index + prefix.length(), endIndex) : "";
        }

        private double parseToDoubleValue(final String value) {
            if (value.endsWith("kB")) {
                return Double.parseDouble(value.substring(0, value.length() - 2).trim()) * KB;
            }
            return 0;
        }

        double getValue(final String prefix) {
            try {
                return parseToDoubleValue(parseStatus(prefix));
            } catch (IOException | NumberFormatException e) {
                return 0;
            }
        }
    }
}