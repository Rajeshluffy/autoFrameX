package com.api.testdata;

/** Something to undo: delete a record a test created, restore a setting it changed. May throw. */
@FunctionalInterface
public interface CleanupAction {
    void run() throws Exception;
}
