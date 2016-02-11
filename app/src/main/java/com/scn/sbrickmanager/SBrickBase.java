package com.scn.sbrickmanager;

import android.content.Context;
import android.content.Intent;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import java.util.Objects;
import java.util.Timer;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.Semaphore;

/**
 * SBrick base abstract class.
 */
abstract class SBrickBase implements SBrick {

    //
    // Private members
    //

    private static final String TAG = "SBrickBase";

    private String name = null;

    //
    // Protected members
    //

    protected final Context context;
    protected boolean isConnected = false;

    protected LinkedBlockingDeque<Command> commandQueue = new LinkedBlockingDeque<>(100);
    protected Semaphore commandSendingSemaphore = new Semaphore(1);
    protected Timer watchdogTimer = null;

    protected int[] channelValues = new int[] { 0, 0, 0, 0 };

    //
    // Object overrides
    //

    @Override
    public String toString() {
        return getName();
    }

    //
    // SBrick overrides
    //

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void setName(String name) {
        this.name = name;
    }

    @Override
    public boolean isConnected() {
        return isConnected;
    }

    @Override
    public boolean readCharacteristic(SBrickCharacteristicType characteristicType) {
        Log.i(TAG, "readCharacteristic - " + getAddress());

        if (!isConnected) {
            Log.w(TAG, "  Not connected.");
            return false;
        }

        Command command = Command.newReadCharacteristic(characteristicType);
        return commandQueue.offer(command);
    }

    @Override
    public synchronized boolean sendCommand(int channel, int value) {
        Log.i(TAG, "sendCommand - " + getAddress());
        Log.i(TAG, "  channel: " + channel);
        Log.i(TAG, "  value: " + value);

        if (!isConnected) {
            Log.i(TAG, "  Not connected.");
            return false;
        }

        Command command = Command.newRemoteControl(channel, value);
        return commandQueue.offer(command);
    }

    @Override
    public synchronized boolean sendCommand(int v1, int v2, int v3, int v4) {
        Log.i(TAG, "sendCommand - " + getAddress());
        Log.i(TAG, "  value1: " + v1);
        Log.i(TAG, "  value2: " + v2);
        Log.i(TAG, "  value3: " + v3);
        Log.i(TAG, "  value4: " + v4);

        if (!isConnected) {
            Log.i(TAG, "  Not connected.");
            return false;
        }

        Command command = Command.newQuickDrive(v1, v2, v3, v4);
        return commandQueue.offer(command);
    }

    //
    // Internal API
    //

    protected SBrickBase(Context context) {
        Log.i(TAG, "SBrickBase...");

        this.context = context;
    }

    protected Intent buildBroadcastIntent(String action) {
        Intent intent = new Intent();
        intent.setAction(action);
        intent.putExtra(EXTRA_SBRICK_ADDRESS, getAddress());
        return intent;
    }

    protected void sendLocalBroadcast(String action) {
        Log.i(TAG, "sendLocalBroadcast...");
        LocalBroadcastManager.getInstance(context).sendBroadcast(buildBroadcastIntent(action));
    }

    protected void startCommandProcessing() {
        Log.i(TAG, "startCommandProcessing...");

        Thread commandProcessThread = new Thread() {

            @Override
            public void run() {

                try {
                    commandQueue.clear();

                    while (true) {
                        // Wait for the GATT callback to release the semaphore.
                        commandSendingSemaphore.acquire();

                        // Get the next command to process.
                        Command command = commandQueue.take();

                        if (command.getCommandType() == Command.CommandType.QUIT) {
                            Log.i(TAG, "Command process thread quits.");
                            Log.i(TAG, "Empty the command queue...");
                            commandQueue.clear();
                            break;
                        }

                        if (processCommand(command)) {
                            // Command was successful, store the current channel values.
                            switch (command.getCommandType()) {

                                case SEND_QUICK_DRIVE:
                                    channelValues[0] = command.getChannelValues()[0];
                                    channelValues[1] = command.getChannelValues()[1];
                                    channelValues[2] = command.getChannelValues()[2];
                                    channelValues[3] = command.getChannelValues()[3];
                                    break;

                                case SEND_REMOTE_CONTROL:
                                    channelValues[command.getChannel()] = command.getChannelValues()[0];
                                    break;
                            }

                            // TODO: check if channel values are all 0. if not start the watchdog.
                        }
                        else {
                            // Command wasn't sent, no need to wait for the GATT callback.
                            commandSendingSemaphore.release();
                        }
                    }

                }
                catch (Exception ex) {
                    Log.e(TAG, "Command process thread has thrown an exception.");
                }
            }
        };

        commandProcessThread.start();
    }

