package com.richmond423.loadbalancerpro.cli;

import com.richmond423.loadbalancerpro.gui.Command;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

public class UndoManager {
    private static final Logger logger = LogManager.getLogger(UndoManager.class);
    private final String undoFile;
    private final Stack<Command> commandHistory = new Stack<>();

    public UndoManager(String undoFile) {
        this.undoFile = undoFile;
        loadUndoHistory();
    }

    public void addCommand(Command command) {
        synchronized (commandHistory) {
            commandHistory.push(command);
        }
    }

    public boolean undo() {
        synchronized (commandHistory) {
            if (commandHistory.isEmpty()) return false;
            commandHistory.pop().undo();
            return true;
        }
    }

    public void saveUndoHistory() {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(undoFile))) {
            synchronized (commandHistory) {
                oos.writeObject(new ArrayList<>(commandHistory));
            }
            logger.info("Undo history saved to {}", undoFile);
        } catch (IOException e) {
            logger.error("Failed to save undo history: {}", e.getMessage(), e);
        }
    }

    public void clearUndoFile() {
        File f = new File(undoFile);
        if (f.exists() && f.delete()) {
            logger.info("Undo file deleted.");
            printSuccess("Undo history cleared.");
        } else if (!f.exists()) {
            logger.info("No undo history file to clear.");
            printSuccess("No undo history to clear.");
        } else {
            logger.error("Failed to delete undo file: {}", undoFile);
            printError("Failed to clear undo history.");
        }
    }

    private void loadUndoHistory() {
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(undoFile))) {
            synchronized (commandHistory) {
                List<Command> loaded = (List<Command>) ois.readObject();
                commandHistory.clear();
                commandHistory.addAll(loaded);
            }
            logger.info("Undo history loaded from {}", undoFile);
            printSuccess("Previous undo history loaded.");
        } catch (FileNotFoundException e) {
            logger.info("No undo history file found, starting fresh.");
        } catch (IOException | ClassNotFoundException e) {
            logger.error("Failed to load undo history: {}", e.getMessage(), e);
        }
    }

    private void printError(String message) {
        System.out.println(CliConfig.AnsiColor.ERROR.getCode() + message + CliConfig.AnsiColor.RESET.getCode());
        logger.error(message);
    }

    private void printSuccess(String message) {
        System.out.println(CliConfig.AnsiColor.SUCCESS.getCode() + message + CliConfig.AnsiColor.RESET.getCode());
    }
}
