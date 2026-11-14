package com.tecnopc.desktop;

/** Native launchers must enter a class that does not extend JavaFX Application. */
public final class Launcher {
    private Launcher() {}
    public static void main(String[] args) { AdminDesktop.main(args); }
}
