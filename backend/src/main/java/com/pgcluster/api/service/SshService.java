package com.pgcluster.api.service;

import com.jcraft.jsch.*;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Service for executing SSH commands on remote hosts.
 * Provides methods for command execution, file upload, and session management
 * with configurable timeouts and automatic retry for transient failures.
 */
@Slf4j
@Service
public class SshService {

    @Value("${ssh.user:root}")
    private String sshUser;

    @Value("${ssh.private-key-path:/home/appuser/.ssh/id_rsa}")
    private String privateKeyPath;

    @Value("${ssh.timeout-ms:30000}")
    private int defaultTimeoutMs;

    @Value("${ssh.retry.max-attempts:3}")
    private int maxRetryAttempts;

    @Value("${ssh.retry.delay-ms:2000}")
    private int retryDelayMs;

    private final HostKeyVerifier hostKeyVerifier;

    private static final int SSH_PORT = 22;

    public SshService(HostKeyVerifier hostKeyVerifier) {
        this.hostKeyVerifier = hostKeyVerifier;
    }

    /**
     * Execute a command on a remote host using default timeout from configuration
     */
    public CommandResult executeCommand(String host, String command) {
        return executeCommand(host, command, defaultTimeoutMs);
    }

    /**
     * Execute a command with custom timeout
     */
    public CommandResult executeCommand(String host, String command, int timeoutMs) {
        return executeCommandInternal(host, command, timeoutMs);
    }

    /**
     * Execute a command with automatic retry for transient failures.
     * Use this for critical operations where temporary network issues should be tolerated.
     */
    public CommandResult executeCommandWithRetry(String host, String command) {
        return executeCommandWithRetry(host, command, defaultTimeoutMs);
    }

    /**
     * Execute a command with custom timeout and automatic retry for transient failures.
     */
    public CommandResult executeCommandWithRetry(String host, String command, int timeoutMs) {
        for (int attempt = 1; attempt <= maxRetryAttempts; attempt++) {
            CommandResult result = executeCommandInternal(host, command, timeoutMs);

            // If command executed (even with non-zero exit code), return the result
            if (result.getExitCode() != -1) {
                return result;
            }

            // Check if the error is transient and retriable
            if (!isTransientError(result.getStderr())) {
                log.debug("Non-transient SSH error on {}: {}", host, result.getStderr());
                return result;
            }

            log.warn("Transient SSH error on {} (attempt {}/{}): {}",
                    host, attempt, maxRetryAttempts, result.getStderr());

            if (attempt < maxRetryAttempts) {
                try {
                    Thread.sleep(retryDelayMs * attempt); // Exponential backoff
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return new CommandResult(-1, "", "Interrupted during retry");
                }
            }
        }

        log.error("SSH command failed after {} attempts on {}", maxRetryAttempts, host);
        return new CommandResult(-1, "", "Failed after " + maxRetryAttempts + " attempts");
    }

    /**
     * Check if an error is transient and should be retried.
     */
    private boolean isTransientError(String errorMessage) {
        if (errorMessage == null) {
            return false;
        }
        String lowerError = errorMessage.toLowerCase();
        return lowerError.contains("connection refused") ||
               lowerError.contains("connection reset") ||
               lowerError.contains("connection timed out") ||
               lowerError.contains("timeout") ||
               lowerError.contains("no route to host") ||
               lowerError.contains("network is unreachable") ||
               lowerError.contains("temporarily unavailable") ||
               lowerError.contains("socket exception") ||
               lowerError.contains("broken pipe");
    }

    /**
     * Internal method to execute SSH command without retry logic.
     */
    private CommandResult executeCommandInternal(String host, String command, int timeoutMs) {
        Session session = null;
        ChannelExec channel = null;

        try {
            session = createSession(host);
            session.connect(timeoutMs);

            channel = (ChannelExec) session.openChannel("exec");
            channel.setCommand(command);

            ByteArrayOutputStream stdout = new ByteArrayOutputStream();
            ByteArrayOutputStream stderr = new ByteArrayOutputStream();
            channel.setOutputStream(stdout);
            channel.setErrStream(stderr);

            channel.connect(timeoutMs);

            // Wait for command to complete
            while (!channel.isClosed()) {
                Thread.sleep(100);
            }

            int exitCode = channel.getExitStatus();

            return new CommandResult(
                    exitCode,
                    stdout.toString().trim(),
                    stderr.toString().trim()
            );

        } catch (Exception e) {
            log.error("SSH command failed on {}: {}", host, e.getMessage());
            return new CommandResult(-1, "", e.getMessage());
        } finally {
            if (channel != null) channel.disconnect();
            if (session != null) session.disconnect();
        }
    }

