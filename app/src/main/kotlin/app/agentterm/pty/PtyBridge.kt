package app.agentterm.pty

/**
 * Native PTY bridge (libpty.so built from src/main/cpp/pty.c):
 * real pseudo-terminal around the device shell — no root required.
 */
object PtyBridge {
    init { System.loadLibrary("pty") }

    external fun nativeOpen(shell: String, argv: Array<String>, envp: Array<String>, cols: Int, rows: Int): Int
    external fun nativeSetSize(fd: Int, cols: Int, rows: Int)
    external fun nativeClose(fd: Int)
}