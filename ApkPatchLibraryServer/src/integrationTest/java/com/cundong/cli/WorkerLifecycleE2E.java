package com.cundong.cli;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Paths;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

/** Verifies actual child-process termination on timeout and interruption. */
public final class WorkerLifecycleE2E {
    public static void main(String[] args) throws Exception {
        if (args.length > 0) {
            System.out.println("ready");
            System.out.flush();
            Thread.sleep(60000);
            return;
        }
        Process worker = sleeper();
        try {
            ApkPatchCli.waitForWorker(worker, 50);
            throw new AssertionError("timeout was not reported");
        } catch (TimeoutException expected) {
            if (worker.isAlive()) throw new AssertionError("worker survived timeout");
        } finally {
            worker.destroyForcibly();
        }
        Process interruptedWorker = sleeper();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread waiter = new Thread(() -> {
            try {
                ApkPatchCli.waitForWorker(interruptedWorker, 10000);
                failure.set(new AssertionError("interruption was not reported"));
            } catch (InterruptedException expected) {
                if (interruptedWorker.isAlive()) failure.set(new AssertionError("worker survived interrupt"));
            } catch (Throwable unexpected) {
                failure.set(unexpected);
            }
        });
        try {
            waiter.start();
            waiter.interrupt();
            waiter.join(5000);
            if (waiter.isAlive()) throw new AssertionError("waiter did not finish");
            if (failure.get() != null) throw new AssertionError(failure.get());
        } finally {
            interruptedWorker.destroyForcibly();
        }
        int exit = ApkPatchCli.run(new String[]{"--timeout-seconds", "0", "diff", "a", "b", "c"},
                System.out, System.err);
        if (exit != ApkPatchCli.EXIT_USAGE) throw new AssertionError("invalid timeout was accepted");
        System.out.println("worker lifecycle PASS: timeout, interruption, invalid timeout option");
    }

    private static Process sleeper() throws Exception {
        Process child = new ProcessBuilder(Paths.get(System.getProperty("java.home"), "bin", "java").toString(),
                "-cp", System.getProperty("java.class.path"), WorkerLifecycleE2E.class.getName(), "sleep")
                .redirectErrorStream(true).start();
        String ready = new BufferedReader(new InputStreamReader(child.getInputStream())).readLine();
        if (!"ready".equals(ready)) {
            child.destroyForcibly();
            throw new AssertionError("test child did not start: " + ready);
        }
        return child;
    }
}
