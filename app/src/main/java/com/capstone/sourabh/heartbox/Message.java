package com.capstone.sourabh.heartbox;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
/**
 * Created by sourabh on 4/5/18.
 * Represents a message sent from the computer to the app. It is responsible for int
 */

enum MessageType {
    ECG, SPO2, BP, MISC
};

public class Message {
    int start, end = -3;
    float value;
    MessageType _type;

    private Message() {
        MessageType _type = MessageType.ECG;
        float value = -1;
    }

    Message(byte [] buff) {
        byte [] float_bytes = Arrays.copyOfRange(buff, 1, 4);
        value = ByteBuffer.wrap(float_bytes).order(ByteOrder.BIG_ENDIAN).getFloat();
        if(buff[0] == 1) {
            _type = MessageType.ECG;
        } else if(buff[0] == 2) {
            _type = MessageType.SPO2;
        } else if(buff[0] == 3) {
            _type = MessageType.BP;
        }
    }

    private void process_buffer(byte [] buff, int index) {
        byte [] float_bytes = Arrays.copyOfRange(buff, index + 1, index + 4);
        value = ByteBuffer.wrap(float_bytes).order(ByteOrder.BIG_ENDIAN).getFloat();
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
