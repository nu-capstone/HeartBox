package com.capstone.sourabh.heartbox;

import android.util.Log;

/**
 * Created by sourabh on 4/5/18.
 * Represents a message sent from the computer to the app. It is responsible for int
 */

enum MessageType {
    ECG, SPO2, BP, MISC
};

public class Message {
    float value;
    MessageType _type;

    private Message() {
        MessageType _type = MessageType.ECG;
        float value = -1;
    }

    Message(byte [] buff) {
        int asInt = (buff[1] & 0xFF)
                | (buff[2] & 0xFF) << 8
                | (buff[3] & 0xFF) << 16
                | (buff[4] & 0xFF) << 24;
        value = Float.intBitsToFloat(asInt);
        if(buff[0] == 49 ) { //"1" == 49 in ASCII
            _type = MessageType.ECG;
        } else if(buff[0] == 50) {
            _type = MessageType.SPO2;
        } else if(buff[0] == 51) {
            _type = MessageType.BP;
        } else {
            _type = MessageType.MISC;
        }
        Log.i("Message", "Created message");
    }

    public float get_value() {
        return value;
    }

    public MessageType get_type() {
        return _type;
    }

}
