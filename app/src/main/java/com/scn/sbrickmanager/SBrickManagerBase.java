package com.scn.sbrickmanager;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.scn.sbrickmanager.sbrickcommand.Command;
import com.scn.sbrickmanager.sbrickcommand.CommandMethod;
import com.scn.sbrickmanager.sbrickcommand.QuitCommand;
import com.scn.sbrickmanager.sbrickcommand.SBrickCommand;
import com.scn.sbrickmanager.sbrickcommand.WriteCharacteristicCommand;
import com.scn.sbrickmanager.sbrickcommand.WriteQuickDriveCommand;
import com.scn.sbrickmanager.sbrickcommand.WriteRemoteControlCommand;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.Semaphore;

/**
 * SBrick manager base abstract class.
 */
abstract class SBrickManagerBase implements SBrickManager {

    //
    // Private members
    //

    private static final String TAG = SBrickManagerBase.class.getSimpleName();

    private static final String SBrickMapPreferencesName = "SBrickMapPrefs";

    private LinkedBlockingDeque<Command> commandQueue = new LinkedBlockingDeque<>(20);
    private Thread commandProcessThread = null;
    private Semaphore commandSemaphore = new Semaphore(1);
    private Object lockObject = new Object();

    //
    // Protected members
    //

    protected final Context context;
    protected final Map<String, SBrick> sbrickMap = new HashMap<>();

    protected boolean isScanning = false;

    //
    // Constructor
    //

    protected SBrickManagerBase(Context context) {
        Log.i(TAG, "SBrickManagerBase...");

        this.context = context;
    }

    //
    // SBrickManager overrides
    //

    @Override
    public boolean loadSBricks() {
        Log.i(TAG, "loadSBricks...");

        try {
            SharedPreferences prefs = context.getSharedPreferences(SBrickMapPreferencesName, Context.MODE_PRIVATE);

            sbrickMap.clear();

            HashMap<String, String> sbrickAddressAndNameMap = (HashMap<String, String>)prefs.getAll();
            for (String sbrickAddress : sbrickAddressAndNameMap.keySet()) {
                SBrick sbrick = createSBrick(sbrickAddress);
                sbrick.setName(sbrickAddressAndNameMap.get(sbrickAddress));
            }
        }
        catch (Exception ex) {
            Log.e(TAG, "Error during loading SBricks.", ex);
            return false;
        }

        return true;
    }

    @Override
    public boolean saveSBricks() {
        Log.i(TAG, "saveSBricks...");

        try {
            SharedPreferences prefs = context.getSharedPreferences(SBrickMapPreferencesName, Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();

            // Clear first
            editor.clear();

            // Write the sbricks
            for (String sbrickAddress : sbrickMap.keySet()) {
                editor.putString(sbrickAddress, sbrickMap.get(sbrickAddress).getName());
            }

            editor.commit();
        }
        catch (Exception ex) {
            Log.e(TAG, "Error during saving SBricks.", ex);
            return false;
        }

        return true;
    }

    @Override
    public List<SBrick> getSBricks() {
        Log.i(TAG, "getSBricks...");

        return new ArrayList<>(sbrickMap.values());
    }

    @Override
    public List<String> getSBrickAddresses() {
        Log.i(TAG, "getSBrickAddresses...");

        List<String> sbrickAddresses = new ArrayList<>();
        for (SBrick sBrick : sbrickMap.values()) {
            sbrickAddresses.add(sBrick.getAddress());
        }

        return sbrickAddresses;
    }

    @Override
    public void forgetSBrick(String sbrickAddress) {
        Log.i(TAG, "forgetSBrick - " + sbrickAddress);

        if (sbrickMap.containsKey(sbrickAddress))
            sbrickMap.remove(sbrickAddress);
    }

    @Override
    public boolean startCommandProcessing() {

        synchronized (lockObject) {
            Log.i(TAG, "startCommandProcessing...");

            if (commandProcessThread != null) {
                Log.w(TAG, "  Command processing has already been started.");
                return false;
            }

            try {
                final Semaphore processThreadStartedSemaphore = new Semaphore(1);
                processThreadStartedSemaphore.acquire();

                commandProcessThread = new Thread() {

                    @Override
                    public void run() {

                        try {
                            commandQueue.clear();
                            processThreadStartedSemaphore.release();

                            while (true) {
                                try {
                                    // Wait for the GATT callback to release the semaphore.
                                    commandSemaphore.acquire();

                                    // Get the next command to process.
                                    Command command = commandQueue.take();

                                    synchronized (lockObject) {
                                        if (command instanceof QuitCommand) {

                                            // Quit command
                                            Log.i(TAG, "Quit command.");
                                            Log.i(TAG, "Empty the command queue...");
                                            commandQueue.clear();
                                            break;
                                        } else if (command instanceof SBrickCommand) {

                                            SBrickCommand sbrickCommand = (SBrickCommand) command;
                                            CommandMethod commandMethod = sbrickCommand.getCommandMethod();
                                            SBrickBase sbrick = (SBrickBase) sbrickCommand.getSbrick(); // Not nice :(

                                            // Execute the command method
                                            if (commandMethod != null && commandMethod.execute()) {

                                                // Set the last write command and its time on the SBrick
                                                if (command instanceof WriteCharacteristicCommand)
                                                    sbrick.setLastWriteCommand((WriteCharacteristicCommand) command);
                                            } else {
                                                Log.w(TAG, "Command method execution failed.");
                                                // Command wasn't sent, no need to wait for the GATT callback.
                                                commandSemaphore.release();
                                            }
                                        }
                                    }
                                } catch (Exception ex) {
                                    Log.e(TAG, "Command process thread has thrown an exception.", ex);
                                    commandSemaphore.release();
                                }
                            }

                            Log.i(TAG, "Command process thread exits...");
                        } catch (Exception ex) {
                            Log.e(TAG, "Command process thread has thrown an exception.", ex);
                        }

                        commandProcessThread = null;
                    }
                };

                commandProcessThread.start();

                // Waiting for the process thread to start.
                processThreadStartedSemaphore.acquire();

                return true;
            } catch (Exception ex) {
                Log.e(TAG, "Faild to start command processing thread.", ex);
                return false;
            }
        }
    }

    public void stopCommandProcessing() {

        synchronized (lockObject) {
            Log.i(TAG, "stopCommandProcessing...");

            if (commandProcessThread == null) {
                Log.w(TAG, "  Command processing has not been started.");
                return;
            }

            Command quitCommand = Command.newQuitCommand();
            commandQueue.clear();
            commandQueue.offerFirst(quitCommand);

            // Just to be sure the semaphore doesn't block the thread.
            commandSemaphore.release();
        }
    }

    //
    // Internal API
    //

    Object getLockObject() { return lockObject; }

    boolean sendCommand(Command command) {
        //Log.i(TAG, "sendCommand...");
        //Log.i(TAG, "  " + command);

        return commandQueue.offer(command);
    }

    boolean sendPriorityCommand(Command command) {
        //Log.i(TAG, "sendPriorityCommand...");
        //Log.i(TAG, "  " + command);

        return commandQueue.offerFirst(command);
    }

    void releaseCommandSemaphore() {
        commandSemaphore.release();
    }

    //
    // Protected abstract methods
    //

    protected abstract SBrick createSBrick(String sbrickAddress);
}
