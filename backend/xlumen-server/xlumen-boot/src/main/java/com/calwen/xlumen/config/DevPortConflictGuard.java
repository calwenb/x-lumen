package com.calwen.xlumen.config;

import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.Environment;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 开发环境端口守卫（IDEA-008）：在 Web 容器绑定前询问是否结束监听进程。
 * 通过 XLUMEN_DEV_PORT_GUARD 显式开启，生产环境默认关闭。
 */
public final class DevPortConflictGuard implements ApplicationListener<ApplicationEnvironmentPreparedEvent> {

    private final int currentPid = (int) ProcessHandle.current().pid();

    @Override
    public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
        Environment environment = event.getEnvironment();
        if (!Boolean.parseBoolean(environment.getProperty("XLUMEN_DEV_PORT_GUARD", "false"))) {
            return;
        }
        if (List.of(environment.getActiveProfiles()).stream().anyMatch("prod"::equalsIgnoreCase)) {
            return;
        }
        int port = parsePort(environment.getProperty("server.port", "8080"));
        List<PortOwner> owners = findOwners(port);
        owners.removeIf(owner -> owner.pid() == currentPid);
        if (owners.isEmpty()) return;

        System.err.printf("端口 %d 已被占用：%s。输入 y 自动结束这些进程并继续启动，其他输入退出：%n",
                port, owners.stream().map(PortOwner::display).reduce((a, b) -> a + ", " + b).orElse("未知进程"));
        if (!readConfirmation()) {
            throw new IllegalStateException("端口 " + port + " 已被占用，用户选择不结束占用进程");
        }
        owners.removeIf(owner -> owner.pid() <= 4);
        owners.forEach(owner -> ProcessHandle.of(owner.pid()).ifPresent(handle -> {
            handle.destroyForcibly();
            if (handle.isAlive()) handle.destroy();
        }));
        if (!waitUntilFree(port)) {
            throw new IllegalStateException("端口 " + port + " 仍被占用，未继续启动应用");
        }
    }

    private int parsePort(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new IllegalStateException("server.port 配置无效：" + value, e);
        }
    }

    private boolean readConfirmation() {
        try {
            System.out.print("确认结束占用进程？ [y/N] ");
            String answer = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8)).readLine();
            return "y".equalsIgnoreCase(answer == null ? "" : answer.trim());
        } catch (IOException e) {
            return false;
        }
    }

    private boolean waitUntilFree(int port) {
        for (int i = 0; i < 20; i++) {
            if (findOwners(port).isEmpty()) return true;
            try {
                Thread.sleep(100L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    private List<PortOwner> findOwners(int port) {
        return isWindows() ? findWindowsOwners(port) : findUnixOwners(port);
    }

    private List<PortOwner> findWindowsOwners(int port) {
        List<PortOwner> result = new ArrayList<>();
        String output = run("cmd", "/c", "netstat -ano -p tcp");
        for (String line : output.split("\\R")) {
            String[] parts = line.trim().split("\\s+");
            if (parts.length < 5 || !"LISTENING".equalsIgnoreCase(parts[3])) continue;
            if (parseAddressPort(parts[1]) != port) continue;
            try {
                int pid = Integer.parseInt(parts[4]);
                String name = run("cmd", "/c", "tasklist /FI \"PID eq " + pid + "\" /FO CSV /NH")
                        .split("\\R", 2)[0].replaceAll("^\"|\".*$", "");
                result.add(new PortOwner(pid, name));
            } catch (NumberFormatException ignored) {
                // 忽略无法解析的系统行，让 Spring Boot 保留原始占用错误。
            }
        }
        return result;
    }

    private int parseAddressPort(String address) {
        String value = address.trim();
        int separator = value.lastIndexOf(':');
        if (separator < 0 || separator == value.length() - 1) return -1;
        try {
            return Integer.parseInt(value.substring(separator + 1));
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private List<PortOwner> findUnixOwners(int port) {
        List<PortOwner> result = new ArrayList<>();
        String output = run("sh", "-c", "lsof -nP -iTCP:" + port + " -sTCP:LISTEN -t");
        for (String line : output.split("\\R")) {
            try {
                int pid = Integer.parseInt(line.trim());
                String name = run("sh", "-c", "ps -p " + pid + " -o comm=").trim();
                result.add(new PortOwner(pid, name));
            } catch (NumberFormatException ignored) {
                // lsof 不可用或无结果时返回空集合。
            }
        }
        return result;
    }

    private String run(String... command) {
        try {
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            return new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }

    private boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    private record PortOwner(int pid, String name) {
        String display() {
            return name == null || name.isBlank() ? "PID " + pid : name + " (PID " + pid + ")";
        }
    }
}