    protected void stopCommandProcessing() {
        Log.i(TAG, "stopCommandProcessing...");

        Command quitCommand = Command.newQuit();
        if (!commandQueue.offerFirst(quitCommand)) {
            Log.e(TAG, "  Could not send quit command to queue.");
            return;
        }

        // Just to be sure the semaphore doesn't block the thread.
        commandSendingSemaphore.release();
    }

    protected abstract boolean processCommand(Command command);

    //

    protected static class Command {

        public enum CommandType {
            SEND_QUICK_DRIVE,
            SEND_REMOTE_CONTROL,
            READ_CHARACTERISTIC,
            QUIT
        };

        private CommandType commandType;
        private byte[] commandBuffer;
        private SBrickCharacteristicType characteristicType;
        private int channel;
        private int[] channelValues;

        private Command () {

            this.commandType = CommandType.QUIT;
            this.commandBuffer = null;

            this.characteristicType = SBrickCharacteristicType.Unknown;
            this.channel = -1;
            this.channelValues = null;
        }

        private Command(int channel, int value) {

            byte invert = (byte)((0 <= value) ? 0 : 1);
            byte byteValue = (byte)(Math.min(255, Math.abs(value)));

            this.commandType = CommandType.SEND_REMOTE_CONTROL;
            this.commandBuffer = new byte[] { 0x01, (byte)channel, invert, byteValue};

            this.characteristicType = SBrickCharacteristicType.Unknown;
            this.channel = channel;
            this.channelValues = new int[] { value };
        }

        private Command(int v1, int v2, int v3, int v4) {

            byte bv1 = (byte)((Math.min(255, Math.abs(v1)) & 0xfe) | (0 <= v1 ? 0 : 1));
            byte bv2 = (byte)((Math.min(255, Math.abs(v2)) & 0xfe) | (0 <= v2 ? 0 : 1));
            byte bv3 = (byte)((Math.min(255, Math.abs(v3)) & 0xfe) | (0 <= v3 ? 0 : 1));
            byte bv4 = (byte)((Math.min(255, Math.abs(v4)) & 0xfe) | (0 <= v4 ? 0 : 1));

            this.commandType = CommandType.SEND_QUICK_DRIVE;
            this.commandBuffer = new byte[] { bv1, bv2, bv3, bv4 };

            this.characteristicType = SBrickCharacteristicType.Unknown;
            this.channel = -1;
            this.channelValues = new int[] { v1, v2, v3, v4 };
        }

        private Command(SBrickCharacteristicType characteristicType) {

            this.commandType = CommandType.READ_CHARACTERISTIC;
            this.commandBuffer = null;

            this.characteristicType = characteristicType;
            this.channel = -1;
            this.channelValues = null;
        }

        public static Command newRemoteControl(int channel, int value) {
            return new Command(channel, value);
        }

        public static Command newQuickDrive(int v1, int v2, int v3, int v4) {
            return new Command(v1, v2, v3, v4);
        }

        public static Command newReadCharacteristic(SBrickCharacteristicType characteristicType) {
            return new Command(characteristicType);
        }

        public static Command newQuit() {
            return new Command();
        }

        public CommandType getCommandType() { return commandType; }
        public byte[] getCommandBuffer() { return commandBuffer; }
        public SBrickCharacteristicType getCharacteristicType() { return characteristicType; }
        public int getChannel() { return channel; }
        public int[] getChannelValues() { return channelValues; }
    }
}