    /**
     * Check if host is reachable via SSH
     */
    public boolean isHostReachable(String host) {
        Session session = null;
        try {
            session = createSession(host);
            session.connect(5000);
            return true;
        } catch (Exception e) {
            return false;
        } finally {
            if (session != null) session.disconnect();
        }
    }

    /**
     * Wait for SSH to become available
     */
    public boolean waitForSsh(String host, int maxAttempts, int intervalMs) {
        log.info("Waiting for SSH on {}...", host);

        for (int i = 0; i < maxAttempts; i++) {
            if (isHostReachable(host)) {
                log.info("SSH available on {} after {} attempts", host, i + 1);
                return true;
            }
            try {
                Thread.sleep(intervalMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }

        log.warn("SSH not available on {} after {} attempts", host, maxAttempts);
        return false;
    }

    /**
     * Copy file to remote host
     */
    public void copyFile(String host, String localPath, String remotePath) throws Exception {
        Session session = null;
        ChannelSftp channel = null;

        try {
            session = createSession(host);
            session.connect(defaultTimeoutMs);

            channel = (ChannelSftp) session.openChannel("sftp");
            channel.connect(defaultTimeoutMs);

            try (InputStream is = Files.newInputStream(Path.of(localPath))) {
                channel.put(is, remotePath);
            }

            log.info("File copied to {}:{}", host, remotePath);

        } finally {
            if (channel != null) channel.disconnect();
            if (session != null) session.disconnect();
        }
    }

    /**
     * Upload string content to remote host
     */
    public void uploadContent(String host, String content, String remotePath) {
        Session session = null;
        ChannelSftp channel = null;

        try {
            session = createSession(host);
            session.connect(defaultTimeoutMs);

            channel = (ChannelSftp) session.openChannel("sftp");
            channel.connect(defaultTimeoutMs);

            // Create parent directory if needed
            String parentDir = remotePath.substring(0, remotePath.lastIndexOf('/'));
            try {
                channel.stat(parentDir);
            } catch (SftpException e) {
                // Directory doesn't exist, create it
                executeCommand(host, "mkdir -p " + parentDir);
                channel.disconnect();
                channel = (ChannelSftp) session.openChannel("sftp");
                channel.connect(defaultTimeoutMs);
            }

            try (InputStream is = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8))) {
                channel.put(is, remotePath);
            }

            log.info("Content uploaded to {}:{}", host, remotePath);

        } catch (Exception e) {
            log.error("Failed to upload content to {}:{} - {}", host, remotePath, e.getMessage());
            throw new RuntimeException("Failed to upload content: " + e.getMessage(), e);
        } finally {
            if (channel != null) channel.disconnect();
            if (session != null) session.disconnect();
        }
    }

    private Session createSession(String host) throws JSchException {
        JSch jsch = new JSch();

        // Add private key
        if (privateKeyPath != null && !privateKeyPath.isEmpty()) {
            jsch.addIdentity(privateKeyPath);
        }

        // Use TOFU host key verification instead of disabling verification
        jsch.setHostKeyRepository(hostKeyVerifier);

        Session session = jsch.getSession(sshUser, host, SSH_PORT);

        // Set preferred host key algorithms
        java.util.Properties config = new java.util.Properties();
        config.put("PreferredAuthentications", "publickey");
        session.setConfig(config);

        return session;
    }

    /**
     * Remove host key trust when server is deleted.
     */
    public void removeHostKeyTrust(String host) {
        hostKeyVerifier.removeHost(host);
    }

    @Data
    public static class CommandResult {
        private final int exitCode;
        private final String stdout;
        private final String stderr;

        public boolean isSuccess() {
            return exitCode == 0;
        }
    }
}
