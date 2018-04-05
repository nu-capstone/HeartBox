package com.capstone.sourabh.heartbox;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
/**
 * Created by sourabh on 4/5/18.
 * Represents a message sent from the computer to the app. It is responsible for int
 */

enum MessageType {
    ECG, SP02, BP, MISC
};

public class Message {
    int START_BYTE = 49;
    int END_BYTE = 50;
    int start, end = -3;
    float value;
    MessageType _type;

    private Message() {
        MessageType _type = MessageType.ECG;
        float value = -1;
    }

    Message(byte [] buff) {
        int idx = -2;
        for (int ii = 0; ii < buff.length; ii++) {
            if(buff[ii] == START_BYTE) {
                start = ii;
                end = ii + 4;
                idx = ii;
            }
        }
        process_buffer(buff, idx + 1);
    }

    private void process_buffer(byte [] buff, int index) {
        byte [] float_bytes = Arrays.copyOfRange(buff, index + 1, index + 4);
        value = ByteBuffer.wrap(float_bytes).order(ByteOrder.LITTLE_ENDIAN).getFloat();
    }

    public float get_value() {
        return value;
    }

    public MessageType get_type() {
        return _type;
    }

    public int get_end() {
        return end;
    }

}
