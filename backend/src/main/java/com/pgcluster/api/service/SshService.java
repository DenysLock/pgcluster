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

@Slf4j
@Service
public class SshService {

    @Value("${ssh.user:root}")
    private String sshUser;

    @Value("${ssh.private-key-path:/home/appuser/.ssh/id_rsa}")
    private String privateKeyPath;

    private static final int SSH_PORT = 22;
    private static final int TIMEOUT_MS = 30000;

    /**
     * Execute a command on a remote host
     */
    public CommandResult executeCommand(String host, String command) {
        return executeCommand(host, command, TIMEOUT_MS);
    }

    /**
     * Execute a command with custom timeout
     */
    public CommandResult executeCommand(String host, String command, int timeoutMs) {
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
            session.connect(TIMEOUT_MS);

            channel = (ChannelSftp) session.openChannel("sftp");
            channel.connect(TIMEOUT_MS);

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
            session.connect(TIMEOUT_MS);

            channel = (ChannelSftp) session.openChannel("sftp");
            channel.connect(TIMEOUT_MS);

            // Create parent directory if needed
            String parentDir = remotePath.substring(0, remotePath.lastIndexOf('/'));
            try {
                channel.stat(parentDir);
            } catch (SftpException e) {
                // Directory doesn't exist, create it
                executeCommand(host, "mkdir -p " + parentDir);
                channel.disconnect();
                channel = (ChannelSftp) session.openChannel("sftp");
                channel.connect(TIMEOUT_MS);
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

        Session session = jsch.getSession(sshUser, host, SSH_PORT);

        // Disable strict host key checking for automation
        java.util.Properties config = new java.util.Properties();
        config.put("StrictHostKeyChecking", "no");
        session.setConfig(config);

        return session;
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
