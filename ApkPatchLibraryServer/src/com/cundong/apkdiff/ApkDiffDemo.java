package com.cundong.apkdiff;

import com.cundong.cli.ApkPatchCli;

/** @deprecated Use {@link ApkPatchCli} with {@code diff <old> <new> <patch>}. */
@Deprecated
public final class ApkDiffDemo {

    private ApkDiffDemo() {
    }

    public static void main(String[] args) {
        String[] cliArgs = new String[args.length + 1];
        cliArgs[0] = "diff";
        System.arraycopy(args, 0, cliArgs, 1, args.length);
        ApkPatchCli.main(cliArgs);
    }
}
